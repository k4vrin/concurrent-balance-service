package dev.kavrin.balance.operation.domain;

/**
 * Names terminal business decisions retained without a journal or balance mutation.
 *
 * <p>Reasons remain distinct for replay and HTTP mapping. Technical database failures and broken
 * invariants must not be disguised as these committed business outcomes.
 */
public enum RejectionReason {
    ACCOUNT_NOT_FOUND,
    INSUFFICIENT_FUNDS,
    BALANCE_OVERFLOW,
    TRANSACTION_LIMIT_EXCEEDED,
    BALANCE_LIMIT_EXCEEDED
}
