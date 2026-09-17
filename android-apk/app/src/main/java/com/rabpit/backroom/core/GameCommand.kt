package com.rabpit.backroom.core

sealed interface GameCommand {
  val commandId: String
  val turnId: String?
  val actorId: String
  val targetId: String?
  val source: CommandSource
}

data class ItemCommand(
  override val commandId: String,
  override val turnId: String?,
  override val actorId: String,
  override val targetId: String? = null,
  override val source: CommandSource,
  val operation: Operation,
  val itemId: String,
  val itemName: String,
  val quantity: Int = 1,
  val slot: String? = null,
  val metadata: Map<String, String> = emptyMap()
) : GameCommand {
  enum class Operation { PICKUP, DROP, USE, TRANSFER, EQUIP, UNEQUIP }
}


data class QueryCommand(
  override val commandId: String,
  override val turnId: String?,
  override val actorId: String,
  override val targetId: String? = null,
  override val source: CommandSource,
  val type: Type
) : GameCommand {
  enum class Type { CHARACTER, INVENTORY, PARTY, STATUS }
}

data class ValidatedLegacyStateCommand(
  override val commandId: String,
  override val turnId: String?,
  override val actorId: String = KAI_ID,
  override val targetId: String? = null,
  override val source: CommandSource,
  val location: String? = null,
  val title: String? = null,
  val levelJson: String? = null,
  val playerJson: String? = null,
  val flagsJson: String? = null,
  val validatedByGameEngine: Boolean
) : GameCommand

data class ValidationResult(val valid: Boolean, val reason: String? = null)
data class ExecutionResult(
  val state: GameState,
  val applied: Boolean,
  val duplicate: Boolean = false,
  val validation: ValidationResult = ValidationResult(true),
  val events: List<String> = emptyList()
)
