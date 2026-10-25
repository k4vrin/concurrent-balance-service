package dev.kavrin.balance.operation.web.request;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.domain.TransactionId;
import dev.kavrin.balance.operation.web.error.ApiExceptionHandler;
import java.util.Currency;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Converts HTTP request fields into domain values before controllers invoke application operations.
 * Centralizes transport-format checks and translates invalid constructor arguments into
 * {@link InvalidRequest}, which {@link ApiExceptionHandler} maps to HTTP 400 with code
 * {@code INVALID_REQUEST}. Business rules remain in commands and application/domain logic.
 */
public final class RequestValues {
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /** Prevents instantiation of this static conversion utility. */
    private RequestValues() { }

    /**
     * Parses an account ID in the full hyphenated UUID form (8-4-4-4-12 hexadecimal digits).
     * Accepts upper- or lowercase hexadecimal digits, without trimming whitespace.
     *
     * @param text account ID supplied in a path or request body
     * @return the parsed account identity; account existence is not checked here
     * @throws InvalidRequest if the text is null or does not match the required UUID form
     */
    public static AccountId account(String text) {
        // UUID.fromString also accepts shortened groups; the transport requires the full form.
        if (text == null || !UUID_TEXT.matcher(text).matches()) {
            throw new InvalidRequest();
        }
        return new AccountId(UUID.fromString(text));
    }

    /**
     * Creates a transaction ID using {@link TransactionId}'s format rules, preserving the
     * exact text and case so retries retain their original identity.
     *
     * @param text caller-supplied transaction ID
     * @return the validated transaction identity
     * @throws InvalidRequest if the text is null or violates the transaction ID format
     */
    public static TransactionId transaction(String text) {
        return parse(() -> new TransactionId(text));
    }

    /**
     * Combines an unscaled amount with a currency code recognized by {@link Currency#getInstance(String)}.
     * This conversion does not enforce positivity,
     * operation limits or the application's supported-currency policy.
     *
     * @param amount amount already deserialized as a {@code long}
     * @param currency currency code, such as {@code IRR}
     * @return money containing the supplied amount and parsed currency
     * @throws InvalidRequest if the currency is null or its code is not recognized
     */
    public static Money money(long amount, String currency) {
        return parse(() -> new Money(amount, Currency.getInstance(currency)));
    }

    /**
     * Runs a value or command constructor at the HTTP boundary, translating
     * {@link IllegalArgumentException} and {@link NullPointerException} into a uniform
     * invalid-request error. Other exceptions propagate unchanged.
     *
     * @param constructor supplier that constructs and validates a value or command
     * @param <T> type produced by the supplier
     * @return the supplier's result, unchanged
     * @throws InvalidRequest if the supplier is null or throws either translated exception
     */
    public static <T> T parse(Supplier<T> constructor) {
        try {
            return constructor.get();
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new InvalidRequest();
        }
    }
}
