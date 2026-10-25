package dev.kavrin.balance.operation.infrastructure.configuration;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.operation.application.policy.AmountLimits;
import dev.kavrin.balance.operation.application.policy.ExecutionPolicy;
import dev.kavrin.balance.operation.application.policy.FundingSelection;
import dev.kavrin.balance.operation.application.policy.SupportedCurrencies;
import java.util.Currency;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

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

    public record FundingProperties(UUID accountId, String policyVersion) {
        FundingSelection toSelection() {
            return new FundingSelection(new AccountId(accountId), policyVersion);
        }
    }
}
