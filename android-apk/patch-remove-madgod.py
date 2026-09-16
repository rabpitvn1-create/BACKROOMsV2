from pathlib import Path

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
FACADE = CORE / "GameCoreFacade.kt"


def remove_braced_block(source: str, marker: str, label: str) -> str:
    start = source.find(marker)
    if start < 0:
        return source
    brace = source.find("{", start)
    if brace < 0:
        raise RuntimeError(f"{label}: opening brace missing")
    depth = 0
    i = brace
    while i < len(source):
        ch = source[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                end = i + 1
                while end < len(source) and source[end] in " \t":
                    end += 1
                if end < len(source) and source[end] == "\r":
                    end += 1
                if end < len(source) and source[end] == "\n":
                    end += 1
                return source[:start] + source[end:]
        i += 1
    raise RuntimeError(f"{label}: closing brace missing")


facade = FACADE.read_text(encoding="utf-8")

# Remove the complete MadGod runtime surface. The feature was legacy/generated and was
# breaking the issue-43 build patch chain, so it no longer participates in gameplay.
for line in (
    "    if (MadGodCanon.cheat(action)) return applyMadGodCheat(legacy,state)\n",
    "    if (MadGodCanon.cheat(action)) return actionStartResponse(true,null,null)\n",
    "    output.put(\"equipment\",MadGodCanon.legacy(state))\n",
):
    facade = facade.replace(line, "")

facade = remove_braced_block(
    facade,
    "    if (isMadGodEquipRequest(action)) {",
    "MadGod processRule equip block",
)
facade = remove_braced_block(
    facade,
    "  private fun applyMadGodCheat(legacy:JSONObject,state:GameState):String {",
    "MadGod cheat handler",
)
facade = remove_braced_block(
    facade,
    "  private fun isMadGodEquipRequest(action: String): Boolean {",
    "MadGod equip detector",
)

# If an earlier patch ever materializes MadGod-specific validation replies again, strip only
# those dedicated when-arms rather than touching general inventory/equipment handling.
facade_lines = []
for line in facade.splitlines(keepends=True):
    lowered = line.lower()
    if "madgod_" in lowered or "madgod set" in lowered or "madgodcanon" in lowered or "madgod_set_id" in lowered:
        continue
    facade_lines.append(line)
facade = "".join(facade_lines)

for token in ("MadGodCanon", "MADGOD_", "madgod_", "MadGod Set"):
    if token in facade:
        raise RuntimeError(f"MadGod runtime reference survived cleanup: {token}")

FACADE.write_text(facade, encoding="utf-8")

# These files were generated only by the retired MadGod patch chain. Remove them if a stale
# checkout/build step materializes them before this cleanup stage.
for path in (
    CORE / "MadGodCanon.kt",
    ROOT / "app/src/test/java/com/rabpit/backroom/core/MadGodEquipmentTest.kt",
):
    if path.exists():
        path.unlink()

print("MadGod runtime removed from the Android build path.")
