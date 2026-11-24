from dataclasses import dataclass
from typing import Callable

@dataclass(frozen=True)
class Tool:
    name: str
    description: str
    handler: Callable
    confirmation: bool = False

class Registry:
    def __init__(self):
        self._tools = {}

    def register(self, tool):
        if not tool.name or tool.name in self._tools:
            raise ValueError("Invalid or duplicate tool")
        self._tools[tool.name] = tool

    def describe(self):
        return [{"name": t.name, "description": t.description, "confirmation": t.confirmation} for t in self._tools.values()]

    def execute(self, name, args, confirm=None):
        if name not in self._tools or not isinstance(args, dict):
            raise ValueError("Invalid tool request")
        tool = self._tools[name]
        if tool.confirmation and (confirm is None or confirm(name, dict(args)) is not True):
            raise PermissionError("User confirmation required")
        return tool.handler(**args)
