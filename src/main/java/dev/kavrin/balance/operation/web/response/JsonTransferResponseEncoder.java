package dev.kavrin.balance.operation.web.response;

import dev.kavrin.balance.operation.application.command.TransferCommand;
import dev.kavrin.balance.operation.application.result.MutationResult;
import dev.kavrin.balance.operation.application.result.StoredResponse;
import dev.kavrin.balance.operation.application.transfer.TransferResponseEncoder;
import dev.kavrin.balance.operation.domain.OperationRejected;
import dev.kavrin.balance.operation.domain.OperationSuccess;
import java.net.URI;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class JsonTransferResponseEncoder implements TransferResponseEncoder {
    private final JsonMapper json;

    public JsonTransferResponseEncoder(JsonMapper json) { this.json = json; }

    @Override
    public StoredResponse encode(TransferCommand command, MutationResult result) {
        var response = OperationResponses.from(result);
        if (response.getBody() instanceof OperationProblem problem) {
            problem.setInstance(URI.create("/api/v1/transfers"));
        }
        return new StoredResponse(1, response.getStatusCode().value(),
                result.outcome() instanceof OperationSuccess ? "application/json" : "application/problem+json",
                json.writeValueAsBytes(response.getBody()));
    }

    @Override
    public void validate(MutationResult result) {
        var response = result.storedResponse().orElseThrow();
        Map<?, ?> body = json.readValue(response.body(), Map.class);
        boolean success = result.outcome() instanceof OperationSuccess;
        if (!result.outcome().transactionId().value().equals(body.get("transactionId"))
                || !result.outcome().operationType().name().equals(body.get("operationType"))
                || !result.currency().getCurrencyCode().equals(body.get("currency"))
                || success && (response.status() != 200 || !"SUCCEEDED".equals(body.get("status")))
                || !success && (response.status() < 400
                || !((OperationRejected) result.outcome()).reason().name().equals(body.get("code"))
                || !(body.get("status") instanceof Number status) || status.intValue() != response.status())) {
            throw new IllegalStateException("Stored response does not match the retained outcome");
        }
    }
}
