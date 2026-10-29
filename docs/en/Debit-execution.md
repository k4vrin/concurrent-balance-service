> CBS15 update, 2026-10-29: mutations now use [bounded whole-transaction retry and conservative uncertain-outcome recovery](Retry-execution.md). Earlier statements below that retry is unimplemented describe the previous checkpoint. `execute` remains a single-transaction primitive; mutation handlers use `executeRecovering`.

# Durable debit execution and CBS13 evidence

[فارسی](../fa/Debit-execution.md) · [Credit execution](Credit-execution.md) · [Transaction ownership](Operation-coordinator.md) · [API](API-v1.md) · [Hexagonal architecture](Hexagonal-architecture.md)

## Scope and activation

CBS11–12 credit and CBS13 debit are implemented and regression-tested. CBS14 atomic transfer is now implemented in a separate slice; see [transfer execution](Transfer-execution.md). Formal learner acceptance remains separate from this implementation evidence.

Both funded operations require `balance.execution.enabled=true` and a valid execution policy with a funding counterpart for each supported currency. They use the same restricted runtime database identity and unchanged V1 schema. See [policy configuration](Execution-policy.md) and [SQL Server setup](Schema.md). Default disabled execution and test-only smoke assembly still return 503 for mutations.

## Concrete request and accounting flow

An active customer C has 100 IRR and the configured active debit-normal funding account F has 100 IRR, backed by prior balanced history. The request is:

```http
POST /api/v1/accounts/10000000-0000-4000-8000-000000000001/debits
Content-Type: application/json

{"transactionId":"debit-example-001","amountRial":100,"currency":"IRR"}
```

The handler first checks canonical intent against retained operations before evaluating current policy. For new intent it reserves a PENDING operation with `operation_type=DEBIT`, `source_account_id=C`, retained policy/funding metadata and canonical bytes. A valid over-cap request completes as a retained rejection before participant locking.

For an admitted amount, it locks the complete customer/funding participant set in `AccountLockOrder.DESCENDING`, refreshes managed accounts, validates category/currency/status, and checks funds from the locked customer snapshot. Exact funds are permitted. A debit of 101 against 100 completes as `INSUFFICIENT_FUNDS`, with no journal or projection update. Missing requested customer is a retained `ACCOUNT_NOT_FOUND`; missing or incompatible internal funding is a technical defect and rolls back.

Success creates a customer DEBIT of 100 and funding CREDIT of 100. C and F both become zero because customer liability is credit-normal and funding is debit-normal. All proposed balances are checked before history/projection writes. The operation, one journal, two postings, both projections and original encoded response commit together. `complete()` does not commit independently; the owning coordinator returns after transaction completion.

The same ID and intent replay the original bytes without subtracting again. A rejected 101-rial debit remains rejected even after a later credit increases funds; a new ID requests a new decision. Changing the amount, account or operation type under the same ID conflicts. A lock timeout is unavailability, not insufficient funds, and leaves no committed reservation. Recovery uses the same transaction ID.

## Source map

| Component | Responsibility |
| --- | --- |
| [DebitCommand](../../src/main/java/dev/kavrin/balance/operation/application/command/DebitCommand.java) | Required retry identity, account and positive money; no policy/database work |
| [DebitAttempt](../../src/main/java/dev/kavrin/balance/operation/application/debit/DebitAttempt.java) | Fixed operation/journal IDs, caller intent, execution snapshot and funding selection |
| [DebitHandler](../../src/main/java/dev/kavrin/balance/operation/application/debit/DebitHandler.java) | Replay-before-policy, locked funds decision, journal calculation, retained rejection and completion |
| [DebitStore](../../src/main/java/dev/kavrin/balance/operation/application/debit/DebitStore.java) | Transaction-bound persistence port; all writes share the owning attempt |
| [JpaDebitStore](../../src/main/java/dev/kavrin/balance/operation/infrastructure/persistence/JpaDebitStore.java) | Named native SQL, source-account reservation, replay mapping, history/projection writes and completion |
| [DebitResponseEncoder](../../src/main/java/dev/kavrin/balance/operation/application/debit/DebitResponseEncoder.java) | Produce and validate the retained response |
| [JsonDebitResponseEncoder](../../src/main/java/dev/kavrin/balance/operation/web/response/JsonDebitResponseEncoder.java) | Jackson encoding and debit-specific problem instance URI |
| [HttpAdapterConfiguration](../../src/main/java/dev/kavrin/balance/operation/web/configuration/HttpAdapterConfiguration.java) | Dispatch each mutation to its enabled handler |

