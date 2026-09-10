from pathlib import Path
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ASSETS = ROOT / "app/src/main/assets/level_snapshots"
main = MAIN.read_text(encoding="utf-8")

LEVEL0_ASSETS = (
    "backrooms_level0_01_open_room_16bit.webp",
    "backrooms_level0_02_long_corridor_16bit.webp",
    "backrooms_level0_03_maze_junction_16bit.webp",
    "backrooms_level0_04_ceiling_corner_16bit.webp",
)

for name in LEVEL0_ASSETS:
    asset = ASSETS / name
    raw = asset.read_bytes() if asset.is_file() else b""
    if len(raw) < 16 or raw[:4] != b"RIFF" or raw[8:12] != b"WEBP":
        raise RuntimeError(f"Invalid Level 0 WebP snapshot asset: {asset}")

old = "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);}else{"

# Keep the legacy picker text intact until patch-visual-state-sync-final.py upgrades
# it to structured state. refs[0] is immediately redirected to the new Level 0 asset,
# so the removed legacy level_0.webp file is never used at runtime.
new = "var refs={0:'file:///android_asset/level_snapshots/level_0.webp',1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=lm?Number(lm[1]):0;refs[0]='file:///android_asset/level_snapshots/backrooms_level0_01_open_room_16bit.webp';var level0Refs=['file:///android_asset/level_snapshots/backrooms_level0_01_open_room_16bit.webp','file:///android_asset/level_snapshots/backrooms_level0_02_long_corridor_16bit.webp','file:///android_asset/level_snapshots/backrooms_level0_03_maze_junction_16bit.webp','file:///android_asset/level_snapshots/backrooms_level0_04_ceiling_corner_16bit.webp'];/* LEVEL0_FOUR_SNAPSHOT_V1 */var turn=Math.max(1,Number(state&&state.turn)||1);var localLevel0=lv===0;var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=localLevel0?level0Refs[(turn-1)%level0Refs.length]:(r?r.dataUri:(refs[lv]||refs[0]));bg.alt=localLevel0?'Level 0 Snapshot Turn '+turn:(r?'Snapshot Turn '+turn:'Level '+lv+' - Escape the Backrooms Wiki');if(localLevel0)bg.onerror=function(){this.onerror=null;this.src=level0Refs[0];};else if(!r)bg.onerror=function(){this.onerror=null;this.src=refs[0];};box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);if(!r){"

count = main.count(old)
if count != 1:
    raise RuntimeError(f"Snapshot layered renderer anchor: expected 1 match, found {count}")
main = main.replace(old, new, 1)

old_css = ".snapshot-placeholder{position:relative;z-index:3;width:100%;height:100%;display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}"
new_css = ".snapshot-placeholder{display:none}"
count = main.count(old_css)
if count != 1:
    raise RuntimeError(f"Snapshot placeholder style: expected 1 match, found {count}")
main = main.replace(old_css, new_css, 1)

for required in (
    "LEVEL0_FOUR_SNAPSHOT_V1",
    "backrooms_level0_01_open_room_16bit.webp",
    "backrooms_level0_02_long_corridor_16bit.webp",
    "backrooms_level0_03_maze_junction_16bit.webp",
    "backrooms_level0_04_ceiling_corner_16bit.webp",
    "refs[0]='file:///android_asset/level_snapshots/backrooms_level0_01_open_room_16bit.webp'",
    "(turn-1)%level0Refs.length",
    "var localLevel0=lv===0",
):
    if required not in main:
        raise RuntimeError("Level 0 four-snapshot contract missing: " + required)

MAIN.write_text(main, encoding="utf-8")
print("Local Level Snapshot fallback installed; Level 0 cycles through four packaged snapshots by turn.")

# Research-only CI probes. They enumerate candidates from the exact Fandom source pages
# and recover article-embedded images whose MediaWiki imageinfo records are unavailable.
# Neither probe changes runtime snapshot selection, and source-site outages stay non-fatal.
if os.environ.get("GITHUB_ACTIONS") == "true":
    for research_script in (
        "harvest-wiki-snapshot-candidates.py",
        "resolve-fandom-embedded-snapshot-images.py",
    ):
        script = ROOT / research_script
        if not script.is_file():
            continue
        result = subprocess.run([sys.executable, str(script)], cwd=ROOT, check=False)
        if result.returncode != 0:
            print(f"Wiki snapshot research warning: script={research_script} exit={result.returncode}")
