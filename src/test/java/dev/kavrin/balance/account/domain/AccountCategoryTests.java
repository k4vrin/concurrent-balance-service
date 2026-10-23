package dev.kavrin.balance.account.domain;

import java.util.Currency;

import dev.kavrin.balance.money.Money;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AccountCategoryTests {

    @ParameterizedTest
    @ValueSource(longs = {0, 1, Long.MAX_VALUE})
    void customerBalanceMayBeZeroOrPositive(long amount) {
        assertThatCode(() -> AccountCategory.CUSTOMER_LIABILITY.validateBalance(rials(amount)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, Long.MIN_VALUE})
    void customerBalanceMustNotBeNegative(long amount) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AccountCategory.CUSTOMER_LIABILITY.validateBalance(rials(amount)));
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1, 0, 1, Long.MAX_VALUE})
    void syntheticFundingBalanceMayBeSigned(long amount) {
        assertThatCode(() -> AccountCategory.FUNDING_CLEARING.validateBalance(rials(amount)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(AccountCategory.class)
    void everyCategoryRequiresBalance(AccountCategory category) {
        assertThatNullPointerException()
                .isThrownBy(() -> category.validateBalance(null));
    }

    private Money rials(long amount) {
        return new Money(amount, Currency.getInstance("IRR"));
    }
}
