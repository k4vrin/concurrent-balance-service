package dev.kavrin.balance.operation.web.response;

import com.fasterxml.jackson.annotation.JsonValue;
import tools.jackson.databind.util.RawValue;

/** RawValue preserves the server-generated JSON bytes retained with the committed outcome. */
public record StoredOperationResponse(@JsonValue RawValue body) implements OperationResponseBody {
}
