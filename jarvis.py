import argparse
from core.runtime import Jarvis
from integrations import termux

def respond(jarvis, text):
    command = text.strip().casefold()
    if command in ("ora", "cât e ceasul"):
        return jarvis.execute("time", {})
    if command == "baterie":
        return str(termux.battery())
    if command == "stocare":
        return str(jarvis.execute("storage", {})) + " bytes"
    if command.startswith("memorează ") and "=" in text:
        key, value = text[len("memorează "):].split("=", 1)
        if not key.strip():
            raise ValueError("Empty memory key")
        jarvis.remember(key.strip(), value.strip())
        return "Salvat."
    return jarvis.infer(text)

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--cli", required=True)
    p.add_argument("--model", required=True)
    p.add_argument("--db", default="jarvis.sqlite3")
    p.add_argument("--voice", action="store_true")
    args = p.parse_args()
    jarvis = Jarvis(args.db, args.cli, args.model)
    try:
        while True:
            try:
                query = termux.listen() if args.voice else input("Tu: ")
                if query.strip().casefold() in ("exit", "stop", "ieșire"):
                    break
                answer = respond(jarvis, query)
                print("JARVIS:", answer)
                if args.voice:
                    termux.speak(answer)
            except (EOFError, KeyboardInterrupt):
                break
            except Exception as exc:
                print("Eroare:", exc)
                if args.voice:
                    break
    finally:
        jarvis.close()

if __name__ == "__main__":
    main()
