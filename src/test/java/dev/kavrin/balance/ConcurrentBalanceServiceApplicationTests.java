package dev.kavrin.balance;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("smoke")
class ConcurrentBalanceServiceApplicationTests {

    @LocalServerPort
    private int port;

    @Test
    void unwiredFinancialRoutesNeverReportSuccess() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var root = "http://127.0.0.1:" + port + "/api/v1";
            var account = "/accounts/00000000-0000-0000-0000-000000000001";
            var body = "{\"transactionId\":\"local-001\",\"amountRial\":100,\"currency\":\"IRR\"}";
            for (var route : java.util.List.of(account + "/credits", account + "/debits", "/transfers")) {
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
