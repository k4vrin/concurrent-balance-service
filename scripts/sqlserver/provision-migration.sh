#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C
for key in CBS_MIGRATOR_PASSWORD CBS_INITIALIZER_PASSWORD; do
    value=${!key:-}
    if [[ ! $value =~ ^[A-Za-z0-9_-]{16,128}$ ]] || [[ ! $value =~ [A-Z] ]] \
        || [[ ! $value =~ [a-z] ]] || [[ ! $value =~ [0-9] ]]; then
        echo "$key requires 16–128 ASCII letters/digits/_/-, with upper/lowercase and a digit." >&2
        exit 1
    fi
    if [[ $value == "${MSSQL_SA_PASSWORD:-}" || $value == "${CBS_APP_PASSWORD:-}" ]]; then
        echo 'Migration, initializer, runtime and administrator secrets must be distinct.' >&2
        exit 1
    fi
done
if [[ $CBS_MIGRATOR_PASSWORD == "$CBS_INITIALIZER_PASSWORD" ]]; then
    echo 'Migration and initializer secrets must be distinct.' >&2
    exit 1
fi
export SQLCMDPASSWORD="${MSSQL_SA_PASSWORD:?Administrator password is required}"
/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -l 5 -b -i /cbs-bootstrap/provision-migration.sql
for database in cbs_dev cbs_test; do
    SQLCMDPASSWORD="$CBS_MIGRATOR_PASSWORD" /opt/mssql-tools18/bin/sqlcmd -S localhost -U cbs_migrator \
        -d "$database" -C -l 5 -b -Q "IF IS_SRVROLEMEMBER('sysadmin') <> 0 OR IS_MEMBER('db_owner') <> 0 THROW 51000, 'Unexpected migration authority', 1;"
    SQLCMDPASSWORD="$CBS_INITIALIZER_PASSWORD" /opt/mssql-tools18/bin/sqlcmd -S localhost -U cbs_initializer \
        -d "$database" -C -l 5 -b -Q "IF IS_SRVROLEMEMBER('sysadmin') <> 0 OR IS_MEMBER('db_owner') <> 0 THROW 51000, 'Unexpected initializer authority', 1;"
done
