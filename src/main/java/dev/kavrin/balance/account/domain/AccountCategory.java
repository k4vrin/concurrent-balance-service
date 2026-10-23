package dev.kavrin.balance.account.domain;

import java.util.Objects;

import dev.kavrin.balance.money.Money;

public enum AccountCategory {
    CUSTOMER_LIABILITY,
    FUNDING_CLEARING;

    public void validateBalance(Money balance) {
        Objects.requireNonNull(balance, "Balance is required");

        // ADR-007 permits signed synthetic funding balances, not customer overdrafts.
        if (this == CUSTOMER_LIABILITY && balance.amount() < 0) {
            throw new IllegalArgumentException("Customer balance must not be negative");
        }
    }
}
