# V1 requirement and failure matrix

[فارسی](../fa/Requirements.md) · [API contract](API-v1.md) · [Decisions](Decisions.md) · [Constraint review](Database-review.md) · [Business outcomes and commit boundary](Operation-outcomes.md)

Status, 2026-10-22: assisted CBS01 design artifact based on the recorded discussion and repository roadmap. No financial behavior or proposed test below is implemented or verified. The original challenge has not been independently rechecked for this matrix; the baseline labels refer to the repository's stated four-operation scope, not a claim that every policy is an employer requirement.

## Scope and application boundary

Core baseline: credit, debit, transfer and authoritative balance lookup. Accepted project extensions: HTTP delivery, IRR rial policy, durable terminal rejection and the four-table double-entry model. Later optional stages: authentication/ownership before external use, OTP, Redis, outbox/Kafka and conditional CDC. FX remains future design.

Thin HTTP controllers validate request shape and map outcomes. The internal application service coordinates durable identity, account locks, business checks, balanced postings, projection updates and stored outcomes in one SQL Server transaction. Repository adapters implement persistence; controllers must not write balances independently. The existing Java interface is scaffolding, not a frozen signature contract. Detailed DTOs remain CBS23; transaction and identity protocols remain CBS03–04.

## Cases and proposed evidence

In this table, a terminal rejection means a committed operation identity/fingerprint/original rejection response with no monetary effect. Every success includes all projections and one balanced journal with at least two immutable postings. Replay never adds another journal. These mutation rules do not turn a balance GET into a recorded financial operation.

| Case | Required outcome / durable state | Proposed evidence |
| --- | --- | --- |
| Credit / debit | Server-selected funding counterpart; atomic balanced postings, projections and success | HTTP-to-SQL test; independently reconstruct both accounts |
| Transfer | Equal rial amount between distinct existing customer accounts; atomic success | Verify both balances and balanced journal directly in SQL |
| Balance lookup | Committed projection and currency; no operation/journal creation | Read after commit; unknown account response |
| Null/blank/malformed IDs | Validation rejection before operation creation | Contract tests; operation count unchanged |
| Zero/negative/fractional amount, unsupported currency or amount above long range | Validation rejection; never truncate or silently round | Boundary JSON tests and no durable financial rows |
| Self-transfer | Validation rejection before operation creation | Same-account request; no durable rows |
| Missing participating account in a valid mutation | Terminal business rejection; preserve request identity even if account is created later | Reject, create account, retry same ID and verify original rejection |
| Insufficient funds | Terminal rejection; exact available balance may be spent to zero | Exact-balance success; one-rial excess rejection; replay after replenishment |
| Resulting projection overflow | Terminal rejection, all balances unchanged and no journal | Recipient/system-account boundary tests and full rollback assertions |
| Identical ID/payload | Original stored response; one financial effect | Concurrent duplicates and lost-response retry |
| Same ID/different payload or operation type | Conflict; preserve original operation | Concurrent conflicting requests; one retained outcome |
| Distinct concurrent IDs draining one account | Lock before funds check; at most affordable work succeeds | Barrier-controlled competing debits and final journal/projection checks |
| Opposite transfers / shared funding account | Same global account order, including system accounts | Opposite-direction and funding contention tests; bounded waits |
| Deadlock victim | Fresh whole-transaction retry with same ID; bounded attempts | Inject transient rollback; check one eventual financial effect |
| Database unavailable before transaction | Temporary failure; no terminal decision | Restore service and retry same ID |
| Lost commit acknowledgment | Uncertain outcome; same-ID recovery replays commit or executes if absent | Fault at commit acknowledgment; assert one durable operation effect |
| Failure after partial writes before commit | Roll back every monetary write and uncommitted outcome | Inject failure at each write boundary |
| Nonzero opening balance | Internal operation and balanced journal committed with projection | Reconstruct opening balance from postings; no direct balance seed |
| Incorrect committed financial instruction | Preserve history; append authorized linked correction/reversal | Original entries unchanged; inspect correction linkage and balances |
| Correct history, corrupt projection | Controlled audited projection repair; no false financial journal | Detect isolated corruption, coordinate writers, revalidate and repair |
| Concurrent reconciliation | Compare one committed snapshot; coordinate repair with later writers | Commit between reads without false mismatch; stale repair protection |

