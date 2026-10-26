package dev.kavrin.balance.operation.domain;

import java.util.Objects;

/** A value so terminal rejection can commit without monetary writes; technical failures must escape the attempt. */
public record OperationRejected(TransactionId transactionId, OperationType operationType,
        RejectionReason reason) implements OperationOutcome {

    public OperationRejected {
        Objects.requireNonNull(transactionId, "Transaction ID is required");
        Objects.requireNonNull(operationType, "Operation type is required");
        Objects.requireNonNull(reason, "Rejection reason is required");
    }
}
