# ADR-007: Include double-entry accounting in V1

Status: Accepted design, 2026-10-22. Owner: project maintainer, selected option E. Implementation: absent. SQL Server evidence: absent. Performance: not measured. [فارسی](ADR-007-double-entry-ledger.fa.md)

## Decision and context

Use `accounts`, `operations`, `journals` and `postings` in the existing SQL Server service. Posting history is immutable accounting evidence; persisted account balances are synchronous projections. Commit the complete balanced journal, projections and successful operation outcome together. Rejections have no journal or postings; retries return the stored outcome without another posting. Opening money uses an internal opening journal. Credit/debit use a funding/clearing counterpart.

This supersedes ADR-003's optional/later double-entry clause and the two-table starting scope. It accepts the architecture, not the still-pending detailed constraint, identity, locking or security protocols in ADR-001–005. See [Ledger.md](../en/Ledger.md) for sign rules, examples, reconciliation and staged delivery. This is a user-selected extension to the challenge.

## Alternatives and costs

Balance-only storage cannot reconstruct accounting history. A fixed two-account operation record is simpler but limits future multi-line accounting. A BankingLedger-style set of extra transfer/ledger transaction layers or a separate service adds lifecycle and distributed boundaries unnecessary for this scope. The chosen model supports multiple posting lines while keeping one local transaction. Separate SQL schemas do not add accounting guarantees.

Costs include journal/posting storage, indexes, extra log writes, reconciliation and contention on shared funding rows. Measure them in CBS21–22. Do not weaken invariants to claim speed. Future FX requires a separate currency/clearing and reservation review.

## Implementation and acceptance

Review the [constraint matrix](../en/Database-review.md) and CBS02–04 before the first business migration. Add accounting domain rules, four-table migration, mappings and operation slices in their existing guided tasks. Do not bulk-implement future steps or mark learning gates passed. Test SQL Server concurrency, duplicate races, opening entries, immutability, rollback at every write boundary, restart and independent reconstruction. Later corrections append reversing entries; a reversal endpoint is outside V1. No schema rollback or data migration exists today. Any future change to posted history needs a reviewed forward migration and retained identities.
