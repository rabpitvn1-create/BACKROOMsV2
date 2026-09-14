from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
TEMPLATE = ROOT / "patch-runtime-debug-turn-trace-v2-final.py"

main = MAIN.read_text(encoding="utf-8")
source = TEMPLATE.read_text(encoding="utf-8")

# Later startup/action-policy patches can rewrite the settled bridge while preserving the same
# semantics. Adapt only the exact anchors that are known to drift, rather than duplicating another
# copy of the already-large tracing template.
if "requireGameCore().processRule(stateJson, action)" in main:
    source = source.replace("gameCore.processRule(stateJson, action)", "requireGameCore().processRule(stateJson, action)")
if "requireGameCore().processValidatedCandidate(before.toString(), candidateState.toString(), action)" in main:
    source = source.replace(
        "gameCore.processValidatedCandidate(before.toString(), candidateState.toString(), action)",
        "requireGameCore().processValidatedCandidate(before.toString(), candidateState.toString(), action)",
    )

# The typed action patches have changed makeGameplayRolls parameters over time. The diagnostic only
# needs the resulting rolls object, so locate that settled declaration without assuming its args.
old_roll_anchor = 'roll_anchor = "          JSONObject rolls = makeGameplayRolls(before, action, meta);\\n"\n'
new_roll_anchor = '''roll_match = re.search(r'(?m)^\\s*JSONObject rolls = makeGameplayRolls\\([^;\\n]+\\);\\n', submit)
if roll_match is None:
    raise RuntimeError("roll trace: settled makeGameplayRolls declaration not found")
roll_anchor = roll_match.group(0)
'''
if old_roll_anchor not in source:
    raise RuntimeError("adaptive roll-template anchor missing")
source = source.replace(old_roll_anchor, new_roll_anchor, 1)

namespace = {"__name__": "__main__", "__file__": str(TEMPLATE), "re": re}
exec(compile(source, str(TEMPLATE), "exec"), namespace, namespace)
print("Adaptive runtime turn trace matched the settled GameCore accessor and typed roll declaration.")
