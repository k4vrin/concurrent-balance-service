package dev.kavrin.balance.operation.web.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.annotation.JsonDeserialize;

public record TransferRequest(
        @NotNull @JsonDeserialize(using = StrictTextDeserializer.class) String transactionId,
        @NotNull @Positive @JsonDeserialize(using = StrictAmountDeserializer.class) Long amountRial,
        @NotNull @JsonDeserialize(using = StrictTextDeserializer.class) String currency,
        @NotNull @JsonDeserialize(using = StrictTextDeserializer.class) String sourceAccountId,
        @NotNull @JsonDeserialize(using = StrictTextDeserializer.class) String destinationAccountId) {
}
