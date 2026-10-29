package dev.kavrin.balance.operation.application.execution;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.CannotAcquireLockException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks conservative vendor-code classification without connecting to SQL Server.
 *
 * <p>Constructed cause and JDBC next-exception chains cover eligible codes, conflicting failure
 * evidence and cycles. These cases verify classification only; rollback confirmation and real
 * vendor errors are exercised separately by coordinator and integration tests.
 */
class SqlServerFailureClassifierTests {
    private final SqlServerFailureClassifier classifier = new SqlServerFailureClassifier();

    @ParameterizedTest
    @ValueSource(ints = {1205, 1222})
    void recognizesVendorCodesThroughSpringAndJdbcChains(int code) {
        var chain = new SQLException("wrapper");
        chain.setNextException(new SQLException("vendor", "S0001", code));
        assertThat(classifier.retryableRollback(new CannotAcquireLockException("wrapped", chain))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {2601, 2627, 547, 8115, -2})
    void rejectsOtherUniqueKeysConstraintsOverflowAndUnclassifiedTimeouts(int code) {
        assertThat(classifier.retryableRollback(new SQLException("sql", "S0001", code))).isFalse();
    }

    @Test
    void springLockCategoryWithoutVendorEvidenceDoesNotAuthorizeRetry() {
        assertThat(classifier.retryableRollback(new CannotAcquireLockException("unknown"))).isFalse();
    }

    @Test
    void connectionOrCleanupFailureOverridesATransientCode() {
        var transientError = new SQLException("deadlock", "S0001", 1205);
        transientError.setNextException(new SQLException("connection lost", "08006", 0));
        assertThat(classifier.retryableRollback(transientError)).isFalse();
        var timeout = new SQLException("timeout", "S0001", 1222);
        timeout.addSuppressed(new IllegalStateException("rollback failed"));
        assertThat(classifier.retryableRollback(timeout)).isFalse();
    }

    @Test
    void causeCyclesTerminate() {
        var first = new RuntimeException();
        var second = new RuntimeException(first);
        first.initCause(second);
        assertThat(classifier.retryableRollback(first)).isFalse();
    }
}
