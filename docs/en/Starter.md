# Spring starter and dependency guide

[فارسی](../fa/Starter.md)

## Provenance and versions

Generated from [Spring Initializr](https://start.spring.io/) on 2026-10-20 using Maven, Java 21, group `dev.kavrin`, artifact `concurrent-balance-service`, package `dev.kavrin.balance`, JAR packaging, and dependency IDs `web,validation,actuator,data-jpa,flyway,sqlserver,data-redis,kafka,oauth2-resource-server`.

Initializr advertised and generated parent `4.1.1.RELEASE`; Maven Central could not resolve that parent. The project uses the standard published coordinate `org.springframework.boot:spring-boot-starter-parent:4.1.1`. This normalization is intentional; there is no custom repository. The downloaded starter archive SHA-256 is `351820ecc9c8a5946f832289aa90a2fbf891d697cacb28d01742a73e7d09b438`. The generated Maven wrapper pins Maven 3.10.0; enforcer 3.6.2 requires a Java 21 runtime. The parent manages Spring/JDBC/Flyway versions rather than individually mixing release lines.

Boot 4 uses modular starter names, including `spring-boot-starter-webmvc`, `spring-boot-starter-kafka`, `spring-boot-starter-flyway` and `spring-boot-starter-security-oauth2-resource-server`. Follow the matching Boot 4 documentation rather than copying Boot 3 coordinates from BankingLedger. Initializr's generated test starters remain paired with their corresponding dependency profiles.

## Dependency choices

Default Web MVC supplies the embedded HTTP server; Validation prepares request validation; Actuator supplies the bootstrap health endpoint. None implements a financial use case. The smoke test starts a real embedded server on a random port and checks health with the JDK HTTP client.

The `sqlserver` Maven profile adds JPA, Flyway, `org.flywaydb:flyway-sqlserver`, and runtime `com.microsoft.sqlserver:mssql-jdbc`. It keeps the database library stack out of the default no-infrastructure context. SQL Server runtime configuration uses environment credentials and schema validation. Migration V1 deliberately waits for correctness review. No H2 fallback is included.

The `redis` profile supplies Redis access for later completed-outcome replay. The `kafka` profile supplies producers/consumers for the later outbox/history phase. Configuration examples do not constitute cache, event schema or listener implementations. Kafka producer idempotence does not deduplicate application outbox republishes; consumer dedup remains necessary.

The `security` profile supplies JWT validation libraries/configuration, not a finished access policy. It needs a real issuer and phase-specific ownership/role tests before financial endpoints are exposed. No generated development password or live OTP is added. Debezium is external connector infrastructure and is deliberately not an application Maven dependency. Testcontainers, OpenAPI and observability exporters can be added when their phases have a demonstrated need; they are not claimed today.

## Structure and commands

Code is organized by feature starting with `operation/application/BalanceService`. The interface is a contract without a bean implementation. Keep account persistence, transaction coordination, confirmation, idempotency and event adapters as explicit feature boundaries when implemented; avoid creating empty repository/service/controller layers now.

`./mvnw verify` validates the default bootstrap. `./mvnw -Psqlserver,redis,kafka,security -DskipTests package` validates compilation and dependency compatibility of the full planned classpath; it does not connect to infrastructure or run persistence/fault tests. Add those test gates with the corresponding feature.

Sources: [Initializr](https://start.spring.io/), [Spring Boot](https://docs.spring.io/spring-boot/), [profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html), [Flyway SQL Server support](https://documentation.red-gate.com/fd/sql-server-database-277579330.html). Dependency versions are resolved from the checked-in POM, not from a promise that a moving documentation URL matches this exact release.
