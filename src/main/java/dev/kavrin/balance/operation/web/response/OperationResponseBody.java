package dev.kavrin.balance.operation.web.response;

public sealed interface OperationResponseBody permits OperationResponse, OperationProblem, StoredOperationResponse {
}
