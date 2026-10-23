package dev.kavrin.balance.operation.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

public record AmountRequest(
        @NotNull @JsonDeserialize(using = StrictTextDeserializer.class) String transactionId,
        @NotNull @Positive @JsonDeserialize(using = StrictAmountDeserializer.class) Long amountRial,
        @NotNull @JsonDeserialize(using = StrictTextDeserializer.class) String currency) {
}
