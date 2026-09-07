from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
COMBAT = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatRuntime.kt"


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# patch-lucia-combat-evasion-compat.py runs earlier in the runtime patch chain and
# expands Lucia's hit gate. The true-turn finalizer deliberately starts from the
# original two-line Lucia attack anchor so it can move that attack out of Kai's
# local hitChance scope. Temporarily normalize the gate, run the finalizer, then
# restore the existing 25% Entity-evasion rule against Lucia's independent chance.
combat = COMBAT.read_text(encoding="utf-8")

evasion_gate = '''          val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
          val luciaEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 97), 100)
          val luciaEntityEvaded = luciaEvasionRoll < ENTITY_EVASION_PERCENT
          if (luciaRoll < hitChance && !luciaEntityEvaded) {
'''
baseline_gate = '''          val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
          if (luciaRoll < hitChance) {
'''

if "TRUE_TURN_COMBAT_V2" not in combat:
    combat = replace_once(combat, evasion_gate, baseline_gate, "normalize Lucia evasion gate before true-turn split")
    COMBAT.write_text(combat, encoding="utf-8")

runpy.run_path(str(ROOT / "patch-true-turn-combat-final.py"), run_name="__main__")

combat = COMBAT.read_text(encoding="utf-8")
true_turn_gate = '''          val luciaRangeBonus = when (c.range) { RangeBand.CLOSE -> 18; RangeBand.NEAR -> 10; RangeBand.FAR -> -5 }
          val luciaHitChance = (58 + luciaRangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)
          val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
          if (luciaRoll < luciaHitChance) {
'''
true_turn_evasion_gate = '''          val luciaRangeBonus = when (c.range) { RangeBand.CLOSE -> 18; RangeBand.NEAR -> 10; RangeBand.FAR -> -5 }
          val luciaHitChance = (58 + luciaRangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)
          val luciaRoll = roll(c.copy(eventCounter = c.eventCounter + 83), 100)
          val luciaEvasionRoll = roll(c.copy(eventCounter = c.eventCounter + 97), 100)
          val luciaEntityEvaded = luciaEvasionRoll < ENTITY_EVASION_PERCENT
          if (luciaRoll < luciaHitChance && !luciaEntityEvaded) {
'''
combat = replace_once(combat, true_turn_gate, true_turn_evasion_gate, "restore Lucia evasion after true-turn split")

for marker in (
    "TRUE_TURN_COMBAT_V2",
    "val luciaHitChance = (58 + luciaRangeBonus + c.opening * 11 + c.momentum * 6).coerceIn(20, 96)",
    "val luciaEntityEvaded = luciaEvasionRoll < ENTITY_EVASION_PERCENT",
    "if (luciaRoll < luciaHitChance && !luciaEntityEvaded)",
):
    if marker not in combat:
        raise RuntimeError("True-turn Lucia evasion compatibility marker missing: " + marker)

COMBAT.write_text(combat, encoding="utf-8")
print("True-turn Lucia compatibility applied: independent Lucia hit scope with existing 25% Entity evasion preserved.")
