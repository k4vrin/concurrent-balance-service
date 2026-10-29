package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.command.CreditCommand;
import dev.kavrin.balance.operation.application.credit.CreditHandler;
import dev.kavrin.balance.operation.domain.OperationType;
import dev.kavrin.balance.operation.domain.TransactionId;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies commit and response recovery against actual SQL Server effects for every mutation type.
 *
 * <p>Controlled test wrappers select failure boundaries, while real application transactions,
 * history and projections remain authoritative. Empty-read masking and HTTP response replacement
 * are explicit simulations, not physical network partitions. Peer replay and independent SQL
 * reconstruction must preserve one terminal outcome and at most one financial effect.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SqlServerRecoveryIT {
    private static final Currency IRR = Currency.getInstance("IRR");
    private static final long CAP = 10_000_000_000_000L;
    private final AccountId funding = new AccountId(UUID.randomUUID());
    private final ApplicationFaultPlan faults = new ApplicationFaultPlan();
    private RunningLedgerApplication first;
    private RunningLedgerApplication second;

    @BeforeAll
    void startApplications() throws Exception {
        createAccount(funding, "FUNDING_CLEARING");
        first = RunningLedgerApplication.start("cbs19-fault", funding, new ReservationRaceGate(), null, faults);
        try { second = RunningLedgerApplication.start("cbs19-peer", funding, new ReservationRaceGate()); }
        catch (Exception | Error failure) { first.close(); throw failure; }
    }

    @AfterAll
    void closeApplications() {
        if (second != null) { second.close(); }
        if (first != null) { first.close(); }
    }

    static Stream<Arguments> commitCases() {
        return Stream.of(OperationType.CREDIT, OperationType.DEBIT, OperationType.TRANSFER).flatMap(type ->
                Stream.of(true, false).flatMap(success -> Arrays.stream(CommitFaultMode.values())
                        .filter(mode -> mode != CommitFaultMode.NONE).map(mode -> Arguments.of(type, success, mode))));
    }

    static Stream<Arguments> responseCases() {
        return Stream.of(OperationType.CREDIT, OperationType.DEBIT, OperationType.TRANSFER)
                .flatMap(type -> Stream.of(true, false).map(success -> Arguments.of(type, success)));
    }

    @ParameterizedTest
    @MethodSource("commitCases")
    void commitFailureRecoveryNeverReexecutesAnUncertainEffect(OperationType type, boolean success, CommitFaultMode mode) throws Exception {
        var customer = customer(100);
        var recipient = customer(0);
        var command = request(type, id(), customer, recipient, success ? 25 : type == OperationType.CREDIT ? CAP + 1 : 101);
        var before = snapshot(customer, recipient, funding);
        faults.arm(mode);
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(command.at(first), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(response.statusCode()).isEqualTo(mode == CommitFaultMode.AFTER_COMMIT ? success ? 200 : 409 : 503);
            assertThat(faults.reservations).hasValue(1);
            assertThat(faults.hiddenReads).hasValue(mode == CommitFaultMode.HIDE_COMMITTED_RESULT ? 3 : 0);
            if (mode == CommitFaultMode.BEFORE_COMMIT) { assertAbsent(command.transactionId()); assertUnchanged(before); }
            else if (success) { assertEffect(command, before); }
            else { assertUnchanged(before); }
            var recovered = client.send(command.at(second), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(recovered.statusCode()).isEqualTo(success ? 200 : 409);
            assertRetained(command, recovered, success);
            if (success) { assertEffect(command, before); } else { assertUnchanged(before); }
            if (!success) { fund(customer, 300); }
            var after = snapshot(customer, recipient, funding);
            var repeated = client.send(command.at(first), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(repeated.statusCode()).isEqualTo(recovered.statusCode());
            assertThat(repeated.body()).isEqualTo(recovered.body());
            assertRetained(command, repeated, success);
            assertUnchanged(after);
        }
    }

    @ParameterizedTest
    @MethodSource("responseCases")
    void replacedHttpResponseRecoversCommittedOutcomeThroughAnotherApplication(OperationType type, boolean success) throws Exception {
        var customer = customer(100);
        var recipient = customer(0);
        var command = request(type, id(), customer, recipient, success ? 25 : type == OperationType.CREDIT ? CAP + 1 : 101);
        var before = snapshot(customer, recipient, funding);
        faults.arm(CommitFaultMode.NONE);
        try (var client = HttpClient.newHttpClient()) {
            var marked = HttpRequest.newBuilder(command.at(first), (name, value) -> true).header("X-Test-Drop-Response", "true").build();
            assertThat(client.send(marked, HttpResponse.BodyHandlers.ofByteArray()).statusCode()).isEqualTo(503);
            assertThat(faults.droppedResponses).hasValue(1);
            assertThat(faults.droppedStatus).isEqualTo(success ? 200 : 409);
            assertThat(faults.reservations).hasValue(1);
            if (success) { assertEffect(command, before); } else { assertUnchanged(before); }
            if (!success) { fund(customer, 300); }
            var after = snapshot(customer, recipient, funding);
            var recovered = client.send(command.at(second), HttpResponse.BodyHandlers.ofByteArray());
            assertThat(recovered.statusCode()).isEqualTo(faults.droppedStatus);
            assertThat(recovered.body()).isEqualTo(faults.droppedBody);
            assertRetained(command, recovered, success);
            assertUnchanged(after);
        }
    }

    private void assertAbsent(TransactionId id) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT COUNT(*) FROM dbo.operations WHERE transaction_id=?")) {
            statement.setString(1, id.value());
            try (var rows = statement.executeQuery()) { rows.next(); assertThat(rows.getInt(1)).isZero(); }
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
        first.bean(CreditHandler.class).handle(new CreditCommand(new TransactionId("cbs19-fixture-" + UUID.randomUUID()),
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

    private TransactionId id() { return new TransactionId("cbs19-" + UUID.randomUUID()); }

    private MutationRaceRequest request(OperationType type, TransactionId id, AccountId customer, AccountId recipient, long amount) {
        return new MutationRaceRequest(type, id, customer, recipient, amount);
    }
}
