from pathlib import Path

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

required_assets = [
    ASSETS / "kai_snapshot_overlay.png",
    ASSETS / "kai_entity_overlay.png",
    ASSETS / "avatars/kai_avatar.png",
]
for asset in required_assets:
    if not asset.is_file() or asset.stat().st_size <= 0:
        raise RuntimeError(f"Missing Kai SRU visual asset: {asset}")

text = MAIN.read_text(encoding="utf-8")

old = "var key=activeEntityKey();if(!key){"
new = (
    "var key=activeEntityKey();"
    "var kai=box.querySelector('.snapshot-character');"
    "if(kai){"
    "if(key){"
    "if(!kai.dataset.kaiBaseSrc)kai.dataset.kaiBaseSrc=kai.getAttribute('src')||'';"
    "kai.src='file:///android_asset/kai_entity_overlay.png';"
    "}else if(kai.dataset.kaiBaseSrc){"
    "kai.src=kai.dataset.kaiBaseSrc;delete kai.dataset.kaiBaseSrc;"
    "}"
    "}"
    "if(!key){"
)
count = text.count(old)
if count != 1:
    raise RuntimeError(f"Kai Entity overlay switch anchor: expected exactly 1 match, found {count}")
text = text.replace(old, new, 1)

for marker in [
    "file:///android_asset/kai_snapshot_overlay.png",
    "file:///android_asset/kai_entity_overlay.png",
    "box.querySelector('.snapshot-character')",
    "function activeEntityKey()",
    "window.backroomEntityOverlay=function(payload)",
]:
    if marker not in text:
        raise RuntimeError(f"Kai SRU runtime marker missing: {marker}")

MAIN.write_text(text, encoding="utf-8")
print("Kai SRU visuals applied: new normal overlay/avatar and dedicated armed overlay while an Entity is present.")
