package dev.kavrin.balance.infrastructure.sqlserver;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlServerEnvironmentIT {
    private static SqlServerTestDatabase database;

    @BeforeAll
    static void requireRealDatabase() throws Exception {
        database = SqlServerTestDatabase.fromEnvironment();
        // Fail class setup on absent infrastructure; do not turn an unavailable database into skipped evidence.
        try (var connection = database.openConnection()) {
            assertThat(connection.isValid(5)).isTrue();
        }
    }

    @Test
    void selectUsesActualSqlServerAndNonAdministratorLogin() throws Exception {
        try (var connection = database.openConnection(); var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT DB_NAME(), 1, IS_SRVROLEMEMBER('sysadmin')")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).isEqualTo("cbs_test");
            assertThat(result.getInt(2)).isEqualTo(1);
            assertThat(result.getInt(3)).isZero();
            assertThat(connection.getMetaData().getDriverName()).contains("Microsoft JDBC Driver");
        }
    }

    @Test
    void rollbackRemovesUncommittedLocalFixtureRows() throws Exception {
        try (var connection = database.openConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE #cbs08_local (probe_value int NOT NULL)");
            connection.setAutoCommit(false);
            try {
                statement.executeUpdate("INSERT INTO #cbs08_local VALUES (100)");
                try (var inserted = statement.executeQuery("SELECT COUNT(*) FROM #cbs08_local")) {
                    assertThat(inserted.next()).isTrue();
                    assertThat(inserted.getInt(1)).isEqualTo(1);
                }
            } finally {
                connection.rollback();
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM #cbs08_local")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isZero();
            }
            connection.setAutoCommit(true);
        }
    }

    @Test
    void committedSetupIsVisibleToAnotherConnection() throws Exception {
        try (var fixture = CommittedSqlServerFixture.create(database); var reader = database.openConnection();
                var statement = reader.createStatement();
                var result = statement.executeQuery("SELECT probe_value FROM " + fixture.tableName())) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(7);
            assertThat(result.next()).isFalse();
        }
    }

    @Test
    void rollingBackAnAttemptPreservesSeparateCommittedSetup() throws Exception {
        try (var fixture = CommittedSqlServerFixture.create(database)) {
            try (var writer = database.openConnection(); var statement = writer.createStatement()) {
                writer.setAutoCommit(false);
                try {
                    assertThat(statement.executeUpdate("UPDATE " + fixture.tableName() + " SET probe_value = 9")).isEqualTo(1);
                } finally {
                    writer.rollback();
                }
            }
            try (var reader = database.openConnection(); var statement = reader.createStatement();
                    var result = statement.executeQuery("SELECT probe_value FROM " + fixture.tableName())) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(7);
            }
        }
    }

    @Test
    void fixtureCleanupRemovesOnlyItsOwnTemporaryTable() throws Exception {
        String tableName;
        try (var fixture = CommittedSqlServerFixture.create(database)) {
            tableName = fixture.tableName();
        }
        try (var connection = database.openConnection();
                var statement = connection.prepareStatement("SELECT OBJECT_ID(?)")) {
            statement.setString(1, "tempdb.." + tableName);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getObject(1)).isNull();
            }
        }
    }
}
