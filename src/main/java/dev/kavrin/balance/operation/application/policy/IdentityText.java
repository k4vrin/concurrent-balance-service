package dev.kavrin.balance.operation.application.policy;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Validates nonblank, well-formed Unicode text used in policy and actor identities.
 *
 * <p>Malformed UTF-16 is rejected before UTF-8 encoding could replace characters and collapse distinct
 * identities. Valid text is preserved without trimming, normalization or an ASCII-only restriction.
 */
public final class IdentityText {
    private IdentityText() { }

    public static void requireValid(String text) {
        Objects.requireNonNull(text, "Identity text is required");
        if (text.isBlank()) {
            throw new IllegalArgumentException("Identity text must not be blank");
        }
        // Reject malformed UTF-16 so encoding cannot silently replace distinct identity text.
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(text)) {
            throw new IllegalArgumentException("Identity text must be valid Unicode");
        }
    }
}
