from __future__ import annotations

from pathlib import Path
import hashlib
import io
import urllib.request

from PIL import Image

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"

SOURCES = {
    "lucia": {
        "file_id": "1MTpCrPHymZxqQpsDNlFS3f5Rl_3Bhka2",
        "sha256": "c1077f762254555b92232a18a50d930dfe8d572bfc1327da3ddeb8859d63029d",
        "size": (1086, 1448),
        "output": "lucia_entity_overlay.png",
    },
    "syvial": {
        "file_id": "1JJK9FilclU25446m2ABjOKb5zEJzBE-X",
        "sha256": "2130a7201b48cf709bb53c9eb083874a7b84571a6d221cb7f13e96be469ca373",
        "size": (1024, 1536),
        "output": "syvial_entity_overlay.png",
    },
}


def download_drive_file(file_id: str) -> bytes:
    url = f"https://drive.usercontent.google.com/download?id={file_id}&export=download&confirm=t"
    request = urllib.request.Request(url, headers={"User-Agent": "BACKROOMsV2-release-builder/1.0"})
    with urllib.request.urlopen(request, timeout=45) as response:
        data = response.read()
    if not data:
        raise RuntimeError(f"Drive source {file_id} returned an empty response")
    return data


def sync(label: str) -> Path:
    spec = SOURCES[label]
    data = download_drive_file(spec["file_id"])
    digest = hashlib.sha256(data).hexdigest()
    if digest != spec["sha256"]:
        raise RuntimeError(f"{label} combat overlay hash mismatch: expected {spec['sha256']}, got {digest}")
    image = Image.open(io.BytesIO(data))
    image.load()
    if image.format != "PNG":
        raise RuntimeError(f"{label} combat overlay must be PNG, got {image.format}")
    if image.size != spec["size"]:
        raise RuntimeError(f"{label} combat overlay dimensions mismatch: expected {spec['size']}, got {image.size}")
    if "A" not in image.getbands():
        raise RuntimeError(f"{label} combat overlay must preserve an alpha channel")
    output = ASSETS / spec["output"]
    output.write_bytes(data)
    if not output.is_file() or output.stat().st_size <= 0:
        raise RuntimeError(f"Combat overlay was not written: {output}")
    return output


outputs = [sync("lucia"), sync("syvial")]
print("Party combat overlays synchronized and verified: " + ", ".join(f"{p.name}={p.stat().st_size} bytes" for p in outputs))
