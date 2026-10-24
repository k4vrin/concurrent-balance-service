package dev.kavrin.balance.operation.application;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class IdentityText {
    private IdentityText() { }

    static void requireValid(String text) {
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
