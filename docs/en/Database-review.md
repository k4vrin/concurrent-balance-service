> Current review disposition, 2026-10-23: CBS02–04 engineering review is closed for entry to CBS09. [The implementation contract](CBS09-contract.md) selects storage, lock SQL, constraints, retry defaults and authority. Earlier open-choice entries below are historical unless retained in that contract. Migration/entity/financial verification belongs to CBS09–20; no business DDL has been applied.

> Bootstrap update, 2026-10-20: SQL Server replaces the earlier proposed Oracle target. Phase P0 is started, not complete. Dependencies and a health smoke test do not prove financial correctness.

# Database correctness and cost review

Status: pre-schema proposal, no DDL applied and no measurements. [فارسی](../fa/Database-review.md) · [Decisions](Decisions.md)

## Review before migration V1

Accepted option E starts with conceptual `accounts`, `operations`, `journals` and `postings`; see [Ledger.md](Ledger.md) and [ADR-007](../adr/ADR-007-double-entry-ledger.en.md). Later phases add `transfer_intents`, `outbox_events`, `processed_events` and `transaction_history`. Table and column names below are proposed. An operation row represents one credit, debit or complete transfer and its durable outcome. Internal generated row IDs and client transaction IDs are different concepts.

Before accepting V1 settle: SQL Server image/version and portability boundary; identifier representation; a fixed money unit/currency; account initialization authority; global transaction ID scope; maximum value; rejection persistence; retention; account closure/deletion; and detailed posting/account-category rules. Double-entry starts in V1 by accepted design; the detailed schema still needs review. Capture changes in ADRs before migrations.

| Invariant | Proposed safeguard | What it does not prove | Cost hypothesis / evidence needed |
| --- | --- | --- | --- |
| Required identity/amount/outcome fields exist | Named NOT NULL plus application validation | Correct authorization or request meaning | Validation CPU; invalid direct-SQL tests |
| Amount positive and Java-compatible integer | `amount > 0`, integer representation, explicit signed-long upper bound | Safe multi-value arithmetic | Row checks; overflow tests and checked Java arithmetic |
| Customer balance nonnegative; system balance follows its category policy; all within long range | NOT NULL and category-aware range CHECK | Correct read-check-write concurrency | Per-update validation; direct SQL and concurrent debits |
| One transaction ID across all mutation types | NOT NULL plus global UNIQUE transaction ID | Same payload, caller access or a new ID for the same intent | Unique-index writes, duplicate contention, storage |
| Referenced accounts exist | Named foreign keys for participating accounts | Ownership, active status or currency match | Lookup/index maintenance; FK index review and orphan test |
| Operation shape matches type | Type allowlist and conditional row CHECK for required/forbidden source/destination | Cross-row state | Check evaluation and migration cost for new types |
| Transfer source differs from destination | Conditional CHECK plus service validation | Both updates commit together | Small row check; direct SQL rejection |
| Balanced journal and synchronous projections | Validated complete posting set and one transaction covering operation, journal, postings and all account projections | Independent reconstruction or correct account selection | Projection and posting writes, lock duration, transaction-log/version-store work; failure injection |
| No lost updates | Ordered account row locks before validation; all balance writers obey protocol | Unlimited throughput on one hot account | Lock waits, connection saturation and hot-account test |
| OTP authorizes one immutable intent | Owner binding, immutable financial fields, intent row lock, unique executed intent linkage | Secure delivery channel by itself | Intent writes and contention; concurrent-confirm tests |
| History consumer applies an event once | Unique `(consumer_name, event_id)` with effect in same transaction | Exactly-once delivery from Kafka | Dedup-index writes, retention and duplicate test |

A CHECK that evaluates to unknown can permit null values; pair required conditions with NOT NULL and explicit operation-shape handling. SQL Server collation and trailing-space comparisons can affect textual identity. Choose and test explicit transaction-ID comparison semantics; reject blank IDs at the application boundary. Use SQL Server `BIGINT` for Java `long` rial amounts and balances, with one unit equal to one rial, and category-aware balance checks and positive posting amounts. Validate input before conversion; database numeric coercion alone is not an API validation contract.

Normal row CHECK constraints cannot verify a transfer across two account rows, ownership in another table, or balanced totals over multiple postings. Do not label such invariants “database CHECK enforced.” Double-entry is required by ADR-007. Enforce balanced construction and transactional persistence, restrict alternate writers, and reconcile postings against cached balances.

## Index review before adding indexes

For every candidate record: exact query and phase, equality/range/sort predicates, expected data distribution, proposed column order, existing PK/UNIQUE indexes, write frequency, storage and the plan/benchmark link. Avoid an extra index duplicating the transaction-ID unique index. Review foreign-key indexes for the actual parent update/delete and concurrency workload; do not assume every FK has an automatically created index.

