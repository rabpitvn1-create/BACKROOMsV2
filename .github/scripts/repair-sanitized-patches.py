from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

lucia_path = ROOT / "android-apk/patch-lucia-follower.py"
lucia = lucia_path.read_text(encoding="utf-8")
broken_profile = "profile_anchor = '  val NORMAL = InventoryProfile(maxTypes = 2, maxPerType = 2)\n'"
fixed_profile = "profile_anchor = '  val NORMAL = InventoryProfile(maxTypes = 2, maxPerType = 2)\\n'"
lucia = lucia.replace(broken_profile, fixed_profile)
broken_lucia = "policy = replace_once(policy, profile_anchor, '  val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)\n' + profile_anchor, \"Lucia inventory profile\")"
fixed_lucia = "policy = replace_once(policy, profile_anchor, '  val LUCIA = InventoryProfile(maxTypes = 3, maxPerType = 100)\\n' + profile_anchor, \"Lucia inventory profile\")"
lucia = lucia.replace(broken_lucia, fixed_lucia)
compile(lucia, str(lucia_path), "exec")
lucia_path.write_text(lucia, encoding="utf-8")

stats_path = ROOT / "android-apk/patch-character-stat-schema.py"
stats = stats_path.read_text(encoding="utf-8")
stats = stats.replace('    "PROTECTED FOLLOWER / NON-COMBAT",\n', '')
compile(stats, str(stats_path), "exec")
stats_path.write_text(stats, encoding="utf-8")

# The historical special-follower patch mixed a retired follower with the still-live Iris/Syvial
# canon. Materialize only the shared live canon directly so the equipment/status pipeline remains
# self-contained after the mixed legacy patch is deleted.
special_path = ROOT / "android-apk/app/src/main/java/com/rabpit/backroom/core/SpecialFollowersCanon.kt"
special_path.write_text(r'''package com.rabpit.backroom.core

const val IRIS_ID = "iris"
const val SYVIAL_ID = "syvial"

const val IRIS_IVORY_ID = "iris:ivory"
const val IRIS_EBONY_ID = "iris:ebony"
const val IRIS_RECON_FRAME_ID = "iris:blackblood-recon-frame-r03"
const val SYVIAL_GODKILLER_ID = "syvial:godkiller"
const val SYVIAL_LUCIFER_ARMOR_ID = "syvial:lucifer-armor"

object SpecialFollowersCanon {
  const val ENCOUNTER_CHANCE = "0.25%"
  const val ENCOUNTER_LEVELS = "0-6"

  val irisEquipmentSlots: Map<String, String> = linkedMapOf(
    "weapon_primary" to IRIS_IVORY_ID,
    "weapon_secondary" to IRIS_EBONY_ID,
    "armor" to IRIS_RECON_FRAME_ID
  )

  val syvialEquipmentSlots: Map<String, String> = linkedMapOf(
    "weapon" to SYVIAL_GODKILLER_ID,
    "armor" to SYVIAL_LUCIFER_ARMOR_ID
  )

  fun irisCharacter(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = IRIS_ID,
      name = "Iris",
      physiology = PhysiologyState.freshRunBaseline()
    )
    return base.copy(
      id = IRIS_ID,
      name = "Iris",
      inventoryId = IRIS_ID,
      equipmentId = IRIS_ID,
      metadata = base.metadata + mapOf(
        "npcType" to "follower",
        "joinEligible" to "true",
        "followsPlayer" to "true",
        "encounterChance" to ENCOUNTER_CHANCE,
        "encounterLevels" to ENCOUNTER_LEVELS,
        "combatant" to "true",
        "role" to "Scout / Target Eliminator",
        "combatStyle" to "Gunslinger",
        "signatureWeapons" to "Ivory & Ebony",
        "armor" to "Blackblood Recon Frame R03",
        "canonRef" to "IRIS-BELIAL-BLACKBLOOD-CODEX-20260817-R05",
        "inventoryProfile" to "special_companion"
      )
    )
  }

  fun syvialCharacter(existing: CharacterState? = null): CharacterState {
    val base = existing ?: CharacterState(
      id = SYVIAL_ID,
      name = "Syvial",
      physiology = PhysiologyState.freshRunBaseline()
    )
    return base.copy(
      id = SYVIAL_ID,
      name = "Syvial",
      inventoryId = SYVIAL_ID,
      equipmentId = SYVIAL_ID,
      metadata = base.metadata + mapOf(
        "npcType" to "follower",
        "joinEligible" to "true",
        "followsPlayer" to "true",
        "encounterChance" to ENCOUNTER_CHANCE,
        "encounterLevels" to ENCOUNTER_LEVELS,
        "combatant" to "true",
        "combatTier" to "UR+",
        "role" to "High-level supernatural swordswoman",
        "signatureWeapon" to "GodKiller",
        "armor" to "Lucifer Armor",
        "canonRef" to "SYVIAL-LUCIFER-CODEX-20260816-R03",
        "inventoryProfile" to "special_companion"
      )
    )
  }

  fun ensure(state: GameState): GameState {
    val iris = irisCharacter(state.characters[IRIS_ID])
    val syvial = syvialCharacter(state.characters[SYVIAL_ID])
    val irisInventory = state.inventories[IRIS_ID] ?: InventoryState(IRIS_ID)
    val syvialInventory = state.inventories[SYVIAL_ID] ?: InventoryState(SYVIAL_ID)
    val irisEquipment = state.equipment[IRIS_ID]?.slots.orEmpty() + irisEquipmentSlots
    val syvialEquipment = state.equipment[SYVIAL_ID]?.slots.orEmpty() + syvialEquipmentSlots
    return state.copy(
      characters = state.characters + (IRIS_ID to iris) + (SYVIAL_ID to syvial),
      inventories = state.inventories + (IRIS_ID to irisInventory) + (SYVIAL_ID to syvialInventory),
      equipment = state.equipment +
        (IRIS_ID to EquipmentState(IRIS_ID, irisEquipment)) +
        (SYVIAL_ID to EquipmentState(SYVIAL_ID, syvialEquipment))
    )
  }
}
''', encoding="utf-8")

print("Sanitized patches repaired; shared Iris/Syvial canon preserved.")
