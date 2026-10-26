package dev.kavrin.balance.operation.application.debit;

import dev.kavrin.balance.account.application.LockedAccount;
import dev.kavrin.balance.account.domain.Account;
import dev.kavrin.balance.account.domain.AccountCategory;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.ledger.domain.Journal;
import dev.kavrin.balance.ledger.domain.Posting;
import dev.kavrin.balance.ledger.domain.PostingSide;
import dev.kavrin.balance.operation.application.command.DebitCommand;
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

/**
 * Executes the durable debit use case when {@code balance.execution.enabled=true}.
 * Checks retained request identity before current policy, then reserves new intent and uses
 * locked customer/funding snapshots to calculate balanced history and projections. Success
 * commits history, projections and the retained response together; terminal rejection retains
 * its outcome without a journal (ADR-007 and ADR-010).
 *
 * <p>Transaction ownership belongs to {@link OperationCoordinator}. A duplicate reservation
 * triggers a separate recovery transaction; general deadlock/timeout retries are not implemented here.
 */
@Service
@ConditionalOnProperty(prefix = "balance.execution", name = "enabled", havingValue = "true")
public final class DebitHandler {
    private final OperationCoordinator coordinator;
    private final ExecutionPolicy policy;
    private final DebitStore store;
    private final DebitResponseEncoder encoder;

    /**
     * Assembles debit orchestration from transaction, policy, persistence and response ports.
     *
     * @param coordinator owner of each execution or recovery transaction
     * @param policy snapshot used to evaluate new debit intent
     * @param store persistence operations participating in the owning transaction
     * @param encoder serializer and validator for retained debit responses
     */
    public DebitHandler(OperationCoordinator coordinator, ExecutionPolicy policy, DebitStore store, DebitResponseEncoder encoder) {
        this.coordinator = coordinator;
        this.policy = policy;
        this.store = store;
        this.encoder = encoder;
    }

    /**
     * Processes a new debit or returns its original retained result on matching replay.
     * Converts Spring data-access and transaction failures, including commit failures, into
     * unavailability; callers must retain the same transaction ID when recovering an uncertain result.
     *
     * @param command validated debit intent and retry identity
     * @return committed success or terminal rejection, including its retained response
     * @throws OperationFailure if identity conflicts, a new currency is unsupported, persistence
     *         is unavailable or duplicate-reservation recovery finds no completed result
     * @throws IllegalStateException if configuration, participant state or retained data is inconsistent
     */
    public MutationResult handle(DebitCommand command) {
        try {
            return process(command);
        } catch (DataAccessException | TransactionException unavailable) {
            throw new OperationFailure(OperationFailureReason.UNAVAILABLE, unavailable);
        }
    }

    /**
     * Checks replay first, selects current policy only for new intent, and executes one new
     * attempt. A duplicate reservation is recovered after the failed transaction has exited.
     *
     * @param command debit intent to canonicalize and resolve
     * @return the retained or newly committed result
     */
    private MutationResult process(DebitCommand command) {
        var canonical = CanonicalRequest.debit(command, Optional.empty());
        // ADR-010: completed identity is authoritative before current currency, cap or funding policy.
        var existing = coordinator.execute(() -> recover(command, canonical.fingerprint()));
        if (existing.isPresent()) { return existing.get(); }

        var attempt = prepareAttempt(command, canonical);
        return executeOrRecover(attempt);
    }

    /**
     * Selects policy and funding only after replay has found no original result.
     *
     * @param command new debit intent
     * @param canonical caller intent to retain with the reservation
     * @return fixed identities and policy selections for one writing attempt
     */
    private DebitAttempt prepareAttempt(DebitCommand command, CanonicalRequest canonical) {
        if (!policy.supportedCurrencies().currencies().contains(command.amount().currency())) {
            throw new OperationFailure(OperationFailureReason.UNSUPPORTED_CURRENCY);
        }
        var funding = policy.fundingFor(command.amount().currency());
        if (funding.counterpartAccountId().equals(command.accountId())) {
            throw new IllegalStateException("Funding counterpart must differ from the customer");
        }
        return new DebitAttempt(UUID.randomUUID(), UUID.randomUUID(), command, canonical, policy, funding);
    }

