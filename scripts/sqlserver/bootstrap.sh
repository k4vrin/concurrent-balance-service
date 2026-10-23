#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

# sqlcmd substitutes this value into one SQL literal. Reject metacharacters rather than interpolate arbitrary SQL.
if [[ ! ${CBS_APP_PASSWORD:-} =~ ^[A-Za-z0-9_-]{16,128}$ ]] \
    || [[ ! $CBS_APP_PASSWORD =~ [A-Z] ]] \
    || [[ ! $CBS_APP_PASSWORD =~ [a-z] ]] \
    || [[ ! $CBS_APP_PASSWORD =~ [0-9] ]]; then
    echo 'CBS_APP_PASSWORD must have 16–128 ASCII letters/digits/_/-, including upper/lowercase and a digit.' >&2
    exit 1
fi
if [[ $CBS_APP_PASSWORD == "${MSSQL_SA_PASSWORD:-}" ]]; then
    echo 'Use distinct administrator and application passwords.' >&2
    exit 1
fi
export SQLCMDPASSWORD="${MSSQL_SA_PASSWORD:?Administrator password is required}"
/opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -C -l 5 -b -W -s "|" -i /cbs-bootstrap/bootstrap.sql

# On an existing volume, verify the retained login rather than silently rotating its password.
export SQLCMDPASSWORD="$CBS_APP_PASSWORD"
for database in cbs_dev cbs_test; do
    /opt/mssql-tools18/bin/sqlcmd -S localhost -U cbs_app -d "$database" -C -l 5 -b -W -s "|" \
        -Q "SET NOCOUNT ON; IF IS_SRVROLEMEMBER('sysadmin') <> 0 OR IS_MEMBER('db_owner') <> 0 OR HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'CREATE TABLE') <> 0 THROW 51000, 'Unexpected application privileges', 1; SELECT DB_NAME() AS database_name, 1 AS connected, IS_SRVROLEMEMBER('sysadmin') AS is_sysadmin, HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'CREATE TABLE') AS can_create_table;"
done
