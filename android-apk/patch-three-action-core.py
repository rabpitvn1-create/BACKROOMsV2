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

for forbidden in (
    "val committed = TurnCoordinator.commit(pending.state, commands)",
    "fun beginAction(legacyStateJson: String, kindRaw: String, action: String): String {\n    val legacy = JSONObject(legacyStateJson)\n    val state = loadOrMigrate(legacy)\n    val kind = enumValues<ActionKind>()",
):
    # The second marker is the exact legacy injected body. The current materialized body
    # may contain unrelated fast paths between load and kind parsing, so it must not match.
    if forbidden in facade:
        raise RuntimeError("Legacy ActionRuntime source authority survived: " + forbidden[:96])

print("Step 2 core bridge verified against checked-in Kotlin authority; no GameCoreFacade rewrite performed.")
