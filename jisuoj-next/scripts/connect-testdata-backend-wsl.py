"""Connect the running local development backend without printing its credentials."""
import json
import os
from pathlib import Path
import signal
import subprocess
import time
import urllib.request

PROJECT = Path(__file__).resolve().parents[1]


def main():
    if os.geteuid() == 0:
        raise SystemExit("Run as the existing backend account, not root")
    settings = {}
    for line in (Path.home() / ".config/jisuoj/testdata.env").read_text().splitlines():
        if line.startswith(("TEST_DATA_RUNNER_URL=", "TEST_DATA_RUNNER_TOKEN=")):
            key, value = line.split("=", 1)
            settings[key] = value
    request = urllib.request.Request(settings["TEST_DATA_RUNNER_URL"] + "/health",
        headers={"Authorization": "Bearer " + settings["TEST_DATA_RUNNER_TOKEN"]})
    with urllib.request.urlopen(request, timeout=15) as response:
        if response.status != 200:
            raise SystemExit("Runner is not ready; backend was not changed")

    found = []
    for proc in Path("/proc").iterdir():
        if not proc.name.isdecimal():
            continue
        try:
            if proc.stat().st_uid != os.geteuid() or proc.joinpath("cwd").resolve() != PROJECT / "backend":
                continue
            args = proc.joinpath("cmdline").read_bytes().split(b"\0")
            if b"com.jisuoj.JisuOjApplication" in args:
                found.append(proc)
        except (FileNotFoundError, PermissionError, ProcessLookupError):
            pass
    if len(found) != 1:
        raise SystemExit("Expected exactly one running JISUOJ development backend; refusing to stop other processes")
    proc = found[0]
    environment = dict(entry.split("=", 1) for entry in proc.joinpath("environ").read_bytes().decode().split("\0") if "=" in entry)
    environment.update(settings)
    pid = int(proc.name)
    os.kill(pid, signal.SIGTERM)
    for _ in range(100):
        if not proc.exists():
            break
        time.sleep(0.2)
    else:
        raise SystemExit("Backend did not stop gracefully; it was not forcibly killed")
    log_path = PROJECT / "tmp/backend-rag.log"
    with log_path.open("ab") as log:
        child = subprocess.Popen(["bash", str(PROJECT / "scripts/dev-backend-wsl.sh")], cwd=PROJECT / "backend",
            env=environment, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    (PROJECT / "tmp/backend-rag.pid").write_text(str(child.pid) + "\n")
    for _ in range(90):
        try:
            with urllib.request.urlopen("http://127.0.0.1:8080/actuator/health", timeout=2) as response:
                if response.status == 200 and json.load(response).get("status") == "UP":
                    print(json.dumps({"backend": "UP", "runnerUrl": settings["TEST_DATA_RUNNER_URL"],
                                      "previousPid": pid, "launcherPid": child.pid, "credentials": "preserved, not printed"}))
                    return
        except Exception:
            pass
        time.sleep(1)
    raise SystemExit("Backend startup did not pass health check; see tmp/backend-rag.log")


if __name__ == "__main__":
    main()
