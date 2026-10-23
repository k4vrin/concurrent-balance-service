package dev.kavrin.balance.infrastructure.sqlserver;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/** Candidate identity constraints in tempdb only; not a business migration or runtime adapter. */
final class TransactionIdSqlFixture implements AutoCloseable {
    static final String ALLOWED = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789._:-";
    private final Connection owner;
    private final String suffix = UUID.randomUUID().toString().replace("-", "");
    private final String tableName = "##cbs_identity_" + suffix;

    private TransactionIdSqlFixture(Connection owner) {
        this.owner = owner;
    }

    static TransactionIdSqlFixture create(SqlServerTestDatabase database) throws SQLException {
        var fixture = new TransactionIdSqlFixture(database.openConnection());
        try (var statement = fixture.owner.createStatement()) {
            // Extra column capacity makes truncation to the column width remain over the domain limit.
            statement.executeUpdate("CREATE TABLE " + fixture.tableName + " ("
                    + "transaction_id nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL, "
                    + "CONSTRAINT " + fixture.uniqueConstraint() + " UNIQUE (transaction_id), "
                    + "CHECK (DATALENGTH(transaction_id) BETWEEN 2 AND 256), "
                    + "CHECK (" + allowedCharacters("transaction_id") + "))");
            return fixture;
        } catch (SQLException failure) {
            try {
                fixture.owner.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    static String allowedCharacters(String column) {
        // Binary comparison preserves disallowed characters, including NUL and trailing spaces.
        return "CONVERT(varbinary(512), TRANSLATE(" + column + " COLLATE Latin1_General_100_BIN2, N'"
                + ALLOWED + "', REPLICATE(N'A', 66))) = "
                + "CONVERT(varbinary(512), REPLICATE(N'A', DATALENGTH(" + column + ") / 2))";
    }

    void insert(Connection connection, String value) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO " + tableName + " VALUES (?)")) {
            statement.setNString(1, value);
            statement.executeUpdate();
        }
    }

    String uniqueConstraint() {
        return "uq_cbs_identity_" + suffix;
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
