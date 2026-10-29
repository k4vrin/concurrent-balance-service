package dev.kavrin.balance.account.domain;

import java.util.Objects;

import dev.kavrin.balance.money.Money;

/**
 * Distinguishes customer liabilities from synthetic funding/clearing accounts.
 *
 * <p>Customer balances cannot be negative; funding balances may span the signed-long range.
 * Posting signs depend on the category's accounting normal side rather than on a universal meaning
 * of debit or credit (ADR-007).
 */
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
