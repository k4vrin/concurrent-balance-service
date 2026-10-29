package dev.kavrin.balance.operation.domain;

import java.util.Objects;

/**
 * Represents a successful business outcome associated with one operation identity and type.
 *
 * <p>The value itself is not proof of commit; orchestration may expose it only after the financial
 * transaction and its retained response complete successfully.
 *
 * @param transactionId original caller or controlled opening identity
 * @param operationType type of the completed operation
 */
public record OperationSuccess(TransactionId transactionId, OperationType operationType) implements OperationOutcome {

    public OperationSuccess {
        Objects.requireNonNull(transactionId, "Transaction ID is required");
        Objects.requireNonNull(operationType, "Operation type is required");
    }
}
