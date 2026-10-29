package dev.kavrin.balance.ledger.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.kavrin.balance.account.domain.Account;
import dev.kavrin.balance.account.domain.AccountCategory;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import org.junit.jupiter.api.Test;

import static dev.kavrin.balance.account.domain.AccountCategory.CUSTOMER_LIABILITY;
import static dev.kavrin.balance.account.domain.AccountCategory.FUNDING_CLEARING;
import static dev.kavrin.balance.ledger.domain.PostingSide.CREDIT;
import static dev.kavrin.balance.ledger.domain.PostingSide.DEBIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Checks exact projection calculation from immutable journal lines and account snapshots.
 *
 * <p>Cases cover category signs, wide intermediate totals, final bounds and invalid participants.
 * These domain calculations neither persist balances nor prove database transaction semantics.
 */
class JournalProjectionTests {

    private static final Currency IRR = Currency.getInstance("IRR");
    private static final AccountId A = new AccountId(new UUID(0, 1));
    private static final AccountId B = new AccountId(new UUID(0, 2));
    private static final AccountId F = new AccountId(new UUID(0, 3));

    @Test
    void projectsTransferWithoutChangingOriginalSnapshots() {
        var accounts = Map.of(A, account(A, CUSTOMER_LIABILITY, 500),
                B, account(B, CUSTOMER_LIABILITY, 200));
        var journal = new Journal(List.of(posting(A, DEBIT, 100), posting(B, CREDIT, 100)));

        var result = journal.projectBalances(accounts);

        assertThat(result.get(A).balance()).isEqualTo(new Money(400, IRR));
        assertThat(result.get(B).balance()).isEqualTo(new Money(300, IRR));
        assertThat(accounts.get(A).balance().amount()).isEqualTo(500);
        assertThat(accounts.get(B).balance().amount()).isEqualTo(200);
    }

    @Test
    void usesResolvedAccountCategoryForEachPosting() {
        var accounts = Map.of(A, account(A, CUSTOMER_LIABILITY, 500),
                F, account(F, FUNDING_CLEARING, -100));
        var journal = new Journal(List.of(posting(F, DEBIT, 100), posting(A, CREDIT, 100)));

        var result = journal.projectBalances(accounts);

        assertThat(result.get(A).balance().amount()).isEqualTo(600);
        assertThat(result.get(F).balance().amount()).isZero();
    }

    @Test
    void validatesNetResultDespiteTemporaryCustomerOverdraft() {
        // The learner's case: the first debit alone gives -50, but the journal gives 100.
        var accounts = Map.of(A, account(A, CUSTOMER_LIABILITY, 50),
                F, account(F, FUNDING_CLEARING, 0));
        var lines = List.of(posting(A, DEBIT, 100), posting(A, CREDIT, 150),
                posting(F, DEBIT, 50));
        var reversed = new ArrayList<>(lines);
        Collections.reverse(reversed);

        var result = new Journal(lines).projectBalances(accounts);

        assertThat(result.get(A).balance().amount()).isEqualTo(100);
        assertThat(result.get(F).balance().amount()).isEqualTo(50);
        assertThat(new Journal(reversed).projectBalances(accounts)).isEqualTo(result);
    }

