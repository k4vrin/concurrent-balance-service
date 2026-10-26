package dev.kavrin.balance.operation.application.debit;

import dev.kavrin.balance.account.application.LockedAccount;
import dev.kavrin.balance.account.domain.Account;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.ledger.domain.Journal;
import dev.kavrin.balance.operation.application.command.DebitCommand;
import dev.kavrin.balance.operation.application.execution.DuplicateOperation;
import dev.kavrin.balance.operation.application.identity.CanonicalFingerprint;
import dev.kavrin.balance.operation.application.result.MutationResult;
import dev.kavrin.balance.operation.application.result.OperationFailure;
import dev.kavrin.balance.operation.application.result.StoredResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistence port for durable debit execution and replay. All methods participate in the
 * coordinator's owning transaction and must not commit independently. The handler composes
 * reservation, immutable history, balance projections and completion into one atomic unit
 * (ADR-007 and ADR-010); a terminal rejection completes without monetary writes.
 */
public interface DebitStore {

    /**
     * Looks up a completed request by transaction ID and compares its canonical intent.
     * Does not reserve identity, post money or reevaluate current execution policy.
     *
     * @param command requested debit identity and intent
     * @param canonical canonical request representation used to detect conflicting reuse
     * @return the original outcome, currency and response, or empty when no operation exists
     * @throws OperationFailure if the transaction ID is bound to a different request
     * @throws IllegalStateException if stored canonical data or terminal state is unusable
     */
    Optional<MutationResult> replay(DebitCommand command, CanonicalFingerprint canonical);

    /**
     * Reserves a pending operation with its canonical intent and execution/funding metadata.
     * The reservation must complete or roll back within the same transaction.
     *
     * @param attempt new operation identity, command and policy snapshot
     * @throws DuplicateOperation if another operation has reserved the same transaction ID;
     *         recovery must occur after this transaction has rolled back
     */
    void reserve(DebitAttempt attempt);

    /**
     * Acquires participant locks in the shared application-defined account-ID order before
     * financial checks. Locks must remain held until transaction completion (ADR-010).
     *
     * @param participants complete customer and funding account set
     * @return locked account snapshots keyed by ID; missing accounts are absent
     */
    Map<AccountId, LockedAccount> lockAccounts(List<AccountId> participants);

    /**
     * Appends the journal header and immutable postings for a successful debit.
     * The caller must validate all proposed projections before invoking this method.
     *
     * @param attempt operation and journal identities linking history to the debit
     * @param journal balanced postings to persist
     */
    void appendJournal(DebitAttempt attempt, Journal journal);

    /**
     * Updates the already-locked accounts to the balances calculated from the journal.
     * These writes share the transaction with history and operation completion.
     *
     * @param projections validated proposed account states keyed by account ID
     */
    void applyProjections(Map<AccountId, Account> projections);

    /**
     * Transitions the pending reservation to success or terminal rejection and retains its
     * original response. This writes completion state; the coordinator still owns commit.
     *
     * @param attempt reservation to complete
     * @param result terminal outcome and original currency
     * @param response serialized response retained for subsequent matching requests
     * @throws IllegalStateException if the reservation cannot be completed exactly once
     */
    void complete(DebitAttempt attempt, MutationResult result, StoredResponse response);
}
