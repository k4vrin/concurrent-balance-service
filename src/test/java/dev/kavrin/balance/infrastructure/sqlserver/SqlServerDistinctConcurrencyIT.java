package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.account.infrastructure.persistence.AccountStore;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.command.CreditCommand;
import dev.kavrin.balance.operation.application.credit.CreditHandler;
import dev.kavrin.balance.operation.domain.TransactionId;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;

/**
 * Verifies concurrent distinct mutation identities through HTTP against the dedicated SQL Server test database.
 *
 * <p>Barriers coordinate clients and first entry to real account locking without replacing SQL locks.
 * Tests assert retained decisions, exact history counts, absence of committed pending operations,
 * funds conservation and independent projection reconstruction after workers finish.
 *
 * <p>Fixtures use journal-backed credits and restricted existing database identities. This suite
 * covers one application instance and selected interleavings; cross-instance duplicate/conflict
 * races, opposite transfers, independent progress and performance remain separate gates.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SqlServerDistinctConcurrencyIT {
    private static final Currency IRR = Currency.getInstance("IRR");
    private static final AccountId FUNDING = new AccountId(UUID.randomUUID());
    @Autowired CreditHandler credits;
    @Autowired Environment environment;
    @MockitoSpyBean AccountStore accounts;

    @DynamicPropertySource
    static void explicitDatabaseAndFunding(DynamicPropertyRegistry properties) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection()) {
            assertThat(connection.isValid(5)).isTrue();
        }
        properties.add("spring.datasource.url", () -> System.getenv("SQLSERVER_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("SQLSERVER_TEST_USERNAME"));
        properties.add("spring.datasource.password", () -> System.getenv("SQLSERVER_TEST_PASSWORD"));
        properties.add("balance.execution.enabled", () -> "true");
        properties.add("balance.execution.funding[IRR].account-id", () -> FUNDING.value().toString());
        properties.add("balance.execution.funding[IRR].policy-version", () -> "cbs16-funding-v1");
    }

    @BeforeAll
    static void zeroFunding() throws Exception { createAccount(FUNDING, "FUNDING_CLEARING"); }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void concurrentCreditsPreserveEveryDistinctEffectWithSharedFunding(boolean sameCustomer) throws Exception {
        String prefix = prefix();
        var participants = new ArrayList<AccountId>();
        var requests = new ArrayList<HttpRequest>();
        var shared = customer(0);
        long fundingBefore = balance(FUNDING);
        for (int i = 0; i < 8; i++) {
            var customer = sameCustomer ? shared : customer(0);
            participants.add(customer);
            requests.add(credit(prefix, i, customer, 25));
        }
        var responses = race(requests);
        assertStatuses(responses, 8, 0);
        assertBatch(prefix, 8, 8, 0);
        for (var customer : participants) {
            assertThat(balance(customer)).isEqualTo(sameCustomer ? 200 : 25);
            assertReconciles(customer);
        }
        assertThat(balance(FUNDING)).isEqualTo(fundingBefore + 200);
        assertReconciles(FUNDING);
        assertReplay(requests, responses);
        assertBatch(prefix, 8, 8, 0);
        for (var customer : participants) { assertReconciles(customer); }
        assertReconciles(FUNDING);
    }

    @Test
    void competingDebitsCommitOnlyAffordableWorkAndRetainLosingDecisions() throws Exception {
        var customer = customer(100);
        String prefix = prefix();
        long fundingBefore = balance(FUNDING);
        var requests = new ArrayList<HttpRequest>();
        for (int i = 0; i < 8; i++) { requests.add(debit(prefix, i, customer, 30)); }
        var responses = race(requests);
        assertStatuses(responses, 3, 5);
        assertBatch(prefix, 8, 3, 5);
        assertThat(balance(customer)).isEqualTo(10);
        assertThat(balance(FUNDING)).isEqualTo(fundingBefore - 90);
        assertReconciles(customer);
        assertReconciles(FUNDING);
        fund(customer, 300);
        assertReplay(requests, responses);
        assertThat(balance(customer)).isEqualTo(310);
        assertBatch(prefix, 8, 3, 5);
        assertReconciles(customer);
        assertReconciles(FUNDING);
    }

    @Test
    void competingTransfersCannotOverdrawTheirSharedSource() throws Exception {
        var source = customer(100);
        String prefix = prefix();
        long fundingBefore = balance(FUNDING);
        var destinations = new ArrayList<AccountId>();
        var requests = new ArrayList<HttpRequest>();
        for (int i = 0; i < 8; i++) {
            var destination = customer(0);
            destinations.add(destination);
            requests.add(transfer(prefix, i, source, destination, 30));
        }
        var responses = race(requests);
        assertStatuses(responses, 3, 5);
        assertBatch(prefix, 8, 3, 5);
        assertThat(balance(source)).isEqualTo(10);
        long recipientTotal = 0;
        for (int i = 0; i < destinations.size(); i++) {
            long amount = balance(destinations.get(i));
            assertThat(amount).isEqualTo(responses.get(i).statusCode() == 200 ? 30 : 0);
            recipientTotal += amount;
            assertReconciles(destinations.get(i));
        }
        assertThat(recipientTotal + balance(source)).isEqualTo(100);
        assertThat(balance(FUNDING)).isEqualTo(fundingBefore);
        assertReconciles(source);
        fund(source, 300);
        assertReplay(requests, responses);
        assertThat(balance(source)).isEqualTo(310);
        assertBatch(prefix, 8, 3, 5);
        for (var destination : destinations) { assertReconciles(destination); }
        assertReconciles(source);
        assertReconciles(FUNDING);
    }

    @Test
    void mixedDistinctCreditsDebitsAndTransfersReconcileUnderContention() throws Exception {
        var source = customer(1000);
        var destination = customer(1000);
        String prefix = prefix();
        long fundingBefore = balance(FUNDING);
        var requests = new ArrayList<HttpRequest>();
        for (int i = 0; i < 3; i++) {
            requests.add(credit(prefix, i * 3, source, 25));
            requests.add(debit(prefix, i * 3 + 1, source, 10));
            requests.add(transfer(prefix, i * 3 + 2, source, destination, 15));
        }
        var responses = race(requests);
        assertStatuses(responses, 9, 0);
        assertBatch(prefix, 9, 9, 0);
        assertThat(balance(source)).isEqualTo(1000);
        assertThat(balance(destination)).isEqualTo(1045);
        assertThat(balance(FUNDING)).isEqualTo(fundingBefore + 45);
        assertReconciles(source);
        assertReconciles(destination);
        assertReconciles(FUNDING);
        assertReplay(requests, responses);
        assertBatch(prefix, 9, 9, 0);
        assertReconciles(source);
        assertReconciles(destination);
        assertReconciles(FUNDING);
    }

    private List<HttpResponse<byte[]>> race(List<HttpRequest> requests) throws Exception {
        var locking = new CyclicBarrier(requests.size());
        var arrivals = new AtomicInteger();
        doAnswer(call -> {
            // Only first attempts meet here; actual lock acquisition/order remains the production adapter's job.
            if (arrivals.incrementAndGet() <= requests.size()) { locking.await(4, TimeUnit.SECONDS); }
            return call.callRealMethod();
        }).when(accounts).lockAll(anyCollection());
        try {
            var result = ConcurrentHttpBatch.send(requests);
            assertThat(arrivals.get()).isGreaterThanOrEqualTo(requests.size());
            return result;
        } finally { Mockito.reset(accounts); }
    }

    private void assertStatuses(List<HttpResponse<byte[]>> responses, long succeeded, long rejected) {
        assertThat(responses.stream().filter(r -> r.statusCode() == 200).count()).isEqualTo(succeeded);
        assertThat(responses.stream().filter(r -> r.statusCode() == 409).count()).isEqualTo(rejected);
        assertThat(responses).allSatisfy(response -> {
            assertThat(response.statusCode()).isIn(200, 409);
            if (response.statusCode() == 409) {
                assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("INSUFFICIENT_FUNDS");
            }
        });
    }

    private void assertReplay(List<HttpRequest> requests, List<HttpResponse<byte[]>> originals) throws Exception {
        var replays = ConcurrentHttpBatch.send(requests);
        for (int i = 0; i < originals.size(); i++) {
            assertThat(replays.get(i).statusCode()).isEqualTo(originals.get(i).statusCode());
            assertThat(replays.get(i).body()).isEqualTo(originals.get(i).body());
        }
    }

    private void assertBatch(String prefix, int operations, int successes, int rejections) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("""
                        SELECT COUNT(*),
                            COALESCE(SUM(CASE WHEN state='SUCCEEDED' THEN 1 ELSE 0 END),0),
                            COALESCE(SUM(CASE WHEN state='REJECTED' THEN 1 ELSE 0 END),0),
                            COALESCE(SUM(CASE WHEN state='PENDING' THEN 1 ELSE 0 END),0)
                        FROM dbo.operations WHERE transaction_id LIKE ?
                        """)) {
            statement.setString(1, prefix + "-%");
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getInt(1)).isEqualTo(operations);
                assertThat(rows.getInt(2)).isEqualTo(successes);
                assertThat(rows.getInt(3)).isEqualTo(rejections);
                assertThat(rows.getInt(4)).isZero();
            }
            try (var history = connection.prepareStatement("""
                    SELECT o.state, COUNT(DISTINCT j.journal_id), COUNT(p.line_number),
                        COALESCE(SUM(CASE WHEN p.side='DEBIT' THEN CAST(p.amount AS decimal(38,0))
                            ELSE -CAST(p.amount AS decimal(38,0)) END),0)
                    FROM dbo.operations o
                    LEFT JOIN dbo.journals j ON j.operation_id=o.operation_id
                    LEFT JOIN dbo.postings p ON p.journal_id=j.journal_id
                    WHERE o.transaction_id LIKE ? GROUP BY o.operation_id,o.state
                    """)) {
                history.setString(1, prefix + "-%");
                try (var rows = history.executeQuery()) {
                    int seen = 0;
                    while (rows.next()) {
                        seen++;
                        boolean success = rows.getString(1).equals("SUCCEEDED");
                        assertThat(rows.getInt(2)).isEqualTo(success ? 1 : 0);
                        assertThat(rows.getInt(3)).isEqualTo(success ? 2 : 0);
                        assertThat(rows.getBigDecimal(4)).isZero();
                    }
                    assertThat(seen).isEqualTo(operations);
                }
            }
        }
    }

    private void assertReconciles(AccountId id) throws Exception {
        // Workers have finished: reconstruct from immutable history with a wider accumulator in one query.
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("""
                        SELECT a.balance, COALESCE(SUM(CASE
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

    private AccountId customer(long opening) throws Exception {
        var id = new AccountId(UUID.randomUUID());
        createAccount(id, "CUSTOMER_LIABILITY");
        if (opening > 0) { fund(id, opening); }
        return id;
    }

    private void fund(AccountId customer, long amount) {
        credits.handle(new CreditCommand(new TransactionId("cbs16-fixture-" + UUID.randomUUID()), customer, new Money(amount, IRR)));
    }

    private static void createAccount(AccountId id, String category) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment("SQLSERVER_INITIALIZER_TEST").openConnection();
                var statement = connection.prepareStatement("INSERT dbo.accounts (account_id,category,currency) VALUES (CAST(? AS uniqueidentifier),?,N'IRR')")) {
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

    private String prefix() { return "cbs16-" + UUID.randomUUID(); }

    private HttpRequest credit(String prefix, int index, AccountId customer, long amount) {
        return mutation("/api/v1/accounts/" + customer.value() + "/credits", "{\"transactionId\":\""
                + prefix + "-" + index + "\",\"amountRial\":" + amount + ",\"currency\":\"IRR\"}");
    }

    private HttpRequest debit(String prefix, int index, AccountId customer, long amount) {
        return mutation("/api/v1/accounts/" + customer.value() + "/debits", "{\"transactionId\":\""
                + prefix + "-" + index + "\",\"amountRial\":" + amount + ",\"currency\":\"IRR\"}");
    }

    private HttpRequest transfer(String prefix, int index, AccountId source, AccountId destination, long amount) {
        return mutation("/api/v1/transfers", "{\"transactionId\":\"" + prefix + "-" + index
                + "\",\"sourceAccountId\":\"" + source.value() + "\",\"destinationAccountId\":\""
                + destination.value() + "\",\"amountRial\":" + amount + ",\"currency\":\"IRR\"}");
    }

    private HttpRequest mutation(String path, String body) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + environment.getProperty("local.server.port") + path))
                .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    }
}
