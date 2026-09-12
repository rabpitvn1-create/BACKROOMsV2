"""Validate authored campaign continuation without changing the APK startup state.

The campaign materializers intentionally produce a fully advanced authored snapshot for
continuity regression checks. That snapshot is a test fixture only: production assets must
still start a New Game at the Level 0 prologue. This wrapper materializes into the source
asset temporarily, copies the result under app/build for tests, then restores index.html
byte-for-byte before Gradle continues packaging the APK.
"""
from __future__ import annotations

from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
LEVEL0 = ROOT / "patch-main-campaign-level0.py"
MATERIALIZER = ROOT / "patch-main-campaign-materialize-current.py"
FIXTURE = ROOT / "app/build/generated/campaign-validation/src/main/assets/index.html"


def initial_block(html: str) -> str:
    start = html.find("const initial={")
    if start < 0:
        raise RuntimeError("Startup validation: const initial anchor missing")
    end = html.find("\n\nconst byId=", start)
    if end < 0:
        raise RuntimeError("Startup validation: const byId anchor missing after initial state")
    return html[start:end]


def require_clean_startup(html: str) -> None:
    startup = initial_block(html)
    required = (
        'level:{number:0,name:"The Lobby"}',
        'location:"Level 0 / The Lobby — khu phòng vàng ban đầu"',
        'party:[]',
        'exploration:{sublevelId:""}',
        'currentBeat:"STORY.PROLOGUE.ENTRY_COMPLETE"',
        'nextBeat:"STORY.LEVEL0.ARRIVAL"',
    )
    for marker in required:
        if marker not in startup:
            raise RuntimeError("New Game startup contract missing: " + marker)

    forbidden = (
        'SUBLEVEL.00.41',
        'currentBeat:"STORY.LEVEL0.41.COMPLETE"',
        'nextBeat:"STORY.LEVEL0.5.ENTRY"',
        'party:[{id:"lucia"',
        'Kai và Lucia đã hoàn tất Level 0.41',
    )
    for marker in forbidden:
        if marker in startup:
            raise RuntimeError("Later campaign checkpoint leaked into New Game startup: " + marker)


original_bytes = INDEX.read_bytes()
original_html = original_bytes.decode("utf-8")
require_clean_startup(original_html)

try:
    subprocess.run(["python3", str(LEVEL0)], cwd=ROOT, check=True)
    subprocess.run(["python3", str(MATERIALIZER)], cwd=ROOT, check=True)
    materialized = INDEX.read_text(encoding="utf-8")
    for marker in (
        "LEVEL 0.41 / DISEASE — DO NOT NAME WHAT YOU HAVE NOT TESTED",
        'currentBeat:"STORY.LEVEL0.41.COMPLETE"',
        'nextBeat:"STORY.LEVEL0.5.ENTRY"',
        'sublevelId:"SUBLEVEL.00.41"',
        'THREAD.MAIN.LEVEL0.41',
        'party:[{id:"lucia",name:"Lucia"}]',
    ):
        if marker not in materialized:
            raise RuntimeError("Materialized campaign validation fixture missing: " + marker)

    FIXTURE.parent.mkdir(parents=True, exist_ok=True)
    FIXTURE.write_text(materialized, encoding="utf-8")
finally:
    INDEX.write_bytes(original_bytes)

if INDEX.read_bytes() != original_bytes:
    raise RuntimeError("Campaign validation failed to restore index.html byte-for-byte")
require_clean_startup(INDEX.read_text(encoding="utf-8"))

print(f"Campaign validated through Level 0.41; New Game startup restored; fixture: {FIXTURE}")
