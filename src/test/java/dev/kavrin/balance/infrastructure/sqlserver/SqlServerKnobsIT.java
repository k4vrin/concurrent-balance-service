package dev.kavrin.balance.infrastructure.sqlserver;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Isolated engine experiments; these settings must not change the financial lock protocol. */
class SqlServerKnobsIT {
    private static SqlServerTestDatabase database;

    @BeforeAll
    static void requireRealDatabase() throws Exception {
        database = SqlServerTestDatabase.fromEnvironment();
        try (var connection = database.openConnection()) {
            assertThat(connection.isValid(5)).isTrue();
            System.out.printf("SQL Server knobs lab: %s, driver %s%n",
                    connection.getMetaData().getDatabaseProductVersion(),
                    connection.getMetaData().getDriverVersion());
        }
    }

    @ParameterizedTest
    @CsvSource({"ON, OFF, true, false", "OFF, ON, false, true", "OFF, OFF, false, false"})
    void indexOptionsControlAllowedLockTypes(String rows, String pages, boolean allowRows,
            boolean allowPages) throws Exception {
        try (var fixture = CommittedSqlServerFixture.create(database);
                var connection = database.openConnection()) {
            execute(connection, "ALTER INDEX ALL ON " + fixture.tableName()
                    + " SET (ALLOW_ROW_LOCKS = " + rows + ", ALLOW_PAGE_LOCKS = " + pages + ")");
            try (var statement = connection.prepareStatement("""
                    SELECT allow_row_locks, allow_page_locks FROM tempdb.sys.indexes
                    WHERE object_id = OBJECT_ID(?) AND index_id = 1
                    """)) {
                statement.setString(1, "tempdb.." + fixture.tableName());
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).isTrue();
                    assertThat(result.getBoolean(1)).isEqualTo(allowRows);
                    assertThat(result.getBoolean(2)).isEqualTo(allowPages);
                    assertThat(result.next()).isFalse();
                }
            }
            assertThat(ids(connection, fixture.tableName(), "READCOMMITTEDLOCK")).containsExactly(1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"FORCESEEK", "FORCESCAN"})
    void accessPathHintsPreserveTheSelectedRows(String hint) throws Exception {
        try (var fixture = CommittedSqlServerFixture.create(database);
                var connection = database.openConnection()) {
            seedSecondRow(connection, fixture.tableName());
            // INDEX(1) is the fixture's clustered primary key, never a production index assumption.
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT probe_value FROM " + fixture.tableName()
                            + " WITH (INDEX(1), " + hint + ") WHERE fixture_id = 2")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(11);
                assertThat(result.next()).isFalse();
            }
        }
    }

    @Test
    void readpastSkipsLockedKeyAndTheRowReturnsAfterRollback() throws Exception {
        try (var fixture = CommittedSqlServerFixture.create(database);
                var holder = database.openConnection(); var reader = database.openConnection()) {
            seedSecondRow(holder, fixture.tableName());
            reader.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            holder.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            execute(reader, "SET LOCK_TIMEOUT 250");
            holder.setAutoCommit(false);
            try {
                // XLOCK forces a reader conflict; UPDLOCK alone permits ordinary shared reads.
                lockFirstRow(holder, fixture.tableName(), "ROWLOCK");
                assertThatThrownBy(() -> ids(reader, fixture.tableName(), "READCOMMITTEDLOCK"))
                        .isInstanceOfSatisfying(SQLException.class,
                                failure -> assertThat(failure.getErrorCode()).isEqualTo(1222));
                assertThat(ids(reader, fixture.tableName(), "READPAST, READCOMMITTEDLOCK"))
                        .containsExactly(2);
                holder.rollback();
                assertThat(ids(reader, fixture.tableName(), "READPAST, READCOMMITTEDLOCK"))
                        .containsExactly(1, 2);
            } finally {
                holder.rollback();
            }
        }
    }

    @Test
    void readpastDoesNotSkipPageLocks() throws Exception {
        try (var fixture = CommittedSqlServerFixture.create(database);
                var holder = database.openConnection(); var reader = database.openConnection()) {
            seedSecondRow(holder, fixture.tableName());
            reader.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            holder.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            execute(reader, "SET LOCK_TIMEOUT 250");
            holder.setAutoCommit(false);
            try {
                lockFirstRow(holder, fixture.tableName(), "PAGLOCK");
                assertThatThrownBy(() -> ids(reader, fixture.tableName(), "READPAST, READCOMMITTEDLOCK"))
                        .isInstanceOfSatisfying(SQLException.class,
                                failure -> assertThat(failure.getErrorCode()).isEqualTo(1222));
                holder.rollback();
                assertThat(ids(reader, fixture.tableName(), "READPAST, READCOMMITTEDLOCK"))
                        .containsExactly(1, 2);
            } finally {
                holder.rollback();
            }
        }
    }

    private static void lockFirstRow(Connection connection, String table, String granularity) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT fixture_id FROM " + table
                        + " WITH (XLOCK, HOLDLOCK, " + granularity + ") WHERE fixture_id = 1")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }

    private static List<Integer> ids(Connection connection, String table, String hints) throws SQLException {
        var ids = new ArrayList<Integer>();
        // Lock-based reads make the comparison explicit even when cbs_test uses RCSI.
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT fixture_id FROM " + table
                        + " WITH (" + hints + ") ORDER BY fixture_id")) {
            while (result.next()) {
                ids.add(result.getInt(1));
            }
        }
        return ids;
    }

    private static void seedSecondRow(Connection connection, String table) throws SQLException {
        execute(connection, "INSERT INTO " + table + " VALUES (2, 11)");
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
