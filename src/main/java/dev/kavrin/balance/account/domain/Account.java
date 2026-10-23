package dev.kavrin.balance.account.domain;

import java.util.Objects;

import dev.kavrin.balance.money.Money;

/** An immutable account snapshot; construction does not create or persist an account. */
public record Account(AccountId id, AccountCategory category, Money balance) {

    public Account {
        Objects.requireNonNull(id, "Account ID is required");
        Objects.requireNonNull(category, "Account category is required");
        category.validateBalance(balance);
    }

    public Account applyBalanceDelta(Money delta) {
        // Validate the proposed state before a caller can persist any projection change.
        // ADR-007 still requires balanced postings and one transaction for all accounts.
        return new Account(id, category, balance.add(delta));
    }
}
