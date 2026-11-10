package com.samwang.flexirate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Robustness tests added after the 2026-11-10 critical re-review.
 * The frozen suites (FlexiRateAcceptanceTest, ParsingAndValidationFatalTest,
 * MainCliTest) are untouched; this file locks the review's findings:
 * malformed billing_cycle, item-level duration type errors, null items,
 * duplicate user_id handling, peak surcharges in tier_2/tier_3, and
 * giant-duration behaviour under cycle clipping.
 */
class RobustnessTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);

    private FlexiRateCalculatorApi api;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        api    = new FlexiRateCalculatorApi(FIXED_CLOCK);
        mapper = new ObjectMapper();
    }

    private JsonNode parse(String inputJson) throws Exception {
        return mapper.readTree(api.calculate(inputJson));
    }

    private JsonNode hdr(JsonNode root)        { return root.path("header"); }
    private JsonNode res(JsonNode root, int i) { return root.path("results").get(i); }
    private JsonNode tb (JsonNode root, int i) { return res(root, i).path("tier_breakdown"); }
    private JsonNode sur(JsonNode root, int i) { return res(root, i).path("applied_surcharges"); }

    private static String event(String id, String userId, String timestamp, String durationJson) {
        return "{\"event_id\":\"" + id + "\",\"user_id\":\"" + userId
                + "\",\"timestamp\":\"" + timestamp + "\",\"duration_minutes\":" + durationJson + "}";
    }

    private static String doc(String cycle, String users, String events) {
        return "{\"billing_cycle\":\"" + cycle + "\",\"users\":[" + users
                + "],\"events\":[" + events + "]}";
    }

    private static final String U1_NON_ENT_UTC =
            "{\"user_id\":\"u1\",\"is_enterprise\":false,\"timezone\":\"UTC\"}";

    /** 110 off-peak hours for u1: Nov 2-7 17:00Z x960 + Nov 8 17:00Z x840 = 6600 min. */
    private static String pack110h() {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int day = 2; day <= 7; day++) {
            if (n > 0) sb.append(',');
            sb.append(event("p" + (++n), "u1", String.format("2026-11-%02dT17:00:00Z", day), "960"));
        }
        sb.append(',').append(event("p" + (++n), "u1", "2026-11-08T17:00:00Z", "840"));
        return sb.toString();
    }

    /** 510 off-peak hours for u1 in two parallel streams = 30600 min, all starting 17:00Z. */
    private static String pack510h() {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int day = 1; day <= 16; day++) {
            if (n > 0) sb.append(',');
            sb.append(event("a" + (++n), "u1", String.format("2026-11-%02dT17:00:00Z", day), "960"));
        }
        for (int day = 1; day <= 15; day++) {
            sb.append(',').append(event("b" + (++n), "u1", String.format("2026-11-%02dT17:00:00Z", day), "960"));
        }
        sb.append(',').append(event("b" + (++n), "u1", "2026-11-16T17:00:00Z", "840"));
        return sb.toString();
    }

    // ── F1: malformed billing_cycle is a clean FATAL ─────────────────────

    @Test
    void malformedBillingCycle_throwsFatal() {
        assertThrows(FatalBillingException.class,
                () -> api.calculate(doc("2026-13", U1_NON_ENT_UTC, "")));
        assertThrows(FatalBillingException.class,
                () -> api.calculate(doc("not-a-cycle", U1_NON_ENT_UTC, "")));
    }

    // ── F2: bad duration values skip only their own event ────────────────

    @Test
    void fractionalDuration_skipsOnlyThatEvent() throws Exception {
        String events = event("eBad", "u1", "2026-11-02T17:00:00Z", "60.5")
                + "," + event("eGood", "u1", "2026-11-02T17:00:00Z", "660");
        JsonNode root = parse(doc("2026-11", U1_NON_ENT_UTC, events));
        assertEquals(1, hdr(root).path("error_count").asInt());
        assertEquals("missing or invalid duration_minutes",
                hdr(root).path("skipped_items").get(0).path("reason").asText());
        assertEquals(1, root.path("results").size());
        // eGood: 10h grace + 1h tier_1, all off-peak
        assertEquals(1.00, res(root, 0).path("total_cost").asDouble(), 0.001);
    }

    @Test
    void stringDuration_skipsOnlyThatEvent() throws Exception {
        String events = event("e1", "u1", "2026-11-02T17:00:00Z", "\"sixty\"");
        JsonNode root = parse(doc("2026-11", U1_NON_ENT_UTC, events));
        assertEquals(1, hdr(root).path("error_count").asInt());
        assertEquals(0.00, res(root, 0).path("total_cost").asDouble(), 0.001);
    }

    @Test
    void hugeDurationValue_skipsOnlyThatEvent() throws Exception {
        String events = event("e1", "u1", "2026-11-02T17:00:00Z", "3000000000");
        JsonNode root = parse(doc("2026-11", U1_NON_ENT_UTC, events));
        assertEquals(1, hdr(root).path("error_count").asInt());
        assertEquals(0.00, res(root, 0).path("total_cost").asDouble(), 0.001);
    }

    // ── F3: null items cannot crash the run ──────────────────────────────

    @Test
    void nullEventElement_countedAsSkipped() throws Exception {
        String events = "null," + event("e1", "u1", "2026-11-02T17:00:00Z", "15");
        JsonNode root = parse(doc("2026-11", U1_NON_ENT_UTC, events));
        assertEquals(1, hdr(root).path("error_count").asInt());
        assertTrue(hdr(root).path("skipped_items").get(0).path("event_id").isNull());
        assertEquals(0.25, tb(root, 0).path("grace").path("hours").asDouble(), 0.001);
    }

    @Test
    void nullUserElement_ignored() throws Exception {
        JsonNode root = parse(doc("2026-11", "null," + U1_NON_ENT_UTC, ""));
        assertEquals(1, root.path("results").size());
        assertEquals("u1", res(root, 0).path("user_id").asText());
    }

    // ── F5: duplicate user_id — first valid entry wins ───────────────────

    @Test
    void duplicateUserId_firstEntryTimezoneWins() throws Exception {
        String users = U1_NON_ENT_UTC
                + ",{\"user_id\":\"u1\",\"is_enterprise\":false,\"timezone\":\"America/New_York\"}";
        String events = event("e1", "u1", "2026-10-31T20:30", "60");
        JsonNode root = parse(doc("2026-10", users, events));
        assertEquals(1, root.path("results").size());
        // First entry (UTC): the event falls in October and consumes 1h of grace.
        // Had the second entry won, it would be Nov 1 00:30Z and October grace would be 0.
        assertEquals(1.00, tb(root, 0).path("grace").path("hours").asDouble(), 0.001);
    }

    @Test
    void duplicateUserId_firstEntryEnterpriseWins() throws Exception {
        String users = "{\"user_id\":\"u1\",\"is_enterprise\":true,\"timezone\":\"UTC\"}"
                + ",{\"user_id\":\"u1\",\"is_enterprise\":false,\"timezone\":\"UTC\"}";
        JsonNode root = parse(doc("2026-11", users, pack110h()));
        assertEquals(1, root.path("results").size());
        // Enterprise pricing for 110h off-peak (Example B): $98, not $100.
        assertEquals(98.00, res(root, 0).path("total_cost").asDouble(), 0.001);
    }

    // ── Peak surcharge beyond tier_1 (was untested) ──────────────────────

    @Test
    void tier2PeakSurcharge() throws Exception {
        String events = pack110h() + "," + event("peak1", "u1", "2026-11-09T10:00:00Z", "60");
        JsonNode root = parse(doc("2026-11", U1_NON_ENT_UTC, events));
        assertEquals(0.80, tb(root, 0).path("tier_2").path("cost").asDouble(), 0.001);
        assertEquals(1.00, sur(root, 0).path("peak_hours").asDouble(), 0.001);
        assertEquals(0.16, sur(root, 0).path("peak_surcharge_total").asDouble(), 0.001);
        assertEquals(100.96, res(root, 0).path("total_cost").asDouble(), 0.001);
    }

    @Test
    void tier3PeakSurcharge() throws Exception {
        String events = pack510h() + "," + event("peak1", "u1", "2026-11-22T10:00:00Z", "15");
        JsonNode root = parse(doc("2026-11", U1_NON_ENT_UTC, events));
        assertEquals(0.25, tb(root, 0).path("tier_3").path("hours").asDouble(), 0.001);
        assertEquals(0.13, tb(root, 0).path("tier_3").path("cost").asDouble(), 0.001);
        assertEquals(0.25, sur(root, 0).path("peak_hours").asDouble(), 0.001);
        assertEquals(0.03, sur(root, 0).path("peak_surcharge_total").asDouble(), 0.001);
        assertEquals(420.16, res(root, 0).path("total_cost").asDouble(), 0.001);
    }

    // ── Empty input ──────────────────────────────────────────────────────

    @Test
    void emptyUsersAndEvents() throws Exception {
        JsonNode root = parse(doc("2026-11", "", ""));
        assertEquals(0, hdr(root).path("error_count").asInt());
        assertEquals(0, hdr(root).path("warning_count").asInt());
        assertEquals(0, root.path("results").size());
    }

    // ── Giant durations under cycle clipping ─────────────────────────────

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void giantOutOfCycleDuration_completes() throws Exception {
        // 2,147,483,640 minutes (a multiple of 15) starting after the billed cycle.
        String events = event("big", "u1", "2027-06-01T00:00:00Z", "2147483640");
        JsonNode root = parse(doc("2026-11", U1_NON_ENT_UTC, events));
        assertEquals(0, hdr(root).path("error_count").asInt());
        assertEquals(0.00, res(root, 0).path("total_cost").asDouble(), 0.001);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void giantInCycleDuration_hoursInvariant() throws Exception {
        // Same giant duration starting at the cycle's first instant: clipping
        // keeps exactly November's 720 hours of units.
        String events = event("big", "u1", "2026-11-01T00:00:00Z", "2147483640");
        JsonNode root = parse(doc("2026-11", U1_NON_ENT_UTC, events));
        double hours = tb(root, 0).path("grace").path("hours").asDouble()
                + tb(root, 0).path("tier_1").path("hours").asDouble()
                + tb(root, 0).path("tier_2").path("hours").asDouble()
                + tb(root, 0).path("tier_3").path("hours").asDouble();
        assertEquals(720.00, hours, 0.001);
    }
}
