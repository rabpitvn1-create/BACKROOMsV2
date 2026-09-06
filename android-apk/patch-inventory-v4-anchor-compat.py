from pathlib import Path

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"

facade = FACADE.read_text(encoding="utf-8")

# The release patch stack may rewrite the existing player-pickup guard while preserving its
# semantics. Inventory V4's finalizer needs one stable insertion point inside processRule, so add a
# canonical guard immediately before the Restore hand-off when that exact block is no longer present.
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
    section = facade[method_start:method_end]
    if "GameIntent.PICKUP_ITEM" not in section or "player_pickup_unavailable" not in section:
        raise RuntimeError("Inventory V4 compat: existing player pickup authority guard missing")

    restore_candidates = (
        "    // Restore is lore/narrative-only.",
        "    if (interpreted.candidates.any { it.intent == GameIntent.OMNIVAULT_RESTORE }) {",
    )
    insert_at = -1
    for marker in restore_candidates:
        absolute = facade.find(marker, method_start, method_end)
        if absolute >= 0:
            insert_at = absolute
            break
    if insert_at < 0:
        raise RuntimeError("Inventory V4 compat: Restore hand-off anchor missing")
    facade = facade[:insert_at] + pickup_block + "\n" + facade[insert_at:]

FACADE.write_text(facade, encoding="utf-8")
print("Inventory V4 compatibility anchor prepared without changing existing authority semantics.")
