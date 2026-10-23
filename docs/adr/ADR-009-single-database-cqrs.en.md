# ADR-009: Single-database command/query model separation

Status: Accepted design by the project owner, 2026-10-23. [فارسی](ADR-009-single-database-cqrs.fa.md) · [Architecture](../en/Architecture.md)

Context: money-changing use cases require rich validation, locking, accounting and stored outcomes; balance queries need a small committed view. Using the write-domain model as the mandatory HTTP read representation couples those different needs.

Decision: apply CQRS within the accepted modular monolith using separate command and query models/handlers in the same SQL Server database. Credit/debit/transfer commands pass through OperationCoordinator and the one-transaction financial path. GetBalanceHandler returns a query projection/BalanceResponse directly from committed account data without constructing a writable Account aggregate or loading journals. Later history queries can have their own read DTOs. Keep these types inside their feature packages; no global command/query layers or required command bus.

Consistency: account projections update synchronously with operation success, journal and postings in one transaction. Balance queries use committed authoritative database data, not Redis or a lagging Kafka history projection. A single balance read is not a multi-query snapshot promise. Replays return the original stored operation response rather than a fresh balance query. Commands may return outcomes and balances; CQRS does not require void responses.

Alternatives: a shared model is simpler for trivial CRUD but would expose financial write objects unnecessarily here. Separate read databases introduce synchronization, lag, rebuild and deployment work without a measured need. Event sourcing is an independent storage choice, not required for this CQRS decision. Redis, Kafka and reporting retain their later-stage scope.

Costs: extra query DTOs/projections and explicit mapping, plus tests to preserve the boundary. No throughput improvement is claimed. Preserve authorization on queries and mutations before external access. Persistence and transaction detail decisions still require their existing review/tests.

Acceptance evidence to obtain during implementation: command tests enforce financial invariants; query tests return only required committed fields with no operation/journal/projection mutation; HTTP tests keep both contracts explicit; real SQL Server tests cover committed visibility, rollback and replay. Do not assert a universal one-query budget or lock-free reads without execution evidence.

Migration/rollback: no new tables or dependencies are needed by this choice. If the split stops being useful, consolidate application models behind the same public contracts, preserving all stored operation responses and financial invariants. No code is added by this ADR.

References reviewed 2026-10-23: [Microsoft CQRS](https://learn.microsoft.com/en-us/azure/architecture/patterns/cqrs), [Fowler CQRS](https://martinfowler.com/bliki/CQRS.html). Source descriptions are guidance, not proof of application behavior.
