"""Route the canonical typed action form through submitFreedom.

Runs after patch-gm-choices-freedom-final.py. This does not alter World state or add
an ActionKind; it only distinguishes player-authored text from curated GM choices.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

old = 'Android.submitAction(JSON.stringify(state),"EXECUTE",a)'
new = 'Android.submitFreedom(JSON.stringify(state),a)'
count = html.count(old)
if count != 1:
    raise RuntimeError(f"Freedom submit route: expected exactly 1 canonical Execute submit, found {count}")
html = html.replace(old, new, 1)

if html.count(new) != 1:
    raise RuntimeError("Freedom submit route was not installed exactly once")
if old in html:
    raise RuntimeError("Typed form still routes through curated submitAction")

INDEX.write_text(html, encoding="utf-8")
print("Typed text now routes through submitFreedom; curated A/B/C still use submitAction.")
