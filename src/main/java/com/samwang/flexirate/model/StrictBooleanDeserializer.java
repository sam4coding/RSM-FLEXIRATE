package com.samwang.flexirate.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/**
 * Strict boolean deserializer: only JSON boolean literals (true / false) are accepted.
 * Non-boolean values (strings, numbers, objects, arrays) deserialize as null,
 * causing user validation to reject the record per SPEC §2 and DECISIONS #14.
 */
public class StrictBooleanDeserializer extends JsonDeserializer<Boolean> {

    @Override
    public Boolean deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_TRUE) {
            return Boolean.TRUE;
        } else if (token == JsonToken.VALUE_FALSE) {
            return Boolean.FALSE;
        }
        p.skipChildren();
        return null;
    }
}