A missing-account rejection creates a schema design question: storing its requested account ID must not require that absent account to exist. CBS02 must distinguish requested identifiers from successful posting references; do not add account foreign keys blindly to every request field.

## Open policies and gates

- A separate business transaction cap is accepted; numeric value and configuration semantics are still open. Do not substitute Long.MAX_VALUE or invent a regulatory cap. Any business maximum balance is also unresolved.
- Choose whether exceeding the configured transaction cap is admission validation or a durable business decision, and define replay behavior across policy changes. Recheck stored identity before re-evaluating mutable business rules for an existing operation.
- Account IDs now wrap UUID and use the accepted descending unsigned 128-bit application lock order. Transaction-ID syntax/comparison and canonical fingerprint encoding still require review; account UUID parsing at HTTP and SQL mapping/lock behavior are not implemented.
- Missing-account rejection storage, operation-to-journal uniqueness and append-only enforcement need CBS02–04 design. No business migration before those reviews.
- All tests in the table are planned. The guided oral review and this assisted matrix do not establish independent implementation, full CBS01 acceptance or completion of the earlier CBS00 review/PR-practice gap.

## CBS05 partial implementation, 2026-10-23

`Money(long amount, Currency currency)` replaces the earlier positive-only RialAmount. It requires a non-null java.util.Currency and preserves the supplied long amount directly. For IRR, 100 means 100 rials. Zero and signed amounts are representable; command/posting positivity is separate and pending; the account-category balance-sign policy is now implemented independently. Currency metadata does not scale the amount. No FX conversion or other-currency API support is introduced. Boundary tests cover both long extremes, positive, zero and negative values, 100 IRR preservation and missing currency. Money now provides same-currency add/subtract with checked long arithmetic and same-currency comparison without subtraction overflow. JSON validation, business caps, journals, transactions and SQL constraints remain pending.

Verification, 2026-10-23: Java 21 `clean verify` passed 9 tests, zero failures/errors/skips: 8 Money cases and the existing HTTP health smoke test.

Arithmetic verification, 2026-10-23: Java 21 Maven `verify` passed 20 tests, zero failures/errors/skips: 19 Money cases plus the HTTP health smoke test. Covers signed/zero results, exact boundaries, overflow in both directions, currency mismatch, null operands, comparison extremes and operand immutability. No database behavior tested.

Account-category increment, 2026-10-23: `AccountCategory.validateBalance(Money)` rejects negative CUSTOMER_LIABILITY balances and permits signed synthetic FUNDING_CLEARING balances. Every category requires a non-null balance. The policy validates a value; no account object, posting-side calculation, business cap, HTTP failure mapping, locking or database update is implemented.

Account-policy verification, 2026-10-23: Java 21 Maven `verify` passed 32 tests, zero failures/errors/skips: 12 AccountCategory cases, 19 Money cases and 1 HTTP smoke test. AccountCategory validation is not yet wired into an account model or financial use case.

Account-model increment, 2026-10-23: immutable `Account(AccountId, AccountCategory, Money)` validates required fields and category balance rules at construction. `applyBalanceDelta(Money)` checks currency and arithmetic through Money, validates the resulting category balance and returns a new snapshot without mutating the original. This delta is an internal signed projection change, not a positive transfer amount or an accounting DEBIT/CREDIT side. AccountId now requires a non-null UUID; AccountLockOrder.DESCENDING supplies the accepted shared ordering. HTTP parsing and actual SQL locking remain pending. Nonzero snapshot construction supports representing state, not creating persisted opening money. Opening journals, balanced postings, locks and atomic persistence remain required and unimplemented. Domain exceptions are not yet mapped to HTTP or durable business outcomes.

Account-model verification, 2026-10-23: Java 21 Maven `verify` passed 55 tests with zero failures/errors/skips: 16 Account, 7 AccountId, 12 AccountCategory, 19 Money and 1 HTTP smoke test. Includes immutable exact-zero changes, overdraft rejection, signed funding changes, nulls, currency mismatch and overflow. No SQL Server behavior tested.

UUID account identity, 2026-10-23: `AccountId(UUID)` replaces String identity. `AccountLockOrder.DESCENDING` compares most-significant then least-significant bits unsigned and reverses the result. It defines ordering only; no repository/locks exist yet. Distinct participants must later be sorted and locked sequentially with this policy, including funding accounts. Transaction IDs remain a separate client-supplied identity. UUID does not establish authorization or account existence. SQL uniqueidentifier mapping/index layout remains a persistence review item.

