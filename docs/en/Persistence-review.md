> CBS09 implementation update, 2026-10-23: V1 is applied and validated in the local development/test databases. [Schema/runbook](Schema.md) records actual constraints, grants and 240 portable + 94 SQL Server tests. Historical pre-migration statements below are superseded by this implementation evidence. Financial execution remains pending.

> Current review disposition, 2026-10-23: CBS02–04 engineering review is closed for entry to CBS09. [The implementation contract](CBS09-contract.md) selects storage, lock SQL, constraints, retry defaults and authority. Earlier open-choice entries below are historical unless retained in that contract. Migration/entity/financial verification belongs to CBS09–20; no business DDL has been applied.

# Pre-migration SQL Server review

[فارسی](../fa/Persistence-review.md) · [Accepted limits](Amount-limits.md) · [Database review](Database-review.md) · [Transaction protocol](Transaction-protocol.md) · [Test runbook](SQL-server-tests.md)

Status, 2026-10-23: partial CBS02–04 review with actual SQL Server observations. No business migration or financial handler is implemented. The accepted ledger design and numeric limits do not by themselves close the persistence gate.

## Why these experiments matter

Suppose two debits both read a customer balance of 100 and each approves spending 80. Validation against an unlocked snapshot cannot prevent both approvals. A transaction must protect the balance it uses until it finishes writing the journal, projections and outcome. Closing a JDBC result is not the transaction boundary.

Idempotency has a different risk: Java considers `transfer-001`, `Transfer-001` and `transfer-001 ` different strings. TransactionId rejects the third string, while preserving case. A database identity key must agree with the accepted domain, including inputs through every permitted write path. Choosing a column collation alone is insufficient evidence.

## Four additional integration cases

[SqlServerProtocolReviewIT](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/SqlServerProtocolReviewIT.java) uses the CBS08 dedicated test connection and generated temporary fixtures. It runs in `./mvnw -Psqlserver-it verify`. No administrator privilege, migration or permanent table is needed.

| Case | Observed result |
| --- | --- |
| Explicit Latin1_General_100_BIN2 comparison | Case variants differ; text with a trailing space still compares equal. DATALENGTH reports 24 versus 26 bytes for the two Unicode strings. |
| Plain READ_COMMITTED read | After the result closes, another connection updates the row while the reader's transaction is still open. The reader then sees 9 instead of 7. |
| UPDLOCK/HOLDLOCK with holder rollback | A second locking read of key 1 fails with SQL error 1222. An update to existing key 2 succeeds while the holder still owns key 1. After holder rollback, a fresh locking read of key 1 succeeds. |
| UPDLOCK/HOLDLOCK with holder commit | The same protection and unrelated-key progress hold; after holder commit, the fresh locking read succeeds. |

The query under review is a parameterized primary-key equality lookup:

```sql
SELECT probe_value
FROM <generated temporary fixture> WITH (UPDLOCK, HOLDLOCK)
WHERE fixture_id = ?;
```

