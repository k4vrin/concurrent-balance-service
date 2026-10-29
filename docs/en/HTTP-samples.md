# Run the HTTP samples locally

[فارسی](../fa/HTTP-samples.md) · [README](../../README.md) · [Request file](../../http/balance-service.http) · [API contract](API-v1.md)

The file you execute is an **HTTP Client request file**, with the `.http` extension. The collection uses the shared `@variable = value`, `{{variable}}` and `###` syntax documented by [IntelliJ IDEA HTTP Client](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html) and [VS Code REST Client](https://github.com/Huachao/vscode-restclient). It has no editor-specific scripts, generated request IDs or credentials.

## 1. Prepare SQL Server

Run the commands in **Bash from the repository root**, with JDK 21 selected. Use `.env.example` as a template; if `.env.sqlserver` already exists, edit it rather than overwriting it.

```bash
cp .env.example .env.sqlserver
chmod 600 .env.sqlserver
```

Edit the ignored file locally. Set `ACCEPT_EULA=Y` after accepting the SQL Server Developer license, and choose four distinct secrets: `MSSQL_SA_PASSWORD`, `CBS_APP_PASSWORD`, `CBS_MIGRATOR_PASSWORD`, `CBS_INITIALIZER_PASSWORD`. The three restricted-login secrets require 16–128 ASCII letters/digits/underscore/hyphen, including uppercase, lowercase and a digit. Use port 14333 below, or consistently substitute your selected `SQLSERVER_PORT`.

The pinned image needs Docker resources; ARM64 emulation is an unsupported local experiment. See [Docker setup](Docker.md) for platform/license details. Start and bootstrap:

```bash
docker compose --env-file .env.sqlserver config --quiet
docker compose --env-file .env.sqlserver up -d --wait --wait-timeout 360 sqlserver
docker compose --env-file .env.sqlserver exec -T sqlserver bash /cbs-bootstrap/bootstrap.sh
```

The env file is Compose input, not a shell script. **Do not `source` it**: JDBC URLs contain shell metacharacters, and Spring Boot does not load it automatically. Enter the same migration and initializer passwords you put in the file when prompted:

```bash
read -r -s -p 'Migration password: ' CBS_MIGRATOR_PASSWORD; printf '\n'
read -r -s -p 'Initializer password: ' CBS_INITIALIZER_PASSWORD; printf '\n'
export CBS_MIGRATOR_PASSWORD CBS_INITIALIZER_PASSWORD
bash scripts/sqlserver/provision-migration-local.sh
export MIGRATION_DB_USERNAME=cbs_migrator
export MIGRATION_DB_PASSWORD="$CBS_MIGRATOR_PASSWORD"
for migration_database in cbs_dev cbs_test; do
  export MIGRATION_DB_URL="jdbc:sqlserver://127.0.0.1:14333;databaseName=$migration_database;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=5000"
  ./mvnw -Pdb-migrate flyway:migrate flyway:validate || break
done
```

Require success for **both** catalogs before continuing. Apply runtime grants:

```bash
bash scripts/sqlserver/grant-runtime-local.sh
unset MIGRATION_DB_URL MIGRATION_DB_USERNAME MIGRATION_DB_PASSWORD CBS_MIGRATOR_PASSWORD
```

These steps preserve existing data and do not rotate retained passwords. If setup fails, follow [Schema.md](Schema.md); do not reset the volume to hide a credentials/schema mismatch.

## 2. Create zero-balance demo accounts

Run [create-demo-accounts.sql](../../http/create-demo-accounts.sql) with `cbs_initializer` against **cbs_dev**. It refuses another catalog or an administrator/owner identity. The command below passes the secret and SQL through stdin; no secret is put in command arguments:

```bash
{
  printf '%s\n' "$CBS_INITIALIZER_PASSWORD"
  cat http/create-demo-accounts.sql
} | docker compose --env-file .env.sqlserver exec -T sqlserver bash -c \
  'read -r SQLCMDPASSWORD; export SQLCMDPASSWORD; exec /opt/mssql-tools18/bin/sqlcmd -S localhost -U cbs_initializer -d cbs_dev -C -b'
unset CBS_INITIALIZER_PASSWORD
```

| Role | UUID |
| --- | --- |
| Funding | `90000000-0000-4000-8000-000000000001` |
| Alice | `10000000-0000-4000-8000-000000000001` |
| Bob | `20000000-0000-4000-8000-000000000002` |

A repeat inserts only missing accounts, checks existing category/currency/status, and leaves balances/history intact. There is no nonzero SQL seed and no public account-creation route. Only HTTP credits introduce money. The fixture is for one local operator; do not run concurrent provisioning copies.

## 3. Start the application

In the same Bash terminal, enter the runtime password matching `CBS_APP_PASSWORD` in the ignored file:

```bash
export DB_URL='jdbc:sqlserver://127.0.0.1:14333;databaseName=cbs_dev;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=5000'
export DB_USERNAME=cbs_app
read -r -s -p 'Runtime password: ' DB_PASSWORD; printf '\n'
export DB_PASSWORD
export EXECUTION_POLICY_ENABLED=true
export EXECUTION_IRR_FUNDING_ACCOUNT_ID=90000000-0000-4000-8000-000000000001
export EXECUTION_IRR_FUNDING_POLICY_VERSION=demo-funding-v1
./mvnw spring-boot:run
```

Runtime receives no migration/initializer secret from the preceding steps. If your shell already exported administrator or other provisioning/test secrets, remove those before launching. In an IDE, configure these same six variables in the application's run configuration. Do not put migration credentials in that configuration. `DB_URL` must be quoted in a shell because it contains semicolons. Default server: `http://127.0.0.1:8080`; adjust `SERVER_PORT` and the request file's `baseUrl` together if needed.

## 4. Execute the request file

Open [http/balance-service.http](../../http/balance-service.http):

- **IntelliJ IDEA:** enable HTTP Client support and use the run control beside a request.
- **VS Code:** install/enable REST Client and select **Send Request** above a request.

Run the numbered requests sequentially, with no concurrent writer to the demo accounts. Both clients use the file's variables; no `http-client.env.json` is required. `runId=demo-v1` is intentionally stable. Keep it unchanged for retries, restarts and replay demonstrations. A per-request random value would test new operations instead.

## Expected walkthrough

These values assume fresh zero accounts, default IRR limits and the exact request order. Response status/codes are also recorded next to each request in the file.

| Requests | Expected result | Alice / Bob / funding (rials) |
| --- | --- | --- |
| 01–02 | Health 200/UP; Alice starts at zero. | 0 / 0 / 0 |
| 03–05 | Credit Alice 1000, replay it without another effect, credit Bob 500. | 1000 / 500 / 1500 |
| 06 | Debit Alice 100. | 900 / 500 / 1400 |
| 07–11 | Transfer 250, replay it, reject changed amount under the original ID; query both accounts. | 650 / 750 / 1400 |
| 12 | Debit 651 returns 409 `INSUFFICIENT_FUNDS`; no journal. | 650 / 750 / 1400 |
| 13–16 | Top up Alice 100; the rejected debit still replays its original 409. | 750 / 750 / 1500 |
| 17–20 | Zero/fractional amount, self-transfer and malformed UUID return 400. | Unchanged |
| 21–23 | Missing lookup/mutation return 404; over-cap credit returns 409 `TRANSACTION_LIMIT_EXCEEDED`. | Unchanged |

After all requests there are **8 retained demo operations**: 5 successes, 3 business rejections, 5 journals and 10 postings. The missing lookup, validation failures, conflict and replays add no operation. The missing-account examples assume the `missing` UUID has not been provisioned. Funding increases on credit and decreases on debit; its 1500-rial projection is the counterpart of the two customer liabilities.

Replaying the collection does not reset accounts: earlier intermediate balances will reflect the final state. The same IDs still return their original outcomes, including the insufficient-funds rejection. For a genuinely fresh demonstration, choose new customer/funding UUIDs in **both** SQL and `.http`, update the server's funding setting, and choose a new `runId`. Changing `runId` alone creates new financial attempts against existing balances and changes the expected scenario.

To demonstrate restart replay, stop/restart the application with the same settings and rerun requests 04, 08 and 14. Check requests 15–16 remain unchanged. For automated rollback/restart/concurrency evidence, use [core acceptance](Core-acceptance.md); clicking these sequential samples does not prove concurrent behavior.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Connection refused | Start the application; verify the loopback port and `baseUrl`. |
| Literal `${DB_URL}` / datasource startup failure | Export runtime variables in the launching process; the env file alone is insufficient. |
| 503 on all mutations | Enable execution, configure the funding UUID/version and verify the migrated schema/database connection. Keep the same transaction ID when recovering an uncertain outcome. |
| 404 for Alice/Bob | Run the fixture in `cbs_dev`; confirm runtime uses that catalog and matching UUIDs. |
| Unexpected balances | Existing history, a changed `runId`, a different request order or another writer; never fix this with a direct balance UPDATE. |
| 409 on a changed request | The original ID is retained. Replay it unchanged; use a new ID only for a new intended operation. |

Use `Ctrl+C` to stop the host application. `docker compose --env-file .env.sqlserver down` removes the container/network while retaining the database volume. Do not add `--volumes` unless you explicitly intend to discard data.

## Measured sample verification, 2026-10-29

All 23 requests were executed in order against the packaged application and the real local SQL Server `cbs_dev` catalog. Every expected status/code and intermediate balance matched; replay returned identical bodies. After an application-process restart, credit/transfer/rejection replay and final balances remained unchanged. Independent SQL confirmed 8 operations, 5 successes, 3 rejections, 5 journals, 10 postings and projection/history equality. Repeated fixture execution preserved accounts; wrong-catalog and runtime-identity attempts were rejected. These requests were executed by a temporary verifier reading this `.http` file; editor UI execution itself was not measured. Portable Maven verification also passed 421 tests, zero failures/errors/skips.
