from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
facade = FACADE.read_text(encoding="utf-8")

# ActionRuntime authority is materialized in checked-in Kotlin on the issue-43 cleanup
# branch. This historical patch is verification-only: build-time Python must not rewrite
# GameCoreFacade or reintroduce an older pending-turn commit shape.
required = (
    "fun beginAction(legacyStateJson: String, kindRaw: String, action: String)",
    "fun currentActionContext(): String",
    "fun abortAction(reason: String): Boolean",
    "private fun commitActionRuntime(",
    "ActionRuntime.advance(state, active.sessionId, \"resolve\", minutes)",
    "ActionRuntime.markSearchCoverage(",
    "ActionRuntime.complete(finalState, active.sessionId)",
    "val committed = commitActionRuntime(pending.state, commands, action, turnId)",
)
for marker in required:
    if marker not in facade:
        raise RuntimeError("Materialized ActionRuntime core contract missing: " + marker)

# The pre-ActionRuntime processRule path committed directly from the pending state.
# beginAction itself may legitimately load state and parse ActionKind consecutively now
# that retired fast paths are gone, so that source shape is not evidence of legacy authority.
for forbidden in (
    "val committed = TurnCoordinator.commit(pending.state, commands)",
):
    if forbidden in facade:
        raise RuntimeError("Legacy ActionRuntime source authority survived: " + forbidden)

print("Step 2 core bridge verified against checked-in Kotlin authority; no GameCoreFacade rewrite performed.")
