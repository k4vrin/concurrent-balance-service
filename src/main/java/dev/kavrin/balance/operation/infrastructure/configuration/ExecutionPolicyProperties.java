package dev.kavrin.balance.operation.infrastructure.configuration;

import dev.kavrin.balance.operation.application.policy.AmountLimits;
import dev.kavrin.balance.operation.application.policy.ExecutionPolicy;
import dev.kavrin.balance.operation.application.policy.SupportedCurrencies;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Binds external execution settings before constructing an immutable validated domain policy.
 *
 * <p>Defaults describe the accepted local IRR policy, while funding identities remain explicit.
 * Conversion checks the supported set, caps and counterpart selections; binding alone is not
 * account-existence or locked participant validation.
 *
 * @param version retained execution-policy version
 * @param supportedCurrencies configured currencies admitted for new intent
 * @param maxTransactionAmount inclusive per-mutation amount cap
 * @param maxCustomerBalance inclusive customer projection cap
 * @param funding currency-specific counterpart settings
 */
@ConfigurationProperties("balance.execution")
public record ExecutionPolicyProperties(
        @DefaultValue("execution-v1") String version,
        @DefaultValue("IRR") Set<Currency> supportedCurrencies,
        @DefaultValue("10000000000000") long maxTransactionAmount,
        @DefaultValue("10000000000000") long maxCustomerBalance,
        Map<Currency, FundingProperties> funding) {

    public ExecutionPolicy toPolicy() {
        Objects.requireNonNull(funding, "Funding selections are required");
        var selections = funding.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> entry.getValue().toSelection()));
        return new ExecutionPolicy(version, new SupportedCurrencies(supportedCurrencies),
                new AmountLimits(maxTransactionAmount, maxCustomerBalance), selections);
    }

}
