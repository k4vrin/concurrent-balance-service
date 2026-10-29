package dev.kavrin.balance.operation.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Carries strict JSON fields shared by the credit and debit HTTP routes.
 *
 * <p>Custom deserializers reject text/numeric coercion before command construction. Currency support
 * and business caps remain replay-aware application decisions rather than DTO policy.
 *
 * @param transactionId exact caller retry identity supplied as JSON text
 * @param amountRial positive whole-rial amount supplied as an integer token
 * @param currency requested currency code supplied as JSON text
 */
public record AmountRequest(
        @NotNull @JsonDeserialize(using = StrictTextDeserializer.class) String transactionId,
        @NotNull @Positive @JsonDeserialize(using = StrictAmountDeserializer.class) Long amountRial,
        @NotNull @JsonDeserialize(using = StrictTextDeserializer.class) String currency) {
}
