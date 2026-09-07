from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ASSETS = ROOT / "app/src/main/assets/level_snapshots"
main = MAIN.read_text(encoding="utf-8")

level0_assets = (
    ASSETS / "level_0_turn_a.webp",
    ASSETS / "level_0_turn_b.webp",
)
for asset in level0_assets:
    if not asset.is_file() or asset.stat().st_size <= 0:
        raise RuntimeError(f"Missing Level 0 turn-cycle snapshot: {asset}")

old = "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);}else{"

new = "var refs={1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};var level0Refs=['file:///android_asset/level_snapshots/level_0_turn_a.webp','file:///android_asset/level_snapshots/level_0_turn_b.webp'];var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level[^0-9]*([0-6])/i);var structuredLv=state&&state.level&&state.level.number!=null?Number(state.level.number):null;var lv=structuredLv!=null?structuredLv:(lm?Number(lm[1]):0);var turn=Math.max(1,Number(state&&state.turn)||1);var localLevel0=lv===0;var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=localLevel0?level0Refs[(turn-1)%level0Refs.length]:(r?r.dataUri:(refs[lv]||level0Refs[0]));bg.alt=localLevel0?'Level 0 Snapshot Turn '+turn:(r?'Snapshot Turn '+turn:'Level '+lv+' — Escape the Backrooms Wiki');if(localLevel0)bg.onerror=function(){this.onerror=null;this.src=level0Refs[0];};else if(!r)bg.onerror=function(){this.onerror=null;this.src=level0Refs[0];};box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);if(!r){"

count = main.count(old)
if count != 1:
    raise RuntimeError(f"Snapshot layered renderer anchor: expected 1 match, found {count}")
main = main.replace(old, new, 1)

# Do not rewrite requestSnapshot here. patch-kai-hd-continuous.py runs later in the
# canonical chain and owns that exact function contract; changing it early breaks CI.
old_css = ".snapshot-placeholder{position:relative;z-index:3;width:100%;height:100%;display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}"
new_css = ".snapshot-placeholder{display:none}"
count = main.count(old_css)
if count != 1:
    raise RuntimeError(f"Snapshot placeholder style: expected 1 match, found {count}")
main = main.replace(old_css, new_css, 1)

for marker in (
    "level_0_turn_a.webp",
    "level_0_turn_b.webp",
    "(turn-1)%level0Refs.length",
    "state.level.number",
):
    if marker not in main:
        raise RuntimeError("Level 0 turn-cycle marker missing: " + marker)

MAIN.write_text(main, encoding="utf-8")
print("Local Level 0 16-bit snapshots alternate every turn; downstream Snapshot request policy remains intact.")
