> CBS09 implementation update, 2026-10-23: V1 is applied and validated in the local development/test databases. [Schema/runbook](Schema.md) records actual constraints, grants and 240 portable + 94 SQL Server tests. Historical pre-migration statements below are superseded by this implementation evidence. Financial execution remains pending.

# CBS09 implementation contract

[فارسی](../fa/CBS09-contract.md) · [Ledger](Ledger.md) · [Identity storage](Transaction-id-storage.md) · [ADR-010](../adr/ADR-010-persistence-contract.en.md)

## Review disposition, 2026-10-23

The engineering review for CBS02–04 is complete enough to begin CBS09. This contract selects the previously open implementation choices; it does not claim project-owner acceptance of every technical detail or learner mastery. Accepted business decisions remain the source for the four-operation scope, ledger, identity retention, customer/funding categories and 10000000000000-rial limits.

CBS09 now means implementing and testing the migration below. Complete financial behavior and its fault evidence remain CBS10–20 work, rather than prerequisites for creating its schema. No business DDL has been applied by this review.

## Selected storage and constraints

Use SQL Server 2022 CU22, schema `dbo`, BIGINT amounts in rials, `uniqueidentifier` account/operation/journal IDs, UTC `datetime2(7)` timestamps and explicit binary collations for symbolic codes. Use application-generated UUIDs allocated once per logical attempt; uncertain recovery retains the original IDs. Public transaction IDs remain opaque validated strings.

| Table | Required fields and row constraints |
| --- | --- |
| accounts | `account_id` UUID PK; `category` CUSTOMER_LIABILITY or FUNDING_CLEARING; uppercase three-letter `currency`; `balance` BIGINT NOT NULL; `status` ACTIVE or CLOSED; `created_at` UTC. Customer balance >= 0; synthetic funding can use the full signed-long range. New accounts start at zero through the controlled initialization path. Category/currency/identity are immutable in runtime writes. |
| operations | `operation_id` UUID PK; `transaction_id` globally unique with the tested nvarchar(256)/BIN2 length/character constraints; `operation_type` CREDIT/DEBIT/TRANSFER/OPENING; requested source/destination UUIDs; positive BIGINT `amount`; currency; canonical format version, full bytes and SHA-256 digest; execution policy version; optional retained funding ID/version/funded digest; state PENDING/SUCCEEDED/REJECTED; nullable rejection reason and response envelope; creation/completion UTC timestamps. |
| journals | `journal_id` UUID PK; mandatory unique `operation_id`; currency; created_at. Exactly one header per successful mutation is the application transaction invariant. |
| postings | PK `(journal_id, line_number)` with line_number > 0; mandatory account UUID, journal UUID, currency, DEBIT/CREDIT side, positive BIGINT amount. No independent mutable posting identity or cascade delete is needed. |

Operation shape is explicit: CREDIT/OPENING require destination and forbid source; DEBIT requires source and forbids destination; TRANSFER requires both and different values. Pair nullable checks with explicit IS NULL/IS NOT NULL branches so SQL unknown cannot bypass shape rules. Requested account IDs and retained funding provenance in operations have no account FKs: missing-account rejection must be recordable, and reservation must not acquire implicit account FK locks before the sorted locking phase.

Use `uq_operations_transaction_id` only on transaction_id. Add candidate keys `(account_id,currency)`, `(operation_id,currency)` and `(journal_id,currency)` to support composite currency FKs. Journals reference operations by operation_id/currency; postings reference journal and account with currency. Give constraints stable descriptive names, use NO ACTION on deletions, and document the extra candidate indexes as currency integrity costs rather than duplicate performance indexes. Add the postings account/currency index for account-reference access and reconciliation. Do not add speculative history/status indexes or another transaction-ID index.

Canonical bytes are authoritative; digest is a companion, not a replacement. Store canonical format version 1 with nonempty `varbinary(max)` bytes and `binary(32)` SHA-256. Require digest consistency with HASHBYTES in migration tests. Funding metadata is all absent or complete; transfers have none; successful CREDIT/DEBIT/OPENING require it. Preserve one server-owned immutable policy/funding snapshot across whole-attempt retries, retain it on completion and never rerun current selection during replay. Unsupported stored versions fail closed, not as a new operation.

