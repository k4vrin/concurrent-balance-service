# Implementation status

The public README describes the first release contract. The current checkout has portable domain/HTTP foundations, the verified four-table schema, JPA mappings and a real SQL Server balance route. GET balance now returns 200/404, with temporary query unavailability mapped to 503. Public credit/debit/transfer remain unavailable.

Latest full verification: 240 portable plus 103 real SQL Server tests, zero failures/errors/skips. [Persistence/opening evidence](Persistence.md) covers committed journal-backed test initialization, native JPA lock lifetime, three rollback boundaries, reconciliation and HTTP reads. [Schema/runbook](Schema.md) owns setup.

Next is the durable credit/coordinator/replay slice. Opening initialization is test-only; it is not a fifth public route. The runtime has no finished public money-changing handler, production authorization or full retry/recovery engine.
