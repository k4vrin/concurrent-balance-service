package dev.kavrin.balance.operation.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Preserves the caller's exact opaque retry identity within the global operation namespace.
 *
 * <p>The value permits 1 to 128 ASCII letters, digits and the documented punctuation. Invalid input
 * is rejected without trimming or case folding so separate textual identities cannot be conflated.
 *
 * @param value exact, case-sensitive transaction identifier
 */
public record TransactionId(String value) {

    private static final Pattern VALID_FORMAT = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    public TransactionId {
        Objects.requireNonNull(value, "Transaction ID is required");
        // Preserve exact retry identity: reject invalid input instead of trimming or folding case.
        if (!VALID_FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "Transaction ID must contain 1–128 ASCII letters, digits, '.', '_', ':' or '-'");
        }
    }
}
