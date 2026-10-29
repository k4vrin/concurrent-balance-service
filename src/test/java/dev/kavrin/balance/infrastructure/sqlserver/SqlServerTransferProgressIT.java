package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.application.AccountLockOrder;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.command.CreditCommand;
import dev.kavrin.balance.operation.application.credit.CreditHandler;
import dev.kavrin.balance.operation.domain.OperationType;
import dev.kavrin.balance.operation.domain.TransactionId;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies opposite transfers and unrelated HTTP progress while an external account lock stalls a writer.
 *
 * <p>Two applications use independent HTTP and persistence resources against real SQL Server.
 * Passive adapter observations preserve real locking, and external JDBC locks remain held until
 * the independent request commits. Quiescent SQL checks history counts and reconstructs projections.
 *
 * <p>Returned adapter order is observed rather than an independent driver-level acquisition trace.
 * Selected interleavings in one JVM do not prove absence of every deadlock or establish throughput.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlServerTransferProgressIT {
    private static final Currency IRR = Currency.getInstance("IRR");
    private final AccountId funding = new AccountId(UUID.randomUUID());
    private final ReservationRaceGate gate = new ReservationRaceGate();
    private final AccountLockProbe locks = new AccountLockProbe();
    private RunningLedgerApplication first;
    private RunningLedgerApplication second;

    @BeforeAll
    void startApplications() throws Exception {
        createAccount(funding, "FUNDING_CLEARING");
        first = RunningLedgerApplication.start("cbs18-first", funding, gate, locks);
        try { second = RunningLedgerApplication.start("cbs18-second", funding, gate, locks); }
        catch (Exception | Error failure) { first.close(); throw failure; }
    }

    @AfterAll
    void closeApplications() {
        if (second != null) { second.close(); }
        if (first != null) { first.close(); }
    }

    @ParameterizedTest
    @CsvSource({"30,20", "100,100"})
    void oppositeTransfersReturnTheSameAccountOrderAndCommitEachEffectOnce(long forwardAmount, long reverseAmount) throws Exception {
        var a = customer(100);
        var b = customer(100);
        long fundingBefore = balance(funding);
        var forward = mutation(OperationType.TRANSFER, a, b, forwardAmount);
        var reverse = mutation(OperationType.TRANSFER, b, a, reverseAmount);
        locks.reset();
        gate.arm();
        List<HttpResponse<byte[]>> responses;
        try { responses = ConcurrentHttpBatch.send(List.of(forward.at(first), reverse.at(second))); }
        finally { gate.disarm(); }
        assertThat(gate.arrivals()).isEqualTo(2);
        assertThat(gate.duplicates()).isZero();
        assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
        var expectedOrder = List.of(a, b).stream().sorted(AccountLockOrder.DESCENDING).toList();
        assertThat(locks.completedOrders()).containsExactlyInAnyOrder(expectedOrder, expectedOrder);
        assertOutcome(forward);
        assertOutcome(reverse);
        assertBalances(Map.of(a, 100 - forwardAmount + reverseAmount, b, 100 + forwardAmount - reverseAmount, funding, fundingBefore));
        var replays = ConcurrentHttpBatch.send(List.of(forward.at(first), forward.at(second), reverse.at(first), reverse.at(second)));
        for (int i = 0; i < replays.size(); i++) {
            assertThat(replays.get(i).statusCode()).isEqualTo(200);
            assertThat(replays.get(i).body()).isEqualTo(responses.get(i < 2 ? 0 : 1).body());
        }
        assertOutcome(forward);
        assertOutcome(reverse);
        assertBalances(Map.of(a, 100 - forwardAmount + reverseAmount, b, 100 + forwardAmount - reverseAmount, funding, fundingBefore));
    }

    @ParameterizedTest
    @CsvSource({"CREDIT,true", "CREDIT,false", "DEBIT,true", "DEBIT,false",
            "TRANSFER,true", "TRANSFER,false", "BALANCE,true", "BALANCE,false"})
    void unrelatedRequestCompletesBeforeBlockedTransferIsReleased(String type, boolean sameApplication) throws Exception {
        var source = customer(100);
        var destination = customer(0);
        var independent = customer(100);
        var independentRecipient = customer(0);
        long fundingBefore = balance(funding);
        var blocked = mutation(OperationType.TRANSFER, source, destination, 40);
        var application = sameApplication ? first : second;
        var other = type.equals("BALANCE") ? null : mutation(OperationType.valueOf(type), independent, independentRecipient, 25);
        HttpRequest independentHttp = other == null
                ? HttpRequest.newBuilder(application.uri("/api/v1/accounts/" + independent.value() + "/balance"))
                        .timeout(Duration.ofSeconds(30)).GET().build()
                : other.at(application);
        locks.reset();
        locks.observeBlocked(source);
        try (var blocker = SqlServerTestDatabase.fromEnvironment().openConnection();
                var client = HttpClient.newHttpClient(); var worker = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.prepareStatement("UPDATE dbo.accounts SET balance=balance WHERE account_id=CAST(? AS uniqueidentifier)")) {
                statement.setString(1, source.value().toString());
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            try {
                var waiting = worker.submit(() -> client.send(blocked.at(first), HttpResponse.BodyHandlers.ofByteArray()));
                assertThat(locks.awaitBlockedEntry()).as("blocked transfer entered actual account-lock adapter").isTrue();
                assertUncommittedReservation(blocked.transactionId());
                assertThat(waiting.isDone()).isFalse();
                var progressed = client.send(independentHttp, HttpResponse.BodyHandlers.ofByteArray());
                assertThat(progressed.statusCode()).isEqualTo(200);
                assertThat(waiting.isDone()).as("independent response precedes blocker release and transfer completion").isFalse();
                var expected = new HashMap<AccountId, Long>(Map.of(independent, 100L, independentRecipient, 0L, funding, fundingBefore));
                switch (type) {
                    case "CREDIT" -> { expected.put(independent, 125L); expected.put(funding, fundingBefore + 25); }
                    case "DEBIT" -> { expected.put(independent, 75L); expected.put(funding, fundingBefore - 25); }
                    case "TRANSFER" -> { expected.put(independent, 75L); expected.put(independentRecipient, 25L); }
                    case "BALANCE" -> assertThat(new String(progressed.body(), java.nio.charset.StandardCharsets.UTF_8))
                            .contains("\"balanceRial\":100", independent.value().toString());
                    default -> throw new AssertionError(type);
                }
                if (other != null) { assertOutcome(other); }
                assertBalances(expected);
                // The external lock is still held while independent history/projections are inspected.
                assertThat(waiting.isDone()).isFalse();
                blocker.rollback();
                var resumed = waiting.get(30, TimeUnit.SECONDS);
                assertThat(resumed.statusCode()).isEqualTo(200);
                assertOutcome(blocked);
                expected.put(source, 60L);
                expected.put(destination, 40L);
                assertBalances(expected);
                var replay = client.send(blocked.at(second), HttpResponse.BodyHandlers.ofByteArray());
                assertThat(replay.statusCode()).isEqualTo(200);
                assertThat(replay.body()).isEqualTo(resumed.body());
                assertOutcome(blocked);
                assertBalances(expected);
            } finally { blocker.rollback(); }
        }
    }

    private void assertUncommittedReservation(TransactionId id) throws Exception {
        // Diagnostic observation only: financial decisions never use READUNCOMMITTED.
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT state FROM dbo.operations WITH (READUNCOMMITTED) WHERE transaction_id=?")) {
            statement.setQueryTimeout(2);
            statement.setString(1, id.value());
            try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("PENDING"); }
        }
    }

    private void assertOutcome(MutationRaceRequest request) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("""
                        SELECT o.state,o.operation_type,o.amount,
                            (SELECT COUNT(*) FROM dbo.journals j WHERE j.operation_id=o.operation_id),
                            (SELECT COUNT(*) FROM dbo.postings p JOIN dbo.journals j ON p.journal_id=j.journal_id WHERE j.operation_id=o.operation_id),
                            (SELECT COALESCE(SUM(CASE WHEN p.side='DEBIT' THEN CAST(p.amount AS decimal(38,0))
                                ELSE -CAST(p.amount AS decimal(38,0)) END),0)
                                FROM dbo.postings p JOIN dbo.journals j ON p.journal_id=j.journal_id WHERE j.operation_id=o.operation_id)
                        FROM dbo.operations o WHERE transaction_id=?
                        """)) {
            statement.setString(1, request.transactionId().value());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("SUCCEEDED");
                assertThat(rows.getString(2)).isEqualTo(request.type().name());
                assertThat(rows.getLong(3)).isEqualTo(request.amount());
                assertThat(rows.getInt(4)).isEqualTo(1);
                assertThat(rows.getInt(5)).isEqualTo(2);
                assertThat(rows.getBigDecimal(6)).isZero();
                assertThat(rows.next()).isFalse();
            }
        }
    }

    private void assertBalances(Map<AccountId, Long> expected) throws Exception {
        for (var entry : expected.entrySet()) {
            try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                    var statement = connection.prepareStatement("""
                            SELECT a.balance,COALESCE(SUM(CASE
                                WHEN p.side=CASE WHEN a.category='FUNDING_CLEARING' THEN 'DEBIT' ELSE 'CREDIT' END
                                THEN CAST(p.amount AS decimal(38,0)) ELSE -CAST(p.amount AS decimal(38,0)) END),0)
                            FROM dbo.accounts a LEFT JOIN dbo.postings p ON p.account_id=a.account_id
                            WHERE a.account_id=CAST(? AS uniqueidentifier) GROUP BY a.balance,a.category
                            """)) {
                statement.setQueryTimeout(2);
                statement.setString(1, entry.getKey().value().toString());
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong(1)).isEqualTo(entry.getValue());
                    assertThat(rows.getBigDecimal(2)).isEqualByComparingTo(entry.getValue().toString());
                }
            }
        }
    }

    private AccountId customer(long opening) throws Exception {
        var id = new AccountId(UUID.randomUUID());
        createAccount(id, "CUSTOMER_LIABILITY");
        if (opening > 0) {
            first.bean(CreditHandler.class).handle(new CreditCommand(new TransactionId("cbs18-fixture-" + UUID.randomUUID()),
                    id, new Money(opening, IRR)));
        }
        return id;
    }

    private static void createAccount(AccountId id, String category) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment("SQLSERVER_INITIALIZER_TEST").openConnection();
                var statement = connection.prepareStatement("INSERT dbo.accounts(account_id,category,currency) VALUES(CAST(? AS uniqueidentifier),?,N'IRR')")) {
            statement.setString(1, id.value().toString());
            statement.setString(2, category);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private long balance(AccountId id) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT balance FROM dbo.accounts WHERE account_id=CAST(? AS uniqueidentifier)")) {
            statement.setString(1, id.value().toString());
            try (var rows = statement.executeQuery()) { assertThat(rows.next()).isTrue(); return rows.getLong(1); }
        }
    }

    private MutationRaceRequest mutation(OperationType type, AccountId customer, AccountId recipient, long amount) {
        return new MutationRaceRequest(type, new TransactionId("cbs18-" + UUID.randomUUID()), customer, recipient, amount);
    }
}
