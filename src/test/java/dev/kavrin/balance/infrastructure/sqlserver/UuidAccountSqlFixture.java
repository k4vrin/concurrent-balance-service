package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.application.AccountLockOrder;
import dev.kavrin.balance.account.domain.AccountId;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

/** Native SQL prototype only; CBS10 must verify its entity mapping inside the real transaction. */
final class UuidAccountSqlFixture implements AutoCloseable {
    static final AccountId A = new AccountId(UUID.fromString("f0000000-0000-4000-8000-000000000001"));
    static final AccountId B = new AccountId(UUID.fromString("10000000-0000-4000-8000-000000000002"));
    static final AccountId MISSING = new AccountId(UUID.fromString("20000000-0000-4000-8000-000000000003"));
    private final Connection owner;
    private final String tableName = "##cbs_uuid_" + UUID.randomUUID().toString().replace("-", "");

    private UuidAccountSqlFixture(Connection owner) {
        this.owner = owner;
    }

    static UuidAccountSqlFixture create(SqlServerTestDatabase database) throws SQLException {
        var fixture = new UuidAccountSqlFixture(database.openConnection());
        try (var statement = fixture.owner.createStatement()) {
            statement.executeUpdate("CREATE TABLE " + fixture.tableName
                    + " (account_id uniqueidentifier PRIMARY KEY, balance bigint NOT NULL)");
            fixture.insert(fixture.owner, A, Long.MAX_VALUE);
            fixture.insert(fixture.owner, B, Long.MIN_VALUE);
            return fixture;
        } catch (SQLException failure) {
            try {
                fixture.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    void insert(Connection connection, AccountId id, long balance) throws SQLException {
        try (var statement = connection.prepareStatement(
                "INSERT INTO " + tableName + " VALUES (CAST(? AS uniqueidentifier), ?)")) {
            statement.setString(1, id.value().toString());
            statement.setLong(2, balance);
            statement.executeUpdate();
        }
    }

    OptionalLong lock(Connection connection, AccountId id) throws SQLException {
        // Canonical UUID text comes from AccountId, never raw HTTP text that SQL could truncate/coerce.
        try (var statement = connection.prepareStatement("SELECT balance FROM " + tableName
                + " WITH (UPDLOCK, HOLDLOCK) WHERE account_id = CAST(? AS uniqueidentifier)")) {
            statement.setString(1, id.value().toString());
            try (var result = statement.executeQuery()) {
                return result.next() ? OptionalLong.of(result.getLong(1)) : OptionalLong.empty();
            }
        }
    }

    List<AccountId> lockAll(Connection connection, Collection<AccountId> participants) throws SQLException {
        var ordered = participants.stream().distinct().sorted(AccountLockOrder.DESCENDING).toList();
        for (var id : ordered) {
            if (lock(connection, id).isEmpty()) {
                throw new SQLException("Expected existing UUID fixture participant");
            }
        }
        return ordered;
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
