package dev.kavrin.balance.operation.application;

import dev.kavrin.balance.account.domain.Account;
import dev.kavrin.balance.account.domain.AccountCategory;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.ledger.domain.Journal;
import dev.kavrin.balance.ledger.domain.Posting;
import dev.kavrin.balance.ledger.domain.PostingSide;
import dev.kavrin.balance.operation.domain.OperationOutcome;
import dev.kavrin.balance.operation.domain.OperationType;
import dev.kavrin.balance.operation.domain.RejectionReason;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

@Service
@ConditionalOnProperty(prefix = "balance.execution", name = "enabled", havingValue = "true")
public final class CreditHandler {
    private final OperationCoordinator coordinator;
    private final ExecutionPolicy policy;
    private final CreditStore store;
    private final CreditResponseEncoder encoder;

    public CreditHandler(OperationCoordinator coordinator, ExecutionPolicy policy, CreditStore store, CreditResponseEncoder encoder) {
        this.coordinator = coordinator;
        this.policy = policy;
        this.store = store;
        this.encoder = encoder;
    }

    public MutationResult handle(CreditCommand command) {
        try {
            return process(command);
        } catch (DataAccessException | TransactionException unavailable) {
            throw new OperationFailure(OperationFailure.Reason.UNAVAILABLE, unavailable);
        }
    }

    private MutationResult process(CreditCommand command) {
        var canonical = CanonicalRequest.credit(command, Optional.empty());
        // ADR-010: completed identity is authoritative before current currency, cap or funding policy.
        var existing = coordinator.execute(() -> recover(command, canonical.fingerprint()));
        if (existing.isPresent()) { return existing.get(); }
        if (!policy.supportedCurrencies().currencies().contains(command.amount().currency())) {
            throw new OperationFailure(OperationFailure.Reason.UNSUPPORTED_CURRENCY);
        }
        var funding = policy.fundingFor(command.amount().currency());
        if (funding.counterpartAccountId().equals(command.accountId())) {
            throw new IllegalStateException("Funding counterpart must differ from the customer");
        }
        var attempt = new CreditAttempt(UUID.randomUUID(), UUID.randomUUID(), command, canonical, policy, funding);
        try {
            return coordinator.execute(() -> execute(attempt));
        } catch (DuplicateOperation duplicate) {
            // The failed reservation has left its transaction before committed-result recovery.
            return coordinator.execute(() -> recover(command, canonical.fingerprint()))
                    .orElseThrow(() -> new OperationFailure(OperationFailure.Reason.UNAVAILABLE));
        }
    }

    private Optional<MutationResult> recover(CreditCommand command, CanonicalFingerprint canonical) {
        var result = store.replay(command, canonical);
        result.ifPresent(encoder::validate);
        return result;
    }

    private MutationResult execute(CreditAttempt attempt) {
        store.reserve(attempt);
        var command = attempt.command();
        if (!attempt.policy().limits().acceptsTransactionAmount(command.amount().amount())) {
            return reject(attempt, RejectionReason.TRANSACTION_LIMIT_EXCEEDED);
        }
        // ADR-010: acquire the complete customer/funding set before checking decision-making state.
        var locked = store.lockAccounts(List.of(command.accountId(), attempt.funding().counterpartAccountId()));
        var funding = locked.get(attempt.funding().counterpartAccountId());
        requireParticipant(funding, AccountCategory.FUNDING_CLEARING, command);
        var customer = locked.get(command.accountId());
        if (customer == null) { return reject(attempt, RejectionReason.ACCOUNT_NOT_FOUND); }
        requireParticipant(customer, AccountCategory.CUSTOMER_LIABILITY, command);
        long room = attempt.policy().limits().maxCustomerBalance() - command.amount().amount();
        if (customer.snapshot().balance().amount() > room) {
            return reject(attempt, RejectionReason.BALANCE_LIMIT_EXCEEDED);
        }
        var journal = new Journal(List.of(new Posting(attempt.funding().counterpartAccountId(), PostingSide.DEBIT, command.amount()),
                new Posting(command.accountId(), PostingSide.CREDIT, command.amount())));
        Map<AccountId, Account> snapshots = locked.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().snapshot()));
        var projections = journal.projectBalances(snapshots);
        // ADR-007: validate every projection before any history/projection mutation; completion shares this transaction.
        store.appendJournal(attempt, journal);
        store.applyProjections(projections);
        return complete(attempt, new OperationOutcome.Success(command.transactionId(), OperationType.CREDIT));
    }

    private void requireParticipant(CreditStore.LockedAccount account, AccountCategory category, CreditCommand command) {
        if (account == null || !account.active() || account.snapshot().category() != category
                || !account.snapshot().balance().currency().equals(command.amount().currency())) {
            throw new IllegalStateException("Credit participant is unavailable or incompatible");
        }
    }

    private MutationResult reject(CreditAttempt attempt, RejectionReason reason) {
        // ADR-010: terminal rejection commits only its original outcome; no journal or projection writes.
        return complete(attempt, new OperationOutcome.Rejected(attempt.command().transactionId(), OperationType.CREDIT, reason));
    }

    private MutationResult complete(CreditAttempt attempt, OperationOutcome outcome) {
        var result = new MutationResult(outcome, attempt.command().amount().currency());
        var response = encoder.encode(attempt.command(), result);
        store.complete(attempt, result, response);
        return new MutationResult(outcome, result.currency(), Optional.of(response));
    }
}
