> Bootstrap update, 2026-10-20: SQL Server replaces the earlier proposed Oracle target. Phase P0 is started, not complete. Dependencies and a health smoke test do not prove financial correctness.

# Incremental roadmap

Status: proposed implementation plan, 2026-10-20. [فارسی](../fa/Roadmap.md) · [Index](../../README.md)

## Sequence and release gates

A phase is complete only when its code, tests, migrations where applicable, and both language documents agree. No phase below is implemented yet. Estimates will follow repository setup and the first working vertical slice.

| Phase | Deliverable | Required evidence before moving on | Example branch |
| --- | --- | --- | --- |
| P0 | Requirements, threat assumptions, ADR review, pinned toolchain, repository skeleton and CI | Four-operation contract; transaction ID policy; failure matrix; database choice; invariant and query inventory; wrapper runs on Java 21 | `docs/balance-service-design` |
| P1 | V1 JSON HTTP API for all four operations backed by SQL Server; Docker setup, four-table ledger and journal-backed initialization | Balanced immutable journals/postings; atomic projections; independent reconciliation; all four operations; checked integer money; atomic transfer; ordered row locks; durable operation identity; DTO validation; stable errors; HTTP-to-database, unit and real SQL Server integration tests | `feature/core-balance-service` |
| P2 | SQL correctness and query baseline | Direct-SQL constraint tests; rollback evidence; actual cursor plans for hot queries; baseline mixed and hot-account load report | `test/database-correctness-baseline` |
| P3 | Authenticated access and API security hardening | Request validation; owner/role checks; cross-user replay denied; credit/debit funding privileges; error contract; bypass tests | `feature/balance-http-api` |
| P4 | Transaction-bound one-time transfer confirmation | Immutable intent; expiration and attempts; atomic consumption with transfer; replay and concurrent-confirmation tests; no secret logging | `feature/transfer-confirmation` |
| P5 | Redis completed-response storage | Fingerprint validation; database fallback; Redis outage/flush/expiry tests; two-instance duplicate tests; hit-rate and latency comparison | `feature/redis-idempotency-cache` |
| P6 | Transactional outbox, Kafka producer and history consumer | Broker outage recovery; publish/mark crash test; duplicate-safe consumer; offset-after-DB-commit behavior; retries and dead-letter handling | `feature/transaction-events` |
| P7 | Debezium CDC feasibility and migration | Exact SQL Server/connector compatibility, permissions, native CDC, SQL Server Agent and change-table retention review; snapshot/restart tests; one active publisher ownership policy | `feature/outbox-cdc` |
| P8 | Final hardening and interview delivery | Regression and fault suite; plans after all indexes/features; throughput report; reproducible startup; bilingual release notes and limitations | `docs/release-readiness` |

P1 should be split into small PRs: HTTP contract and adapter foundation, schema, balance lookup, credit, debit, transfer and concurrency/API tests. Each financial operation is an HTTP-to-database slice. P6 should be split into outbox, publisher, consumer, and failure tests. The branch names are examples, not branches already created.

## Challenge baseline

V1 delivers `credit`, `debit`, `transfer`, and `getBalance` semantics through the [HTTP API](API-v1.md). The existing Java interface is internal scaffolding; its signatures may evolve and are not the release boundary. String account IDs may map internally to typed identifiers. Use Java `long` rial amounts for IRR, with one unit equal to one rial, positive amounts and overflow rejection; see the accepted monetary policy in Decisions.md. Nonzero fixture/bootstrap balances require internal opening operations with balanced postings, as defined in Ledger.md. A successful transfer moves the exact same amount between two existing, distinct accounts.

All operation types share a global transaction ID namespace for this single-tenant baseline. A different payload or operation type under an existing ID conflicts. Fingerprints include operation type, account IDs, amount, currency and authenticated actor when authentication exists; exclude OTP secrets, retry timestamps, and transport correlation IDs. Bind current authorization before returning a stored result. Define business rejection replay and transient failure retry separately in ADR-002.

P1 tests include simultaneous credits, competing debits, mixed credit/debit/transfer activity, simultaneous duplicates for each mutation, same ID/different request, opposite-direction transfers, independent-account progress while another account is locked, unknown accounts, invalid amounts, self-transfer, overflow and failure rollback. Use barriers/latches, bounded waits, and worker exception collection. A load test does not prove correctness for every possible interleaving.

## Extension boundaries

V1 includes HTTP integration tests alongside service and SQL tests. Initially it is a local loopback API for a trusted development operator. P3 adds authentication, ownership and role checks before shared or external access; credit/debit privileges and replay authorization are required at that gate. P4 adds confirmation to that boundary without allowing HTTP callers to bypass it through another endpoint. Credit/debit endpoints become privileged operations before customer access.

