package dev.kavrin.balance.operation.application.execution;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks Spring transaction ownership and completion ordering with a recording manager.
 *
 * <p>Cases reject ambient transactions and prevent candidate results from escaping failed commits.
 * Recorded lifecycle decisions do not simulate physical SQL Server locks or durable effects.
 */
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
            assertThat(manager.events()).containsExactly("begin", "commit");
        }
    }

    @Test
    void productionServiceStillRequiresAManagerEvenIfATestProfileIsNamed() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("smoke");
            context.register(OperationCoordinator.class);
            assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
        }
    }

    @Test
    void ownsAWritableReadCommittedTransactionAndReturnsAfterCommit() {
        var result = coordinator.execute(() -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            assertThat(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())
                    .isEqualTo(TransactionDefinition.ISOLATION_READ_COMMITTED);
            manager.events().add("attempt");
            return "stored outcome";
        });
        manager.events().add("returned");

        assertThat(result).isEqualTo("stored outcome");
        assertThat(manager.events()).containsExactly("begin", "attempt", "commit", "returned");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void rollsBackAndPropagatesTheOriginalAttemptFailure() {
        var failure = new IllegalStateException("write failed");

        assertThatThrownBy(() -> coordinator.execute(() -> { throw failure; })).isSameAs(failure);

        assertThat(manager.events()).containsExactly("begin", "rollback");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void doesNotReturnTheOutcomeWhenCommitFails() {
        manager.failCommit(new TransactionSystemException("commit outcome uncertain"));

        assertThatThrownBy(() -> coordinator.execute(() -> "unconfirmed outcome"))
                .isSameAs(manager.commitFailure());

        assertThat(manager.events()).containsExactly("begin", "commit");
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
            assertThat(manager.events()).containsExactly("begin");
        });

        assertThat(invoked).isFalse();
        assertThat(manager.events()).containsExactly("begin", "commit");
    }

}
