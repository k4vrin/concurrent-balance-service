package dev.kavrin.balance.operation.web;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

public final class StrictAmountDeserializer extends ValueDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) {
        // Jackson's normal numeric coercion can truncate a fractional monetary request.
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return (Long) context.handleUnexpectedToken(Long.class, parser);
        }
        return parser.getLongValue();
    }
}
