# CBS07 SQL Server environment evidence

[فارسی](../fa/SQL-server-evidence.md) · [Setup and lifecycle](Docker.md) · [Roadmap](Roadmap.md)

Recorded 2026-10-23. The user explicitly accepted the applicable Developer license and authorized downloading/starting the local ARM64 emulation experiment. This verifies the environment and actual JDBC connectivity, not financial behavior or a supported Microsoft ARM64 deployment.

| Item | Observed value |
| --- | --- |
| Docker Desktop / engine | 4.42.0 / 28.2.2 |
| Docker Compose | 2.36.2-desktop.1 |
| Daemon architecture / available VM memory | linux/arm64, reported aarch64; 8,218,316,800 bytes |
| Image | mcr.microsoft.com/mssql/server:2022-CU22-ubuntu-22.04 |
| Verified manifest / running image digest | sha256:db9a8fe3098b7e8bbde41106bdc7caee942e97124e5fdb71b872ca208de3092d |
| Requested container platform | linux/amd64, emulated |
| Running container | cbs-local-sqlserver-1, healthy |
| Published endpoint | 127.0.0.1:14333 → container TCP 1433 |
| Persistent volume | cbs-local_sqlserver-data mounted at /var/opt/mssql |
| Container / server configured memory caps | 4,294,967,296 bytes / 2,048 MB |
| SQL Server product version | 16.0.4225.2 |
| Edition | Developer Edition (64-bit) |
| Server and database collation | SQL_Latin1_General_CP1_CI_AS |
| Development / test database | cbs_dev / cbs_test |
| Compatibility level, both databases | 160 |
| READ_COMMITTED_SNAPSHOT, both databases | 0 |
| Snapshot isolation, both databases | OFF |
| sqlcmd connection encryption | TRUE |
| JDBC driver | Microsoft JDBC Driver 13.4 for SQL Server, 13.4.0.0 |
| JDBC-reported server version | 16.00.4225 |
| Dedicated login | cbs_app; CONNECT granted in each database |
| App sysadmin / CREATE TABLE | 0 / 0 in each database; db_owner guard also passed |
| User tables | 0 in each database |

## What was exercised

1. Resolved the image manifest through Microsoft's registry before pinning the digest. Downloaded and started that exact image after authorization.
2. Validated Compose with placeholders without printing resolved secrets. Checked bootstrap shell syntax and compiled the standalone Java 21 probe.
3. Verified four invalid application-password forms fail before sqlcmd. Real locally generated credentials are in ignored mode-0600 .env.sqlserver and are omitted from evidence.
4. Ran infrastructure bootstrap: created the two empty databases, login and users; checked metadata, encryption and application privileges.
5. Connected from the host with the project's actual Microsoft JDBC driver to each database and executed SELECT as the dedicated non-administrator login.
6. Reran bootstrap without dropping data or rotating credentials.
7. Restarted the container, waited for health and successfully connected to each database through JDBC **before** bootstrap could recreate anything. This verifies survival of these databases/login across that restart using the retained named volume. It does not prove backup/recovery, disk-failure durability or clean-host restoration.
8. Reran bootstrap after restart; it completed and reported zero user tables in both databases.
9. Pointed the JDBC probe at unavailable loopback port 1; it returned a nonzero process exit. This is probe failure evidence, not yet the CBS08 absent-database Maven test gate.

The full Java 21.0.11 `./mvnw verify` plus classpath generation passed 223 portable tests with zero failures/errors/skips in 9.827 seconds. Database probes above are additional manual integration checks, not counted as automated Failsafe tests.

## Limits and handoff

Microsoft does not support SQL Server container emulation on ARM64. Local healthy startup and SELECT are useful experimental evidence; repeat on an x86-64 Linux Docker host when supported-platform evidence is required. No performance, load or production-readiness claim follows.

The self-generated certificate is trusted explicitly for local sqlcmd/JDBC. Encryption TRUE does not prove trusted server identity. Driver/protocol defaults and a certificate-backed external deployment need their own review.

The default CI_AS collation does not establish the required case-sensitive TransactionId semantics. CBS02–04 must settle column comparison, constraints, transaction/locking SQL, business caps and migration/runtime privileges before CBS09. The CONNECT-only login cannot create Flyway/business tables; no working financial Spring startup is claimed.

No accounts, operations, journals or postings tables exist; no balances were seeded. There are no financial mutation handlers, stored fingerprints/outcomes, durable replay or real ledger transactions. CBS08 still needs Failsafe sqlserver-it, explicit isolated datasource configuration, positive test counts, no-DB failure, rollback and later committed multi-connection fixtures.

The authorized container remains running on loopback. The stop/down/reset commands are in [Docker.md](Docker.md). No volume was deleted and no credentials are committed. Formal task acceptance, earlier independent learner checkpoints and CBS02–04 review status remain unchanged.
