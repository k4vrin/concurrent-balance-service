package dev.kavrin.balance.operation.application;

import dev.kavrin.balance.account.domain.AccountId;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ExecutionPolicyTests {
    private static final Currency IRR = Currency.getInstance("IRR");
    private static final Currency USD = Currency.getInstance("USD");
    private static final SupportedCurrencies CURRENCIES = new SupportedCurrencies(Set.of(IRR));
    private static final AmountLimits LIMITS = new AmountLimits(10_000_000_000_000L, 10_000_000_000_000L);
    private static final FundingSelection FUNDING = new FundingSelection(new AccountId(UUID.randomUUID()), "funding-v1");

    @Test
    void returnsTheServerSelectedCounterpartAndRetainsSeparatePolicyVersions() {
        var policy = new ExecutionPolicy("execution-v1", CURRENCIES, LIMITS, Map.of(IRR, FUNDING));

        assertThat(policy.fundingFor(IRR)).isEqualTo(FUNDING);
        assertThat(policy.version()).isEqualTo("execution-v1");
        assertThat(policy.fundingFor(IRR).policyVersion()).isEqualTo("funding-v1");
    }

    @Test
    void ownsTheFundingMapSoChangingALoaderCannotChangeAnExistingAttempt() {
        var supplied = new HashMap<>(Map.of(IRR, FUNDING));
        var retained = new ExecutionPolicy("execution-v1", CURRENCIES, LIMITS, supplied);
        var replacement = new FundingSelection(new AccountId(UUID.randomUUID()), "funding-v2");
        supplied.put(IRR, replacement);
        var next = new ExecutionPolicy("execution-v2", CURRENCIES, new AmountLimits(20, 30), supplied);

        assertThat(retained.fundingFor(IRR)).isEqualTo(FUNDING);
        assertThat(retained.limits()).isEqualTo(LIMITS);
        assertThat(next.fundingFor(IRR)).isEqualTo(replacement);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> retained.fundingByCurrency().put(IRR, replacement));
    }

    @Test
    void rejectsMissingCounterpartsAsInvalidConfiguration() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExecutionPolicy("v1", CURRENCIES, LIMITS, Map.of()));
    }

    @Test
    void rejectsFundingForCurrenciesOutsideTheSupportedSet() {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExecutionPolicy("v1", CURRENCIES, LIMITS, Map.of(IRR, FUNDING, USD, FUNDING)));
    }

    @Test
    void rejectsUnsupportedLookupAndDoesNotHardcodeRial() {
        var policy = new ExecutionPolicy("v1", new SupportedCurrencies(Set.of(USD)), LIMITS, Map.of(USD, FUNDING));

        assertThat(policy.fundingFor(USD)).isEqualTo(FUNDING);
        assertThatIllegalArgumentException().isThrownBy(() -> policy.fundingFor(IRR));
    }

    @Test
    void permitsAnExplicitSnapshotWithAllCurrenciesDisabled() {
        var policy = new ExecutionPolicy("disabled-v1", new SupportedCurrencies(Set.of()), LIMITS, Map.of());

        assertThatIllegalArgumentException().isThrownBy(() -> policy.fundingFor(IRR));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\uD800"})
    void rejectsInvalidVersionText(String version) {
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ExecutionPolicy(version, CURRENCIES, LIMITS, Map.of(IRR, FUNDING)));
    }

    @Test
    void rejectsMissingSnapshotParts() {
        assertThatNullPointerException().isThrownBy(() -> new ExecutionPolicy(null, CURRENCIES, LIMITS, Map.of(IRR, FUNDING)));
        assertThatNullPointerException().isThrownBy(() -> new ExecutionPolicy("v1", null, LIMITS, Map.of(IRR, FUNDING)));
        assertThatNullPointerException().isThrownBy(() -> new ExecutionPolicy("v1", CURRENCIES, null, Map.of(IRR, FUNDING)));
        assertThatNullPointerException().isThrownBy(() -> new ExecutionPolicy("v1", CURRENCIES, LIMITS, null));
    }
}
