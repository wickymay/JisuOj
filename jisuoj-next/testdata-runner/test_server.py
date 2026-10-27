import json
import unittest
import threading
from http.client import HTTPConnection
from unittest.mock import patch, Mock
import server


def task():
    return {"caseCount": 2, "seed": 42, "generator": "generator", "reference": "reference",
            "independent": "independent", "validator": "validator", "mutant": "mutant",
            "attackInputs": ["0 0\n"], "sampleInput": "1 2\n", "sampleOutput": "3\n"}


class FakeSandbox:
    def __init__(self, mismatch=False, invalid=False, nondeterministic=False):
        self.mismatch = mismatch
        self.invalid = invalid
        self.nondeterministic = nondeterministic
        self.calls = 0

    def __call__(self, request, deadline):
        if request["action"] == "compile":
            return {"ok": True, "binary": request["source"]}
        name = request["binary"]
        data = request["input"]
        output = ""
        code = 0
        if name == "generator":
            self.calls += 1
            i = int(request["args"][0])
            output = f"{i + (self.calls if self.nondeterministic else 2)} 4\n"
        elif name == "validator":
            code = 1 if self.invalid else 0
        else:
            a, b = map(int, data.split())
            output = str(a + b + (1 if name == "independent" and self.mismatch else 0)) + "\n"
            if name == "mutant":
                output = str(a - b) + "\n"
        return {"ok": True, "exitCode": code, "output": output, "stderr": ""}


class GenerationTests(unittest.TestCase):
    def test_outputs_are_computed_and_wrong_solution_is_detected(self):
        result = server.generate(task(), FakeSandbox())
        self.assertEqual(len(result["cases"]), 4)
        self.assertEqual(result["cases"][1]["output"], "6\n")
        self.assertGreater(result["mutantRejected"], 0)

    def test_disagreement_prevents_any_export(self):
        with self.assertRaisesRegex(ValueError, "输出不一致"):
            server.generate(task(), FakeSandbox(mismatch=True))

    def test_invalid_input_prevents_any_export(self):
        with self.assertRaisesRegex(ValueError, "validator"):
            server.generate(task(), FakeSandbox(invalid=True))

    def test_sample_mismatch_is_rejected(self):
        value = task()
        value["sampleOutput"] = "4\n"
        with self.assertRaisesRegex(ValueError, "未通过题面样例"):
            server.generate(value, FakeSandbox())

    def test_seed_must_reproduce_input(self):
        with self.assertRaisesRegex(ValueError, "无法复现"):
            server.generate(task(), FakeSandbox(nondeterministic=True))

    def test_duplicates_are_removed_and_missing_mutants_are_disclosed(self):
        value = task()
        value["mutant"] = ""
        value["attackInputs"] = ["1 2\n"]
        result = server.generate(value, FakeSandbox())
        self.assertEqual(len(result["cases"]), 3)
        self.assertTrue(any("重复" in x for x in result["notes"]))
        self.assertTrue(any("未验证错误解" in x for x in result["notes"]))

    def test_boolean_counts_and_oversized_inputs_are_rejected(self):
        value = task()
        value["caseCount"] = True
        with self.assertRaises(ValueError):
            server.generate(value, FakeSandbox())
        value = task()
        value["attackInputs"] = ["x" * (server.FILE_LIMIT + 1)]
        with self.assertRaises(ValueError):
            server.generate(value, FakeSandbox())

    def test_rootful_runtime_is_never_ready(self):
        with patch.object(server.os, "geteuid", return_value=0, create=True), patch.object(server.subprocess, "run") as run:
            self.assertFalse(server.ready())
            run.assert_not_called()

    def test_cgroup_v1_is_not_ready(self):
        results = [Mock(returncode=0, stdout=b"true v1\n")]
        with patch.object(server.os, "geteuid", return_value=1000, create=True), patch.object(server.subprocess, "run", side_effect=results):
            self.assertFalse(server.ready())


class HttpBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.token = patch.object(server, "TOKEN", "fixture-token-at-least-32-characters-long")
        self.token.start()
        self.http = server.ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        self.thread = threading.Thread(target=self.http.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.http.shutdown()
        self.http.server_close()
        self.thread.join(timeout=2)
        self.token.stop()

    def test_unauthenticated_requests_never_invoke_generation(self):
        connection = HTTPConnection(*self.http.server_address, timeout=3)
        with patch.object(server, "generate") as generate:
            connection.request("POST", "/generate", "{}", {"Content-Type": "application/json"})
            response = connection.getresponse()
            self.assertEqual(response.status, 401)
            response.read()
            generate.assert_not_called()
        connection.close()

    def test_authenticated_requests_use_the_pipeline_and_reject_oversized_bodies(self):
        headers = {"Authorization": "Bearer " + server.TOKEN, "Content-Type": "application/json"}
        connection = HTTPConnection(*self.http.server_address, timeout=3)
        with patch.object(server, "generate", return_value={"ok": True, "cases": []}) as generate:
            connection.request("POST", "/generate", json.dumps(task()), headers)
            response = connection.getresponse()
            self.assertEqual(response.status, 200)
            self.assertTrue(json.loads(response.read())["ok"])
            generate.assert_called_once()
        connection.close()
        connection = HTTPConnection(*self.http.server_address, timeout=3)
        connection.request("POST", "/generate", b"", {**headers, "Content-Length": str(4 * 1024 * 1024 + 1)})
        response = connection.getresponse()
        self.assertEqual(response.status, 413)
        response.read()
        connection.close()
if __name__ == "__main__":
    unittest.main()
