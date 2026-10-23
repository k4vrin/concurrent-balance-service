package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.domain.AccountId;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlServerUuidLockIT {
    private static SqlServerTestDatabase database;

    @BeforeAll
    static void requireRealDatabase() throws Exception {
        database = SqlServerTestDatabase.fromEnvironment();
        try (var connection = database.openConnection()) {
            assertThat(connection.isValid(5)).isTrue();
        }
    }

    @Test
    void uuidAndBigintRoundTripWithoutAssumingSqlGuidOrder() throws Exception {
        try (var fixture = UuidAccountSqlFixture.create(database); var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try {
                assertThat(fixture.lock(connection, UuidAccountSqlFixture.A).orElseThrow()).isEqualTo(Long.MAX_VALUE);
                assertThat(fixture.lock(connection, UuidAccountSqlFixture.B).orElseThrow()).isEqualTo(Long.MIN_VALUE);
            } finally {
                connection.rollback();
            }
            try (var statement = connection.createStatement(); var result = statement.executeQuery(
                    "SELECT account_id FROM " + fixture.tableName() + " ORDER BY account_id DESC")) {
                var sqlOrder = new ArrayList<AccountId>();
                while (result.next()) {
                    sqlOrder.add(new AccountId(UUID.fromString(result.getString(1))));
                }
                assertThat(sqlOrder).containsExactly(UuidAccountSqlFixture.B, UuidAccountSqlFixture.A);
                assertThat(fixture.lockAll(connection, List.of(UuidAccountSqlFixture.B, UuidAccountSqlFixture.A,
                        UuidAccountSqlFixture.A))).containsExactly(UuidAccountSqlFixture.A, UuidAccountSqlFixture.B);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void existingUuidProtectionBlocksAWriterButAllowsAnotherExistingKey() throws Exception {
        try (var fixture = UuidAccountSqlFixture.create(database);
                var holder = database.openConnection(); var writer = database.openConnection()) {
            begin(holder);
            begin(writer);
            try {
                fixture.lock(holder, UuidAccountSqlFixture.A);
                assertThatThrownBy(() -> update(writer, fixture, UuidAccountSqlFixture.A))
                        .isInstanceOfSatisfying(SQLException.class,
                                failure -> assertThat(failure.getErrorCode()).isEqualTo(1222));
                writer.rollback();
                assertThat(update(writer, fixture, UuidAccountSqlFixture.B)).isEqualTo(1);
                writer.commit();
                holder.rollback();
                assertThat(update(writer, fixture, UuidAccountSqlFixture.A)).isEqualTo(1);
                writer.commit();
            } finally {
                holder.rollback();
                writer.rollback();
            }
        }
    }

    @Test
    void missingUuidLockPreventsCreationUntilTheRejectingAttemptEnds() throws Exception {
        try (var fixture = UuidAccountSqlFixture.create(database);
                var holder = database.openConnection(); var creator = database.openConnection()) {
            begin(holder);
            begin(creator);
            try {
                assertThat(fixture.lock(holder, UuidAccountSqlFixture.MISSING)).isEmpty();
                assertThatThrownBy(() -> fixture.insert(creator, UuidAccountSqlFixture.MISSING, 0))
                        .isInstanceOfSatisfying(SQLException.class,
                                failure -> assertThat(failure.getErrorCode()).isEqualTo(1222));
                creator.rollback();
                holder.commit();
                fixture.insert(creator, UuidAccountSqlFixture.MISSING, 0);
                creator.commit();
            } finally {
                holder.rollback();
                creator.rollback();
            }
        }
    }

    @Test
    void oppositeParticipantDirectionsAcquireTheSameDeduplicatedOrder() throws Exception {
        try (var fixture = UuidAccountSqlFixture.create(database); var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            var forward = executor.submit(() -> lockAttempt(fixture,
                    List.of(UuidAccountSqlFixture.A, UuidAccountSqlFixture.B), ready, start));
            var reverse = executor.submit(() -> lockAttempt(fixture,
                    List.of(UuidAccountSqlFixture.B, UuidAccountSqlFixture.A, UuidAccountSqlFixture.B), ready, start));
            try {
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                assertThat(forward.get(10, TimeUnit.SECONDS))
                        .containsExactly(UuidAccountSqlFixture.A, UuidAccountSqlFixture.B);
                assertThat(reverse.get(10, TimeUnit.SECONDS))
                        .containsExactly(UuidAccountSqlFixture.A, UuidAccountSqlFixture.B);
            } finally {
                start.countDown();
                executor.shutdownNow();
            }
        }
    }

    private static List<AccountId> lockAttempt(UuidAccountSqlFixture fixture, List<AccountId> participants,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        try (var connection = database.openConnection()) {
            begin(connection);
            ready.countDown();
            try {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                var order = fixture.lockAll(connection, participants);
                connection.commit();
                return order;
            } finally {
                connection.rollback();
            }
        }
    }

    private static void begin(Connection connection) throws SQLException {
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        connection.setAutoCommit(false);
        try (var statement = connection.createStatement()) {
            statement.execute("SET LOCK_TIMEOUT 1000");
        }
    }

    private static int update(Connection connection, UuidAccountSqlFixture fixture, AccountId id) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE " + fixture.tableName()
                + " SET balance = 0 WHERE account_id = CAST(? AS uniqueidentifier)")) {
            statement.setString(1, id.value().toString());
            return statement.executeUpdate();
        }
    }
}
