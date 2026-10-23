# Implementation status

The README describes the first release contract. The current checkout has portable domain/HTTP foundations and the verified CBS09 schema. Financial handlers remain unavailable, so all four financial routes return 503.

Latest evidence: 240 portable and 94 real SQL Server tests, zero failures/errors/skips. See [schema/runbook](Schema.md), [roadmap](Roadmap.md) and [HTTP evidence](HTTP-adapter-evidence.md). Next is entity mapping, opening fixtures and committed balance reads. Schema/health checks do not establish a completed financial release.
