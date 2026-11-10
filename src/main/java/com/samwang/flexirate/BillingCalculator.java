package com.samwang.flexirate;

import com.samwang.flexirate.model.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.time.zone.ZoneRules;
import java.util.*;

/**
 * Pure calculator core: BillingInput → BillingOutput.
 * Clock is injectable so tests can fix "current cycle" for omitted billing_cycle.
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
        YearMonth targetCycle;
        try {
            targetCycle = YearMonth.parse(cycle);
        } catch (DateTimeParseException e) {
            throw new FatalBillingException("Invalid billing_cycle: " + cycle);
        }

        // Step 1: User validation (DECISIONS #14)
        Map<String, User> validUsers = new HashMap<>();
        List<User> inputUsers = input.getUsers() != null ? input.getUsers() : Collections.emptyList();
        for (User user : inputUsers) {
            if (user == null) {
                continue;
            }
            if (isValidUser(user)) {
                // Keep first valid entry for each user_id
                validUsers.putIfAbsent(user.getUser_id(), user);
            }
        }

        // Step 2: Event validation & duplicate policy (SPEC §4, DECISIONS #10, #11, #13, #15)
        List<UsageEvent> inputEvents = input.getEvents() != null ? input.getEvents() : Collections.emptyList();

        // Duplicate tracking per (user_id, event_id): payload set
        Map<EventKey, Set<EventPayload>> seenPayloads = new HashMap<>();
        // Billed events per user in input order
        Map<String, List<ValidatedEvent>> billedEventsByUser = new HashMap<>();

        for (UsageEvent event : inputEvents) {
            if (event == null) {
                header.setError_count(header.getError_count() + 1);
                SkippedItem skippedNull = new SkippedItem();
                skippedNull.setEvent_id(null);
                skippedNull.setReason("missing event_id");
                header.getSkipped_items().add(skippedNull);
                continue;
            }
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
                }
            } else {
                Set<EventPayload> set = new HashSet<>();
                set.add(payload);
                seenPayloads.put(key, set);
            }

            // Accepted for billing: parse timestamp to UTC Instant
            User user = validUsers.get(event.getUser_id());
            ParsedTimestamp parsed = parseTimestamp(event.getTimestamp(), user.getTimezone());
            ValidatedEvent validEvent = new ValidatedEvent(event, parsed.instant(), event.getDuration_minutes());

            // Concurrent usage (DECISIONS #15): overlapping events raise no warning and are billed in full
            billedEventsByUser.computeIfAbsent(event.getUser_id(), k -> new ArrayList<>()).add(validEvent);
        }

        // Step 3: Per-user pricing (SPEC §3, DECISIONS #4, #5, #6, #9, #12, #17, #18)
        List<UserBill> results = new ArrayList<>();
        Set<String> addedUserIds = new HashSet<>();

        for (User user : inputUsers) {
            if (user == null) {
                continue;
            }
            String uid = user.getUser_id();
            if (validUsers.containsKey(uid) && addedUserIds.add(uid)) {
                User canonical = validUsers.get(uid);
                UserBill bill = calculateUserBill(canonical, billedEventsByUser.getOrDefault(uid, Collections.emptyList()), targetCycle);
                results.add(bill);
            }
        }

        BillingOutput output = new BillingOutput();
        output.setHeader(header);
        output.setResults(results);
        return output;
    }

    private UserBill calculateUserBill(User user, List<ValidatedEvent> events, YearMonth targetCycle) {
        // 1. Sort events chronologically by instant; ties broken by event_id
        List<ValidatedEvent> sortedEvents = new ArrayList<>(events);
        sortedEvents.sort(Comparator.comparing(ValidatedEvent::startInstant)
                .thenComparing(e -> e.rawEvent().getEvent_id()));

        // 2. Expand into 15-minute units and keep only units in target cycle (DECISIONS #12)
        List<BillingUnit> keptUnits = new ArrayList<>();
        // Clip events to the billing cycle before unit expansion for performance:
        // generate only units whose start falls inside [cycleStart, cycleEnd).
        Instant cycleStart = targetCycle.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant cycleEnd = targetCycle.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        for (ValidatedEvent ev : sortedEvents) {
            int totalUnits = ev.durationMinutes() / 15;
            // Index of the first unit starting at or after cycleStart. Event starts are
            // quarter-aligned and cycleStart is midnight UTC, so the division is exact;
            // negative for events starting inside the cycle, clamped to 0.
            long unitsBeforeCycle = Duration.between(ev.startInstant(), cycleStart).toMinutes() / 15;
            int firstKept = (int) Math.max(0, Math.min(totalUnits, unitsBeforeCycle));
            Instant firstKeptStart = ev.startInstant().plus(Duration.ofMinutes(firstKept * 15L));
            long unitsUntilCycleEnd = Duration.between(firstKeptStart, cycleEnd).toMinutes() / 15;
            int keptCount = (int) Math.max(0, Math.min(totalUnits - firstKept, unitsUntilCycleEnd));
            for (int u = firstKept; u < firstKept + keptCount; u++) {
                Instant unitStart = ev.startInstant().plus(Duration.ofMinutes(u * 15L));
                keptUnits.add(new BillingUnit(unitStart, ev.rawEvent().getEvent_id()));
            }
        }

        // Sort kept units chronologically
        keptUnits.sort(Comparator.comparing(BillingUnit::startInstant)
                .thenComparing(BillingUnit::eventId));

        // 3. Price kept units
        boolean isEnterprise = Boolean.TRUE.equals(user.getIs_enterprise());
        int graceUnitsRemaining = 40; // 10h grace = 40 units
        int progressionUnits = 0;

        int graceUnitsBilled = 0;
        int tier1UnitsBilled = 0;
        int tier2UnitsBilled = 0;
        int tier3UnitsBilled = 0;

        BigDecimal tier1Cost = BigDecimal.ZERO;
        BigDecimal tier2Cost = BigDecimal.ZERO;
        BigDecimal tier3Cost = BigDecimal.ZERO;

        int peakUnits = 0;
        BigDecimal peakSurchargeTotal = BigDecimal.ZERO;

        for (BillingUnit unit : keptUnits) {
            LocalTime time = unit.startInstant().atZone(ZoneOffset.UTC).toLocalTime();
            boolean isPeak = !time.isBefore(LocalTime.of(9, 0)) && time.isBefore(LocalTime.of(17, 0));

            if (graceUnitsRemaining > 0) {
                graceUnitsRemaining--;
                graceUnitsBilled++;
                if (isEnterprise) {
                    progressionUnits++;
                }
                // Grace units bill $0.00 and never incur peak surcharge
            } else {
                BigDecimal unitBaseCost;
                if (progressionUnits < 400) {
                    // 0–100h: Tier 1 ($1.00/h -> $0.25/unit)
                    unitBaseCost = new BigDecimal("0.25");
                    tier1Cost = tier1Cost.add(unitBaseCost);
                    tier1UnitsBilled++;
                } else if (progressionUnits < 2000) {
                    // 100–500h: Tier 2 ($0.80/h -> $0.20/unit)
                    unitBaseCost = new BigDecimal("0.20");
                    tier2Cost = tier2Cost.add(unitBaseCost);
                    tier2UnitsBilled++;
                } else {
                    // 500h+: Tier 3 ($0.50/h -> $0.125/unit)
                    unitBaseCost = new BigDecimal("0.125");
                    tier3Cost = tier3Cost.add(unitBaseCost);
                    tier3UnitsBilled++;
                }

                if (isPeak) {
                    BigDecimal surchargePortion = unitBaseCost.multiply(new BigDecimal("0.20"));
                    peakSurchargeTotal = peakSurchargeTotal.add(surchargePortion);
                    peakUnits++;
                }

                progressionUnits++;
            }
        }

        // 4. Assemble tier breakdown and surcharge (DECISIONS #9, #18, SPEC §5)
        BigDecimal graceHours = BigDecimal.valueOf(graceUnitsBilled).multiply(new BigDecimal("0.25")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tier1Hours = BigDecimal.valueOf(tier1UnitsBilled).multiply(new BigDecimal("0.25")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tier2Hours = BigDecimal.valueOf(tier2UnitsBilled).multiply(new BigDecimal("0.25")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tier3Hours = BigDecimal.valueOf(tier3UnitsBilled).multiply(new BigDecimal("0.25")).setScale(2, RoundingMode.HALF_UP);

        BigDecimal peakHours = BigDecimal.valueOf(peakUnits).multiply(new BigDecimal("0.25")).setScale(2, RoundingMode.HALF_UP);

        TierLine graceLine = new TierLine();
        graceLine.setHours(graceHours);
        graceLine.setRate(new BigDecimal("0.00"));
        graceLine.setCost(new BigDecimal("0.00"));

        TierLine tier1Line = new TierLine();
        tier1Line.setHours(tier1Hours);
        tier1Line.setRate(new BigDecimal("1.00"));
        tier1Line.setCost(tier1Cost.setScale(2, RoundingMode.HALF_UP));

        TierLine tier2Line = new TierLine();
        tier2Line.setHours(tier2Hours);
        tier2Line.setRate(new BigDecimal("0.80"));
        tier2Line.setCost(tier2Cost.setScale(2, RoundingMode.HALF_UP));

        TierLine tier3Line = new TierLine();
        tier3Line.setHours(tier3Hours);
        tier3Line.setRate(new BigDecimal("0.50"));
        tier3Line.setCost(tier3Cost.setScale(2, RoundingMode.HALF_UP));

        TierBreakdown breakdown = new TierBreakdown();
        breakdown.setGrace(graceLine);
        breakdown.setTier_1(tier1Line);
        breakdown.setTier_2(tier2Line);
        breakdown.setTier_3(tier3Line);

        SurchargeInfo surcharge = new SurchargeInfo();
        surcharge.setPeak_hours(peakHours);
        surcharge.setPeak_rate(new BigDecimal("0.20"));
        surcharge.setPeak_surcharge_total(peakSurchargeTotal.setScale(2, RoundingMode.HALF_UP));

        BigDecimal totalCost = graceLine.getCost()
                .add(tier1Line.getCost())
                .add(tier2Line.getCost())
                .add(tier3Line.getCost())
                .add(surcharge.getPeak_surcharge_total());

        UserBill bill = new UserBill();
        bill.setUser_id(user.getUser_id());
        bill.setTotal_cost(totalCost);
        bill.setTier_breakdown(breakdown);
        bill.setApplied_surcharges(surcharge);

        return bill;
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

    private record ValidatedEvent(UsageEvent rawEvent, Instant startInstant, int durationMinutes) {}

    private record BillingUnit(Instant startInstant, String eventId) {}
}
