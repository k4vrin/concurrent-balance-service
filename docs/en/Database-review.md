> Bootstrap update, 2026-10-20: SQL Server replaces the earlier proposed Oracle target. Phase P0 is started, not complete. Dependencies and a health smoke test do not prove financial correctness.

# Database correctness and cost review

Status: pre-schema proposal, no DDL applied and no measurements. [فارسی](../fa/Database-review.md) · [Decisions](Decisions.md)

## Review before migration V1

Start with conceptual `accounts` and `operations`. Later phases add `transfer_intents`, `outbox_events`, `processed_events` and `transaction_history`. Table and column names below are proposed. An operation row represents one credit, debit or complete transfer and its durable outcome. Internal generated row IDs and client transaction IDs are different concepts.

Before accepting V1 settle: SQL Server image/version and portability boundary; identifier representation; a fixed money unit/currency; account initialization authority; global transaction ID scope; maximum value; rejection persistence; retention; account closure/deletion; and whether double-entry history starts now or later. Capture changes in ADRs before migrations.

| Invariant | Proposed safeguard | What it does not prove | Cost hypothesis / evidence needed |
| --- | --- | --- | --- |
| Required identity/amount/outcome fields exist | Named NOT NULL plus application validation | Correct authorization or request meaning | Validation CPU; invalid direct-SQL tests |
| Amount positive and Java-compatible integer | `amount > 0`, integer representation, explicit signed-long upper bound | Safe multi-value arithmetic | Row checks; overflow tests and checked Java arithmetic |
| Balance nonnegative and within long range | NOT NULL and range CHECK | Correct read-check-write concurrency | Per-update validation; direct SQL and concurrent debits |
| One transaction ID across all mutation types | NOT NULL plus global UNIQUE transaction ID | Same payload, caller access or a new ID for the same intent | Unique-index writes, duplicate contention, storage |
| Referenced accounts exist | Named foreign keys for participating accounts | Ownership, active status or currency match | Lookup/index maintenance; FK index review and orphan test |
| Operation shape matches type | Type allowlist and conditional row CHECK for required/forbidden source/destination | Cross-row state | Check evaluation and migration cost for new types |
| Transfer source differs from destination | Conditional CHECK plus service validation | Both updates commit together | Small row check; direct SQL rejection |
| Equal debit and credit in a transfer | One amount and one database transaction covering both accounts | Complete ledger reconciliation | Account writes, lock duration, transaction-log/version-store work; failure injection |
| No lost updates | Ordered account row locks before validation; all balance writers obey protocol | Unlimited throughput on one hot account | Lock waits, connection saturation and hot-account test |
| OTP authorizes one immutable intent | Owner binding, immutable financial fields, intent row lock, unique executed intent linkage | Secure delivery channel by itself | Intent writes and contention; concurrent-confirm tests |
| History consumer applies an event once | Unique `(consumer_name, event_id)` with effect in same transaction | Exactly-once delivery from Kafka | Dedup-index writes, retention and duplicate test |

A CHECK that evaluates to unknown can permit null values; pair required conditions with NOT NULL and explicit operation-shape handling. SQL Server collation and trailing-space comparisons can affect textual identity. Choose and test explicit transaction-ID comparison semantics; reject blank IDs at the application boundary. Use SQL Server `BIGINT` for Java long minor units and CHECK nonnegative balances/positive amounts. Validate input before conversion; database numeric coercion alone is not an API validation contract.

Normal row CHECK constraints cannot verify a transfer across two account rows, ownership in another table, or balanced totals over multiple postings. Do not label such invariants “database CHECK enforced.” If double-entry is added, enforce balanced construction and transactional persistence, restrict alternate writers, and reconcile postings against cached balances.

## Index review before adding indexes

For every candidate record: exact query and phase, equality/range/sort predicates, expected data distribution, proposed column order, existing PK/UNIQUE indexes, write frequency, storage and the plan/benchmark link. Avoid an extra index duplicating the transaction-ID unique index. Review foreign-key indexes for the actual parent update/delete and concurrency workload; do not assume every FK has an automatically created index.

Candidates to evaluate: account PK lookup/lock; transaction-ID unique lookup; owner/account keyset history; pending outbox by status/next-attempt/time; consumer event identity; executed intent uniqueness. Later-phase candidates are not all migration-V1 requirements. Status-only indexes may have poor selectivity. A history index that accelerates reads adds a write for every history insert. An outbox status index is also maintained when status changes.

## Measure overhead rather than assign a percentage

Use a disposable local benchmark schema and synthetic data. Keep the correctness constraints in the shipping schema. To isolate a constraint's cost, compare controlled schema variants in the disposable benchmark, record accompanying index/plan changes, and restore from a reproducible fixture. A UNIQUE comparison changes both integrity checks and the supporting index; report that combined effect instead of attributing all cost to a CHECK.

For each variant keep hardware, versions, row count, data skew, request mix, durability settings, connection pool, concurrency and statistics comparable. Warm up, repeat runs and report spread. Test independent accounts and a hot-account distribution separately; also compare unique requests and duplicate-heavy requests. Do not combine lock-wait differences with an alleged per-row constraint cost.

Measure committed financial operations/second, end-to-end p50/p95/p99, failures and rejection rate, DB CPU, logical reads, log bytes per operation, lock waits, pool waits and index size. Compare equal successful work; a system that rejects more requests can appear deceptively fast. Use `overhead_pct = (variant_latency - baseline_latency) / baseline_latency * 100` only for matched latency measurements and show sample counts/spread. Throughput delta is a separate metric.

Report template: experiment ID; date/commit; invariant; variants; SQL/DDL diff; fixed workload; hardware/version; warmup/repeats; raw results; percentage with spread; correctness result; confounders; decision; evidence path. Initial result for every constraint: **not measured**. Set numeric performance targets after recording a baseline and the user's target environment, not as invented production SLOs.

Sources: [SQL Server constraints](https://learn.microsoft.com/en-us/sql/relational-databases/tables/unique-constraints-and-check-constraints), [SQL Server optimizer statistics](https://learn.microsoft.com/en-us/sql/relational-databases/statistics/statistics). Recheck against the pinned database version.
