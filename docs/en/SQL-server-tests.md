> CBS09 implementation update, 2026-10-23: V1 is applied and validated in the local development/test databases. [Schema/runbook](Schema.md) records actual constraints, grants and 240 portable + 94 SQL Server tests. Historical pre-migration statements below are superseded by this implementation evidence. Financial execution remains pending.

# CBS08 real SQL Server integration harness

[فارسی](../fa/SQL-server-tests.md) · [Environment setup](Docker.md) · [CBS07 observations](SQL-server-evidence.md) · [Roadmap](Roadmap.md)

The sqlserver-it Maven profile runs infrastructure integration tests against the already provisioned cbs_test SQL Server database. Normal verify remains portable. This is an automated JDBC harness, not financial use-case, JPA/Flyway or HTTP-to-ledger integration evidence.

## What the profile changes

[pom.xml](../../pom.xml) enables Maven Failsafe 3.5.6 only under `-Psqlserver-it`, selecting `**/*IT.java` and failing if no integration tests are found. Spring Boot's parent already binds integration-test and verify, so there is no duplicate execution block. Run the verify lifecycle, not just integration-test, to evaluate Failsafe results. [Failsafe lifecycle reference](https://maven.apache.org/surefire/maven-failsafe-plugin/usage.html).

This profile controls test execution, not SQL Server dependencies or application startup. SQL Server remains a default application dependency. No matching Spring runtime profile is required for this raw-JDBC harness. Portable Surefire tests still run first, including the legitimate persistence-disabled HTTP smoke test in its own test context. The integration harness separately rejects smoke requested through active/include/default system or environment profile settings.

## Explicit test datasource and protections

[SqlServerTestDatabase](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/SqlServerTestDatabase.java) requires all three environment variables:

| Variable | Meaning |
| --- | --- |
| SQLSERVER_TEST_URL | Explicit JDBC SQL Server URL with databaseName=cbs_test |
| SQLSERVER_TEST_USERNAME | Dedicated non-administrator login, cbs_app in the local setup |
| SQLSERVER_TEST_PASSWORD | Matching secret supplied outside Git |

There is no default datasource and no fallback to DB_URL/DB_USERNAME/DB_PASSWORD. Missing/blank values fail class setup. Credentials embedded as user/userName/password/accessToken URL properties are rejected; keep them in the dedicated environment variables. Real connections must report the Microsoft SQL Server product and exact connected catalog cbs_test before any fixture write. Checking the actual catalog also covers redirected/overridden URL properties. A cbs_dev connection is closed and rejected.

Connection/login and socket waits are bounded to five seconds through driver properties. The helper never prints the credentials or URL. It uses the actual runtime Microsoft JDBC driver from the project, with no H2, Oracle, mocks, connection substitution or assumptions that skip an unavailable database. [Portable guard tests](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/SqlServerTestDatabaseTests.java) check these configuration rules without connecting.

## Run from Bash

First provision the CBS07 environment and keep the SQL Server container healthy. With JAVA_HOME pointing at Java 21:

```bash
export SQLSERVER_TEST_URL='jdbc:sqlserver://127.0.0.1:14333;databaseName=cbs_test;encrypt=true;trustServerCertificate=true'
export SQLSERVER_TEST_USERNAME=cbs_app
read -r -s -p 'Test login password: ' SQLSERVER_TEST_PASSWORD
export SQLSERVER_TEST_PASSWORD
./mvnw -Psqlserver-it verify
unset SQLSERVER_TEST_PASSWORD
```

Enter the local application password from the ignored environment file. It is not a new password or the administrator password. Bash read hides input and avoids embedding a secret in command history. Compose's .env.sqlserver is not automatically loaded into Maven. The local trustServerCertificate=true exception is described in the setup guide; it is not an external-deployment TLS policy.

## Run from PowerShell

Set JAVA_HOME to a JDK 21 location and use the same local application password:

