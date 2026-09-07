from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ASSETS = ROOT / "app/src/main/assets/level_snapshots"
main = MAIN.read_text(encoding="utf-8")

# The two Level 0 cycle assets are packaged locally. Keep this early patch compatible
# with the established downstream exact-string patch contracts.
for asset in (
    ASSETS / "level_0_turn_a.webp",
    ASSETS / "level_0_turn_b.webp",
):
    if not asset.is_file() or asset.stat().st_size <= 0:
        raise RuntimeError(f"Missing Level 0 turn-cycle snapshot: {asset}")

old = "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);}else{"

new = "var refs={0:'file:///android_asset/level_snapshots/level_0.webp',1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=lm?Number(lm[1]):0;var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r?r.dataUri:(refs[lv]||refs[0]);bg.alt=r?'Snapshot Turn '+(state.turn||''):'Level '+lv+' — Escape the Backrooms Wiki';if(!r)bg.onerror=function(){this.onerror=null;this.src=refs[0];};box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);if(!r){"

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

MAIN.write_text(main, encoding="utf-8")
print("Local Level Snapshot fallback installed; Level 0 turn-cycle is applied by the final visual layer.")
