from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
TEMPLATE = ROOT / "patch-runtime-debug-turn-trace-v2-final.py"

main = MAIN.read_text(encoding="utf-8")
source = TEMPLATE.read_text(encoding="utf-8")

# Later startup-hardening patches can rewrite the GameCore bridge from direct field access to
# requireGameCore(). The tracing template intentionally stays readable; adapt only its exact call
# anchors to the accessor that survived the settled runtime.
if "requireGameCore().processRule(stateJson, action)" in main:
    source = source.replace("gameCore.processRule(stateJson, action)", "requireGameCore().processRule(stateJson, action)")
if "requireGameCore().processValidatedCandidate(before.toString(), candidateState.toString(), action)" in main:
    source = source.replace(
        "gameCore.processValidatedCandidate(before.toString(), candidateState.toString(), action)",
        "requireGameCore().processValidatedCandidate(before.toString(), candidateState.toString(), action)",
    )

namespace = {"__name__": "__main__", "__file__": str(TEMPLATE)}
exec(compile(source, str(TEMPLATE), "exec"), namespace, namespace)
print("Adaptive runtime turn trace matched the settled lazy GameCore accessor.")
