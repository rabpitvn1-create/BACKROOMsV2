from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
POLICY = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameplayRollPolicy.kt"
SPECIAL_FOLLOWER_PATCH = ROOT / "patch-an-nhien-follower-final.py"

# GameplayRollPolicy is the only gameplay-roll authority. This historical slot stays in the
# chain because later compatibility transforms still consume the pre-Core Java method shape.
# Materialize the remaining An Nhien core/UI compatibility here while stripping its retired
# Java gameplay authority, then let the terminal Entity/Core bridge collapse the staging method.
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

if not SPECIAL_FOLLOWER_PATCH.is_file():
    raise RuntimeError("Special follower compatibility patch missing")
exec(compile(SPECIAL_FOLLOWER_PATCH.read_text(encoding="utf-8"), str(SPECIAL_FOLLOWER_PATCH), "exec"), {
    "__name__": "__main__",
    "__file__": str(SPECIAL_FOLLOWER_PATCH),
})

print("Gameplay roll parity slot is authority-free: Kotlin GameplayRollPolicy owns probabilities, eligibility and RNG order.")
