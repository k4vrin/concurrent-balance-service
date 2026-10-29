package dev.kavrin.balance.operation.web.response;

/**
 * Bounds the typed mutation response family to success, business problems and retained JSON.
 *
 * <p>The sealed family allows HTTP handlers to return explicit response types while preserving
 * Spring problem serialization and the original JSON retained for replay.
 */
public sealed interface OperationResponseBody permits OperationResponse, OperationProblem, StoredOperationResponse {
}
