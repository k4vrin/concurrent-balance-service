# V1 requirement and failure matrix

[فارسی](../fa/Requirements.md) · [API contract](API-v1.md) · [Decisions](Decisions.md) · [Constraint review](Database-review.md)

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
- Define ID format/length/comparison and canonical fingerprint encoding. Descending account order was selected in the numeric example; the comparator for the final identifier representation needs CBS04 review.
- Missing-account rejection storage, operation-to-journal uniqueness and append-only enforcement need CBS02–04 design. No business migration before those reviews.
- All tests in the table are planned. The guided oral review and this assisted matrix do not establish independent implementation, full CBS01 acceptance or completion of the earlier CBS00 review/PR-practice gap.

## CBS05 partial implementation, 2026-10-23

`Money(long amount, Currency currency)` replaces the earlier positive-only RialAmount. It requires a non-null java.util.Currency and preserves the supplied long amount directly. For IRR, 100 means 100 rials. Zero and signed amounts are representable; command/posting positivity and account-category balance rules are separate and remain unimplemented. Currency metadata does not scale the amount. No FX conversion or other-currency API support is introduced. Boundary tests cover both long extremes, positive, zero and negative values, 100 IRR preservation and missing currency. Money now provides same-currency add/subtract with checked long arithmetic and same-currency comparison without subtraction overflow. JSON validation, business caps, account-category rules, journals, transactions and SQL constraints remain pending.

Verification, 2026-10-23: Java 21 `clean verify` passed 9 tests, zero failures/errors/skips: 8 Money cases and the existing HTTP health smoke test.

Arithmetic verification, 2026-10-23: Java 21 Maven `verify` passed 20 tests, zero failures/errors/skips: 19 Money cases plus the HTTP health smoke test. Covers signed/zero results, exact boundaries, overflow in both directions, currency mismatch, null operands, comparison extremes and operand immutability. No database behavior tested.
