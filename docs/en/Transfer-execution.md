# Atomic transfer execution and CBS14 evidence

[فارسی](../fa/Transfer-execution.md) · [Debit](Debit-execution.md) · [Transaction ownership](Operation-coordinator.md) · [API](API-v1.md)

## Scope and request

CBS14 adds `TransferHandler`, `TransferAttempt`, `TransferStore`, `JpaTransferStore`, `TransferResponseEncoder` and `JsonTransferResponseEncoder`. `BalanceController` constructor-injects the transfer handler and calls it directly from the existing transfer route. The V1 schema, account-lock adapter and request/response contract are unchanged. Credit, debit and transfer share the explicit `balance.execution.enabled=true` activation and existing validated startup policy. That policy still requires funding configuration for credit/debit, but a transfer neither selects nor locks a funding account and retains no funding metadata.

A source customer has 100 IRR and a destination customer has 20 IRR, backed by committed ledger history:

```http
POST /api/v1/transfers
Content-Type: application/json

{"transactionId":"transfer-example-001","sourceAccountId":"10000000-0000-4000-8000-000000000001","destinationAccountId":"f0000000-0000-4000-8000-000000000002","amountRial":100,"currency":"IRR"}
```

Success leaves source 0 and destination 120. The combined customer balance stays 120. One journal contains a source DEBIT and destination CREDIT of 100 each. The funding projection does not change.

## Transaction and replay boundaries

1. Canonicalize caller intent and resolve retained identity before current currency or cap policy. A matching ID returns the original response bytes; changed amount, source, destination or operation type conflicts.
2. For new intent, reserve a PENDING TRANSFER with both requested UUIDs, canonical bytes/digest and execution-policy version. Funding columns stay NULL. A valid amount above the transaction cap becomes a retained rejection.
3. Lock the complete source/destination set through `AccountStore.lockAll`, which deduplicates and sorts by `AccountLockOrder.DESCENDING` before sequential `UPDLOCK, HOLDLOCK` reads. Neither transfer direction nor SQL GUID ordering changes lock acquisition order.
4. Missing source or destination commits `ACCOUNT_NOT_FOUND` without history or balance writes. Existing participants must be active customer liabilities in the command currency; incompatible stored state is a technical failure and rolls back, as in the existing slices.
5. Decide insufficient funds from the locked source. Exact funds are allowed. Check recipient capacity as `destinationBalance > maxCustomerBalance - amount` before adding. This prevents signed-long overflow and retains `BALANCE_LIMIT_EXCEEDED` even with a recipient at `Long.MAX_VALUE`. The transfer amount is counted once for the transaction cap.
6. Build the balanced journal and calculate both checked projections before monetary writes. Append history, apply both projections and retain the terminal response inside the coordinator's single owned transaction. Success is returned only after transaction completion.

Self-transfer is invalid command syntax: HTTP returns 400 before reservation. Business rejection commits only the retained decision, with no journal/postings. Technical write or lock failure rolls back the reservation and every financial write. A named transaction-ID uniqueness collision exits its failed transaction before fresh retained-result recovery. General transient retries and comprehensive uncertain-commit recovery remain CBS15 and later work; a 503 or lost response requires the caller to keep the same ID.

## Verification, 2026-10-26

Java 21 `./mvnw -o -B -ntp -Psqlserver-it verify` passed **385 portable tests and 154 real SQL Server tests**, with **zero failures, errors or skips**, in **36.105 seconds**. Fresh Surefire/Failsafe XML totals were checked. This adds 34 portable cases and 20 transfer database cases to the CBS13 checkpoint. The transfer-only SQL Server run also passed all 20 cases. Existing credit, debit, account-locking, persistence, schema, constraint and permission suites passed in the full run.

The first focused portable compilation caught an incorrectly renamed account-category enum in the new test fixture; it was fixed before the pass. Review also corrected posting-side literals in the new independent SQL test assertions before their successful execution. Docker Desktop was initially stopped; after startup, SQL Server failed with startup errors 17138/17300/17312/28709 and the first integration attempt failed during fixture setup with a pre-login error. Restarting the same existing container restored health without image, volume, resource, schema or permission changes. That failed attempt proves no transfer behavior. The final passing runs used the existing restricted runtime, migrator and initializer test identities, with secrets kept in the ignored local environment.

Portable tests cover decisions and Spring transaction lifecycle, not database guarantees. The real SQL Server suite includes exact-funds HTTP success, the exact recipient-cap boundary, conservation and independent reconciliation, replay/conflict, both missing-account cases, recipient cap, self-transfer, retained rejections after policy/funds changes, application/pool recreation, lock timeout and injected rollback.

The rollback cases fail after journal/postings, after a source-only SQL projection write, after both projections, and after outcome persistence. They require zero retained operation/journal/posting rows, unchanged source/destination balances and successful same-ID retry. The source-only case uses a test spy inside the owning HTTP transaction, without a production failure hook. The recipient `Long.MAX_VALUE` case injects only the locked domain snapshot and checks a real retained rejection with unchanged actual balances; it does not claim committed near-BIGINT history or SQL arithmetic overflow evidence.

## Remaining gates

All four HTTP operation paths now have implementations, but this alone does not close the core release or independent learner acceptance. Broad duplicate/opposite-direction/mixed-operation concurrency, retry/backoff, uncertain-commit recovery, authenticated ownership and performance evidence retain their later gates. The local SQL Server ARM64 emulation remains unsupported by Microsoft. Opening remains a controlled test fixture. No production-readiness claim is made.
