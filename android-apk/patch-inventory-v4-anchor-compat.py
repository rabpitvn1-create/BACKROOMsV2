from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"

facade = FACADE.read_text(encoding="utf-8")

# The release patch stack currently removes the early processRule pickup guard while the reducer
# still rejects unauthorized PICKUP commands. Restore an explicit deterministic guard here so the
# final V4 layer can also attach the UI-only management gate without invoking Gemini.
pickup_block = '''    if (isDirectPlayerPickupAction(action) || interpreted.candidates.any { it.intent == GameIntent.PICKUP_ITEM }) {
      val result = syncLegacy(legacy, state, incrementTurn = false)
      val reply = validationReply("player_pickup_unavailable")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "player_pickup_unavailable")))
      return response(true, result, "player_pickup_unavailable", "validation_rejected", reply)
    }
'''

if pickup_block not in facade:
    method_start = facade.find("  fun processRule(")
    method_end = facade.find("\n  fun ", method_start + 4)
    if method_start < 0:
        raise RuntimeError("Inventory V4 compat: processRule missing")
    if method_end < 0:
        method_end = len(facade)

    insert_candidates = (
        "    // Restore is lore/narrative-only.",
        "    if (interpreted.candidates.any { it.intent == GameIntent.OMNIVAULT_RESTORE }) {",
        "    if (interpreted.candidates.any { it.intent == GameIntent.NO_ACTION || it.confidence != IntentConfidence.HIGH }) {",
        "    val resolvedCommands = interpreted.candidates.mapIndexedNotNull",
    )
    insert_at = -1
    for marker in insert_candidates:
        absolute = facade.find(marker, method_start, method_end)
        if absolute >= 0:
            insert_at = absolute
            break
    if insert_at < 0:
        raise RuntimeError("Inventory V4 compat: processRule insertion anchor missing")
    facade = facade[:insert_at] + pickup_block + "\n" + facade[insert_at:]

FACADE.write_text(facade, encoding="utf-8")
print("Inventory V4 compatibility guard prepared for the final generated GameCore facade.")
