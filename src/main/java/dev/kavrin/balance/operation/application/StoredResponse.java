package dev.kavrin.balance.operation.application;

import java.util.Objects;
import java.util.Arrays;

/** Original serialized response; bytes are retained so replay does not use a new serializer or policy. */
public record StoredResponse(int version, int status, String contentType, byte[] body) {
    public StoredResponse {
        Objects.requireNonNull(contentType, "Response content type is required");
        body = body.clone();
        if (version != 1 || body.length == 0
                || !(status == 200 && contentType.equals("application/json")
                || status >= 400 && status <= 499 && contentType.equals("application/problem+json"))) {
            throw new IllegalArgumentException("Unsupported retained response envelope");
        }
    }

    @Override
    public byte[] body() { return body.clone(); }

    @Override
    public boolean equals(Object other) {
        return other instanceof StoredResponse response && version == response.version && status == response.status
                && contentType.equals(response.contentType) && Arrays.equals(body, response.body);
    }

    @Override
    public int hashCode() {
        return 31 * Objects.hash(version, status, contentType) + Arrays.hashCode(body);
    }
}
