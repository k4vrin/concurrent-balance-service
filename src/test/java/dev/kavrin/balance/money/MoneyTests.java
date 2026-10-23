package dev.kavrin.balance.money;

import java.util.Currency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MoneyTests {

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1, 0, 1, 100, 150_000, Long.MAX_VALUE})
    void preservesRialAmountsWithoutConversion(long amount) {
        var currency = Currency.getInstance("IRR");

        var money = new Money(amount, currency);

        assertThat(money.amount()).isEqualTo(amount);
        assertThat(money.currency()).isEqualTo(currency);
    }

    @Test
    void requiresCurrency() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Money(100, null));
    }

    @Test
    void addsWithoutChangingOperands() {
        var first = rials(100);
        var second = rials(50);

        assertThat(first.add(second)).isEqualTo(rials(150));
        assertThat(first).isEqualTo(rials(100));
        assertThat(second).isEqualTo(rials(50));
    }

    @ParameterizedTest
    @CsvSource({"100, 100, 0", "100, 150, -50"})
    void subtractionCanProduceZeroOrSignedAmounts(long amount, long debit, long expected) {
        assertThat(rials(amount).subtract(rials(debit))).isEqualTo(rials(expected));
    }

    @Test
    void permitsExactArithmeticBoundaries() {
        assertThat(rials(Long.MAX_VALUE - 1).add(rials(1))).isEqualTo(rials(Long.MAX_VALUE));
        assertThat(rials(Long.MIN_VALUE + 1).subtract(rials(1))).isEqualTo(rials(Long.MIN_VALUE));
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, Long.MAX_VALUE})
    void rejectsAdditionOverflow(long boundary) {
        var delta = boundary == Long.MAX_VALUE ? rials(1) : rials(-1);

        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> rials(boundary).add(delta));
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, Long.MAX_VALUE})
    void rejectsSubtractionOverflow(long boundary) {
        var delta = boundary == Long.MIN_VALUE ? rials(1) : rials(-1);

        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> rials(boundary).subtract(delta));
    }

    @Test
    void comparesWithoutOverflow() {
        assertThat(rials(Long.MIN_VALUE).compareTo(rials(Long.MAX_VALUE))).isNegative();
        assertThat(rials(Long.MAX_VALUE).compareTo(rials(Long.MIN_VALUE))).isPositive();
        assertThat(rials(100).compareTo(rials(100))).isZero();
    }

    @Test
    void rejectsCurrencyMismatchForEveryOperation() {
        var usd = new Money(100, Currency.getInstance("USD"));

        assertThatIllegalArgumentException().isThrownBy(() -> rials(100).add(usd));
        assertThatIllegalArgumentException().isThrownBy(() -> rials(100).subtract(usd));
        assertThatIllegalArgumentException().isThrownBy(() -> rials(100).compareTo(usd));
    }

    @Test
    void rejectsMissingOperandForEveryOperation() {
        assertThatNullPointerException().isThrownBy(() -> rials(100).add(null));
        assertThatNullPointerException().isThrownBy(() -> rials(100).subtract(null));
        assertThatNullPointerException().isThrownBy(() -> rials(100).compareTo(null));
    }

    private Money rials(long amount) {
        return new Money(amount, Currency.getInstance("IRR"));
    }
}
