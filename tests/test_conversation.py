import unittest
from core.runtime import Jarvis
from jarvis import respond, build_registry

class ConversationTest(unittest.TestCase):
    def setUp(self):
        self.jarvis = Jarvis(":memory:", "/missing/cli", "/missing/model")
        self.registry = build_registry(self.jarvis)
    def tearDown(self):
        self.jarvis.close()
    def test_memory_requires_confirmation(self):
        with self.assertRaises(PermissionError):
            respond(self.jarvis, "memorează test=real", self.registry)
        self.assertIsNone(self.jarvis.recall("test"))
        self.assertEqual(respond(self.jarvis, "memorează test=real", self.registry, lambda n,a: True), "Salvat.")
        self.assertEqual(respond(self.jarvis, "amintește test", self.registry), "real")
    def test_storage(self):
        self.assertGreaterEqual(int(respond(self.jarvis, "stocare", self.registry)), 0)
    def test_unavailable_inference(self):
        with self.assertRaises(FileNotFoundError):
            respond(self.jarvis, "Salut", self.registry)
