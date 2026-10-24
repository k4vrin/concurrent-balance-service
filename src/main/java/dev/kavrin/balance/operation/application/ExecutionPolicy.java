package dev.kavrin.balance.operation.application;

import java.util.Currency;
import java.util.Map;
import java.util.Objects;

/** Retain one snapshot across a new operation's attempts; replay uses its stored decision first. */
public record ExecutionPolicy(String version, SupportedCurrencies supportedCurrencies,
                              AmountLimits limits, Map<Currency, FundingSelection> fundingByCurrency) {
    public ExecutionPolicy {
        IdentityText.requireValid(version);
        Objects.requireNonNull(supportedCurrencies, "Supported currencies are required");
        Objects.requireNonNull(limits, "Amount limits are required");
        fundingByCurrency = Map.copyOf(fundingByCurrency);
        if (!fundingByCurrency.keySet().equals(supportedCurrencies.currencies())) {
            throw new IllegalArgumentException("Each supported currency must have exactly one funding selection");
        }
    }

    public FundingSelection fundingFor(Currency currency) {
        supportedCurrencies.requireSupported(currency);
        return fundingByCurrency.get(currency);
    }
}
