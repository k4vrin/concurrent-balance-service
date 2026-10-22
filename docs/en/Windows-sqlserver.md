# SQL Server on Windows without Docker

[فارسی](../fa/Windows-sqlserver.md)

Optional alternative. The learning track uses [Docker Compose](Docker.md) as its selected infrastructure route.

Install a supported SQL Server Developer edition on a supported Windows machine using Microsoft's native installation wizard. Install Database Engine Services and SSMS separately as appropriate. Developer is for development/testing; a live internal service requires a production-permitted edition/license. Record server version, edition and database compatibility level before selecting CDC features.

Create a dedicated empty development database named `balance_service`. Use a dedicated application login rather than `sa`. Decide separate migration/runtime permissions when schema work begins. Configure a fixed TCP port if the Java application connects over TCP; verify the actual port and enable only the necessary network access. SSMS connecting locally does not by itself prove JDBC TCP connectivity.

PowerShell environment example, replacing the values locally:

```powershell
$env:DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=balance_service;encrypt=true;trustServerCertificate=false'
$env:DB_USERNAME = 'balance_app'
$env:DB_PASSWORD = '<set-locally>'
.\mvnw.cmd spring-boot:run
```

Use a server certificate trusted by the client. If an isolated development instance uses a self-signed certificate, an explicit local-only URL override `trustServerCertificate=true` bypasses certificate verification; do not copy that setting into shared/production configuration. Never commit a real password. This project does not read `.env` automatically and does not create the login/database.

If Java runs on another machine, including the current Mac, use the Windows hostname in the URL; `localhost` refers to the Java process's own machine. Native Windows database installation does not provide Redis, Kafka or Kafka Connect. Keep those later dependencies separate. No claim is made that the whole extended stack runs natively on Windows without additional setup.

Flyway, Hibernate and the SQL Server driver are included by default without Maven or Spring SQL Server profiles, but no financial migration exists and no server connectivity has been tested. The later Debezium gate must verify native CDC, SQL Server Agent/capture jobs, permissions, change-table retention and the exact edition/version supported by the connector. Prefer Developer over LocalDB/Express for the planned CDC experiments; do not assume every free edition exposes the needed jobs/features.

Sources: [Windows setup](https://learn.microsoft.com/en-us/sql/database-engine/install-windows/install-sql-server-from-the-installation-wizard-setup), [SQL Server editions](https://learn.microsoft.com/en-us/sql/sql-server/editions-and-components-of-sql-server-2025), [JDBC encryption](https://learn.microsoft.com/en-us/sql/connect/jdbc/connecting-with-ssl-encryption), [Debezium SQL Server](https://debezium.io/documentation/reference/stable/connectors/sqlserver.html).

## Accepted option E scope, 2026-10-22

The optional native Windows route uses the same [four-table ledger](Ledger.md), reviewed migrations and integration tests as Docker. Initialize nonzero balances through opening journals after schema review; direct balance seeding bypasses accounting. Windows setup is not required for the selected Docker route.
