from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "patch-an-nhien-follower.py"
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

# An Nhiên gameplay authority is now materialized in checked-in Kotlin. This historical
# patch remains only to prepare the Android/legacy staging shape consumed by later runtime
# finalizers. Never execute the Kotlin mutation sections again during a build.
core_markers = {
    "GameState.kt": (
        "AN_NHIEN_ID to AnNhienCanon.character()",
        "AN_NHIEN_ID to AnNhienCanon.inventory()",
        "AN_NHIEN_ID to AnNhienCanon.equipment()",
    ),
    "GameStateCodec.kt": ("AnNhienCanon.ensure(decoded)",),
    "AnNhienCanon.kt": (
        "const val SURVIVAL_MULTIPLIER = 0.70",
        "fun survivalMultiplierFor(character: CharacterState): Double",
        '"survivalMultiplier" to SURVIVAL_MULTIPLIER.toString()',
    ),
    "InventoryPolicy.kt": (
        "val AN_NHIEN = InventoryProfile",
        "if (characterId == AN_NHIEN_ID) return AN_NHIEN",
        "an_nhien_food_only",
    ),
    "PhysiologyStatusPolicy.kt": (
        "survivalMultiplier: Double = 1.0",
        "scaled(REST_CRITICAL_MINUTES, survivalMultiplier)",
    ),
    "Engines.kt": (
        "an_nhien_equipment_locked",
        "an_nhien_follower_locked",
        "an_nhien_cannot_lead",
    ),
    "GameCoreFacade.kt": ('"an nhiên" to AN_NHIEN_ID', '"an nhien" to AN_NHIEN_ID'),
}
for filename, markers in core_markers.items():
    text = (CORE / filename).read_text(encoding="utf-8")
    for marker in markers:
        if marker not in text:
            raise RuntimeError(f"Checked-in An Nhiên Kotlin authority missing in {filename}: {marker}")

# The settled regression tests exercise the multiplier at the policy boundary. Character detail
# projection intentionally uses the generic persisted physiology view; do not resurrect the old
# build-time projection rewrite merely to satisfy a historical literal anchor.
an_test = (TESTS / "AnNhienFollowerTest.kt").read_text(encoding="utf-8")
for marker in (
    "survivalCapacityIsThirtyPercentLowerThroughExistingPhysiologyPolicy",
    "PhysiologyStatusPolicy.awakeBand(awakeMinutes, AnNhienCanon.SURVIVAL_MULTIPLIER)",
    "PhysiologyStatusPolicy.restPercent(awakeMinutes, AnNhienCanon.SURVIVAL_MULTIPLIER)",
):
    if marker not in an_test:
        raise RuntimeError("Checked-in An Nhiên survival regression missing: " + marker)

source = SOURCE.read_text(encoding="utf-8")
java_section = "# 7) Final Android gameplay integration: deterministic Level 0 encounter, bonuses and exit gate."
if java_section not in source:
    raise RuntimeError("An Nhiên Android staging section missing from historical patch")
preamble = source.split("# 1) Seed An Nhien in the authoritative core", 1)[0]
tail = java_section + source.split(java_section, 1)[1]
code = preamble + tail

# The historical Android staging still feeds later special-follower transforms. Keep its
# prompt insertion tolerant because the settled prompt may already contain the replacement.
strict_prompt = 'main = replace_once(main, prompt_marker, prompt_extra, "An Nhien GM hard lock")\n'
if code.count(strict_prompt) != 1:
    raise RuntimeError("An Nhien prompt compatibility anchor is not unique")
code = code.replace(strict_prompt, 'if prompt_marker in main:\n    main = main.replace(prompt_marker, prompt_extra, 1)\n', 1)
code = code.replace("    'AN NHIÊN HARD LOCK',\n", "", 1)

exec(compile(code, str(SOURCE), "exec"), {"__name__": "__main__", "__file__": str(SOURCE)})

# Keep current 0.25% semantics explicit when the generic GAMEPLAY_ROLLS anchor is still available.
main = MAIN.read_text(encoding="utf-8")
prompt_marker = '"GAMEPLAY_ROLLS do Android sinh là bất biến: chỉ outcome success=true mới được xuất hiện. Không reroll, không tự đổi xác suất, không tự tạo encounter/item/reunion/level transition trái roll. " +\n'
prompt_extra = prompt_marker + '            "SPECIAL FOLLOWER LOCK: An Nhiên, Iris và Syvial dùng ba roll độc lập 0.2500% trên mỗi lượt physical đủ điều kiện ở Level 0–6 khi chưa gặp/reunion. Chỉ success=true mới được xuất hiện. An Nhiên không còn bắt buộc ở Level 0 và không chặn việc rời Level 0. Khi Party đầy, giữ present + joinPending thay vì đuổi thành viên khác. An Nhiên không chiến đấu; bonus +10% loot và +2% exit chỉ áp dụng khi cô thực sự đang theo Kai. " +\n'
if "SPECIAL FOLLOWER LOCK:" not in main and main.count(prompt_marker) == 1:
    main = main.replace(prompt_marker, prompt_extra, 1)
MAIN.write_text(main, encoding="utf-8")
print("Special follower compatibility staging applied to Android only; checked-in Kotlin An Nhiên authority verified without source rewrite.")
