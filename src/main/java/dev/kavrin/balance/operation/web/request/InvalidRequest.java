package dev.kavrin.balance.operation.web.request;

/**
 * Signals invalid adapter input before any mutation identity is reserved.
 *
 * <p>The global HTTP advice maps this boundary failure to INVALID_REQUEST without treating it as a
 * retained business rejection or exposing internal conversion details.
 */
public final class InvalidRequest extends RuntimeException {
}
