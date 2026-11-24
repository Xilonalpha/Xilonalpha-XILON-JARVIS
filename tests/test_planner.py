import unittest
from agents.planner import dispatch
from agents.tools import Registry, Tool
from core.runtime import Jarvis

class PlannerTest(unittest.TestCase):
    def setUp(self):
        self.jarvis = Jarvis(":memory:", "/missing/cli", "/missing/model")
        self.registry = Registry()
        self.registry.register(Tool("storage", "Free bytes", lambda: self.jarvis.execute("storage", {})))
        self.registry.register(Tool("remember", "Save memory", self.jarvis.remember, True))
    def tearDown(self):
        self.jarvis.close()
    def test_real_result(self):
        result = dispatch(lambda _: '{"tool":"storage","args":{}}', self.registry, "storage")
        self.assertEqual(result["kind"], "tool_result")
        self.assertGreaterEqual(result["result"], 0)
    def test_unknown_tool(self):
        with self.assertRaises(ValueError):
            dispatch(lambda _: '{"tool":"shell","args":{}}', self.registry, "test")
    def test_invalid_json(self):
        with self.assertRaises(ValueError):
            dispatch(lambda _: "invalid", self.registry, "test")
    def test_denied_write(self):
        with self.assertRaises(PermissionError):
            dispatch(lambda _: '{"tool":"remember","args":{"key":"a","value":"b"}}', self.registry, "test")
        self.assertIsNone(self.jarvis.recall("a"))
