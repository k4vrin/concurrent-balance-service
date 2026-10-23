> Current persistence update: [CBS10 evidence](Persistence.md) now verifies JPA mappings, committed opening fixtures and the SQL Server balance route. Mutations remain unavailable. Earlier all-route-503 and schema-only statements below are historical.

# CBS09 SQL Server schema and migration runbook

[فارسی](../fa/Schema.md) · [Contract](CBS09-contract.md) · [Local SQL Server](Docker.md) · [Integration harness](SQL-server-tests.md)

## Implemented scope

[V1__create_balance_schema.sql](../../src/main/resources/db/migration/V1__create_balance_schema.sql) now creates accounts, operations, journals and postings in dbo. It has been applied and validated with Flyway 12.4.0 in the local cbs_dev and cbs_test databases. No financial handler, opening journal, entity mapping or durable replay is implemented by this migration. The HTTP fallback still returns 503.

| Storage rule | Migration enforcement |
| --- | --- |
| Account identity/currency/category | UUID PK, currency candidate key, uppercase three-letter Unicode currency, category/status allowlists |
| Balance representation | BIGINT; customers nonnegative; synthetic funding permits signed bounds; default balance zero |
| Global request identity | Named uq_operations_transaction_id, tested Unicode BIN2 syntax/length checks, no operation-type partition |
| Recorded intent | Explicit operation shape; positive BIGINT amount; full canonical bytes/version and SHA-256 consistency |
| Policy and selection | Nonempty execution-policy version; all-or-none funding metadata; successful funded types require selection |
| Stored response row | Explicit PENDING/SUCCEEDED/REJECTED nullability, reason/status/content-type/version/body/completion checks |
| Journal identity | UUID PK, unique operation, currency-consistent operation FK |
| Posting identity | Journal/line PK; positive line/amount; side allowlist; currency-consistent journal/account FKs |
| Historical deletion | NO ACTION FKs; runtime lacks history update/delete permission |

Requested account IDs and retained funding provenance in operations deliberately have no account FKs. This permits missing-account rejection and avoids implicit account locking during reservation. Postings require real references. Original operation amounts can exceed the configured business cap so terminal cap rejection remains storable. Exact policy limits are enforced by the future evaluator, while row constraints enforce structural ranges.

The migration has no financial seed data. It cannot enforce balanced totals across posting rows, success-to-journal completeness, no committed PENDING, immutable terminal outcomes or projection equality using ordinary row CHECKs. Those remain the controlled transaction protocol and reconciliation's responsibilities. Tests intentionally probe row storage shape inside rolled-back transactions; they are not financial operation examples.

## Database identities

| Identity | Authority |
| --- | --- |
| Local sa | Infrastructure provisioning and grants only; absent from Maven tests and application startup |
| cbs_migrator | CONNECT/CREATE TABLE, ALTER/REFERENCES/VIEW DEFINITION on dbo, ownership of cbs_migrations history schema; no sysadmin/db_owner and no broad business-table DML grants |
| cbs_app | SELECT four tables; INSERT operations/journals/postings; UPDATE account balance and operation selection/completion columns only |
| cbs_initializer | SELECT/INSERT accounts for controlled zero-account initialization; no financial history writes or schema DDL |

The implementation places Flyway history in cbs_migrations, owned by cbs_migrator. Business tables remain in dbo. This refines ADR-010's separate-authority design: history ownership avoids granting schema-wide business DML just to maintain Flyway history. Runtime is denied history access by omission of grants. No broad db_datawriter role or table-level UPDATE grant is added. Existing unexpected grants are not silently removed by setup; permission tests must pass for the intended local identities.

SQL DML grants do not enforce journal balance or forbid a compromised runtime writer from storing PENDING or changing a terminal result through its allowed completion columns. The single trusted posting path must implement those rules. The initializer is also trusted to create zero accounts; nonzero initialization requires CBS10's opening journal path.

## Provision and migrate from Bash

First complete the CBS07 environment/bootstrap. Use JDK 21 and three distinct application/migration/initializer secrets plus the existing administrator secret. The two new secrets follow the same 16–128 ASCII letters/digits/_/- rule, with upper/lowercase and a digit. Provisioning is repeatable and verifies existing passwords rather than silently rotating them. Secrets generated for this session are in the ignored, mode-0600 .env.sqlserver file; no secret value is in Git.

Read the new secrets without command-history exposure, then provision using the host wrapper. It sends secrets through stdin to the container, not docker command arguments:

```bash
read -r -s -p 'Migration password: ' CBS_MIGRATOR_PASSWORD
export CBS_MIGRATOR_PASSWORD
read -r -s -p 'Initializer password: ' CBS_INITIALIZER_PASSWORD
export CBS_INITIALIZER_PASSWORD
bash scripts/sqlserver/provision-migration-local.sh
export MIGRATION_DB_USERNAME=cbs_migrator
export MIGRATION_DB_PASSWORD="$CBS_MIGRATOR_PASSWORD"
for migration_database in cbs_dev cbs_test; do
  export MIGRATION_DB_URL="jdbc:sqlserver://127.0.0.1:14333;databaseName=$migration_database;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=5000"
  ./mvnw -Pdb-migrate flyway:migrate flyway:validate || break
done
```

