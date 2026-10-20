#!/usr/bin/env python3
"""Phase 5A: verify the staged PostgreSQL build using disposable local data only."""
import os
import pathlib
import secrets
import shutil
import signal
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
PG = ROOT / "desktop/bundle/postgresql"
BIN = PG / "bin"
SCHEMA = ROOT / "backend/conception/bdd"
JAVA = ROOT / "desktop/bundle/runtime/bin/java"
JAR = ROOT / "desktop/bundle/backend.jar"


def run(args, *, env=None, input_text=None):
    result = subprocess.run(args, env=env, input=input_text, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if result.returncode:
        # Never print SQL input or credential-bearing environment values.
        raise RuntimeError(f"{pathlib.Path(args[0]).name} failed: {result.stderr[-1000:]}")
    return result.stdout.strip()


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def private_dir(path):
    path.mkdir(parents=True, mode=0o700)
    path.chmod(0o700)
    return path


def quote_pg(value):
    return "'" + str(value).replace("'", "''") + "'"


def psql(socket_dir, port, user, password, database, *, sql=None, file=None):
    env = {"PATH": "/usr/bin:/bin:/usr/sbin:/sbin", "PGPASSWORD": password}
    args = [str(BIN / "psql"), "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1",
            "-h", str(socket_dir), "-p", str(port), "-U", user, "-d", database]
    if file:
        args.extend(["-f", str(file)])
    else:
        args.extend(["-c", sql])
    return run(args, env=env)


def ready(port):
    return subprocess.run([str(BIN / "pg_isready"), "-h", "127.0.0.1",
                           "-p", str(port), "-q"], stdout=subprocess.DEVNULL,
                          stderr=subprocess.DEVNULL).returncode == 0


def start_server(cluster, config, log, port):
    out = log.open("ab")
    process = subprocess.Popen([str(BIN / "postgres"), "-D", str(cluster),
                                "-c", f"config_file={config}"],
                               stdin=subprocess.DEVNULL, stdout=out, stderr=out,
                               env={"PATH": "/usr/bin:/bin:/usr/sbin:/sbin"})
    out.close()
    for _ in range(120):
        if ready(port):
            return process
        if process.poll() is not None:
            raise RuntimeError("postgres stopped during startup: " + log.read_text()[-1200:])
        time.sleep(.1)
    raise RuntimeError("postgres startup timed out")


def stop_server(cluster, process):
    run([str(BIN / "pg_ctl"), "-D", str(cluster), "-m", "fast", "-w", "stop"])
    process.wait(timeout=10)


def main():
    global PG, BIN
    os.umask(0o077)
    for name in ("postgres", "initdb", "pg_ctl", "pg_isready", "pg_dump", "pg_restore", "psql"):
        if not (BIN / name).is_file():
            raise RuntimeError(f"Missing staged executable: {name}")
    with tempfile.TemporaryDirectory(prefix="galaxam-phase5a-") as scratch:
        # Exercise a moved copy, never the original build prefix.
        moved_package = pathlib.Path(scratch) / "relocated-postgresql"
        shutil.copytree(PG, moved_package, symlinks=True)
        PG, BIN = moved_package, moved_package / "bin"
        home = private_dir(pathlib.Path(scratch) / "GALAXAM SIRH")
        base = private_dir(home / "data/postgresql")
        cluster_pending = base / "cluster.pending"
        cluster = base / "cluster"
        config_dir = private_dir(base / "config")
        socket_dir = "127.0.0.1"
        log_dir = private_dir(base / "logs")
        backup_dir = private_dir(base / "backups")
        port = free_port()
        admin_password = secrets.token_hex(32)
        app_password = secrets.token_hex(32)
        password_file = config_dir / "initdb.pw"
        password_file.write_text(admin_password + "\n")
        password_file.chmod(0o600)
        run([str(BIN / "initdb"), "-D", str(cluster_pending), "-U", "galaxam_cluster_admin",
             "--encoding=UTF8", "--locale=C", "--auth-local=scram-sha-256",
             "--auth-host=scram-sha-256", f"--pwfile={password_file}"])
        password_file.unlink()
        print("initdb=ok")
        config = config_dir / "postgresql.conf"
        hba = config_dir / "pg_hba.conf"
        hba.write_text("local all all scram-sha-256\nhost all all 127.0.0.1/32 scram-sha-256\n")
        config.write_text("listen_addresses = '127.0.0.1'\n"
                          f"port = {port}\n"
                          "unix_socket_directories = ''\n"
                          f"hba_file = {quote_pg(hba)}\n"
                          "ssl = off\n")
        config.chmod(0o600)
        hba.chmod(0o600)
        log = log_dir / "postgres.log"
        process = start_server(cluster_pending, config, log, port)
        try:
            env_admin = (socket_dir, port, "galaxam_cluster_admin", admin_password, "postgres")
            psql(*env_admin, sql=f"CREATE ROLE galaxam_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD {quote_pg(app_password)}")
            psql(*env_admin, sql="CREATE DATABASE galaxam_test OWNER galaxam_app")
            for number in range(1, 7):
                schema_file = next(SCHEMA.glob(f"{number:02d}_schema_*.sql"))
                psql(socket_dir, port, "galaxam_app", app_password, "galaxam_test", file=schema_file)
            app_conn = (socket_dir, port, "galaxam_app", app_password, "galaxam_test")
            version = psql(*app_conn, sql="SHOW server_version")
            ext = psql(*app_conn, sql="SELECT extname FROM pg_extension WHERE extname='btree_gist'")
            tables = int(psql(*app_conn, sql="SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'"))
            role_flags = psql(*env_admin, sql="SELECT rolsuper::int::text || rolcreatedb::int::text || rolcreaterole::int::text FROM pg_roles WHERE rolname='galaxam_app'")
            listen = psql(*app_conn, sql="SHOW listen_addresses")
            if (not version.startswith("15.19") or ext != "btree_gist" or tables < 20
                    or role_flags != "000" or listen != "127.0.0.1"):
                raise RuntimeError("schema validation failed")
            print(f"version={version} btree_gist=ok tables={tables}")
            print("role_privileges=restricted loopback_only=ok")
            psql(*app_conn, sql="CREATE TABLE phase5a_marker (id integer PRIMARY KEY, label text NOT NULL)")
            psql(*app_conn, sql="INSERT INTO phase5a_marker VALUES (1, 'restored')")
            dump = backup_dir / "galaxam_test.dump"
            pg_env = {"PATH": "/usr/bin:/bin:/usr/sbin:/sbin", "PGPASSWORD": app_password}
            run([str(BIN / "pg_dump"), "-Fc", "-h", str(socket_dir), "-p", str(port),
                 "-U", "galaxam_app", "-f", str(dump), "galaxam_test"], env=pg_env)
            psql(*env_admin, sql="CREATE DATABASE galaxam_restore OWNER galaxam_app")
            run([str(BIN / "pg_restore"), "--no-owner", "--exit-on-error", "-h", str(socket_dir),
                 "-p", str(port), "-U", "galaxam_app", "-d", "galaxam_restore", str(dump)], env=pg_env)
            restored = psql(socket_dir, port, "galaxam_app", app_password, "galaxam_restore",
                            sql="SELECT label FROM phase5a_marker WHERE id=1")
            if restored != "restored":
                raise RuntimeError("restore marker missing")
            print("dump_restore=ok")
            if JAVA.is_file() and JAR.is_file():
                api_port = free_port()
                backend_home = private_dir(home / "backend")
                private_dir(backend_home / "logs")
                env_java = {"PATH": "/usr/bin:/bin:/usr/sbin:/sbin", "HOME": os.environ["HOME"],
                            "GALAXAM_DESKTOP_HOME": str(backend_home),
                            "GALAXAM_DESKTOP_DB_URL": f"jdbc:postgresql://127.0.0.1:{port}/galaxam_test",
                            "GALAXAM_DESKTOP_DB_USER": "galaxam_app",
                            "GALAXAM_DESKTOP_DB_PASSWORD": app_password,
                            "GALAXAM_DESKTOP_JWT_SECRET": secrets.token_hex(64),
                            "GALAXAM_BACKEND_PORT": str(api_port)}
                backend_log = (backend_home / "logs/backend-console.log").open("ab")
                backend = subprocess.Popen([str(JAVA), "-Dspring.profiles.active=desktop", "-jar", str(JAR)],
                                           env=env_java, stdout=backend_log, stderr=backend_log)
                backend_log.close()
                try:
                    health = False
                    for _ in range(160):
                        try:
                            with urllib.request.urlopen(f"http://127.0.0.1:{api_port}/api/auth/ping", timeout=.4) as response:
                                health = response.status == 200
                        except (urllib.error.URLError, TimeoutError):
                            pass
                        if health or backend.poll() is not None:
                            break
                        time.sleep(.25)
                    if not health:
                        raise RuntimeError("Spring Boot JDBC health check failed")
                    print("spring_jdbc_ping=ok")
                finally:
                    backend.send_signal(signal.SIGTERM)
                    backend.wait(timeout=10)
        finally:
            stop_server(cluster_pending, process)
        cluster_pending.rename(cluster)
        process = start_server(cluster, config, log, port)
        try:
            marker = psql(socket_dir, port, "galaxam_app", app_password, "galaxam_test",
                          sql="SELECT label FROM phase5a_marker WHERE id=1")
            if marker != "restored":
                raise RuntimeError("restart marker missing")
            print("restart_persistence=ok")
        finally:
            stop_server(cluster, process)
        for directory in (base, cluster, config_dir, log_dir, backup_dir):
            if directory.stat().st_mode & 0o077:
                raise RuntimeError("PostgreSQL directory permissions too broad")
        for file in (config, hba, log, dump):
            if file.stat().st_mode & 0o077:
                raise RuntimeError("PostgreSQL file permissions too broad")
        print("private_data_permissions=ok")


if __name__ == "__main__":
    main()
