# SQL Server migrations

V1__create_balance_schema.sql creates the reviewed accounts, operations, journals and postings in dbo. CBS02–04 review is recorded by ADR-010 and the bilingual CBS09 contract. See docs/en/Schema.md and docs/fa/Schema.md for actual evidence and commands.

Run `./mvnw -Pdb-migrate flyway:migrate flyway:validate` with explicit MIGRATION_DB_URL/USERNAME/PASSWORD. Flyway history belongs to cbs_migrations, preprovisioned for the dedicated migration user. cleanDisabled=true, baselineOnMigrate=false and createSchemas=false are intentional. Never edit an applied migration; use the next version.

Normal application startup disables Flyway and uses restricted runtime DB_* credentials with Hibernate ddl-auto=validate. The portable smoke profile still excludes persistence. Actual SQL Server integration tests require separate runtime/migration/initializer test connections and never skip absent infrastructure. No nonzero financial seed data is in V1; opening journals and entity mappings are CBS10 work.