UUID/order verification, 2026-10-23: Java 21 Maven verify passed 56 tests, zero failures/errors/skips: 6 order, 2 UUID identity, 16 Account, 12 category, 19 Money and 1 smoke. Earlier String-ID tests were replaced, not skipped. Comparator tests cover unsigned high/low boundaries, equality, opposite-direction ordering, extremes/transitivity and nulls.

Ledger domain increment: Posting validates positive amounts and required fields; its signed delta depends on account category. Journal validates an immutable single-currency posting set with at least two lines and exact BigInteger balance. Net per-account projection preparation, actual account-currency validation, operation/header identity and persistence are still pending. See Ledger.md for the implemented example and boundaries.

Ledger-domain verification, 2026-10-23: Java 21 Maven verify passed 79 tests, zero failures/errors/skips: 11 Journal, 12 Posting and 56 prior cases. Includes multi-line balance, immutable collections, single-currency validation, normal-side deltas, exact totals above long and rejection of overflow-disguised imbalance. No database behavior tested.

## CBS05 net balance projection, 2026-10-23

`Journal.projectBalances(Map<AccountId, Account>)` now calculates immutable proposed snapshots for the journal's participating accounts. It resolves each posting through the supplied map, rejects missing accounts, mismatched key/identity and account currencies, and uses each resolved account's category for `balanceDelta`. Extra nonparticipating accounts do not appear in the result; participants with zero net change do.

All changes for one account are accumulated exactly with `BigInteger`. Only the final value, current balance plus net change, is converted with `longValueExact` and validated by the Account constructor. A net delta can exceed long even when both old and new signed funding balances fit. No partial result escapes if any participant fails; input snapshots stay unchanged. The returned map is immutable and its iteration order has no locking meaning.

The learner correctly predicted 100 rials from a starting customer balance of 50, debit 100 and credit 150. The balancing third line is funding debit 50. The implementation validates the final 100 instead of rejecting a temporary -50 caused by applying the first line alone. Reordering the same posting set produces the same projections. This domain calculation does not override the API's self-transfer rejection policy.

The caller must eventually supply current snapshots obtained under the accepted financial transaction/locking protocol. This method does not load or lock database rows, authorize a request, persist projections or create a terminal operation outcome. Invalid domain input currently throws IllegalArgumentException, null input throws NullPointerException, and final numeric overflow throws ArithmeticException; HTTP/business-outcome mapping remains future work.

Java 21.0.11 Maven verify passed 92 tests, zero failures/errors/skips in 8.354 seconds: 13 projection cases plus the previous 79. Projection tests cover transfer and category effects, repeated-account netting, reversed line order, temporary overdraft/overflow, net deltas beyond long, final positive/negative overflow, customer nonnegative policy, missing/misidentified accounts, currency, nulls and immutable participant-only results. This is portable domain evidence, not SQL Server atomicity/concurrency evidence. Next CBS05 work is command validation and transaction identity boundaries before HTTP adaptation.

## CBS05 transaction ID validation, 2026-10-23

The learner selected rejection of `" transfer-001 "` rather than trimming it into an existing ID. `operation.domain.TransactionId` now implements the documented baseline: 1–128 ASCII letters, digits, `.`, `_`, `:` or `-`, with exact case-sensitive identity and no normalization. UUID strings are accepted as opaque text, not parsed or required. AccountId remains a separate UUID-backed type.

```java
new TransactionId("transfer-001");   // valid, preserved exactly
new TransactionId("Transfer-001");   // valid, distinct identity
new TransactionId(" transfer-001 "); // IllegalArgumentException
```

Null input raises NullPointerException; invalid syntax raises IllegalArgumentException. This value object does not reserve an ID, compare commands, replay a response or enforce database uniqueness. The HTTP/coordinator path is not wired yet; it must construct validated IDs before reservation. SQL Server comparison/collation must eventually match this exact case-sensitive identity and be verified on SQL Server. Accepting whitespace rejection does not approve the remaining draft fingerprint/storage/recovery protocol.

Java 21.0.11 Maven verify passed 115 tests with zero failures/errors/skips in 7.871 seconds, including 23 TransactionId cases. Cases cover minimum/maximum length, 129-character rejection, allowed punctuation, UUID-shaped text, null/empty, whitespace/control/invisible characters, unsupported Unicode and punctuation, equality and case distinction. No HTTP or SQL Server financial behavior tested. Next CBS05 increment: typed mutation command validation, including positive IRR amounts and distinct transfer accounts; business cap values remain unresolved.

