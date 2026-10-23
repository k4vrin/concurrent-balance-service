package dev.kavrin.balance.money;

import java.util.Currency;
import java.util.Objects;

/**
 * An amount and its currency. For IRR, 100 means 100 rials without scaling.
 * Commands, postings and account categories enforce their own sign rules.
 */
public record Money(long amount, Currency currency) {

    public Money {
        Objects.requireNonNull(currency, "Currency is required");
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(amount, other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(amount, other.amount), currency);
    }

    /** Compares amounts only when both values use the same currency. */
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(amount, other.amount);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "Money is required");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("Money currencies must match");
        }
    }
}
