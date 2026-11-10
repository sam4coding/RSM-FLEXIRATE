package com.samwang.flexirate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for the CLI (T4): file and stdin/stdout modes,
 * per-item stderr log, FATAL and I/O exit codes.
 */
class MainCliTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private record Result(int exit, String out, String err) {}

    private Result runCli(String stdin, String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        int exit = Main.run(args,
                new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(outBytes, true, StandardCharsets.UTF_8),
                new PrintStream(errBytes, true, StandardCharsets.UTF_8));
        return new Result(exit,
                outBytes.toString(StandardCharsets.UTF_8),
                errBytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void fileToFile_skipsAndWarningsLoggedToStderr(@TempDir Path dir) throws Exception {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T00:00:00Z","duration_minutes":600},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-03T09:00:00Z","duration_minutes":60},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-03T09:00:00Z","duration_minutes":60},
                    {"event_id":"eBad","user_id":"u1","timestamp":"2026-11-03T10:07:00Z","duration_minutes":60}
                  ]
                }""";
        Path in = dir.resolve("input.json");
        Path outFile = dir.resolve("output.json");
        Files.writeString(in, json, StandardCharsets.UTF_8);

        Result r = runCli("", in.toString(), outFile.toString());

        assertEquals(0, r.exit());
        JsonNode root = mapper.readTree(Files.readString(outFile, StandardCharsets.UTF_8));
        assertEquals(1, root.path("header").path("error_count").asInt());
        assertEquals(1, root.path("header").path("warning_count").asInt());
        // e1 exhausts grace; e2 = 4 peak tier_1 units: $1.00 base + $0.20 surcharge
        assertEquals(1.20, root.path("results").get(0).path("total_cost").asDouble(), 0.001);
        assertTrue(r.err().contains("SKIPPED") && r.err().contains("eBad"),
                "stderr should log the skipped item: " + r.err());
        assertTrue(r.err().contains("WARNING") && r.err().contains("duplicate"),
                "stderr should log the duplicate warning: " + r.err());
    }

    @Test
    void stdinToStdout_exampleF() {
        String json = """
                {
                  "billing_cycle": "2026-11",
                  "users":  [{"user_id":"u1","is_enterprise":false,"timezone":"UTC"}],
                  "events": [
                    {"event_id":"e1","user_id":"u1","timestamp":"2026-11-02T00:00:00Z","duration_minutes":600},
                    {"event_id":"e2","user_id":"u1","timestamp":"2026-11-02T16:30:00Z","duration_minutes":60}
                  ]
                }""";
        Result r = runCli(json);
        assertEquals(0, r.exit());
        assertEquals(1.10, assertDoesNotThrow(() ->
                mapper.readTree(r.out()).path("results").get(0).path("total_cost").asDouble()), 0.001);
        assertTrue(r.err().isBlank(), "clean input should log nothing: " + r.err());
    }

    @Test
    void fatal_malformedJson_exit1NoOutput() {
        Result r = runCli("{not json");
        assertEquals(1, r.exit());
        assertTrue(r.out().isBlank());
        assertTrue(r.err().contains("FATAL"), "stderr should explain the fatal error: " + r.err());
    }

    @Test
    void missingInputFile_exit2(@TempDir Path dir) {
        Result r = runCli("", dir.resolve("missing.json").toString());
        assertEquals(2, r.exit());
        assertTrue(r.err().contains("could not read input"), "stderr: " + r.err());
    }
}
