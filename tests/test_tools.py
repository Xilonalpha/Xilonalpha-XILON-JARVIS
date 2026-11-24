import unittest
from agents.tools import Registry, Tool

class TestRegistry(unittest.TestCase):
    def test_confirmation(self):
        registry = Registry()
        registry.register(Tool("save", "Save value", lambda value: value, True))
        with self.assertRaises(PermissionError):
            registry.execute("save", {"value": 1})
        self.assertEqual(registry.execute("save", {"value": 1}, lambda n, a: True), 1)

    def test_duplicate(self):
        registry = Registry()
        tool = Tool("test", "Test", lambda: None)
        registry.register(tool)
        with self.assertRaises(ValueError):
            registry.register(tool)
