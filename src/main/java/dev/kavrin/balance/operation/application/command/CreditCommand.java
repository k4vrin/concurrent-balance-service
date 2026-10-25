package dev.kavrin.balance.operation.application.command;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.domain.TransactionId;
import java.util.Objects;

/**
 * Immutable intent to credit a customer account using a server-selected funding counterpart.
 * Construction checks required values and a positive amount; account existence, supported
 * currency and financial limits are checked when the application handles the command.
 * Creating a command does not move money or persist an operation.
 *
 * @param transactionId caller-supplied identity used to recognize retries and conflicting requests
 * @param accountId customer account to credit
 * @param amount positive amount and its currency; IRR amounts are whole rials
 */
public record CreditCommand(TransactionId transactionId, AccountId accountId, Money amount) {

    /**
     * Validates the credit intent without performing application or database work.
     *
     * @throws NullPointerException if any component is null
     * @throws IllegalArgumentException if the amount is zero or negative
     */
    public CreditCommand {
        Objects.requireNonNull(transactionId, "Transaction ID is required");
        Objects.requireNonNull(accountId, "Account ID is required");
        CommandValidation.requirePositiveAmount(amount);
    }
}