Require success for both databases before applying grants:

```bash
bash scripts/sqlserver/grant-runtime-local.sh
unset MIGRATION_DB_URL MIGRATION_DB_USERNAME MIGRATION_DB_PASSWORD
```

The db-migrate Maven profile pins the plugin to the Boot-managed Flyway version and adds its SQL Server/JDBC plugin dependencies. It reads only explicit MIGRATION_DB_URL/USERNAME/PASSWORD, has no DB_* fallback, uses createSchemas=false, baselineOnMigrate=false and cleanDisabled=true, and verifies migration filenames. Missing settings fail Maven. Do not use Maven -D password properties, debug logs or effective-POM exports containing resolved secrets.

Normal application startup now has spring.flyway.enabled=false and Hibernate ddl-auto=validate. Apply/validate schema separately, then launch with runtime DB_* settings only. No migration credentials belong in the runtime environment. The SQL Server dependency remains default; no SQL Server runtime profile is needed.

## Run the real test suite

Set all three explicitly separate test connections; each must connect to cbs_test. Copying the same explicit test URL here does not enable any runtime DB_* fallback:

```bash
export SQLSERVER_TEST_URL='jdbc:sqlserver://127.0.0.1:14333;databaseName=cbs_test;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=5000'
export SQLSERVER_TEST_USERNAME=cbs_app
read -r -s -p 'Runtime password: ' SQLSERVER_TEST_PASSWORD
export SQLSERVER_TEST_PASSWORD
export SQLSERVER_MIGRATION_TEST_URL="$SQLSERVER_TEST_URL"
export SQLSERVER_MIGRATION_TEST_USERNAME=cbs_migrator
export SQLSERVER_MIGRATION_TEST_PASSWORD="$CBS_MIGRATOR_PASSWORD"
export SQLSERVER_INITIALIZER_TEST_URL="$SQLSERVER_TEST_URL"
export SQLSERVER_INITIALIZER_TEST_USERNAME=cbs_initializer
export SQLSERVER_INITIALIZER_TEST_PASSWORD="$CBS_INITIALIZER_PASSWORD"
./mvnw -Psqlserver-it verify
unset CBS_MIGRATOR_PASSWORD CBS_INITIALIZER_PASSWORD SQLSERVER_TEST_PASSWORD
unset SQLSERVER_MIGRATION_TEST_PASSWORD SQLSERVER_INITIALIZER_TEST_PASSWORD
```

For PowerShell, use the same named environment variables through `$env:NAME`, Read-Host -AsSecureString and NetworkCredential conversion as in the [test runbook](SQL-server-tests.md). Run `.\mvnw.cmd -Pdb-migrate flyway:migrate flyway:validate` once for each explicit migration URL. The local provisioning wrappers require Bash; run them in a Bash terminal on the Docker host. A PowerShell-native provisioning wrapper has not been tested.

[SqlServerMigrationIT](../../src/test/java/dev/kavrin/balance/infrastructure/sqlserver/SqlServerMigrationIT.java) validates and reruns V1 through the guarded migration datasource. It checks actual tables/indexes/trusted noncascading FKs, named constraint failures, required-field NULL rejection, valid row shapes and funding requirements, case-sensitive uniqueness, over-cap storage, signed bounds, rollback and runtime permissions. Its 50 cases add to the 44 earlier real SQL Server cases. Migration/initializer settings are mandatory; no test is skipped when they are missing. The class provisions exactly three stable zero-balance account fixtures in cbs_test through cbs_initializer; all operation/journal/posting and nonzero-balance probes roll back. These rows do not substitute for CBS10 opening journals.

## Recorded evidence, 2026-10-23

- The test database had zero user tables before its first migration. Dedicated cbs_migrator applied V1 and validate in cbs_dev/cbs_test; both histories record version 1, success 1, checksum 982769180. Dev migration took 8.886 seconds including plugin setup; test migration took 3.583 seconds. These are observations, not performance targets.
- Final Java 21.0.11 full verification passed 240 portable plus 94 actual SQL Server tests, zero failures/errors/skips, in 17.879 seconds. The 50 new schema cases took 2.585 seconds. Fresh XML counts were checked.
- Missing migration settings failed Maven with the expected configuration error. Using cbs_app for migrate failed with SQL permission error 262. Flyway clean is rejected. Grants were rerun successfully.
- The packaged application reached HTTP health UP on a temporary loopback port using only cbs_app and no migration credentials, then was stopped.
- Final dev counts are 0 accounts/operations/journals/postings. Test counts are 3 zero accounts and 0 operations/journals/postings. No nonzero or unbalanced financial fixture was committed. Generated local secrets remain ignored; logs/reports were scanned for them.

Evidence remains the authorized unsupported ARM64 SQL Server 2022 CU22 Developer experiment. Actual empty-database application was measured on the initial local run; subsequent automated tests validate/rerun the retained schema and never clean/drop it. An applied V1 is immutable: future changes use V2 rather than editing/repairing its checksum. CBS09 implementation is verified; formal learning acceptance remains separate. Next is CBS10 mappings, journal-backed opening fixtures and committed balance reads.
