package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.ConcurrentBalanceServiceApplication;
import dev.kavrin.balance.account.domain.Account;
import dev.kavrin.balance.account.domain.AccountCategory;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.application.AmountLimits;
import dev.kavrin.balance.operation.application.CreditCommand;
import dev.kavrin.balance.operation.application.CreditHandler;
import dev.kavrin.balance.operation.application.CreditResponseEncoder;
import dev.kavrin.balance.operation.application.CreditStore;
import dev.kavrin.balance.operation.application.ExecutionPolicy;
import dev.kavrin.balance.operation.application.FundingSelection;
import dev.kavrin.balance.operation.application.OperationCoordinator;
import dev.kavrin.balance.operation.application.OperationFailure;
import dev.kavrin.balance.operation.application.SupportedCurrencies;
import dev.kavrin.balance.operation.domain.TransactionId;
import dev.kavrin.balance.operation.infrastructure.persistence.JpaCreditStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SqlServerCreditIT {
    private static final Currency IRR = Currency.getInstance("IRR");
    private static final long CAP = 10_000_000_000_000L;
    private static final AccountId FUNDING = new AccountId(UUID.randomUUID());
    @Autowired CreditHandler handler;
    @Autowired OperationCoordinator coordinator;
    @Autowired CreditResponseEncoder encoder;
    @Autowired ExecutionPolicy policy;
    @Autowired Environment environment;
    @MockitoSpyBean JpaCreditStore store;

    @DynamicPropertySource
    static void databaseAndPolicy(DynamicPropertyRegistry properties) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection()) {
            assertThat(connection.isValid(5)).isTrue();
        }
        properties.add("spring.datasource.url", () -> System.getenv("SQLSERVER_TEST_URL"));
        properties.add("spring.datasource.username", () -> System.getenv("SQLSERVER_TEST_USERNAME"));
        properties.add("spring.datasource.password", () -> System.getenv("SQLSERVER_TEST_PASSWORD"));
        properties.add("balance.execution.enabled", () -> "true");
        properties.add("balance.execution.funding[IRR].account-id", () -> FUNDING.value().toString());
        properties.add("balance.execution.funding[IRR].policy-version", () -> "credit-it-funding-v1");
    }

    @BeforeAll
    static void committedZeroFunding() throws Exception { createAccount(FUNDING, "FUNDING_CLEARING"); }

    @Test
    void httpCreditCommitsBalancedHistoryAndReplaysExactBytesAfterOtherChanges() throws Exception {
        var customer = customer();
        long fundingBefore = balance(FUNDING);
        var command = command(customer, 100);
        var first = post(command);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        assertThat(first.body()).isEqualTo(retainedBody(command));
        assertThat(balance(customer)).isEqualTo(100);
        assertCounts(command, 1, 1, 2);
        assertBalanced(command);
        assertReconciles(customer);
        assertThat(post(command(customer, 200)).statusCode()).isEqualTo(200);
        var replay = post(command);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(replay.body()).isEqualTo(first.body());
        assertThat(balance(customer)).isEqualTo(300);
        assertThat(balance(FUNDING)).isEqualTo(fundingBefore + 300);
        assertCounts(command, 1, 1, 2);
        assertReconciles(customer);
        assertReconciles(FUNDING, true);
    }

    @Test
    void aFreshHandlerReplaysBeforeCurrentCurrencyAndFundingSelection() throws Exception {
        var command = command(customer(), 100);
        var original = handler.handle(command);
        var changed = new ExecutionPolicy("changed", new SupportedCurrencies(Set.of()), new AmountLimits(1, 1), Map.of());
        var fresh = new CreditHandler(coordinator, changed, store, encoder);

        var replay = fresh.handle(command);

        assertThat(replay.storedResponse().orElseThrow().body()).isEqualTo(original.storedResponse().orElseThrow().body());
        assertThat(balance(command.accountId())).isEqualTo(100);
        assertCounts(command, 1, 1, 2);
    }

    @Test
    void replaySurvivesClosingAndRecreatingTheApplicationContextAndPool() throws Exception {
        var command = command(customer(), 100);
        byte[] original;
        try (var first = independentApplication()) {
            original = first.getBean(CreditHandler.class).handle(command).storedResponse().orElseThrow().body();
        }
        try (var second = independentApplication()) {
            assertThat(second.getBean(CreditHandler.class).handle(command).storedResponse().orElseThrow().body()).isEqualTo(original);
        }
        assertThat(balance(command.accountId())).isEqualTo(100);
        assertCounts(command, 1, 1, 2);
    }

    @Test
    void changedPayloadUnderTheSameIdConflictsWithoutAdditionalEffects() throws Exception {
        var original = command(customer(), 100);
        assertThat(post(original).statusCode()).isEqualTo(200);
        var conflict = new CreditCommand(original.transactionId(), original.accountId(), new Money(101, IRR));

        var response = post(conflict);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("TRANSACTION_ID_CONFLICT");
        assertThat(balance(original.accountId())).isEqualTo(100);
        assertCounts(original, 1, 1, 2);
    }

    @Test
    void simultaneousReservationsRecoverOneCommittedResult() throws Exception {
        var command = command(customer(), 100);
        var barrier = new CyclicBarrier(2);
        var reads = new AtomicInteger();
        doAnswer(call -> {
            var result = call.callRealMethod();
            if (reads.incrementAndGet() <= 2) { barrier.await(3, TimeUnit.SECONDS); }
            return result;
        }).when(store).replay(any(), any());

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> handler.handle(command));
            var second = workers.submit(() -> handler.handle(command));
            assertThat(first.get(10, TimeUnit.SECONDS).storedResponse().orElseThrow().body())
                    .isEqualTo(second.get(10, TimeUnit.SECONDS).storedResponse().orElseThrow().body());
        }

        assertThat(balance(command.accountId())).isEqualTo(100);
        assertCounts(command, 1, 1, 2);
        assertBalanced(command);
    }

    @Test
    void transactionCapRejectionIsRetainedAfterPolicyChanges() throws Exception {
        var command = command(customer(), CAP + 1);
        var first = post(command);
        assertThat(first.statusCode()).isEqualTo(409);
        assertThat(new String(first.body(), StandardCharsets.UTF_8)).contains("TRANSACTION_LIMIT_EXCEEDED");
        assertThat(first.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        assertThat(first.body()).isEqualTo(retainedBody(command));
        var higher = new ExecutionPolicy("higher", policy.supportedCurrencies(), new AmountLimits(CAP * 2, CAP * 2), policy.fundingByCurrency());

        var replay = new CreditHandler(coordinator, higher, store, encoder).handle(command);

        assertThat(replay.storedResponse().orElseThrow().body()).isEqualTo(first.body());
        assertThat(balance(command.accountId())).isZero();
        assertCounts(command, 1, 0, 0);
    }

    @Test
    void customerBalanceCapAcceptsEqualityAndRejectsTheNextRial() throws Exception {
        var customer = customer();
        assertThat(post(command(customer, CAP - 50)).statusCode()).isEqualTo(200);
        var rejected = command(customer, 100);
        var response = post(rejected);
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("BALANCE_LIMIT_EXCEEDED");
        assertCounts(rejected, 1, 0, 0);
        assertThat(post(command(customer, 50)).statusCode()).isEqualTo(200);
        assertThat(balance(customer)).isEqualTo(CAP);
        assertThat(post(rejected).body()).isEqualTo(response.body());
        assertThat(post(command(customer, 1)).statusCode()).isEqualTo(409);
        assertReconciles(customer);
    }

    @Test
    void missingCustomerRejectionRemainsAfterTheAccountIsCreated() throws Exception {
        var id = new AccountId(UUID.randomUUID());
        var command = command(id, 100);
        var first = post(command);
        assertThat(first.statusCode()).isEqualTo(404);
        createAccount(id, "CUSTOMER_LIABILITY");

        assertThat(post(command).body()).isEqualTo(first.body());
        assertThat(balance(id)).isZero();
        assertCounts(command, 1, 0, 0);
    }

    @Test
    void unsupportedNewCurrencyLeavesNoOperation() throws Exception {
        var command = new CreditCommand(new TransactionId("credit-" + UUID.randomUUID()), customer(), new Money(100, Currency.getInstance("USD")));

        assertThat(post(command).statusCode()).isEqualTo(400);
        assertCounts(command, 0, 0, 0);
    }

    @Test
    void missingInternalFundingIsATechnicalFailureAndRollsBackReservation() throws Exception {
        var command = command(customer(), 100);
        var invalid = new ExecutionPolicy("missing", policy.supportedCurrencies(), policy.limits(),
                Map.of(IRR, new FundingSelection(new AccountId(UUID.randomUUID()), "missing")));

        assertThatThrownBy(() -> new CreditHandler(coordinator, invalid, store, encoder).handle(command))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("participant");
        assertCounts(command, 0, 0, 0);
        assertThat(balance(command.accountId())).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"journal", "projections", "outcome"})
    void failureAfterAWritingBoundaryRollsBackEverything(String boundary) throws Exception {
        var command = command(customer(), 100);
        long fundingBefore = balance(FUNDING);
        switch (boundary) {
            case "journal" -> doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("injected"); })
                    .when(store).appendJournal(any(), any());
            case "projections" -> doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("injected"); })
                    .when(store).applyProjections(any());
            case "outcome" -> doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("injected"); })
                    .when(store).complete(any(), any(), any());
            default -> throw new AssertionError(boundary);
        }

        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(OperationFailure.class)
                .hasRootCauseMessage("injected")
                .satisfies(failure -> assertThat(((OperationFailure) failure).reason()).isEqualTo(OperationFailure.Reason.UNAVAILABLE));

        assertCounts(command, 0, 0, 0);
        assertThat(balance(command.accountId())).isZero();
        assertThat(balance(FUNDING)).isEqualTo(fundingBefore);
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkedFundingOverflowRollsBackTheActualOperationReservation() throws Exception {
        var command = command(customer(), 100);
        long fundingBefore = balance(FUNDING);
        // Inject only the locked domain snapshot; never commit an unreconciled setup balance.
        doAnswer(call -> {
            var actual = (Map<AccountId, CreditStore.LockedAccount>) call.callRealMethod();
            var result = new HashMap<>(actual);
            result.put(FUNDING, new CreditStore.LockedAccount(new Account(FUNDING, AccountCategory.FUNDING_CLEARING,
                    new Money(Long.MAX_VALUE, IRR)), true));
            return Map.copyOf(result);
        }).when(store).lockAccounts(any());

        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(ArithmeticException.class);

        assertCounts(command, 0, 0, 0);
        assertThat(balance(command.accountId())).isZero();
        assertThat(balance(FUNDING)).isEqualTo(fundingBefore);
    }

    private CreditCommand command(AccountId customer, long amount) {
        return new CreditCommand(new TransactionId("credit-" + UUID.randomUUID()), customer, new Money(amount, IRR));
    }

    private AccountId customer() throws Exception {
        var id = new AccountId(UUID.randomUUID());
        createAccount(id, "CUSTOMER_LIABILITY");
        return id;
    }

    private static void createAccount(AccountId id, String category) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment("SQLSERVER_INITIALIZER_TEST").openConnection();
                var statement = connection.prepareStatement("INSERT dbo.accounts (account_id,category,currency) VALUES (CAST(? AS uniqueidentifier),?,N'IRR')")) {
            statement.setString(1, id.value().toString());
            statement.setString(2, category);
            statement.executeUpdate();
        }
    }

    private HttpResponse<byte[]> post(CreditCommand command) throws Exception {
        var url = "http://127.0.0.1:" + environment.getProperty("local.server.port") + "/api/v1/accounts/" + command.accountId().value() + "/credits";
        var body = "{\"transactionId\":\"" + command.transactionId().value() + "\",\"amountRial\":" + command.amount().amount()
                + ",\"currency\":\"" + command.amount().currency().getCurrencyCode() + "\"}";
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
        }
    }

    private long balance(AccountId id) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT balance FROM dbo.accounts WHERE account_id=CAST(? AS uniqueidentifier)")) {
            statement.setQueryTimeout(2);
            statement.setString(1, id.value().toString());
            try (var result = statement.executeQuery()) { assertThat(result.next()).isTrue(); return result.getLong(1); }
        }
    }

    private byte[] retainedBody(CreditCommand command) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT response_body FROM dbo.operations WHERE transaction_id=?")) {
            statement.setString(1, command.transactionId().value());
            try (var result = statement.executeQuery()) { assertThat(result.next()).isTrue(); return result.getBytes(1); }
        }
    }

    private void assertCounts(CreditCommand command, int operations, int journals, int postings) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT (SELECT COUNT(*) FROM dbo.operations WHERE transaction_id=?),"
                        + "(SELECT COUNT(*) FROM dbo.journals j JOIN dbo.operations o ON j.operation_id=o.operation_id WHERE o.transaction_id=?),"
                        + "(SELECT COUNT(*) FROM dbo.postings p JOIN dbo.journals j ON p.journal_id=j.journal_id "
                        + "JOIN dbo.operations o ON o.operation_id=j.operation_id WHERE o.transaction_id=?)")) {
            for (int i = 1; i <= 3; i++) { statement.setString(i, command.transactionId().value()); }
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(operations);
                assertThat(result.getInt(2)).isEqualTo(journals);
                assertThat(result.getInt(3)).isEqualTo(postings);
            }
        }
    }

    private void assertReconciles(AccountId customer) throws Exception {
        assertReconciles(customer, false);
    }

    private void assertReconciles(AccountId customer, boolean debitNormal) throws Exception {
        var positiveSide = debitNormal ? "DEBIT" : "CREDIT";
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT SUM(CASE WHEN side='" + positiveSide + "' THEN CAST(amount AS decimal(38,0)) "
                        + "ELSE -CAST(amount AS decimal(38,0)) END) FROM dbo.postings WHERE account_id=CAST(? AS uniqueidentifier)")) {
            statement.setString(1, customer.value().toString());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo(Long.toString(balance(customer)));
            }
        }
    }

    private ConfigurableApplicationContext independentApplication() {
        return new SpringApplicationBuilder(ConcurrentBalanceServiceApplication.class).web(WebApplicationType.NONE)
                .logStartupInfo(false).initializers(context -> context.getEnvironment().getPropertySources().addFirst(
                        new MapPropertySource("explicit-credit-test", Map.of(
                                "spring.datasource.url", System.getenv("SQLSERVER_TEST_URL"),
                                "spring.datasource.username", System.getenv("SQLSERVER_TEST_USERNAME"),
                                "spring.datasource.password", System.getenv("SQLSERVER_TEST_PASSWORD"),
                                "balance.execution.enabled", "true",
                                "balance.execution.funding[IRR].account-id", FUNDING.value().toString(),
                                "balance.execution.funding[IRR].policy-version", "credit-it-funding-v1"))))
                .run();
    }

    private void assertBalanced(CreditCommand command) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection();
                var statement = connection.prepareStatement("SELECT SUM(CASE WHEN p.side='DEBIT' THEN CAST(p.amount AS decimal(38,0)) ELSE 0 END),"
                        + "SUM(CASE WHEN p.side='CREDIT' THEN CAST(p.amount AS decimal(38,0)) ELSE 0 END) "
                        + "FROM dbo.postings p JOIN dbo.journals j ON p.journal_id=j.journal_id JOIN dbo.operations o ON o.operation_id=j.operation_id "
                        + "WHERE o.transaction_id=?")) {
            statement.setString(1, command.transactionId().value());
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBigDecimal(1)).isEqualByComparingTo(Long.toString(command.amount().amount()));
                assertThat(result.getBigDecimal(2)).isEqualByComparingTo(result.getBigDecimal(1));
            }
        }
    }
}
