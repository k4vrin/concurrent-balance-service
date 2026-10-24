package dev.kavrin.balance.operation.application;

import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

import dev.kavrin.balance.account.domain.AccountId;
import dev.kavrin.balance.money.Money;
import dev.kavrin.balance.operation.domain.OperationType;

/** Caller intent is independent of the transaction-ID lookup key and mutable funding selection. */
public final class CanonicalRequest {
    private final CanonicalFingerprint fingerprint;
    private final AccountId targetAccountId;

    private CanonicalRequest(OperationType type, AccountId target, AccountId source,
            AccountId destination, Money amount, Optional<String> actorId) {
        Objects.requireNonNull(actorId, "Actor binding must be explicit");
        actorId.ifPresent(IdentityText::requireValid);
        this.targetAccountId = target;
        this.fingerprint = CanonicalFingerprint.encode("request", type.name(), uuid(target),
                uuid(source), uuid(destination), Long.toString(amount.amount()),
                amount.currency().getCurrencyCode(), actorId.orElse(null));
    }

    public static CanonicalRequest credit(CreditCommand command, Optional<String> actorId) {
        Objects.requireNonNull(command, "Credit command is required");
        return new CanonicalRequest(OperationType.CREDIT, command.accountId(), null, null, command.amount(), actorId);
    }

    public static CanonicalRequest debit(DebitCommand command, Optional<String> actorId) {
        Objects.requireNonNull(command, "Debit command is required");
        return new CanonicalRequest(OperationType.DEBIT, command.accountId(), null, null, command.amount(), actorId);
    }

    public static CanonicalRequest transfer(TransferCommand command, Optional<String> actorId) {
        Objects.requireNonNull(command, "Transfer command is required");
        return new CanonicalRequest(OperationType.TRANSFER, null, command.sourceAccountId(),
                command.destinationAccountId(), command.amount(), actorId);
    }

    /** Fingerprints positive internal opening intent; it does not initialize an account or create a journal. */
    public static CanonicalRequest opening(AccountId accountId, Money amount, Optional<String> actorId) {
        Objects.requireNonNull(accountId, "Opening account is required");
        CommandValidation.requirePositiveAmount(amount);
        return new CanonicalRequest(OperationType.OPENING, accountId, null, null, amount, actorId);
    }

    public CanonicalFingerprint fingerprint() {
        return fingerprint;
    }

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

    private static String uuid(AccountId id) {
        return id == null ? null : id.value().toString();
    }
}
