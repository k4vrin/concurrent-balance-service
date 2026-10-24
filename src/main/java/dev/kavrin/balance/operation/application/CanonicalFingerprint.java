package dev.kavrin.balance.operation.application;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** Versioned canonical bytes are the comparison authority; the digest is only an index/check aid. */
public final class CanonicalFingerprint {
    private static final int VERSION = 1;
    private final byte[] bytes;
    private final String sha256;

    private CanonicalFingerprint(byte[] bytes) {
        this.bytes = bytes;
        this.sha256 = digest(bytes);
    }

    static CanonicalFingerprint encode(String... fields) {
        try (var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes)) {
            output.writeInt(VERSION);
            output.writeInt(fields.length);
            for (var field : fields) {
                writeField(output, field);
            }
            return new CanonicalFingerprint(bytes.toByteArray());
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot encode in-memory canonical fields", failure);
        }
    }

    private static void writeField(DataOutputStream output, String field) throws IOException {
        if (field == null) {
            output.writeInt(-1);
        } else {
            var encoded = utf8(field);
            output.writeInt(encoded.length);
            output.write(encoded);
        }
    }

    private static byte[] utf8(String text) {
        try {
            // Replacement of malformed UTF-16 could collapse two distinct identities to the same bytes.
            var encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(text));
            var result = new byte[encoded.remaining()];
            encoded.get(result);
            return result;
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException("Identity text must be valid Unicode", invalid);
        }
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    public int version() {
        return VERSION;
    }

    public byte[] bytes() {
        return bytes.clone();
    }

    public String sha256() {
        return sha256;
    }

    @Override
    public boolean equals(Object other) {
        // Hash equality alone must never authorize replay of a financial operation.
        return other instanceof CanonicalFingerprint fingerprint && Arrays.equals(bytes, fingerprint.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }
}
