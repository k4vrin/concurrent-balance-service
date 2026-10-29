#!/usr/bin/env python3
"""Create disposable CI-only SQL Server identities and explicit verification settings.

Requires an explicit Developer-license flag. Existing local configuration is never
overwritten; generated secrets stay in ignored dotenv files and GitHub environment
storage, with GitHub masking enabled before later commands can print them.
"""
import argparse
import os
from pathlib import Path
import secrets


def prepare(config_path, github_env):
    path = Path(config_path)
    if path.exists():
        raise FileExistsError("Refusing to overwrite existing SQL Server configuration")
    passwords = {key: "Aa1_" + secrets.token_hex(20) for key in
                 ["MSSQL_SA_PASSWORD", "CBS_APP_PASSWORD", "CBS_MIGRATOR_PASSWORD", "CBS_INITIALIZER_PASSWORD"]}
    for password in passwords.values():
        print("::add-mask::" + password)
    env = dict(passwords, ACCEPT_EULA="Y", SQLSERVER_PORT="14333")
    base = "jdbc:sqlserver://127.0.0.1:14333;databaseName="
    tail = ";encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=5000"
    for prefix, user, key in [("SQLSERVER_TEST", "cbs_app", "CBS_APP_PASSWORD"),
                               ("SQLSERVER_MIGRATION_TEST", "cbs_migrator", "CBS_MIGRATOR_PASSWORD"),
                               ("SQLSERVER_INITIALIZER_TEST", "cbs_initializer", "CBS_INITIALIZER_PASSWORD")]:
        env[prefix + "_URL"] = base + "cbs_test" + tail
        env[prefix + "_USERNAME"] = user
        env[prefix + "_PASSWORD"] = passwords[key]
    env.update(MIGRATION_DB_URL=base + "cbs_dev" + tail, MIGRATION_DB_USERNAME="cbs_migrator",
               MIGRATION_DB_PASSWORD=passwords["CBS_MIGRATOR_PASSWORD"])
    with path.open("x") as output:
        path.chmod(0o600)
        output.write("\n".join(k + "=" + v for k, v in env.items()) + "\n")
    with Path(github_env).open("a") as output:
        output.write("\n".join(k + "=" + v for k, v in env.items()) + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--accept-developer-license", action="store_true", required=True)
    parser.add_argument("--config", default=".env.sqlserver")
    args = parser.parse_args()
    prepare(args.config, os.environ["GITHUB_ENV"])
