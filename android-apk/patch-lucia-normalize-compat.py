from pathlib import Path


ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
system = (CORE / "CharacterEquipmentSystem.kt").read_text(encoding="utf-8")
codec = (CORE / "GameStateCodec.kt").read_text(encoding="utf-8")
policy = (CORE / "InventoryPolicy.kt").read_text(encoding="utf-8")

for marker in ("object CharacterEquipmentSystem", "private fun normalizeInternal"):
    if marker not in system:
        raise RuntimeError("Checked-in equipment normalizer authority missing: " + marker)
if "LuciaCanon.ensure" not in codec:
    raise RuntimeError("Checked-in Lucia save/backfill authority missing from GameStateCodec")
for marker in (
    "val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)",
    "if (characterId == LUCIA_ID) return LUCIA",
    "if (characterId == AN_NHIEN_ID) return AN_NHIEN",
):
    if marker not in policy:
        raise RuntimeError("Checked-in Lucia inventory authority missing: " + marker)

print("Lucia normalization and inventory authority verified in checked-in Kotlin; no source rewrite performed.")