Credit and debit deliberately remain explicit slices, following the recorded implementation guide. Shared transaction ownership, account locking, canonical identity and HTTP outcome mapping are reused. Identical codec/persistence mechanics can be extracted in a later focused refactor after both behaviors are established; the debit slice never calls credit-specific reservation SQL.

## Verification, 2026-10-25

Java 21 `./mvnw -o -B -ntp -Psqlserver-it verify` passed **351 portable tests and 134 SQL Server tests**, zero failures/errors/skips, in **31.235 seconds**. Fresh Surefire/Failsafe XML totals were checked. This adds 26 portable and 14 real SQL Server cases to the preceding checkout. The full run includes the existing credit, schema, permission, locking, opening and AccountStore projection regressions.

- [DebitHandlerTests](../../src/test/java/dev/kavrin/balance/operation/application/debit/DebitHandlerTests.java): 18 mock/lifecycle cases covering exact funds, journal signs, both projections, replay before disabled policy, conflict, retained rejection, funding underflow, storage/encoding/lock failure, uncertain commit and recovery after a duplicate reservation rolls back.
- [JsonDebitResponseEncoderTests](../../src/test/java/dev/kavrin/balance/operation/web/response/JsonDebitResponseEncoderTests.java) and [OperationResponsesTests](../../src/test/java/dev/kavrin/balance/operation/web/response/OperationResponsesTests.java): seven debit replay-validation cases and one Spring-configured debit problem serialization case.
- [SqlServerDebitIT](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/SqlServerDebitIT.java): 14 cases proving HTTP exact-funds success and balance read, durable insufficient-funds replay after later credit, payload/account/cross-operation conflict, success/rejection after application and pool recreation, policy/cap changes, missing-account retention, rollback after journal/projection/outcome writes with successful same-ID retry, a real blocked-account timeout and injected funding-underflow rollback. Independent JDBC queries verify per-ID record counts, retained response bytes, balanced sums, source-account intent and ledger-derived projections.

Fixtures insert only zero accounts through the initializer, then fund customers through real committed credits. Successful financial history is retained across reruns. The underflow case injects only the locked domain snapshot; it does not commit an unreconciled setup balance. No migration, volume deletion, data cleanup or permission expansion was needed.

The first portable run exposed an incorrect debit posting-side mapping introduced while adapting the credit slice; it was corrected before the passing runs. The existing SQL Server container initially stalled with errors 17300/17312. A restart of that same container recovered it without changing image, volume, 4 GB limit or other settings. An early integration attempt during recovery failed setup with pre-login errors; after the server became healthy and its loopback port was available, the full suite above passed. These failures are not counted as successful tests. Secret values were kept in the ignored local environment.

## Remaining boundaries

SQL Server 2022 CU22 on this ARM64 emulation setup remains unsupported by Microsoft; passing local tests do not establish production readiness. General transient retry/backoff, comprehensive uncertain-commit recovery, multi-instance mixed-operation gates, authentication/ownership remain open. Checked funding arithmetic currently propagates as a technical/internal failure and rolls back; CBS15’s broader error-classification work is not claimed complete.

The public README continues to describe the intended finished first version. For current behavior, use [implementation status](Implementation-status.md). For repeating the full suite, supply all three explicit restricted test identities from [Schema.md](Schema.md); do not put secrets in Maven command-line properties.
