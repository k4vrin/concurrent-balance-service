package dev.kavrin.balance.operation.application.execution;

import dev.kavrin.balance.operation.application.result.OperationFailure;
import dev.kavrin.balance.operation.application.result.OperationFailureReason;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks bounded recovery decisions using a recording manager and controlled monotonic time.
 *
 * <p>Cases cover stable prepared inputs, rollback-before-retry, retained winners, conflicts,
 * exhaustion, budget admission, interruption, rollback failure, uncertain completion and validated
 * configuration binding. Lifecycle evidence is portable and does not simulate database durability.
 */
class OperationRecoveryTests {
    private final RecordingTransactionManager manager = new RecordingTransactionManager();
    private final DeterministicRecoveryRuntime runtime = new DeterministicRecoveryRuntime();
    private final OperationCoordinator coordinator = new OperationCoordinator(manager, RetryPolicy.defaults(), runtime);

    @AfterEach
    void noTransactionLeaks() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        Thread.interrupted();
    }

    @ParameterizedTest
    @ValueSource(ints = {1205, 1222})
    void retriesWholeAttemptsAfterRollbackWithStablePreparedInputsAndFreshRecovery(int code) {
        var preparations = new AtomicInteger();
        var writes = new AtomicInteger();
        var lookups = new AtomicInteger();
        var prepared = new Object();
        var result = coordinator.executeRecovering(() -> {
            lookups.incrementAndGet();
            return Optional.<String>empty();
        }, () -> { preparations.incrementAndGet(); return prepared; }, attempt -> {
            assertThat(attempt).isSameAs(prepared);
            if (writes.incrementAndGet() < 3) { throw transientFailure(code); }
            return "committed";
        });
        assertThat(result).isEqualTo("committed");
        assertThat(preparations).hasValue(1);
        assertThat(writes).hasValue(3);
        assertThat(lookups).hasValue(3);
        assertThat(runtime.delays).containsExactly(50L, 100L);
        assertThat(manager.events()).containsExactly("begin", "commit", "begin", "rollback", "begin", "commit",
                "begin", "rollback", "begin", "commit", "begin", "commit");
    }

    @Test
    void retryExhaustionRetainsTheOriginalFailureAndNeverAcknowledgesSuccess() {
        var writes = new AtomicInteger();
        var failure = transientFailure(1222);
        assertThatThrownBy(() -> coordinator.executeRecovering(Optional::<String>empty, Object::new, attempt -> {
            writes.incrementAndGet(); throw failure;
        })).isInstanceOfSatisfying(OperationFailure.class, error -> {
            assertThat(error.reason()).isEqualTo(OperationFailureReason.UNAVAILABLE);
            assertThat(error.getCause()).isSameAs(failure);
        });
        assertThat(writes).hasValue(3);
        assertThat(manager.events().stream().filter("rollback"::equals).count()).isEqualTo(3);
    }

    @Test
    void committedReplayPrecedesPreparationAndPolicyEvaluation() {
        assertThat(coordinator.executeRecovering(() -> Optional.of("original rejection"),
                () -> { throw new AssertionError("policy must not run"); }, attempt -> "unexpected"))
                .isEqualTo("original rejection");
    }

    @Test
    void concurrentWinnerIsReturnedAfterTransientRollbackWithoutAnotherWrite() {
        var reads = new AtomicInteger();
        var writes = new AtomicInteger();
        assertThat(coordinator.executeRecovering(() -> reads.incrementAndGet() == 1 ? Optional.empty() : Optional.of("winner"),
                Object::new, attempt -> { writes.incrementAndGet(); throw transientFailure(1222); })).isEqualTo("winner");
        assertThat(writes).hasValue(1);
        assertThat(runtime.delays).isEmpty();
    }

    @Test
    void conflictDuringRecoveryEscapesWithoutAnotherAttempt() {
        var reads = new AtomicInteger();
        var conflict = new OperationFailure(OperationFailureReason.TRANSACTION_ID_CONFLICT);
        assertThatThrownBy(() -> coordinator.executeRecovering(() -> {
            if (reads.incrementAndGet() > 1) { throw conflict; }
            return Optional.<String>empty();
        }, Object::new, attempt -> { throw transientFailure(1205); })).isSameAs(conflict);
        assertThat(runtime.delays).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void uncertainCommitRecoversButNeverReexecutesEvenAfterEmptyLookups(boolean visibleEventually) {
        manager.failCommitOn(2);
        var reads = new AtomicInteger();
        var writes = new AtomicInteger();
        java.util.function.Supplier<Optional<String>> lookup = () -> {
            int count = reads.incrementAndGet();
            return visibleEventually && count == 4 ? Optional.of("retained") : Optional.empty();
        };
        java.util.function.Supplier<String> call = () -> coordinator.executeRecovering(lookup, Object::new,
                attempt -> { writes.incrementAndGet(); return "candidate"; });
        if (visibleEventually) { assertThat(call.get()).isEqualTo("retained"); }
        else { assertThatThrownBy(call::get).isInstanceOf(OperationFailure.class).hasCauseInstanceOf(TransactionSystemException.class); }
        assertThat(writes).hasValue(1);
        assertThat(reads).hasValue(4);
    }

    @Test
    void readFailureBeforePreparationCanRetryWithoutWriting() {
        var reads = new AtomicInteger();
        assertThat(coordinator.executeRecovering(() -> {
            if (reads.incrementAndGet() == 1) { throw transientFailure(1205); }
            return Optional.of("original");
        }, Object::new, attempt -> "unexpected")).isEqualTo("original");
        assertThat(reads).hasValue(2);
    }

    @Test
    void noAttemptIsAdmittedWithoutEnoughRemainingPoolAndTransactionBudget() {
        var writes = new AtomicInteger();
        assertThatThrownBy(() -> coordinator.executeRecovering(Optional::<String>empty, () -> {
            runtime.now += 11_000_000_000L;
            return new Object();
        }, attempt -> { writes.incrementAndGet(); return "unexpected"; })).isInstanceOf(OperationFailure.class);
        assertThat(writes).hasValue(0);
    }

    @Test
    void elapsedTransactionAndRecoveryTimeCanPreventAnotherWrite() {
        var writes = new AtomicInteger();
        assertThatThrownBy(() -> coordinator.executeRecovering(Optional::<String>empty, Object::new, attempt -> {
            writes.incrementAndGet();
            runtime.now += 9_980_000_000L;
            throw transientFailure(1222);
        })).isInstanceOf(OperationFailure.class);
        assertThat(writes).hasValue(1);
        assertThat(runtime.delays).isEmpty();
    }

    @Test
    void interruptDuringBackoffIsPreservedAndStopsRetries() {
        runtime.interrupt = true;
        assertThatThrownBy(() -> coordinator.executeRecovering(Optional::<String>empty, Object::new,
                attempt -> { throw transientFailure(1205); })).isInstanceOf(OperationFailure.class)
                .hasCauseInstanceOf(InterruptedException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void permanentConstraintAndDomainDefectsAreNeverRetried() {
        var constraint = new DataIntegrityViolationException("FK", new SQLException("FK", "S0001", 547));
        assertThatThrownBy(() -> coordinator.executeRecovering(Optional::<String>empty, Object::new,
                attempt -> { throw constraint; })).isSameAs(constraint);
        var defect = new ArithmeticException("invariant");
        assertThatThrownBy(() -> coordinator.executeRecovering(Optional::<String>empty, Object::new,
                attempt -> { throw defect; })).isSameAs(defect);
        assertThat(runtime.delays).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 11})
    void invalidAttemptConfigurationFailsAtConstruction(int attempts) {
        assertThatThrownBy(() -> new RetryPolicy(attempts, 20000, 50)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rollbackFailureDoesNotAuthorizeAnotherWrite() {
        manager.failRollback();
        var writes = new AtomicInteger();
        assertThatThrownBy(() -> coordinator.executeRecovering(Optional::<String>empty, Object::new, attempt -> {
            writes.incrementAndGet(); throw transientFailure(1222);
        })).isInstanceOf(OperationFailure.class).hasCauseInstanceOf(TransactionSystemException.class);
        assertThat(writes).hasValue(1);
    }

    @Test
    void oneAttemptPolicyDisablesWritingRetriesButStillRecoversIdentity() {
        var single = new OperationCoordinator(manager, new RetryPolicy(1, 20000, 0), runtime);
        var writes = new AtomicInteger();
        assertThatThrownBy(() -> single.executeRecovering(Optional::<String>empty, Object::new, attempt -> {
            writes.incrementAndGet(); throw transientFailure(1205);
        })).isInstanceOf(OperationFailure.class);
        assertThat(writes).hasValue(1);
        assertThat(manager.events()).containsExactly("begin", "commit", "begin", "rollback", "begin", "commit");
        assertThat(runtime.delays).isEmpty();
    }

    @Test
    void ambientTransactionIsRejectedBeforeReplayOrPreparation() {
        new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(status ->
                assertThatThrownBy(() -> coordinator.executeRecovering(() -> {
                    throw new AssertionError("no lookup allowed");
                }, Object::new, attempt -> "unexpected"))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("own its transaction"));
    }

    @Test
    void runtimeConfigurationIsBoundAndInvalidLimitsFailStartup() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "invalid-retry", java.util.Map.of("balance.retry.max-attempts", "0")));
            context.registerBean(org.springframework.transaction.PlatformTransactionManager.class, () -> manager);
            context.register(OperationCoordinator.class);
            assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(IllegalArgumentException.class);
        }
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "custom-retry", java.util.Map.of("balance.retry.max-attempts", "2", "balance.retry.backoff-millis", "0")));
            context.registerBean(org.springframework.transaction.PlatformTransactionManager.class, () -> manager);
            context.register(OperationCoordinator.class);
            context.refresh();
            assertThat(context.getBean(RetryPolicy.class)).isEqualTo(new RetryPolicy(2, 20000, 0));
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {9999, 120001})
    void invalidBudgetsFailAtConstruction(long budget) {
        assertThatThrownBy(() -> new RetryPolicy(3, budget, 50)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 1001})
    void invalidBackoffFailsAtConstruction(long backoff) {
        assertThatThrownBy(() -> new RetryPolicy(3, 20000, backoff)).isInstanceOf(IllegalArgumentException.class);
    }

    private CannotAcquireLockException transientFailure(int code) {
        return new CannotAcquireLockException("SQL Server transient", new SQLException("vendor", "S0001", code));
    }
}
