> CBS09 implementation update, 2026-10-23: V1 is applied and validated in the local development/test databases. [Schema/runbook](Schema.md) records actual constraints, grants and 240 portable + 94 SQL Server tests. Historical pre-migration statements below are superseded by this implementation evidence. Financial execution remains pending.

# Local SQL Server with Docker Compose

[فارسی](../fa/Docker.md) · [Index](../../README.md) · [Database review](Database-review.md)

CBS07 local experiment, 2026-10-23: after explicit user license acceptance and startup authorization, the pinned SQL Server container is healthy, both empty databases accept real JDBC connections and they survived a container restart. See [measured setup evidence](SQL-server-evidence.md). This is unsupported ARM64 emulation, not supported-platform or financial correctness evidence. Formal acceptance remains open.

## Selected configuration and support boundary

[compose.yaml](../../compose.yaml) pins SQL Server 2022 CU22 on Ubuntu 22.04, Developer edition, by tag and immutable manifest digest:

```text
mcr.microsoft.com/mssql/server:2022-CU22-ubuntu-22.04
sha256:db9a8fe3098b7e8bbde41106bdc7caee942e97124e5fdb71b872ca208de3092d
```

Microsoft's registry returned that digest on 2026-10-23. It is a selected reproducible baseline, not a claim to be the newest security update. The downloaded/running image, server build and edition are recorded in [setup evidence](SQL-server-evidence.md). The sqlcmd path is `/opt/mssql-tools18/bin/sqlcmd`, used by SQL Server 2022 images from CU14 onward. [Microsoft deployment reference](https://learn.microsoft.com/en-us/sql/linux/quickstart-install-connect-docker).

