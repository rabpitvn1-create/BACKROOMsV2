package com.rabpit.backroom.core

import java.util.Random
import org.json.JSONObject

/** Kill rewards are SYSTEM-owned and never depend on the exploration loot roll or GM. */
object EntityDrops {
  private const val PENDING = "entity.pendingDrops"
  private const val LAST = "entity.lastRewardedEncounter"
  data class Award(val state: GameState, val message: String)

  fun award(state: GameState, encounterId: String, random: Random = Random()): Award {
    if (encounterId.isBlank() || state.metadata[LAST] == encounterId) return Award(state, "")
    val pool = ItemCatalog.all().filter { it.rewardable }
    check(pool.isNotEmpty()) { "Entity drop catalog is empty" }
    val item = pool[random.nextInt(pool.size)]
    val pending = JSONObject(state.metadata[PENDING] ?: "{}")
    val previous = pending.optInt(item.id, 0)
    pending.put(item.id, previous + 1)
    val next = claimPending(state.copy(metadata = state.metadata +
      mapOf(LAST to encounterId, PENDING to pending.toString())))
    val remaining = JSONObject(next.metadata[PENDING] ?: "{}").optInt(item.id, 0)
    val message = if (remaining > 0) "${item.displayName} đã rớt; kho đầy, phần thưởng được lưu chờ nhận."
      else "Nhận 1 ${item.displayName} từ Entity."
    return Award(next, message)
  }

  fun claimPending(state: GameState): GameState {
    val pending = JSONObject(state.metadata[PENDING] ?: "{}")
    var next = state
    for (id in pending.keys().asSequence().toList()) {
      val definition = ItemCatalog.resolve(id) ?: continue
      var count = pending.optInt(id, 0)
      val item = ItemCatalog.canonicalize(definition, ItemStack(id, definition.displayName))
      while (count > 0) {
        val inventory = next.inventories[KAI_ID] ?: InventoryState(KAI_ID)
        if (InventoryPolicy.validateAddition(next, KAI_ID, inventory, item, 1) != null) break
        val old = inventory.items[id]
        val updated = (old ?: item).copy(quantity = (old?.quantity ?: 0) + 1)
        next = next.copy(inventories = next.inventories +
          (KAI_ID to inventory.copy(items = inventory.items + (id to updated))))
        count--
      }
      if (count == 0) pending.remove(id) else pending.put(id, count)
    }
    return next.copy(metadata = if (pending.length() == 0) next.metadata - PENDING
      else next.metadata + (PENDING to pending.toString()))
  }
}
