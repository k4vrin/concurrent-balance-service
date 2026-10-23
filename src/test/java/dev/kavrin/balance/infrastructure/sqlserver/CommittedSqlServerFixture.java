package dev.kavrin.balance.infrastructure.sqlserver;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/** A uniquely named tempdb fixture with committed setup, visible while its owner connection stays open. */
final class CommittedSqlServerFixture implements AutoCloseable {
    private final Connection owner;
    private final String tableName;

    private CommittedSqlServerFixture(Connection owner, String tableName) {
        this.owner = owner;
        this.tableName = tableName;
    }

    static CommittedSqlServerFixture create(SqlServerTestDatabase database) throws SQLException {
        var owner = database.openConnection();
        // Identifiers are generated locally, never interpolated from environment or request input.
        var tableName = "##cbs08_" + UUID.randomUUID().toString().replace("-", "");
        try (var statement = owner.createStatement()) {
            owner.setAutoCommit(false);
            statement.executeUpdate("CREATE TABLE " + tableName + " (fixture_id int PRIMARY KEY, probe_value int NOT NULL)");
            statement.executeUpdate("INSERT INTO " + tableName + " VALUES (1, 7)");
            owner.commit();
            owner.setAutoCommit(true);
            return new CommittedSqlServerFixture(owner, tableName);
        } catch (SQLException failure) {
            try {
                owner.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            try {
                owner.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    String tableName() {
        return tableName;
    }

    @Override
    public void close() throws SQLException {
        try (owner; var statement = owner.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS " + tableName);
        }
    }
}