    /**
     * Executes one writing transaction, recovering a reservation race in a fresh transaction.
     *
     * @param attempt prepared debit with stable identities and policy
     * @return the committed result, or the competing operation's matching retained result
     */
    private MutationResult executeOrRecover(DebitAttempt attempt) {
        try {
            return coordinator.execute(() -> execute(attempt));
        } catch (DuplicateOperation duplicate) {
            // The failed reservation has left its transaction before committed-result recovery.
            return coordinator.execute(() -> recover(attempt.command(), attempt.canonical().fingerprint()))
                    .orElseThrow(() -> new OperationFailure(OperationFailureReason.UNAVAILABLE));
        }
    }

    /**
     * Reads an original outcome and validates its retained response inside the caller's transaction.
     *
     * @param command request whose transaction ID is being recovered
     * @param canonical request representation used to reject conflicting reuse
     * @return a validated retained result, or empty if no operation exists
     */
    private Optional<MutationResult> recover(DebitCommand command, CanonicalFingerprint canonical) {
        var result = store.replay(command, canonical);
        result.ifPresent(encoder::validate);
        return result;
    }

    /**
     * Reserves intent, checks limits and locked participants, then appends balanced postings,
     * applies validated projections and records completion within the owning transaction.
     * Expected business failures complete as terminal rejections; exceptions abort the attempt.
     *
     * @param attempt fixed identities, command and policies for this execution
     * @return the completed result awaiting the coordinator's commit
     */
    private MutationResult execute(DebitAttempt attempt) {
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

        // ADR-010: decide insufficient funds from locked state and retain the rejection for retries.
        if (customer.snapshot().balance().amount() < command.amount().amount()) {
            return reject(attempt, RejectionReason.INSUFFICIENT_FUNDS);
        }

        var journal = createJournal(attempt);
        var projections = calculateProjections(journal, locked);
        // ADR-007: validate every projection before any history/projection mutation; completion shares this transaction.
        store.appendJournal(attempt, journal);
        store.applyProjections(projections);
        return complete(attempt, new OperationSuccess(command.transactionId(), OperationType.DEBIT));
    }

    /**
     * Builds the balanced customer debit and funding credit without persisting either line.
     *
     * @param attempt debit and its selected funding counterpart
     * @return the validated single-currency debit journal
     */
    private Journal createJournal(DebitAttempt attempt) {
        var command = attempt.command();
        return new Journal(List.of(
                new Posting(command.accountId(), PostingSide.DEBIT, command.amount()),
                new Posting(attempt.funding().counterpartAccountId(), PostingSide.CREDIT, command.amount())));
    }

    /**
     * Calculates every proposed balance from locked snapshots before any monetary write.
     *
     * @param journal balanced debit postings
     * @param locked participant state protected by the owning transaction
     * @return validated proposed balances for all journal participants
     */
    private Map<AccountId, Account> calculateProjections(Journal journal, Map<AccountId, LockedAccount> locked) {
        var snapshots = locked.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().snapshot()));
        return journal.projectBalances(snapshots);
    }

    /**
     * Requires an active participant of the expected accounting category and command currency.
     * A missing customer is handled separately as a business rejection by the execution flow.
     *
     * @param account locked participant to inspect
     * @param category required funding or customer classification
     * @param command debit supplying the expected currency
     * @throws IllegalStateException if the participant is missing, inactive or incompatible
     */
    private void requireParticipant(LockedAccount account, AccountCategory category, DebitCommand command) {
        if (account == null || !account.active() || account.snapshot().category() != category
                || !account.snapshot().balance().currency().equals(command.amount().currency())) {
            throw new IllegalStateException("Debit participant is unavailable or incompatible");
        }
    }

    /**
     * Records a terminal debit rejection without appending a journal or applying projections.
     *
     * @param attempt reserved debit being rejected
     * @param reason business decision to retain for matching retries
     * @return the rejection result awaiting transaction commit
     */
    private MutationResult reject(DebitAttempt attempt, RejectionReason reason) {
        // ADR-010: terminal rejection commits only its original outcome; no journal or projection writes.
        return complete(attempt, new OperationRejected(attempt.command().transactionId(), OperationType.DEBIT, reason));
    }

    /**
     * Encodes the original response and persists it with the terminal outcome before commit.
     * Encoding or storage failure propagates so the owning transaction can roll back.
     *
     * @param attempt reserved debit being completed
     * @param outcome success or terminal rejection to retain
     * @return the outcome and currency with their encoded response attached
     */
    private MutationResult complete(DebitAttempt attempt, OperationOutcome outcome) {
        var result = new MutationResult(outcome, attempt.command().amount().currency());
        var response = encoder.encode(attempt.command(), result);
        store.complete(attempt, result, response);
        return new MutationResult(outcome, result.currency(), Optional.of(response));
    }
}
