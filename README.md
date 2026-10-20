# Concurrent Balance Service

[Public repository](https://github.com/k4vrin/concurrent-balance-service)
[فارسی](README.fa.md)

Java 21 / Spring Boot 4.1.1 / Maven / SQL Server target.

An incremental financial-service project starting from MOFID's concurrent balance challenge. The planned core supports credit, debit, atomic transfer and authoritative balance lookup. Later phases add transaction confirmation, Redis response replay, Kafka history events and Debezium CDC. Future FX integration is a design direction, not an implemented integration.

## Current state

Implemented: official Spring Initializr bootstrap adapted to reproducible Maven coordinates, application entry point, the four-operation Java interface, infrastructure-free HTTP health smoke test, SQL Server/Redis/Kafka/JWT dependency profiles, externalized configuration, CI definition and bilingual design documents.

Not implemented: financial operation logic, account schema, durable idempotency, access/ownership policies, OTP, Redis caching behavior, outbox, producer/consumer, CDC or FX settlement. There is no financial HTTP endpoint. No SQL Server instance has been provisioned or validated. A passing bootstrap test does not certify financial correctness.

## Build and run

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

The default server binds to loopback and exposes health/info only. The health response includes `"status":"UP"`; additional metadata may be present. No SQL Server, Redis or Kafka connection is required in the default dependency set. First build needs internet access for Maven dependencies. Environment variables in `.env.example` are a reference; Spring Boot does not automatically load `.env`.

## Staged dependencies

Maven `-P` controls which integration libraries are packaged. Spring runtime profiles control their configuration. Use both together for a deliberate integration run; a runtime profile alone does not add missing libraries, and a dependency profile alone can activate unconfigured auto-configuration.

| Maven profile / matching Spring profile | Included libraries | Current meaning |
| --- | --- | --- |
| Default, no profile | Web MVC, Validation, Actuator, matching test starters | Runnable bootstrap |
| `sqlserver` | Data JPA, Flyway starter, SQL Server Flyway module, Microsoft JDBC, matching test starters | Persistence dependencies/configuration, no business schema |
| `redis` | Spring Data Redis and test starter | Future completed-outcome replay cache |
| `kafka` | Spring Kafka and test starter | Future outbox publisher and history consumer |
| `security` | OAuth2 resource-server and test starter | Future JWT integration; no finished ownership/authorization policy |

Compile/package all staged dependencies without attempting external integration tests:

```sh
./mvnw -Psqlserver,redis,kafka,security -DskipTests package
```

This is dependency/build verification only. The default `verify` runs the bootstrap HTTP smoke test. Real SQL Server and broker fault tests will be added in their implementation phases; H2 is not substituted as evidence.

After separately preparing an empty development database and exporting `DB_URL`, `DB_USERNAME` and `DB_PASSWORD`, the explicit persistence startup command is:

```sh
./mvnw -Psqlserver spring-boot:run -Dspring-boot.run.profiles=sqlserver
```

No migration has been written. Flyway may initialize its history table in the supplied development database, but that does not implement the account schema. Keep `ddl-auto=validate`; review constraints before V1. For a packaged integration JAR use `--spring.profiles.active=sqlserver` with the same environment. Do not point bootstrap experiments at an existing business database.

## Verification

[Bootstrap evidence](docs/en/Bootstrap-evidence.md): clean Java 21 verification passed with one HTTP smoke test. All optional dependency profiles also packaged successfully in staging with tests skipped. External services remain unverified.

## Documentation

- [Spring starter and dependency decisions](docs/en/Starter.md) / [فارسی](docs/fa/Starter.md)
- [Native SQL Server on Windows](docs/en/Windows-sqlserver.md) / [فارسی](docs/fa/Windows-sqlserver.md)
- [Roadmap and acceptance gates](docs/en/Roadmap.md) / [فارسی](docs/fa/Roadmap.md)
- [Architecture decision register](docs/en/Decisions.md) / [فارسی](docs/fa/Decisions.md)
- [Database constraints and cost review](docs/en/Database-review.md) / [فارسی](docs/fa/Database-review.md)
- [SQL Server actual plans and throughput](docs/en/Query-performance.md) / [فارسی](docs/fa/Query-performance.md)
- [Git and bilingual documentation workflow](docs/en/Workflow.md) / [فارسی](docs/fa/Workflow.md)
- [Accepted bootstrap decision](docs/adr/ADR-000-bootstrap.en.md) / [فارسی](docs/adr/ADR-000-bootstrap.fa.md)

The repository is authoritative for the evolving implementation plan. Earlier vault documents retain discussion history. Financial design ADRs remain proposed. No constraint-overhead or throughput numbers have been measured. The next milestone is accepting the P1 transaction/constraint protocol and writing its SQL Server migration, then implementing and testing all four operations.
