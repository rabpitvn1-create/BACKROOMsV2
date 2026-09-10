from pathlib import Path
import json
import re
import urllib.parse

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
ASSETS = ROOT / "app/src/main/assets/level_snapshots"
CATALOG = ROOT / "app/src/main/assets/knowledge/sublevels_0_6_source.json"
MANIFEST = ASSETS / "wiki_snapshot_manifest.json"
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

catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
expected_targets = {f"LEVEL.{level}" for level in range(7)} | {
    str(record["id"]) for record in catalog.get("records") or []
}
if len(expected_targets) != 40:
    raise RuntimeError(f"Wiki snapshot catalog target count changed: {len(expected_targets)}")

targets = manifest.get("targets") or {}
if set(targets) != expected_targets:
    missing = sorted(expected_targets - set(targets))
    extra = sorted(set(targets) - expected_targets)
    raise RuntimeError(f"Wiki snapshot manifest target mismatch: missing={missing} extra={extra}")

deferred = set(manifest.get("deferredTargetIds") or [])
expected_deferred = {"SUBLEVEL.02.2", "SUBLEVEL.04.11"}
if deferred != expected_deferred:
    raise RuntimeError(f"Wiki snapshot deferred target mismatch: {sorted(deferred)}")
if manifest.get("readyTargetCount") != 38:
    raise RuntimeError("Wiki snapshot ready target count must be 38")

seen_assets = {}
snapshot_count = 0
snapshot_pools = {}
reject_name_parts = (
    "logo", "watermark", "marker", "badge", "profile_picture", "readthepage",
    "ms-dos", "screenshot", "wallpaper", "simple_eye", "wretched_stalker",
)
for target_id in sorted(expected_targets):
    entries = targets.get(target_id)
    if not isinstance(entries, list):
        raise RuntimeError(f"Wiki snapshot pool is not a list: {target_id}")
    if target_id in deferred:
        if entries:
            raise RuntimeError(f"Deferred wiki snapshot target must stay empty: {target_id}")
        snapshot_pools[target_id] = []
        continue
    if not 1 <= len(entries) <= 10:
        raise RuntimeError(f"Wiki snapshot pool size invalid for {target_id}: {len(entries)}")
    urls = []
    for entry in entries:
        if not isinstance(entry, dict):
            raise RuntimeError(f"Wiki snapshot entry invalid: {target_id}")
        title = str(entry.get("fileTitle") or "").strip()
        url = str(entry.get("assetUrl") or "").strip()
        if not title or not url:
            raise RuntimeError(f"Wiki snapshot title/url missing: {target_id}")
        lower_title = title.casefold()
        if any(part in lower_title for part in reject_name_parts):
            raise RuntimeError(f"Rejected overlay/chrome-like wiki snapshot selected: {target_id} {title}")
        parsed = urllib.parse.urlsplit(url)
        if parsed.scheme != "https" or parsed.netloc.lower() != "static.wikia.nocookie.net":
            raise RuntimeError(f"Wiki snapshot asset host invalid: {target_id} {url}")
        if not parsed.path.startswith("/backrooms/images/"):
            raise RuntimeError(f"Wiki snapshot asset path invalid: {target_id} {url}")
        normalized_path = urllib.parse.unquote(parsed.path)
        normalized_path = re.sub(r"/revision/latest(?:/scale-to-width-down/[0-9]+)?$", "", normalized_path)
        duplicate_key = parsed.netloc.lower() + normalized_path
        previous = seen_assets.get(duplicate_key)
        if previous is not None:
            raise RuntimeError(f"Duplicate wiki snapshot selected: {target_id} duplicates {previous}: {title}")
        seen_assets[duplicate_key] = target_id
        urls.append(url)
        snapshot_count += 1
    snapshot_pools[target_id] = urls

if snapshot_count != manifest.get("snapshotCount") or snapshot_count != 98:
    raise RuntimeError(
        f"Wiki snapshot count mismatch: selected={snapshot_count} manifest={manifest.get('snapshotCount')}"
    )
if any("Umbrallight.png" in url for url in snapshot_pools["SUBLEVEL.06.2"]):
    raise RuntimeError("Umbrallight belongs to Level 6.99, not Level 6.2")
if not any("Umbrallight.png" in url for url in snapshot_pools["SUBLEVEL.06.99"]):
    raise RuntimeError("Level 6.99 must retain its verified Umbrallight snapshot")
if not all("Level_6_Deviantart.png" in url for url in snapshot_pools["LEVEL.6"]):
    raise RuntimeError("Parent Level 6 must keep the reviewed outdoor dark-tundra snapshot only")


