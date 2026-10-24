package dev.kavrin.balance.operation.application;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationCoordinatorTests {
    private final RecordingTransactionManager manager = new RecordingTransactionManager();
    private final OperationCoordinator coordinator = new OperationCoordinator(manager);

    @Test
    void springInjectsTheConfiguredManagerIntoTheService() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(PlatformTransactionManager.class, () -> manager);
            context.register(OperationCoordinator.class);
            context.refresh();

            assertThat(context.getBean(OperationCoordinator.class).execute(() -> "injected")).isEqualTo("injected");
            assertThat(manager.events).containsExactly("begin", "commit");
        }
    }

    @Test
    void smokeProfileDoesNotRequireATransactionManager() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("smoke");
            context.register(OperationCoordinator.class);
            context.refresh();

            assertThat(context.getBeansOfType(OperationCoordinator.class)).isEmpty();
        }
    }

    @Test
    void ownsAWritableReadCommittedTransactionAndReturnsAfterCommit() {
        var result = coordinator.execute(() -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
            manager.events.add("attempt");
            return "stored outcome";
        });
        manager.events.add("returned");

        assertThat(result).isEqualTo("stored outcome");
        assertThat(manager.events).containsExactly("begin", "attempt", "commit", "returned");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void rollsBackAndPropagatesTheOriginalAttemptFailure() {
        var failure = new IllegalStateException("write failed");

        assertThatThrownBy(() -> coordinator.execute(() -> { throw failure; })).isSameAs(failure);

        assertThat(manager.events).containsExactly("begin", "rollback");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void doesNotReturnTheOutcomeWhenCommitFails() {
        manager.commitFailure = new TransactionSystemException("commit outcome uncertain");

        assertThatThrownBy(() -> coordinator.execute(() -> "unconfirmed outcome"))
                .isSameAs(manager.commitFailure);

        assertThat(manager.events).containsExactly("begin", "commit");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void rejectsAnAmbientWritableTransactionBeforeInvokingTheAttempt() {
        assertAmbientTransactionRejected(false);
    }

    @Test
    void rejectsAnAmbientReadOnlyTransactionBeforeInvokingTheAttempt() {
        assertAmbientTransactionRejected(true);
    }

    private void assertAmbientTransactionRejected(boolean readOnly) {
        var outer = new TransactionTemplate(manager);
        outer.setReadOnly(readOnly);
        var invoked = new AtomicBoolean();

        outer.executeWithoutResult(status -> {
            assertThatThrownBy(() -> coordinator.execute(() -> {
                invoked.set(true);
                return "unexpected";
            })).isInstanceOf(IllegalStateException.class).hasMessageContaining("own its transaction");
            assertThat(manager.events).containsExactly("begin");
        });

        assertThat(invoked).isFalse();
        assertThat(manager.events).containsExactly("begin", "commit");
    }

    // Exercises Spring's transaction lifecycle without claiming database rollback or commit evidence.
    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final List<String> events = new ArrayList<>();
        private TransactionSystemException commitFailure;

        @Override
        protected Object doGetTransaction() { return new Object(); }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) { events.add("begin"); }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            events.add("commit");
            if (commitFailure != null) { throw commitFailure; }
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) { events.add("rollback"); }
    }
}
