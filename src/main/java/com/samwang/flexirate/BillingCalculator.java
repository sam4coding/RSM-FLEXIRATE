package com.samwang.flexirate;

import com.samwang.flexirate.model.*;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.zone.ZoneRules;
import java.util.*;

/**
 * Pure calculator core: BillingInput → BillingOutput.
 * Clock is injectable so tests can fix "current cycle" for omitted billing_cycle.
 * Pricing core stays stubbed in T2; implementation in T3.
 */
public class BillingCalculator {

    private final Clock clock;

    public BillingCalculator(Clock clock) {
        this.clock = clock;
    }

    public BillingCalculator() {
        this(Clock.systemUTC());
    }

    /**
     * Calculate billing for one cycle.
     *
     * @param input billing input (pre-parsed)
     * @return billing output with header + per-user results
     */
    public BillingOutput calculate(BillingInput input) {
        BillingHeader header = new BillingHeader();
        header.setSkipped_items(new ArrayList<>());
        header.setWarnings(new ArrayList<>());

        String cycle = input.getBilling_cycle();
        if (cycle == null || cycle.isBlank()) {
            cycle = YearMonth.now(clock).toString();
        }
        header.setBilling_cycle(cycle);

        // Step 1: User validation (DECISIONS #14)
        Map<String, User> validUsers = new HashMap<>();
        List<User> inputUsers = input.getUsers() != null ? input.getUsers() : Collections.emptyList();
        for (User user : inputUsers) {
            if (isValidUser(user)) {
                validUsers.put(user.getUser_id(), user);
            }
        }

        // Step 2: Event validation & duplicate policy (SPEC §4, DECISIONS #10, #11, #13)
        List<UsageEvent> inputEvents = input.getEvents() != null ? input.getEvents() : Collections.emptyList();
        List<UsageEvent> eventsToBill = new ArrayList<>();

        // Duplicate tracking per (user_id, event_id): payload set
        Map<EventKey, Set<EventPayload>> seenPayloads = new HashMap<>();

        for (UsageEvent event : inputEvents) {
            String errorReason = getErrorReason(event, validUsers);
            if (errorReason != null) {
                header.setError_count(header.getError_count() + 1);
                SkippedItem skipped = new SkippedItem();
                String eventId = event.getEvent_id();
                skipped.setEvent_id(eventId == null || eventId.isBlank() ? null : eventId);
                skipped.setReason(errorReason);
                header.getSkipped_items().add(skipped);
                continue;
            }

            // Valid event: apply duplicate policy (DECISIONS #10)
            EventKey key = new EventKey(event.getUser_id(), event.getEvent_id());
            EventPayload payload = new EventPayload(event.getTimestamp(), event.getDuration_minutes());

            Set<EventPayload> payloads = seenPayloads.get(key);
            if (payloads != null) {
                header.setWarning_count(header.getWarning_count() + 1);
                WarningItem warning = new WarningItem();
                warning.setEvent_id(event.getEvent_id());

                if (payloads.contains(payload)) {
                    // Identical payload: bill once, extra copies warning
                    warning.setReason("duplicate event_id, identical payload");
                    header.getWarnings().add(warning);
                    continue;
                } else {
                    // Different payload: bill both, later one warning
                    warning.setReason("duplicate event_id, different payload");
                    header.getWarnings().add(warning);
                    payloads.add(payload);
                    eventsToBill.add(event);
                }
            } else {
                Set<EventPayload> set = new HashSet<>();
                set.add(payload);
                seenPayloads.put(key, set);
                eventsToBill.add(event);
            }
        }

        // Step 3: Stubbed results for valid users (pricing implemented in T3)
        List<UserBill> results = new ArrayList<>();
        Set<String> addedUserIds = new HashSet<>();
        for (User user : inputUsers) {
            String uid = user.getUser_id();
            if (validUsers.containsKey(uid) && addedUserIds.add(uid)) {
                UserBill bill = new UserBill();
                bill.setUser_id(uid);
                bill.setTotal_cost(new BigDecimal("0.00"));

                TierBreakdown breakdown = new TierBreakdown();
                breakdown.setGrace(createTierLine("0.00"));
                breakdown.setTier_1(createTierLine("1.00"));
                breakdown.setTier_2(createTierLine("0.80"));
                breakdown.setTier_3(createTierLine("0.50"));
                bill.setTier_breakdown(breakdown);

                SurchargeInfo surcharge = new SurchargeInfo();
                surcharge.setPeak_hours(new BigDecimal("0.00"));
                surcharge.setPeak_rate(new BigDecimal("0.20"));
                surcharge.setPeak_surcharge_total(new BigDecimal("0.00"));
                bill.setApplied_surcharges(surcharge);

                results.add(bill);
            }
        }

        BillingOutput output = new BillingOutput();
        output.setHeader(header);
        output.setResults(results);
        return output;
    }

