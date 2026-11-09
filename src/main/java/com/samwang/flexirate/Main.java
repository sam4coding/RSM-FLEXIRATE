package com.samwang.flexirate;

/**
 * CLI entry point for the Flexi-Rate Usage Calculator.
 *
 * <p>Usage:
 * <pre>
 *   java -jar flexirate.jar [input.json [output.json]]
 * </pre>
 * No output argument → result is written to stdout.
 * No input argument  → input is read from stdin.
 * FATAL (malformed JSON / missing arrays) → non-zero exit + stderr message, no output JSON.
 *
 * <p>Business logic lives in {@code BillingCalculator}; this class is a thin CLI wrapper only.
 * Implementation is added in T4; this skeleton satisfies T0.
 */
public class Main {

    public static void main(String[] args) {
        // Business logic implemented in T4.
        // T0 skeleton only — no logic here intentionally.
    }
}
