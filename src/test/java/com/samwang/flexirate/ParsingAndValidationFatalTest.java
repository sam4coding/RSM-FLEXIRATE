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
 * Tests covering FATAL input parsing and document-level validation per SPEC §4 and DECISIONS #19.
 */
class ParsingAndValidationFatalTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC);

    private FlexiRateCalculatorApi api;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        api = new FlexiRateCalculatorApi(FIXED_CLOCK);
        mapper = new ObjectMapper();
    }

    @Test
    void fatal_nullOrBlankInput_throwsFatalBillingException() {
        assertThrows(FatalBillingException.class, () -> api.calculate(null));
        assertThrows(FatalBillingException.class, () -> api.calculate(""));
        assertThrows(FatalBillingException.class, () -> api.calculate("   "));
    }

    @Test
    void fatal_malformedJson_throwsFatalBillingException() {
        assertThrows(FatalBillingException.class, () -> api.calculate("{ not json }"));
        assertThrows(FatalBillingException.class, () -> api.calculate("{\"users\": [}"));
    }

    @Test
    void fatal_nonObjectRoot_throwsFatalBillingException() {
        assertThrows(FatalBillingException.class, () -> api.calculate("[]"));
        assertThrows(FatalBillingException.class, () -> api.calculate("\"string\""));
        assertThrows(FatalBillingException.class, () -> api.calculate("12345"));
    }

    @Test
    void fatal_missingUsersArray_throwsFatalBillingException() {
        String json = "{\"events\": []}";
        assertThrows(FatalBillingException.class, () -> api.calculate(json));
    }

    @Test
    void fatal_usersNotAnArray_throwsFatalBillingException() {
        String json = "{\"users\": \"not-an-array\", \"events\": []}";
        assertThrows(FatalBillingException.class, () -> api.calculate(json));
    }

    @Test
    void fatal_missingEventsArray_throwsFatalBillingException() {
        String json = "{\"users\": []}";
        assertThrows(FatalBillingException.class, () -> api.calculate(json));
    }

    @Test
    void fatal_eventsNotAnArray_throwsFatalBillingException() {
        String json = "{\"users\": [], \"events\": {}}";
        assertThrows(FatalBillingException.class, () -> api.calculate(json));
    }

    @Test
    void nonBooleanIsEnterprise_rejectsUser_doesNotThrowFatal() throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users": [
                    {"user_id": "u1", "is_enterprise": "not_a_bool", "timezone": "UTC"}
                  ],
                  "events": [
                    {"event_id": "e1", "user_id": "u1", "timestamp": "2026-11-02T00:00:00Z", "duration_minutes": 15}
                  ]
                }
                """;
        String out = api.calculate(json);
        assertNotNull(out);
        JsonNode root = mapper.readTree(out);
        assertEquals(0, root.path("results").size(), "Rejected user should not produce a result row");
        assertEquals(1, root.path("header").path("error_count").asInt(), "Event for rejected user should be counted as error");
    }

    @Test
    void unknownFieldsIgnoredAtEveryLevel() throws Exception {
        String json = """
                {
                  "unknown_top_level": "ignored",
                  "users": [
                    {"user_id": "u1", "is_enterprise": false, "timezone": "UTC", "unknown_user_prop": 999}
                  ],
                  "events": [
                    {"event_id": "e1", "user_id": "u1", "timestamp": "2026-11-02T00:00:00Z", "duration_minutes": 15, "unknown_event_prop": true}
                  ]
                }
                """;
        String out = api.calculate(json);
        assertNotNull(out);
        JsonNode root = mapper.readTree(out);
        assertEquals(0, root.path("header").path("error_count").asInt());
        assertEquals(1, root.path("results").size());
    }
}
