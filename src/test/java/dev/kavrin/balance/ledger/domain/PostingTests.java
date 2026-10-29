package dev.kavrin.balance.ledger.domain;

import java.util.Currency;
import java.util.UUID;

import dev.kavrin.balance.account.domain.AccountCategory;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Checks positive immutable posting lines and category-dependent balance deltas.
 *
 * <p>Debit/credit signs are verified for both account categories, including representable long boundaries.
 */
class PostingTests {

    private static final AccountId ID =
            new AccountId(UUID.fromString("10000000-0000-4000-8000-000000000001"));

    @ParameterizedTest
    @ValueSource(longs = {1, Long.MAX_VALUE})
    void preservesPositivePostingAmounts(long amount) {
        assertThat(new Posting(ID, PostingSide.DEBIT, rials(amount)).amount())
                .isEqualTo(rials(amount));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonpositivePostingAmounts(long amount) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Posting(ID, PostingSide.CREDIT, rials(amount)));
    }

    @Test
    void requiresEveryPostingField() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Posting(null, PostingSide.DEBIT, rials(100)));
        assertThatNullPointerException().isThrownBy(() -> new Posting(ID, null, rials(100)));
        assertThatNullPointerException().isThrownBy(() -> new Posting(ID, PostingSide.DEBIT, null));
    }

    @ParameterizedTest
    @CsvSource({
            "CUSTOMER_LIABILITY, CREDIT, 100",
            "CUSTOMER_LIABILITY, DEBIT, -100",
            "FUNDING_CLEARING, DEBIT, 100",
            "FUNDING_CLEARING, CREDIT, -100"
    })
    void derivesBalanceSignFromAccountCategory(AccountCategory category, PostingSide side, long expected) {
        var posting = new Posting(ID, side, rials(100));

        assertThat(posting.balanceDelta(category)).isEqualTo(rials(expected));
        assertThat(posting.amount()).isEqualTo(rials(100));
    }

    @Test
    void largestPositiveAmountHasRepresentableNegativeDelta() {
        var debit = new Posting(ID, PostingSide.DEBIT, rials(Long.MAX_VALUE));
        var credit = new Posting(ID, PostingSide.CREDIT, rials(Long.MAX_VALUE));

        assertThat(debit.balanceDelta(AccountCategory.CUSTOMER_LIABILITY))
                .isEqualTo(rials(-Long.MAX_VALUE));
        assertThat(credit.balanceDelta(AccountCategory.FUNDING_CLEARING))
                .isEqualTo(rials(-Long.MAX_VALUE));
    }

    @Test
    void requiresCategoryForBalanceDelta() {
        var posting = new Posting(ID, PostingSide.CREDIT, rials(100));

        assertThatNullPointerException().isThrownBy(() -> posting.balanceDelta(null));
    }

    private Money rials(long amount) {
        return new Money(amount, Currency.getInstance("IRR"));
    }
}
