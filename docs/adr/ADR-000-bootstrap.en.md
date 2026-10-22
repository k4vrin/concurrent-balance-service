# ADR-000: Bootstrap and staged integrations

Status: Accepted for initial scaffolding. Date: 2026-10-20. [فارسی](ADR-000-bootstrap.fa.md)

SQL Server dependency/runtime profile and normal-startup portions are superseded by [ADR-006](ADR-006-default-sqlserver.en.md), 2026-10-22. The original decision below is retained as history.

Use Java 21, Maven wrapper, Spring Boot 4.1.1 and SQL Server as the persistence target. Keep SQL Server, Redis, Kafka and JWT integration libraries in explicit Maven profiles. Default startup runs a loopback HTTP health endpoint with no external service. This supports incremental delivery and prevents infrastructure availability from obscuring whether the starter itself builds.

Maven profiles alter packaged dependencies; Spring profiles alter runtime configuration. Document both. Dependencies and configuration are preparation, not implemented financial features. Redis remains a copy of durable outcomes, Kafka remains asynchronous, OTP requires authenticated intent binding, and Debezium is deferred external infrastructure. Financial protocol/schema decisions remain Proposed in the decision register.

Costs: profile commands are longer, and each integration classpath needs its own build/test gates. The default smoke test cannot certify the integrated classpath or real SQL Server behavior. CI compiles all staged libraries separately; real integration tests arrive with their features. Do not introduce H2 or silently disable migrations to call SQL Server tests passed.

Spring Initializr generated an unavailable 4.1.1.RELEASE coordinate; normalize to published 4.1.1 and verify the resulting build. Source details are in the starter guide. Evidence status is recorded in the bootstrap evidence file after verification, not inferred from this decision.
