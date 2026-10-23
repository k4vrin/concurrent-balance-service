#!/usr/bin/env bash
set -euo pipefail
project_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
docker compose --project-directory "$project_root" --env-file "$project_root/.env.sqlserver" exec -T sqlserver \
    bash -c 'export SQLCMDPASSWORD="$MSSQL_SA_PASSWORD"; for database in cbs_dev cbs_test; do /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -l 5 -b -d "$database" -i /cbs-bootstrap/grant-runtime.sql || exit; done'
