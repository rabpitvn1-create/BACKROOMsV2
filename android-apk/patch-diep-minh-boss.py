from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"

# DIEP_MINH_BOSS_VERIFIER_V2
# Diệp Minh gameplay is checked-in Kotlin authority. This historical node only keeps the generated
# Android overlay projection compatible and verifies Core so it cannot rewrite true-turn combat.
combat = COMBAT.read_text(encoding="utf-8")
main = MAIN.read_text(encoding="utf-8")
test = TEST.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)

main = replace_once(
    main,
    '      case "jeff_the_killer": case "jane_the_killer": case "slenderman":\n        return key;\n',
    '      case "jeff_the_killer": case "jane_the_killer": case "slenderman": case "diep_minh":\n        return key;\n',
    "Diệp Minh canonical overlay key",
)
main = replace_once(
    main,
    '      case "slenderman": name = "Slenderman"; break;\n',
    '      case "slenderman": name = "Slenderman"; break;\n      case "diep_minh": name = "Diệp Minh"; break;\n',
    "Diệp Minh overlay display name",
)
main = replace_once(
    main,
    "'jeff_the_killer','jane_the_killer','slenderman'];",
    "'jeff_the_killer','jane_the_killer','slenderman','diep_minh'];",
    "Diệp Minh overlay JavaScript key",
)

for marker in (
    'private const val DIEP_MINH_MAX_HP = 2999',
    'private const val DIEP_MINH_ATTACK_PERCENT = 10',
    'private const val DIEP_MINH_REGEN_PER_TURN = 30',
    'private const val DIEP_MINH_ULTIMATE_INTERVAL_TURNS = 5',
    'private const val DIEP_MINH_ULTIMATE_PERCENT = 5',
    'Profile(DIEP_MINH_KEY, "Diệp Minh", DIEP_MINH_MAX_HP, 0, 8, 9)',
    'val enhancedEntityMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS',
    'val canonicalMaxHp = if (profile.key == DIEP_MINH_KEY) DIEP_MINH_MAX_HP else profile.maxHp + ENTITY_HP_BONUS',
    'private fun damageActivePartyByPercent(state: GameState, percent: Int): PartyPercentDamage',
    'damageActivePartyByPercent(resolvedState, DIEP_MINH_ULTIMATE_PERCENT)',
    'Devils And Gold kích hoạt',
    'percentDamage(targetMaxHp, DIEP_MINH_ATTACK_PERCENT)',
    'percentDamage(c.playerMaxHp, DIEP_MINH_ATTACK_PERCENT)',
    'val entityRegen = if (c.entityKey == DIEP_MINH_KEY) DIEP_MINH_REGEN_PER_TURN else ENTITY_REGEN_PER_TURN',
):
    if marker not in combat:
        raise RuntimeError("Checked-in Kotlin Diệp Minh authority missing: " + marker)

for marker in (
    'case "diep_minh":',
    'case "diep_minh": name = "Diệp Minh"; break;',
    "'slenderman','diep_minh']",
    'file:///android_asset/entity/',
):
    if marker not in main:
        raise RuntimeError("Checked-in Android Diệp Minh projection missing: " + marker)

for forbidden in (
    'thresholdRoll("diepMinhEncounter"',
    'unique boss 3%',
    'independent 3% encounter for diep_minh',
    'DIỆP MINH BOSS HARD LOCK:',
):
    if forbidden in main:
        raise RuntimeError("Retired Diệp Minh encounter channel survived: " + forbidden)

MAIN.write_text(main, encoding="utf-8")

for marker in (
    'diepMinhHasExact2999HpAndRegeneratesThirtyPerSurvivingTurn',
    'assertEquals(2999, started.entityMaxHp)',
    'assertEquals(2930, after.entityHp)',
    'diepMinhDevilsAndGoldHitsEveryActivePartyMemberForFivePercentMaxHpOnTurnFive',
    'result.reply.contains("Devils And Gold")',
):
    if marker not in test:
        raise RuntimeError("Checked-in Diệp Minh regression coverage missing: " + marker)

asset = ROOT / "app/src/main/assets/entity/diep_minh.png"
if not asset.is_file() or asset.stat().st_size <= 0:
    raise RuntimeError("Original Diệp Minh PNG is missing from assets/entity/diep_minh.png")
if asset.read_bytes()[:8] != b"\x89PNG\r\n\x1a\n":
    raise RuntimeError("Diệp Minh asset is not a PNG")

print("Diệp Minh boss authority verified in checked-in Kotlin; Android overlay projection synchronized without gameplay source rewrite.")
