package dev.kavrin.balance.operation.web;

public sealed interface OperationResponseBody permits OperationResponse, OperationProblem, StoredOperationResponse {
}
