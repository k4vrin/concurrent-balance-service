package dev.kavrin.balance.operation.domain;

/**
 * Identifies public mutations and controlled internal opening operations.
 *
 * <p>Type participates in canonical intent within the global transaction-ID namespace. Reusing an
 * identity with a different type conflicts rather than introducing another financial effect.
 */
public enum OperationType {
    CREDIT,
    DEBIT,
    TRANSFER,
    OPENING
}
