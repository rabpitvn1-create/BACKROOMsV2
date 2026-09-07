from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ASSETS = ROOT / "app/src/main/assets/level_snapshots"

for asset in (
    ASSETS / "level_0_turn_a.webp",
    ASSETS / "level_0_turn_b.webp",
):
    raw = asset.read_bytes() if asset.is_file() else b""
    if len(raw) < 16 or raw[:4] != b"RIFF" or raw[8:12] != b"WEBP":
        raise RuntimeError(f"Invalid Level 0 WebP snapshot asset: {asset}")

main = MAIN.read_text(encoding="utf-8")
marker = "LEVEL0_TURN_SNAPSHOT_V1"
if marker not in main:
    old_refs = "var refs={0:'file:///android_asset/level_snapshots/level_0.webp',1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};var structuredLevel=state&&state.level&&state.level.number;"
    new_refs = "var refs={0:'file:///android_asset/level_snapshots/level_0.webp',1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};var level0Refs=['file:///android_asset/level_snapshots/level_0_turn_a.webp','file:///android_asset/level_snapshots/level_0_turn_b.webp'];/* LEVEL0_TURN_SNAPSHOT_V1 */var structuredLevel=state&&state.level&&state.level.number;"
    count = main.count(old_refs)
    if count != 1:
        raise RuntimeError(f"Final authoritative Snapshot Level picker: expected 1 match, found {count}")
    main = main.replace(old_refs, new_refs, 1)

    old_background = "var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r?r.dataUri:(refs[lv]||refs[0]);bg.alt=r?'Snapshot Turn '+(state.turn||''):'Level '+lv+' — Escape the Backrooms Wiki';if(!r)bg.onerror=function(){this.onerror=null;this.src=refs[0];};box.appendChild(bg);"
    new_background = "var turn=Math.max(1,Number(state&&state.turn)||1);var localLevel0=lv===0;var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=localLevel0?level0Refs[(turn-1)%level0Refs.length]:(r?r.dataUri:(refs[lv]||refs[0]));bg.alt=localLevel0?'Level 0 Snapshot Turn '+turn:(r?'Snapshot Turn '+turn:'Level '+lv+' — Escape the Backrooms Wiki');if(localLevel0)bg.onerror=function(){this.onerror=null;this.src=level0Refs[0];};else if(!r)bg.onerror=function(){this.onerror=null;this.src=refs[0];};box.appendChild(bg);"
    count = main.count(old_background)
    if count != 1:
        raise RuntimeError(f"Final Snapshot background renderer: expected 1 match, found {count}")
    main = main.replace(old_background, new_background, 1)

for required in (
    marker,
    "level_0_turn_a.webp",
    "level_0_turn_b.webp",
    "(turn-1)%level0Refs.length",
    "var localLevel0=lv===0",
    "bg.src=localLevel0?level0Refs",
):
    if required not in main:
        raise RuntimeError("Level 0 final turn-cycle contract missing: " + required)

MAIN.write_text(main, encoding="utf-8")
print("Level 0 local WebP Snapshot cycle finalized: odd/even turns alternate A/B after all authoritative visual-state patches.")
