package dev.kavrin.balance.operation.web;

import java.util.Currency;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.operation.domain.TransactionId;
import dev.kavrin.balance.money.Money;

final class RequestValues {
    private static final Pattern UUID_TEXT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private RequestValues() { }

    static AccountId account(String text) {
        // UUID.fromString also accepts shortened groups; the transport requires the full form.
        if (text == null || !UUID_TEXT.matcher(text).matches()) {
            throw new InvalidRequest();
        }
        return new AccountId(UUID.fromString(text));
    }

    static TransactionId transaction(String text) {
        return parse(() -> new TransactionId(text));
    }

    static Money money(long amount, String currency) {
        return parse(() -> new Money(amount, Currency.getInstance(currency)));
    }

    static <T> T parse(Supplier<T> constructor) {
        try {
            return constructor.get();
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new InvalidRequest();
        }
    }
}
