from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
POLICY = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameplayRollPolicy.kt"

# GameplayRollPolicy is the only gameplay-roll authority. This historical slot stays in the
# chain only because later compatibility transforms still consume the pre-Core Java method shape.
# Do not restate thresholds, eligibility, pools or RNG behavior here. The terminal Entity/Core
# bridge collapses that staging method to GameplayRollPolicy after those transforms have run.
if not POLICY.is_file():
    raise RuntimeError("Kotlin GameplayRollPolicy source missing")
policy = POLICY.read_text(encoding="utf-8")
for marker in (
    "object GameplayRollPolicy",
    "EntityEncounterPolicy.roll(",
    "emuLevel1Progression",
    "emuLevel06Traversal",
    'rolls.put("exitProbe"',
    'rolls.put("levelExit"',
):
    if marker not in policy:
        raise RuntimeError("Kotlin gameplay-roll authority contract missing: " + marker)

main = MAIN.read_text(encoding="utf-8")
for marker in (
    "private JSONObject makeGameplayRolls(",
    "private boolean rollSuccess(JSONObject rolls, String key)",
):
    if marker not in main:
        raise RuntimeError("Historical roll staging shape missing before terminal Core bridge: " + marker)

print("Gameplay roll parity slot is authority-free: Kotlin GameplayRollPolicy owns probabilities, eligibility and RNG order.")
