package dev.kavrin.balance.operation.domain;

import java.util.Objects;

/** A terminal business result value; constructing it does not prove database commit. */
public sealed interface OperationOutcome permits OperationOutcome.Success, OperationOutcome.Rejected {

    TransactionId transactionId();

    OperationType operationType();

    record Success(TransactionId transactionId, OperationType operationType) implements OperationOutcome {

        public Success {
            Objects.requireNonNull(transactionId, "Transaction ID is required");
            Objects.requireNonNull(operationType, "Operation type is required");
        }
    }

    // Business rejection is a value so the financial attempt can commit its outcome
    // without monetary writes. Technical failures must escape that attempt instead.
    record Rejected(TransactionId transactionId, OperationType operationType,
            RejectionReason reason) implements OperationOutcome {

        public Rejected {
            Objects.requireNonNull(transactionId, "Transaction ID is required");
            Objects.requireNonNull(operationType, "Operation type is required");
            Objects.requireNonNull(reason, "Rejection reason is required");
        }
    }
}
