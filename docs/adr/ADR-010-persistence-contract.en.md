# ADR-010: SQL Server persistence implementation contract

[فارسی](ADR-010-persistence-contract.fa.md)

Status: Engineering decision for implementation, 2026-10-23. This closes CBS02–04 technical review for entry to CBS09; it is not an assertion of additional project-owner approvals or learner acceptance.

Context: earlier reviews left exact transaction-ID storage, UUID lock SQL, four-table constraints, retry defaults and database authority unresolved. The selected ledger and business caps need one concrete migration contract.

Decision: follow [CBS09-contract.md](../en/CBS09-contract.md). Use tested Unicode/BIN2 identity constraints, UUID primary-key point locking with UPDLOCK/HOLDLOCK in application comparator order, four-table currency-consistent references and explicit row states, guarded REQUIRED TransactionTemplate, bounded whole-attempt recovery, and separate migration/runtime/initializer authority. Keep cross-row accounting guarantees in the controlled transaction protocol and independent reconciliation; do not label them row-CHECK guarantees.

Consequences: CBS09 can implement the migration now. Native JDBC experiments passed 240 portable plus 44 real tests; entity mappings, grants, actual Flyway execution and financial failure scenarios must be verified in their assigned implementation stages. No performance or production-readiness claim follows from this decision. Changes to this contract require a recorded reason and matching bilingual updates.
