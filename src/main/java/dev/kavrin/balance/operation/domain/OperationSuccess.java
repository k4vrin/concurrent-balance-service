package dev.kavrin.balance.operation.domain;

import java.util.Objects;

public record OperationSuccess(TransactionId transactionId, OperationType operationType) implements OperationOutcome {

    public OperationSuccess {
        Objects.requireNonNull(transactionId, "Transaction ID is required");
        Objects.requireNonNull(operationType, "Operation type is required");
    }
}
