package dev.kavrin.balance.operation.infrastructure.configuration;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.operation.application.ExecutionPolicy;
import java.util.Currency;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionPolicyConfigurationTests {
    private static final String FUNDING_ID = "00000000-0000-4000-8000-000000000007";
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ExecutionPolicyConfiguration.class);

    @Test
    void leavesPolicyAbsentUntilExplicitlyEnabled() {
        runner.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ExecutionPolicy.class));
    }

    @Test
    void disabledPolicyDoesNotBindIncompleteConfiguration() {
        runner.withPropertyValues("balance.execution.enabled=false", "balance.execution.max-transaction-amount=invalid")
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ExecutionPolicy.class));
    }

    @Test
    void createsTheInitialSnapshotFromTypedProperties() {
        enabled().run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(ExecutionPolicy.class);
            var policy = context.getBean(ExecutionPolicy.class);
            assertThat(policy.version()).isEqualTo("execution-v1");
            assertThat(policy.limits().maxTransactionAmount()).isEqualTo(10_000_000_000_000L);
            assertThat(policy.limits().maxCustomerBalance()).isEqualTo(10_000_000_000_000L);
            var selection = policy.fundingFor(Currency.getInstance("IRR"));
            assertThat(selection.counterpartAccountId()).isEqualTo(new AccountId(UUID.fromString(FUNDING_ID)));
            assertThat(selection.policyVersion()).isEqualTo("funding-v1");
        });
    }

    @Test
    void acceptsExplicitPolicyOverridesWithoutHardcodingRial() {
        runner.withPropertyValues("balance.execution.enabled=true", "balance.execution.version=execution-v2",
                        "balance.execution.supported-currencies=USD", "balance.execution.max-transaction-amount=100",
                        "balance.execution.max-customer-balance=1000", "balance.execution.funding[USD].account-id=" + FUNDING_ID,
                        "balance.execution.funding[USD].policy-version=funding-v2")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var policy = context.getBean(ExecutionPolicy.class);
                    assertThat(policy.version()).isEqualTo("execution-v2");
                    assertThat(policy.supportedCurrencies().currencies()).containsExactly(Currency.getInstance("USD"));
                    assertThat(policy.limits().maxTransactionAmount()).isEqualTo(100);
                    assertThat(policy.limits().maxCustomerBalance()).isEqualTo(1000);
                    assertThat(policy.fundingFor(Currency.getInstance("USD")).policyVersion()).isEqualTo("funding-v2");
                });
    }

    @Test
    void enabledPolicyFailsStartupWithoutFundingConfiguration() {
        runner.withPropertyValues("balance.execution.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("Funding selections are required");
        });
    }

    @Test
    void applicationYamlResolvesTheDocumentedEnvironmentPlaceholders() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("EXECUTION_POLICY_ENABLED=true", "EXECUTION_IRR_FUNDING_ACCOUNT_ID=" + FUNDING_ID,
                        "EXECUTION_IRR_FUNDING_POLICY_VERSION=funding-env-v1")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ExecutionPolicy.class);
                    var funding = context.getBean(ExecutionPolicy.class).fundingFor(Currency.getInstance("IRR"));
                    assertThat(funding.counterpartAccountId()).isEqualTo(new AccountId(UUID.fromString(FUNDING_ID)));
                    assertThat(funding.policyVersion()).isEqualTo("funding-env-v1");
                });
    }

    @Test
    void applicationYamlDoesNotInventMissingFundingValues() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("EXECUTION_POLICY_ENABLED=true", "EXECUTION_IRR_FUNDING_ACCOUNT_ID=",
                        "EXECUTION_IRR_FUNDING_POLICY_VERSION=")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"balance.execution.version=", "balance.execution.max-transaction-amount=0",
            "balance.execution.max-customer-balance=-1", "balance.execution.supported-currencies=invalid",
            "balance.execution.funding[IRR].account-id=invalid", "balance.execution.funding[IRR].policy-version="})
    void invalidEnabledConfigurationFailsStartup(String property) {
        enabled().withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void policyActivationDependsOnItsExplicitSettingInsteadOfATestProfile() {
        enabled().withInitializer(context -> context.getEnvironment().setActiveProfiles("smoke"))
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(ExecutionPolicy.class));
    }

    private ApplicationContextRunner enabled() {
        return runner.withPropertyValues("balance.execution.enabled=true",
                "balance.execution.funding[IRR].account-id=" + FUNDING_ID,
                "balance.execution.funding[IRR].policy-version=funding-v1");
    }
}