Candidates to evaluate: account PK lookup/lock; transaction-ID unique lookup; owner/account keyset history; pending outbox by status/next-attempt/time; consumer event identity; executed intent uniqueness. Later-phase candidates are not all migration-V1 requirements. Status-only indexes may have poor selectivity. A history index that accelerates reads adds a write for every history insert. An outbox status index is also maintained when status changes.

## Measure overhead rather than assign a percentage

Use a disposable local benchmark schema and synthetic data. Keep the correctness constraints in the shipping schema. To isolate a constraint's cost, compare controlled schema variants in the disposable benchmark, record accompanying index/plan changes, and restore from a reproducible fixture. A UNIQUE comparison changes both integrity checks and the supporting index; report that combined effect instead of attributing all cost to a CHECK.

For each variant keep hardware, versions, row count, data skew, request mix, durability settings, connection pool, concurrency and statistics comparable. Warm up, repeat runs and report spread. Test independent accounts and a hot-account distribution separately; also compare unique requests and duplicate-heavy requests. Do not combine lock-wait differences with an alleged per-row constraint cost.

Measure committed financial operations/second, end-to-end p50/p95/p99, failures and rejection rate, DB CPU, logical reads, log bytes per operation, lock waits, pool waits and index size. Compare equal successful work; a system that rejects more requests can appear deceptively fast. Use `overhead_pct = (variant_latency - baseline_latency) / baseline_latency * 100` only for matched latency measurements and show sample counts/spread. Throughput delta is a separate metric.

Report template: experiment ID; date/commit; invariant; variants; SQL/DDL diff; fixed workload; hardware/version; warmup/repeats; raw results; percentage with spread; correctness result; confounders; decision; evidence path. Initial result for every constraint: **not measured**. Set numeric performance targets after recording a baseline and the user's target environment, not as invented production SLOs.

Sources: [SQL Server constraints](https://learn.microsoft.com/en-us/sql/relational-databases/tables/unique-constraints-and-check-constraints), [SQL Server optimizer statistics](https://learn.microsoft.com/en-us/sql/relational-databases/statistics/statistics). Recheck against the pinned database version.

## Four-table review checklist for option E

- `journals.operation_id` is mandatory and unique. Committed success has exactly one complete journal; rejected operations have none. Review this cross-row protocol explicitly, including internal opening operations.
- Postings require journal/account foreign keys, positive BIGINT amounts, explicit DEBIT/CREDIT sides and currency-consistent account references. Review composite keys or equivalent safeguards for currency matching. No cascading deletion of posted history.
- A journal has at least two lines, both sides and equal totals per currency. These are cross-row invariants, not ordinary row CHECK guarantees. Restrict alternate writes; test the controlled posting path and attempted history updates/deletes.
- Customer liability projections cannot go below zero. Signed synthetic funding/clearing balances follow the documented account policy. Check every projected balance for overflow before applying any delta.
- Opening balances use internal operation/journal/posting records. Reconcile each projection from the complete immutable history with a consistent committed view and an exact wider accumulator. Inject corruption in isolated tests and detect it independently.
- Review posting access by account and stable journal/line identity, unique operation-to-journal lookup, FK indexes, reconciliation joins and append-only permissions. Measure storage, log bytes, lock duration and shared funding-row contention before choosing additional indexes.

Request versus posting references: requested account IDs in operations must remain recordable for terminal missing-account rejection, so they are not mandatory account foreign keys. Postings reference real accounts through mandatory foreign keys; no cascading deletion of accounting history. See Requirements.md and the CBS03 protocol.

## CBS02–04 review update, 2026-10-23

The shared cap and retained over-cap rejection are now [accepted business policy](Amount-limits.md). Four additional real JDBC cases bring the latest profile run to 240 portable plus 9 SQL Server tests, zero failures/errors/skips. See [observations, limits and remaining migration gates](Persistence-review.md). These experiments do not introduce a business schema or complete the review. Earlier CBS08 counts remain historical evidence.

## Transaction-ID review checkpoint, 2026-10-23

The tested engineering candidate uses nvarchar(256) with explicit BIN2 collation, NOT NULL, 1–128-character length/allowlist checks and named global uniqueness. Unicode JDBC binding preserves invalid input for rejection. Thirty-one new real cases include exhaustive single-BMP-code-unit predicate checking and competing inserts. Full Java 21 profile verification passed 240 portable plus 40 SQL Server tests, zero failures/errors/skips, in 12.756 seconds; the new class took 2.091 seconds. See [design, SQL, examples and limitations](Transaction-id-storage.md). Earlier counts are historical. No business migration, runtime repository or durable replay is added. Next review is the UUID account-lock adapter and complete four-table transaction/permissions contract; formal learner acceptance remains open.
