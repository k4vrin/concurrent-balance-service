package dev.kavrin.balance.infrastructure.sqlserver;

import java.sql.Connection;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Measures native SQL Server comparison and locking behavior in isolated temporary fixtures.
 *
 * <p>Cases distinguish plain committed reads from retained update/serializable locks and show why a
 * 1222 timeout needs whole-transaction cleanup. These probes do not certify the complete financial protocol.
 */
class SqlServerProtocolReviewIT {
    private static SqlServerTestDatabase database;

    @BeforeAll
    static void requireRealDatabase() throws Exception {
        database = SqlServerTestDatabase.fromEnvironment();
        try (var connection = database.openConnection()) {
            assertThat(connection.isValid(5)).isTrue();
        }
    }

    @Test
    void binaryCollationDistinguishesCaseButStillPadsTrailingSpaces() throws Exception {
        try (var connection = database.openConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("""
                        SELECT
                          CASE WHEN N'transfer-001' COLLATE Latin1_General_100_BIN2 = N'Transfer-001'
                               THEN 1 ELSE 0 END,
                          CASE WHEN N'transfer-001' COLLATE Latin1_General_100_BIN2 = N'transfer-001 '
                               THEN 1 ELSE 0 END,
                          DATALENGTH(N'transfer-001'), DATALENGTH(N'transfer-001 ')
                        """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isZero();
            assertThat(result.getInt(2)).isEqualTo(1);
            assertThat(result.getInt(3)).isEqualTo(24);
            assertThat(result.getInt(4)).isEqualTo(26);
        }
    }

    @Test
    void plainReadCommittedReadDoesNotProtectAgainstSubsequentWriter() throws Exception {
        try (var fixture = CommittedSqlServerFixture.create(database);
                var reader = database.openConnection(); var writer = database.openConnection()) {
            beginAttempt(reader);
            try {
                assertThat(read(reader, fixture.tableName(), "")).isEqualTo(7);
                setLockTimeout(writer);
                try (var statement = writer.createStatement()) {
                    assertThat(statement.executeUpdate("UPDATE " + fixture.tableName()
                            + " SET probe_value = 9 WHERE fixture_id = 1")).isEqualTo(1);
                }
                assertThat(read(reader, fixture.tableName(), "")).isEqualTo(9);
            } finally {
                reader.rollback();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lockingReadProtectsAfterResultCloseAndReleasesOnTransactionEnd(boolean commit) throws Exception {
        try (var fixture = CommittedSqlServerFixture.create(database);
                var holder = database.openConnection(); var contender = database.openConnection()) {
            try (var statement = contender.createStatement()) {
                statement.executeUpdate("INSERT INTO " + fixture.tableName() + " VALUES (2, 11)");
            }
            beginAttempt(holder);
            beginAttempt(contender);
            try {
                assertThat(read(holder, fixture.tableName(), "WITH (UPDLOCK, HOLDLOCK)")).isEqualTo(7);
                setLockTimeout(contender);
                // The statement and result have closed. Only the transaction now owns this protection.
                assertThatThrownBy(() -> read(contender, fixture.tableName(), "WITH (UPDLOCK, HOLDLOCK)"))
                        .isInstanceOfSatisfying(SQLException.class,
                                failure -> assertThat(failure.getErrorCode()).isEqualTo(1222));
                assertThat(transactionState(contender)).isEqualTo(1);
                // Error 1222 cancels the statement, so the caller must roll back the whole attempt.
                contender.rollback();
                try (var statement = contender.createStatement()) {
                    assertThat(statement.executeUpdate("UPDATE " + fixture.tableName()
                            + " SET probe_value = 12 WHERE fixture_id = 2")).isEqualTo(1);
                }
                contender.commit();
                if (commit) {
                    holder.commit();
                } else {
                    holder.rollback();
                }
                assertThat(read(contender, fixture.tableName(), "WITH (UPDLOCK, HOLDLOCK)")).isEqualTo(7);
                contender.commit();
            } finally {
                holder.rollback();
                contender.rollback();
            }
        }
    }

    private static void beginAttempt(Connection connection) throws SQLException {
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        try (var statement = connection.createStatement()) {
            statement.execute("SET XACT_ABORT OFF");
        }
        connection.setAutoCommit(false);
    }

    private static void setLockTimeout(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("SET LOCK_TIMEOUT 250");
        }
    }

    private static int read(Connection connection, String tableName, String hints) throws SQLException {
        // Both identifier and hints come only from this test's source-controlled fixture.
        try (var statement = connection.prepareStatement(
                "SELECT probe_value FROM " + tableName + " " + hints + " WHERE fixture_id = ?")) {
            statement.setInt(1, 1);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getInt(1);
            }
        }
    }

    private static int transactionState(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT XACT_STATE()")) {
            assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }
}
