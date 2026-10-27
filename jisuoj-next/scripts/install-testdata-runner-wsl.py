"""Install the local rootless runner account and service; run explicitly as WSL root.

Packages must already be installed. No API keys or service tokens are printed.
The image is built and smoke-tested separately before enabling the service.
"""
import argparse
import json
import os
from pathlib import Path
import pwd
import re
import secrets
import shutil
import subprocess

PROJECT = Path(__file__).resolve().parents[1]
ACCOUNT = "jisuoj-runner"
INSTALL = Path("/opt/jisuoj-testdata")


def write_private(path, content, uid, gid):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    path.chmod(0o600)
    os.chown(path, uid, gid)


def main():
    parser = argparse.ArgumentParser(description="Install the rootless test-data runner and backend environment file")
    parser.add_argument("--backend-user", required=True, help="Existing non-root Linux account running the backend")
    options = parser.parse_args()
    if os.geteuid() != 0:
        raise SystemExit("Run explicitly as WSL root to install this service")
    try:
        backend_user = pwd.getpwnam(options.backend_user)
    except KeyError:
        raise SystemExit("The backend account must already exist")
    if backend_user.pw_uid == 0 or backend_user.pw_name == ACCOUNT:
        raise SystemExit("Use a separate non-root backend account")
    try:
        account = pwd.getpwnam(ACCOUNT)
        if account.pw_dir != "/home/" + ACCOUNT or account.pw_shell != "/usr/sbin/nologin":
            raise SystemExit("Existing runner account has unexpected settings; refusing to change it")
    except KeyError:
        subprocess.run(["useradd", "--create-home", "--home-dir", "/home/" + ACCOUNT,
                        "--shell", "/usr/sbin/nologin", ACCOUNT], check=True)
        account = pwd.getpwnam(ACCOUNT)
    for filename in ("/etc/subuid", "/etc/subgid"):
        if not any(line.startswith(ACCOUNT + ":") for line in Path(filename).read_text().splitlines()):
            raise SystemExit("Missing subordinate ID allocation: " + filename)

    INSTALL.mkdir(mode=0o755, exist_ok=True)
    for filename in ("server.py", "execute.py", "Dockerfile.sandbox", "smoke_test.py"):
        destination = INSTALL / filename
        shutil.copyfile(PROJECT / "testdata-runner" / filename, destination)
        destination.chmod(0o644)
        os.chown(destination, 0, 0)

    runner_home = Path(account.pw_dir)
    config_dir = runner_home / ".config/jisuoj"
    config_dir.mkdir(parents=True, exist_ok=True)
    config_dir.chmod(0o700)
    os.chown(config_dir, account.pw_uid, account.pw_gid)
    token_file = config_dir / "runner.env"
    token = None
    if token_file.exists():
        for line in token_file.read_text().splitlines():
            if line.startswith("TEST_DATA_RUNNER_TOKEN="):
                candidate = line.split("=", 1)[1]
                if re.fullmatch(r"[a-f0-9]{64}", candidate):
                    token = candidate
    token = token or secrets.token_hex(32)
    write_private(token_file, "TEST_DATA_RUNNER_TOKEN=" + token +
                  "\nTEST_DATA_BIND=127.0.0.1\nTEST_DATA_SANDBOX_IMAGE=jisuoj-testdata-sandbox:local\n",
                  account.pw_uid, account.pw_gid)

    unit_dir = runner_home / ".config/systemd/user"
    unit_dir.mkdir(parents=True, exist_ok=True)
    unit = """[Unit]
Description=JISUOJ test-data rootless execution service

[Service]
Type=simple
WorkingDirectory=/opt/jisuoj-testdata
EnvironmentFile=%h/.config/jisuoj/runner.env
Environment=PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
ExecStart=/usr/bin/python3 -I /opt/jisuoj-testdata/server.py
Restart=on-failure
RestartSec=3
Delegate=yes
TasksMax=512
MemoryMax=1536M
UMask=0077

[Install]
WantedBy=default.target
"""
    unit_path = unit_dir / "jisuoj-testdata-runner.service"
    unit_path.write_text(unit)
    for path in (runner_home / ".config", runner_home / ".config/systemd", unit_dir, unit_path):
        os.chown(path, account.pw_uid, account.pw_gid)
    unit_path.chmod(0o644)

    backend_config = Path(backend_user.pw_dir) / ".config/jisuoj"
    backend_config.mkdir(parents=True, exist_ok=True)
    backend_config.chmod(0o700)
    os.chown(backend_config, backend_user.pw_uid, backend_user.pw_gid)
    write_private(backend_config / "testdata.env", "TEST_DATA_RUNNER_URL=http://127.0.0.1:8766\n" +
                  "TEST_DATA_RUNNER_TOKEN=" + token + "\n", backend_user.pw_uid, backend_user.pw_gid)

    subprocess.run(["loginctl", "enable-linger", ACCOUNT], check=True)
    subprocess.run(["systemctl", "start", f"user@{account.pw_uid}.service"], check=True)
    print(json.dumps({"runnerUser": ACCOUNT, "uid": account.pw_uid, "sourceDirectory": str(INSTALL),
                      "backendEnvironmentFile": str(backend_config / "testdata.env"), "token": "saved privately"}))


if __name__ == "__main__":
    main()
