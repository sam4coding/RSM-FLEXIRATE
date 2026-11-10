package com.samwang.flexirate.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.math.BigInteger;

/**
 * Tolerant deserializer for duration_minutes: accepts JSON integer values
 * within int range and strings that parse as an int in range. Anything else
 * (fractional or out-of-range numbers, non-numeric strings, booleans,
 * objects, arrays) deserializes as null, so the event is skipped as an
 * item-level error instead of failing the whole document (SPEC section 4).
 */
public class TolerantDurationDeserializer extends JsonDeserializer<Integer> {

    private static final BigInteger INT_MIN = BigInteger.valueOf(Integer.MIN_VALUE);
    private static final BigInteger INT_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    @Override
    public Integer deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_NUMBER_INT) {
            BigInteger value = p.getBigIntegerValue();
            if (value.compareTo(INT_MIN) >= 0 && value.compareTo(INT_MAX) <= 0) {
                return value.intValue();
            }
            return null;
        }
        if (token == JsonToken.VALUE_STRING) {
            try {
                return Integer.valueOf(p.getText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (token != null && token.isStructStart()) {
            p.skipChildren();
        }
        return null;
    }
}