The tests explicitly select READ_COMMITTED and use integer fixture keys. UPDLOCK requests update protection during the read; HOLDLOCK requests serializable semantics for the referenced table within the transaction. These hints do not guarantee only physical row locks. A primary-key lookup helps keep the search narrow; scans, missing keys and escalation need separate review. See [Microsoft's locking guide](https://learn.microsoft.com/en-us/sql/relational-databases/sql-server-transaction-locking-and-row-versioning-guide?view=sql-server-ver16).

The holder's statement/result close before the contender executes. LOCK_TIMEOUT is 250 milliseconds for the contender, and JDBC socket waits remain bounded by the harness. With XACT_ABORT explicitly OFF, XACT_STATE remains 1 after error 1222: the transaction is still active and committable. The test rolls it back before the next attempt. Runtime financial code must let technical failures leave the transaction callback and ensure full rollback before recovery/retry. These experiment settings are not selected production timeout values.

The schedule is deterministic: the first connection acquires protection, then the second issues its conflicting request. This avoids sleeps and proves overlap without relying on thread timing. It is a small protocol experiment, not a load test, deadlock-victim test or throughput measurement.

## Exact identity still needs a storage contract

The BIN2 observation supports case distinction but does not settle a complete transaction-ID column/constraint design. SQL Server pads character strings for equality comparisons, including these binary-collated strings. Changing ANSI_PADDING is not a fix for equality semantics. [Microsoft's comparison rules](https://learn.microsoft.com/en-us/sql/t-sql/language-elements/string-comparison-assignment?view=sql-server-ver16).

The final design must reject every character outside the accepted 1–128 ASCII letters/digits/`._:-` domain before any lossy conversion, preserve allowed case variants as different keys, and reject exact duplicates through one named global unique constraint. Test direct writes and parameter binding as well as Java validation. Include leading/trailing spaces, control/NUL characters, Unicode lookalikes and length boundaries. A successful literal comparison does not prove index uniqueness or safe column conversion. The initial comparison experiment alone selected no storage design. The follow-up [transaction-ID storage review](Transaction-id-storage.md) now supplies a tested engineering candidate; the full migration gate remains open.

## Remaining migration gates

| Boundary | Current state and required evidence |
| --- | --- |
| Business caps | User selected the shared 10000000000000-rial limit and durable over-cap rejection. [Examples](Amount-limits.md) explain the accepted behavior. Evaluator, outcome mapping and policy storage remain pending. |
| Global identity | Portable canonical bytes exist. [Unicode BIN2 candidate](Transaction-id-storage.md) now verifies character/length checks, named uniqueness and competing insert commit/rollback. Real operations mapping, canonical conflict/replay and uncertain commit remain pending. |
| Account locks | Native JDBC integer-key prototype verified. UUID mapping, sequential AccountLockOrder lookups, JPA/native adapter choice, missing-row protection, escalation and deadlock behavior remain open. |
| Four-table constraints | Final accounts/operations/journals/postings DDL, currency references, operation shapes and response/policy storage still need review. Rejected missing-account intent must not require account FKs; actual postings must. |
| Transaction lifecycle | Enforced nontransactional coordinator plus REQUIRED TransactionTemplate is accepted. Reservation flush, no committed PENDING state, write-boundary rollback, uncertain commit recovery and named duplicate classification need implementation/tests. |
| Retry budget | Maximum attempts, backoff/jitter, lock/statement/transaction/pool waits and total deadline remain unselected. A lock timeout is not proof of rollback; a commit connection error is not proof of noncommit. |
| Permissions | cbs_app still has CONNECT only in application databases. Review a separate migration identity, controlled financial write authority and history update/delete restrictions. No new grants made. |
| Reconciliation | Rebuild projections independently with exact wider totals and a consistent committed view. Atomic insertion alone does not prove balanced history. |

Every writer, including initialization, closure and repair, must follow the complete account-lock order and atomic ledger protocol. Restricting history updates/deletes does not prevent an alternate writer from inserting an unbalanced journal. The permitted posting path and reconciliation remain part of correctness review.

## Recorded verification

Java 21.0.11 full profile verification passed **240 portable plus 9 SQL Server tests**, zero failures/errors/skips, in 12.062 seconds. The four new cases took 0.890 seconds; the five CBS08 cases took 0.573 seconds. These are test execution times, not performance targets. Fresh XML reports were checked.

The first invocation could not open Maven's cache lock under the restricted filesystem and ran no tests. The subsequent authorized run succeeded. Evidence applies to the existing SQL Server 2022 CU22 Developer ARM64 emulation experiment. It does not establish supported ARM64 deployment, production performance, account schema correctness, complete financial transactions, JPA lock mapping or learner acceptance. Finish the listed review before CBS09 migrations.

## Transaction-ID review checkpoint, 2026-10-23

The tested engineering candidate uses nvarchar(256) with explicit BIN2 collation, NOT NULL, 1–128-character length/allowlist checks and named global uniqueness. Unicode JDBC binding preserves invalid input for rejection. Thirty-one new real cases include exhaustive single-BMP-code-unit predicate checking and competing inserts. Full Java 21 profile verification passed 240 portable plus 40 SQL Server tests, zero failures/errors/skips, in 12.756 seconds; the new class took 2.091 seconds. See [design, SQL, examples and limitations](Transaction-id-storage.md). Earlier counts are historical. No business migration, runtime repository or durable replay is added. Next review is the UUID account-lock adapter and complete four-table transaction/permissions contract; formal learner acceptance remains open.