PENDING has no completion, rejection or response. SUCCEEDED requires completion and a response and forbids rejection reason. REJECTED requires completion, response and a known reason. Terminal responses retain version 1, HTTP status, exact UTF-8 body bytes and content type; success uses status 200 and application/json, rejection uses its selected 4xx status and application/problem+json. Internal OPENING retains its internal result envelope without adding a public route. Original canonical amount may exceed the business cap so rejected over-cap attempts remain storable. Cap reason names TRANSACTION_LIMIT_EXCEEDED and BALANCE_LIMIT_EXCEEDED join the existing business reasons when evaluator/HTTP mapping is implemented.

Business caps are policy checks, not hardcoded into the generic Money type or operations amount CHECK. For a new operation, enforce the selected 10000000000000-rial transaction cap and resulting customer-balance cap using the retained versioned policy snapshot; schema retains structural nonnegativity and BIGINT bounds. A future cap change needs the documented transition policy. Existing terminal outcomes replay before reevaluation. Rejection writes no journal or projections.

## Selected locking adapter

Use explicit native SQL through EntityManager under the same JpaTransactionManager/REQUIRED TransactionTemplate, with a fresh attempt persistence context. Do not depend on a generic pessimistic annotation's SQL translation or reuse previously loaded account state. CBS10 must verify entity mapping and transaction participation against the real migration.

```sql
SELECT account_id, category, currency, balance, status
FROM dbo.accounts WITH (UPDLOCK, HOLDLOCK)
WHERE account_id = CAST(? AS uniqueidentifier);
```

Bind only canonical UUID text obtained from AccountId. Deduplicate the complete participant set, sort with AccountLockOrder.DESCENDING and issue one lookup per ID in that order. Do not use SQL ORDER BY or a multi-ID query as an acquisition-order guarantee. Missing requested accounts yield terminal rejection; the missing-key lookup holds range protection through completion. Missing internal funding is a technical configuration defect and rolls back. Missing-key protection may block an entire index gap; no row-only or universal independent-progress claim is made. All initialization/maintenance writers obey the same order.

