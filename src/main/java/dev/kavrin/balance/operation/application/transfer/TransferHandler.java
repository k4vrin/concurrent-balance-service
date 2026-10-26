package dev.kavrin.balance.operation.application.transfer;

import dev.kavrin.balance.account.application.LockedAccount;
import dev.kavrin.balance.account.domain.Account;
import dev.kavrin.balance.account.domain.AccountCategory;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.ledger.domain.Journal;
import dev.kavrin.balance.ledger.domain.Posting;
import dev.kavrin.balance.ledger.domain.PostingSide;
import dev.kavrin.balance.operation.application.command.TransferCommand;
import dev.kavrin.balance.operation.application.execution.DuplicateOperation;
import dev.kavrin.balance.operation.application.execution.OperationCoordinator;
import dev.kavrin.balance.operation.application.identity.CanonicalFingerprint;
import dev.kavrin.balance.operation.application.identity.CanonicalRequest;
import dev.kavrin.balance.operation.application.policy.ExecutionPolicy;
import dev.kavrin.balance.operation.application.result.MutationResult;
import dev.kavrin.balance.operation.application.result.OperationFailure;
import dev.kavrin.balance.operation.application.result.OperationFailureReason;
import dev.kavrin.balance.operation.domain.OperationOutcome;
import dev.kavrin.balance.operation.domain.OperationRejected;
import dev.kavrin.balance.operation.domain.OperationSuccess;
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

/** Transfers between customer liabilities in one owned transaction, without a funding leg (ADR-007/010). */
@Service
@ConditionalOnProperty(prefix = "balance.execution", name = "enabled", havingValue = "true")
public final class TransferHandler {
    private final OperationCoordinator coordinator;
    private final ExecutionPolicy policy;
    private final TransferStore store;
    private final TransferResponseEncoder encoder;

    public TransferHandler(OperationCoordinator coordinator, ExecutionPolicy policy,
            TransferStore store, TransferResponseEncoder encoder) {
        this.coordinator = coordinator;
        this.policy = policy;
        this.store = store;
        this.encoder = encoder;
    }

    public MutationResult handle(TransferCommand command) {
        try {
            var canonical = CanonicalRequest.transfer(command, Optional.empty());
            // ADR-010: replay the original decision before evaluating mutable admission policy.
            var existing = coordinator.execute(() -> recover(command, canonical.fingerprint()));
            if (existing.isPresent()) { return existing.get(); }
            if (!policy.supportedCurrencies().currencies().contains(command.amount().currency())) {
                throw new OperationFailure(OperationFailureReason.UNSUPPORTED_CURRENCY);
            }
            var attempt = new TransferAttempt(UUID.randomUUID(), UUID.randomUUID(), command, canonical, policy);
            try {
                return coordinator.execute(() -> execute(attempt));
            } catch (DuplicateOperation duplicate) {
                // Recover only after the losing reservation's entire transaction has rolled back.
                return coordinator.execute(() -> recover(command, canonical.fingerprint()))
                        .orElseThrow(() -> new OperationFailure(OperationFailureReason.UNAVAILABLE));
            }
        } catch (DataAccessException | TransactionException unavailable) {
            // A failed acknowledgement can be uncertain; the caller must keep this transaction ID.
            throw new OperationFailure(OperationFailureReason.UNAVAILABLE, unavailable);
        }
    }

    private Optional<MutationResult> recover(TransferCommand command, CanonicalFingerprint canonical) {
        var result = store.replay(command, canonical);
        result.ifPresent(encoder::validate);
        return result;
    }

    private MutationResult execute(TransferAttempt attempt) {
        store.reserve(attempt);
        var command = attempt.command();
        if (!attempt.policy().limits().acceptsTransactionAmount(command.amount().amount())) {
            return reject(attempt, RejectionReason.TRANSACTION_LIMIT_EXCEEDED);
        }
        // ADR-010: the adapter sorts/deduplicates both IDs before sequential locks, including missing keys.
        // Decide funds and recipient capacity only from snapshots protected until commit.
        var locked = store.lockAccounts(List.of(command.sourceAccountId(), command.destinationAccountId()));
        var source = locked.get(command.sourceAccountId());
        var destination = locked.get(command.destinationAccountId());
        if (source == null || destination == null) { return reject(attempt, RejectionReason.ACCOUNT_NOT_FOUND); }
        requireParticipant(source, command);
        requireParticipant(destination, command);
        if (source.snapshot().balance().amount() < command.amount().amount()) {
            return reject(attempt, RejectionReason.INSUFFICIENT_FUNDS);
        }
        // Subtract from the cap first: adding to a near-BIGINT recipient could overflow before rejection.
        long room = attempt.policy().limits().maxCustomerBalance() - command.amount().amount();
        if (destination.snapshot().balance().amount() > room) {
            return reject(attempt, RejectionReason.BALANCE_LIMIT_EXCEEDED);
        }
        var journal = new Journal(List.of(
                new Posting(command.sourceAccountId(), PostingSide.DEBIT, command.amount()),
                new Posting(command.destinationAccountId(), PostingSide.CREDIT, command.amount())));
        var snapshots = locked.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().snapshot()));
        Map<AccountId, Account> projections = journal.projectBalances(snapshots);
        // ADR-007: validate both projections first, then commit history, both balances and response together.
        store.appendJournal(attempt, journal);
        store.applyProjections(projections);
        return complete(attempt, new OperationSuccess(command.transactionId(), OperationType.TRANSFER));
    }

    private void requireParticipant(LockedAccount account, TransferCommand command) {
        if (!account.active() || account.snapshot().category() != AccountCategory.CUSTOMER_LIABILITY
                || !account.snapshot().balance().currency().equals(command.amount().currency())) {
            throw new IllegalStateException("Transfer participant is unavailable or incompatible");
        }
    }

    private MutationResult reject(TransferAttempt attempt, RejectionReason reason) {
        // Terminal business decisions retain only their outcome, with no journal or balance writes (ADR-010).
        return complete(attempt, new OperationRejected(attempt.command().transactionId(), OperationType.TRANSFER, reason));
    }

    private MutationResult complete(TransferAttempt attempt, OperationOutcome outcome) {
        var result = new MutationResult(outcome, attempt.command().amount().currency());
        var response = encoder.encode(attempt.command(), result);
        store.complete(attempt, result, response);
        return new MutationResult(outcome, result.currency(), Optional.of(response));
    }
}