Microsoft supports Linux SQL Server containers on Intel/AMD x86-64 Linux hosts. ARM64 translation/emulation, including Rosetta/QEMU, is unsupported. Our explicit linux/amd64 setting permits a local experiment on this ARM64 Mac; it does not establish support or compatibility. Use an x86-64 Docker host if emulation fails or supported-platform evidence is required. Do not substitute H2/Oracle or another engine. [Microsoft architecture boundary](https://learn.microsoft.com/en-us/sql/linux/sql-server-linux-docker-container-deployment).

The service publishes `127.0.0.1:14333` by default, persists `/var/opt/mssql` in a named project volume, has a bounded readiness check and does not auto-restart. SQL Server's configured memory limit is 2,048 MB and the container limit is 4 GB. These are local setup choices, not measured capacity recommendations. Allow enough Docker VM memory and disk for SQL Server and image extraction; do not start if the host cannot spare them.

Only SQL Server is included. Redis, Kafka and Connect/Debezium remain in their later track stages. The application still runs on the host; normal Spring startup requires a configured database, but this setup is not proof of application startup or migrations.

## External secrets and license choice

Copy [.env.example](../../.env.example) to the ignored `.env.sqlserver`. Fill locally:

- `ACCEPT_EULA=Y` only after explicitly accepting Microsoft's applicable SQL Server Developer license for development/testing.
- Distinct `MSSQL_SA_PASSWORD` and `CBS_APP_PASSWORD`; no actual value belongs in Git, screenshots or evidence.
- Application password: 16–128 ASCII letters/digits/underscore/hyphen, with upper/lowercase and a digit. This bootstrap restriction prevents SQL metacharacters in sqlcmd variable substitution. It is not the general SQL Server password syntax.
- `SQLSERVER_PORT` defaults to 14333. Choose another loopback port if occupied.

```sh
cp .env.example .env.sqlserver
chmod 600 .env.sqlserver
# Edit the ignored file locally. Do not paste its values into chat or logs.
docker compose --env-file .env.sqlserver config --quiet
```

Use config --quiet for validation; plain config or config --environment can reveal interpolated secrets. Compose environment variables are visible to users with Docker inspection access; this local setup does not provide a production secret store. Spring does not automatically load Compose's env file. [Compose interpolation](https://docs.docker.com/reference/compose-file/interpolation).

## Start, bootstrap and inspect

These commands start a service, pull an image if absent and allocate a persistent volume. Run only after the license/resource choice above:

```sh
docker version
docker compose version
docker compose --env-file .env.sqlserver up -d --wait --wait-timeout 360 sqlserver
docker compose --env-file .env.sqlserver exec -T sqlserver bash /cbs-bootstrap/bootstrap.sh
docker compose --env-file .env.sqlserver ps
docker compose --env-file .env.sqlserver logs --tail 100 sqlserver
```

[bootstrap.sh](../../scripts/sqlserver/bootstrap.sh) validates the application secret before calling [bootstrap.sql](../../scripts/sqlserver/bootstrap.sql). The SQL script creates empty `cbs_dev` and `cbs_test` databases and a dedicated `cbs_app` login/user with CONNECT. It prints server version/edition/collation, compatibility and snapshot isolation settings, and connection encryption status. The shell verifies app connectivity and prints sysadmin/CREATE TABLE permission checks for both databases. It never seeds money or creates the financial schema.

Rerunning bootstrap reuses existing databases/login/users and does not drop data or rotate passwords. If the env file no longer matches the retained application login, verification fails. Restore the matching secret or perform an explicit reviewed rotation; do not delete a volume as a connection fix. The two databases isolate names/data but share one local login; stronger per-environment identities remain a later deployment choice.

The application login intentionally has no db_owner, elevated server role or DDL role. CBS02–04/CBS09 must review migration and runtime permissions before financial migrations. In particular, this login alone cannot create Flyway history/business tables; do not advertise normal application startup as working from this bootstrap. Keep sa for bootstrap administration, not application runtime.

The sqlcmd -C flag trusts the local server certificate. JDBC uses encrypt=true plus an explicit development-only trustServerCertificate=true exception on loopback. This encrypts transport without validating server identity against a trusted certificate. External deployments need a valid certificate/hostname/trust policy; do not carry this exception into them. [sqlcmd variables](https://learn.microsoft.com/en-us/sql/tools/sqlcmd/sqlcmd-use-scripting-variables).

## Prove the actual JDBC driver connects

[SqlServerProbe.java](../../scripts/sqlserver/SqlServerProbe.java) is a source-mode Java 21 connectivity probe, not the CBS08 integration harness. It uses the project's actual JDBC classpath, requires credentials from the environment, runs SELECT and refuses a sysadmin application connection. It does not log the URL or password, run Spring, alter schema or test transactions.

With Java 21 JAVA_HOME and exported DB_URL, DB_USERNAME=cbs_app and DB_PASSWORD matching the local application secret:

```sh
./mvnw dependency:build-classpath -Dmdep.outputFile=/tmp/cbs-jdbc-classpath
"$JAVA_HOME/bin/java" --class-path "$(cat /tmp/cbs-jdbc-classpath)" scripts/sqlserver/SqlServerProbe.java
```

For cbs_dev the URL is `jdbc:sqlserver://127.0.0.1:14333;databaseName=cbs_dev;encrypt=true;trustServerCertificate=true`. Quote it in shells because of semicolons. Repeat with databaseName=cbs_test. Keep credentials in connection properties/environment, never inside the URL. PowerShell can read the classpath with `Get-Content -Raw /tmp/cbs-jdbc-classpath` and invoke Java with `&`; use a local temporary path on Windows.

A successful SELECT proves connectivity only. CBS08 still needs a Maven Failsafe sqlserver-it profile, positive actual test count, absent-database failure and rollback/committed-fixture evidence. Do not activate the persistence-disabled smoke profile for those tests. CBS09 business migrations remain blocked on the concrete constraint/transaction review.

## Stop, retain data and reset deliberately

```sh
docker compose --env-file .env.sqlserver stop sqlserver
docker compose --env-file .env.sqlserver start sqlserver
# Remove the container/network while preserving its named data volume:
docker compose --env-file .env.sqlserver down
```

Destructive reset, only when deliberately discarding both development and test data:

```sh
docker compose --env-file .env.sqlserver down --volumes
```

Changing sa environment variables does not constitute a password-rotation procedure for retained data. No automatic cleanup or volume deletion is part of this task.

## Evidence checklist and current verification

Record image digest, host/daemon architecture, exact server build/edition, database compatibility/collation, RCSI/snapshot settings, encryption flag, dedicated-login privileges and JDBC driver version/SELECT results. Never include credentials. Version pinning and config validation are not successful runtime evidence.

Prepared here: Docker client/engine 28.2.2, Docker Desktop 4.42.0, daemon linux/arm64 and Compose 2.36.2; Microsoft registry manifest verified; Compose config validated using nonsecret placeholders without launching a container; shell syntax checked; Java probe compiled with Java 21. The authorized local experiment subsequently verified healthy startup, actual server metadata, JDBC to both databases and survival across one container restart; details and limitations are in [setup evidence](SQL-server-evidence.md). No financial tables or balances were initialized. CBS00 independent learner acceptance is unchanged.

The environment can also run on an explicitly selected x86-64 Docker host. Recheck loopback/TLS assumptions at that host rather than exposing port 1433 broadly. [Native Windows](Windows-sqlserver.md) remains optional.

## CBS08 automated harness

The [SQL Server test guide](SQL-server-tests.md) now documents the sqlserver-it Failsafe profile, dedicated test variables, temporary fixtures and failure guards. The standalone CBS07 probe remains useful for diagnosis; it is not the automated harness.
