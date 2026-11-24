import argparse
from core.runtime import Jarvis
from integrations import termux, whisper
from agents.tools import Registry, Tool
from agents.planner import dispatch
import json

def build_registry(jarvis):
    registry = Registry()
    registry.register(Tool("time", "Local time", lambda: jarvis.execute("time", {})))
    registry.register(Tool("storage", "Free bytes", lambda: jarvis.execute("storage", {})))
    registry.register(Tool("battery", "Android battery", termux.battery))
    registry.register(Tool("recall", "Recall memory", jarvis.recall))
    registry.register(Tool("remember", "Save memory", jarvis.remember, True))
    return registry

def respond(jarvis, text, registry=None, confirm=None, agent=False):
    registry = registry or build_registry(jarvis)
    command = text.strip().casefold()
    names = {"ora": "time", "cât e ceasul": "time", "baterie": "battery", "stocare": "storage"}
    if command in names:
        return str(registry.execute(names[command], {}))
    if command.startswith("memorează ") and "=" in text:
        key, value = text[len("memorează "):].split("=", 1)
        if not key.strip():
            raise ValueError("Empty memory key")
        registry.execute("remember", {"key": key.strip(), "value": value.strip()}, confirm)
        return "Salvat."
    if command.startswith("amintește "):
        value = registry.execute("recall", {"key": text.strip()[len("amintește "):].strip()})
        return value if value is not None else "Nu există această amintire."
    if agent:
        return json.dumps(dispatch(jarvis.infer, registry, text, confirm), ensure_ascii=False)
    return jarvis.infer(text)

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--cli", required=True)
    p.add_argument("--model", required=True)
    p.add_argument("--db", default="jarvis.sqlite3")
    p.add_argument("--voice", action="store_true")
    p.add_argument("--agent", action="store_true")
    p.add_argument("--audio")
    p.add_argument("--whisper-cli")
    p.add_argument("--whisper-model")
    args = p.parse_args()
    if args.audio and (not args.whisper_cli or not args.whisper_model or args.voice):
        p.error("--audio needs Whisper CLI and model and cannot be combined with --voice")
    jarvis = Jarvis(args.db, args.cli, args.model)
    registry = build_registry(jarvis)
    def confirm(name, params):
        return input(f"Confirm {name} {params}? [da/N] ").strip().lower() == "da"
    try:
        while True:
            try:
                if args.audio:
                    query = whisper.transcribe(args.whisper_cli, args.whisper_model, args.audio)
                    print("Tu:", query)
                else:
                    query = termux.listen() if args.voice else input("Tu: ")
                if query.strip().casefold() in ("exit", "stop", "ieșire"):
                    break
                answer = respond(jarvis, query, registry, confirm, args.agent)
                print("JARVIS:", answer)
                if args.voice:
                    termux.speak(answer)
                if args.audio:
                    break
            except (EOFError, KeyboardInterrupt):
                break
            except Exception as exc:
                print("Eroare:", exc)
                if args.audio:
                    raise
                if args.voice:
                    break
    finally:
        jarvis.close()

if __name__ == "__main__":
    main()
