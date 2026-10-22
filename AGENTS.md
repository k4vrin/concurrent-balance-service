# Engineering instructions

This repository starts as a bootstrap. Read README.md, docs/en/Roadmap.md and docs/en/Decisions.md before implementing financial behavior. SQL Server is the target; never claim Oracle or H2 tests prove SQL Server semantics.

Use Java 21 and Maven wrapper. SQL Server dependencies and configuration are part of the default application; normal startup requires a configured database. Keep portable tests independent of infrastructure with explicit test-only configuration. Activate the optional Redis, Kafka and security Maven dependency profiles and matching Spring runtime profiles intentionally. Preserve staged delivery: complete the four-operation core and correctness evidence before OTP, Redis, Kafka and Debezium. Do not introduce business migrations before constraint review.

Use feature-based packages and constructor injection. Comments explain intent and constraints. Keep English and Persian documents consistent in the same change. Separate planned, implemented and measured status. Prefer the codebase-memory graph for code discovery; index this repository first if absent, then use file searches where the graph is insufficient.

Branches use descriptive lowercase names with feature/, fix/, refactor/, docs/, test/, perf/, build/, ci/ or chore/. Never include assistant/tool identities. Commit messages have three sections: a capitalized imperative Type: summary; a 1–3 sentence reason; and Key changes include: with implementation bullets. See docs/en/Workflow.md.

Run ./mvnw verify for bootstrap changes and appropriate real integration/failure tests for financial changes. Never claim skipped tests passed. Keep credentials, OTP secrets, production data and the company PDF out of Git.

## Accepted ledger scope, 2026-10-22

Follow accepted ADR-007 and docs/en/Ledger.md with their Persian pairs. V1 requires accounts, operations, journals and postings. Commit balanced immutable posting history, account balance projections and operation success together; rejection has no journal, replay adds none. Opening balances require journals and credit/debit require funding counterparts. Preserve CBS02–04 constraint/transaction review before business migrations and the guided implementation sequence. Do not treat double-entry as optional or introduce a separate ledger service. Design acceptance is not financial implementation or measured evidence.
