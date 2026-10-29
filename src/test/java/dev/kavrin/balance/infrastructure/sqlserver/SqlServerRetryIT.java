package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.operation.application.execution.OperationCoordinator;
import dev.kavrin.balance.operation.application.execution.RetryPolicy;
import dev.kavrin.balance.operation.application.execution.SqlServerFailureClassifier;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises whole-attempt recovery from an actual SQL Server deadlock victim in an isolated JDBC fixture.
 *
 * <p>Two connections deliberately acquire fixture locks in opposite order. The coordinator's
 * transaction uses low deadlock priority so vendor code 1205 can be checked, its earlier write can
 * be shown rolled back, and one subsequent attempt can produce the final effect.
 *
 * <p>The inverted order and extended detector wait are test-only. This case does not establish
 * a complete JPA transfer deadlock or change the production account-lock protocol.
 */
class SqlServerRetryIT {
    @Test
    void actualSqlServerDeadlockVictimRollsBackItsEarlierWriteBeforeWholeAttemptRetry() throws Exception {
        var database = SqlServerTestDatabase.fromEnvironment();
        var dataSource = database.dataSource();
        var manager = new DataSourceTransactionManager(dataSource);
        var attempts = new AtomicInteger();
        var failures = new AtomicInteger();
        var firstLocked = new CountDownLatch(1);
        try (var fixture = CommittedSqlServerFixture.create(database);
                var blocker = database.openConnection(); var worker = Executors.newSingleThreadExecutor()) {
            String table = fixture.tableName();
            sql(blocker, "INSERT " + table + " VALUES (2, 11)");
            blocker.setAutoCommit(false);
            sql(blocker, "SET LOCK_TIMEOUT 10000");
            sql(blocker, "UPDATE " + table + " SET probe_value=probe_value WHERE fixture_id=2");
            var competitor = worker.submit(() -> {
                if (!firstLocked.await(10, TimeUnit.SECONDS)) { throw new AssertionError("victim did not lock first row"); }
                // Deliberately invert locks in this isolated fixture to force a real vendor 1205.
                sql(blocker, "UPDATE " + table + " SET probe_value=probe_value WHERE fixture_id=1");
                blocker.commit();
                return true;
            });
            var runtime = new CallbackRecoveryRuntime(() -> {
                try {
                    assertThat(competitor.get(15, TimeUnit.SECONDS)).isTrue();
                    assertThat(value(database, table, 1)).isEqualTo(7);
                } catch (Exception failure) { throw new AssertionError(failure); }
            });
            var coordinator = new OperationCoordinator(manager, RetryPolicy.defaults(), runtime);
            var result = coordinator.executeRecovering(Optional::<String>empty, Object::new, prepared -> {
                var connection = DataSourceUtils.getConnection(dataSource);
                try {
                    sql(connection, "SET DEADLOCK_PRIORITY LOW; SET LOCK_TIMEOUT 10000");
                    sql(connection, "UPDATE " + table + " SET probe_value=probe_value+1 WHERE fixture_id=1");
                    if (attempts.incrementAndGet() == 1) { firstLocked.countDown(); }
                    sql(connection, "UPDATE " + table + " SET probe_value=probe_value+1 WHERE fixture_id=2");
                    return "committed";
                } catch (SQLException failure) {
                    assertThat(failure.getErrorCode()).isEqualTo(1205);
                    failures.incrementAndGet();
                    var translated = new CannotAcquireLockException("actual SQL Server victim", failure);
                    assertThat(new SqlServerFailureClassifier().retryableRollback(translated)).isTrue();
                    throw translated;
                } finally { DataSourceUtils.releaseConnection(connection, dataSource); }
            });
            assertThat(result).isEqualTo("committed");
            assertThat(competitor.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(attempts).hasValue(2);
            assertThat(failures).hasValue(1);
            assertThat(value(database, table, 1)).isEqualTo(8);
            assertThat(value(database, table, 2)).isEqualTo(12);
        }
    }

    private static void sql(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            // Test-only extended wait lets the deadlock detector choose a victim before cancellation.
            statement.setQueryTimeout(10);
            statement.execute(sql);
        }
    }

    private static int value(SqlServerTestDatabase database, String table, int id) throws SQLException {
        try (var connection = database.openConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT probe_value FROM " + table + " WHERE fixture_id=" + id)) {
            assertThat(rows.next()).isTrue();
            return rows.getInt(1);
        }
    }
}
