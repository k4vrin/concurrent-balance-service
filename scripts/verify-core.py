#!/usr/bin/env python3
"""Verify the core suite and a separate packaged-JAR HTTP/JDBC acceptance scenario.

Uses only the dedicated cbs_test catalog and fresh UUID fixtures. Secrets stay in
process environments, and schema/history are never cleaned or reset. The optional
local env file is parsed as data, never executed as shell code.
"""
import argparse
import hashlib
from contextlib import contextmanager
import json
import os
from pathlib import Path
import re
import selectors
import shutil
import socket
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
import uuid
import zipfile

ROOT = Path(__file__).resolve().parent.parent


def environment(env_file):
    env = os.environ.copy()
    if env_file:
        config = {}
        for line in Path(env_file).read_text().splitlines():
            if line.strip() and not line.lstrip().startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                if not re.fullmatch(r"[A-Z][A-Z0-9_]*", key.strip()):
                    raise ValueError("Invalid environment key")
                config[key.strip()] = value.strip().strip("\"'")
        port = int(config.get("SQLSERVER_PORT", "14333"))
        if not 1 <= port <= 65535:
            raise ValueError("Invalid SQL Server port")
        url = f"jdbc:sqlserver://127.0.0.1:{port};databaseName=cbs_test;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=5000"
        for prefix, user, secret in [("SQLSERVER_TEST", "cbs_app", "CBS_APP_PASSWORD"),
                                     ("SQLSERVER_MIGRATION_TEST", "cbs_migrator", "CBS_MIGRATOR_PASSWORD"),
                                     ("SQLSERVER_INITIALIZER_TEST", "cbs_initializer", "CBS_INITIALIZER_PASSWORD")]:
            env[prefix + "_URL"] = url
            env[prefix + "_USERNAME"] = user
            env[prefix + "_PASSWORD"] = config[secret]
    for prefix in ["SQLSERVER_TEST", "SQLSERVER_MIGRATION_TEST", "SQLSERVER_INITIALIZER_TEST"]:
        for part in ["URL", "USERNAME", "PASSWORD"]:
            if not env.get(prefix + "_" + part):
                raise ValueError(prefix + "_" + part + " is required")
    if not env.get("JAVA_HOME") and Path("/usr/libexec/java_home").exists():
        env["JAVA_HOME"] = subprocess.check_output(["/usr/libexec/java_home", "-v", "21"], text=True).strip()
    return env


def http(base, path, body=None, expected=200):
    raw = None if body is None else (body.encode() if isinstance(body, str) else json.dumps(body).encode())
    request = Request(base + path, data=raw, headers={"Content-Type": "application/json"} if raw else {})
    try:
        with urlopen(request, timeout=30) as response:
            status, data = response.status, response.read()
    except HTTPError as failure:
        status, data = failure.code, failure.read()
    if status != expected:
        raise AssertionError(f"HTTP {status}, expected {expected}: {data.decode(errors='replace')}")
    return data


@contextmanager
def application(java, jar, env, funding, log_path):
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        port = listener.getsockname()[1]
    runtime = {k: v for k, v in env.items() if not k.startswith(("SQLSERVER_", "MIGRATION_", "CBS_", "MSSQL_",
                "SPRING_", "BALANCE_", "DB_", "EXECUTION_", "SERVER_"))
               and k not in {"JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS"}}
    runtime.update(DB_URL=env["SQLSERVER_TEST_URL"], DB_USERNAME=env["SQLSERVER_TEST_USERNAME"],
                   DB_PASSWORD=env["SQLSERVER_TEST_PASSWORD"], SERVER_ADDRESS="127.0.0.1", SERVER_PORT=str(port),
                   EXECUTION_POLICY_ENABLED="true", EXECUTION_IRR_FUNDING_ACCOUNT_ID=funding,
                   EXECUTION_IRR_FUNDING_POLICY_VERSION="core-acceptance-v1")
    with log_path.open("w") as log:
        process = subprocess.Popen([java, "-jar", str(jar)], env=runtime, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        base = f"http://127.0.0.1:{port}"
        try:
            deadline = time.monotonic() + 45
            while time.monotonic() < deadline:
                if process.poll() is not None:
                    raise RuntimeError("Packaged application exited; inspect runtime log")
                try:
                    if json.loads(http(base, "/actuator/health"))["status"] == "UP":
                        break
                except (URLError, OSError):
                    pass
                time.sleep(0.2)
            else:
                raise TimeoutError("Packaged application health deadline exceeded")
            yield base
        finally:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)


