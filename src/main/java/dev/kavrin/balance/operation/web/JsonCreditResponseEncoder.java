package dev.kavrin.balance.operation.web;

import dev.kavrin.balance.operation.application.CreditCommand;
import dev.kavrin.balance.operation.application.CreditResponseEncoder;
import dev.kavrin.balance.operation.application.MutationResult;
import dev.kavrin.balance.operation.application.StoredResponse;
import dev.kavrin.balance.operation.domain.OperationOutcome;
import java.net.URI;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class JsonCreditResponseEncoder implements CreditResponseEncoder {
    private final JsonMapper json;

    public JsonCreditResponseEncoder(JsonMapper json) { this.json = json; }

    @Override
    public StoredResponse encode(CreditCommand command, MutationResult result) {
        var response = OperationResponses.from(result);
        if (response.getBody() instanceof OperationProblem problem) {
            problem.setInstance(URI.create("/api/v1/accounts/" + command.accountId().value() + "/credits"));
        }
        return new StoredResponse(1, response.getStatusCode().value(),
                result.outcome() instanceof OperationOutcome.Success ? "application/json" : "application/problem+json",
                json.writeValueAsBytes(response.getBody()));
    }

    @Override
    public void validate(MutationResult result) {
        var response = result.storedResponse().orElseThrow();
        Map<?, ?> body = json.readValue(response.body(), Map.class);
        boolean success = result.outcome() instanceof OperationOutcome.Success;
        if (!result.outcome().transactionId().value().equals(body.get("transactionId"))
                || !result.outcome().operationType().name().equals(body.get("operationType"))
                || !result.currency().getCurrencyCode().equals(body.get("currency"))
                || success && (response.status() != 200 || !"SUCCEEDED".equals(body.get("status")))
                || !success && (response.status() < 400
                || !((OperationOutcome.Rejected) result.outcome()).reason().name().equals(body.get("code"))
                || !(body.get("status") instanceof Number status) || status.intValue() != response.status())) {
            throw new IllegalStateException("Stored response does not match the retained outcome");
        }
    }
}
