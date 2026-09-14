from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
facade = FACADE.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


normalize_old = '''    val before = JSONObject(beforeJson)
    val candidate = StoryProgressionPolicy.normalizeCandidate(before, JSONObject(candidateJson), action)
    val core = loadOrMigrate(before)
'''
normalize_new = '''    val before = JSONObject(beforeJson)
    val rawCandidate = JSONObject(candidateJson)
    RuntimeDebugLog.recordTurnStage(before.optInt("turn", 0), "candidateBeforeStoryNormalization", rawCandidate)
    val candidate = StoryProgressionPolicy.normalizeCandidate(before, rawCandidate, action)
    RuntimeDebugLog.recordTurnStage(before.optInt("turn", 0), "candidateNormalized", candidate)
    RuntimeDebugLog.appendTurnStage(before.optInt("turn", 0), "storyProgression", JSONObject()
      .put("storyArcBefore", before.optJSONObject("flags")?.opt("storyArc") ?: JSONObject.NULL)
      .put("storyArcNormalized", candidate.optJSONObject("flags")?.opt("storyArc") ?: JSONObject.NULL)
      .put("luciaEncounter", candidate.optJSONObject("flags")?.opt("luciaEncounter") ?: JSONObject.NULL))
    val core = loadOrMigrate(before)
'''
facade = replace_once(facade, normalize_old, normalize_new, "validated candidate story normalization")

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

# Other finalizers may insert authoritative commands immediately before commit. Anchor on the
# settled commit itself so diagnostics observe the exact command list that is actually committed.
commit_old = '''    val committed = TurnCoordinator.commit(pending.state, commands)
'''
commit_new = '''    RuntimeDebugLog.appendTurnStage(before.optInt("turn", 0), "gameCoreCommands", JSONArray().apply {
      commands.forEach { command -> put(JSONObject()
        .put("commandId", command.commandId)
        .put("turnId", command.turnId)
        .put("actorId", command.actorId)
        .put("source", command.source.name)
        .put("type", command::class.java.simpleName)) }
    })
    val committed = TurnCoordinator.commit(pending.state, commands)
    RuntimeDebugLog.appendTurnStage(before.optInt("turn", 0), "gameCoreCommitResult", JSONObject()
      .put("error", committed.error ?: "")
      .put("state", JSONObject(GameStateCodec.encode(committed.state))))
'''
if facade.count(commit_old) != 1:
    diagnostic = [
        f"{index + 1}: {line}"
        for index, line in enumerate(facade.splitlines())
        if "TurnCoordinator" in line or "committed" in line or "commands" in line
    ]
    raise RuntimeError(
        "validated candidate commit trace: settled GameCore anchor not found; candidates:\n"
        + "\n".join(diagnostic[-80:])
    )
facade = facade.replace(commit_old, commit_new, 1)

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
    '"candidateBeforeStoryNormalization"',
    '"candidateNormalized"',
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
print("GameCore runtime debug trace installed: raw/normalized story state, pending state, settled command list, commit result and synchronized final state.")
