package com.samwang.flexirate;

import java.time.Clock;

/**
 * Public API: accepts JSON string input, returns JSON string output.
 * Thin wrapper over BillingCalculator + Jackson; implementation in T2/T3/T4.
 */
public class FlexiRateCalculatorApi {

    private final Clock clock;

    public FlexiRateCalculatorApi(Clock clock) {
        this.clock = clock;
    }

    public FlexiRateCalculatorApi() {
        this(Clock.systemUTC());
    }

    /**
     * Process a billing input JSON document and return the output JSON.
     *
     * @param inputJson the full billing input JSON
     * @return output JSON document
     * @throws IllegalArgumentException on FATAL parse errors (malformed JSON, missing arrays)
     */
    public String calculate(String inputJson) {
        throw new UnsupportedOperationException("not implemented");
    }
}
