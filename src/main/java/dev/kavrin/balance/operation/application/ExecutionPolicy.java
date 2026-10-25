package dev.kavrin.balance.operation.application;

import java.util.Currency;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable policy snapshot for evaluating a new operation. Retain the same snapshot across
 * that operation's attempts; replay must consult its stored decision before current policy.
 * Funding selections are copied into an unmodifiable map.
 *
 * @param version nonblank, valid-Unicode execution-policy identity
 * @param supportedCurrencies currencies permitted for new operations
 * @param limits transaction-amount and customer-balance caps
 * @param fundingByCurrency exactly one server-selected funding counterpart per supported currency
 */
public record ExecutionPolicy(String version, SupportedCurrencies supportedCurrencies,
                              AmountLimits limits, Map<Currency, FundingSelection> fundingByCurrency) {
    /**
     * Validates the policy identity and requires funding-map keys to match the supported set exactly.
     *
     * @throws NullPointerException if any component, funding-map key or funding-map value is null
     * @throws IllegalArgumentException if the version is blank or malformed Unicode, or the
     *         funding-map keys differ from the supported currencies
     */
    public ExecutionPolicy {
        IdentityText.requireValid(version);
        Objects.requireNonNull(supportedCurrencies, "Supported currencies are required");
        Objects.requireNonNull(limits, "Amount limits are required");
        fundingByCurrency = Map.copyOf(fundingByCurrency);
        if (!fundingByCurrency.keySet().equals(supportedCurrencies.currencies())) {
            throw new IllegalArgumentException("Each supported currency must have exactly one funding selection");
        }
    }

    /**
     * Looks up the funding selection from this snapshot after checking currency support.
     *
     * @param currency currency of a new operation
     * @return the configured funding counterpart and funding-policy version
     * @throws NullPointerException if the currency is null
     * @throws IllegalArgumentException if the currency is unsupported
     */
    public FundingSelection fundingFor(Currency currency) {
        supportedCurrencies.requireSupported(currency);
        return fundingByCurrency.get(currency);
    }
}
