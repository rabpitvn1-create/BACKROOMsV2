package com.rabpit.backroom.core

const val CURRENT_SAVE_VERSION = 3
const val KAI_ID = "kai"
const val KAI_WHITE_WRAITH_ID = "kai:white-wraith-magnum"
const val KAI_BLACKBLOOD_ARMOR_ID = "kai:blackblood-armor"

object KaiStartingEquipment {
  const val WEAPON_NAME = "W.W Magnum"
  const val ARMOR_NAME = "Blackblood Armor & linked modules"

  val slots: Map<String, String> = linkedMapOf(
    "weapon" to KAI_WHITE_WRAITH_ID,
    "armor" to KAI_BLACKBLOOD_ARMOR_ID,
  )

  fun displayName(itemId: String): String? = when (itemId) {
    KAI_WHITE_WRAITH_ID -> WEAPON_NAME
    KAI_BLACKBLOOD_ARMOR_ID -> ARMOR_NAME
    else -> null
  }

  fun slotFor(itemId: String, itemName: String): String? {
    val key = "$itemId $itemName".lowercase()
    return when {
      key.contains("w.w magnum") || key.contains("white wraith") || key.contains("wraith magnum") -> "weapon"
      key.contains("blackblood armor") || key.contains("black blood armor") -> "armor"
      else -> null
    }
  }

  fun itemIdForSlot(slot: String): String? = slots[slot]
  fun isSignature(itemId: String, itemName: String): Boolean = slotFor(itemId, itemName) != null || itemId in slots.values
}

enum class CharacterPresence { ACTIVE, SEPARATED, MISSING, DEAD }
enum class CommandSource { RULE, LITERT, GEMINI, UI, SYSTEM }
enum class PendingTurnStatus { CREATED, INTERPRETING, VALIDATING, EXECUTING, COMMITTED, FAILED }

data class ItemStack(
  val itemId: String,
  val name: String,
  val quantity: Int = 1,
  val condition: String? = null,
  val metadata: Map<String, String> = emptyMap(),
  val archetypeId: String = itemId,
  val contentState: ContentState = ContentState.NONE
)

data class InventoryState(val ownerId: String, val items: Map<String, ItemStack> = emptyMap())
data class EquipmentState(val ownerId: String, val slots: Map<String, String> = emptyMap())

data class StatusEffect(
  val id: String,
  val type: String,
  val source: String,
  val startTurnId: String? = null,
  val durationTurns: Int? = null,
  val persistent: Boolean = false,
  val metadata: Map<String, String> = emptyMap()
)

data class PhysiologyState(
  val minutesSinceFood: Long? = null,
  val minutesSinceWater: Long? = null,
  val minutesAwake: Long? = null,
  val painState: String? = null,
  val infectionState: String? = null,
  val thermalState: String? = null,
  val metadata: Map<String, String> = emptyMap()
) {
  companion object {
    /** Simulation baseline for a fresh run: needs begin satisfied at Backrooms entry. */
    fun freshRunBaseline(): PhysiologyState = PhysiologyState(
      minutesSinceFood = 0L,
      minutesSinceWater = 0L,
      minutesAwake = 0L,
      metadata = mapOf("baseline" to "fresh_run_entry")
    )
  }
}

data class CharacterState(
  val id: String,
  val name: String,
  val avatarRef: String? = null,
  val healthState: String? = null,
  val injuries: List<String> = emptyList(),
  val presence: CharacterPresence = CharacterPresence.ACTIVE,
  val inventoryId: String = id,
  val equipmentId: String = id,
  val statusIds: Set<String> = emptySet(),
  val physiology: PhysiologyState = PhysiologyState(),
  val metadata: Map<String, String> = emptyMap()
)

data class PartyState(val leaderId: String = KAI_ID, val memberIds: List<String> = listOf(KAI_ID), val maxMembers: Int = 4)


data class PendingTurn(
  val turnId: String,
  val input: String,
  val status: PendingTurnStatus = PendingTurnStatus.CREATED,
  val commandIds: List<String> = emptyList(),
  val error: String? = null
)

data class TurnState(
  val currentTurnId: String = "TURN_1",
  val pending: PendingTurn? = null,
  val completedTurnIds: Set<String> = emptySet(),
  val executedCommandIds: Set<String> = emptySet()
)

data class GameTimeState(
  val elapsedSubjectiveMinutes: Long = 0L,
  val lastAdvanceMinutes: Int = 0,
  val lastAdvanceReason: String? = null
)

data class GameState(
  val characters: Map<String, CharacterState>,
  val party: PartyState = PartyState(),
  val inventories: Map<String, InventoryState> = emptyMap(),
  val equipment: Map<String, EquipmentState> = emptyMap(),
  val statuses: Map<String, StatusEffect> = emptyMap(),
  val turn: TurnState = TurnState(),
  val time: GameTimeState = GameTimeState(),
  val world: Map<String, String> = emptyMap(),
  val saveVersion: Int = CURRENT_SAVE_VERSION,
  val metadata: Map<String, String> = emptyMap()
) {
  companion object {
    fun initial(): GameState = GameState(
      characters = mapOf(
        KAI_ID to CharacterState(
          KAI_ID,
          "Kai Akechi",
          avatarRef = "avatars/kai_avatar.png",
          physiology = PhysiologyState.freshRunBaseline(),
          metadata = mapOf("inventoryProfile" to "kai")
        )
      ),
      inventories = mapOf(KAI_ID to InventoryState(KAI_ID)),
      equipment = mapOf(KAI_ID to EquipmentState(KAI_ID, KaiStartingEquipment.slots))
    )
  }
}
