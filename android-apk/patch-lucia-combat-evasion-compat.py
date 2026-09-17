from pathlib import Path

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"

# Combat gameplay is checked in Kotlin now. This legacy compatibility step is
# verification-only: it must never materialize or rewrite CombatRuntime at build time.
text = COMBAT.read_text(encoding="utf-8")
required = (
    "TRUE_TURN_COMBAT_V2",
    "val luciaHitChance = (58 + luciaRangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)",
    "val luciaEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 97), 100)",
    "val luciaEntityEvaded = luciaEvasionRoll < ENTITY_EVASION_PERCENT",
    "if (luciaRoll < luciaHitChance && !luciaEntityEvaded)",
)
for marker in required:
    if marker not in text:
        raise RuntimeError("Materialized Lucia Entity evasion contract missing: " + marker)

# Guard against the pre-true-turn source shape returning to the authoritative file.
if "if (luciaRoll < hitChance && !luciaEntityEvaded)" in text:
    raise RuntimeError("Legacy Lucia hitChance-scoped evasion gate returned to CombatRuntime")

print("Lucia Entity evasion verified in checked-in Kotlin CombatRuntime; no source rewrite performed.")
