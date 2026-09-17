from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"

# GUILTY_CROWN_OVERRIDE_VERIFIER_V2
# Guilty Crown is checked-in Kotlin Game Core authority. This historical patch-chain node is
# verification-only so build-time Python cannot replace the current true-turn combat flow.
combat = COMBAT.read_text(encoding="utf-8")
test = TEST.read_text(encoding="utf-8")

for marker in (
    'private const val KAI_GUILTY_CROWN_INTERVAL_TURNS = 3',
    'private const val KAI_GUILTY_CROWN_SHOTS = 24',
    'private const val KAI_GUILTY_CROWN_ACCURACY_PERCENT = 200',
    'private const val KAI_GUILTY_CROWN_DAMAGE_PER_SHOT = 10',
    'if (c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0)',
    'val totalDamage = KAI_GUILTY_CROWN_SHOTS * KAI_GUILTY_CROWN_DAMAGE_PER_SHOT',
    'val hp = max(0, c.entityHp - totalDamage)',
    'bỏ qua toàn bộ hiệu ứng né',
    'val isGuiltyCrownTurn = c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0',
    'if (!isGuiltyCrownTurn && c.entityHp > 0)',
):
    if marker not in combat:
        raise RuntimeError("Checked-in Kotlin Guilty Crown authority missing: " + marker)

ultimate_start = combat.find('    if (c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0)')
ultimate_end = combat.find('    val isGuiltyCrownTurn =', ultimate_start)
if ultimate_start < 0 or ultimate_end < 0:
    raise RuntimeError("Checked-in Kotlin Guilty Crown priority section missing")
ultimate_section = combat[ultimate_start:ultimate_end]
for forbidden in ('roll(', 'entityEvaded', 'ENTITY_EVASION_PERCENT'):
    if forbidden in ultimate_section:
        raise RuntimeError("Guilty Crown authority must bypass RNG/evasion: " + forbidden)

for marker in (
    'guiltyCrownOverrideTriggersAutomaticallyOnEveryThirdCombatTurn',
    'guiltyCrownOverrideAppliesExactTwentyFourTimesTenHpBeforeNormalRegen',
    'assertTrue(third.reply.contains("Accuracy 200%"))',
    'assertTrue(third.reply.contains("bỏ qua toàn bộ hiệu ứng né"))',
    'assertTrue(third.reply.contains("tổng -240 HP"))',
    'assertEquals(261, after.entityHp)',
    'guiltyCrownTurnKeepsPriorityOverAutomaticGunSkillRolls',
):
    if marker not in test:
        raise RuntimeError("Checked-in Guilty Crown regression coverage missing: " + marker)

print("Kai Guilty Crown authority verified in checked-in Kotlin Core; legacy patch performed no source rewrite.")
