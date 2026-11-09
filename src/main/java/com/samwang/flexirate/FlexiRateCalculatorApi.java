package com.samwang.flexirate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.samwang.flexirate.model.BillingInput;
import com.samwang.flexirate.model.BillingOutput;

import java.time.Clock;

/**
 * Public API: accepts JSON string input, returns JSON string output.
 * Thin wrapper over BillingCalculator + Jackson; implementation in T2/T3/T4.
 */
public class FlexiRateCalculatorApi {

    private final Clock clock;
    private final ObjectMapper mapper;

    public FlexiRateCalculatorApi(Clock clock) {
        this.clock = clock;
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public FlexiRateCalculatorApi() {
        this(Clock.systemUTC());
    }

    /**
     * Process a billing input JSON document and return the output JSON.
     *
     * @param inputJson the full billing input JSON
     * @return output JSON document
     * @throws FatalBillingException on FATAL parse errors (malformed JSON, missing arrays)
     */
    public String calculate(String inputJson) {
        if (inputJson == null || inputJson.isBlank()) {
            throw new FatalBillingException("Input JSON cannot be null or blank");
        }

        JsonNode tree;
        try {
            tree = mapper.readTree(inputJson);
        } catch (JsonProcessingException e) {
            throw new FatalBillingException("Malformed JSON input: " + e.getOriginalMessage(), e);
        }

        if (tree == null || !tree.isObject()) {
            throw new FatalBillingException("Top-level document must be a JSON object");
        }
        if (!tree.has("users") || !tree.get("users").isArray()) {
            throw new FatalBillingException("Missing or invalid 'users' array");
        }
        if (!tree.has("events") || !tree.get("events").isArray()) {
            throw new FatalBillingException("Missing or invalid 'events' array");
        }

        BillingInput input;
        try {
            input = mapper.treeToValue(tree, BillingInput.class);
        } catch (JsonProcessingException e) {
            throw new FatalBillingException("Failed to bind input JSON to model: " + e.getOriginalMessage(), e);
        }

        BillingCalculator calculator = new BillingCalculator(clock);
        BillingOutput output = calculator.calculate(input);

        try {
            return mapper.writeValueAsString(output);
        } catch (JsonProcessingException e) {
            throw new FatalBillingException("Failed to serialize output JSON: " + e.getOriginalMessage(), e);
        }
    }
}
