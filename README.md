# Concurrent Balance Service

[![Build](https://github.com/k4vrin/concurrent-balance-service/actions/workflows/build.yml/badge.svg)](https://github.com/k4vrin/concurrent-balance-service/actions/workflows/build.yml)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![SQL Server](https://img.shields.io/badge/SQL_Server-2022-CC2927)](https://www.microsoft.com/sql-server)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A Java and Spring Boot service for moving money safely under concurrent requests. It combines an authoritative balance API, a double-entry ledger and durable request identity in one SQL Server database.

[فارسی](README.fa.md) · [API reference](docs/en/API-v1.md) · [Architecture](docs/en/Architecture.md) · [Setup](docs/en/Schema.md)

## First version

The first version is a local-development JSON API for credit, debit, atomic transfer and balance lookup. Its release contract is described below. For the current checkout's implemented behavior and verification evidence, see [implementation status](docs/en/Implementation-status.md). [Credit classes and request flows](docs/en/Credit-class-walkthrough.md).

| Operation | Endpoint | Responsibility |
| --- | --- | --- |
| Credit | `POST /api/v1/accounts/{accountId}/credits` | Increase a customer's balance using a server-selected funding counterpart. |
| Debit | `POST /api/v1/accounts/{accountId}/debits` | Decrease a customer's balance when sufficient funds are available. |
| Transfer | `POST /api/v1/transfers` | Move an amount between two distinct accounts atomically. |
| Balance | `GET /api/v1/accounts/{accountId}/balance` | Read the authoritative committed balance and currency. |

Amounts are whole Iranian rials. One unit is one rial, represented by Java `long` and SQL Server `BIGINT`. The initial policy permits IRR and caps each mutation and customer balance at **10,000,000,000,000 rials**. Fractions, coercion and silent rounding are rejected.

## Correctness model

Each mutation carries a caller-supplied `transactionId`. A matching retry returns the original retained result; reuse with a different operation or payload conflicts. A lost response is recovered using the same ID. Terminal business rejections are retained too, so retry does not reevaluate them after funds or policy change.

Every successful mutation commits one balanced journal, its immutable debit/credit postings, the affected balance projections and its result in one transaction. Rejection has no journal. Replay creates no additional postings. Nonzero account initialization uses an internal opening journal.

All writers lock the complete participant set in the same application-defined UUID order before checking balances. Checked arithmetic prevents overflow. Deadlocks and lock timeouts require bounded whole-transaction recovery; an uncertain commit requires same-ID recovery. [Transaction protocol](docs/en/Transaction-protocol.md).

The four tables have distinct jobs:

| Table | Purpose |
| --- | --- |
| `accounts` | Identity, account category, currency and synchronous balance projection. |
| `operations` | Global request identity, canonical intent, policy evidence and retained result. |
| `journals` | One accounting header for each successful operation. |
| `postings` | Immutable debit and credit lines from which balances can be reconciled. |

## API example

The release-contract transfer request uses UUID account IDs and an exact, case-sensitive transaction ID:

```http
POST /api/v1/transfers
Content-Type: application/json

{
  "transactionId": "transfer-001",
  "sourceAccountId": "10000000-0000-4000-8000-000000000001",
  "destinationAccountId": "f0000000-0000-4000-8000-000000000002",
  "amountRial": 150000,
  "currency": "IRR"
}
```

Successful mutations acknowledge the committed operation. Errors use `application/problem+json` with stable application codes. The [API reference](docs/en/API-v1.md) defines parsing, outcomes and examples, and the [operation walkthrough](docs/en/Operation-flow.md) explains the complete request flow.

## Build and local setup

Requirements: JDK 21; Docker for the SQL Server integration environment. Maven is supplied by the wrapper.

```bash
./mvnw verify
```

Portable verification requires no database. Follow the [SQL Server setup](docs/en/Docker.md) and [migration runbook](docs/en/Schema.md) to accept the applicable Developer license, provision separate identities and apply the schema. Supply secrets through environment variables or the ignored local configuration. Normal runtime uses restricted `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` settings and does not run migrations.

```bash
./mvnw -Pdb-migrate flyway:migrate flyway:validate
./mvnw -Psqlserver-it verify
./mvnw spring-boot:run
```

The migration and integration commands require their separate explicit environment settings from the runbook. On Windows, use `.\mvnw.cmd`. SQL Server's local ARM64 emulation experiment is unsupported by Microsoft; see [environment evidence](docs/en/SQL-server-evidence.md).

## Scope and extensions

The first version assumes a trusted local operator and binds to loopback. Authentication, account ownership and privileged funding controls are required before shared or external access. No production-readiness or throughput claim is made.

OTP transfer confirmation, Redis replay caching, Kafka/outbox events, Debezium CDC and future FX settlement follow the core release. SQL Server remains the financial authority; caches and asynchronous history do not decide whether money moves.

## Learn the design

- [Durable debit, insufficient funds and SQL Server evidence](docs/en/Debit-execution.md)
- [Hexagonal architecture, ports and adapters through this project](docs/en/Hexagonal-architecture.md)
- [Why canonical requests and fingerprints are separate](docs/en/Canonical-design.md)
- [Double-entry ledger and account categories](docs/en/Ledger.md)
- [Journal calculation and balance projections](docs/en/Journal-projection.md)
- [Database constraints and permissions](docs/en/Schema.md)
- [Roadmap and implementation evidence](docs/en/Roadmap.md)
- [Contribution and Git workflow](docs/en/Workflow.md)

## License

[MIT](LICENSE). SQL Server and other dependencies retain their own licenses.