    @Test
    void acceptsFinalBalanceWhenIntermediateValueWouldOverflow() {
        var accounts = Map.of(F, account(F, FUNDING_CLEARING, Long.MAX_VALUE));
        var journal = new Journal(List.of(posting(F, DEBIT, 1), posting(F, CREDIT, 1)));

        assertThat(journal.projectBalances(accounts).get(F).balance().amount())
                .isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void supportsNetChangeBeyondLongWhenFinalBalancesFit() {
        // Crossing the full signed range requires a delta of 2^64-1, not a long delta.
        var accounts = Map.of(A, account(A, FUNDING_CLEARING, Long.MIN_VALUE),
                B, account(B, FUNDING_CLEARING, Long.MAX_VALUE));
        var lines = new ArrayList<Posting>();
        for (long amount : new long[] {Long.MAX_VALUE, Long.MAX_VALUE, 1}) {
            lines.add(posting(A, DEBIT, amount));
            lines.add(posting(B, CREDIT, amount));
        }

        var result = new Journal(lines).projectBalances(accounts);

        assertThat(result.get(A).balance().amount()).isEqualTo(Long.MAX_VALUE);
        assertThat(result.get(B).balance().amount()).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void rejectsFinalCustomerOverdraftWithoutChangingInputs() {
        var accounts = Map.of(A, account(A, CUSTOMER_LIABILITY, 50),
                B, account(B, CUSTOMER_LIABILITY, 200));
        var journal = new Journal(List.of(posting(B, CREDIT, 100), posting(A, DEBIT, 100)));

        assertThatIllegalArgumentException().isThrownBy(() -> journal.projectBalances(accounts));
        assertThat(accounts.get(A).balance().amount()).isEqualTo(50);
        assertThat(accounts.get(B).balance().amount()).isEqualTo(200);
    }

    @Test
    void rejectsFinalPositiveOverflow() {
        var accounts = Map.of(A, account(A, CUSTOMER_LIABILITY, Long.MAX_VALUE),
                F, account(F, FUNDING_CLEARING, 0));
        var journal = new Journal(List.of(posting(A, CREDIT, 1), posting(F, DEBIT, 1)));

        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> journal.projectBalances(accounts));
    }

    @Test
    void rejectsFinalNegativeOverflow() {
        var accounts = Map.of(A, account(A, CUSTOMER_LIABILITY, 1),
                F, account(F, FUNDING_CLEARING, Long.MIN_VALUE));
        var journal = new Journal(List.of(posting(A, DEBIT, 1), posting(F, CREDIT, 1)));

        assertThatExceptionOfType(ArithmeticException.class)
                .isThrownBy(() -> journal.projectBalances(accounts));
    }

    @Test
    void rejectsMissingParticipant() {
        var journal = new Journal(List.of(posting(A, DEBIT, 1), posting(B, CREDIT, 1)));

        assertThatIllegalArgumentException().isThrownBy(() -> journal.projectBalances(
                Map.of(A, account(A, CUSTOMER_LIABILITY, 10))));
    }

    @Test
    void rejectsAccountFiledUnderWrongId() {
        var journal = new Journal(List.of(posting(A, DEBIT, 1), posting(B, CREDIT, 1)));
        var wrongAccounts = Map.of(A, account(B, CUSTOMER_LIABILITY, 10),
                B, account(B, CUSTOMER_LIABILITY, 0));

        assertThatIllegalArgumentException().isThrownBy(() -> journal.projectBalances(wrongAccounts));
    }

    @Test
    void rejectsPostingCurrencyDifferentFromAccountCurrency() {
        var journal = new Journal(List.of(posting(A, DEBIT, 1), posting(B, CREDIT, 1)));
        var accounts = Map.of(A, account(A, CUSTOMER_LIABILITY, 10),
                B, new Account(B, CUSTOMER_LIABILITY, new Money(0, Currency.getInstance("USD"))));

        assertThatIllegalArgumentException().isThrownBy(() -> journal.projectBalances(accounts));
    }

    @Test
    void returnsImmutableParticipantsIncludingZeroNetChange() {
        var accounts = new HashMap<>(Map.of(A, account(A, CUSTOMER_LIABILITY, 50),
                B, account(B, CUSTOMER_LIABILITY, 200)));
        var journal = new Journal(List.of(posting(A, DEBIT, 10), posting(A, CREDIT, 10)));

        var result = journal.projectBalances(accounts);
        accounts.clear();

        assertThat(result).containsOnlyKeys(A);
        assertThat(result.get(A)).isEqualTo(account(A, CUSTOMER_LIABILITY, 50));
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(result::clear);
    }

    @Test
    void rejectsNullAccountMapOrSnapshot() {
        var journal = new Journal(List.of(posting(A, DEBIT, 1), posting(B, CREDIT, 1)));
        var accounts = new HashMap<AccountId, Account>();
        accounts.put(A, null);

        assertThatNullPointerException().isThrownBy(() -> journal.projectBalances(null));
        assertThatNullPointerException().isThrownBy(() -> journal.projectBalances(accounts));
    }

    private Account account(AccountId id, AccountCategory category, long balance) {
        return new Account(id, category, new Money(balance, IRR));
    }

    private Posting posting(AccountId id, PostingSide side, long amount) {
        return new Posting(id, side, new Money(amount, IRR));
    }
}
