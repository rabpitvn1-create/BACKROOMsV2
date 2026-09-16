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

# Keep the trace tied to the same authoritative lifecycle it is meant to observe. These checks catch
# a stale debug snapshot without granting Python any authority to reconstruct gameplay code.
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
positions = [facade.find(marker) for marker in ordering]
if any(position < 0 for position in positions) or positions != sorted(positions):
    raise RuntimeError("Checked-in GameCore runtime debug trace is stale or out of authoritative commit order")

print(
    "GameCore runtime debug trace verified in checked-in Kotlin Core: post-story candidate, pending state, "
    "validated ActionRuntime command list, commit result and synchronized final state; no source rewrite performed."
)
