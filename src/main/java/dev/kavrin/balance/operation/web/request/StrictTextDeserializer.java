package dev.kavrin.balance.operation.web.request;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Accepts only JSON string tokens for identity and currency fields.
 *
 * <p>Numbers and other token types are rejected rather than converted to text, preserving the API's
 * explicit input contract before domain parsing or canonicalization.
 */
public final class StrictTextDeserializer extends ValueDeserializer<String> {
    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return (String) context.handleUnexpectedToken(String.class, parser);
        }
        return parser.getString();
    }
}
