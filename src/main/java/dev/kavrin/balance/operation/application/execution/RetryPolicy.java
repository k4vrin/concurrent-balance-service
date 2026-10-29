package dev.kavrin.balance.operation.application.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Defines validated local-development limits for mutation retries and retained-result recovery.
 *
 * <p>Spring binds {@code balance.retry} once during configuration. Invalid limits fail startup.
 * The elapsed-time budget is cooperative: it controls admission and backoff but cannot cancel
 * transaction cleanup or establish a hard network/commit deadline (ADR-010).
 *
 * @param maxAttempts maximum writing attempts and maximum reads within each recovery phase, from 1 to 10
 * @param budgetMillis total cooperative request budget in milliseconds, from 10000 to 120000
 * @param backoffMillis exponential full-jitter base in milliseconds, from 0 to 1000
 */
@ConfigurationProperties("balance.retry")
public record RetryPolicy(
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("20000") long budgetMillis,
        @DefaultValue("50") long backoffMillis) {
    public RetryPolicy {
        if (maxAttempts < 1 || maxAttempts > 10 || budgetMillis < 10000 || budgetMillis > 120000
                || backoffMillis < 0 || backoffMillis > 1000) {
            throw new IllegalArgumentException("Invalid balance.retry limits");
        }
    }

    public static RetryPolicy defaults() { return new RetryPolicy(3, 20000, 50); }
}
