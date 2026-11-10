package com.samwang.flexirate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Frozen acceptance tests for the Flexi-Rate Usage Calculator.
 *
 * Written in T1 — no implementation exists yet; all tests are expected to fail
 * with UnsupportedOperationException until T2/T3 implement the calculator.
 *
 * Every SPEC §6 example (A–L) is covered, plus every item in the TASKS.md T1
 * coverage list. Numbers come directly from the SPEC; none are guesses.
 *
 * Fixed clock: 2026-11-09T12:00:00Z → "current cycle" = 2026-11.
 */
class FlexiRateAcceptanceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);

    private FlexiRateCalculatorApi api;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        api    = new FlexiRateCalculatorApi(FIXED_CLOCK);
        mapper = new ObjectMapper();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Parse the calculator output JSON. */
    private JsonNode parse(String inputJson) throws Exception {
        return mapper.readTree(api.calculate(inputJson));
    }

    private JsonNode hdr(JsonNode root)       { return root.path("header"); }
    private JsonNode res(JsonNode root, int i) { return root.path("results").get(i); }
    private JsonNode tb (JsonNode root, int i) { return res(root, i).path("tier_breakdown"); }
    private JsonNode sur(JsonNode root, int i) { return res(root, i).path("applied_surcharges"); }

    // ═══════════════════════════════════════════════════════════════════
    // ── PRICING TESTS ──────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    /**
     * SPEC §6 Example A — Non-enterprise, 110 h, all off-peak → $100.00.
     * grace=10h($0), tier_1=100h×$1=$100, tier_2=0h, tier_3=0h.
     */
    @Test
    void exampleA_nonEnterprise_110h_offPeak_total100() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"e3","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"e4","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"e5","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"e6","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"e7","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":840}
                  ]
                }""";
        JsonNode root = parse(json);

        assertEquals(0,      hdr(root).path("error_count").asInt());
        assertEquals(100.00, res(root, 0).path("total_cost").asDouble(),            0.001);
        assertEquals(10.00,  tb(root, 0).path("grace").path("hours").asDouble(),    0.001);
        assertEquals(0.00,   tb(root, 0).path("grace").path("cost").asDouble(),     0.001);
        assertEquals(100.00, tb(root, 0).path("tier_1").path("hours").asDouble(),   0.001);
        assertEquals(1.00,   tb(root, 0).path("tier_1").path("rate").asDouble(),    0.001);
        assertEquals(100.00, tb(root, 0).path("tier_1").path("cost").asDouble(),    0.001);
        assertEquals(0.00,   tb(root, 0).path("tier_2").path("hours").asDouble(),   0.001);
        assertEquals(0.00,   tb(root, 0).path("tier_3").path("hours").asDouble(),   0.001);
        assertEquals(0.00,   sur(root, 0).path("peak_hours").asDouble(),            0.001);
        assertEquals(0.00,   sur(root, 0).path("peak_surcharge_total").asDouble(),  0.001);
    }

    /**
     * SPEC §6 Example B — Enterprise, 110 h, all off-peak → $90.00.
     * Enterprise grace counts toward progression → only 90h in tier_1.
     */
    @Test
    void exampleB_enterprise_110h_offPeak_total90() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":true,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"e3","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"e4","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"e5","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"e6","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"e7","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":840}
                  ]
                }""";
        JsonNode root = parse(json);

        assertEquals(0,     hdr(root).path("error_count").asInt());
        assertEquals(90.00, res(root, 0).path("total_cost").asDouble(),           0.001);
        assertEquals(10.00, tb(root, 0).path("grace").path("hours").asDouble(),   0.001);
        assertEquals(0.00,  tb(root, 0).path("grace").path("cost").asDouble(),    0.001);
        // Enterprise: grace counts toward progression → 90h remain for tier_1
        assertEquals(90.00, tb(root, 0).path("tier_1").path("hours").asDouble(),  0.001);
        assertEquals(90.00, tb(root, 0).path("tier_1").path("cost").asDouble(),   0.001);
        assertEquals(0.00,  tb(root, 0).path("tier_2").path("hours").asDouble(),  0.001);
        assertEquals(0.00,  sur(root, 0).path("peak_hours").asDouble(),           0.001);
        assertEquals(0.00,  sur(root, 0).path("peak_surcharge_total").asDouble(), 0.001);
    }

    /**
     * SPEC §6 Example C — Non-enterprise, 20 h all peak → $12.00, surcharge $2.00.
     * Event starts 09:00Z (20h = 1200 min). First 10h grace ($0, non-enterprise
     * grace does NOT count toward progression). Remaining 10h: tier_1 × peak × 1.2.
     * peak_hours = 10 (billed peak units only, NOT grace units).
     */
    @Test
    void exampleC_nonEnterprise_20h_allPeak_total12() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-03T09:00:00Z","duration_minutes":480},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-04T09:00:00Z","duration_minutes":480},
                    {"event_id":"e3","user_id":"u1","timestamp":"2026-11-05T09:00:00Z","duration_minutes":240}
                  ]
                }""";
        JsonNode root = parse(json);

        assertEquals(0,     hdr(root).path("error_count").asInt());
        assertEquals(12.00, res(root, 0).path("total_cost").asDouble(),           0.001);
        assertEquals(10.00, tb(root, 0).path("grace").path("hours").asDouble(),   0.001);
        assertEquals(0.00,  tb(root, 0).path("grace").path("cost").asDouble(),    0.001);
        assertEquals(10.00, tb(root, 0).path("tier_1").path("hours").asDouble(),  0.001);
        assertEquals(10.00, tb(root, 0).path("tier_1").path("cost").asDouble(),   0.001);
        assertEquals(0.00,  tb(root, 0).path("tier_2").path("hours").asDouble(),  0.001);
        assertEquals(10.00, sur(root, 0).path("peak_hours").asDouble(),           0.001);
        assertEquals(2.00,  sur(root, 0).path("peak_surcharge_total").asDouble(), 0.001);
    }

    /**
     * SPEC §6 Example D — Enterprise, 510 h, off-peak → $415.00.
     * grace=10h (counts toward progression) + tier_1=90h×$1=$90
     * + tier_2=400h×$0.80=$320 + tier_3=10h×$0.50=$5.
     */
    @Test
    void exampleD_enterprise_510h_offPeak_total415() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":true,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"b0","user_id":"u1","timestamp":"2026-11-01T00:00:00Z","duration_minutes":540},
                    {"event_id":"b1","user_id":"u1","timestamp":"2026-11-01T17:00:00Z","duration_minutes":960},
                    {"event_id":"b2","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"b3","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"b4","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"b5","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"b6","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"b7","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"b8","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":960},
                    {"event_id":"b9","user_id":"u1","timestamp":"2026-11-09T17:00:00Z","duration_minutes":960},
                    {"event_id":"b10","user_id":"u1","timestamp":"2026-11-10T17:00:00Z","duration_minutes":960},
                    {"event_id":"b11","user_id":"u1","timestamp":"2026-11-11T17:00:00Z","duration_minutes":960},
                    {"event_id":"b12","user_id":"u1","timestamp":"2026-11-12T17:00:00Z","duration_minutes":960},
                    {"event_id":"b13","user_id":"u1","timestamp":"2026-11-13T17:00:00Z","duration_minutes":960},
                    {"event_id":"b14","user_id":"u1","timestamp":"2026-11-14T17:00:00Z","duration_minutes":960},
                    {"event_id":"b15","user_id":"u1","timestamp":"2026-11-15T17:00:00Z","duration_minutes":960},
                    {"event_id":"b16","user_id":"u1","timestamp":"2026-11-16T17:00:00Z","duration_minutes":960},
                    {"event_id":"b17","user_id":"u1","timestamp":"2026-11-17T17:00:00Z","duration_minutes":960},
                    {"event_id":"b18","user_id":"u1","timestamp":"2026-11-18T17:00:00Z","duration_minutes":960},
                    {"event_id":"b19","user_id":"u1","timestamp":"2026-11-19T17:00:00Z","duration_minutes":960},
                    {"event_id":"b20","user_id":"u1","timestamp":"2026-11-20T17:00:00Z","duration_minutes":960},
                    {"event_id":"b21","user_id":"u1","timestamp":"2026-11-21T17:00:00Z","duration_minutes":960},
                    {"event_id":"b22","user_id":"u1","timestamp":"2026-11-22T17:00:00Z","duration_minutes":960},
                    {"event_id":"b23","user_id":"u1","timestamp":"2026-11-23T17:00:00Z","duration_minutes":960},
                    {"event_id":"b24","user_id":"u1","timestamp":"2026-11-24T17:00:00Z","duration_minutes":960},
                    {"event_id":"b25","user_id":"u1","timestamp":"2026-11-25T17:00:00Z","duration_minutes":960},
                    {"event_id":"b26","user_id":"u1","timestamp":"2026-11-26T17:00:00Z","duration_minutes":960},
                    {"event_id":"b27","user_id":"u1","timestamp":"2026-11-27T17:00:00Z","duration_minutes":960},
                    {"event_id":"b28","user_id":"u1","timestamp":"2026-11-28T17:00:00Z","duration_minutes":960},
                    {"event_id":"b29","user_id":"u1","timestamp":"2026-11-29T17:00:00Z","duration_minutes":960},
                    {"event_id":"b30","user_id":"u1","timestamp":"2026-11-30T17:00:00Z","duration_minutes":420},
                    {"event_id":"p1","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"p2","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":840}
                  ]
                }""";
        JsonNode root = parse(json);

        assertEquals(0,      hdr(root).path("error_count").asInt());
        assertEquals(415.00, res(root, 0).path("total_cost").asDouble(),           0.001);
        assertEquals(10.00,  tb(root, 0).path("grace").path("hours").asDouble(),   0.001);
        assertEquals(0.00,   tb(root, 0).path("grace").path("cost").asDouble(),    0.001);
        assertEquals(90.00,  tb(root, 0).path("tier_1").path("hours").asDouble(),  0.001);
        assertEquals(90.00,  tb(root, 0).path("tier_1").path("cost").asDouble(),   0.001);
        assertEquals(400.00, tb(root, 0).path("tier_2").path("hours").asDouble(),  0.001);
        assertEquals(320.00, tb(root, 0).path("tier_2").path("cost").asDouble(),   0.001);
        assertEquals(10.00,  tb(root, 0).path("tier_3").path("hours").asDouble(),  0.001);
        assertEquals(5.00,   tb(root, 0).path("tier_3").path("cost").asDouble(),   0.001);
        assertEquals(0.00,   sur(root, 0).path("peak_surcharge_total").asDouble(), 0.001);
    }

    /**
     * SPEC §6 Example E — Non-enterprise, 510 h, off-peak → $420.00.
     * Non-enterprise: grace does NOT count toward progression.
     * 510h = 10h grace + 100h tier_1 + 400h tier_2; tier_3 = 0h.
     */
    @Test
    void exampleE_nonEnterprise_510h_offPeak_total420() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"b0","user_id":"u1","timestamp":"2026-11-01T00:00:00Z","duration_minutes":540},
                    {"event_id":"b1","user_id":"u1","timestamp":"2026-11-01T17:00:00Z","duration_minutes":960},
                    {"event_id":"b2","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"b3","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"b4","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"b5","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"b6","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"b7","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"b8","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":960},
                    {"event_id":"b9","user_id":"u1","timestamp":"2026-11-09T17:00:00Z","duration_minutes":960},
                    {"event_id":"b10","user_id":"u1","timestamp":"2026-11-10T17:00:00Z","duration_minutes":960},
                    {"event_id":"b11","user_id":"u1","timestamp":"2026-11-11T17:00:00Z","duration_minutes":960},
                    {"event_id":"b12","user_id":"u1","timestamp":"2026-11-12T17:00:00Z","duration_minutes":960},
                    {"event_id":"b13","user_id":"u1","timestamp":"2026-11-13T17:00:00Z","duration_minutes":960},
                    {"event_id":"b14","user_id":"u1","timestamp":"2026-11-14T17:00:00Z","duration_minutes":960},
                    {"event_id":"b15","user_id":"u1","timestamp":"2026-11-15T17:00:00Z","duration_minutes":960},
                    {"event_id":"b16","user_id":"u1","timestamp":"2026-11-16T17:00:00Z","duration_minutes":960},
                    {"event_id":"b17","user_id":"u1","timestamp":"2026-11-17T17:00:00Z","duration_minutes":960},
                    {"event_id":"b18","user_id":"u1","timestamp":"2026-11-18T17:00:00Z","duration_minutes":960},
                    {"event_id":"b19","user_id":"u1","timestamp":"2026-11-19T17:00:00Z","duration_minutes":960},
                    {"event_id":"b20","user_id":"u1","timestamp":"2026-11-20T17:00:00Z","duration_minutes":960},
                    {"event_id":"b21","user_id":"u1","timestamp":"2026-11-21T17:00:00Z","duration_minutes":960},
                    {"event_id":"b22","user_id":"u1","timestamp":"2026-11-22T17:00:00Z","duration_minutes":960},
                    {"event_id":"b23","user_id":"u1","timestamp":"2026-11-23T17:00:00Z","duration_minutes":960},
                    {"event_id":"b24","user_id":"u1","timestamp":"2026-11-24T17:00:00Z","duration_minutes":960},
                    {"event_id":"b25","user_id":"u1","timestamp":"2026-11-25T17:00:00Z","duration_minutes":960},
                    {"event_id":"b26","user_id":"u1","timestamp":"2026-11-26T17:00:00Z","duration_minutes":960},
                    {"event_id":"b27","user_id":"u1","timestamp":"2026-11-27T17:00:00Z","duration_minutes":960},
                    {"event_id":"b28","user_id":"u1","timestamp":"2026-11-28T17:00:00Z","duration_minutes":960},
                    {"event_id":"b29","user_id":"u1","timestamp":"2026-11-29T17:00:00Z","duration_minutes":960},
                    {"event_id":"b30","user_id":"u1","timestamp":"2026-11-30T17:00:00Z","duration_minutes":420},
                    {"event_id":"p1","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"p2","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":840}
                  ]
                }""";
        JsonNode root = parse(json);

        assertEquals(0,      hdr(root).path("error_count").asInt());
        assertEquals(420.00, res(root, 0).path("total_cost").asDouble(),           0.001);
        assertEquals(10.00,  tb(root, 0).path("grace").path("hours").asDouble(),   0.001);
        assertEquals(100.00, tb(root, 0).path("tier_1").path("hours").asDouble(),  0.001);
        assertEquals(100.00, tb(root, 0).path("tier_1").path("cost").asDouble(),   0.001);
        assertEquals(400.00, tb(root, 0).path("tier_2").path("hours").asDouble(),  0.001);
        assertEquals(320.00, tb(root, 0).path("tier_2").path("cost").asDouble(),   0.001);
        assertEquals(0.00,   tb(root, 0).path("tier_3").path("hours").asDouble(),  0.001);
        assertEquals(0.00,   sur(root, 0).path("peak_surcharge_total").asDouble(), 0.001);
    }

    /**
     * SPEC §6 Example F — Peak boundary split at 16:30Z, 60 min, tier_1.
     * First event (600 min off-peak) exhausts grace.
     * Second event 16:30Z/60min: units at 16:30(peak), 16:45(peak), 17:00(off), 17:15(off).
     * Cost = 2×(0.25×$1×1.2) + 2×(0.25×$1) = $0.60 + $0.50 = $1.10.
     * peak_hours=0.50h, peak_surcharge_total=$0.10.
     */
    @Test
    void exampleF_peakBoundarySplit_total1_10() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T00:00:00Z","duration_minutes":600},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-02T16:30:00Z","duration_minutes":60}
                  ]
                }""";
        JsonNode root = parse(json);

        assertEquals(0,    hdr(root).path("error_count").asInt());
        assertEquals(1.10, res(root, 0).path("total_cost").asDouble(),           0.001);
        assertEquals(10.00, tb(root, 0).path("grace").path("hours").asDouble(),  0.001);
        // All 4 units from e2 land in tier_1 (progression only 10h so far)
        assertEquals(1.00, tb(root, 0).path("tier_1").path("hours").asDouble(),  0.001);
        assertEquals(1.00, tb(root, 0).path("tier_1").path("cost").asDouble(),   0.001);
        assertEquals(0.50, sur(root, 0).path("peak_hours").asDouble(),           0.001);
        assertEquals(0.10, sur(root, 0).path("peak_surcharge_total").asDouble(), 0.001);
    }

    // ── Tier boundary tests ────────────────────────────────────────────

    /** Non-enterprise 111 h off-peak: crosses into tier_2 by 1 h → $100.80. */
    @Test
    void tierBoundary_nonEnterprise_111h_offPeak_total100_80() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"e3","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"e4","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"e5","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"e6","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"e7","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":900}
                  ]
                }""";
        // 111h = 10h grace + 100h tier_1 + 1h tier_2
        JsonNode root = parse(json);
        assertEquals(100.80, res(root, 0).path("total_cost").asDouble(),          0.001);
        assertEquals(10.00,  tb(root, 0).path("grace").path("hours").asDouble(),  0.001);
        assertEquals(100.00, tb(root, 0).path("tier_1").path("hours").asDouble(), 0.001);
        assertEquals(100.00, tb(root, 0).path("tier_1").path("cost").asDouble(),  0.001);
        assertEquals(1.00,   tb(root, 0).path("tier_2").path("hours").asDouble(), 0.001);
        assertEquals(0.80,   tb(root, 0).path("tier_2").path("cost").asDouble(),  0.001);
        assertEquals(0.00,   tb(root, 0).path("tier_3").path("hours").asDouble(), 0.001);
    }

    /** Non-enterprise 511 h off-peak: crosses into tier_3 by 1 h → $420.50. */
    @Test
    void tierBoundary_nonEnterprise_511h_offPeak_total420_50() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"b0","user_id":"u1","timestamp":"2026-11-01T00:00:00Z","duration_minutes":540},
                    {"event_id":"b1","user_id":"u1","timestamp":"2026-11-01T17:00:00Z","duration_minutes":960},
                    {"event_id":"b2","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"b3","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"b4","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"b5","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"b6","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"b7","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"b8","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":960},
                    {"event_id":"b9","user_id":"u1","timestamp":"2026-11-09T17:00:00Z","duration_minutes":960},
                    {"event_id":"b10","user_id":"u1","timestamp":"2026-11-10T17:00:00Z","duration_minutes":960},
                    {"event_id":"b11","user_id":"u1","timestamp":"2026-11-11T17:00:00Z","duration_minutes":960},
                    {"event_id":"b12","user_id":"u1","timestamp":"2026-11-12T17:00:00Z","duration_minutes":960},
                    {"event_id":"b13","user_id":"u1","timestamp":"2026-11-13T17:00:00Z","duration_minutes":960},
                    {"event_id":"b14","user_id":"u1","timestamp":"2026-11-14T17:00:00Z","duration_minutes":960},
                    {"event_id":"b15","user_id":"u1","timestamp":"2026-11-15T17:00:00Z","duration_minutes":960},
                    {"event_id":"b16","user_id":"u1","timestamp":"2026-11-16T17:00:00Z","duration_minutes":960},
                    {"event_id":"b17","user_id":"u1","timestamp":"2026-11-17T17:00:00Z","duration_minutes":960},
                    {"event_id":"b18","user_id":"u1","timestamp":"2026-11-18T17:00:00Z","duration_minutes":960},
                    {"event_id":"b19","user_id":"u1","timestamp":"2026-11-19T17:00:00Z","duration_minutes":960},
                    {"event_id":"b20","user_id":"u1","timestamp":"2026-11-20T17:00:00Z","duration_minutes":960},
                    {"event_id":"b21","user_id":"u1","timestamp":"2026-11-21T17:00:00Z","duration_minutes":960},
                    {"event_id":"b22","user_id":"u1","timestamp":"2026-11-22T17:00:00Z","duration_minutes":960},
                    {"event_id":"b23","user_id":"u1","timestamp":"2026-11-23T17:00:00Z","duration_minutes":960},
                    {"event_id":"b24","user_id":"u1","timestamp":"2026-11-24T17:00:00Z","duration_minutes":960},
                    {"event_id":"b25","user_id":"u1","timestamp":"2026-11-25T17:00:00Z","duration_minutes":960},
                    {"event_id":"b26","user_id":"u1","timestamp":"2026-11-26T17:00:00Z","duration_minutes":960},
                    {"event_id":"b27","user_id":"u1","timestamp":"2026-11-27T17:00:00Z","duration_minutes":960},
                    {"event_id":"b28","user_id":"u1","timestamp":"2026-11-28T17:00:00Z","duration_minutes":960},
                    {"event_id":"b29","user_id":"u1","timestamp":"2026-11-29T17:00:00Z","duration_minutes":960},
                    {"event_id":"b30","user_id":"u1","timestamp":"2026-11-30T17:00:00Z","duration_minutes":420},
                    {"event_id":"p1","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"p2","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":840},
                    {"event_id":"p3","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":60}
                  ]
                }""";
        // 511h = 10h grace + 100h tier_1 + 400h tier_2 + 1h tier_3
        JsonNode root = parse(json);
        assertEquals(420.50, res(root, 0).path("total_cost").asDouble(),           0.001);
        assertEquals(100.00, tb(root, 0).path("tier_1").path("hours").asDouble(),  0.001);
        assertEquals(100.00, tb(root, 0).path("tier_1").path("cost").asDouble(),   0.001);
        assertEquals(400.00, tb(root, 0).path("tier_2").path("hours").asDouble(),  0.001);
        assertEquals(320.00, tb(root, 0).path("tier_2").path("cost").asDouble(),   0.001);
        assertEquals(1.00,   tb(root, 0).path("tier_3").path("hours").asDouble(),  0.001);
        assertEquals(0.50,   tb(root, 0).path("tier_3").path("cost").asDouble(),   0.001);
    }

    /** Non-enterprise 99 h off-peak: stays entirely in tier_1 → $89.00. */
    @Test
    void staysInTier1_nonEnterprise_99h_offPeak_total89() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"e3","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"e4","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"e5","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"e6","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"e7","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":180}
                  ]
                }""";
        // 99h = 10h grace + 89h tier_1; tier_2 = 0h
        JsonNode root = parse(json);
        assertEquals(89.00, res(root, 0).path("total_cost").asDouble(),          0.001);
        assertEquals(10.00, tb(root, 0).path("grace").path("hours").asDouble(),  0.001);
        assertEquals(89.00, tb(root, 0).path("tier_1").path("hours").asDouble(), 0.001);
        assertEquals(89.00, tb(root, 0).path("tier_1").path("cost").asDouble(),  0.001);
        assertEquals(0.00,  tb(root, 0).path("tier_2").path("hours").asDouble(), 0.001);
        assertEquals(0.00,  tb(root, 0).path("tier_3").path("hours").asDouble(), 0.001);
    }

    /** Non-enterprise 499 h off-peak: stays in tier_2, never reaches tier_3 → $411.20. */
    @Test
    void staysInTier2_nonEnterprise_499h_offPeak_total411_20() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"b0","user_id":"u1","timestamp":"2026-11-01T00:00:00Z","duration_minutes":540},
                    {"event_id":"b1","user_id":"u1","timestamp":"2026-11-01T17:00:00Z","duration_minutes":960},
                    {"event_id":"b2","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"b3","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"b4","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"b5","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"b6","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"b7","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"b8","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":960},
                    {"event_id":"b9","user_id":"u1","timestamp":"2026-11-09T17:00:00Z","duration_minutes":960},
                    {"event_id":"b10","user_id":"u1","timestamp":"2026-11-10T17:00:00Z","duration_minutes":960},
                    {"event_id":"b11","user_id":"u1","timestamp":"2026-11-11T17:00:00Z","duration_minutes":960},
                    {"event_id":"b12","user_id":"u1","timestamp":"2026-11-12T17:00:00Z","duration_minutes":960},
                    {"event_id":"b13","user_id":"u1","timestamp":"2026-11-13T17:00:00Z","duration_minutes":960},
                    {"event_id":"b14","user_id":"u1","timestamp":"2026-11-14T17:00:00Z","duration_minutes":960},
                    {"event_id":"b15","user_id":"u1","timestamp":"2026-11-15T17:00:00Z","duration_minutes":960},
                    {"event_id":"b16","user_id":"u1","timestamp":"2026-11-16T17:00:00Z","duration_minutes":960},
                    {"event_id":"b17","user_id":"u1","timestamp":"2026-11-17T17:00:00Z","duration_minutes":960},
                    {"event_id":"b18","user_id":"u1","timestamp":"2026-11-18T17:00:00Z","duration_minutes":960},
                    {"event_id":"b19","user_id":"u1","timestamp":"2026-11-19T17:00:00Z","duration_minutes":960},
                    {"event_id":"b20","user_id":"u1","timestamp":"2026-11-20T17:00:00Z","duration_minutes":960},
                    {"event_id":"b21","user_id":"u1","timestamp":"2026-11-21T17:00:00Z","duration_minutes":960},
                    {"event_id":"b22","user_id":"u1","timestamp":"2026-11-22T17:00:00Z","duration_minutes":960},
                    {"event_id":"b23","user_id":"u1","timestamp":"2026-11-23T17:00:00Z","duration_minutes":960},
                    {"event_id":"b24","user_id":"u1","timestamp":"2026-11-24T17:00:00Z","duration_minutes":960},
                    {"event_id":"b25","user_id":"u1","timestamp":"2026-11-25T17:00:00Z","duration_minutes":960},
                    {"event_id":"b26","user_id":"u1","timestamp":"2026-11-26T17:00:00Z","duration_minutes":960},
                    {"event_id":"b27","user_id":"u1","timestamp":"2026-11-27T17:00:00Z","duration_minutes":960},
                    {"event_id":"b28","user_id":"u1","timestamp":"2026-11-28T17:00:00Z","duration_minutes":960},
                    {"event_id":"b29","user_id":"u1","timestamp":"2026-11-29T17:00:00Z","duration_minutes":960},
                    {"event_id":"b30","user_id":"u1","timestamp":"2026-11-30T17:00:00Z","duration_minutes":420},
                    {"event_id":"p1","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"p2","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":180}
                  ]
                }""";
        // 499h = 10h grace + 100h tier_1 + 389h tier_2; tier_3 = 0h
        // cost = 100×$1 + 389×$0.80 = $100 + $311.20 = $411.20
        JsonNode root = parse(json);
        assertEquals(411.20, res(root, 0).path("total_cost").asDouble(),           0.001);
        assertEquals(10.00,  tb(root, 0).path("grace").path("hours").asDouble(),   0.001);
        assertEquals(100.00, tb(root, 0).path("tier_1").path("hours").asDouble(),  0.001);
        assertEquals(100.00, tb(root, 0).path("tier_1").path("cost").asDouble(),   0.001);
        assertEquals(389.00, tb(root, 0).path("tier_2").path("hours").asDouble(),  0.001);
        assertEquals(311.20, tb(root, 0).path("tier_2").path("cost").asDouble(),   0.001);
        assertEquals(0.00,   tb(root, 0).path("tier_3").path("hours").asDouble(),  0.001);
    }

    /** Grace boundary: exactly 10 h total → $0.00 (all grace). */
    @Test
    void graceBoundary_exactly10h_total0() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-11-02T00:00:00Z","duration_minutes":600}]
                }""";
        JsonNode root = parse(json);
        assertEquals(0.00,  res(root, 0).path("total_cost").asDouble(),          0.001);
        assertEquals(10.00, tb(root, 0).path("grace").path("hours").asDouble(),  0.001);
        assertEquals(0.00,  tb(root, 0).path("grace").path("cost").asDouble(),   0.001);
        assertEquals(0.00,  tb(root, 0).path("tier_1").path("hours").asDouble(), 0.001);
    }

    /** Grace boundary + 1 unit: 10h 15min → $0.25 (one tier_1 unit). */
    @Test
    void graceBoundaryPlusOneUnit_total0_25() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-11-02T17:00:00Z","duration_minutes":615}]
                }""";
        // 41 units: 40 grace + 1 tier_1 off-peak = 0.25×$1 = $0.25
        JsonNode root = parse(json);
        assertEquals(0.25,  res(root, 0).path("total_cost").asDouble(),          0.001);
        assertEquals(10.00, tb(root, 0).path("grace").path("hours").asDouble(),  0.001);
        assertEquals(0.25,  tb(root, 0).path("tier_1").path("hours").asDouble(), 0.001);
        assertEquals(0.25,  tb(root, 0).path("tier_1").path("cost").asDouble(),  0.001);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── TIME TESTS ─────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    /** Peak boundary: event starting exactly 09:00Z → peak, cost = 0.25×$1×1.2 = $0.30. */
    @Test
    void time_peak_exactly09_00Z() throws Exception {
        // First event exhausts grace; second unit at 09:00Z is tier_1 peak.
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T00:00:00Z","duration_minutes":600},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-02T09:00:00Z","duration_minutes":15}
                  ]
                }""";
        JsonNode root = parse(json);
        assertEquals(0,    hdr(root).path("error_count").asInt());
        assertEquals(0.30, res(root, 0).path("total_cost").asDouble(),           0.001);
        assertEquals(0.25, sur(root, 0).path("peak_hours").asDouble(),           0.001);
        assertEquals(0.05, sur(root, 0).path("peak_surcharge_total").asDouble(), 0.001);
    }

    /** Peak boundary: event starting exactly 17:00Z → off-peak, cost = 0.25×$1 = $0.25. */
    @Test
    void time_offPeak_exactly17_00Z() throws Exception {
        // First event exhausts grace; second unit at 17:00Z is tier_1 off-peak.
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T00:00:00Z","duration_minutes":600},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":15}
                  ]
                }""";
        JsonNode root = parse(json);
        assertEquals(0,    hdr(root).path("error_count").asInt());
        assertEquals(0.25, res(root, 0).path("total_cost").asDouble(),           0.001);
        assertEquals(0.00, sur(root, 0).path("peak_hours").asDouble(),           0.001);
        assertEquals(0.00, sur(root, 0).path("peak_surcharge_total").asDouble(), 0.001);
    }

    /**
     * Shuffled input order: two events for the same user provided in reverse
     * chronological order. The calculator must sort, so the result must be
     * identical to chronological order.
     */
    @Test
    void time_shuffledInputOrder_sameResultAsChronological() throws Exception {
        // Sorted (chronological) baseline
        String sorted = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T00:00:00Z","duration_minutes":300},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-02T06:00:00Z","duration_minutes":300}
                  ]
                }""";
        // Reversed (shuffled) — same events, reversed order in JSON array
        String shuffled = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-02T06:00:00Z","duration_minutes":300},
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T00:00:00Z","duration_minutes":300}
                  ]
                }""";
        JsonNode s = parse(sorted);
        JsonNode u = parse(shuffled);
        assertEquals(
                res(s, 0).path("total_cost").asDouble(),
                res(u, 0).path("total_cost").asDouble(),
                0.001);
        assertEquals(
                hdr(s).path("error_count").asInt(),
                hdr(u).path("error_count").asInt());
    }

    /**
     * SPEC §6 Example G — UTC conversion.
     * User in America/New_York; offset-less event "2026-10-31T23:30"
     * (America/New_York is EDT=UTC-4 on that date) → 2026-11-01T03:30Z.
     * All 4 units (03:30Z, 03:45Z, 04:00Z, 04:15Z) fall in November.
     * billing_cycle="2026-11" → event IS billed, error_count=0.
     */
    @Test
    void exampleG_utcConversion_newYorkEventInNovember() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"America/New_York"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-10-31T23:30","duration_minutes":60}]
                }""";
        JsonNode root = parse(json);
        assertEquals(0,    hdr(root).path("error_count").asInt());
        assertEquals("2026-11", hdr(root).path("billing_cycle").asText());
        // 4 units → 60min = 1h, all within grace → $0.00
        assertEquals(0.00, res(root, 0).path("total_cost").asDouble(), 0.001);
        // grace hours should be 1h (from these 4 units)
        assertEquals(1.00, tb(root, 0).path("grace").path("hours").asDouble(), 0.001);
    }

    /**
     * SPEC §6 Example J — Cross-month unit attribution.
     * Event "2026-10-31T23:30:00Z", 60min → 4 units:
     *   23:30Z(Oct), 23:45Z(Oct), 00:00Z Nov 1(Nov), 00:15Z Nov 1(Nov).
     * billing_cycle="2026-11": only 2 November units counted → 30 min = 0.50 h grace.
     */
    @Test
    void exampleJ_crossMonthAttribution_onlyNovUnitsCountInNov() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-10-31T23:30:00Z","duration_minutes":60}]
                }""";
        JsonNode root = parse(json);
        assertEquals(0,    hdr(root).path("error_count").asInt());
        assertEquals(0.00, res(root, 0).path("total_cost").asDouble(), 0.001);
        // Only the 2 November units (00:00Z and 00:15Z Nov 1) are attributed to 2026-11
        assertEquals(0.50, tb(root, 0).path("grace").path("hours").asDouble(), 0.001);
        assertEquals(0.00, tb(root, 0).path("tier_1").path("hours").asDouble(), 0.001);
    }

    /**
     * SPEC §6 Example L — DST ambiguous: America/New_York, offset-less
     * "2026-11-01T01:30" is in the fall-back overlap → ERROR, reason "ambiguous local time".
     */
    @Test
    void exampleL_dstAmbiguous_error() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"America/New_York"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-11-01T01:30","duration_minutes":15}]
                }""";
        JsonNode root = parse(json);
        assertEquals(1, hdr(root).path("error_count").asInt());
        assertEquals(0, hdr(root).path("warning_count").asInt());
        JsonNode skipped = hdr(root).path("skipped_items");
        assertEquals(1, skipped.size());
        assertEquals("e1", skipped.get(0).path("event_id").asText());
        assertTrue(skipped.get(0).path("reason").asText().toLowerCase().contains("ambiguous"),
                "reason should mention 'ambiguous'");
    }

    /**
     * SPEC §6 Example L — DST nonexistent: America/New_York, offset-less
     * "2026-03-08T02:30" is in the spring-forward gap → ERROR, reason "nonexistent local time".
     */
    @Test
    void exampleL_dstNonexistent_error() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-03",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"America/New_York"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-03-08T02:30","duration_minutes":15}]
                }""";
        JsonNode root = parse(json);
        assertEquals(1, hdr(root).path("error_count").asInt());
        assertEquals(0, hdr(root).path("warning_count").asInt());
        JsonNode skipped = hdr(root).path("skipped_items");
        assertEquals(1, skipped.size());
        assertEquals("e1", skipped.get(0).path("event_id").asText());
        assertTrue(skipped.get(0).path("reason").asText().toLowerCase().contains("nonexistent"),
                "reason should mention 'nonexistent'");
    }

    /**
     * Offset-bearing timestamp is NOT subject to DST rejection.
     * "2026-11-01T01:30:00-05:00" for an America/New_York user is unambiguous → no error.
     */
    @Test
    void time_offsetBearingTimestampUnaffectedByDst_noError() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"America/New_York"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-11-01T01:30:00-05:00","duration_minutes":15}]
                }""";
        JsonNode root = parse(json);
        assertEquals(0, hdr(root).path("error_count").asInt());
        assertEquals(0, hdr(root).path("warning_count").asInt());
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── VALIDATION (ERROR) TESTS ────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    /** SPEC §4 — Unknown user_id: event's user_id not in users list → error_count=1. */
    @Test
    void validation_unknownUser_error() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [{"event_id":"e1","user_id":"UNKNOWN",
                               "timestamp":"2026-11-02T00:00:00Z","duration_minutes":15}]
                }""";
        JsonNode root = parse(json);
        assertEquals(1, hdr(root).path("error_count").asInt());
        JsonNode skipped = hdr(root).path("skipped_items");
        assertEquals(1, skipped.size());
        assertEquals("e1", skipped.get(0).path("event_id").asText());
    }

    /**
     * SPEC §4 — Rejected user (invalid timezone): user is rejected; their event
     * is treated as an unknown-user ERROR.
     */
    @Test
    void validation_rejectedUserEvent_unknownUserError() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"Invalid/Zone"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-11-02T00:00:00Z","duration_minutes":15}]
                }""";
        JsonNode root = parse(json);
        // u1 is rejected → not in results
        assertEquals(1, hdr(root).path("error_count").asInt());
        // No result row for u1
        assertEquals(0, root.path("results").size());
    }

    /** SPEC §4 — Missing event_id field: error_count=1, skipped_items records event_id=null. */
    @Test
    void validation_missingEventId_error() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [{"user_id":"u1",
                               "timestamp":"2026-11-02T00:00:00Z","duration_minutes":15}]
                }""";
        JsonNode root = parse(json);
        assertEquals(1, hdr(root).path("error_count").asInt());
        JsonNode skipped = hdr(root).path("skipped_items");
        assertEquals(1, skipped.size());
        assertTrue(skipped.get(0).path("event_id").isNull(),
                "event_id in skipped_items should be null for missing event_id");
    }

    /** SPEC §4 — Blank event_id: error_count=1, skipped_items records event_id=null. */
    @Test
    void validation_blankEventId_error() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [{"event_id":"","user_id":"u1",
                               "timestamp":"2026-11-02T00:00:00Z","duration_minutes":15}]
                }""";
        JsonNode root = parse(json);
        assertEquals(1, hdr(root).path("error_count").asInt());
        JsonNode skipped = hdr(root).path("skipped_items");
        assertEquals(1, skipped.size());
        // blank event_id recorded as null
        assertTrue(skipped.get(0).path("event_id").isNull() ||
                        skipped.get(0).path("event_id").asText().isEmpty(),
                "event_id in skipped_items should be null/empty for blank event_id");
    }

    /** SPEC §4 — Bad timestamp (unparseable): error_count=1. */
    @Test
    void validation_badTimestamp_error() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"not-a-date","duration_minutes":15}]
                }""";
        JsonNode root = parse(json);
        assertEquals(1, hdr(root).path("error_count").asInt());
        assertEquals("e1", hdr(root).path("skipped_items").get(0).path("event_id").asText());
    }

    /** SPEC §4 — duration_minutes = 0: error_count=1. */
    @Test
    void validation_durationZero_error() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-11-02T00:00:00Z","duration_minutes":0}]
                }""";
        JsonNode root = parse(json);
        assertEquals(1, hdr(root).path("error_count").asInt());
    }

    /** SPEC §4 — duration_minutes = -30 (negative): error_count=1. */
    @Test
    void validation_durationNegative_error() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-11-02T00:00:00Z","duration_minutes":-30}]
                }""";
        JsonNode root = parse(json);
        assertEquals(1, hdr(root).path("error_count").asInt());
    }

    /** SPEC §6 Example H — duration = 10 (not a multiple of 15): error_count=1. */
    @Test
    void exampleH_duration10_notMultipleOf15_error() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-11-02T00:00:00Z","duration_minutes":10}]
                }""";
        JsonNode root = parse(json);
        assertEquals(1, hdr(root).path("error_count").asInt());
        JsonNode skipped = hdr(root).path("skipped_items");
        assertEquals(1, skipped.size());
        assertEquals("e1", skipped.get(0).path("event_id").asText());
        assertTrue(skipped.get(0).path("reason").asText().toLowerCase().contains("15"),
                "reason should mention '15'");
    }

    /** SPEC §6 Example H — duration = 20 (not a multiple of 15): error_count=1. */
    @Test
    void exampleH_duration20_notMultipleOf15_error() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-11-02T00:00:00Z","duration_minutes":20}]
                }""";
        JsonNode root = parse(json);
        assertEquals(1, hdr(root).path("error_count").asInt());
    }

    /** SPEC §6 Example H — start at :07 (not quarter-aligned): error_count=1. */
    @Test
    void exampleH_startAt07_notQuarterAligned_error() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-11-02T10:07:00Z","duration_minutes":15}]
                }""";
        JsonNode root = parse(json);
        assertEquals(1, hdr(root).path("error_count").asInt());
        JsonNode skipped = hdr(root).path("skipped_items");
        assertEquals(1, skipped.size());
        assertEquals("e1", skipped.get(0).path("event_id").asText());
        assertTrue(skipped.get(0).path("reason").asText().toLowerCase().contains("quarter"),
                "reason should mention 'quarter'");
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── VALIDATION (USERS) TESTS ────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    /**
     * SPEC §4 — User with invalid timezone is rejected.
     * Not in results array; their event is an ERROR.
     */
    @Test
    void validation_invalidTimezoneRejected_notInResults() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"Not/AZone"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-11-02T00:00:00Z","duration_minutes":15}]
                }""";
        JsonNode root = parse(json);
        // Rejected user: not in results
        assertEquals(0, root.path("results").size());
        // Their event becomes unknown-user ERROR
        assertEquals(1, hdr(root).path("error_count").asInt());
    }

    /**
     * SPEC §4 — User with missing is_enterprise field is rejected.
     * Not in results array.
     */
    @Test
    void validation_missingIsEnterpriseRejected() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","timezone":"UTC"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-11-02T00:00:00Z","duration_minutes":15}]
                }""";
        JsonNode root = parse(json);
        assertEquals(0, root.path("results").size());
        // Their event becomes unknown-user ERROR
        assertEquals(1, hdr(root).path("error_count").asInt());
    }

    /**
     * SPEC §4 — Unknown JSON fields in users, events, and top-level are ignored.
     * Processing continues normally with no additional errors or warnings.
     */
    @Test
    void validation_unknownJsonFieldsIgnored() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "extra_top_level": "ignored",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC","extra_user_field":42}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-11-02T00:00:00Z","duration_minutes":15,
                               "extra_event_field":"ignored"}]
                }""";
        JsonNode root = parse(json);
        assertEquals(0, hdr(root).path("error_count").asInt());
        assertEquals(0, hdr(root).path("warning_count").asInt());
        assertEquals(1, root.path("results").size());
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── WARNING TESTS ───────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    /**
     * SPEC §6 Example K — Duplicate event, identical payload.
     * Same (user_id, event_id, timestamp, duration) sent twice.
     * → Billed once, warning_count=1, error_count=0.
     * warnings[0].reason contains "identical payload".
     */
    @Test
    void exampleK_duplicateIdenticalPayload_billedOnce_warning() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T00:00:00Z","duration_minutes":15},
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T00:00:00Z","duration_minutes":15}
                  ]
                }""";
        JsonNode root = parse(json);
        assertEquals(0, hdr(root).path("error_count").asInt());
        assertEquals(1, hdr(root).path("warning_count").asInt());
        JsonNode warnings = hdr(root).path("warnings");
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).path("reason").asText().toLowerCase().contains("identical payload"),
                "warning reason should mention 'identical payload'");
        // Billed once → 15min < 10h grace → $0.00
        assertEquals(0.25, tb(root, 0).path("grace").path("hours").asDouble(), 0.001);
    }

    /**
     * SPEC §6 Example K — Duplicate event, different payload (different duration).
     * Same event_id, same user_id, but different duration → both billed, warning_count=1, error_count=0.
     */
    @Test
    void exampleK_duplicateDifferentPayload_bothBilled_warning() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T00:00:00Z","duration_minutes":15},
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T00:00:00Z","duration_minutes":30}
                  ]
                }""";
        JsonNode root = parse(json);
        assertEquals(0, hdr(root).path("error_count").asInt());
        assertEquals(1, hdr(root).path("warning_count").asInt());
        JsonNode warnings = hdr(root).path("warnings");
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).path("reason").asText().toLowerCase().contains("different payload"),
                "warning reason should mention 'different payload'");
        // Both billed → 15+30 = 45min grace total
        assertEquals(0.75, tb(root, 0).path("grace").path("hours").asDouble(), 0.001);
    }

    /**
     * Overlapping events for the same user represent ordinary concurrent API usage (DECISIONS #15):
     * both billed in full independently, no warning raised.
     */
    @Test
    void overlappingEvents_billedAsConcurrentUsage_noWarning() throws Exception {
        // e1: 00:00Z–01:00Z (60 min). e2: 00:30Z–01:30Z (60 min). Concurrent usage.
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T00:00:00Z","duration_minutes":60},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-02T00:30:00Z","duration_minutes":60}
                  ]
                }""";
        JsonNode root = parse(json);
        assertEquals(0, hdr(root).path("error_count").asInt());
        assertEquals(0, hdr(root).path("warning_count").asInt());
        JsonNode warnings = hdr(root).path("warnings");
        assertEquals(0, warnings.size());
        // Both events billed: total 120min = 2h < grace; $0
        assertEquals(2.00, tb(root, 0).path("grace").path("hours").asDouble(), 0.001);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── ENVELOPE TESTS ──────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Header must have all four mandatory fields: error_count, warning_count,
     * skipped_items (empty list), warnings (empty list).
     */
    @Test
    void envelope_headerFields_allPresent() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-11-02T00:00:00Z","duration_minutes":15}]
                }""";
        JsonNode root = parse(json);
        JsonNode h = hdr(root);
        // All four fields present and correct defaults for a clean input
        assertFalse(h.path("error_count").isMissingNode(),   "error_count must be present");
        assertFalse(h.path("warning_count").isMissingNode(), "warning_count must be present");
        assertFalse(h.path("skipped_items").isMissingNode(), "skipped_items must be present");
        assertFalse(h.path("warnings").isMissingNode(),      "warnings must be present");
        assertEquals(0, h.path("error_count").asInt());
        assertEquals(0, h.path("warning_count").asInt());
        assertTrue(h.path("skipped_items").isArray());
        assertEquals(0, h.path("skipped_items").size());
        assertTrue(h.path("warnings").isArray());
        assertEquals(0, h.path("warnings").size());
    }

    /**
     * Valid events outside the requested billing cycle are silently ignored —
     * neither an error nor a warning.
     */
    @Test
    void envelope_outOfCycleEvents_neitherErrorNorWarning() throws Exception {
        // Event in October, billing cycle November
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-10-15T00:00:00Z","duration_minutes":15}]
                }""";
        JsonNode root = parse(json);
        assertEquals(0, hdr(root).path("error_count").asInt());
        assertEquals(0, hdr(root).path("warning_count").asInt());
    }

    /**
     * When billing_cycle is omitted, the calculator uses the injected clock to
     * determine the current cycle. Fixed clock → "2026-11".
     */
    @Test
    void envelope_billingCycleOmitted_usesFixedClock_2026_11() throws Exception {
        String json = """
                {
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [{"event_id":"e1","user_id":"u1",
                               "timestamp":"2026-11-02T00:00:00Z","duration_minutes":15}]
                }""";
        JsonNode root = parse(json);
        assertEquals("2026-11", hdr(root).path("billing_cycle").asText());
        assertEquals(0, hdr(root).path("error_count").asInt());
    }

    /**
     * A user with no events in the cycle is returned with total_cost=0.00
     * and all tier hours = 0.
     */
    @Test
    void envelope_zeroUsageUser_returnedWith0_00() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": []
                }""";
        JsonNode root = parse(json);
        assertEquals(1, root.path("results").size());
        assertEquals("u1", res(root, 0).path("user_id").asText());
        assertEquals(0.00, res(root, 0).path("total_cost").asDouble(), 0.001);
        assertEquals(0.00, tb(root, 0).path("grace").path("hours").asDouble(),  0.001);
        assertEquals(0.00, tb(root, 0).path("tier_1").path("hours").asDouble(), 0.001);
        assertEquals(0.00, tb(root, 0).path("tier_2").path("hours").asDouble(), 0.001);
        assertEquals(0.00, tb(root, 0).path("tier_3").path("hours").asDouble(), 0.001);
    }

    /**
     * SPEC §6 Example I — Rounding HALF_UP anchor.
     * User has consumed exactly 500 h of tier-progression (all via prior events in this cycle),
     * then 1 more off-peak unit. tier_3 cost = 0.25 × $0.50 = $0.125 → rounds HALF_UP to $0.13.
     * total_cost = $0.13.
     *
     * Setup: two events. First: 510h non-enterprise off-peak (uses up 10h grace + 500h tier progression).
     * Second: 15min off-peak (1 tier_3 unit = $0.125 → $0.13).
     */
    @Test
    void exampleI_roundingHalfUp_tier3SingleUnit_0_13() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"a1","user_id":"u1","timestamp":"2026-11-01T17:00:00Z","duration_minutes":960},
                    {"event_id":"b1","user_id":"u1","timestamp":"2026-11-01T17:00:00Z","duration_minutes":960},
                    {"event_id":"a2","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"b2","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"a3","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"b3","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"a4","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"b4","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"a5","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"b5","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"a6","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"b6","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"a7","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"b7","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"a8","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":960},
                    {"event_id":"b8","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":960},
                    {"event_id":"a9","user_id":"u1","timestamp":"2026-11-09T17:00:00Z","duration_minutes":960},
                    {"event_id":"b9","user_id":"u1","timestamp":"2026-11-09T17:00:00Z","duration_minutes":960},
                    {"event_id":"a10","user_id":"u1","timestamp":"2026-11-10T17:00:00Z","duration_minutes":960},
                    {"event_id":"b10","user_id":"u1","timestamp":"2026-11-10T17:00:00Z","duration_minutes":960},
                    {"event_id":"a11","user_id":"u1","timestamp":"2026-11-11T17:00:00Z","duration_minutes":960},
                    {"event_id":"b11","user_id":"u1","timestamp":"2026-11-11T17:00:00Z","duration_minutes":960},
                    {"event_id":"a12","user_id":"u1","timestamp":"2026-11-12T17:00:00Z","duration_minutes":960},
                    {"event_id":"b12","user_id":"u1","timestamp":"2026-11-12T17:00:00Z","duration_minutes":960},
                    {"event_id":"a13","user_id":"u1","timestamp":"2026-11-13T17:00:00Z","duration_minutes":960},
                    {"event_id":"b13","user_id":"u1","timestamp":"2026-11-13T17:00:00Z","duration_minutes":960},
                    {"event_id":"a14","user_id":"u1","timestamp":"2026-11-14T17:00:00Z","duration_minutes":960},
                    {"event_id":"b14","user_id":"u1","timestamp":"2026-11-14T17:00:00Z","duration_minutes":960},
                    {"event_id":"a15","user_id":"u1","timestamp":"2026-11-15T17:00:00Z","duration_minutes":960},
                    {"event_id":"b15","user_id":"u1","timestamp":"2026-11-15T17:00:00Z","duration_minutes":960},
                    {"event_id":"a16","user_id":"u1","timestamp":"2026-11-16T17:00:00Z","duration_minutes":960},
                    {"event_id":"b16","user_id":"u1","timestamp":"2026-11-16T17:00:00Z","duration_minutes":840},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-22T00:00:00Z","duration_minutes":15}
                  ]
                }""";
        // e1: 510h = 10h grace + 100h tier_1 + 400h tier_2 (non-enterprise)
        // After e1, progression counter = 500h (all of tier_1+tier_2 filled)
        // e2: 15min = 1 tier_3 unit off-peak → 0.25 × $0.50 = $0.125 → rounds to $0.13
        JsonNode root = parse(json);
        assertEquals(0, hdr(root).path("error_count").asInt());

        // tier_3.cost should be $0.13 (rounded HALF_UP from $0.125)
        assertEquals(0.13, tb(root, 0).path("tier_3").path("cost").asDouble(), 0.001);
        // total_cost = tier_1.cost(rounded) + tier_2.cost(rounded) + tier_3.cost(rounded) + surcharge(0)
        // = $100.00 + $320.00 + $0.13 + $0.00 = $420.13
        assertEquals(420.13, res(root, 0).path("total_cost").asDouble(), 0.001);
    }

    /**
     * Breakdown invariant for Example I: sum of tier line costs + peak_surcharge_total = total_cost.
     * Also verifies the simpler isolated case: a single tier_3 unit → $0.13.
     */
    @Test
    void exampleI_breakdownInvariant_lineCostsPlusSurcharge_equalsTotalCost() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"a1","user_id":"u1","timestamp":"2026-11-01T17:00:00Z","duration_minutes":960},
                    {"event_id":"b1","user_id":"u1","timestamp":"2026-11-01T17:00:00Z","duration_minutes":960},
                    {"event_id":"a2","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"b2","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"a3","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"b3","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"a4","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"b4","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"a5","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"b5","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"a6","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"b6","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"a7","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"b7","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"a8","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":960},
                    {"event_id":"b8","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":960},
                    {"event_id":"a9","user_id":"u1","timestamp":"2026-11-09T17:00:00Z","duration_minutes":960},
                    {"event_id":"b9","user_id":"u1","timestamp":"2026-11-09T17:00:00Z","duration_minutes":960},
                    {"event_id":"a10","user_id":"u1","timestamp":"2026-11-10T17:00:00Z","duration_minutes":960},
                    {"event_id":"b10","user_id":"u1","timestamp":"2026-11-10T17:00:00Z","duration_minutes":960},
                    {"event_id":"a11","user_id":"u1","timestamp":"2026-11-11T17:00:00Z","duration_minutes":960},
                    {"event_id":"b11","user_id":"u1","timestamp":"2026-11-11T17:00:00Z","duration_minutes":960},
                    {"event_id":"a12","user_id":"u1","timestamp":"2026-11-12T17:00:00Z","duration_minutes":960},
                    {"event_id":"b12","user_id":"u1","timestamp":"2026-11-12T17:00:00Z","duration_minutes":960},
                    {"event_id":"a13","user_id":"u1","timestamp":"2026-11-13T17:00:00Z","duration_minutes":960},
                    {"event_id":"b13","user_id":"u1","timestamp":"2026-11-13T17:00:00Z","duration_minutes":960},
                    {"event_id":"a14","user_id":"u1","timestamp":"2026-11-14T17:00:00Z","duration_minutes":960},
                    {"event_id":"b14","user_id":"u1","timestamp":"2026-11-14T17:00:00Z","duration_minutes":960},
                    {"event_id":"a15","user_id":"u1","timestamp":"2026-11-15T17:00:00Z","duration_minutes":960},
                    {"event_id":"b15","user_id":"u1","timestamp":"2026-11-15T17:00:00Z","duration_minutes":960},
                    {"event_id":"a16","user_id":"u1","timestamp":"2026-11-16T17:00:00Z","duration_minutes":960},
                    {"event_id":"b16","user_id":"u1","timestamp":"2026-11-16T17:00:00Z","duration_minutes":840},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-22T00:00:00Z","duration_minutes":15}
                  ]
                }""";
        JsonNode root = parse(json);

        double graceCost   = tb(root, 0).path("grace").path("cost").asDouble();
        double tier1Cost   = tb(root, 0).path("tier_1").path("cost").asDouble();
        double tier2Cost   = tb(root, 0).path("tier_2").path("cost").asDouble();
        double tier3Cost   = tb(root, 0).path("tier_3").path("cost").asDouble();
        double surcharge   = sur(root, 0).path("peak_surcharge_total").asDouble();
        double totalCost   = res(root, 0).path("total_cost").asDouble();

        assertEquals(totalCost, graceCost + tier1Cost + tier2Cost + tier3Cost + surcharge, 0.001,
                "Breakdown invariant: line costs + surcharge must equal total_cost");
    }

    /**
     * Breakdown invariant for Example D (enterprise 510h off-peak → $415.00).
     */
    @Test
    void exampleD_breakdownInvariant() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":true,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"b0","user_id":"u1","timestamp":"2026-11-01T00:00:00Z","duration_minutes":540},
                    {"event_id":"b1","user_id":"u1","timestamp":"2026-11-01T17:00:00Z","duration_minutes":960},
                    {"event_id":"b2","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"b3","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"b4","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"b5","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"b6","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"b7","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"b8","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":960},
                    {"event_id":"b9","user_id":"u1","timestamp":"2026-11-09T17:00:00Z","duration_minutes":960},
                    {"event_id":"b10","user_id":"u1","timestamp":"2026-11-10T17:00:00Z","duration_minutes":960},
                    {"event_id":"b11","user_id":"u1","timestamp":"2026-11-11T17:00:00Z","duration_minutes":960},
                    {"event_id":"b12","user_id":"u1","timestamp":"2026-11-12T17:00:00Z","duration_minutes":960},
                    {"event_id":"b13","user_id":"u1","timestamp":"2026-11-13T17:00:00Z","duration_minutes":960},
                    {"event_id":"b14","user_id":"u1","timestamp":"2026-11-14T17:00:00Z","duration_minutes":960},
                    {"event_id":"b15","user_id":"u1","timestamp":"2026-11-15T17:00:00Z","duration_minutes":960},
                    {"event_id":"b16","user_id":"u1","timestamp":"2026-11-16T17:00:00Z","duration_minutes":960},
                    {"event_id":"b17","user_id":"u1","timestamp":"2026-11-17T17:00:00Z","duration_minutes":960},
                    {"event_id":"b18","user_id":"u1","timestamp":"2026-11-18T17:00:00Z","duration_minutes":960},
                    {"event_id":"b19","user_id":"u1","timestamp":"2026-11-19T17:00:00Z","duration_minutes":960},
                    {"event_id":"b20","user_id":"u1","timestamp":"2026-11-20T17:00:00Z","duration_minutes":960},
                    {"event_id":"b21","user_id":"u1","timestamp":"2026-11-21T17:00:00Z","duration_minutes":960},
                    {"event_id":"b22","user_id":"u1","timestamp":"2026-11-22T17:00:00Z","duration_minutes":960},
                    {"event_id":"b23","user_id":"u1","timestamp":"2026-11-23T17:00:00Z","duration_minutes":960},
                    {"event_id":"b24","user_id":"u1","timestamp":"2026-11-24T17:00:00Z","duration_minutes":960},
                    {"event_id":"b25","user_id":"u1","timestamp":"2026-11-25T17:00:00Z","duration_minutes":960},
                    {"event_id":"b26","user_id":"u1","timestamp":"2026-11-26T17:00:00Z","duration_minutes":960},
                    {"event_id":"b27","user_id":"u1","timestamp":"2026-11-27T17:00:00Z","duration_minutes":960},
                    {"event_id":"b28","user_id":"u1","timestamp":"2026-11-28T17:00:00Z","duration_minutes":960},
                    {"event_id":"b29","user_id":"u1","timestamp":"2026-11-29T17:00:00Z","duration_minutes":960},
                    {"event_id":"b30","user_id":"u1","timestamp":"2026-11-30T17:00:00Z","duration_minutes":420},
                    {"event_id":"p1","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"p2","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":840}
                  ]
                }""";
        JsonNode root = parse(json);

        double graceCost = tb(root, 0).path("grace").path("cost").asDouble();
        double tier1Cost = tb(root, 0).path("tier_1").path("cost").asDouble();
        double tier2Cost = tb(root, 0).path("tier_2").path("cost").asDouble();
        double tier3Cost = tb(root, 0).path("tier_3").path("cost").asDouble();
        double surcharge = sur(root, 0).path("peak_surcharge_total").asDouble();
        double totalCost = res(root, 0).path("total_cost").asDouble();

        assertEquals(totalCost, graceCost + tier1Cost + tier2Cost + tier3Cost + surcharge, 0.001,
                "Breakdown invariant violated for Example D");
    }

    /**
     * Breakdown invariant for Example F (peak boundary split → $1.10).
     */
    @Test
    void exampleF_breakdownInvariant() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T00:00:00Z","duration_minutes":600},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-02T16:30:00Z","duration_minutes":60}
                  ]
                }""";
        JsonNode root = parse(json);

        double graceCost = tb(root, 0).path("grace").path("cost").asDouble();
        double tier1Cost = tb(root, 0).path("tier_1").path("cost").asDouble();
        double tier2Cost = tb(root, 0).path("tier_2").path("cost").asDouble();
        double tier3Cost = tb(root, 0).path("tier_3").path("cost").asDouble();
        double surcharge = sur(root, 0).path("peak_surcharge_total").asDouble();
        double totalCost = res(root, 0).path("total_cost").asDouble();

        assertEquals(totalCost, graceCost + tier1Cost + tier2Cost + tier3Cost + surcharge, 0.001,
                "Breakdown invariant violated for Example F");
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── TIER-HOURS INVARIANT ────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    /**
     * SPEC invariant: grace.hours + tier_1.hours + tier_2.hours + tier_3.hours
     * = total valid usage hours for the cycle (for Example A, 110h).
     */
    @Test
    void invariant_tierHoursSum_equalsTotal_exampleA() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"e3","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"e4","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"e5","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"e6","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"e7","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":840}
                  ]
                }""";
        JsonNode root = parse(json);
        double grace  = tb(root, 0).path("grace").path("hours").asDouble();
        double tier1  = tb(root, 0).path("tier_1").path("hours").asDouble();
        double tier2  = tb(root, 0).path("tier_2").path("hours").asDouble();
        double tier3  = tb(root, 0).path("tier_3").path("hours").asDouble();
        assertEquals(110.0, grace + tier1 + tier2 + tier3, 0.001,
                "grace + tier_1 + tier_2 + tier_3 must equal total usage hours (110h)");
    }

    /**
     * SPEC invariant: grace.hours + tier_1.hours + tier_2.hours + tier_3.hours
     * = total valid usage hours for the cycle (for Example D, 510h enterprise).
     */
    @Test
    void invariant_tierHoursSum_equalsTotal_exampleD() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":true,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"b0","user_id":"u1","timestamp":"2026-11-01T00:00:00Z","duration_minutes":540},
                    {"event_id":"b1","user_id":"u1","timestamp":"2026-11-01T17:00:00Z","duration_minutes":960},
                    {"event_id":"b2","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"b3","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"b4","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"b5","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"b6","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"b7","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"b8","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":960},
                    {"event_id":"b9","user_id":"u1","timestamp":"2026-11-09T17:00:00Z","duration_minutes":960},
                    {"event_id":"b10","user_id":"u1","timestamp":"2026-11-10T17:00:00Z","duration_minutes":960},
                    {"event_id":"b11","user_id":"u1","timestamp":"2026-11-11T17:00:00Z","duration_minutes":960},
                    {"event_id":"b12","user_id":"u1","timestamp":"2026-11-12T17:00:00Z","duration_minutes":960},
                    {"event_id":"b13","user_id":"u1","timestamp":"2026-11-13T17:00:00Z","duration_minutes":960},
                    {"event_id":"b14","user_id":"u1","timestamp":"2026-11-14T17:00:00Z","duration_minutes":960},
                    {"event_id":"b15","user_id":"u1","timestamp":"2026-11-15T17:00:00Z","duration_minutes":960},
                    {"event_id":"b16","user_id":"u1","timestamp":"2026-11-16T17:00:00Z","duration_minutes":960},
                    {"event_id":"b17","user_id":"u1","timestamp":"2026-11-17T17:00:00Z","duration_minutes":960},
                    {"event_id":"b18","user_id":"u1","timestamp":"2026-11-18T17:00:00Z","duration_minutes":960},
                    {"event_id":"b19","user_id":"u1","timestamp":"2026-11-19T17:00:00Z","duration_minutes":960},
                    {"event_id":"b20","user_id":"u1","timestamp":"2026-11-20T17:00:00Z","duration_minutes":960},
                    {"event_id":"b21","user_id":"u1","timestamp":"2026-11-21T17:00:00Z","duration_minutes":960},
                    {"event_id":"b22","user_id":"u1","timestamp":"2026-11-22T17:00:00Z","duration_minutes":960},
                    {"event_id":"b23","user_id":"u1","timestamp":"2026-11-23T17:00:00Z","duration_minutes":960},
                    {"event_id":"b24","user_id":"u1","timestamp":"2026-11-24T17:00:00Z","duration_minutes":960},
                    {"event_id":"b25","user_id":"u1","timestamp":"2026-11-25T17:00:00Z","duration_minutes":960},
                    {"event_id":"b26","user_id":"u1","timestamp":"2026-11-26T17:00:00Z","duration_minutes":960},
                    {"event_id":"b27","user_id":"u1","timestamp":"2026-11-27T17:00:00Z","duration_minutes":960},
                    {"event_id":"b28","user_id":"u1","timestamp":"2026-11-28T17:00:00Z","duration_minutes":960},
                    {"event_id":"b29","user_id":"u1","timestamp":"2026-11-29T17:00:00Z","duration_minutes":960},
                    {"event_id":"b30","user_id":"u1","timestamp":"2026-11-30T17:00:00Z","duration_minutes":420},
                    {"event_id":"p1","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"p2","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":840}
                  ]
                }""";
        JsonNode root = parse(json);
        double grace = tb(root, 0).path("grace").path("hours").asDouble();
        double tier1 = tb(root, 0).path("tier_1").path("hours").asDouble();
        double tier2 = tb(root, 0).path("tier_2").path("hours").asDouble();
        double tier3 = tb(root, 0).path("tier_3").path("hours").asDouble();
        assertEquals(510.0, grace + tier1 + tier2 + tier3, 0.001,
                "grace + tier_1 + tier_2 + tier_3 must equal total usage hours (510h)");
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── ENTERPRISE vs NON-ENTERPRISE PAIR ──────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Enterprise vs non-enterprise explicit contrast (the B/A contrast).
     * Same 110h input: enterprise → $90, non-enterprise → $100.
     */
    @Test
    void enterpriseVsNonEnterprise_110h_contrast() throws Exception {
        String ent = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":true,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"e3","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"e4","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"e5","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"e6","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"e7","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":840}
                  ]
                }""";
        String nonEnt = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T17:00:00Z","duration_minutes":960},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-03T17:00:00Z","duration_minutes":960},
                    {"event_id":"e3","user_id":"u1","timestamp":"2026-11-04T17:00:00Z","duration_minutes":960},
                    {"event_id":"e4","user_id":"u1","timestamp":"2026-11-05T17:00:00Z","duration_minutes":960},
                    {"event_id":"e5","user_id":"u1","timestamp":"2026-11-06T17:00:00Z","duration_minutes":960},
                    {"event_id":"e6","user_id":"u1","timestamp":"2026-11-07T17:00:00Z","duration_minutes":960},
                    {"event_id":"e7","user_id":"u1","timestamp":"2026-11-08T17:00:00Z","duration_minutes":840}
                  ]
                }""";
        JsonNode entRoot    = parse(ent);
        JsonNode nonEntRoot = parse(nonEnt);

        assertEquals(90.00,  res(entRoot,    0).path("total_cost").asDouble(), 0.001);
        assertEquals(100.00, res(nonEntRoot, 0).path("total_cost").asDouble(), 0.001);
        // Enterprise has fewer tier_1 hours (grace eats into progression)
        assertEquals(90.00,  tb(entRoot,    0).path("tier_1").path("hours").asDouble(), 0.001);
        assertEquals(100.00, tb(nonEntRoot, 0).path("tier_1").path("hours").asDouble(), 0.001);
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── MULTIPLE USERS ──────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Results are returned in input order, one per valid user, including users
     * with zero billable usage.
     */
    @Test
    void multipleUsers_resultsInInputOrder_includesZeroUsage() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [
                    {"user_id":"u1","is_enterprise":false,"timezone":"UTC"},
                    {"user_id":"u2","is_enterprise":false,"timezone":"UTC"}
                  ],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T00:00:00Z","duration_minutes":15}
                  ]
                }""";
        // u2 has no events → returned with 0.00
        JsonNode root = parse(json);
        assertEquals(2, root.path("results").size());
        assertEquals("u1", res(root, 0).path("user_id").asText());
        assertEquals("u2", res(root, 1).path("user_id").asText());
        assertEquals(0.00, res(root, 1).path("total_cost").asDouble(), 0.001);
    }
}
