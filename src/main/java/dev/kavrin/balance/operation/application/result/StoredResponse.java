package dev.kavrin.balance.operation.application.result;

import dev.kavrin.balance.operation.application.credit.CreditResponseEncoder;
import java.util.Arrays;
import java.util.Objects;

/**
 * Retains the original HTTP response envelope and serialized body for deterministic replay
 * without applying a new serializer or policy. Body bytes are copied on construction and access.
 * Envelope validation does not parse JSON or verify its agreement with an operation outcome;
 * that is the responsibility of {@link CreditResponseEncoder#validate(MutationResult)}.
 *
 * @param version response format version; currently only version 1 is accepted
 * @param status HTTP 200 for success or a status from 400 through 499 for rejection
 * @param contentType {@code application/json} for success or {@code application/problem+json}
 *                    for rejection
 * @param body nonempty serialized response body, encoded as UTF-8 by the JSON adapter
 */
public record StoredResponse(int version, int status, String contentType, byte[] body) {
    /**
     * Copies the body and checks the supported format, status and content-type combinations.
     *
     * @throws NullPointerException if the content type or body is null
     * @throws IllegalArgumentException if the envelope is unsupported or the body is empty
     */
    public StoredResponse {
        Objects.requireNonNull(contentType, "Response content type is required");
        body = body.clone();
        if (version != 1 || body.length == 0
                || !(status == 200 && contentType.equals("application/json")
                || status >= 400 && status <= 499 && contentType.equals("application/problem+json"))) {
            throw new IllegalArgumentException("Unsupported retained response envelope");
        }
    }

    /**
     * Returns a defensive copy so callers cannot modify the retained bytes.
     *
     * @return a new array containing the response body
     */
    @Override
    public byte[] body() { return body.clone(); }

    /**
     * Compares envelope fields and body contents rather than byte-array identity.
     *
     * @param other object to compare
     * @return whether both responses have equal envelope fields and body bytes
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof StoredResponse response && version == response.version && status == response.status
                && contentType.equals(response.contentType) && Arrays.equals(body, response.body);
    }

    /**
     * Computes a hash from the envelope fields and body contents, consistent with {@link #equals(Object)}.
     *
     * @return the content-based response hash
     */
    @Override
    public int hashCode() {
        return 31 * Objects.hash(version, status, contentType) + Arrays.hashCode(body);
    }
}
