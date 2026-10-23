#!/usr/bin/env bash
set -euo pipefail
project_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
: "${CBS_MIGRATOR_PASSWORD:?Export the migration secret}"
: "${CBS_INITIALIZER_PASSWORD:?Export the initializer secret}"
# Feed secrets through stdin, not docker command arguments or captured output.
printf '%s\n' "$CBS_MIGRATOR_PASSWORD" "$CBS_INITIALIZER_PASSWORD" | \
    docker compose --project-directory "$project_root" --env-file "$project_root/.env.sqlserver" \
    exec -T sqlserver bash -c 'read -r CBS_MIGRATOR_PASSWORD; read -r CBS_INITIALIZER_PASSWORD; export CBS_MIGRATOR_PASSWORD CBS_INITIALIZER_PASSWORD; exec bash /cbs-bootstrap/provision-migration.sh'
