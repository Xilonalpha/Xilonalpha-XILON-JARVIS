import unittest
from agents.planner import run_task
from agents.tools import Registry, Tool

class LoopTest(unittest.TestCase):
    def test_repeat(self):
        r = Registry()
        calls = []
        r.register(Tool("read", "Read", lambda: calls.append(1) or len(calls)))
        result = run_task(lambda _: '{"tool":"read","args":{}}', r, "read")
        self.assertEqual(result["status"], "blocked")
        self.assertEqual(len(calls), 1)
    def test_budget(self):
        r = Registry()
        r.register(Tool("read", "Read", lambda: 42))
        result = run_task(lambda _: '{"tool":"read","args":{}}', r, "read", max_steps=1)
        self.assertEqual(result["status"], "budget_exhausted")
        self.assertEqual(result["observations"][0]["result"], 42)
