package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.command.CreditCommand;
import dev.kavrin.balance.operation.application.credit.CreditHandler;
import dev.kavrin.balance.operation.application.debit.DebitHandler;
import dev.kavrin.balance.operation.application.transfer.TransferHandler;
import dev.kavrin.balance.operation.domain.OperationType;
import dev.kavrin.balance.operation.domain.TransactionId;
import jakarta.persistence.EntityManagerFactory;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises same-ID reservation races through two independently assembled HTTP applications and SQL Server pools.
 *
 * <p>Both commands pass their original replay lookup before a bounded reservation barrier releases
 * real JPA inserts. Tests require a genuine named uniqueness collision, one retained terminal
 * operation, correct history counts and independently reconciled projections. Matching results
 * and conflicts are replayed through both applications after the race.
 *
 * <p>The contexts share a JVM and dedicated test database. These selected races do not establish
 * separate-process fault isolation, every possible interleaving or network-partition recovery.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlServerCrossInstanceIdentityIT {
    private static final Currency IRR = Currency.getInstance("IRR");
    private static final long CAP = 10_000_000_000_000L;
    private final AccountId funding = new AccountId(UUID.randomUUID());
    private final ReservationRaceGate gate = new ReservationRaceGate();
    private RunningLedgerApplication first;
    private RunningLedgerApplication second;

    @BeforeAll
    void startIndependentApplications() throws Exception {
        createAccount(funding, "FUNDING_CLEARING");
        first = RunningLedgerApplication.start("cbs17-first", funding, gate);
        try { second = RunningLedgerApplication.start("cbs17-second", funding, gate); }
        catch (Exception | Error failure) { first.close(); throw failure; }
        assertThat(first.uri("/")).isNotEqualTo(second.uri("/"));
        assertThat(first.bean(DataSource.class)).isNotSameAs(second.bean(DataSource.class));
        assertThat(first.bean(EntityManagerFactory.class)).isNotSameAs(second.bean(EntityManagerFactory.class));
        assertThat(first.bean(PlatformTransactionManager.class)).isNotSameAs(second.bean(PlatformTransactionManager.class));
        assertThat(first.bean(CreditHandler.class)).isNotSameAs(second.bean(CreditHandler.class));
        assertThat(first.bean(DebitHandler.class)).isNotSameAs(second.bean(DebitHandler.class));
        assertThat(first.bean(TransferHandler.class)).isNotSameAs(second.bean(TransferHandler.class));
    }

    @AfterAll
    void closeApplications() {
        if (second != null) { second.close(); }
        if (first != null) { first.close(); }
    }

    @ParameterizedTest
    @EnumSource(value = OperationType.class, names = {"CREDIT", "DEBIT", "TRANSFER"})
    void identicalSuccessfulMutationsReturnOneCommittedResponseAcrossInstances(OperationType type) throws Exception {
        var customer = customer(100);
        var recipient = customer(0);
        var request = request(type, id(), customer, recipient, 25);
        var before = snapshot(customer, recipient, funding);
        var responses = race(request, request);
        assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
        assertThat(responses.get(0).body()).isEqualTo(responses.get(1).body());
        assertRetained(request, responses.getFirst(), true);
        assertEffect(request, before);
        assertReplayBoth(request, request, responses);
        assertRetained(request, responses.getFirst(), true);
        assertEffect(request, before);
    }

    @ParameterizedTest
    @EnumSource(value = OperationType.class, names = {"CREDIT", "DEBIT", "TRANSFER"})
    void identicalBusinessRejectionsRemainTerminalAcrossInstancesAfterTopUp(OperationType type) throws Exception {
        var customer = customer(100);
        var recipient = customer(0);
        var request = request(type, id(), customer, recipient, type == OperationType.CREDIT ? CAP + 1 : 101);
        var before = snapshot(customer, recipient, funding);
        var responses = race(request, request);
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains(
                    type == OperationType.CREDIT ? "TRANSACTION_LIMIT_EXCEEDED" : "INSUFFICIENT_FUNDS");
        });
        assertThat(responses.get(0).body()).isEqualTo(responses.get(1).body());
        assertRetained(request, responses.getFirst(), false);
        assertUnchanged(before);
        fund(customer, 300);
        var afterTopUp = snapshot(customer, recipient, funding);
        assertReplayBoth(request, request, responses);
        assertRetained(request, responses.getFirst(), false);
        assertUnchanged(afterTopUp);
    }

    @ParameterizedTest
    @EnumSource(value = OperationType.class, names = {"CREDIT", "DEBIT", "TRANSFER"})
    void sameIdDifferentAmountsRetainOnlyTheWinningIntent(OperationType type) throws Exception {
        var customer = customer(100);
        var recipient = customer(0);
        var id = id();
        assertConflictRace(request(type, id, customer, recipient, 25), request(type, id, customer, recipient, 40),
                snapshot(customer, recipient, funding));
    }

    @ParameterizedTest
    @EnumSource(value = OperationType.class, names = {"CREDIT", "DEBIT", "TRANSFER"})
    void sameIdDifferentCustomerIdsDoNotExecuteTheLosingIntent(OperationType type) throws Exception {
        var customer = customer(100);
        var other = customer(100);
        var recipient = customer(0);
        var id = id();
        assertConflictRace(request(type, id, customer, recipient, 25), request(type, id, other, recipient, 25),
                snapshot(customer, other, recipient, funding));
    }

    @ParameterizedTest
    @CsvSource({"CREDIT,DEBIT", "CREDIT,TRANSFER", "DEBIT,TRANSFER"})
    void transactionIdNamespaceIsGlobalAcrossOperationTypes(OperationType a, OperationType b) throws Exception {
        var customer = customer(100);
        var recipient = customer(0);
        var id = id();
        assertConflictRace(request(a, id, customer, recipient, 25), request(b, id, customer, recipient, 25),
                snapshot(customer, recipient, funding));
    }

    @Test
    void sameIdDifferentTransferRecipientsRetainOnlyOneDestination() throws Exception {
        var customer = customer(100);
        var recipient = customer(0);
        var other = customer(0);
        var id = id();
        assertConflictRace(request(OperationType.TRANSFER, id, customer, recipient, 25),
                request(OperationType.TRANSFER, id, customer, other, 25), snapshot(customer, recipient, other, funding));
    }

    private void assertConflictRace(MutationRaceRequest a, MutationRaceRequest b, Map<AccountId, Long> before) throws Exception {
        var responses = race(a, b);
        assertThat(responses.stream().filter(r -> r.statusCode() == 200).count()).isEqualTo(1);
        assertThat(responses.stream().filter(r -> r.statusCode() == 409).count()).isEqualTo(1);
        int winningIndex = responses.getFirst().statusCode() == 200 ? 0 : 1;
        var winner = winningIndex == 0 ? a : b;
        var rejected = responses.get(1 - winningIndex);
        assertThat(new String(rejected.body(), StandardCharsets.UTF_8)).contains("TRANSACTION_ID_CONFLICT");
        assertRetained(winner, responses.get(winningIndex), true);
        assertEffect(winner, before);
        assertReplayBoth(a, b, responses);
        assertRetained(winner, responses.get(winningIndex), true);
        assertEffect(winner, before);
    }

    private List<HttpResponse<byte[]>> race(MutationRaceRequest a, MutationRaceRequest b) throws Exception {
        gate.arm();
        List<HttpResponse<byte[]>> responses;
        try { responses = ConcurrentHttpBatch.send(List.of(a.at(first), b.at(second))); }
        finally { gate.disarm(); }
        assertThat(gate.arrivals()).as("both first attempts reached reservation").isEqualTo(2);
        assertThat(gate.duplicates()).as("one genuine losing reservation").isEqualTo(1);
        return responses;
    }

    private void assertReplayBoth(MutationRaceRequest a, MutationRaceRequest b, List<HttpResponse<byte[]>> originals) throws Exception {
        var replays = ConcurrentHttpBatch.send(List.of(a.at(first), a.at(second), b.at(first), b.at(second)));
        for (int i = 0; i < replays.size(); i++) {
            var original = originals.get(i < 2 ? 0 : 1);
            assertThat(replays.get(i).statusCode()).isEqualTo(original.statusCode());
            assertThat(replays.get(i).body()).isEqualTo(original.body());
        }
    }

    private void assertRetained(MutationRaceRequest winner, HttpResponse<byte[]> response, boolean success) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("""
                        SELECT o.state,o.operation_type,o.amount,o.http_status,o.response_body,
                            (SELECT COUNT(*) FROM dbo.journals j WHERE j.operation_id=o.operation_id),
                            (SELECT COUNT(*) FROM dbo.postings p JOIN dbo.journals j ON p.journal_id=j.journal_id WHERE j.operation_id=o.operation_id),
                            (SELECT COALESCE(SUM(CASE WHEN p.side='DEBIT' THEN CAST(p.amount AS decimal(38,0))
                                ELSE -CAST(p.amount AS decimal(38,0)) END),0)
                                FROM dbo.postings p JOIN dbo.journals j ON p.journal_id=j.journal_id WHERE j.operation_id=o.operation_id)
                        FROM dbo.operations o WHERE transaction_id=?
                        """)) {
            statement.setString(1, winner.transactionId().value());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo(success ? "SUCCEEDED" : "REJECTED");
                assertThat(rows.getString(2)).isEqualTo(winner.type().name());
                assertThat(rows.getLong(3)).isEqualTo(winner.amount());
                assertThat(rows.getInt(4)).isEqualTo(response.statusCode());
                assertThat(rows.getBytes(5)).isEqualTo(response.body());
                assertThat(rows.getInt(6)).isEqualTo(success ? 1 : 0);
                assertThat(rows.getInt(7)).isEqualTo(success ? 2 : 0);
                assertThat(rows.getBigDecimal(8)).isZero();
                assertThat(rows.next()).as("exactly one retained operation").isFalse();
            }
        }
    }

    private void assertEffect(MutationRaceRequest winner, Map<AccountId, Long> before) throws Exception {
        var expected = new HashMap<>(before);
        long amount = winner.amount();
        switch (winner.type()) {
            case CREDIT -> { expected.merge(winner.customer(), amount, Long::sum); expected.merge(funding, amount, Long::sum); }
            case DEBIT -> { expected.merge(winner.customer(), -amount, Long::sum); expected.merge(funding, -amount, Long::sum); }
            case TRANSFER -> { expected.merge(winner.customer(), -amount, Long::sum); expected.merge(winner.recipient(), amount, Long::sum); }
            default -> throw new AssertionError(winner.type());
        }
        assertUnchanged(expected);
    }

    private void assertUnchanged(Map<AccountId, Long> expected) throws Exception {
        for (var entry : expected.entrySet()) {
            assertThat(balance(entry.getKey())).isEqualTo(entry.getValue());
            assertReconciles(entry.getKey());
        }
    }

    private void assertReconciles(AccountId id) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("""
                        SELECT a.balance,COALESCE(SUM(CASE
                            WHEN p.side=CASE WHEN a.category='FUNDING_CLEARING' THEN 'DEBIT' ELSE 'CREDIT' END
                            THEN CAST(p.amount AS decimal(38,0)) ELSE -CAST(p.amount AS decimal(38,0)) END),0)
                        FROM dbo.accounts a LEFT JOIN dbo.postings p ON p.account_id=a.account_id
                        WHERE a.account_id=CAST(? AS uniqueidentifier) GROUP BY a.balance,a.category
                        """)) {
            statement.setString(1, id.value().toString());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getBigDecimal(2)).isEqualByComparingTo(Long.toString(rows.getLong(1)));
            }
        }
    }

    private Map<AccountId, Long> snapshot(AccountId... ids) throws Exception {
        var result = new HashMap<AccountId, Long>();
        for (var id : ids) { result.put(id, balance(id)); }
        return Map.copyOf(result);
    }

    private long balance(AccountId id) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT balance FROM dbo.accounts WHERE account_id=CAST(? AS uniqueidentifier)")) {
            statement.setString(1, id.value().toString());
            try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); return rows.getLong(1); }
        }
    }

    private AccountId customer(long opening) throws Exception {
        var id = new AccountId(UUID.randomUUID());
        createAccount(id, "CUSTOMER_LIABILITY");
        if (opening > 0) { fund(id, opening); }
        return id;
    }

    private void fund(AccountId customer, long amount) {
        first.bean(CreditHandler.class).handle(new CreditCommand(new TransactionId("cbs17-fixture-" + UUID.randomUUID()),
                customer, new Money(amount, IRR)));
    }

    private static void createAccount(AccountId id, String category) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment("SQLSERVER_INITIALIZER_TEST").openConnection();
                var statement = connection.prepareStatement("INSERT dbo.accounts (account_id,category,currency) VALUES(CAST(? AS uniqueidentifier),?,N'IRR')")) {
            statement.setString(1, id.value().toString());
            statement.setString(2, category);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private TransactionId id() { return new TransactionId("cbs17-" + UUID.randomUUID()); }

    private MutationRaceRequest request(OperationType type, TransactionId id, AccountId customer, AccountId recipient, long amount) {
        return new MutationRaceRequest(type, id, customer, recipient, amount);
    }
}
