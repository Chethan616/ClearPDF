"""Add translations to the app's strings.xml files.

    python tools/i18n/apply.py translations.py [--replace]

`translations.py` defines T = {"de": {"key": "text" or {"one": ..., "other": ...}}, ...}.
Keys a language already has are left alone (unless --replace); new ones go in just before
</resources>. Text is written escaped the way Android wants it (apostrophes, quotes, ampersands),
so the translation file holds plain text.
"""
import re
import runpy
import sys
from pathlib import Path

RES = Path(__file__).resolve().parents[2] / "apps/pdfcraft-android/android/app/src/main/res"


def esc(text: str) -> str:
    t = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    t = t.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    # a leading @ or ? would be read as a reference
    if t[:1] in "@?":
        t = "\\" + t
    return t


def entry(key: str, value) -> str:
    if isinstance(value, dict):
        items = "".join(f'\n        <item quantity="{q}">{esc(v)}</item>' for q, v in value.items())
        return f'    <plurals name="{key}">{items}\n    </plurals>\n'
    return f'    <string name="{key}">{esc(value)}</string>\n'


def apply(path: str, replace: bool) -> None:
    table = runpy.run_path(path)["T"]
    for lang, strings in table.items():
        folder = RES / f"values-{lang}"
        folder.mkdir(exist_ok=True)
        f = folder / "strings.xml"
        if not f.exists():
            f.write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n', encoding="utf-8")
        s = f.read_text(encoding="utf-8")
        added = 0
        for key, value in strings.items():
            pattern = re.compile(rf'[ \t]*<(string|plurals) name="{re.escape(key)}"[^>]*>.*?</\1>[ \t]*\n', re.S)
            if pattern.search(s):
                if not replace:
                    continue
                s = pattern.sub(lambda m: entry(key, value), s, count=1)
            else:
                s = s.replace("</resources>", entry(key, value) + "</resources>")
            added += 1
        f.write_text(s, encoding="utf-8", newline="\n")
        print(f"{lang}: {added} written")


if __name__ == "__main__":
    apply(sys.argv[1], "--replace" in sys.argv)
