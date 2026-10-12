"""Build notes.json from notes/*.md.

Each note file:
---
title: ...
type: 辨析 | 句型 | 文法 | 字詞
core: one-line summary shown on the widget
---
markdown body

The note id is the file name without the leading number and extension,
so renumbering files never resets reading progress.
"""
import hashlib
import json
import pathlib
import re
import sys

import markdown

ROOT = pathlib.Path(__file__).resolve().parent.parent
NOTES_DIR = ROOT / "notes"
OUT = ROOT / "notes.json"
REQUIRED = ("title", "type", "core")


def parse(path):
    text = path.read_text(encoding="utf-8")
    m = re.match(r"^---\n(.*?)\n---\n(.*)$", text, re.S)
    if not m:
        raise ValueError(f"{path.name}: missing front matter")
    meta = {}
    for line in m.group(1).splitlines():
        if not line.strip():
            continue
        key, _, value = line.partition(":")
        meta[key.strip()] = value.strip()
    for key in REQUIRED:
        if not meta.get(key):
            raise ValueError(f"{path.name}: missing '{key}'")
    note_id = re.sub(r"^\d+-", "", path.stem)
    # Notes nest lists with 2 spaces; Python-Markdown needs 4.
    body = re.sub(r"^( +)", lambda s: s.group(1) * 2, m.group(2), flags=re.M)
    html = markdown.markdown(body, extensions=["tables", "sane_lists"])
    return {
        "id": note_id,
        "title": meta["title"],
        "type": meta["type"],
        "core": meta["core"],
        "html": html,
    }


def main():
    files = sorted(NOTES_DIR.glob("*.md"))
    notes = [parse(p) for p in files]
    ids = [n["id"] for n in notes]
    dupes = {i for i in ids if ids.count(i) > 1}
    if dupes:
        raise ValueError(f"duplicate ids: {sorted(dupes)}")
    body = json.dumps(notes, ensure_ascii=False, sort_keys=True)
    version = hashlib.sha1(body.encode("utf-8")).hexdigest()[:12]
    OUT.write_text(
        json.dumps({"version": version, "notes": notes}, ensure_ascii=False, indent=1),
        encoding="utf-8",
    )
    print(f"{len(notes)} notes -> {OUT.name} (version {version})")


if __name__ == "__main__":
    try:
        main()
    except ValueError as e:
        print(f"error: {e}", file=sys.stderr)
        sys.exit(1)