## CBS05 typed mutation commands, 2026-10-23

Implemented application inputs: `CreditCommand(TransactionId, AccountId, Money)`, `DebitCommand(TransactionId, AccountId, Money)` and `TransferCommand(TransactionId, AccountId sourceAccountId, AccountId destinationAccountId, Money)`. These immutable records live in `operation.application`; they are independent of JSON DTOs and persistence entities. Each requires non-null IDs and amount, positive IRR and, for transfer, distinct account UUID values. A package-private CommandValidation helper owns the common amount rule without changing Money's support for zero/signed values and other currencies.

Example, assuming existing typed sourceId and destinationId:

```java
var command = new TransferCommand(
        new TransactionId("transfer-001"), sourceId, destinationId,
        new Money(100, Currency.getInstance("IRR")));
```

Constructing a command does not reserve transaction identity or move money. The future adapter/coordinator must construct it before reservation. Missing fields currently throw NullPointerException; nonpositive/unsupported amounts and self-transfers throw IllegalArgumentException. Equality of AccountId values detects self-transfer even with separate wrapper instances. The original BalanceService interface remains unwired scaffolding; no handler/API implementation is registered by this increment. Credit/debit commands contain only the customer account; funding selection remains server-owned in the future execution path.

Account existence, authorization, sufficient funds and resulting projection overflow are not checked by these constructors. A well-formed mutation naming a missing account still needs the accepted terminal rejection path. JSON fractional/out-of-long amounts and malformed UUID text must be rejected by the future transport parser; a long-backed Money cannot itself represent those invalid JSON inputs. Long.MAX_VALUE is accepted at this representation boundary; numeric business caps and their mutable-policy/replay classification remain unresolved and must be reviewed before financial execution. No cap is inferred from the type limit.

Java 21.0.11 Maven verify passed 128 tests with zero failures/errors/skips in 8.736 seconds. Thirteen new command cases cover positive representation boundaries, nonpositive amounts, unsupported currency, missing fields, UUID-value self-transfer, direction/identity preservation and the absence of reservation during construction. No SQL Server or financial HTTP evidence. Next CBS05 increment: explicit business rejection/success outcome types and their boundary with technical failures, before HTTP adaptation. Task acceptance remains open.

## Supported currency policy amendment, 2026-10-23

At the user's request, supported currencies are no longer hardcoded inside CommandValidation or the command constructors. `CommandValidation.requirePositiveAmount` checks only presence and positivity. CreditCommand, DebitCommand and TransferCommand preserve the Money currency for a separate `SupportedCurrencies(Set<Currency>)` policy. This supersedes the earlier command-constructor IRR restriction. The initial V1 deployment policy remains IRR; a set-based validator does not add FX conversion or define units for additional currencies.

SupportedCurrencies defensively copies the supplied set and exposes `requireSupported(Currency)`. Any supplied currencies can be enabled; none is built into production validation code. Null sets/members/currency are rejected, and an explicitly empty set disables all currencies. Changing the source set cannot change an existing policy snapshot. Future configuration or a database adapter can supply a new snapshot without putting policy in command payloads or adding database reads to value constructors. No loader, database table or runtime bean is implemented yet.

Illustrative composition with a supplied policy set, before execution:

```java
var policy = new SupportedCurrencies(currenciesFromConfiguration);
policy.requireSupported(command.amount().currency());
```

A future coordinator must resolve an existing terminal outcome before reevaluating this mutable admission policy. Matching retries replay their original result even if that currency has since been disabled. For a new operation, evaluate support using one consistent policy snapshot before reservation; valid syntax and supported-by-current-policy are separate checks. Database loading failures are technical failures, not an empty policy or a terminal unsupported-currency decision. The loader/refresh consistency protocol remains future design and requires integration tests.

Java 21.0.11 Maven verify passed 137 tests with zero failures/errors/skips in 7.565 seconds. Nine policy cases cover multiple allowed currencies, rejection outside the set, a set without IRR, empty/null sets and immutable snapshots. The 13 command cases now verify currency preservation instead of a hardcoded unsupported-currency rejection. HTTP, replay ordering and database policy loading remain unimplemented; this is portable calculation/validation evidence.
