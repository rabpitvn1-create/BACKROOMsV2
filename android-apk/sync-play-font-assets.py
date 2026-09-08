from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parent
FONT_DIR = ROOT / "app/src/main/assets/fonts"
FONT_PATH = FONT_DIR / "Play-Bold.ttf"
LICENSE_PATH = FONT_DIR / "OFL-Play.txt"
PLAY_COMMIT = "e36afc7567e2c4dbe669ca5810e0c77f307295a0"
FONT_URL = f"https://raw.githubusercontent.com/google/fonts/{PLAY_COMMIT}/ofl/play/Play-Bold.ttf"
LICENSE_URL = f"https://raw.githubusercontent.com/google/fonts/{PLAY_COMMIT}/ofl/play/OFL.txt"


def fetch(url: str, label: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": "BACKROOMsV2-ui-font-assets/1.0"})
    with urllib.request.urlopen(request, timeout=45) as response:
        data = response.read()
    if not data:
        raise RuntimeError(f"{label} download returned empty data")
    return data


FONT_DIR.mkdir(parents=True, exist_ok=True)
font = FONT_PATH.read_bytes() if FONT_PATH.is_file() else fetch(FONT_URL, "Play Bold font")
if len(font) < 150_000 or font[:4] != b"\x00\x01\x00\x00":
    raise RuntimeError("Play Bold font asset failed validation")
FONT_PATH.write_bytes(font)
license_data = LICENSE_PATH.read_bytes() if LICENSE_PATH.is_file() else fetch(LICENSE_URL, "Play OFL license")
license_text = license_data.decode("utf-8", errors="strict")
if "SIL OPEN FONT LICENSE" not in license_text.upper():
    raise RuntimeError("Play Bold license failed validation")
LICENSE_PATH.write_text(license_text, encoding="utf-8")
print("Play Bold asset synchronized for semantic gameplay spans only.")
