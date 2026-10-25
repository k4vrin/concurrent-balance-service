package dev.kavrin.balance.operation.web.response;

import dev.kavrin.balance.operation.domain.OperationType;

public record OperationResponse(String transactionId, OperationType operationType,
        String currency, String status) implements OperationResponseBody {
}
