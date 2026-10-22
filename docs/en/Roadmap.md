> Bootstrap update, 2026-10-20: SQL Server replaces the earlier proposed Oracle target. Phase P0 is started, not complete. Dependencies and a health smoke test do not prove financial correctness.

# Incremental roadmap

Status: proposed implementation plan, 2026-10-20. [فارسی](../fa/Roadmap.md) · [Index](../../README.md)

## Sequence and release gates

A phase is complete only when its code, tests, migrations where applicable, and both language documents agree. No phase below is implemented yet. Estimates will follow repository setup and the first working vertical slice.

| Phase | Deliverable | Required evidence before moving on | Example branch |
| --- | --- | --- | --- |
| P0 | Requirements, threat assumptions, ADR review, pinned toolchain, repository skeleton and CI | Four-operation contract; transaction ID policy; failure matrix; database choice; invariant and query inventory; wrapper runs on Java 21 | `docs/balance-service-design` |
| P1 | V1 JSON HTTP API for all four operations backed by SQL Server; Docker setup and account initialization | All four operations; checked integer money; atomic transfer; ordered row locks; durable operation identity; DTO validation; stable errors; HTTP-to-database, unit and real SQL Server integration tests | `feature/core-balance-service` |
| P2 | SQL correctness and query baseline | Direct-SQL constraint tests; rollback evidence; actual cursor plans for hot queries; baseline mixed and hot-account load report | `test/database-correctness-baseline` |
| P3 | Authenticated access and API security hardening | Request validation; owner/role checks; cross-user replay denied; credit/debit funding privileges; error contract; bypass tests | `feature/balance-http-api` |
| P4 | Transaction-bound one-time transfer confirmation | Immutable intent; expiration and attempts; atomic consumption with transfer; replay and concurrent-confirmation tests; no secret logging | `feature/transfer-confirmation` |
| P5 | Redis completed-response storage | Fingerprint validation; database fallback; Redis outage/flush/expiry tests; two-instance duplicate tests; hit-rate and latency comparison | `feature/redis-idempotency-cache` |
| P6 | Transactional outbox, Kafka producer and history consumer | Broker outage recovery; publish/mark crash test; duplicate-safe consumer; offset-after-DB-commit behavior; retries and dead-letter handling | `feature/transaction-events` |
| P7 | Debezium CDC feasibility and migration | Exact SQL Server/connector compatibility, permissions, native CDC, SQL Server Agent and change-table retention review; snapshot/restart tests; one active publisher ownership policy | `feature/outbox-cdc` |
| P8 | Final hardening and interview delivery | Regression and fault suite; plans after all indexes/features; throughput report; reproducible startup; bilingual release notes and limitations | `docs/release-readiness` |

P1 should be split into small PRs: HTTP contract and adapter foundation, schema, balance lookup, credit, debit, transfer and concurrency/API tests. Each financial operation is an HTTP-to-database slice. P6 should be split into outbox, publisher, consumer, and failure tests. The branch names are examples, not branches already created.

## Challenge baseline

V1 delivers `credit`, `debit`, `transfer`, and `getBalance` semantics through the [HTTP API](API-v1.md). The existing Java interface is internal scaffolding; its signatures may evolve and are not the release boundary. String account IDs may map internally to typed identifiers. Use `long` minor units, one explicit currency initially, positive amounts, and overflow rejection. Seed balances are fixture/bootstrap behavior and must be documented. A successful transfer moves the exact same amount between two existing, distinct accounts.

All operation types share a global transaction ID namespace for this single-tenant baseline. A different payload or operation type under an existing ID conflicts. Fingerprints include operation type, account IDs, amount, currency and authenticated actor when authentication exists; exclude OTP secrets, retry timestamps, and transport correlation IDs. Bind current authorization before returning a stored result. Define business rejection replay and transient failure retry separately in ADR-002.

P1 tests include simultaneous credits, competing debits, mixed credit/debit/transfer activity, simultaneous duplicates for each mutation, same ID/different request, opposite-direction transfers, independent-account progress while another account is locked, unknown accounts, invalid amounts, self-transfer, overflow and failure rollback. Use barriers/latches, bounded waits, and worker exception collection. A load test does not prove correctness for every possible interleaving.

## Extension boundaries

V1 includes HTTP integration tests alongside service and SQL tests. Initially it is a local loopback API for a trusted development operator. P3 adds authentication, ownership and role checks before shared or external access; credit/debit privileges and replay authorization are required at that gate. P4 adds confirmation to that boundary without allowing HTTP callers to bypass it through another endpoint. Credit/debit endpoints become privileged operations before customer access.

The OTP step authorizes a particular intent; it does not replace idempotency, account ownership, or available-funds validation. Preparing an intent does not reserve funds in the proposed baseline. Recheck balance at confirmation; insufficient funds can therefore reject confirmation.

Redis accelerates immutable completed outcomes. It never decides whether money movement is new. Kafka carries committed facts; it does not execute the synchronous balance operation. Its history view is eventually consistent and is not the source for the authoritative balance endpoint.

Debezium is deliberately later than the polling outbox. SQL Server CDC may require substantial setup. P7 is a feasibility gate, not a promise to introduce unsupported connector/database combinations. Keep the tested polling publisher if the gate fails, and record the reason.

## First concrete milestone

Review ADR-001 through ADR-003 and the database-review table before writing migration V1. Use the existing Java 21/Spring Boot/Maven bootstrap, define the HTTP contract and build durable balance lookup, credit, debit and transfer paths through controllers to SQL Server. Tag the full tested P1/P2 baseline separately so optional infrastructure cannot obscure challenge completion.
