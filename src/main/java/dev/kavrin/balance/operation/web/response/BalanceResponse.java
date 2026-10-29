package dev.kavrin.balance.operation.web.response;

/**
 * Exposes the authoritative committed balance projection without persistence metadata.
 *
 * @param accountId account UUID rendered for the public API
 * @param balanceRial whole-rial projection amount
 * @param currency projection currency code
 */
public record BalanceResponse(String accountId, long balanceRial, String currency) {
}
