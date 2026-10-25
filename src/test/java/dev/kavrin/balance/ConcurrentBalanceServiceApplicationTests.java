package dev.kavrin.balance;

import dev.kavrin.balance.account.application.GetBalanceHandler;
import dev.kavrin.balance.account.infrastructure.persistence.AccountStore;
import dev.kavrin.balance.operation.application.CreditHandler;
import dev.kavrin.balance.operation.application.OperationCoordinator;
import jakarta.persistence.EntityManagerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = HttpSmokeConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "balance.execution.enabled=true")
@ActiveProfiles("smoke")
class ConcurrentBalanceServiceApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Test
    void smokeAssemblyDoesNotLoadFinancialOrPersistenceComponents() {
        for (var type : List.of(AccountStore.class, GetBalanceHandler.class, CreditHandler.class,
                OperationCoordinator.class, PlatformTransactionManager.class, EntityManagerFactory.class)) {
            assertThat(context.getBeanNamesForType(type)).as(type.getSimpleName()).isEmpty();
        }
    }

    @Test
    void unwiredFinancialRoutesNeverReportSuccess() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var root = "http://127.0.0.1:" + port + "/api/v1";
            var account = "/accounts/00000000-0000-0000-0000-000000000001";
            var body = "{\"transactionId\":\"local-001\",\"amountRial\":100,\"currency\":\"IRR\"}";
            for (var route : List.of(account + "/credits", account + "/debits", "/transfers")) {
                var payload = route.equals("/transfers") ? body.replace("}",
                        ",\"sourceAccountId\":\"00000000-0000-0000-0000-000000000001\","
                        + "\"destinationAccountId\":\"00000000-0000-0000-0000-000000000002\"}") : body;
                var request = HttpRequest.newBuilder(URI.create(root + route))
                        .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(503);
                assertThat(response.body()).contains("OPERATION_UNAVAILABLE").doesNotContain("SUCCEEDED");
            }
            var response = client.send(HttpRequest.newBuilder(URI.create(root + account + "/balance"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(response.body()).contains("OPERATION_UNAVAILABLE").doesNotContain("balanceRial");
        }
    }

    @Test
    void bootstrapExposesHealthWithoutExternalInfrastructure() throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"");
        }
    }
}
