import unittest
from core.runtime import Jarvis

class TestRuntime(unittest.TestCase):
    def setUp(self):
        self.jarvis = Jarvis(":memory:", "/nonexistent/cli", "/nonexistent/model")
    def tearDown(self):
        self.jarvis.close()
    def test_memory(self):
        self.jarvis.remember("name", "Jarvis")
        self.assertEqual(self.jarvis.execute("recall", {"key":"name"}), "Jarvis")
    def test_unknown_tool(self):
        with self.assertRaises(ValueError):
            self.jarvis.execute("shell", {})
    def test_missing_runtime(self):
        with self.assertRaises(FileNotFoundError):
            self.jarvis.infer("hello")
    def test_storage(self):
        self.assertGreaterEqual(self.jarvis.execute("storage", {}), 0)

if __name__ == "__main__":
    unittest.main()
