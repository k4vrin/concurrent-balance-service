package dev.kavrin.balance.operation.application.policy;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Checks inclusive transaction and customer-balance caps as independent policy settings.
 *
 * <p>Positive configured bounds and exact-cap cases are portable policy evidence, not persisted rejection tests.
 */
class AmountLimitsTests {
    private static final long CAP = 10_000_000_000_000L;
    private final AmountLimits limits = new AmountLimits(CAP, CAP);

    @Test
    void acceptsPositiveTransactionAmountsThroughTheExactCap() {
        assertThat(limits.acceptsTransactionAmount(1)).isTrue();
        assertThat(limits.acceptsTransactionAmount(CAP)).isTrue();
        assertThat(limits.acceptsTransactionAmount(CAP + 1)).isFalse();
        assertThat(limits.acceptsTransactionAmount(Long.MAX_VALUE)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1, 0})
    void doesNotAcceptNonpositiveMutationAmounts(long amount) {
        assertThat(limits.acceptsTransactionAmount(amount)).isFalse();
    }

    @Test
    void acceptsZeroAndExactCustomerBalanceCap() {
        assertThat(limits.acceptsCustomerBalance(0)).isTrue();
        assertThat(limits.acceptsCustomerBalance(CAP)).isTrue();
        assertThat(limits.acceptsCustomerBalance(-1)).isFalse();
        assertThat(limits.acceptsCustomerBalance(CAP + 1)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1, 0})
    void rejectsInvalidConfiguredCaps(long cap) {
        assertThatIllegalArgumentException().isThrownBy(() -> new AmountLimits(cap, CAP));
        assertThatIllegalArgumentException().isThrownBy(() -> new AmountLimits(CAP, cap));
    }

    @Test
    void treatsTransactionAndCustomerCapsAsSeparateSettings() {
        var separate = new AmountLimits(100, 1_000);
        assertThat(separate.acceptsTransactionAmount(101)).isFalse();
        assertThat(separate.acceptsCustomerBalance(101)).isTrue();
    }
}