The OTP step authorizes a particular intent; it does not replace idempotency, account ownership, or available-funds validation. Preparing an intent does not reserve funds in the proposed baseline. Recheck balance at confirmation; insufficient funds can therefore reject confirmation.

Redis accelerates immutable completed outcomes. It never decides whether money movement is new. Kafka carries committed facts; it does not execute the synchronous balance operation. Its history view is eventually consistent and is not the source for the authoritative balance endpoint.

Debezium is deliberately later than the polling outbox. SQL Server CDC may require substantial setup. P7 is a feasibility gate, not a promise to introduce unsupported connector/database combinations. Keep the tested polling publisher if the gate fails, and record the reason.

## First concrete milestone

Review ADR-001 through ADR-003 and the database-review table before writing migration V1. Use the existing Java 21/Spring Boot/Maven bootstrap, define the HTTP contract and build durable balance lookup, credit, debit and transfer paths through controllers to SQL Server. Tag the full tested P1/P2 baseline separately so optional infrastructure cannot obscure challenge completion.

## Accepted option E scope, 2026-10-22

P0 now includes accepted ADR-007 plus pending CBS02–04 constraint/identity/transaction review. P1 requires all four ledger tables, accounting domain rules, opening entries, funding counterparts, immutable journals/postings and atomic synchronous projections alongside the four HTTP operations. The P1 gate includes journal counts on replay, absence on rejection, write-boundary rollback and independent reconstruction of every account from postings. P2 adds reconciliation/history query plans, posting/index/log cost and shared funding-account contention. Future FX extends this existing single-currency ledger; it does not introduce the first ledger.

## Remaining route to the first accepted version, 2026-10-23

The first acceptance target is the selected challenge core at CBS20/G1, including CBS23 HTTP delivery. This is our project acceptance gate, not a guarantee of employer approval or production readiness. The selected ledger/SQL/API scope extends the original service challenge. Domain models, validated commands, supported-currency policy snapshots and outcome values exist; HTTP financial execution, schema and real database correctness remain unimplemented.

| Order | Tasks | Remaining deliverable and acceptance evidence |
| --- | --- | --- |
| 1 | CBS23, then CBS06 | Define request/response DTOs, strict JSON/UUID/amount parsing and stable errors; separate HTTP from commands; implement versioned canonical request comparison. Contract tests cannot substitute for durable execution. |
| 2 | CBS07–08 | Docker SQL Server environment and integration harness. Pin the server/driver setup, prove connectivity and positive integration test counts; absent DB must fail rather than skip. Verify ARM64 experiment or use the x86-64 host fallback. |
| 3 | CBS02–04 closure, CBS09 | Finish concrete constraint/transaction review before migration, including business cap/classification, collation, locking SQL and runtime permissions. Create accounts, operations, journals and postings with direct-SQL constraint/history protection tests. |
| 4 | CBS10 | Map all four tables, create journal-backed opening fixtures and expose authoritative balance lookup. Verify committed reads through HTTP and independently reconcile opening history. |
| 5 | CBS11–12 | Implement credit, server-selected funding, guarded TransactionTemplate boundary, durable success response, replay and conflict. Commit one balanced journal and both projections with the outcome; verify restart and same-ID retry. |
| 6 | CBS13–15 | Implement debit and atomic transfer, persist terminal business rejections without journals and classify technical failures. Prove exact-funds success, insufficient-funds replay, ordered locks and rollback after partial writes. Wire each HTTP slice. |
| 7 | CBS16–19 | Prove concurrent distinct commands, duplicate/conflicting races across instances, opposite transfers, independent progress and commit/response recovery. Use real SQL Server and exact operation/journal/posting counts and balance reconstruction. |
| 8 | CBS20 | Run an independent mixed scenario and fault case, finish the Docker startup/test runbook and bilingual limitations, review code/CI evidence and demonstrate all four real HTTP-to-SQL operations. Resolve failed gates before accepting the version. |

At the finish line, credit, debit, transfer and balance lookup work from a documented clean setup. Successful mutations atomically commit immutable balanced history, projections and their durable response. Validation leaves no financial operation, business rejection retains its response without monetary writes, matching retries return the original response, conflicting payloads do not execute, and technical failure/recovery preserves at most one committed effect. Reconciliation independently confirms balances from history under a consistent view.

The initial deployment remains trusted and loopback-only. Authentication/ownership is required before shared or external access. CBS21–22 performance experiments, CBS24–25 security expansion, OTP, Redis replay caching, Kafka/outbox, CDC and FX follow the core gate. Do not make database-backed currency administration a first-release prerequisite: supply the supported set through configuration initially, and add its database source later if needed.

