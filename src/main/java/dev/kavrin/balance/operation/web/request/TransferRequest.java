package dev.kavrin.balance.operation.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * Carries the strict JSON shape of a public transfer request.
 *
 * <p>Token validation occurs before domain account parsing and distinct-participant checks. The DTO
 * does not reserve identity, check funds or evaluate mutable execution policy.
 *
 * @param transactionId exact caller retry identity
 * @param amountRial positive whole-rial integer amount
 * @param currency requested currency code
 * @param sourceAccountId source account UUID text
 * @param destinationAccountId destination account UUID text
 */
public record TransferRequest(
        @NotNull @JsonDeserialize(using = StrictTextDeserializer.class) String transactionId,
        @NotNull @Positive @JsonDeserialize(using = StrictAmountDeserializer.class) Long amountRial,
        @NotNull @JsonDeserialize(using = StrictTextDeserializer.class) String currency,
        @NotNull @JsonDeserialize(using = StrictTextDeserializer.class) String sourceAccountId,
        @NotNull @JsonDeserialize(using = StrictTextDeserializer.class) String destinationAccountId) {
}
