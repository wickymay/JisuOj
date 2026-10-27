"""Trusted entrypoint INSIDE a disposable, networkless toolchain container."""
import base64
import json
import os
from pathlib import Path
import resource
import signal
import subprocess
import sys
import tempfile

FILE_LIMIT = 256 * 1024
BINARY_LIMIT = 4 * 1024 * 1024


def limits(compile_mode=False):
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
    resource.setrlimit(resource.RLIMIT_FSIZE, (8 * 1024 * 1024,) * 2)
    resource.setrlimit(resource.RLIMIT_NOFILE, (64, 64))
    cpu = 30 if compile_mode else 5
    resource.setrlimit(resource.RLIMIT_CPU, (cpu, cpu))


def command(args, cwd, data=b"", compile_mode=False):
    with tempfile.TemporaryFile(dir=cwd) as output, tempfile.TemporaryFile(dir=cwd) as error:
        process = subprocess.Popen(args, cwd=cwd, stdin=subprocess.PIPE, stdout=output, stderr=error,
                                   start_new_session=True, preexec_fn=lambda: limits(compile_mode))
        try:
            process.communicate(data, timeout=35 if compile_mode else 5)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()
            raise ValueError("编译超时" if compile_mode else "程序运行超时")
        finally:
            # Also stop forked descendants after the original process exits.
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        size = os.fstat(output.fileno()).st_size
        if size > FILE_LIMIT:
            raise ValueError("程序输出超过256KiB")
        output.seek(0)
        error.seek(0)
        return process.returncode, output.read(FILE_LIMIT), error.read(2000).decode("utf-8", errors="replace")


def execute(task):
    with tempfile.TemporaryDirectory(dir="/work") as directory:
        root = Path(directory)
        if task["action"] == "compile":
            source = task["source"]
            if not isinstance(source, str) or len(source) > 120000:
                raise ValueError("程序长度无效")
            (root / "main.cpp").write_text(source, encoding="utf-8")
            code, _, error = command(["g++", "-std=c++17", "-O2", "-pipe", "main.cpp", "-o", "main"], directory,
                                     compile_mode=True)
            if code:
                raise ValueError("编译失败：" + error)
            size = (root / "main").stat().st_size
            if size > BINARY_LIMIT:
                raise ValueError("可执行文件过大")
            return {"ok": True, "binary": base64.b64encode((root / "main").read_bytes()).decode("ascii")}
        if task["action"] != "run":
            raise ValueError("不支持的操作")
        binary = base64.b64decode(task["binary"], validate=True)
        if len(binary) > BINARY_LIMIT:
            raise ValueError("可执行文件过大")
        (root / "main").write_bytes(binary)
        (root / "main").chmod(0o700)
        arguments = task.get("args", [])
        if len(arguments) > 2 or any(not isinstance(x, str) or not x.isdecimal() or len(x) > 10 for x in arguments):
            raise ValueError("程序参数无效")
        data = task.get("input", "").encode("utf-8")
        if len(data) > FILE_LIMIT:
            raise ValueError("输入超过256KiB")
        code, output, error = command([str(root / "main"), *arguments], directory, data)
        return {"ok": True, "exitCode": code, "output": output.decode("utf-8"), "stderr": error}


if __name__ == "__main__":
    try:
        result = execute(json.load(sys.stdin))
    except Exception as exc:
        result = {"ok": False, "error": str(exc)[:3000]}
    print(json.dumps(result, ensure_ascii=True))
