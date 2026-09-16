from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"
TEST = ROOT / "app/src/test/java/com/rabpit/backroom/core/CombatRuntimeTest.kt"

# ENTITY_COMBAT_DURABILITY_VERIFIER_V2
# Entity durability is now checked-in Kotlin Game Core authority. This legacy build-chain node is
# intentionally verification-only: rewriting CombatRuntime.kt here would recreate split gameplay
# authority and can also erase newer special-Entity rules (for example Diệp Minh).
combat = COMBAT.read_text(encoding="utf-8")
test = TEST.read_text(encoding="utf-8")

for marker in (
    'private const val ENTITY_HP_BONUS = 30',
    'private const val ENTITY_EVASION_PERCENT = 25',
    'private const val ENTITY_REGEN_PER_TURN = 1',
    'profile.maxHp + ENTITY_HP_BONUS',
    'val entityEvaded = evasionRoll < ENTITY_EVASION_PERCENT',
    'c.entityHp + entityRegenPerTurn(profile)',
    'val canonicalMaxHp = canonicalEntityMaxHp(profile)',
    'CharacterStatEngine.effective(state, KAI_ID).maxHp',
):
    if marker not in combat:
        raise RuntimeError("Checked-in Kotlin Entity durability authority missing: " + marker)

for forbidden in ('PLAYER_HP', 'PLAYER_MAX_HP'):
    if forbidden in combat:
        raise RuntimeError("Entity durability restored retired player combat metadata: " + forbidden)

for marker in (
    'assertEquals(110, combat.entityMaxHp)',
    'survivingEntityRegeneratesOneHpPerCombatTurnUpToMax',
    'allEntityProfilesReceiveThirtyBonusHp',
    '"slenderman" to 190',
    'diepMinhHasExact2999HpAndRegeneratesThirtyPerSurvivingTurn',
):
    if marker not in test:
        raise RuntimeError("Checked-in Entity durability regression coverage missing: " + marker)

print("Entity combat durability verified in checked-in Kotlin Core; legacy patch performed no source rewrite.")
