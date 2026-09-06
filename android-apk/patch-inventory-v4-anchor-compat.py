from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
FINALIZER = ROOT / "patch-inventory-v4-final.py"

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

# Keep every load/backfill rule installed by the historical patch stack. Wrap the final generated
# loadOrMigrate implementation instead of replacing it with an older baseline implementation.
if "private fun loadOrMigratePreV4(" not in facade:
    load_start = facade.find("  private fun loadOrMigrate(")
    if load_start < 0:
        raise RuntimeError("Inventory V4 compat: loadOrMigrate missing")
    following = list(re.finditer(r"\n  private fun [A-Za-z0-9_]+\(", facade[load_start + 4:]))
    if not following:
        raise RuntimeError("Inventory V4 compat: loadOrMigrate end boundary missing")
    load_end = load_start + 4 + following[0].start()
    legacy_method = facade[load_start:load_end]
    renamed = legacy_method.replace(
        "  private fun loadOrMigrate(",
        "  private fun loadOrMigratePreV4(",
        1,
    )
    wrapper = '''  private fun loadOrMigrate(legacy: JSONObject): GameState {
    val loaded = loadOrMigratePreV4(legacy)
    val normalized = InventoryV4State.normalize(loaded)
    if (normalized != loaded) repository.save(normalized)
    return normalized
  }

'''
    facade = facade[:load_start] + wrapper + renamed + facade[load_end:]

FACADE.write_text(facade, encoding="utf-8")

# The finalizer was initially written against the checked-in baseline loadOrMigrate body. At this
# point the generated facade contains additional follower/save backfills, so tell the finalizer to
# verify the wrapper above rather than replacing those semantics.
finalizer = FINALIZER.read_text(encoding="utf-8")
brittle_load_line = 'facade = replace_once(facade, load_old, load_new, "Inventory V4 load normalization")'
robust_load_check = '''if "InventoryV4State.normalize(loaded)" not in facade or "loadOrMigratePreV4(legacy)" not in facade:
    raise RuntimeError("Inventory V4 load normalization wrapper missing")'''
if robust_load_check not in finalizer:
    if finalizer.count(brittle_load_line) != 1:
        raise RuntimeError("Inventory V4 compat: finalizer load hook changed unexpectedly")
    finalizer = finalizer.replace(brittle_load_line, robust_load_check, 1)
    FINALIZER.write_text(finalizer, encoding="utf-8")

print("Inventory V4 compatibility prepared: pickup guard restored and generated load semantics preserved.")
