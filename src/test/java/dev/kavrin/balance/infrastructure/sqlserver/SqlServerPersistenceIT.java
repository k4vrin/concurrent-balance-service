package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.application.AccountLockOrder;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.account.infrastructure.persistence.AccountEntity;
import dev.kavrin.balance.account.infrastructure.persistence.AccountStore;
import dev.kavrin.balance.ledger.infrastructure.persistence.*;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.execution.OperationCoordinator;
import dev.kavrin.balance.operation.infrastructure.persistence.OperationEntity;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SqlServerPersistenceIT {
    @Autowired EntityManager entities;
    @Autowired AccountStore accounts;
    @Autowired PlatformTransactionManager manager;
    @Autowired Environment environment;
    @Autowired OperationCoordinator coordinator;

    @Test
    void coordinatorUsesTheJpaManagerAndCommitsBeforeReturning() throws Exception {
        assertThat(manager).isInstanceOf(JpaTransactionManager.class);
        try (var fixture = CommittedSqlServerFixture.create(SqlServerTestDatabase.fromEnvironment())) {
            var result = coordinator.execute(() -> {
                assertThat(entities.isJoinedToTransaction()).isTrue();
                var isolation = entities.createNativeQuery(
                        "SELECT transaction_isolation_level FROM sys.dm_exec_sessions WHERE session_id=@@SPID")
                        .getSingleResult();
                assertThat(((Number) isolation).intValue()).isEqualTo(2);
                entities.createNativeQuery("UPDATE " + fixture.tableName() + " SET probe_value=11 WHERE fixture_id=1")
                        .executeUpdate();
                return "committed probe";
            });
            assertThat(result).isEqualTo("committed probe");
            assertProbeValue(fixture, 11);
        }
    }

    @Test
    void coordinatorRollsBackJpaWritesBeforePropagatingFailure() throws Exception {
        try (var fixture = CommittedSqlServerFixture.create(SqlServerTestDatabase.fromEnvironment())) {
            var failure = new IllegalStateException("injected coordinator failure");
            assertThatThrownBy(() -> coordinator.execute(() -> {
                entities.createNativeQuery("UPDATE " + fixture.tableName() + " SET probe_value=11 WHERE fixture_id=1")
                        .executeUpdate();
                throw failure;
            })).isSameAs(failure);
            assertProbeValue(fixture, 7);
        }
    }

    @Test
    void coordinatorRejectsAnAmbientJpaTransactionBeforeExecutingWork() {
        var outer = new TransactionTemplate(manager);
        outer.setTimeout(5);
        outer.executeWithoutResult(status ->
                assertThatThrownBy(() -> coordinator.execute(() -> {
                    throw new AssertionError("Rejected attempt must not execute");
                })).isInstanceOf(IllegalStateException.class).hasMessageContaining("own its transaction"));
    }

    private void assertProbeValue(CommittedSqlServerFixture fixture, int expected) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.createStatement()) {
            statement.setQueryTimeout(2);
            try (var result = statement.executeQuery("SELECT probe_value FROM " + fixture.tableName() + " WHERE fixture_id=1")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(expected);
            }
        }
    }

    @DynamicPropertySource
    static void explicitRuntimeDatabase(DynamicPropertyRegistry properties) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection()) {
            assertThat(connection.isValid(5)).isTrue();
        }
        properties.add("spring.datasource.url", () -> System.getenv("SQLSERVER_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("SQLSERVER_TEST_USERNAME"));
        properties.add("spring.datasource.password", () -> System.getenv("SQLSERVER_TEST_PASSWORD"));
    }

    @Test
    void committedOpeningMapsAllEntitiesAndBalanceIsAuthoritativeThroughHttp() throws Exception {
        var pair = zeroAccounts();
        var opened = opening().open(pair.customer(), pair.funding(), new Money(150000, Currency.getInstance("IRR")), OpeningFailurePoint.NONE);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            assertThat(entities.find(AccountEntity.class, pair.customer().value()).snapshot().balance().amount()).isEqualTo(150000);
            assertThat(entities.find(AccountEntity.class, pair.funding().value()).snapshot().balance().amount()).isEqualTo(150000);
            var stored = entities.find(OperationEntity.class, opened.operationId());
            assertThat(stored.state()).isEqualTo("SUCCEEDED");
            assertThat(stored.canonicalRequest()).isEqualTo(opened.canonical());
            assertThat(stored.responseBody()).isNotEmpty();
            assertThat(entities.find(JournalEntity.class, opened.journalId())).isNotNull();
            assertThat(entities.find(PostingEntity.class, new PostingId(opened.journalId(), 1))).isNotNull();
            assertThat(entities.find(PostingEntity.class, new PostingId(opened.journalId(), 2))).isNotNull();
        });
        assertThat(get(pair.customer())).satisfies(response -> {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"balanceRial\":150000", "\"currency\":\"IRR\"", pair.customer().value().toString());
        });
        // Independently reconstruct from committed rows with a wider exact SQL accumulator.
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection(); var statement = connection.prepareStatement(
                "SELECT SUM(CASE WHEN p.side='CREDIT' THEN CAST(p.amount AS decimal(38,0)) ELSE -CAST(p.amount AS decimal(38,0)) END) "
                        + "FROM dbo.postings p WHERE p.account_id=CAST(? AS uniqueidentifier)")) {
            statement.setString(1, pair.customer().value().toString());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo(new BigDecimal("150000"));
            }
        }
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection(); var statement = connection.prepareStatement(
                "SELECT SUM(CASE WHEN side='DEBIT' THEN CAST(amount AS decimal(38,0)) ELSE 0 END),"
                        + "SUM(CASE WHEN side='CREDIT' THEN CAST(amount AS decimal(38,0)) ELSE 0 END),COUNT(*) "
                        + "FROM dbo.postings WHERE journal_id=CAST(? AS uniqueidentifier)")) {
            statement.setString(1, opened.journalId().toString());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo("150000");
                assertThat(result.getBigDecimal(2)).isEqualByComparingTo("150000");
                assertThat(result.getInt(3)).isEqualTo(2);
            }
        }
        assertCounts(pair.customer(), 1, 1, 2);
    }

    @ParameterizedTest
    @EnumSource(value = OpeningFailurePoint.class, names = {"AFTER_JOURNAL", "AFTER_POSTINGS", "AFTER_PROJECTIONS"})
    void injectedOpeningFailureRollsBackAllHistoryOutcomeAndProjections(OpeningFailurePoint failure) throws Exception {
        var pair = zeroAccounts();
        assertThatThrownBy(() -> opening().open(pair.customer(), pair.funding(), new Money(100, Currency.getInstance("IRR")), failure))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Injected opening failure");
        assertCounts(pair.customer(), 0, 0, 0);
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            for (var id : List.of(pair.customer(), pair.funding())) {
                assertThat(entities.find(AccountEntity.class, id.value()).snapshot().balance().amount()).isZero();
            }
        });
    }

    @Test
    void missingAccountReturns404AndZeroBalanceReadCreatesNoHistory() throws Exception {
        assertThat(get(new AccountId(UUID.randomUUID())).statusCode()).isEqualTo(404);
        var pair = zeroAccounts();
        assertThat(get(pair.customer()).body()).contains("\"balanceRial\":0");
        assertCounts(pair.customer(), 0, 0, 0);
    }

    @Test
    void locksRequireATransactionAndOpeningRejectsAnAmbientTransaction() throws Exception {
        var pair = zeroAccounts();
        assertThatThrownBy(() -> accounts.lockAll(List.of(pair.customer())))
                .isInstanceOf(InvalidDataAccessApiUsageException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
        var readOnly = new TransactionTemplate(manager);
        readOnly.setReadOnly(true);
        readOnly.executeWithoutResult(status ->
                assertThatThrownBy(() -> accounts.lockAll(List.of(pair.customer())))
                        .isInstanceOf(InvalidDataAccessApiUsageException.class)
                        .hasRootCauseInstanceOf(IllegalStateException.class));
        new TransactionTemplate(manager).executeWithoutResult(status ->
                assertThatThrownBy(() -> opening().open(pair.customer(), pair.funding(), new Money(100, Currency.getInstance("IRR")), OpeningFailurePoint.NONE))
                        .isInstanceOf(IllegalStateException.class).hasMessageContaining("own its transaction"));
    }

    @Test
    void entityLockRefreshesPreviouslyManagedStateAndRollbackIsVisibleInFreshContext() throws Exception {
        var pair = zeroAccounts();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            var stale = entities.find(AccountEntity.class, pair.customer().value());
            entities.createNativeQuery("UPDATE dbo.accounts SET balance=7 WHERE account_id=CAST(? AS uniqueidentifier)")
                    .setParameter(1, pair.customer().value().toString()).executeUpdate();
            assertThat(stale.snapshot().balance().amount()).isZero();
            var locked = accounts.lockAll(List.of(pair.customer(), pair.customer()));
            assertThat(locked).hasSize(1);
            assertThat(locked.get(pair.customer()).snapshot().balance().amount()).isEqualTo(7);
            status.setRollbackOnly();
        });
        new TransactionTemplate(manager).executeWithoutResult(status ->
                assertThat(entities.find(AccountEntity.class, pair.customer().value()).snapshot().balance().amount()).isZero());
    }

    @Test
    void nativeEntityLocksLiveUntilTheJpaTransactionEnds() throws Exception {
        var pair = zeroAccounts();
        try (var writer = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = writer.createStatement()) {
            statement.execute("SET LOCK_TIMEOUT 250");
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                var locked = accounts.lockAll(List.of(pair.customer(), pair.funding(), pair.customer()));
                assertThat(locked.keySet()).containsExactlyElementsOf(List.of(pair.customer(), pair.funding())
                        .stream().sorted(AccountLockOrder.DESCENDING).toList());
                assertThatThrownBy(() -> statement.executeUpdate("UPDATE dbo.accounts SET balance=balance WHERE account_id='"
                        + pair.customer().value() + "'"))
                        .isInstanceOfSatisfying(SQLException.class,
                                failure -> assertThat(failure.getErrorCode()).isEqualTo(1222));
            });
            assertThat(statement.executeUpdate("UPDATE dbo.accounts SET balance=balance WHERE account_id='"
                    + pair.customer().value() + "'")).isEqualTo(1);
        }
    }

    @Test
    void httpBalanceDoesNotExposeAnUncommittedProjection() throws Exception {
        var pair = zeroAccounts();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            entities.createNativeQuery("UPDATE dbo.accounts SET balance=7 WHERE account_id=CAST(? AS uniqueidentifier)")
                    .setParameter(1, pair.customer().value().toString()).executeUpdate();
            try {
                // READ_COMMITTED waits, then the configured query timeout maps to temporary unavailability.
                assertThat(get(pair.customer()).statusCode()).isEqualTo(503);
            } catch (Exception failure) {
                throw new IllegalStateException("HTTP read failed before a response", failure);
            } finally {
                status.setRollbackOnly();
            }
        });
        assertThat(get(pair.customer()).body()).contains("\"balanceRial\":0");
    }

    private OpeningFixture opening() { return new OpeningFixture(entities, accounts, manager); }

    private HttpResponse<String> get(AccountId id) throws Exception {
        var uri = URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + "/api/v1/accounts/" + id.value() + "/balance");
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private AccountPair zeroAccounts() throws Exception {
        var pair = new AccountPair(new AccountId(UUID.randomUUID()), new AccountId(UUID.randomUUID()));
        try (var connection = SqlServerTestDatabase.fromEnvironment("SQLSERVER_INITIALIZER_TEST").openConnection();
                var statement = connection.prepareStatement("INSERT dbo.accounts (account_id,category,currency) VALUES (CAST(? AS uniqueidentifier),?,N'IRR')")) {
            for (var id : List.of(pair.customer(), pair.funding())) {
                statement.setString(1, id.value().toString());
                statement.setString(2, id.equals(pair.customer()) ? "CUSTOMER_LIABILITY" : "FUNDING_CLEARING");
                statement.executeUpdate();
            }
        }
        return pair;
    }

    private void assertCounts(AccountId customer, int operationCount, int journalCount, int postingCount) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection(); var statement = connection.prepareStatement(
                "SELECT (SELECT COUNT(*) FROM dbo.operations WHERE destination_account_id=CAST(? AS uniqueidentifier)),"
                        + "(SELECT COUNT(*) FROM dbo.journals j JOIN dbo.operations o ON j.operation_id=o.operation_id WHERE o.destination_account_id=CAST(? AS uniqueidentifier)),"
                        + "(SELECT COUNT(*) FROM dbo.postings p JOIN dbo.journals j ON p.journal_id=j.journal_id JOIN dbo.operations o ON o.operation_id=j.operation_id WHERE o.destination_account_id=CAST(? AS uniqueidentifier))")) {
            for (int i = 1; i <= 3; i++) { statement.setString(i, customer.value().toString()); }
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(operationCount);
                assertThat(result.getInt(2)).isEqualTo(journalCount);
                assertThat(result.getInt(3)).isEqualTo(postingCount);
            }
        }
    }
}