```powershell
$env:SQLSERVER_TEST_URL = 'jdbc:sqlserver://127.0.0.1:14333;databaseName=cbs_test;encrypt=true;trustServerCertificate=true'
$env:SQLSERVER_TEST_USERNAME = 'cbs_app'
$testSecret = Read-Host 'Test login password' -AsSecureString
$env:SQLSERVER_TEST_PASSWORD = [System.Net.NetworkCredential]::new('', $testSecret).Password
try {
    .\mvnw.cmd -Psqlserver-it verify
} finally {
    Remove-Item Env:SQLSERVER_TEST_PASSWORD -ErrorAction SilentlyContinue
}
```

Secure input prevents screen/history exposure; the driver still receives a process environment secret. It is not a production secrets manager. Never use -D properties for passwords or paste resolved secrets into build evidence.

## Five integration cases

[SqlServerEnvironmentIT](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/SqlServerEnvironmentIT.java) fails BeforeAll if settings/infrastructure are absent. It does not start Spring or run migrations.

| Test | Actual database behavior checked |
| --- | --- |
| selectUsesActualSqlServerAndNonAdministratorLogin | SELECT, catalog, driver identity and non-sysadmin connection |
| rollbackRemovesUncommittedLocalFixtureRows | A local temporary table exists before the attempt; inserted row count changes from one to zero after explicit rollback |
| committedSetupIsVisibleToAnotherConnection | Committed synthetic fixture is visible from a second JDBC connection |
| rollingBackAnAttemptPreservesSeparateCommittedSetup | A separate writer's update is rolled back; a fresh reader still sees original committed value 7 |
| fixtureCleanupRemovesOnlyItsOwnTemporaryTable | Explicit fixture close removes its generated temporary object, checked through OBJECT_ID from another connection |

[CommittedSqlServerFixture](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/CommittedSqlServerFixture.java) creates a global temporary table named `##cbs08_<generated UUID>`, commits a synthetic row and holds the owner connection open until cleanup. The global temporary table permits cross-connection visibility; a local # table would be limited to its session. SQL Server's default global-temp lifetime also depends on the creating session and active references. We explicitly DROP the generated object on normal fixture close rather than use database-wide cleanup. [Microsoft temporary-table behavior](https://learn.microsoft.com/en-us/sql/t-sql/statements/create-table-transact-sql).

Each fixture owns a distinct generated identifier, never a user-controlled SQL identifier. try-with-resources handles cleanup on test assertion failure too. Global temporary objects share the server namespace and are not a security-isolation mechanism; they contain only synthetic test data. No table is created in cbs_dev/cbs_test, no business rows are seeded and no db_owner/DDL role is added to cbs_app.

The local rollback fixture and separately committed multi-connection setup deliberately use different transaction boundaries. Wrapping every test in one rollback-only Spring transaction would not establish visibility of committed setup to another connection. Opening/accounting fixtures must later use the real reviewed posting path once CBS09–10 provides mappings; these temporary rows are not a substitute.

## Prove failure rather than silent skipping

With test credentials still exported, this explicit unavailable endpoint must fail Maven:

```bash
SQLSERVER_TEST_URL='jdbc:sqlserver://127.0.0.1:1;databaseName=cbs_test;encrypt=true;trustServerCertificate=true' \
  ./mvnw -Psqlserver-it verify
```

PowerShell can temporarily set SQLSERVER_TEST_URL to the same unavailable URL and run the same verify command, then restore the cbs_test URL. The check does not stop the shared container or touch another database.

Missing test URL must fail configuration. Explicit smoke must fail even if the database is healthy. A URL pointing at cbs_dev must fail the connected-catalog guard before fixture writes. An intentionally failed negative check is evidence of the guard, not a passing integration test. Restore valid settings afterward and require the positive Failsafe report.

## Reports, evidence and remaining gates

Portable tests write target/surefire-reports; integration tests write target/failsafe-reports. Check the new run's XML counts, failures, errors and skips, not an old report after a portable-only run. failIfNoTests prevents an empty discovered suite from looking successful; an intentionally supplied Maven skip flag still means the suite was not verified.

