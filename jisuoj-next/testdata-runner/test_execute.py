"""Compile/run only fixed, trusted fixtures; these tests do not test namespace isolation."""
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch

if sys.platform == "linux":
    import execute


@unittest.skipUnless(sys.platform == "linux" and shutil.which("g++"), "Linux C++ toolchain required")
class EntrypointTests(unittest.TestCase):
    def setUp(self):
        factory = tempfile.TemporaryDirectory
        self.root = factory()
        self.patch = patch("execute.tempfile.TemporaryDirectory", side_effect=lambda **kw: factory(dir=self.root.name))
        self.patch.start()

    def tearDown(self):
        self.patch.stop()
        self.root.cleanup()

    def test_reference_output_is_computed_from_stdin(self):
        program = '#include <iostream>\nint main(){long long a,b;if(std::cin>>a>>b)std::cout<<a+b<<"\\n";}'
        binary = execute.execute({"action": "compile", "source": program})["binary"]
        result = execute.execute({"action": "run", "binary": binary, "input": "2147483647 1\n"})
        self.assertEqual(result["exitCode"], 0)
        self.assertEqual(result["output"], "2147483648\n")

    def test_invalid_cpp_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "编译失败"):
            execute.execute({"action": "compile", "source": "not valid C++"})

    def test_output_limits_are_enforced(self):
        program = '#include <iostream>\nint main(){for(int i=0;i<300000;i++)std::cout<<"x";}'
        binary = execute.execute({"action": "compile", "source": program})["binary"]
        with self.assertRaisesRegex(ValueError, "输出超过"):
            execute.execute({"action": "run", "binary": binary, "input": ""})


if __name__ == "__main__":
    unittest.main()
