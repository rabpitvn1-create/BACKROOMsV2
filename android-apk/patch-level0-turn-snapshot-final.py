from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ASSETS = ROOT / "app/src/main/assets/level_snapshots"

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

main = MAIN.read_text(encoding="utf-8")
wiki_marker = "WIKI_SNAPSHOT_POOLS_V1"
legacy_marker = "LEVEL0_FOUR_SNAPSHOT_V1"

old_level_picker = "var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=lm?Number(lm[1]):0;"
new_level_picker = "var structuredLevel=state&&state.level&&state.level.number;var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=(structuredLevel!==undefined&&structuredLevel!==null&&Number(structuredLevel)>=0&&Number(structuredLevel)<=6)?Number(structuredLevel):(lm?Number(lm[1]):0);"
if new_level_picker not in main:
    count = main.count(old_level_picker)
    if count != 1:
        raise RuntimeError(f"Final authoritative Snapshot Level picker: expected 1 match, found {count}")
    main = main.replace(old_level_picker, new_level_picker, 1)

if wiki_marker in main:
    # Reviewed wiki pools supersede the old Level-0-only renderer. Keep the four packaged
    # Level 0 images as an offline parent fallback, but require normal runtime rotation to
    # come from the exact target-local wiki pool.
    for required in (
        wiki_marker,
        "backrooms_level0_01_open_room_16bit.webp",
        "backrooms_level0_02_long_corridor_16bit.webp",
        "backrooms_level0_03_maze_junction_16bit.webp",
        "backrooms_level0_04_ceiling_corner_16bit.webp",
        "var target=sid||('LEVEL.'+lv)",
        "var pool=wikiSnapshotPools[target]||[]",
        "var start=pool.length?(turn-1)%pool.length:0",
        "var structuredLevel=state&&state.level&&state.level.number;",
    ):
        if required not in main:
            raise RuntimeError("Wiki Snapshot Level 0 final contract missing: " + required)
    print("Level 0 snapshot finalizer accepted reviewed target-local Wiki rotation with packaged offline fallback.")
else:
    if legacy_marker not in main:
        raise RuntimeError("Level 0 snapshot renderer missing; run patch-level-snapshot-backgrounds.py first")
    for required in (
        legacy_marker,
        "backrooms_level0_01_open_room_16bit.webp",
        "backrooms_level0_02_long_corridor_16bit.webp",
        "backrooms_level0_03_maze_junction_16bit.webp",
        "backrooms_level0_04_ceiling_corner_16bit.webp",
        "(turn-1)%level0Refs.length",
        "var localLevel0=lv===0",
        "bg.src=localLevel0?level0Refs",
        "var structuredLevel=state&&state.level&&state.level.number;",
    ):
        if required not in main:
            raise RuntimeError("Level 0 final four-snapshot contract missing: " + required)
    print("Level 0 four-snapshot cycle finalized with authoritative structured-Level selection.")

MAIN.write_text(main, encoding="utf-8")