The implementation uses infrastructure-only temporary tables. Passing these tests does not establish business BIGINT mapping, JPA entities, Flyway permissions, account locks, atomic ledger writes, durable replay, financial rollback or authorization. The current ARM64 SQL Server environment remains an unsupported emulation experiment.

Before CBS09, finish the CBS02–04 constraint/identity/transaction review, including numeric business caps, exact transaction-ID comparison and migration/runtime privileges. The default CI_AS collation and CONNECT-only runtime user are not those finished decisions. No business migration is introduced by CBS08, and formal learner acceptance remains separate from assistant-run tests.

## Recorded CBS08 verification, 2026-10-23

Java 21.0.11 plain `./mvnw verify` passed 240 portable tests with zero failures/errors/skips in 12.092 seconds. No test datasource was supplied to that run. Final `./mvnw -Psqlserver-it verify` passed the same 240 portable tests plus 5 real SQL Server tests, all with zero failures/errors/skips, in 9.150 seconds. The five integration cases took 0.679 seconds. Counts were checked in both fresh XML report sets.

Four separate Maven checks failed with their intended reasons: unavailable loopback port 1, missing SQLSERVER_TEST_URL, explicit SPRING_PROFILES_ACTIVE=smoke and a URL connected to cbs_dev. Negative checks selected the 17 portable guard cases plus integration class setup; they are not additional passing database tests. The final full positive run restored the passing reports after those expected failures. Local credential values were checked absent from the final log and generated reports. This evidence is limited to the existing unsupported ARM64 SQL Server 2022 CU22 experiment.

Post-suite inspection using the dedicated login found zero remaining ##cbs08_ temporary fixtures and zero user tables in cbs_test. No administrator privileges or business schema were added.

## CBS02–04 review update, 2026-10-23

The shared cap and retained over-cap rejection are now [accepted business policy](Amount-limits.md). Four additional real JDBC cases bring the latest profile run to 240 portable plus 9 SQL Server tests, zero failures/errors/skips. See [observations, limits and remaining migration gates](Persistence-review.md). These experiments do not introduce a business schema or complete the review. Earlier CBS08 counts remain historical evidence.

## Transaction-ID review checkpoint, 2026-10-23

The tested engineering candidate uses nvarchar(256) with explicit BIN2 collation, NOT NULL, 1–128-character length/allowlist checks and named global uniqueness. Unicode JDBC binding preserves invalid input for rejection. Thirty-one new real cases include exhaustive single-BMP-code-unit predicate checking and competing inserts. Full Java 21 profile verification passed 240 portable plus 40 SQL Server tests, zero failures/errors/skips, in 12.756 seconds; the new class took 2.091 seconds. See [design, SQL, examples and limitations](Transaction-id-storage.md). Earlier counts are historical. No business migration, runtime repository or durable replay is added. Next review is the UUID account-lock adapter and complete four-table transaction/permissions contract; formal learner acceptance remains open.

## CBS02–04 engineering review closed for CBS09, 2026-10-23

[ADR-010](../adr/ADR-010-persistence-contract.en.md) and [CBS09 implementation contract](CBS09-contract.md) consolidate the remaining decisions. Four new real UUID cases verify mapping/BIGINT round trips, application-versus-SQL order, blocking and unrelated-key progress, missing-key creation protection and opposite-direction workers. Full profile verify passed 240 portable plus 44 SQL Server tests, zero failures/errors/skips, in 16.997 seconds. Reviewed choices include explicit native EntityManager lock SQL, four-table keys/row states/currency FKs, policy/response retention, retry settings and separate migration/runtime/initializer authority. Next implementation is CBS09 migration and its actual constraint/permission/Flyway tests, rather than further incremental design rounds. Financial failure tests stay in CBS10–20. No business DDL/grants or learner acceptance were added.
