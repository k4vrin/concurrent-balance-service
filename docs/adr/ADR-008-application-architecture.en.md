# ADR-008: Modular monolith and feature boundaries

Status: Accepted design by the project owner, 2026-10-23. [فارسی](ADR-008-application-architecture.fa.md)

Context: financial writes need one local commit while HTTP, domain rules and persistence remain understandable and testable.

Decision: one Spring Boot deployment organized by feature, with api/application/domain/persistence responsibilities and selective ports/adapters. Framework-free Money/account/journal rules and one coordinator-owned transaction are the baseline. Account and ledger persistence do not commit independent parts of an operation. Preserve ADR-007's four-table accounting boundary and the accepted TransactionTemplate mechanism.

[Architecture.md](../en/Architecture.md) contains the accepted package blueprint, request flow, transfer/Money examples and test/resource layout. Future class names are illustrative, not implemented APIs. Add packages incrementally; no empty-layer generation or separate ledger service.

Alternatives: top-level technical layers alone obscure feature ownership; premature services introduce distributed consistency; a general event-sourced framework is unnecessary for the current requirements. CQRS variants, optional boundary-check tooling and other patterns are recommendations pending separate acceptance, not part of this ADR's approval.

Costs: explicit mappings/interfaces and discipline to prevent cross-feature persistence access. No throughput/cost measurements exist. Verify domain boundaries with focused tests and later structural checks if needed; real SQL Server tests still gate persistence/locking claims. Design acceptance does not close CBS00–04 assessment/protocol gaps.

Implementation: Money and bootstrap exist; other features are planned. No migration or dependency change. Reversing this packaging decision needs coordinated imports and docs; financial history is never undone by a package refactor.

Subsequent decision: ADR-009 accepts single-database CQRS on 2026-10-23; the original recommendation status above is historical. Other patterns keep their stated scope.
