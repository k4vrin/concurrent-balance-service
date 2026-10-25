package dev.kavrin.balance.operation.application;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;

/**
 * Application boundary used by HTTP controllers for balance mutations and lookup.
 * Implementations must return mutation results only after confirmed commit, including retained
 * terminal rejections. Matching retries return the original result without posting again;
 * reuse of an identity for different intent is a conflict. Infrastructure failures and uncertain
 * commits must not be reported as successful results.
 *
 * <p>This interface defines the contract; the staged runtime may report operations as unavailable
 * until their handlers are wired. It does not itself implement persistence or transaction control.
 */
public interface BalanceOperations {
    /**
     * Credits the customer account using a server-selected funding counterpart.
     *
     * @param command validated credit intent and retry identity
     * @return the committed success or terminal rejection, or the original result on matching replay
     * @throws OperationFailure if the request conflicts or the operation cannot supply a committed result
     */
    MutationResult credit(CreditCommand command);

    /**
     * Debits the customer account when funds and policy permit, with a funding counterpart.
     *
     * @param command validated debit intent and retry identity
     * @return the committed success or terminal rejection, or the original result on matching replay
     * @throws OperationFailure if the request conflicts or the operation cannot supply a committed result
     */
    MutationResult debit(DebitCommand command);

    /**
     * Moves money between distinct accounts atomically when funds and policy permit.
     *
     * @param command validated source, destination, amount and retry identity
     * @return the committed success or terminal rejection, or the original result on matching replay
     * @throws OperationFailure if the request conflicts or the operation cannot supply a committed result
     */
    MutationResult transfer(TransferCommand command);

    /**
     * Reads an account's authoritative committed balance and currency without creating a mutation.
     *
     * @param accountId account whose balance is requested
     * @return the stored balance projection and its currency
     * @throws OperationFailure if the account is missing or balance lookup is unavailable
     */
    Money balance(AccountId accountId);
}
