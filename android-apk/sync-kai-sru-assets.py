from __future__ import annotations

from pathlib import Path
import hashlib
import io
import runpy
import urllib.request

from PIL import Image

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
AVATARS = ASSETS / "avatars"

SOURCES = {
    "snapshot": {
        "file_id": "1Fa02XR57AjMZoCR3XkqsTeU9mbXn45he",
        "sha256": "0d4a612a984e3b864d9220ea65d8032fd37a09160d8600ecd6ee0d4c24056589",
        "size": (1024, 1536),
    },
    "avatar": {
        "file_id": "1BIJS1fd1wWCBJqFzD2_WEGXkE746DQuX",
        "sha256": "9035c5664b87b3851f5c349106b85d551f700028e0e159b4fbf4933f81016f8a",
        "size": (350, 347),
    },
    "entity": {
        "file_id": "1dBC7cOhwVNHmSZ_fX8Rht89Mfvfdo6R9",
        "sha256": "92e664409f2b1f4ab668e15ad9c372792ef8069c5dbc9a36056815fd47929d1a",
        "size": (1448, 1086),
    },
}


def download_drive_file(file_id: str) -> bytes:
    url = f"https://drive.usercontent.google.com/download?id={file_id}&export=download&confirm=t"
    request = urllib.request.Request(
        url,
        headers={"User-Agent": "BACKROOMsV2-release-builder/1.0"},
    )
    with urllib.request.urlopen(request, timeout=45) as response:
        data = response.read()
    if not data:
        raise RuntimeError(f"Drive source {file_id} returned an empty response")
    return data


def verified_source(label: str) -> bytes:
    spec = SOURCES[label]
    local_asset = spec.get("local_asset")
    if local_asset:
        source_path = ASSETS / local_asset
        if not source_path.is_file():
            raise RuntimeError(f"Kai {label} committed source is missing: {source_path}")
        data = source_path.read_bytes()
    else:
        data = download_drive_file(spec["file_id"])
    digest = hashlib.sha256(data).hexdigest()
    if digest != spec["sha256"]:
        raise RuntimeError(
            f"Kai {label} source hash mismatch: expected {spec['sha256']}, got {digest}"
        )
    return data


def validate_image(data: bytes, label: str, expected_format: str, expected_size: tuple[int, int]) -> Image.Image:
    image = Image.open(io.BytesIO(data))
    image.load()
    if image.format != expected_format:
        raise RuntimeError(f"Kai {label} source format mismatch: expected {expected_format}, got {image.format}")
    if image.size != expected_size:
        raise RuntimeError(f"Kai {label} source dimensions mismatch: expected {expected_size}, got {image.size}")
    return image


snapshot = verified_source("snapshot")
snapshot_image = validate_image(snapshot, "snapshot", "PNG", SOURCES["snapshot"]["size"])
if "A" not in snapshot_image.getbands():
    raise RuntimeError("Kai snapshot overlay must preserve an alpha channel")
(ASSETS / "kai_snapshot_overlay.png").write_bytes(snapshot)

entity = verified_source("entity")
entity_image = validate_image(entity, "entity", "PNG", SOURCES["entity"]["size"])
if "A" not in entity_image.getbands():
    raise RuntimeError("Kai Entity encounter overlay must preserve an alpha channel")
(ASSETS / "kai_entity_overlay.png").write_bytes(entity)

avatar = verified_source("avatar")
avatar_image = validate_image(avatar, "avatar", "JPEG", SOURCES["avatar"]["size"])
AVATARS.mkdir(parents=True, exist_ok=True)
avatar_output = AVATARS / "kai_avatar.png"
avatar_image.convert("RGB").save(avatar_output, format="PNG", optimize=True)

for path in (ASSETS / "kai_snapshot_overlay.png", ASSETS / "kai_entity_overlay.png", avatar_output):
    if not path.is_file() or path.stat().st_size <= 0:
        raise RuntimeError(f"Kai SRU packaged asset was not written: {path}")

print(
    "Kai SRU visuals synchronized and verified: "
    f"snapshot={len(snapshot)} bytes, entity={len(entity)} bytes, avatar_png={avatar_output.stat().st_size} bytes"
)

# Keep all Entity-encounter character overlays in the same verified release-input step.
runpy.run_path(str(ROOT / "sync-party-combat-assets.py"), run_name="__main__")