    private TierLine createTierLine(String rate) {
        TierLine line = new TierLine();
        line.setHours(new BigDecimal("0.00"));
        line.setRate(new BigDecimal(rate));
        line.setCost(new BigDecimal("0.00"));
        return line;
    }

    private boolean isValidUser(User user) {
        if (user.getUser_id() == null || user.getUser_id().isBlank()) {
            return false;
        }
        if (user.getIs_enterprise() == null) {
            return false;
        }
        if (user.getTimezone() == null || user.getTimezone().isBlank()) {
            return false;
        }
        try {
            ZoneId.of(user.getTimezone());
        } catch (DateTimeException e) {
            return false;
        }
        return true;
    }

    private String getErrorReason(UsageEvent event, Map<String, User> validUsers) {
        if (event.getEvent_id() == null || event.getEvent_id().isBlank()) {
            return "missing event_id";
        }
        if (event.getUser_id() == null || event.getUser_id().isBlank()) {
            return "missing user_id";
        }
        User user = validUsers.get(event.getUser_id());
        if (user == null) {
            return "unknown user_id";
        }
        if (event.getTimestamp() == null || event.getTimestamp().isBlank()) {
            return "missing timestamp";
        }

        ParsedTimestamp parsed = parseTimestamp(event.getTimestamp(), user.getTimezone());
        if (parsed.errorReason() != null) {
            return parsed.errorReason();
        }

        if (event.getDuration_minutes() == null) {
            return "missing duration_minutes";
        }
        if (event.getDuration_minutes() <= 0) {
            return "duration_minutes <= 0";
        }
        if (event.getDuration_minutes() % 15 != 0) {
            return "duration_minutes not a multiple of 15";
        }

        // Check UTC alignment: minute must be 00, 15, 30, 45, and second/nanos must be 0
        ZonedDateTime utc = parsed.instant().atZone(ZoneOffset.UTC);
        if (utc.getSecond() != 0 || utc.getNano() != 0 || utc.getMinute() % 15 != 0) {
            return "start not on a quarter hour";
        }

        return null;
    }

    private record ParsedTimestamp(Instant instant, String errorReason) {}

    private ParsedTimestamp parseTimestamp(String timestamp, String timezoneId) {
        // 1. Try OffsetDateTime (handles Z, +02:00, -05:00, etc.)
        try {
            return new ParsedTimestamp(OffsetDateTime.parse(timestamp).toInstant(), null);
        } catch (DateTimeParseException ignored) {
            // Not an offset-bearing timestamp; proceed to local time handling
        }

        // 2. Try LocalDateTime in user's timezone with strict DST handling (DECISIONS #13)
        try {
            LocalDateTime ldt = LocalDateTime.parse(timestamp);
            ZoneId zone = ZoneId.of(timezoneId);
            ZoneRules rules = zone.getRules();
            List<ZoneOffset> offsets = rules.getValidOffsets(ldt);
            if (offsets.isEmpty()) {
                return new ParsedTimestamp(null, "nonexistent local time");
            }
            if (offsets.size() > 1) {
                return new ParsedTimestamp(null, "ambiguous local time");
            }
            return new ParsedTimestamp(ldt.atOffset(offsets.get(0)).toInstant(), null);
        } catch (DateTimeParseException e) {
            return new ParsedTimestamp(null, "bad timestamp");
        }
    }

    private record EventKey(String userId, String eventId) {}

    private record EventPayload(String timestamp, Integer durationMinutes) {}
}
