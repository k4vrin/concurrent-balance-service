# SQL Server migrations

No schema has been created yet. Review the proposed constraints and transaction protocol in `docs/en/Database-review.md` before adding `V1__create_balance_schema.sql`.

Flyway discovers versioned SQL here during normal application startup using the database settings in `application.yaml`. No SQL Server Maven or Spring profile is required. The test-only `smoke` profile disables persistence for the HTTP smoke test and must not be used for database integration tests. Use a dedicated empty development database. Do not use Hibernate `ddl-auto=update` or `baseline-on-migrate` to bypass migration review.

Accepted ADR-007 requires `accounts`, `operations`, `journals` and `postings` from V1. Review journal/operation uniqueness, mandatory and currency-consistent references, posting signs, account-category balance bounds, cross-row balancing, append-only permissions and the atomic projection protocol in CBS02–04 first. Nonzero fixtures use opening journals, never direct balance updates. No business migration is introduced by this design acceptance. See `docs/en/Ledger.md` and its Persian pair.
