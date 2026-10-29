package dev.kavrin.balance.operation.web.request;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Accepts only integer JSON tokens representable as a Java long.
 *
 * <p>Rejecting fractional, textual and other coerced input prevents monetary truncation or implicit
 * conversion. Positive-amount validation remains the DTO/command boundary's responsibility.
 */
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
