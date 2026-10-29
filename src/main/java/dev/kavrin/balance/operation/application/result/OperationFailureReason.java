package dev.kavrin.balance.operation.application.result;

/**
 * Classifies application failures that are separate from retained mutation business outcomes.
 *
 * <p>Lookup absence, conflicting identity, unsupported new currency and temporary unavailability
 * map to different HTTP errors. These values do not authorize a journal or a committed rejection.
 */
public enum OperationFailureReason { ACCOUNT_NOT_FOUND, TRANSACTION_ID_CONFLICT, UNSUPPORTED_CURRENCY, UNAVAILABLE }
