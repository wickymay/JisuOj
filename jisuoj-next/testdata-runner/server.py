"""Internal coordinator using rootless Podman; no host control socket is exposed."""
import hashlib
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
import subprocess
import tempfile
import threading
import time
import uuid

FILE_LIMIT = 256 * 1024
IMAGE = os.environ.get("TEST_DATA_SANDBOX_IMAGE", "jisuoj-testdata-sandbox:local")
TOKEN = os.environ.get("TEST_DATA_RUNNER_TOKEN", "")
SLOTS = threading.BoundedSemaphore(2)


def sandbox_task(task, deadline):
    timeout = min(45 if task["action"] == "compile" else 15, deadline - time.monotonic())
    if timeout <= 0:
        raise ValueError("生成执行超过10分钟")
    name = "jisuoj-testdata-" + uuid.uuid4().hex
    args = ["podman", "run", "--rm", "--pull=never", "-i", "--name", name,
            "--network=none", "--read-only", "--user=65534:65534", "--cap-drop=ALL",
            "--security-opt=no-new-privileges", "--cpus=1", "--memory=512m", "--memory-swap=512m",
            "--pids-limit=64", "--ulimit=nofile=64:64", "--ulimit=core=0:0",
            "--tmpfs=/work:rw,exec,nosuid,size=64m,mode=1777",
            "--tmpfs=/tmp:rw,noexec,nosuid,size=16m,mode=1777", "--log-driver=none", IMAGE]
    # File-backed bounded capture prevents an untrusted child bypassing its stdout redirection
    # through /proc/1/fd/1 from exhausting the coordinator's memory.
    with tempfile.TemporaryFile() as source, tempfile.TemporaryFile() as output, tempfile.TemporaryFile() as error:
        source.write(json.dumps(task).encode())
        source.seek(0)
        process = subprocess.Popen(args, stdin=source, stdout=output, stderr=error)
        try:
            end = time.monotonic() + timeout
            while process.poll() is None:
                if time.monotonic() >= end:
                    raise ValueError("隔离编译或执行超时")
                if os.fstat(output.fileno()).st_size > 7 * 1024 * 1024 or os.fstat(error.fileno()).st_size > 64 * 1024:
                    raise ValueError("隔离执行输出过大")
                time.sleep(0.05)
            if process.returncode:
                raise ValueError("无法启动隔离容器，请检查 rootless Podman、cgroup v2 与工具链镜像")
            output.seek(0)
            raw = output.read(7 * 1024 * 1024 + 1)
            if len(raw) > 7 * 1024 * 1024:
                raise ValueError("隔离执行输出过大")
            try:
                result = json.loads(raw)
            except (ValueError, UnicodeError):
                raise ValueError("隔离程序异常退出或返回格式无效")
            if not result.get("ok"):
                raise ValueError(str(result.get("error", "隔离执行失败"))[:3000])
            return result
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=5)
            subprocess.run(["podman", "rm", "-f", name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)


def text(value, limit=FILE_LIMIT):
    if not isinstance(value, str) or len(value.encode("utf-8")) > limit:
        raise ValueError("程序或测试输入格式、长度无效")
    return value


def generate(task, invoke=sandbox_task):
    if invoke is sandbox_task and not ready():
        raise ValueError("执行器必须使用 rootless Podman、cgroup v2 和已构建的 Linux 工具链镜像")
    count, seed = task.get("caseCount"), task.get("seed")
    if type(count) is not int or not 1 <= count <= 20 or type(seed) is not int or not 0 <= seed <= 2147483647:
        raise ValueError("测试组数或种子无效")
    attacks = task.get("attackInputs", [])
    if not isinstance(attacks, list) or len(attacks) > 3:
        raise ValueError("反例数量无效")
    attacks = [text(x) for x in attacks]
    deadline = time.monotonic() + 600
    binaries, notes = {}, []
    for name in ("generator", "reference", "independent", "validator", "mutant"):
        source = text(task.get(name, ""), 480000)
        if not source:
            if name == "mutant":
                continue
            raise ValueError("缺少必要程序：" + name)
        try:
            binaries[name] = invoke({"action": "compile", "source": source}, deadline)["binary"]
        except ValueError as exc:
            if name != "mutant":
                raise ValueError(name + "：" + str(exc))
            notes.append("典型错误解未能编译，未统计错误解检出率")

    def run(name, data="", args=None, require_success=True):
        result = invoke({"action": "run", "binary": binaries[name], "input": data, "args": args or []}, deadline)
        if require_success and result.get("exitCode") != 0:
            raise ValueError(name + " 程序异常退出：" + str(result.get("stderr", ""))[:1000])
        text(result.get("output"))
        return result

    candidates = []
    sample_in, sample_out = text(task.get("sampleInput", "")), text(task.get("sampleOutput", ""))
    if sample_in or sample_out:
        candidates.append((sample_in, "题面样例", sample_out))
    else:
        notes.append("题面未提供样例，未进行样例输出核对")
    for i in range(count):
        args = [str(i), str(seed)]
        data = run("generator", args=args)["output"]
        if i == 0 and data != run("generator", args=args)["output"]:
            raise ValueError("生成器相同种子返回不同输入，无法复现")
        candidates.append((data, "生成器 #" + str(i), None))
    candidates.extend((x, "GLM 边界反例", None) for x in attacks)
    cases, seen, rejected, total = [], set(), 0, 0
    for data, label, expected in candidates:
        digest = hashlib.sha256(data.encode()).digest()
        duplicate = digest in seen
        if duplicate and expected is None:
            continue
        run("validator", data)
        output = run("reference", data)["output"]
        other = run("independent", data)["output"]
        if output.split() != other.split():
            raise ValueError("两份解法输出不一致，已阻止导出。输入：\n" + data[:1000] +
                             "\n标准解：\n" + output[:500] + "\n独立解：\n" + other[:500])
        if expected is not None and output.split() != expected.split():
            raise ValueError("标准解未通过题面样例，已阻止导出")
        if not cases and run("reference", data)["output"] != output:
            raise ValueError("标准解重复运行输出不一致")
        if "mutant" in binaries:
            try:
                bad = run("mutant", data, require_success=False)
                if bad.get("exitCode") != 0 or bad["output"].split() != output.split():
                    rejected += 1
            except ValueError:
                rejected += 1
        total += len(data.encode()) + len(output.encode())
        if total > 16 * 1024 * 1024:
            raise ValueError("生成数据总量超过16MiB")
        cases.append({"input": data, "output": output, "label": label})
        seen.add(digest)
    if len(cases) < len(candidates):
        notes.append("已移除重复输入；实际组数以结果为准")
    if "mutant" not in binaries:
        notes.append("本次没有可运行的典型错误解，未验证错误解检出能力")
    elif not rejected:
        notes.append("典型错误解通过全部测试，请补充针对性反例后再用于正式判题")
    return {"ok": True, "cases": cases, "notes": notes, "mutantRejected": rejected}


def ready():
    if not hasattr(os, "geteuid") or os.geteuid() == 0:
        return False
    checks = [(["podman", "info", "--format", "{{.Host.Security.Rootless}} {{.Host.CgroupsVersion}}"], b"true v2"),
              (["podman", "image", "inspect", "--format", "{{.Os}}", IMAGE], b"linux")]
    for command, expected in checks:
        result = subprocess.run(command, capture_output=True, timeout=8)
        if result.returncode or result.stdout.strip() != expected:
            return False
    return True


class Handler(BaseHTTPRequestHandler):
    def reply(self, status, payload):
        body = json.dumps(payload, ensure_ascii=True).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def authorized(self):
        return len(TOKEN) >= 32 and hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + TOKEN)

    def do_GET(self):
        if not self.authorized():
            return self.reply(401, {"ok": False})
        if self.path != "/health":
            return self.reply(404, {"ok": False})
        try:
            ok = ready()
            return self.reply(200 if ok else 503, {"ok": ok})
        except (OSError, subprocess.SubprocessError):
            return self.reply(503, {"ok": False})

    def do_POST(self):
        if not self.authorized():
            return self.reply(401, {"ok": False})
        if self.path != "/generate":
            return self.reply(404, {"ok": False})
        if not SLOTS.acquire(blocking=False):
            return self.reply(503, {"ok": False, "error": "隔离执行器繁忙"})
        try:
            self.connection.settimeout(30)
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 4 * 1024 * 1024:
                return self.reply(413, {"ok": False, "error": "执行请求过大或为空"})
            task = json.loads(self.rfile.read(length))
            if not isinstance(task, dict):
                raise ValueError("执行请求格式无效")
            self.reply(200, generate(task))
        except (ValueError, KeyError, UnicodeError) as exc:
            self.reply(422, {"ok": False, "error": str(exc)[:3500]})
        except Exception:
            self.reply(503, {"ok": False, "error": "隔离执行器不可用，请检查 rootless Podman 与工具链镜像"})
        finally:
            SLOTS.release()


if __name__ == "__main__":
    if not hasattr(os, "geteuid") or os.geteuid() == 0:
        raise SystemExit("Run as a dedicated unprivileged Linux user with rootless Podman; never run as root")
    if len(TOKEN) < 32:
        raise SystemExit("TEST_DATA_RUNNER_TOKEN must have at least 32 characters")
    ThreadingHTTPServer((os.environ.get("TEST_DATA_BIND", "127.0.0.1"), 8766), Handler).serve_forever()
