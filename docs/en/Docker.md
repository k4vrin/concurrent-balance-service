# Local infrastructure with Docker Compose

[فارسی](../fa/Docker.md) · [Index](../../README.md)

Status, 2026-10-22: Docker Compose is the selected local infrastructure route. There is no Compose file or provisioned database in this bootstrap yet. This guide defines the setup work for the learning track, not a successful deployment.

## Stage the services

| Task | Container setup |
| --- | --- |
| CBS00 | Check Docker availability; bootstrap verification still needs only Java 21 and the Maven wrapper |
| CBS07 | Add Compose for SQL Server Developer, persistent data and isolated development/test databases |
| CBS30–31 | Add Redis when implementing completed-response replay |
| CBS33–35 | Add Kafka when implementing publisher/consumer behavior |
| CBS36–37 | Evaluate Kafka Connect/Debezium after the CDC compatibility decision |

The initial application runs on the host through Maven or the IDE and connects to published loopback ports. Containerizing the application can follow once its dependency setup works. Do not activate every Maven/Spring profile just because the services use Docker.

## Check the host first

```sh
docker version
docker compose version
```

Both Docker client and daemon must be available. On the current ARM64 Mac, the client was detected on 2026-10-22, but the daemon was unreachable. No container startup or SQL connection was verified.

Microsoft supports SQL Server Linux containers on Intel/AMD x86-64 Linux hosts. ARM64 emulation is not a supported SQL Server configuration. An explicit `linux/amd64` platform setting selects the image architecture; it does not establish compatibility or successful startup. At CBS07, test the local emulated development path if available and record its limitations. Use an x86-64 Docker host if emulation fails or a supported environment is required. Do not replace SQL Server with a different database and claim equivalent evidence.

## CBS07 setup deliverable

Create a reviewable Compose configuration with a pinned SQL Server image version, the Developer edition, explicit license acceptance, a local secret supplied outside Git, a named data volume, a loopback-only published port and a readiness check. Record the resolved image digest, server version, edition, database compatibility level and isolation settings. Review image prerequisites and memory requirements before startup.

Provision separate empty development and test databases and a dedicated application login. Keep `sa` for bootstrap administration; decide migration/runtime permissions before business migrations. Store no credentials in tracked Compose or SQL files. Document startup, readiness, logs, shutdown and a separately labeled destructive volume-reset command. Initialization must tolerate restarting an existing volume. Do not delete a volume to repair an ordinary connection problem.

Spring Boot does not automatically load Compose's `.env` into the host application. Export `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` separately or configure the IDE. From the host, use the published localhost port; from an application container, use the Compose service name and container port. Review development certificate trust explicitly.

CBS08 adds the real JDBC integration harness and proves failure when the database is absent. Starting a container or passing a readiness check alone does not prove application connectivity, migrations, transaction correctness or CDC support. CBS00 remains open until the learner's own bootstrap evidence is recorded.

Native Windows installation remains an [optional alternative](Windows-sqlserver.md).

Sources: [Microsoft container deployment and architecture support](https://learn.microsoft.com/en-us/sql/linux/sql-server-linux-docker-container-deployment?view=sql-server-ver17), [Docker Compose environment variables](https://docs.docker.com/compose/how-tos/environment-variables/).
