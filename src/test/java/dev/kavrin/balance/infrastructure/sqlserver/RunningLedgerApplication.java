package dev.kavrin.balance.infrastructure.sqlserver;

import dev.kavrin.balance.ConcurrentBalanceServiceApplication;
import dev.kavrin.balance.account.domain.AccountId;
import java.net.URI;
import java.util.Map;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * Owns one real HTTP application context and its independently created persistence resources.
 *
 * <p>The server binds to an ephemeral loopback port and uses only explicit, validated test database
 * credentials. Each context receives the same funding policy and its own test reservation spies.
 * An optional passive account-lock probe supports progress/order tests without changing SQL work.
 * Closing the fixture stops its HTTP server and connection pool. Multiple contexts share a JVM
 * and SQL Server database, so this is not evidence of separate operating-system processes or hosts.
 */
final class RunningLedgerApplication implements AutoCloseable {
    private final ConfigurableApplicationContext context;

    private RunningLedgerApplication(ConfigurableApplicationContext context) { this.context = context; }

    static RunningLedgerApplication start(String name, AccountId funding, ReservationRaceGate gate) throws Exception {
        return start(name, funding, gate, null);
    }

    static RunningLedgerApplication start(String name, AccountId funding, ReservationRaceGate gate, AccountLockProbe probe) throws Exception {
        try (var connection = SqlServerTestDatabase.fromEnvironment().openConnection()) {
            if (!connection.isValid(5)) { throw new IllegalStateException("Test database is unavailable"); }
        }
        var context = new SpringApplicationBuilder(ConcurrentBalanceServiceApplication.class)
                .web(WebApplicationType.SERVLET).logStartupInfo(false)
                .initializers(application -> {
                    application.getEnvironment().getPropertySources().addFirst(new MapPropertySource("explicit-race-test", Map.ofEntries(
                            Map.entry("spring.application.name", name),
                            Map.entry("server.address", "127.0.0.1"), Map.entry("server.port", "0"),
                            Map.entry("spring.datasource.url", System.getenv("SQLSERVER_TEST_URL")),
                            Map.entry("spring.datasource.username", System.getenv("SQLSERVER_TEST_USERNAME")),
                            Map.entry("spring.datasource.password", System.getenv("SQLSERVER_TEST_PASSWORD")),
                            Map.entry("spring.datasource.hikari.maximum-pool-size", "2"),
                            Map.entry("balance.execution.enabled", "true"),
                            Map.entry("balance.execution.funding[IRR].account-id", funding.value().toString()),
                            Map.entry("balance.execution.funding[IRR].policy-version", "cbs17-funding-v1"))));
                    application.addBeanFactoryPostProcessor(factory -> {
                        factory.addBeanPostProcessor(new ReservationGatePostProcessor(gate));
                        if (probe != null) { factory.addBeanPostProcessor(new AccountLockProbePostProcessor(probe)); }
                    });
                }).run();
        return new RunningLedgerApplication(context);
    }

    <T> T bean(Class<T> type) { return context.getBean(type); }

    URI uri(String path) {
        return URI.create("http://127.0.0.1:" + context.getEnvironment().getRequiredProperty("local.server.port") + path);
    }

    @Override
    public void close() { context.close(); }
}
