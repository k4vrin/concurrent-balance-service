package dev.kavrin.balance.ledger.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class JournalTests {

    @Test
    void acceptsBalancedTwoLineJournal() {
        var lines = List.of(debit(100), credit(100));

        assertThat(new Journal(lines).postings()).containsExactlyElementsOf(lines);
    }

    @Test
    void acceptsBalancedJournalWithMoreThanTwoLines() {
        var lines = List.of(debit(100), credit(60), credit(30), credit(10));

        assertThat(new Journal(lines).postings()).hasSize(4);
    }

    @Test
    void usesExactTotalsEvenWhenBalancedSumExceedsLong() {
        var lines = List.of(debit(Long.MAX_VALUE), debit(Long.MAX_VALUE),
                credit(Long.MAX_VALUE), credit(Long.MAX_VALUE));

        assertThat(new Journal(lines).postings()).hasSize(4);
    }

    @Test
    void overflowCannotDisguiseAnUnbalancedJournal() {
        // These debits total 2^64 + 1, which would wrap to the credit total of 1 in long.
        var lines = List.of(debit(Long.MAX_VALUE), debit(Long.MAX_VALUE), debit(3), credit(1));

        assertThatIllegalArgumentException().isThrownBy(() -> new Journal(lines));
    }

    @Test
    void requiresAtLeastTwoLines() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Journal(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new Journal(List.of(debit(100))));
    }

    @ParameterizedTest
    @EnumSource(PostingSide.class)
    void rejectsJournalWithOnlyOneSide(PostingSide side) {
        var lines = List.of(posting(1, side, 100, "IRR"), posting(2, side, 100, "IRR"));

        assertThatIllegalArgumentException().isThrownBy(() -> new Journal(lines));
    }

    @Test
    void rejectsDifferentCurrenciesDespiteEqualNumbers() {
        var lines = List.of(posting(1, PostingSide.DEBIT, 100, "IRR"),
                posting(2, PostingSide.CREDIT, 100, "USD"));

        assertThatIllegalArgumentException().isThrownBy(() -> new Journal(lines));
    }

    @Test
    void rejectsMissingPostingCollectionOrLine() {
        assertThatNullPointerException().isThrownBy(() -> new Journal(null));
        assertThatNullPointerException()
                .isThrownBy(() -> new Journal(Arrays.asList(debit(100), null)));
    }

    @Test
    void ownsImmutableCopyOfPostingList() {
        var source = new ArrayList<>(List.of(debit(100), credit(100)));
        var journal = new Journal(source);

        source.clear();

        assertThat(journal.postings()).hasSize(2);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> journal.postings().clear());
    }

    @Test
    void rejectsUnequalTotals() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Journal(List.of(debit(100), credit(99))));
    }

    private Posting debit(long amount) {
        return posting(1, PostingSide.DEBIT, amount, "IRR");
    }

    private Posting credit(long amount) {
        return posting(2, PostingSide.CREDIT, amount, "IRR");
    }

    private Posting posting(long id, PostingSide side, long amount, String currency) {
        return new Posting(new AccountId(new UUID(0, id)), side,
                new Money(amount, Currency.getInstance(currency)));
    }
}