No delivery date is committed. SQL Server compatibility/connectivity is the first infrastructure uncertainty; use observed progress there before estimating calendar time. Test counts describe portable verification, not completion percentage or learner mastery. Latest pre-publication Java 21.0.11 Maven verify passed 150 tests, zero failures/errors/skips in 7.314 seconds. No SQL Server financial evidence yet.

## CBS23 implementation checkpoint, 2026-10-23

The HTTP adapter foundation is implemented with strict DTO parsing, typed application commands, minimal success/query DTOs and stable ProblemDetail mapping. Its fallback returns 503; contract tests use test doubles and do not establish financial correctness. See [evidence](HTTP-adapter-evidence.md). Next is CBS06 versioned canonical request comparison, then CBS07–08 SQL Server setup/harness and CBS02–04 closure before CBS09 business migration. CBS10–20 must replace the fallback with durable slices and prove HTTP-to-SQL Server behavior. Formal task acceptance and independent mastery remain open.

## CBS06 implementation checkpoint, 2026-10-23

Portable version-1 canonical intent and retained-funding fingerprints are implemented. Full Java 21 verify passed 223 tests, zero failures/errors/skips, including 22 canonical cases. See [encoding, comparison and funding replay boundaries](Canonical-request.md). Formal task/learner acceptance remains open. Next implementation is CBS07–08 SQL Server Docker environment and integration harness; CBS02–04 review closure still precedes CBS09 migrations. No HTTP execution, durable replay or SQL Server semantics are proved by this result.

## CBS07 implementation checkpoint, 2026-10-23

Pinned Compose, explicit license/secrets, persistent volume, loopback publishing, readiness, infrastructure-only bootstrap and actual driver probe are implemented. The authorized ARM64 experiment passed startup, dedicated-login JDBC SELECT in separate empty databases and restart checks. See [environment evidence](SQL-server-evidence.md). It does not establish Microsoft ARM64 support, financial behavior or independent acceptance. Next is CBS08 Failsafe sqlserver-it and real rollback/no-DB test evidence; CBS02–04 review still precedes CBS09 business migrations.

## CBS08 implementation checkpoint, 2026-10-23

The sqlserver-it Failsafe profile now requires explicit test-only credentials and checks the real connected cbs_test catalog. Portable verify passed 240 tests; full integration verify passed those plus five real SQL Server tests, all without failures/errors/skips. Missing infrastructure/settings, smoke and wrong-catalog Maven runs failed as intended. Temporary fixtures demonstrate rollback, separately committed multi-connection setup and cleanup without business tables. See [harness evidence](SQL-server-tests.md). Next is concrete CBS02–04 review closure before CBS09 migrations: numeric caps/classification, exact SQL identity comparison, locking/reservation/error/retry SQL and migration/runtime privileges. No financial schema is authorized by these harness results, and independent acceptance remains open.

## CBS02–04 review update, 2026-10-23

The shared cap and retained over-cap rejection are now [accepted business policy](Amount-limits.md). Four additional real JDBC cases bring the latest profile run to 240 portable plus 9 SQL Server tests, zero failures/errors/skips. See [observations, limits and remaining migration gates](Persistence-review.md). These experiments do not introduce a business schema or complete the review. Earlier CBS08 counts remain historical evidence.

## Transaction-ID review checkpoint, 2026-10-23

The tested engineering candidate uses nvarchar(256) with explicit BIN2 collation, NOT NULL, 1–128-character length/allowlist checks and named global uniqueness. Unicode JDBC binding preserves invalid input for rejection. Thirty-one new real cases include exhaustive single-BMP-code-unit predicate checking and competing inserts. Full Java 21 profile verification passed 240 portable plus 40 SQL Server tests, zero failures/errors/skips, in 12.756 seconds; the new class took 2.091 seconds. See [design, SQL, examples and limitations](Transaction-id-storage.md). Earlier counts are historical. No business migration, runtime repository or durable replay is added. Next review is the UUID account-lock adapter and complete four-table transaction/permissions contract; formal learner acceptance remains open.

## CBS02–04 engineering review closed for CBS09, 2026-10-23

[ADR-010](../adr/ADR-010-persistence-contract.en.md) and [CBS09 implementation contract](CBS09-contract.md) consolidate the remaining decisions. Four new real UUID cases verify mapping/BIGINT round trips, application-versus-SQL order, blocking and unrelated-key progress, missing-key creation protection and opposite-direction workers. Full profile verify passed 240 portable plus 44 SQL Server tests, zero failures/errors/skips, in 16.997 seconds. Reviewed choices include explicit native EntityManager lock SQL, four-table keys/row states/currency FKs, policy/response retention, retry settings and separate migration/runtime/initializer authority. Next implementation is CBS09 migration and its actual constraint/permission/Flyway tests, rather than further incremental design rounds. Financial failure tests stay in CBS10–20. No business DDL/grants or learner acceptance were added.
