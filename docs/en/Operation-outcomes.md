# Business outcomes and the commit boundary

[فارسی](../fa/Operation-outcomes.md) · [Transaction protocol](Transaction-protocol.md) · [HTTP contract](API-v1.md)

Follow the [operation walkthrough](Operation-flow.md) for a complete request-to-response example and a source map.

`OperationOutcome` is a framework-free sealed interface with two immutable record variants. It represents a terminal business decision. Constructing it does not execute an operation or prove database commit.

| Variant | Fields | Meaning |
| --- | --- | --- |
| `OperationOutcome.Success` | TransactionId, OperationType | The attempt selected success; it may be exposed only after confirmed commit |
| `OperationOutcome.Rejected` | TransactionId, OperationType, RejectionReason | A terminal business rejection to commit without monetary writes |

OperationType currently contains CREDIT, DEBIT, TRANSFER and internal OPENING. OPENING supports the accepted journal-backed initialization design; the enum does not implement an opening handler or add a public endpoint. Required fields reject null. Success has no rejection reason; Rejected cannot be constructed without one. Sealing the interface permits exhaustive Java 21 switch handling and prevents arbitrary outcome variants.

## Example: insufficient funds

A structurally valid debit requests 100 rials when only 50 are available. After resolving identity and checking funds under the future locking protocol, evaluation can return:

```java
OperationOutcome outcome = new OperationOutcome.Rejected(
        new TransactionId("debit-001"),
        OperationType.DEBIT,
        RejectionReason.INSUFFICIENT_FUNDS
);
```

The accepted reason values are ACCOUNT_NOT_FOUND, INSUFFICIENT_FUNDS and BALANCE_OVERFLOW. ACCOUNT_NOT_FOUND concerns a requested account; missing internal funding configuration is a technical/configuration defect. BALANCE_OVERFLOW concerns a resulting projection; an out-of-long JSON request is invalid input instead. Further business reason policies require review rather than catching all domain exceptions as rejections.

This object stores requested identity, not a reference to an Account entity. A rejection for a missing account must be persistable later without a foreign key to that absent account. Retrying the same ID and command must return the original rejection even if the account or funds change. Durable storage and replay are not implemented by these types.

## Example: success acknowledgment

```java
OperationOutcome outcome = new OperationOutcome.Success(
        new TransactionId("transfer-001"),
        OperationType.TRANSFER
);
```

This is minimal internal business metadata. It carries no balance map, currency, journal ID, HTTP status, serialized response or financial posting plan. Those values must not be invented by the constructor. CBS23 still needs the transport DTO/error contract, and the durable execution path must retain the original serialized response and canonical command metadata, including currency. If responses later include balances, preserve the original values for replay instead of querying current balances. OperationOutcome alone is not the complete stored HTTP replay envelope.

## How this fits TransactionTemplate

The following sequence is the accepted integration plan, not implemented coordinator code:

1. Parse/validate the command and resolve existing durable identity. A conflicting command gets a conflict, not a replacement terminal outcome. Matching completed requests replay the original response.
2. For a new operation, apply admission policy, begin the guarded financial transaction and reserve identity.
3. Lock/resolve participants and evaluate the business rules.
4. On rejection, persist the terminal operation/response only and return Rejected as a normal callback value. A normal return allows that rejection to commit.
5. On success, persist the journal, postings, projections and operation/response together, then return Success as a callback value.
6. Expose/map the result only after the template has completed successfully with no ambient transaction. A callback value alone is not commit evidence.

A technical exception must leave the callback so the attempt rolls back. Expected business rejection is not thrown merely to signal an HTTP error, because that could roll back the rejection record we intend to retain. There is no terminal TechnicalFailure, Timeout or Deadlock rejection variant. Transient rollback may lead to bounded whole-attempt retry; uncertain commit requires same-ID recovery. None of these outcomes authorizes retrying a committed rejection as a new business evaluation.

Validation failure, unsupported-current-policy admission, authorization failure and transaction-ID conflict also remain outside the stored terminal business variants. Their HTTP mapping and transaction timing follow their reviewed boundaries; this enum does not turn them into committed operation records.

## Current implementation and verification

[OperationOutcome.java](../../src/main/java/dev/kavrin/balance/operation/domain/OperationOutcome.java), [OperationType.java](../../src/main/java/dev/kavrin/balance/operation/domain/OperationType.java) and [RejectionReason.java](../../src/main/java/dev/kavrin/balance/operation/domain/RejectionReason.java) are implemented. No evaluator, TransactionTemplate coordinator, exception classifier, repository serialization or HTTP adapter is wired.

Java 21.0.11 Maven verify passed 150 tests with zero failures/errors/skips in 7.696 seconds, including 13 [outcome cases](../../src/test/java/dev/kavrin/balance/operation/domain/OperationOutcomeTests.java). These cover value shape, required metadata, reason distinctions and exhaustive handling. They do not demonstrate commit, rollback, reservation or replay. CBS05 acceptance and independent mastery remain open. Next implementation slice is CBS23 HTTP DTO/error contract foundation, with real financial execution deferred to the SQL Server slices.

## CBS23 boundary update, 2026-10-23

The HTTP adapter and MutationResult outcome/currency envelope now exist. See [API contract](API-v1.md#cbs23-http-foundation-2026-10-23). The outcome examples above remain value construction only. Durable response storage, transaction coordination, SQL failure classification and real replay remain unimplemented. The 150-test result above is historical CBS05 evidence; current adapter evidence is recorded [separately](HTTP-adapter-evidence.md).