New native-JDBC evidence checks UUID and signed BIGINT round trips, the difference between SQL GUID sorting and the application comparator, writer blocking, unrelated existing-key progress, missing-key creation blocking, and two opposite-direction workers completing in the same deduplicated order. It does not measure escalation or prove no possible deadlock. [Microsoft's table-hint rules](https://learn.microsoft.com/en-us/sql/t-sql/queries/hints-transact-sql-table) describe the underlying mechanism; real migration/entity tests remain required.

## Transaction and recovery contract

The coordinator refuses an ambient transaction. Parse/validate intent and check access; locate an existing identity and compare full canonical bytes before mutable policy evaluation. Reserve/flush the operation before account locks. Expected duplicate must exit and roll back before fresh committed-result recovery. Validate locked account state, build the entire balanced journal and calculate every checked projection before any monetary mutation. Persist journal, postings, projections and terminal response together. Return business rejection as a normal callback value so its record commits. Technical failures escape and roll back. Expose success only after confirmed commit.

Cross-row journal balance, success-to-journal completeness, immutable terminal outcomes and absence of committed PENDING are application/protocol invariants. Ordinary row CHECKs cannot prove them. Keep exactly this distinction in migration documentation and tests. Reconciliation independently checks operation/journal counts, all posting totals and projection equality using wider exact arithmetic in a quiescent fixture or a consistent committed snapshot.

| Failure | Required action |
| --- | --- |
| 2627/2601 for the named transaction-ID key | Roll back; fresh recovery compares canonical intent and returns retained result or conflict. Other unique keys are defects, not replay. |
| Deadlock victim 1205 | Retry the entire attempt only after transaction cleanup, within budget. Do not retry one statement. |
| Lock timeout 1222 | Explicitly roll back the whole attempt, then recover/retry within budget. Tests demonstrate an active transaction may remain. |
| Unexpected CHECK/FK, invalid stored data or overflow in an invariant | Roll back and surface a technical failure; do not disguise it as duplicate or insufficient funds. |
| Connection loss during commit/uncertain completion | Same-ID recovery before any reexecution. One empty lookup is not proof of noncommit. If unresolved, return temporary unavailability and retain the ID for retry. |

Selected initial local-development settings are max 3 attempts, READ_COMMITTED, lock wait 1000 ms, query timeout 2 s, transaction timeout 5 s, datasource acquisition timeout 5 s and cooperative request budget 20 s. Retain driver login/socket timeouts of 5 s. Exponential full jitter is 0–50 ms then 0–100 ms, outside transactions. Admission to an attempt must fit remaining budget; connection waits, recovery and backoff count too. Configure query timeouts on native queries and verify driver behavior in implementation. A deadline never permits abandoning cleanup or claiming a hard network/commit bound. These are engineering defaults, not measured production SLOs; runtime configuration and retry implementation are future code.

## Migration and runtime authority

Use a dedicated migration login/user with CONNECT, CREATE TABLE and ALTER/REFERENCES on the application schema, plus the rights needed for its own Flyway history. It must not be sysadmin/db_owner. Do not put migration credentials in the packaged runtime environment. CBS09 must run actual Flyway migrate/validate with that identity, fail when it is absent, and keep clean disabled. Never use baseline-on-migrate to hide an unknown schema.

Runtime cbs_app gets SELECT on the four tables, INSERT on operations/journals/postings, and UPDATE only the operation completion columns and account balance. It gets no schema DDL, history UPDATE/DELETE, operation DELETE, identity/canonical field UPDATE or broad db_datawriter role. Account creation belongs to a separate controlled initializer/maintenance identity with limited account INSERT authority; nonzero initialization still posts an opening journal through the financial path. No public account-creation/closure endpoint is introduced. Granting DML does not make arbitrary inserts balanced or prevent a compromised writer from committing PENDING. The trusted single posting path, no alternate writers, failure tests and reconciliation enforce those invariants. Stronger stored-procedure enforcement would be a separate design change.

Current cbs_app remains CONNECT-only: this review grants nothing. CBS09 provisions the separate identities outside versioned business SQL and tests their actual permissions. Flyway schema changes run separately from runtime startup; keep Hibernate ddl-auto=validate. A privilege/DDL failure must fail startup/deployment, never fall back to automatic schema updates. Retain all operation/journal/posting evidence indefinitely; no cascade or deletion job. Account closure is deferred, and existing posted accounts are never deleted. Corrections are new journals, not edited history.

## CBS09 scope and exit checks

Implement `V1__create_balance_schema.sql` with the four tables, explicit constraints/indexes and no nonzero financial seed data. Provision migration/runtime/initializer authority through local infrastructure setup, using ignored generated secrets. Configure separate Flyway credentials/run mode. Require real SQL Server tests for empty-database migration, validate/rerun, actual metadata and indexes, boundary/invalid direct writes, currency FKs, operation shapes and terminal response row states, global identity uniqueness, restricted runtime permissions and transaction rollback. Verify negative cases fail for the expected named constraint, not missing grants. Keep portable verify independent of SQL Server.

Journal-backed opening fixtures, JPA mappings and authoritative balance query are CBS10. Coordinator/financial operations, idempotent replay, cap outcome mapping, whole-attempt retry, write-boundary failure injection, deadlock-victim recovery, uncertain commit and independent reconciliation are CBS11–20. Query plans/constraint cost and funding-hot-row performance remain CBS21–22. Their unfinished status does not reopen the reviewed schema contract; new evidence that contradicts it requires a recorded revision.

## Verification

Full Java 21.0.11 `./mvnw -Psqlserver-it verify` passed 240 portable and 44 actual SQL Server tests, zero failures/errors/skips, in 16.997 seconds. Four UUID cases took 2.737 seconds. The environment remains the explicitly authorized unsupported ARM64 SQL Server emulation experiment. Formal learning acceptance and production readiness remain separate.
