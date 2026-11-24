import subprocess
import sqlite3
import shutil
from pathlib import Path
from datetime import datetime

class Jarvis:
    def __init__(self, database, cli, model):
        self.db = sqlite3.connect(database)
        self.db.execute("CREATE TABLE IF NOT EXISTS memory (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        self.cli = Path(cli).expanduser()
        self.model = Path(model).expanduser()

    def remember(self, key, value):
        self.db.execute("INSERT OR REPLACE INTO memory VALUES (?, ?)", (key, value))
        self.db.commit()

    def recall(self, key):
        row = self.db.execute("SELECT value FROM memory WHERE key=?", (key,)).fetchone()
        return row[0] if row else None

    def execute(self, tool, args):
        if tool == "time":
            return datetime.now().astimezone().isoformat()
        if tool == "storage":
            return shutil.disk_usage(Path.home()).free
        if tool == "recall":
            return self.recall(args["key"])
        raise ValueError("Unknown or unauthorized tool")

    def infer(self, prompt):
        if not self.cli.is_file() or not self.model.is_file():
            raise FileNotFoundError("Local runtime or model unavailable")
        result = subprocess.run([str(self.cli), "-m", str(self.model), "-p", prompt, "-n", "128", "-c", "1024", "-t", "2"], capture_output=True, text=True, timeout=180, check=True)
        if not result.stdout.strip():
            raise RuntimeError("Empty inference output")
        return result.stdout.strip()

    def close(self):
        self.db.close()
