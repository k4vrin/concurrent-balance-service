package dev.kavrin.balance.operation.application;

import java.util.Currency;
import java.util.UUID;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.domain.TransactionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MutationCommandTests {

    private static final Currency IRR = Currency.getInstance("IRR");
    private static final TransactionId TRANSACTION_ID = new TransactionId("transfer-001");
    private static final AccountId SOURCE = new AccountId(new UUID(0, 1));
    private static final AccountId DESTINATION = new AccountId(new UUID(0, 2));

    @ParameterizedTest
    @ValueSource(longs = {1, 100, Long.MAX_VALUE})
    void preservesPositiveAmounts(long value) {
        // Long.MAX_VALUE is representable input, not an approved business transaction cap.
        var amount = new Money(value, IRR);

        assertThat(new CreditCommand(TRANSACTION_ID, SOURCE, amount).amount()).isEqualTo(amount);
        assertThat(new DebitCommand(TRANSACTION_ID, SOURCE, amount).amount()).isEqualTo(amount);
        assertThat(new TransferCommand(TRANSACTION_ID, SOURCE, DESTINATION, amount).amount())
                .isEqualTo(amount);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void rejectsNonpositiveAmounts(long value) {
        var amount = new Money(value, IRR);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CreditCommand(TRANSACTION_ID, SOURCE, amount));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DebitCommand(TRANSACTION_ID, SOURCE, amount));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TransferCommand(TRANSACTION_ID, SOURCE, DESTINATION, amount));
    }

    @Test
    void preservesCurrencyForSeparatePolicyValidation() {
        var amount = new Money(100, Currency.getInstance("USD"));

        assertThat(new CreditCommand(TRANSACTION_ID, SOURCE, amount).amount()).isEqualTo(amount);
        assertThat(new DebitCommand(TRANSACTION_ID, SOURCE, amount).amount()).isEqualTo(amount);
        assertThat(new TransferCommand(TRANSACTION_ID, SOURCE, DESTINATION, amount).amount())
                .isEqualTo(amount);
    }

    @Test
    void requiresAmount() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CreditCommand(TRANSACTION_ID, SOURCE, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new DebitCommand(TRANSACTION_ID, SOURCE, null));
        assertThatNullPointerException()
                .isThrownBy(() -> new TransferCommand(TRANSACTION_ID, SOURCE, DESTINATION, null));
    }

    @Test
    void requiresTransactionIdentity() {
        var amount = new Money(100, IRR);

        assertThatNullPointerException().isThrownBy(() -> new CreditCommand(null, SOURCE, amount));
        assertThatNullPointerException().isThrownBy(() -> new DebitCommand(null, SOURCE, amount));
        assertThatNullPointerException()
                .isThrownBy(() -> new TransferCommand(null, SOURCE, DESTINATION, amount));
    }

    @Test
    void requiresEveryRequestedAccountId() {
        var amount = new Money(100, IRR);

        assertThatNullPointerException()
                .isThrownBy(() -> new CreditCommand(TRANSACTION_ID, null, amount));
        assertThatNullPointerException()
                .isThrownBy(() -> new DebitCommand(TRANSACTION_ID, null, amount));
        assertThatNullPointerException()
                .isThrownBy(() -> new TransferCommand(TRANSACTION_ID, null, DESTINATION, amount));
        assertThatNullPointerException()
                .isThrownBy(() -> new TransferCommand(TRANSACTION_ID, SOURCE, null, amount));
    }

    @Test
    void rejectsSelfTransferByIdentityValue() {
        // Distinct wrapper instances still identify the same account.
        var sameAccount = new AccountId(UUID.fromString(SOURCE.value().toString()));

        assertThatIllegalArgumentException().isThrownBy(() ->
                new TransferCommand(TRANSACTION_ID, SOURCE, sameAccount, new Money(100, IRR)));
    }

    @Test
    void preservesTransferDirectionAndTransactionIdentity() {
        var command = new TransferCommand(TRANSACTION_ID, SOURCE, DESTINATION, new Money(100, IRR));

        assertThat(command.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(command.sourceAccountId()).isEqualTo(SOURCE);
        assertThat(command.destinationAccountId()).isEqualTo(DESTINATION);
    }

    @Test
    void constructionDoesNotReserveTransactionIdentity() {
        // A value object cannot enforce uniqueness or identify a payload conflict.
        var amount = new Money(100, IRR);
        var credit = new CreditCommand(TRANSACTION_ID, SOURCE, amount);
        var debit = new DebitCommand(TRANSACTION_ID, SOURCE, amount);
        var transfer = new TransferCommand(TRANSACTION_ID, SOURCE, DESTINATION, amount);

        assertThat(credit.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(debit.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(transfer.transactionId()).isEqualTo(TRANSACTION_ID);
    }
}
