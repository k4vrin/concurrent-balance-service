package dev.kavrin.balance.operation.application.debit;

import dev.kavrin.balance.account.domain.Account;
import dev.kavrin.balance.account.domain.AccountCategory;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.ledger.domain.Journal;
import dev.kavrin.balance.ledger.domain.Posting;
import dev.kavrin.balance.ledger.domain.PostingSide;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.command.DebitCommand;
import dev.kavrin.balance.operation.application.execution.DuplicateOperation;
import dev.kavrin.balance.operation.application.execution.OperationCoordinator;
import dev.kavrin.balance.operation.application.identity.CanonicalFingerprint;
import dev.kavrin.balance.operation.application.identity.CanonicalRequest;
import dev.kavrin.balance.operation.application.policy.AmountLimits;
import dev.kavrin.balance.operation.application.policy.ExecutionPolicy;
import dev.kavrin.balance.operation.application.policy.FundingSelection;
import dev.kavrin.balance.operation.application.policy.SupportedCurrencies;
import dev.kavrin.balance.operation.application.result.MutationResult;
import dev.kavrin.balance.operation.application.result.OperationFailure;
import dev.kavrin.balance.operation.application.result.StoredResponse;
import dev.kavrin.balance.operation.domain.OperationOutcome;
import dev.kavrin.balance.operation.domain.OperationType;
import dev.kavrin.balance.operation.domain.RejectionReason;
import dev.kavrin.balance.operation.domain.TransactionId;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class DebitHandlerTests {
    private static final Currency IRR = Currency.getInstance("IRR");
    private static final AccountId CUSTOMER = new AccountId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    private static final AccountId FUNDING = new AccountId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    private final DebitStore store = mock(DebitStore.class);
    private final DebitResponseEncoder encoder = mock(DebitResponseEncoder.class);
    private final RecordingTransactionManager manager = new RecordingTransactionManager();
    private final ExecutionPolicy policy = new ExecutionPolicy("policy-1", new SupportedCurrencies(Set.of(IRR)),
            new AmountLimits(100, 200), Map.of(IRR, new FundingSelection(FUNDING, "funding-1")));
    private final DebitCommand command = new DebitCommand(new TransactionId("portable-debit"), CUSTOMER, new Money(100, IRR));
    private final StoredResponse response = new StoredResponse(1, 200, "application/json", new byte[] {1});
    private final DebitHandler handler = handler(policy);

    @Test
    void successCalculatesBalancedHistoryAndBothProjectionsBeforeCompleting() {
        when(store.lockAccounts(anyList())).thenReturn(accounts(100, 0));
        when(encoder.encode(any(), any())).thenReturn(response);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            manager.events.add("complete");
            return null;
        }).when(store).complete(any(), any(), any());

        var result = handler.handle(command);
        manager.events.add("returned");

        var attempt = org.mockito.ArgumentCaptor.forClass(DebitAttempt.class);
        var ordered = inOrder(store);
        ordered.verify(store).replay(command, fingerprint());
        ordered.verify(store).reserve(attempt.capture());
        ordered.verify(store).lockAccounts(List.of(CUSTOMER, FUNDING));
        ordered.verify(store).appendJournal(attempt.getValue(), new Journal(List.of(
                new Posting(CUSTOMER, PostingSide.DEBIT, command.amount()),
                new Posting(FUNDING, PostingSide.CREDIT, command.amount()))));
        ordered.verify(store).applyProjections(Map.of(
                CUSTOMER, account(CUSTOMER, AccountCategory.CUSTOMER_LIABILITY, 0),
                FUNDING, account(FUNDING, AccountCategory.FUNDING_CLEARING, -100)));
        var outcome = new OperationOutcome.Success(command.transactionId(), OperationType.DEBIT);
        ordered.verify(store).complete(attempt.getValue(), new MutationResult(outcome, IRR), response);
        ordered.verifyNoMoreInteractions();
        assertThat(result).isEqualTo(new MutationResult(outcome, IRR, Optional.of(response)));
        assertThat(attempt.getValue().policy()).isSameAs(policy);
        assertThat(attempt.getValue().funding()).isEqualTo(policy.fundingFor(IRR));
        assertThat(attempt.getValue().canonical().fingerprint()).isEqualTo(fingerprint());
        assertThat(manager.events).containsExactly("begin", "commit", "begin", "complete", "commit", "returned");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void retainedSuccessOrRejectionReplaysBeforeDisabledCurrencyPolicy(boolean success) {
        var retained = retained(success);
        when(store.replay(command, fingerprint())).thenReturn(Optional.of(retained));
        var disabled = new ExecutionPolicy("disabled", new SupportedCurrencies(Set.of()), policy.limits(), Map.of());

        assertThat(handler(disabled).handle(command)).isSameAs(retained);

        verify(encoder).validate(retained);
        verify(store).replay(command, fingerprint());
        verifyNoMoreInteractions(store, encoder);
        assertThat(manager.events).containsExactly("begin", "commit");
    }

    @Test
    void replayConflictEscapesWithoutReservation() {
        var conflict = new OperationFailure(OperationFailure.Reason.TRANSACTION_ID_CONFLICT);
        when(store.replay(command, fingerprint())).thenThrow(conflict);

        assertThatThrownBy(() -> handler.handle(command)).isSameAs(conflict);

        verify(store).replay(command, fingerprint());
        verifyNoMoreInteractions(store);
        assertThat(manager.events).containsExactly("begin", "rollback");
    }

    @Test
    void invalidRetainedResponseFailsBeforeAnyNewWrites() {
        var retained = retained(true);
        when(store.replay(command, fingerprint())).thenReturn(Optional.of(retained));
        var corrupt = new IllegalStateException("invalid retained response");
        doThrow(corrupt).when(encoder).validate(retained);

        assertThatThrownBy(() -> handler.handle(command)).isSameAs(corrupt);

        verify(store).replay(command, fingerprint());
        verifyNoMoreInteractions(store);
        assertThat(manager.events).containsExactly("begin", "rollback");
    }

    @Test
    void newUnsupportedCurrencyDoesNotReserveAnOperation() {
        var disabled = new ExecutionPolicy("disabled", new SupportedCurrencies(Set.of()), policy.limits(), Map.of());

        assertThatThrownBy(() -> handler(disabled).handle(command)).isInstanceOfSatisfying(OperationFailure.class,
                failure -> assertThat(failure.reason()).isEqualTo(OperationFailure.Reason.UNSUPPORTED_CURRENCY));

        verify(store).replay(command, fingerprint());
        verifyNoMoreInteractions(store);
    }

    @ParameterizedTest
    @EnumSource(value = RejectionReason.class, names = {
            "TRANSACTION_LIMIT_EXCEEDED", "INSUFFICIENT_FUNDS", "ACCOUNT_NOT_FOUND"})
    void terminalRejectionCompletesWithoutJournalOrProjectionWrites(RejectionReason reason) {
        var selectedPolicy = reason == RejectionReason.TRANSACTION_LIMIT_EXCEEDED
                ? new ExecutionPolicy("lower-limit", policy.supportedCurrencies(), new AmountLimits(99, 200), policy.fundingByCurrency())
                : policy;
        if (reason != RejectionReason.TRANSACTION_LIMIT_EXCEEDED) {
            when(store.lockAccounts(anyList())).thenReturn(reason == RejectionReason.ACCOUNT_NOT_FOUND
                    ? Map.of(FUNDING, accounts(0, 0).get(FUNDING)) : accounts(99, 0));
        }
        var rejectionResponse = new StoredResponse(1, 409, "application/problem+json", new byte[] {2});
        when(encoder.encode(any(), any())).thenReturn(rejectionResponse);

        var result = handler(selectedPolicy).handle(command);

        var rejected = new OperationOutcome.Rejected(command.transactionId(), OperationType.DEBIT, reason);
        assertThat(result).isEqualTo(new MutationResult(rejected, IRR, Optional.of(rejectionResponse)));
        verify(store).complete(any(), eq(new MutationResult(rejected, IRR)), eq(rejectionResponse));
        verify(store, never()).appendJournal(any(), any());
        verify(store, never()).applyProjections(any());
        if (reason == RejectionReason.TRANSACTION_LIMIT_EXCEEDED) {
            verify(store, never()).lockAccounts(anyList());
        }
        assertThat(manager.events).containsExactly("begin", "commit", "begin", "commit");
    }

    @Test
    void fundingOverflowFailsBeforeHistoryOrProjectionWrites() {
        when(store.lockAccounts(anyList())).thenReturn(accounts(100, Long.MIN_VALUE));

        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(ArithmeticException.class);

        verify(store, never()).appendJournal(any(), any());
        verify(store, never()).applyProjections(any());
        verify(store, never()).complete(any(), any(), any());
        assertThat(manager.events).containsExactly("begin", "commit", "begin", "rollback");
    }

    @ParameterizedTest
    @ValueSource(strings = {"journal", "projection", "completion"})
    void storageFailureRequestsRollbackAndDoesNotReturnSuccess(String stage) {
        when(store.lockAccounts(anyList())).thenReturn(accounts(100, 0));
        var failure = new DataAccessResourceFailureException("storage unavailable");
        switch (stage) {
            case "journal" -> doThrow(failure).when(store).appendJournal(any(), any());
            case "projection" -> doThrow(failure).when(store).applyProjections(any());
            case "completion" -> {
                when(encoder.encode(any(), any())).thenReturn(response);
                doThrow(failure).when(store).complete(any(), any(), any());
            }
            default -> throw new AssertionError(stage);
        }

        assertThatThrownBy(() -> handler.handle(command)).isInstanceOfSatisfying(OperationFailure.class, unavailable -> {
            assertThat(unavailable.reason()).isEqualTo(OperationFailure.Reason.UNAVAILABLE);
            assertThat(unavailable.getCause()).isSameAs(failure);
        });

        if (!stage.equals("completion")) { verify(store, never()).complete(any(), any(), any()); }
        if (stage.equals("journal")) { verify(store, never()).applyProjections(any()); }
        assertThat(manager.events).containsExactly("begin", "commit", "begin", "rollback");
    }

    @Test
    void commitFailureDoesNotAcknowledgeThePreparedSuccess() {
        when(store.lockAccounts(anyList())).thenReturn(accounts(100, 0));
        when(encoder.encode(any(), any())).thenReturn(response);
        manager.failCommit = 2;

        assertThatThrownBy(() -> handler.handle(command)).isInstanceOfSatisfying(OperationFailure.class, failure -> {
            assertThat(failure.reason()).isEqualTo(OperationFailure.Reason.UNAVAILABLE);
            assertThat(failure.getCause()).isInstanceOf(TransactionSystemException.class);
        });

        verify(store).complete(any(), any(), eq(response));
        assertThat(manager.events).containsExactly("begin", "commit", "begin", "commit");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void duplicateReservationRecoversOnlyAfterTheFailedTransactionEnds(boolean outcomeVisible) {
        var retained = retained(true);
        when(store.replay(command, fingerprint())).thenReturn(Optional.empty())
                .thenReturn(outcomeVisible ? Optional.of(retained) : Optional.empty());
        doThrow(new DuplicateOperation(new IllegalStateException("competing identity"))).when(store).reserve(any());

        if (outcomeVisible) {
            assertThat(handler.handle(command)).isSameAs(retained);
            verify(encoder).validate(retained);
        } else {
            assertThatThrownBy(() -> handler.handle(command)).isInstanceOfSatisfying(OperationFailure.class,
                    failure -> assertThat(failure.reason()).isEqualTo(OperationFailure.Reason.UNAVAILABLE));
        }

        var ordered = inOrder(store);
        ordered.verify(store).replay(command, fingerprint());
        ordered.verify(store).reserve(any());
        ordered.verify(store).replay(command, fingerprint());
        ordered.verifyNoMoreInteractions();
        assertThat(manager.events).containsExactly("begin", "commit", "begin", "rollback", "begin", "commit");
    }

    @Test
    void lockTimeoutRollsBackReservationInsteadOfRetainingInsufficientFunds() {
        when(store.lockAccounts(anyList())).thenThrow(new CannotAcquireLockException("lock timeout"));

        assertThatThrownBy(() -> handler.handle(command)).isInstanceOfSatisfying(OperationFailure.class,
                failure -> assertThat(failure.reason()).isEqualTo(OperationFailure.Reason.UNAVAILABLE));

        verify(store).reserve(any());
        verify(store, never()).complete(any(), any(), any());
        verify(store, never()).appendJournal(any(), any());
        assertThat(manager.events).containsExactly("begin", "commit", "begin", "rollback");
    }

    @Test
    void encodingFailureRollsBackRatherThanCompletingAnUnreplayableOutcome() {
        when(store.lockAccounts(anyList())).thenReturn(accounts(100, 100));
        doThrow(new IllegalStateException("encoding failed")).when(encoder).encode(any(), any());

        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(IllegalStateException.class);

        verify(store, never()).complete(any(), any(), any());
        assertThat(manager.events).containsExactly("begin", "commit", "begin", "rollback");
    }

    private DebitHandler handler(ExecutionPolicy selectedPolicy) {
        return new DebitHandler(new OperationCoordinator(manager), selectedPolicy, store, encoder);
    }

    private CanonicalFingerprint fingerprint() {
        return CanonicalRequest.debit(command, Optional.empty()).fingerprint();
    }

    private MutationResult retained(boolean success) {
        OperationOutcome outcome = success ? new OperationOutcome.Success(command.transactionId(), OperationType.DEBIT)
                : new OperationOutcome.Rejected(command.transactionId(), OperationType.DEBIT, RejectionReason.INSUFFICIENT_FUNDS);
        var stored = success ? response : new StoredResponse(1, 409, "application/problem+json", new byte[] {2});
        return new MutationResult(outcome, IRR, Optional.of(stored));
    }

    private Map<AccountId, DebitStore.LockedAccount> accounts(long customerBalance, long fundingBalance) {
        return Map.of(CUSTOMER, new DebitStore.LockedAccount(account(CUSTOMER, AccountCategory.CUSTOMER_LIABILITY, customerBalance), true),
                FUNDING, new DebitStore.LockedAccount(account(FUNDING, AccountCategory.FUNDING_CLEARING, fundingBalance), true));
    }

    private Account account(AccountId id, AccountCategory category, long balance) {
        return new Account(id, category, new Money(balance, IRR));
    }

    // Records Spring lifecycle decisions only; mocked storage has no database commit, rollback or locks.
    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final List<String> events = new ArrayList<>();
        private int commits;
        private int failCommit;

        @Override
        protected Object doGetTransaction() { return new Object(); }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) { events.add("begin"); }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            events.add("commit");
            if (++commits == failCommit) { throw new TransactionSystemException("commit outcome uncertain"); }
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) { events.add("rollback"); }
    }
}
