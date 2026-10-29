package dev.kavrin.balance.operation.application.policy;


import java.util.Currency;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Checks immutable configured currency sets, including an explicitly empty admission policy.
 *
 * <p>Currency support is supplied by the snapshot rather than hardcoded into monetary value construction.
 */
class SupportedCurrenciesTests {

    private static final Currency IRR = Currency.getInstance("IRR");
    private static final Currency USD = Currency.getInstance("USD");

    @ParameterizedTest
    @ValueSource(strings = {"IRR", "USD", "EUR"})
    void acceptsEveryCurrencyInSuppliedSet(String code) {
        var policy = new SupportedCurrencies(Set.of(IRR, USD, Currency.getInstance("EUR")));

        policy.requireSupported(Currency.getInstance(code));
    }

    @Test
    void rejectsCurrencyOutsideSuppliedSet() {
        var policy = new SupportedCurrencies(Set.of(IRR));

        assertThatIllegalArgumentException().isThrownBy(() -> policy.requireSupported(USD));
    }

    @Test
    void doesNotRequireRialInSupportedSet() {
        var policy = new SupportedCurrencies(Set.of(USD));

        policy.requireSupported(USD);
        assertThatIllegalArgumentException().isThrownBy(() -> policy.requireSupported(IRR));
    }

    @Test
    void emptySetDisablesAllCurrencies() {
        var policy = new SupportedCurrencies(Set.of());

        assertThatIllegalArgumentException().isThrownBy(() -> policy.requireSupported(IRR));
    }

    @Test
    void ownsImmutablePolicySnapshot() {
        var source = new HashSet<>(Set.of(IRR));
        var policy = new SupportedCurrencies(source);

        source.clear();
        source.add(USD);

        policy.requireSupported(IRR);
        assertThatIllegalArgumentException().isThrownBy(() -> policy.requireSupported(USD));
        assertThat(policy.currencies()).containsExactly(IRR);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> policy.currencies().add(USD));
    }

    @Test
    void rejectsMissingSetOrSetMember() {
        var currencies = new HashSet<Currency>();
        currencies.add(null);

        assertThatNullPointerException().isThrownBy(() -> new SupportedCurrencies(null));
        assertThatNullPointerException().isThrownBy(() -> new SupportedCurrencies(currencies));
    }

    @Test
    void requiresCurrencyToValidate() {
        var policy = new SupportedCurrencies(Set.of(IRR));

        assertThatNullPointerException().isThrownBy(() -> policy.requireSupported(null));
    }
}
