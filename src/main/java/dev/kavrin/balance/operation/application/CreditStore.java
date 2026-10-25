package dev.kavrin.balance.operation.application;

import dev.kavrin.balance.account.domain.Account;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.ledger.domain.Journal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** All methods participate in the coordinator's owning transaction. */
public interface CreditStore {
    record LockedAccount(Account snapshot, boolean active) { }

    Optional<MutationResult> replay(CreditCommand command, CanonicalFingerprint canonical);
    void reserve(CreditAttempt attempt);
    Map<AccountId, LockedAccount> lockAccounts(List<AccountId> participants);
    void appendJournal(CreditAttempt attempt, Journal journal);
    void applyProjections(Map<AccountId, Account> projections);
    void complete(CreditAttempt attempt, MutationResult result, StoredResponse response);
}
