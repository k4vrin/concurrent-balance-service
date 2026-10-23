# Concurrent Balance Service

[Public repository](https://github.com/k4vrin/concurrent-balance-service)
[فارسی](README.fa.md)

Java 21 / Spring Boot 4.1.1 / Maven / SQL Server target.

An incremental financial-service project starting from MOFID's concurrent balance challenge. The planned core supports credit, debit, atomic transfer and authoritative balance lookup. Later phases add transaction confirmation, Redis response replay, Kafka history events and Debezium CDC. Future FX integration is a design direction, not an implemented integration.

The first functional version delivers credit, debit, transfer and balance lookup through a JSON HTTP API backed by SQL Server with Docker Compose infrastructure. The Java interface is internal bootstrap scaffolding. See the [V1 API contract](docs/en/API-v1.md). JWT/ownership follows before shared or external access; the initial API is local development only.

## Current state

CBS05 has started with immutable `Money(long amount, Currency currency)`. For IRR, 100 means 100 rials directly. Money permits zero and signed values; commands, postings and account categories will enforce their respective sign and business-limit rules. Money supports same-currency checked addition/subtraction and comparison. AccountCategory now validates customer nonnegative balances and signed synthetic funding/clearing balances. Immutable Account and UUID-backed AccountId enforce the balance policy at construction and checked balance changes. AccountLockOrder supplies the shared descending unsigned UUID comparator; database locking is not implemented. Posting and Journal now validate positive accounting lines, category-dependent deltas and complete single-currency balance with exact totals. Journal.projectBalances now prepares validated net account snapshots with exact accumulation. Financial use cases and persistence remain pending.

Implemented: official Spring Initializr bootstrap adapted to reproducible Maven coordinates, application entry point, the four-operation Java interface, infrastructure-free HTTP health smoke test, default SQL Server persistence dependencies, optional Redis/Kafka/JWT dependency profiles, externalized configuration, CI definition and bilingual design documents.

Not implemented: financial operation logic, account schema, durable idempotency, access/ownership policies, OTP, Redis caching behavior, outbox, producer/consumer, CDC or FX settlement. There is no financial HTTP endpoint. No SQL Server instance has been provisioned or validated. A passing bootstrap test does not certify financial correctness.

## Build and run

Normal application startup requires a reachable SQL Server database and exported `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`. No SQL Server Maven or Spring profile is needed. Portable `verify` uses a test-only `smoke` Spring profile that excludes persistence auto-configuration; it does not test database access.

Use a JDK 21 installation and set `JAVA_HOME` accordingly. The build rejects other major JDK versions. Maven is downloaded by the included wrapper; no system Maven or Docker is required for bootstrap.

```sh
./mvnw verify
./mvnw spring-boot:run
curl http://127.0.0.1:8080/actuator/health
```

Windows PowerShell:

```powershell
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

The default server binds to loopback and exposes health/info only. The health response includes `"status":"UP"`; additional metadata may be present. SQL Server is required for normal startup. Redis and Kafka remain optional. Run the startup and curl examples above only after configuring the database. First build needs internet access for Maven dependencies. Environment variables in `.env.example` are a reference; Spring Boot does not automatically load `.env`.

## Staged dependencies

Maven `-P` controls which integration libraries are packaged. Spring runtime profiles control their configuration. This pairing applies to optional Redis, Kafka and security integrations. SQL Server libraries and connection settings are included by default. A runtime profile alone does not add missing libraries, and an optional dependency profile alone can activate unconfigured auto-configuration.

| Maven profile / matching Spring profile | Included libraries | Current meaning |
| --- | --- | --- |
| Default, no profile | Web MVC, Validation, Actuator, Data JPA, Flyway, SQL Server Flyway module, Microsoft JDBC, matching test starters | SQL Server application; no business schema yet |
| `redis` | Spring Data Redis and test starter | Future completed-outcome replay cache |
| `kafka` | Spring Kafka and test starter | Future outbox publisher and history consumer |
| `security` | OAuth2 resource-server and test starter | Future JWT integration; no finished ownership/authorization policy |

Compile/package all staged dependencies without attempting external integration tests:

```sh
./mvnw -Predis,kafka,security -DskipTests package
```

This is dependency/build verification only. The default `verify` runs the bootstrap HTTP smoke test. Real SQL Server and broker fault tests will be added in their implementation phases; H2 is not substituted as evidence.

After separately preparing an empty development database and exporting `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`, the explicit persistence startup command is:

```sh
./mvnw spring-boot:run
```

No migration has been written. Flyway may initialize its history table in the supplied development database, but that does not implement the account schema. Keep `ddl-auto=validate`; review constraints before V1. A packaged JAR uses the same database environment variables without a SQL Server profile. Do not point bootstrap experiments at an existing business database.

## Verification

[Bootstrap evidence](docs/en/Bootstrap-evidence.md): clean Java 21 verification passed with one HTTP smoke test. All optional dependency profiles also packaged successfully in staging with tests skipped. External services remain unverified.

## Documentation

- [Journal record and balance projection walkthrough](docs/en/Journal-projection.md) / [فارسی](docs/fa/Journal-projection.md)

- [Debit and credit across account types](docs/en/Debit-and-credit.md) / [فارسی](docs/fa/Debit-and-credit.md)

- [UUID bits and account lock ordering](docs/en/UUID-lock-order.md) / [فارسی](docs/fa/UUID-lock-order.md)

- [Accepted application architecture and examples](docs/en/Architecture.md) / [فارسی](docs/fa/Architecture.md)

- [Requirements and failure matrix](docs/en/Requirements.md) / [فارسی](docs/fa/Requirements.md)
- [Durable identity protocol](docs/en/Idempotency-protocol.md) / [فارسی](docs/fa/Idempotency-protocol.md)
- [Transaction and locking protocol](docs/en/Transaction-protocol.md) / [فارسی](docs/fa/Transaction-protocol.md)

- [Spring starter and dependency decisions](docs/en/Starter.md) / [فارسی](docs/fa/Starter.md)
- [Local infrastructure with Docker Compose](docs/en/Docker.md) / [فارسی](docs/fa/Docker.md)
- [Optional native SQL Server on Windows](docs/en/Windows-sqlserver.md) / [فارسی](docs/fa/Windows-sqlserver.md)
- [Roadmap and acceptance gates](docs/en/Roadmap.md) / [فارسی](docs/fa/Roadmap.md)
- [Architecture decision register](docs/en/Decisions.md) / [فارسی](docs/fa/Decisions.md)
- [Database constraints and cost review](docs/en/Database-review.md) / [فارسی](docs/fa/Database-review.md)
- [SQL Server actual plans and throughput](docs/en/Query-performance.md) / [فارسی](docs/fa/Query-performance.md)
- [Git and bilingual documentation workflow](docs/en/Workflow.md) / [فارسی](docs/fa/Workflow.md)
- [Accepted bootstrap decision](docs/adr/ADR-000-bootstrap.en.md) / [فارسی](docs/adr/ADR-000-bootstrap.fa.md)

The repository is authoritative for the evolving implementation plan. Earlier vault documents retain discussion history. ADR-007 accepts the ledger architecture; detailed financial protocols remain proposed pending review. No constraint-overhead or throughput numbers have been measured. The next milestone is accepting the P1 transaction/constraint protocol and writing its SQL Server migration, then implementing and testing all four operations.

## Accepted V1 ledger design

The user selected option E on 2026-10-22. V1 includes `accounts`, `operations`, `journals` and `postings`, immutable balanced accounting history and synchronous balance projections committed in one SQL Server transaction. Credit/debit use funding counterparts and nonzero initialization uses opening journals. See [Ledger design](docs/en/Ledger.md) and [ADR-007](docs/adr/ADR-007-double-entry-ledger.en.md). The design is accepted; financial persistence, schema and SQL Server evidence remain absent. Detailed constraint/transaction review still precedes business migrations.