def js_quote(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'"


wiki_pool_literal = "{" + ",".join(
    js_quote(target_id) + ":[" + ",".join(js_quote(url) for url in snapshot_pools[target_id]) + "]"
    for target_id in sorted(snapshot_pools)
) + "}"

old = "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);}else{"

# Wiki snapshots are the normal scene background. The old packaged parent images remain
# as network-failure fallback for parent Levels only. Deferred sublevels intentionally do
# not borrow a parent image because that would mislabel the scene as the wrong sublevel.
# The structured-Level picker and Kai sequence deliberately keep the exact anchors used by
# patch-visual-state-sync-final.py, MadGod, and progression snapshot composition.
new = (
    "var refs={0:'file:///android_asset/level_snapshots/level_0.webp',1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};"
    "var structuredLevel=state&&state.level&&state.level.number;var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=(structuredLevel!==undefined&&structuredLevel!==null&&Number(structuredLevel)>=0&&Number(structuredLevel)<=6)?Number(structuredLevel):(lm?Number(lm[1]):0);"
    "refs[0]='file:///android_asset/level_snapshots/backrooms_level0_01_open_room_16bit.webp';"
    "var level0Refs=['file:///android_asset/level_snapshots/backrooms_level0_01_open_room_16bit.webp','file:///android_asset/level_snapshots/backrooms_level0_02_long_corridor_16bit.webp','file:///android_asset/level_snapshots/backrooms_level0_03_maze_junction_16bit.webp','file:///android_asset/level_snapshots/backrooms_level0_04_ceiling_corner_16bit.webp'];"
    "var wikiSnapshotPools=" + wiki_pool_literal + ";/* WIKI_SNAPSHOT_POOLS_V1 */"
    "var ex=state&&state.flags&&state.flags.exploration||{};var sid=String(ex&&ex.sublevelId||'');var target=sid||('LEVEL.'+lv);"
    "var pool=wikiSnapshotPools[target]||[];var turn=Math.max(1,Number(state&&state.turn)||1);var start=pool.length?(turn-1)%pool.length:0;"
    "var parentFallback=lv===0?level0Refs[(turn-1)%level0Refs.length]:(refs[lv]||refs[0]);"
    "var src=r?r.dataUri:(pool.length?pool[start]:(sid?'':parentFallback));box.classList.toggle('has-wiki-snapshot',!!src);"
    "var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=src||parentFallback;bg.alt=r?'Snapshot Turn '+turn:(sid?target+' Snapshot Turn '+turn:'Level '+lv+' Wiki Snapshot Turn '+turn);if(!src)bg.style.display='none';box.appendChild(bg);"
    "var kai=document.createElement('img');kai.className='snapshot-character';if(!src)kai.style.display='none';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);"
    "if(!r){if(pool.length){var tried=0;bg.onerror=function(){tried++;if(tried<pool.length){this.src=pool[(start+tried)%pool.length];return;}this.onerror=null;if(!sid){this.src=parentFallback;}else{this.style.display='none';kai.style.display='none';box.classList.remove('has-wiki-snapshot');}};}else if(!sid){bg.onerror=function(){this.onerror=null;this.src=refs[0];};}"
)

count = main.count(old)
if count != 1:
    raise RuntimeError(f"Snapshot layered renderer anchor: expected 1 match, found {count}")
main = main.replace(old, new, 1)

old_css = ".snapshot-placeholder{position:relative;z-index:3;width:100%;height:100%;display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}"
new_css = ".snapshot-placeholder{position:relative;z-index:3;width:100%;height:100%;display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}.has-wiki-snapshot .snapshot-placeholder{display:none}"
count = main.count(old_css)
if count != 1:
    raise RuntimeError(f"Snapshot placeholder style: expected 1 match, found {count}")
main = main.replace(old_css, new_css, 1)

for required in (
    "WIKI_SNAPSHOT_POOLS_V1",
    "SUBLEVEL.00.EPSILON",
    "SUBLEVEL.06.99",
    "Umbrallight.png",
    "var structuredLevel=state&&state.level&&state.level.number;",
    "var target=sid||('LEVEL.'+lv)",
    "var start=pool.length?(turn-1)%pool.length:0",
    "box.classList.toggle('has-wiki-snapshot',!!src)",
    "if(!src)kai.style.display='none'",
    "kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);if(!r)",
):
    if required not in main:
        raise RuntimeError("Wiki snapshot runtime contract missing: " + required)

MAIN.write_text(main, encoding="utf-8")
print(
    f"Wiki Snapshot pools installed: targets=40 ready=38 deferred=2 snapshots={snapshot_count}; "
    "rotation is target-local and duplicate assets are rejected."
)
