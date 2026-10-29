# Concurrent Balance Service

[![Build](https://github.com/k4vrin/concurrent-balance-service/actions/workflows/build.yml/badge.svg)](https://github.com/k4vrin/concurrent-balance-service/actions/workflows/build.yml)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.1.1-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![SQL Server](https://img.shields.io/badge/SQL_Server-2022-CC2927)](https://www.microsoft.com/sql-server)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A Java 21 / Spring Boot service for crediting, debiting and transferring whole rials safely under concurrent requests. SQL Server keeps the authoritative balance, double-entry history and original result of every completed financial command together.

If a transfer commits but its HTTP response is lost, retrying the same `transactionId` returns the original response without moving the money again. If a debit is rejected for insufficient funds, that decision still replays after a later top-up.

[فارسی](README.fa.md) · [Run the HTTP samples](docs/en/HTTP-samples.md) · [API reference](docs/en/API-v1.md) · [Documentation index](docs/README.md)

## What is implemented

The selected **CBS20/G1 core is complete**: four HTTP operations, durable success/rejection replay, atomic ledger writes, ordered account locks, bounded whole-transaction recovery and independent reconciliation. The [acceptance guide](docs/en/Core-acceptance.md) records **421 portable + 212 real SQL Server tests** and separate packaged-process rollback/restart evidence. [Verified core CI run](https://github.com/k4vrin/concurrent-balance-service/actions/runs/40886956745).

The application is intended for a **trusted local operator** and binds to `127.0.0.1:8080` by default. Authentication, account ownership and funding privileges must be added before shared or external access. Performance benchmarks, OTP, Redis, Kafka/outbox, CDC and FX are later scope.

| Operation | Endpoint | Effect |
| --- | --- | --- |
| Credit | `POST /api/v1/accounts/{accountId}/credits` | Increase a customer balance with a server-selected funding counterpart. |
| Debit | `POST /api/v1/accounts/{accountId}/debits` | Decrease a customer balance when funds are sufficient. |
| Transfer | `POST /api/v1/transfers` | Move funds between two different customer accounts atomically. |
| Balance | `GET /api/v1/accounts/{accountId}/balance` | Read the committed balance and currency. |

## Get started

Requirements: **JDK 21**, Docker with Compose for SQL Server, and Bash for the local setup commands. The Maven wrapper is included; Python 3 is needed only for the automated core-acceptance harness. SQL Server containers target Linux x86-64; the documented ARM64 emulation experiment is unsupported by Microsoft.

```bash
git clone https://github.com/k4vrin/concurrent-balance-service.git
cd concurrent-balance-service
./mvnw verify
```

This runs the portable suite without a database. On Windows, use `.\mvnw.cmd`; see the [Windows SQL Server guide](docs/en/Windows-sqlserver.md) for its separate setup path.

To run the actual API, follow [the local HTTP walkthrough](docs/en/HTTP-samples.md). It covers the complete sequence:

1. Copy `.env.example` to the ignored `.env.sqlserver`, set distinct secrets and accept the SQL Server Developer license.
2. Start the pinned SQL Server container, provision separate identities, migrate `cbs_dev`/`cbs_test` and apply runtime grants.
3. Run [the demo fixture](http/create-demo-accounts.sql) as the restricted initializer. It creates only zero-balance accounts and never resets balances or history.
4. Launch the application with the runtime database credentials, `EXECUTION_POLICY_ENABLED=true` and the configured funding account.
5. Open [balance-service.http](http/balance-service.http) in IntelliJ IDEA HTTP Client or VS Code REST Client and run the numbered requests.

Spring Boot does **not** load `.env.sqlserver` automatically. A normal startup needs `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`; the walkthrough supplies the exact launch commands. Mutations return 503 while execution is disabled. There is no public account-creation endpoint.

## Try a transfer

After the walkthrough has provisioned and credited the demo accounts, send:

```http
POST http://127.0.0.1:8080/api/v1/transfers
Content-Type: application/json

{
  "transactionId": "demo-v1-transfer",
  "sourceAccountId": "10000000-0000-4000-8000-000000000001",
  "destinationAccountId": "20000000-0000-4000-8000-000000000002",
  "amountRial": 250,
  "currency": "IRR"
}
```

A committed transfer returns HTTP 200:

```json
{"transactionId":"demo-v1-transfer","operationType":"TRANSFER","currency":"IRR","status":"SUCCEEDED"}
```

Send the same body again to replay that result. Changing the amount under the same ID returns HTTP 409 `TRANSACTION_ID_CONFLICT`. The request file also demonstrates insufficient-funds rejection after top-up, missing accounts, self-transfer, fractional amounts and the transaction cap, with expected responses beside each request.

## Money and consistency rules

- **One unit is one Iranian rial.** `amountRial` and `balanceRial` use integer units, Java `long` and SQL Server `BIGINT`. Fractions, exponent tokens, numeric strings and silent rounding are rejected.
- **Initial policy: IRR only.** The default transaction and customer-balance caps are each **10,000,000,000,000 rials**. Checked arithmetic also protects representation limits.
- **One global request identity.** Transaction IDs are exact, case-sensitive strings. A matching retry replays the retained result; a different operation or payload conflicts.
- **One financial commit.** Each success commits a balanced journal, its postings, all affected balance projections and the retained response together. A business rejection retains an operation with no journal or balance changes.
- **Shared lock order.** All mutations acquire their full participant set in the same unsigned descending UUID order before funds checks. Credit/debit include the configured funding account; transfer has no funding leg.
- **Conservative recovery.** Known rollback and uncertain commit have different recovery paths. A 503 or lost response does not establish that nothing committed; recover with the same transaction ID.

## Architecture

One Spring Boot application and one SQL Server database form the consistency boundary. Controllers call use-case handlers directly. Handlers coordinate domain calculations and persistence through application ports; the shared coordinator owns transaction completion and recovery.

```text
src/main/java/dev/kavrin/balance/
  account/      Account domain, balance query and account persistence
  ledger/       Journals, postings and projection calculations
  money/        Integer money representation and checked arithmetic
  operation/    Commands, handlers, identity, retry, HTTP and persistence adapters
src/main/resources/db/migration/  Reviewed Flyway schema
src/test/                        Portable and real SQL Server verification
http/                            Executable requests and zero-account demo fixture
scripts/                         Database setup and packaged-process acceptance
```

| Table | Responsibility |
| --- | --- |
| `accounts` | Account identity/category/currency and synchronous balance projection. |
| `operations` | Canonical request, durable identity, policy evidence and retained result. |
| `journals` | One accounting header for each successful operation. |
| `postings` | Immutable debit/credit history from which balances are reconstructed. |

The runtime identity cannot create schema or update/delete posting history. Flyway runs separately with migration credentials; zero-account initialization has its own identity. See [schema and permissions](docs/en/Schema.md), [the ledger](docs/en/Ledger.md) and [the architecture walkthrough](docs/en/Hexagonal-architecture.md).

## Verification

| Command | What it checks | Requirements |
| --- | --- | --- |
| `./mvnw verify` | Portable domain, application and HTTP-boundary tests. | JDK 21. |
| `./mvnw -Psqlserver-it verify` | Portable suite plus real SQL Server schema, permission, atomicity, replay, concurrency and recovery tests. | Migrated `cbs_test` and all three explicit test credential groups from the [schema guide](docs/en/Schema.md). |
| `python3 scripts/verify-core.py --env-file .env.sqlserver` | Full suite, then a separate packaged JVM with real lock-timeout rollback, same-ID retry, restart/replay and independent JDBC reconciliation. | JDK 21, Python 3 and the configured SQL Server environment. |

GitHub Actions runs portable verification, compilation with optional dependency profiles, and disposable SQL Server/package acceptance. Missing database settings fail integration verification rather than silently skipping it. The recorded baseline passed **633 tests with zero failures/errors/skips**; detailed counts and evidence limits live in [Core acceptance](docs/en/Core-acceptance.md). Timing is not a throughput or production-readiness claim.

## Documentation

Start with [HTTP samples and local startup](docs/en/HTTP-samples.md), [the current API contract](docs/en/API-v1.md) and [the documentation index](docs/README.md). For design and review, use [transaction/replay semantics](docs/en/Transaction-protocol.md), [journal projection](docs/en/Journal-projection.md), [recovery evidence](docs/en/Recovery-acceptance.md) and [core acceptance](docs/en/Core-acceptance.md). The [roadmap](docs/en/Roadmap.md) separates completed core scope from later extensions; dated evidence pages preserve historical checkpoints.

Contributions follow [the engineering and Git workflow](docs/en/Workflow.md) and [AGENTS.md](AGENTS.md). English and Persian guides are maintained together.

## License

[MIT](LICENSE). SQL Server and other dependencies retain their own licenses. SQL Server Developer edition is used for development/testing under its applicable license.
