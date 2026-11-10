package com.samwang.flexirate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI entry point for the Flexi-Rate Usage Calculator.
 *
 * <p>Usage:
 * <pre>
 *   java -jar flexirate.jar [input.json [output.json]]
 * </pre>
 * No input argument  → input is read from stdin.
 * No output argument → result is written to stdout.
 *
 * <p>Every skipped item and every warning is also logged, one line per item,
 * to stderr (DECISIONS #7). The output JSON itself is the authoritative record.
 *
 * <p>Exit codes: 0 = success; 1 = FATAL input document (malformed JSON,
 * missing/invalid users or events array — no output JSON is produced);
 * 2 = usage or I/O error (bad arguments, unreadable input file,
 * unwritable output file).
 *
 * <p>Business logic lives in {@code BillingCalculator} behind
 * {@code FlexiRateCalculatorApi}; this class is a thin CLI wrapper only.
 */
public class Main {

    public static void main(String[] args) {
        System.exit(run(args, System.in, System.out, System.err));
    }

    /** Testable entry point: returns the process exit code. */
    static int run(String[] args, InputStream in, PrintStream out, PrintStream err) {
        if (args.length > 2) {
            err.println("Usage: flexirate [input.json [output.json]]");
            return 2;
        }

        String inputJson;
        try {
            inputJson = (args.length >= 1)
                    ? Files.readString(Path.of(args[0]), StandardCharsets.UTF_8)
                    : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            err.println("ERROR: could not read input: " + e.getMessage());
            return 2;
        }

        String outputJson;
        try {
            outputJson = new FlexiRateCalculatorApi().calculate(inputJson);
        } catch (FatalBillingException e) {
            err.println("FATAL: " + e.getMessage());
            return 1;
        }

        printItemLog(outputJson, err);

        try {
            if (args.length == 2) {
                Files.writeString(Path.of(args[1]), outputJson + "\n", StandardCharsets.UTF_8);
            } else {
                out.println(outputJson);
            }
        } catch (IOException e) {
            err.println("ERROR: could not write output: " + e.getMessage());
            return 2;
        }
        return 0;
    }

    /** One stderr line per skipped item and per warning, from the output header. */
    private static void printItemLog(String outputJson, PrintStream err) {
        try {
            JsonNode header = new ObjectMapper().readTree(outputJson).path("header");
            for (JsonNode item : header.path("skipped_items")) {
                err.println("SKIPPED event " + eventIdText(item) + ": " + item.path("reason").asText());
            }
            for (JsonNode item : header.path("warnings")) {
                err.println("WARNING event " + eventIdText(item) + ": " + item.path("reason").asText());
            }
        } catch (IOException e) {
            // The output was produced by our own API a moment ago; if it cannot
            // be re-parsed for logging, the output itself is still authoritative.
        }
    }

    private static String eventIdText(JsonNode item) {
        JsonNode id = item.path("event_id");
        return (id.isMissingNode() || id.isNull()) ? "<none>" : id.asText();
    }
}
