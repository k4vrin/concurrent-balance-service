# SQL Server migrations

No schema has been created yet. Review the proposed constraints and transaction protocol in `docs/en/Database-review.md` before adding `V1__create_balance_schema.sql`.

Flyway discovers versioned SQL here when both the Maven `sqlserver` dependency profile and Spring `sqlserver` runtime profile are active. Use a dedicated empty development database. Do not use Hibernate `ddl-auto=update` or `baseline-on-migrate` to bypass migration review.
