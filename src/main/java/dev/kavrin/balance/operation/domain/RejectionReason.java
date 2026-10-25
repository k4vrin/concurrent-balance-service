package dev.kavrin.balance.operation.domain;

public enum RejectionReason {
    ACCOUNT_NOT_FOUND,
    INSUFFICIENT_FUNDS,
    BALANCE_OVERFLOW,
    TRANSACTION_LIMIT_EXCEEDED,
    BALANCE_LIMIT_EXCEEDED
}