def run(args):
    env = environment(args.env_file)
    output = (ROOT / args.output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    java = str(Path(env["JAVA_HOME"]) / "bin/java") if env.get("JAVA_HOME") else shutil.which("java")
    version = subprocess.run([java, "-version"], capture_output=True, text=True, check=True).stderr
    if not re.search(r'version "21[.\"]', version):
        raise ValueError("JDK 21 is required")
    if not args.skip_build:
        wrapper = "mvnw.cmd" if os.name == "nt" else "./mvnw"
        with (output / "maven.log").open("w") as log:
            subprocess.run([wrapper, "-B", "-ntp", *(["-o"] if args.offline else []), "-Psqlserver-it", "verify"],
                           cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, check=True)
    jar = ROOT / "target/concurrent-balance-service-0.0.1-SNAPSHOT.jar"
    prefix = "core-" + str(uuid.uuid4()) + "-"
    funding, a, b = [str(uuid.uuid4()) for _ in range(3)]
    with tempfile.TemporaryDirectory(prefix="cbs-core-") as temp:
        temp = Path(temp)
        with zipfile.ZipFile(jar) as archive:
            drivers = [name for name in archive.namelist() if name.startswith("BOOT-INF/lib/mssql-jdbc-") and name.endswith(".jar")]
            if len(drivers) != 1:
                raise AssertionError("Expected one packaged JDBC driver")
            driver = temp / "driver.jar"
            driver.write_bytes(archive.read(drivers[0]))
        javac = str(Path(java).with_name("javac"))
        subprocess.run([javac, "-cp", str(driver), "-d", str(temp), str(ROOT / "scripts/acceptance/CoreAcceptanceProbe.java")], check=True)
        command = [java, "-cp", os.pathsep.join([str(temp), str(driver)]), "CoreAcceptanceProbe"]

        def probe(*parts):
            return subprocess.check_output([*command, *parts], cwd=ROOT, env=env, text=True).strip()

        def amount(suffix, value):
            return {"transactionId": prefix + suffix, "amountRial": value, "currency": "IRR"}

        def transfer(suffix, value):
            return dict(amount(suffix, value), sourceAccountId=a, destinationAccountId=b)

        def balances(base, expected_a, expected_b):
            for account, expected in [(a, expected_a), (b, expected_b)]:
                result = json.loads(http(base, f"/api/v1/accounts/{account}/balance"))
                if result["balanceRial"] != expected or result["currency"] != "IRR":
                    raise AssertionError("Unexpected authoritative balance")

        probe("seed", funding, a, b)
        with application(java, jar, env, funding, output / "runtime-first.log") as base:
            http(base, f"/api/v1/accounts/{a}/credits", amount("credit-a", 1000))
            http(base, f"/api/v1/accounts/{b}/credits", amount("credit-b", 500))
            http(base, f"/api/v1/accounts/{a}/debits", amount("debit", 100))
            original = http(base, "/api/v1/transfers", transfer("transfer", 250))
            balances(base, 650, 750)
            rejected = http(base, "/api/v1/transfers", transfer("rejected", 651), 409)
            if json.loads(rejected)["code"] != "INSUFFICIENT_FUNDS":
                raise AssertionError("Wrong business rejection")
            http(base, f"/api/v1/accounts/{a}/credits", amount("topup", 100))
            if http(base, "/api/v1/transfers", transfer("rejected", 651), 409) != rejected:
                raise AssertionError("Rejection changed after top-up")
            if http(base, "/api/v1/transfers", transfer("transfer", 250)) != original:
                raise AssertionError("Retained success response changed")
            conflict = http(base, "/api/v1/transfers", transfer("transfer", 251), 409)
            if json.loads(conflict)["code"] != "TRANSACTION_ID_CONFLICT":
                raise AssertionError("Wrong identity conflict")
            http(base, f"/api/v1/accounts/{a}/credits", json.dumps(amount("invalid", 1.5)), 400)
            probe("absent", prefix + "invalid")
            holder = subprocess.Popen([*command, "hold", a], env=env, cwd=ROOT, stdin=subprocess.PIPE,
                                      stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            try:
                with selectors.DefaultSelector() as ready:
                    ready.register(holder.stdout, selectors.EVENT_READ)
                    if not ready.select(timeout=10) or holder.stdout.readline().strip() != "LOCKED":
                        raise RuntimeError("JDBC lock holder did not become ready")
                http(base, "/api/v1/transfers", transfer("fault", 25), 503)
                probe("absent", prefix + "fault")
            finally:
                if holder.poll() is None:
                    holder.stdin.write("\n")
                    holder.stdin.flush()
                try:
                    _, error = holder.communicate(timeout=10)
                except subprocess.TimeoutExpired:
                    holder.kill()
                    holder.communicate()
                    raise RuntimeError("JDBC lock holder failed to exit")
                if holder.returncode:
                    raise RuntimeError("JDBC lock holder failed: " + error)
            resumed = http(base, "/api/v1/transfers", transfer("fault", 25))
            balances(base, 725, 775)
        with application(java, jar, env, funding, output / "runtime-restarted.log") as base:
            if http(base, "/api/v1/transfers", transfer("transfer", 250)) != original:
                raise AssertionError("Success replay changed after process restart")
            if http(base, "/api/v1/transfers", transfer("rejected", 651), 409) != rejected:
                raise AssertionError("Rejection replay changed after process restart")
            if http(base, "/api/v1/transfers", transfer("fault", 25)) != resumed:
                raise AssertionError("Recovered response changed after restart")
            balances(base, 725, 775)
        reconciliation = probe("check", prefix, funding, a, b)
    secrets = [v for k, v in env.items() if k.endswith("PASSWORD") and len(v) >= 8]
    for file in output.glob("*.log"):
        text = file.read_text(errors="replace")
        if any(secret in text for secret in secrets):
            raise AssertionError("A credential appeared in an acceptance log")
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    summary = {"status": "passed", "gitHead": head, "buildSkipped": args.skip_build,
               "workingTreeDirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip()),
               "artifactSha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
               "fixturePrefix": prefix, "operations": 7, "successes": 6, "rejections": 1,
               "journals": 6, "postings": 12, "balancesRial": [725, 775], "fundingBalanceRial": 1500,
               "processRestartVerified": True, "realLockTimeoutRollbackVerified": True,
               "reconciliation": reconciliation}
    (output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--env-file", help="Explicit ignored local SQL Server configuration")
    parser.add_argument("--output", default="target/core-acceptance")
    parser.add_argument("--offline", action="store_true", help="Use the existing Maven dependency cache")
    parser.add_argument("--skip-build", action="store_true", help="Run only packaged-artifact acceptance after a separate verified build")
    try:
        run(parser.parse_args())
    except Exception as failure:
        print(f"Core acceptance failed: {type(failure).__name__}: {failure}", file=sys.stderr)
        sys.exit(1)
