from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
facade = FACADE.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


# StoryProgressionPolicy is authoritative at the canon boundary before this method is called.
# This debug finalizer must observe the already-normalized candidate, never normalize it again.
candidate_old = '''    val before = JSONObject(beforeJson)
    val candidate = JSONObject(candidateJson)
    val core = loadOrMigrate(before)
'''
candidate_new = '''    val before = JSONObject(beforeJson)
    val candidate = JSONObject(candidateJson)
    RuntimeDebugLog.recordTurnStage(before.optInt("turn", 0), "candidateAfterStoryNormalization", candidate)
    RuntimeDebugLog.appendTurnStage(before.optInt("turn", 0), "storyProgression", JSONObject()
      .put("storyArcBefore", before.optJSONObject("flags")?.opt("storyArc") ?: JSONObject.NULL)
      .put("storyArcNormalized", candidate.optJSONObject("flags")?.opt("storyArc") ?: JSONObject.NULL)
      .put("luciaEncounter", candidate.optJSONObject("flags")?.opt("luciaEncounter") ?: JSONObject.NULL))
    val core = loadOrMigrate(before)
'''
facade = replace_once(facade, candidate_old, candidate_new, "validated candidate post-story trace")

# Guard the ownership boundary explicitly. A debug patch must never restore story mutation here.
if "StoryProgressionPolicy.normalizeCandidate" in facade:
    raise RuntimeError("GameCore debug trace must not reintroduce StoryProgressionPolicy normalization")

prepared_old = '''    val preparedCore = synchronizeValidatedLuciaCharacter(core, candidate)
    val turnId = nextTurnId(before, preparedCore)
    val pending = TurnCoordinator.createPending(preparedCore, turnId, action)
'''
prepared_new = '''    val preparedCore = synchronizeValidatedLuciaCharacter(core, candidate)
    val turnId = nextTurnId(before, preparedCore)
    RuntimeDebugLog.recordEvent("game_core", "validated_candidate_prepared", before.optInt("turn", 0), JSONObject()
      .put("turnId", turnId)
      .put("action", action)
      .put("preparedCore", JSONObject(GameStateCodec.encode(preparedCore))))
    val pending = TurnCoordinator.createPending(preparedCore, turnId, action)
    RuntimeDebugLog.appendTurnStage(before.optInt("turn", 0), "gameCorePending", JSONObject()
      .put("turnId", turnId)
      .put("error", pending.error ?: "")
      .put("state", JSONObject(GameStateCodec.encode(pending.state))))
'''
facade = replace_once(facade, prepared_old, prepared_new, "validated candidate pending trace")

# ActionRuntime finalizers wrap the settled command commit to apply deterministic progression/time
# semantics before returning the authoritative TurnResult. The same commitActionRuntime call exists
# in another runtime path, so anchor this trace to the validated-candidate time-advance block only.
commit_old = '''    commands += timeAdvanceCommand(turnId, action)

    val committed = commitActionRuntime(pending.state, commands, action, turnId)
    if (committed.error != null) {
'''
commit_new = '''    commands += timeAdvanceCommand(turnId, action)

    RuntimeDebugLog.appendTurnStage(before.optInt("turn", 0), "gameCoreCommands", JSONArray().apply {
      commands.forEach { command -> put(JSONObject()
        .put("commandId", command.commandId)
        .put("turnId", command.turnId)
        .put("actorId", command.actorId)
        .put("source", command.source.name)
        .put("type", command::class.java.simpleName)) }
    })
    val committed = commitActionRuntime(pending.state, commands, action, turnId)
    RuntimeDebugLog.appendTurnStage(before.optInt("turn", 0), "gameCoreCommitResult", JSONObject()
      .put("error", committed.error ?: "")
      .put("state", JSONObject(GameStateCodec.encode(committed.state))))
    if (committed.error != null) {
'''
facade = replace_once(facade, commit_old, commit_new, "validated candidate commit trace")

sync_old = '''    val synchronized = syncLegacy(candidate, committed.state, incrementTurn = false)
    logger.log(PipelineLogEvent("GEMINI_COMMIT", turnId = turnId, source = CommandSource.GEMINI, details = mapOf("commands" to commands.size.toString(), "inventoryLocked" to inventoryLocked.toString())))
'''
sync_new = '''    val synchronized = syncLegacy(candidate, committed.state, incrementTurn = false)
    RuntimeDebugLog.recordTurnStage(before.optInt("turn", 0), "coreCommittedState", synchronized)
    RuntimeDebugLog.recordEvent("game_core", "validated_candidate_committed", before.optInt("turn", 0), JSONObject()
      .put("turnId", turnId)
      .put("commands", commands.size)
      .put("inventoryLocked", inventoryLocked)
      .put("state", synchronized))
    logger.log(PipelineLogEvent("GEMINI_COMMIT", turnId = turnId, source = CommandSource.GEMINI, details = mapOf("commands" to commands.size.toString(), "inventoryLocked" to inventoryLocked.toString())))
'''
facade = replace_once(facade, sync_old, sync_new, "validated candidate committed trace")

for marker in (
    '"candidateAfterStoryNormalization"',
    '"storyProgression"',
    '"gameCorePending"',
    '"gameCoreCommands"',
    '"gameCoreCommitResult"',
    '"coreCommittedState"',
    '"validated_candidate_committed"',
):
    if marker not in facade:
        raise RuntimeError("GameCore debug trace marker missing: " + marker)

FACADE.write_text(facade, encoding="utf-8")
print("GameCore runtime debug trace installed: post-story candidate, pending state, validated ActionRuntime command list, commit result and synchronized final state.")
