Historical evidence below describes the original 2026-10-20 profile-based bootstrap. On 2026-10-22 the SQL Server Maven/Spring profiles were removed; current commands are in README.md. Normal startup now requires a database, while the smoke test disables persistence explicitly.

# Bootstrap verification evidence

Date: 2026-10-20, Asia/Tehran. [فارسی](../fa/Bootstrap-evidence.md)

- Project: `concurrent-balance-service`, verified in its final local checkout.
- Runtime used: Eclipse Temurin Java 21+35, macOS arm64. Wrapper: Maven 3.10.0. Spring Boot parent: 4.1.1.
- Final-location `./mvnw -o -B -ntp clean verify`: BUILD SUCCESS, one HTTP health smoke test passed, zero failures/errors/skips; executable JAR packaged. JAVA_HOME explicitly selected Java 21.
- Staging `./mvnw -B -ntp -Psqlserver,redis,kafka,security -DskipTests package`: BUILD SUCCESS. This resolved/compiled/packaged optional libraries; tests were deliberately skipped and no external service was validated. Maven reported optional upstream metadata repository 401 warnings, but completed successfully using resolved artifacts.
- Local Markdown link/code-fence, whitespace and POM XML checks passed before handoff; final-location Markdown links and fences passed.
- These checks preceded the initial public commit. GitHub CI status is reported by the repository Actions page and is separate from this local evidence.

The initial unavailable parent coordinate and exact-JSON health expectation were corrected before the successful build. No financial operation, SQL migration, database connection, Redis/Kafka runtime, JWT authorization policy, OTP, CDC, FX integration or throughput claim is validated by these checks. No performance numbers are available.

Next gate: complete P0 transaction/constraint decisions, then add SQL Server schema and real-database P1 integration tests. Logs/target files are generated and ignored; reproduce with the commands above.

## Profile removal verification, 2026-10-22

- JDK: JetBrains Runtime 21.0.11, macOS ARM64.
- `./mvnw -B -ntp clean verify`: BUILD SUCCESS; one HTTP smoke test, zero failures/errors/skips. The test activates the test-only smoke profile; it establishes no SQL Server connectivity or financial semantics.
- The initial incremental build passed but retained the deleted profile YAML in target output. A clean build removed the stale file. Packaged JAR inspection confirmed default JPA/Flyway/Microsoft JDBC dependencies and absence of application-sqlserver.yaml and the test-only application-smoke.yaml.
- Normal packaged startup with DB/Spring overrides removed failed as expected for the unresolved DB_URL configuration. No database was contacted successfully or provisioned.
- The first sandboxed Maven invocation could not access its dependency cache. Verification above completed after granting Maven cache/network access.
- Configuration decision: [ADR-006](../adr/ADR-006-default-sqlserver.en.md).

## Learner bootstrap run, 2026-10-22

The learner supplied the terminal output of the following run, completed at 16:22:33 Asia/Tehran:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./mvnw -B -ntp clean verify
```

Java 21.0.11 passed the enforcer check. BUILD SUCCESS in 6.889 seconds: one HTTP smoke test, zero failures/errors/skips; the smoke Spring profile was active and the executable JAR was packaged. Upstream Flyway repository metadata requests returned 401 warnings and Mockito reported dynamic-agent warnings; neither prevented completion. This is learner-supplied bootstrap execution evidence only. SQL Server connectivity, migrations and financial behavior remain unverified. CBS00 explanation, changed-case reasoning and remaining Git/CI review evidence must be assessed separately.
