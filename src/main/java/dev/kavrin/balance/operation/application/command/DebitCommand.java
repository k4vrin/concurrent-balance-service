package dev.kavrin.balance.operation.application.command;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.domain.TransactionId;
import java.util.Objects;

/**
 * Immutable request to debit a customer using a server-selected funding counterpart.
 * Construction checks required values and positive money; available funds and current policy
 * are evaluated by the handler after replay lookup and participant locking.
 *
 * @param transactionId caller identity retained for matching retries and conflict detection
 * @param accountId customer account to debit
 * @param amount positive amount and currency; IRR values are whole rials
 */
public record DebitCommand(TransactionId transactionId, AccountId accountId, Money amount) {

    public DebitCommand {
        Objects.requireNonNull(transactionId, "Transaction ID is required");
        Objects.requireNonNull(accountId, "Account ID is required");
        CommandValidation.requirePositiveAmount(amount);
    }
}
