# ADR-006: Include SQL Server in the default application

Status: Accepted configuration change, 2026-10-22, requested by the user. [فارسی](ADR-006-default-sqlserver.fa.md)

This decision supersedes only ADR-000's optional SQL Server dependency/runtime profiles and infrastructure-free normal startup. Java 21, the Maven wrapper and the later optional integration stages remain unchanged. V1 delivers an HTTP API backed by SQL Server, so requiring two separate switches for its mandatory persistence stack adds unnecessary configuration.

Move JPA, Flyway, the SQL Server Flyway module, the Microsoft JDBC driver and matching test starters into normal Maven dependencies. Move database settings into application.yaml and delete application-sqlserver.yaml. Normal startup requires DB_URL, DB_USERNAME and DB_PASSWORD without Maven/Spring SQL Server profiles. Preserve schema validation and enabled migrations; no business migration is introduced.

The existing HTTP smoke test explicitly activates a smoke Spring profile stored only in src/test/resources. That profile excludes JDBC, Hibernate, JPA repository and Flyway auto-configuration. It checks HTTP server wiring and health only. Real database integration tests must use their own datasource configuration without these exclusions and fail when SQL Server is unavailable. No H2 fallback or persistence-correctness claim follows from the smoke test.

Redis, Kafka and security retain their optional Maven and Spring profiles. CI and current commands drop the SQL Server profile argument. Historical bootstrap evidence retains its original commands with a supersession note. Costs: normal startup now needs the database environment and a reachable server; portable tests require explicit isolation. Reversing this configuration requires coordinated dependency, runtime, test and documentation changes.

Validation results and remaining infrastructure limitations are recorded in [Bootstrap evidence](../en/Bootstrap-evidence.md).
