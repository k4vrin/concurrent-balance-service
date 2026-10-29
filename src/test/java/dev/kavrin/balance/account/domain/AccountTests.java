package dev.kavrin.balance.account.domain;

import java.util.Currency;
import java.util.UUID;

import dev.kavrin.balance.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Checks immutable account snapshots, required fields and category-specific balance validation.
 *
 * <p>Domain construction does not establish database locking or projection/history atomicity.
 */
class AccountTests {

    private static final AccountId ID =
            new AccountId(UUID.fromString("00000000-0000-4000-8000-000000000042"));

    @ParameterizedTest
    @ValueSource(longs = {0, 1, Long.MAX_VALUE})
    void customerSnapshotAcceptsNonnegativeBalance(long amount) {
        assertThat(customer(amount).balance()).isEqualTo(rials(amount));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE})
    void customerSnapshotRejectsNegativeBalance(long amount) {
        assertThatIllegalArgumentException().isThrownBy(() -> customer(amount));
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, 0, Long.MAX_VALUE})
    void fundingSnapshotAcceptsSignedBalance(long amount) {
        assertThat(funding(amount).balance()).isEqualTo(rials(amount));
    }

    @Test
    void requiresAllAccountFields() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Account(null, AccountCategory.CUSTOMER_LIABILITY, rials(0)));
        assertThatNullPointerException().isThrownBy(() -> new Account(ID, null, rials(0)));
        assertThatNullPointerException()
                .isThrownBy(() -> new Account(ID, AccountCategory.CUSTOMER_LIABILITY, null));
    }

    @Test
    void allowsCustomerBalanceToReachZeroWithoutChangingOriginal() {
        var original = customer(100);

        var updated = original.applyBalanceDelta(rials(-100));

        assertThat(updated.balance()).isEqualTo(rials(0));
        assertThat(updated.id()).isEqualTo(original.id());
        assertThat(updated.category()).isEqualTo(original.category());
        assertThat(original.balance()).isEqualTo(rials(100));
    }

    @Test
    void rejectsCustomerOverdraftWithoutChangingOriginal() {
        var original = customer(100);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> original.applyBalanceDelta(rials(-101)));
        assertThat(original.balance()).isEqualTo(rials(100));
    }

    @Test
    void fundingCanCrossZeroInEitherDirection() {
        var original = funding(10);
        var negative = original.applyBalanceDelta(rials(-20));

        assertThat(negative.balance()).isEqualTo(rials(-10));
        assertThat(negative.applyBalanceDelta(rials(20)).balance()).isEqualTo(rials(10));
        assertThat(original.balance()).isEqualTo(rials(10));
    }

    @Test
    void cannotChangeAccountCurrencyThroughDelta() {
        var original = customer(100);
        var dollars = new Money(50, Currency.getInstance("USD"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> original.applyBalanceDelta(dollars));
        assertThat(original.balance()).isEqualTo(rials(100));
    }

    @Test
    void requiresDelta() {
        assertThatNullPointerException().isThrownBy(() -> customer(100).applyBalanceDelta(null));
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, Long.MAX_VALUE})
    void rejectsOverflowWithoutChangingOriginal(long boundary) {
        var original = funding(boundary);
        var delta = boundary == Long.MAX_VALUE ? rials(1) : rials(-1);

        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> original.applyBalanceDelta(delta));
        assertThat(original.balance()).isEqualTo(rials(boundary));
    }

    private Account customer(long amount) {
        return new Account(ID, AccountCategory.CUSTOMER_LIABILITY, rials(amount));
    }

    private Account funding(long amount) {
        return new Account(ID, AccountCategory.FUNDING_CLEARING, rials(amount));
    }

    private Money rials(long amount) {
        return new Money(amount, Currency.getInstance("IRR"));
    }
}
