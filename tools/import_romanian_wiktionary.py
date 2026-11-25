#!/usr/bin/env python3
"""Convert a local Kaikki Romanian JSONL (optionally gzip) into compact lexical JSONL.
Data attribution: English Wiktionary contributors, via Kaikki/Wiktextract.
Derived data: CC BY-SA 4.0; upstream also offers GFDL.
https://kaikki.org/dictionary/Romanian/index.html
https://en.wiktionary.org/wiki/Wiktionary:Copyrights
https://creativecommons.org/licenses/by-sa/4.0/
No network access needed. English glosses are definitions, not verified translations.
"""
import argparse
import gzip
import json
import os
import tempfile
from pathlib import Path

def convert(source, output):
    opener = gzip.open if source.suffix == ".gz" else open
    output.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    fd, tmp = tempfile.mkstemp(dir=output.parent)
    try:
        with opener(source, "rt", encoding="utf-8") as src, os.fdopen(fd, "w", encoding="utf-8") as dst:
            for line in src:
                obj = json.loads(line)
                if obj.get("lang_code") != "ro":
                    continue
                word = obj.get("word")
                if not isinstance(word, str) or not word.strip():
                    continue
                glosses = sorted({g for sense in obj.get("senses", []) for g in sense.get("glosses", []) if isinstance(g, str) and g.strip()})
                forms = sorted({f["form"] for f in obj.get("forms", []) if isinstance(f.get("form"), str) and f["form"].strip()})
                if not glosses:
                    continue
                row = {"word": word, "pos": obj.get("pos"), "english_glosses": glosses,
                       "forms": forms, "source": "https://en.wiktionary.org/wiki/" + word.replace(" ", "_") + "#Romanian"}
                dst.write(json.dumps(row, ensure_ascii=False, separators=(",", ":")) + "\n")
                count += 1
            if count < 10000:
                raise ValueError(f"Incomplete corpus: {count} records")
        os.replace(tmp, output)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)
    return count

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    if args.source.resolve() == args.output.resolve():
        parser.error("Input and output must differ")
    print(f"Imported {convert(args.source, args.output)} lexical records")
