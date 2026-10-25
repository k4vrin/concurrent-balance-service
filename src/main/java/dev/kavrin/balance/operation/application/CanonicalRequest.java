package dev.kavrin.balance.operation.application;

import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.domain.OperationType;

/**
 * Builds a deterministic, versioned representation of caller intent for replay/conflict checks.
 * The transaction ID is the lookup key, so it is deliberately excluded from the representation.
 * Operation type, applicable account IDs, amount, currency and optional actor identity define
 * whether requests with that key express the same intent.
 *
 * <p>Server-selected funding is excluded from caller intent so policy changes do not invalidate
 * matching retries. {@link #fundedFingerprint(FundingSelection)} separately binds the original
 * intent to the selected funding account and funding-policy version for execution metadata.
 * Canonical bytes, rather than digest equality alone, are the authority for request comparison.
 * This class neither looks up retained operations nor posts money.
 */
public final class CanonicalRequest {
    private final CanonicalFingerprint fingerprint;
    private final AccountId targetAccountId;

    /**
     * Encodes fields in a fixed order, preserving absent account/actor fields as null markers.
     * Public factories select the account layout appropriate to each operation type.
     *
     * @param type operation whose intent is represented
     * @param target account for credit, debit or opening; null for transfer
     * @param source transfer source; null for other operations
     * @param destination transfer destination; null for other operations
     * @param amount exact amount and currency
     * @param actorId explicit actor binding, or empty for an unbound request
     * @throws NullPointerException if the actor optional is null
     * @throws IllegalArgumentException if a present actor is blank or malformed Unicode
     */
    private CanonicalRequest(OperationType type, AccountId target, AccountId source,
            AccountId destination, Money amount, Optional<String> actorId) {
        Objects.requireNonNull(actorId, "Actor binding must be explicit");
        actorId.ifPresent(IdentityText::requireValid);
        this.targetAccountId = target;
        this.fingerprint = CanonicalFingerprint.encode("request", type.name(), uuid(target),
                uuid(source), uuid(destination), Long.toString(amount.amount()),
                amount.currency().getCurrencyCode(), actorId.orElse(null));
    }

    /**
     * Represents credit intent independently of its server-selected funding counterpart.
     *
     * @param command validated customer account, amount and currency
     * @param actorId actor binding, or empty when no actor is bound
     * @return canonical credit intent, excluding the command's transaction ID
     * @throws NullPointerException if the command or actor optional is null
     * @throws IllegalArgumentException if a present actor is blank or malformed Unicode
     */
    public static CanonicalRequest credit(CreditCommand command, Optional<String> actorId) {
        Objects.requireNonNull(command, "Credit command is required");
        return new CanonicalRequest(OperationType.CREDIT, command.accountId(), null, null, command.amount(), actorId);
    }

    /**
     * Represents debit intent independently of its server-selected funding counterpart.
     *
     * @param command validated customer account, amount and currency
     * @param actorId actor binding, or empty when no actor is bound
     * @return canonical debit intent, excluding the command's transaction ID
     * @throws NullPointerException if the command or actor optional is null
     * @throws IllegalArgumentException if a present actor is blank or malformed Unicode
     */
    public static CanonicalRequest debit(DebitCommand command, Optional<String> actorId) {
        Objects.requireNonNull(command, "Debit command is required");
        return new CanonicalRequest(OperationType.DEBIT, command.accountId(), null, null, command.amount(), actorId);
    }

    /**
     * Represents transfer intent with source and destination in their distinct roles;
     * reversing direction therefore changes the canonical request.
     *
     * @param command validated source, destination, amount and currency
     * @param actorId actor binding, or empty when no actor is bound
     * @return canonical transfer intent, excluding the command's transaction ID
     * @throws NullPointerException if the command or actor optional is null
     * @throws IllegalArgumentException if a present actor is blank or malformed Unicode
     */
    public static CanonicalRequest transfer(TransferCommand command, Optional<String> actorId) {
        Objects.requireNonNull(command, "Transfer command is required");
        return new CanonicalRequest(OperationType.TRANSFER, null, command.sourceAccountId(),
                command.destinationAccountId(), command.amount(), actorId);
    }

    /**
     * Represents a positive internal opening amount without initializing an account or creating
     * a journal. The operation-type field distinguishes opening intent from a public credit.
     *
     * @param accountId account receiving the opening balance
     * @param amount positive opening amount and currency
     * @param actorId actor binding, or empty when no actor is bound
     * @return canonical opening intent
     * @throws NullPointerException if the account, amount or actor optional is null
     * @throws IllegalArgumentException if the amount is not positive or a present actor is
     *         blank or malformed Unicode
     */
    public static CanonicalRequest opening(AccountId accountId, Money amount, Optional<String> actorId) {
        Objects.requireNonNull(accountId, "Opening account is required");
        CommandValidation.requirePositiveAmount(amount);
        return new CanonicalRequest(OperationType.OPENING, accountId, null, null, amount, actorId);
    }

    /**
     * Returns the versioned caller-intent representation, including canonical bytes and digest.
     *
     * @return the fingerprint created when this request was constructed
     */
    public CanonicalFingerprint fingerprint() {
        return fingerprint;
    }

    /**
     * Binds the complete canonical intent bytes to a funding account and funding-policy version.
     * This execution fingerprint is separate from the caller fingerprint used for replay checks;
     * it does not include the overall execution-policy version or perform funding selection.
     *
     * @param selection server selection retained for this operation
     * @return versioned fingerprint of the funded execution intent
     * @throws NullPointerException if the selection is null
     * @throws IllegalArgumentException if this is a transfer or the counterpart equals the target
     */
    public CanonicalFingerprint fundedFingerprint(FundingSelection selection) {
        Objects.requireNonNull(selection, "Retained funding selection is required");
        if (targetAccountId == null) {
            throw new IllegalArgumentException("Transfers already identify both effective accounts");
        }
        if (targetAccountId.equals(selection.counterpartAccountId())) {
            throw new IllegalArgumentException("Funding counterpart must differ from the target account");
        }
        // Bind the complete caller intent, not just its hash, to the retained execution policy.
        return CanonicalFingerprint.encode("funded-execution", HexFormat.of().formatHex(fingerprint.bytes()),
                uuid(selection.counterpartAccountId()), selection.policyVersion());
    }

    /**
     * Converts an account ID to its standard UUID text while preserving an absent field.
     *
     * @param id account identity, or null for an inapplicable operation field
     * @return standard UUID text, or null when the ID is absent
     */
    private static String uuid(AccountId id) {
        return id == null ? null : id.value().toString();
    }
}
