package dev.kavrin.balance.operation.application;

import java.util.Currency;
import java.util.Objects;
import java.util.Set;

/** A policy snapshot supplied by configuration or a future database adapter. */
public record SupportedCurrencies(Set<Currency> currencies) {

    public SupportedCurrencies {
        currencies = Set.copyOf(currencies);
    }

    public void requireSupported(Currency currency) {
        Objects.requireNonNull(currency, "Currency is required");
        if (!currencies.contains(currency)) {
            throw new IllegalArgumentException("Currency is not supported");
        }
    }
}
