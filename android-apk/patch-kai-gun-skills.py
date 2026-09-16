from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"

# KAI_GUN_SKILLS_VERIFIER_V2
# Kai's combat skills are checked-in Kotlin Game Core authority. This legacy build-chain node is
# verification-only so it cannot replace true-turn targeting, penalties, or persistence.
combat = COMBAT.read_text(encoding="utf-8")
test = TEST.read_text(encoding="utf-8")

for marker in (
    'private const val KAI_LAST_REQUIEM_CHANCE_PERCENT = 30',
    'private const val KAI_LAST_REQUIEM_DAMAGE_PERCENT = 170',
    'private const val KAI_LAST_REQUIEM_BLEED_TURNS = 3',
    'private const val KAI_LAST_REQUIEM_BLEED_MAX_HP_PERCENT = 5',
    'private const val KAI_SILENT_LULLABY_CHANCE_PERCENT = 20',
    'private const val KAI_SILENT_LULLABY_DAMAGE_PERCENT = 130',
    'private const val KAI_SALVATION_CHANCE_PERCENT = 20',
    'private const val KAI_SALVATION_DAMAGE_PERCENT = 147',
    'private const val KAI_QUICK_STEP_CHANCE_PERCENT = 30',
    'private const val KAI_QUICK_STEP_EVASION_BONUS_PERCENT = 50',
    'private const val KAI_QUICK_STEP_DURATION_TURNS = 3',
    'private const val KAI_BLEED_TURNS_KEY = "combat.kaiBleedTurns"',
    'private const val KAI_QUICK_STEP_TURNS_KEY = "combat.kaiQuickStepTurns"',
    'Bleeding từ The Last Requiem gây',
    'The Last Requiem tự động kích hoạt',
    'Silent Lullaby tự động kích hoạt',
    'Salvation tự động kích hoạt',
    'Quick Step tự động kích hoạt',
    'var entityStunnedThisTurn = splitAuto && (state.metadata[AUTO_STUN_KEY]?.toIntOrNull() ?: 0) > 0',
    'val quickStepEvasion = if (quickStepTurns > 0) KAI_QUICK_STEP_EVASION_BONUS_PERCENT else 0',
    'trueTurnEnemyAccuracyPenalty',
    'resolvedState = withCombatCounter(resolvedState, KAI_QUICK_STEP_TURNS_KEY, quickStepTurns)',
    'val isGuiltyCrownTurn = c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0',
):
    if marker not in combat:
        raise RuntimeError("Checked-in Kotlin Kai gun-skill authority missing: " + marker)

ultimate_start = combat.find('    if (c.eventCounter % KAI_GUILTY_CROWN_INTERVAL_TURNS == 0)')
ultimate_end = combat.find('    val isGuiltyCrownTurn =', ultimate_start)
if ultimate_start < 0 or ultimate_end < 0:
    raise RuntimeError("Checked-in Guilty Crown priority section missing after Kai gun skills")
ultimate_section = combat[ultimate_start:ultimate_end]
for marker in (
    'KAI_GUILTY_CROWN_SHOTS * KAI_GUILTY_CROWN_DAMAGE_PER_SHOT',
    'Accuracy $KAI_GUILTY_CROWN_ACCURACY_PERCENT%',
    'bỏ qua toàn bộ hiệu ứng né',
):
    if marker not in ultimate_section:
        raise RuntimeError("Guilty Crown priority contract changed: " + marker)
for forbidden in ('roll(', 'ENTITY_EVASION_PERCENT'):
    if forbidden in ultimate_section:
        raise RuntimeError("Kai gun-skill authority introduced RNG/evasion into Guilty Crown: " + forbidden)

for marker in (
    'kaiAutomaticGunSkillsExposeAllFourIndependentProcContracts',
    'lastRequiemBleedingPersistsAndTicksFivePercentMaxHp',
    'silentLullabyStunSuppressesCurrentEnemyResponse',
    'quickStepGrantsFiftyEvasionForThreeTurnsAndCountsDown',
    'guiltyCrownTurnKeepsPriorityOverAutomaticGunSkillRolls',
    'assertEquals("2", result.state.metadata["combat.kaiQuickStepTurns"])',
    'trueTurnEntityResponseHonorsPersistedAccuracyPenalty',
    'trueTurnPendingAccuracyPenaltyIsConsumedByFirstEntityResponse',
):
    if marker not in test:
        raise RuntimeError("Checked-in Kai gun-skill regression coverage missing: " + marker)

print("Kai gun-skill authority verified in checked-in Kotlin Core; legacy patch performed no source rewrite.")
