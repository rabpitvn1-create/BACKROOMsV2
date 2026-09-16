from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
facade = FACADE.read_text(encoding="utf-8")

# Runtime debug tracing is already materialized in the checked-in Kotlin Core. This historical
# finalizer is verification-only: build-time Python must not rewrite GameCoreFacade, especially
# around the authoritative validated-candidate/ActionRuntime commit path.
#
# StoryProgressionPolicy remains authoritative at the canon boundary before this method is called.
# A debug verifier must never reintroduce story mutation here.
if "StoryProgressionPolicy.normalizeCandidate" in facade:
    raise RuntimeError("GameCore debug trace must not reintroduce StoryProgressionPolicy normalization")

required = (
    "fun processValidatedCandidate(",
    'RuntimeDebugLog.recordTurnStage(before.optInt("turn", 0), "candidateAfterStoryNormalization", candidate)',
    'RuntimeDebugLog.appendTurnStage(before.optInt("turn", 0), "storyProgression", JSONObject()',
    'RuntimeDebugLog.recordEvent("game_core", "validated_candidate_prepared", before.optInt("turn", 0), JSONObject()',
    'RuntimeDebugLog.appendTurnStage(before.optInt("turn", 0), "gameCorePending", JSONObject()',
    'RuntimeDebugLog.appendTurnStage(before.optInt("turn", 0), "gameCoreCommands", JSONArray().apply {',
    'val committed = commitActionRuntime(pending.state, commands, action, turnId)',
    'RuntimeDebugLog.appendTurnStage(before.optInt("turn", 0), "gameCoreCommitResult", JSONObject()',
    'RuntimeDebugLog.recordTurnStage(before.optInt("turn", 0), "coreCommittedState", synchronized)',
    'RuntimeDebugLog.recordEvent("game_core", "validated_candidate_committed", before.optInt("turn", 0), JSONObject()',
)
for marker in required:
    if marker not in facade:
        raise RuntimeError("Checked-in GameCore runtime debug contract missing: " + marker)

# Keep the trace tied to processValidatedCandidate itself. Similar commitActionRuntime calls exist in
# earlier Core paths, so a whole-file position comparison would mistake those legitimate calls for
# this method's commit point. Verify monotonically inside this method instead.
method_start = facade.find("fun processValidatedCandidate(")
method_end = facade.find("\n  fun startEntityEncounters(", method_start)
if method_start < 0 or method_end < 0:
    raise RuntimeError("Checked-in processValidatedCandidate boundary missing")
method = facade[method_start:method_end]
ordering = (
    '"candidateAfterStoryNormalization"',
    '"validated_candidate_prepared"',
    '"gameCorePending"',
    '"gameCoreCommands"',
    'commitActionRuntime(pending.state, commands, action, turnId)',
    '"gameCoreCommitResult"',
    '"coreCommittedState"',
    '"validated_candidate_committed"',
)
cursor = 0
for marker in ordering:
    position = method.find(marker, cursor)
    if position < 0:
        raise RuntimeError("Checked-in GameCore runtime debug trace is stale or out of authoritative commit order: " + marker)
    cursor = position + len(marker)

print(
    "GameCore runtime debug trace verified in checked-in Kotlin Core: post-story candidate, pending state, "
    "validated ActionRuntime command list, commit result and synchronized final state; no source rewrite performed."
)
