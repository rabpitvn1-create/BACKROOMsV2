package com.rabpit.backroom.core

const val KAI_DEMON_JAW_MASK_ID = "kai:demon-jaw-mask"
const val KAI_TALON_GAUNTLETS_ID = "kai:talon-gauntlets"
const val KAI_PHANTOM_GREAVES_ID = "kai:phantom-greaves"
const val IRIS_IVORY_EBONY_SET_ID = "iris:ivory-ebony-set"

enum class EquipmentSlot(val key: String) {
  WEAPON("weapon"), ARMOR("armor"), HEAD("head"), GAUNTLETS("gauntlets"), GREAVES("greaves"),
  RING("ring"), SPECIAL("special"), BLADE("blade"), WRIST("wrist"), OUTFIT("outfit"), FOOTWEAR("footwear");

  companion object {
    fun fromRaw(raw: String?): EquipmentSlot? {
      val key = raw?.trim()?.lowercase()?.replace('-', '_') ?: return null
      return when (key) {
        "weapon", "weapon_primary", "weapon_secondary" -> WEAPON
        "armor" -> ARMOR
        "head", "mask", "helmet" -> HEAD
        "gauntlet", "gauntlets", "gloves" -> GAUNTLETS
        "greave", "greaves", "boots" -> GREAVES
        "ring" -> RING
        "special" -> SPECIAL
        "blade", "knife" -> BLADE
        "wrist", "watch" -> WRIST
        "outfit" -> OUTFIT
        "footwear", "shoes", "slippers" -> FOOTWEAR
        else -> null
      }
    }
  }
}

enum class ItemClassification { CANONICAL, GENERAL }

data class EquipmentBonuses(
  val hp: Int = 0,
  val str: Int = 0,
  val df: Int = 0,
  val agi: Int = 0,
  val crit: Int = 0
) {
  fun any() = hp != 0 || str != 0 || df != 0 || agi != 0 || crit != 0
}

data class WeaponGameplayStats(
  val dmg: Int,
  val ammoDisplay: String? = null,
  val rpmCapability: Int? = null,
  val fireModes: List<String> = emptyList()
)

data class EquipmentAbility(
  val name: String,
  val description: String,
  val importantLimit: String? = null
)

data class EquipmentComponent(
  val name: String,
  val bonuses: EquipmentBonuses = EquipmentBonuses(),
  val weapon: WeaponGameplayStats? = null
)

data class EquipmentDefinition(
  val id: String,
  val name: String,
  val type: String,
  val primarySlot: EquipmentSlot,
  val occupiesSlots: Set<EquipmentSlot> = setOf(primarySlot),
  val rarity: String? = null,
  val bonuses: EquipmentBonuses = EquipmentBonuses(),
  val weapon: WeaponGameplayStats? = null,
  val abilities: List<EquipmentAbility> = emptyList(),
  val restrictions: List<String> = emptyList(),
  val classification: ItemClassification = ItemClassification.CANONICAL,
  val canonRef: String? = null,
  val components: List<EquipmentComponent> = emptyList()
)

object EquipmentCatalog {
  private fun ability(name: String, description: String, limit: String? = null) = EquipmentAbility(name, description, limit)

  private val all = listOf(
    EquipmentDefinition(
      id = KAI_WHITE_WRAITH_ID, name = "White Wraith Magnum", type = "MAGNUM", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(crit = 8),
      weapon = WeaponGameplayStats(32, "∞", 600, listOf("Single Shot", "Full Auto")),
      abilities = listOf(
        ability("Demonic Ammunition", "Đạn được hình thành trực tiếp từ Sparda Core.", "Không dùng magazine vật lý làm nguồn đạn chính."),
        ability("Single Shot", "Bắn từng viên với nhịp ngắm chính xác."),
        ability("Full Auto", "Cơ cấu có khả năng bắn tự động tới khoảng 600 RPM.", "RPM không đồng nghĩa một Action bắn 600 viên."),
        ability("Core Self-Repair", "Tự sửa chữa cấu trúc bằng quỷ lực từ Sparda Core.", "Armor/weapon repair không hồi HP nhân vật."),
        ability("Guilty Crown Compatibility", "Tương thích Guilty Crown Override.", "Override giữ đúng 24 shots khi đủ điều kiện canon; không phải passive instant-kill.")
      ),
      canonRef = "KAI-AKECHI-CODEX"
    ),
    EquipmentDefinition(
      id = KAI_BLACKBLOOD_ARMOR_ID, name = "Blackblood Armor", type = "ARMOR", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(hp = 25, str = 8, df = 18, agi = 6),
      abilities = listOf(
        ability("Physical Enhancement", "Tăng sức mạnh và tốc độ vận động."),
        ability("Impact Dispersion", "Hấp thụ và phân tán lực va chạm."),
        ability("Stealth Movement", "Giảm tiếng bước chân và hỗ trợ di chuyển kín đáo."),
        ability("Environmental Protection", "Bảo vệ trước độc tố, nhiệt, lạnh và áp suất."),
        ability("Core Self-Repair", "Tự sửa chữa bằng Sparda Core."),
        ability("Battlefield Tracking", "Theo dõi chiến trường, hỗ trợ combat analysis và đồng bộ dữ liệu tác chiến."),
        ability("Omnivault Integration", "Kết nối trực tiếp Omnivault Ring.")
      ),
      restrictions = listOf("Không áp Heavy Armor mobility penalty; canon xác định giáp vận hành như phần mở rộng của cơ thể."),
      canonRef = "KAI-AKECHI-CODEX"
    ),
    EquipmentDefinition(
      id = KAI_DEMON_JAW_MASK_ID, name = "Demon Jaw Mask", type = "HEAD / MASK", primarySlot = EquipmentSlot.HEAD,
      bonuses = EquipmentBonuses(hp = 5, df = 6, crit = 6),
      abilities = listOf(
        ability("Head & Neck Protection", "Bảo vệ đầu, cổ và phần dưới khuôn mặt."),
        ability("Toxin Filtration", "Lọc khí độc."),
        ability("Enhanced Vision", "Enhanced vision, motion tracking và biological analysis."),
        ability("Demonic Identification", "Nhận diện demonic-energy."),
        ability("Encrypted Communication", "Liên lạc mã hóa và combat HUD."),
        ability("Target-Lock Assistance", "Hỗ trợ khóa mục tiêu.", "Chỉ hỗ trợ, không tạo auto-hit.")
      ),
      canonRef = "KAI-AKECHI-CODEX"
    ),
    EquipmentDefinition(
      id = KAI_TALON_GAUNTLETS_ID, name = "Talon Gauntlets", type = "GAUNTLETS", primarySlot = EquipmentSlot.GAUNTLETS,
      bonuses = EquipmentBonuses(hp = 5, str = 12, df = 4),
      abilities = listOf(
        ability("Mechanical Talons", "Triển khai móng vuốt cơ khí và tăng lực đấm."),
        ability("Grip / Hook / Climbing", "Hỗ trợ grip, hook, climbing và di chuyển trên bề mặt."),
        ability("Short-Range EM Field", "Tạo trường điện từ tầm ngắn tác động lên vật thể kim loại."),
        ability("Weapon Control", "Có thể giật, khóa hoặc nghiền vũ khí khi đủ gần.")
      ),
      canonRef = "KAI-AKECHI-CODEX"
    ),
    EquipmentDefinition(
      id = KAI_PHANTOM_GREAVES_ID, name = "Phantom Greaves", type = "GREAVES", primarySlot = EquipmentSlot.GREAVES,
      bonuses = EquipmentBonuses(hp = 5, str = 5, df = 3, agi = 14),
      abilities = listOf(
        ability("Burst Acceleration", "Tăng gia tốc chân và burst movement."),
        ability("Traversal", "High jump, air-direction correction và wall running."),
        ability("Impact Reduction", "Giảm impact khi tiếp đất."),
        ability("Kinetic Kick", "Tăng lực đá và hỗ trợ pursuit trên địa hình phức tạp.")
      ),
      canonRef = "KAI-AKECHI-CODEX"
    ),
    EquipmentDefinition(
      id = KAI_OMNIVAULT_RING_ID, name = "Omnivault Ring", type = "UTILITY EQUIPMENT", primarySlot = EquipmentSlot.RING,
      abilities = listOf(
        ability("Infinite Physical Storage", "Lưu trữ vật vô tri không giới hạn theo canon.", "Không tác động lên sinh vật sống."),
        ability("Scan Template", "Lưu mẫu để sao chép.", "Có đúng 3 template slots."),
        ability("Copy", "Tạo bản sao từ template còn tồn tại.", "Không tự tăng số template slot."),
        ability("Summon", "Triệu hồi vật đã lưu hoặc template hợp lệ."),
        ability("Rapid Re-Equip", "Hỗ trợ khôi phục hoặc thay thế trang bị gần như tức thời khi điều kiện hợp lệ.")
      ),
      restrictions = listOf("Giới hạn Omnivault lấy trực tiếp từ Character Codex; không tác động lên sinh vật sống."),
      canonRef = "KAI-AKECHI-CODEX"
    ),
    EquipmentDefinition(
      id = IRIS_RECON_FRAME_ID, name = "Blackblood Recon Frame R03", type = "RECON ARMOR", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(hp = 20, df = 14, agi = 10, crit = 4),
      abilities = listOf(
        ability("Recon Protection", "Bảo vệ trước va đập, mảnh văng và môi trường ở mức trinh sát chiến đấu."),
        ability("Dual-Gun Stabilization", "Ổn định vai, cẳng tay, cổ tay, tư thế và phân bố lực khi sử dụng song súng."),
        ability("Sensor Suite", "Range sensor, motion sensor và environmental sensor."),
        ability("ARGUS Terrain Read Support", "Hỗ trợ ARGUS Terrain Read từ dữ liệu quan sát/cảm biến hợp lệ."),
        ability("Mobile Firing Support", "Hỗ trợ bắn từ góc khó hoặc trong khi di chuyển.")
      ),
      restrictions = listOf("Không có drone.", "Không có missile, shoulder cannon, launcher hoặc remote camera mesh."),
      canonRef = "IRIS-BELIAL-BLACKBLOOD-CODEX-20260817-R05"
    ),
    EquipmentDefinition(
      id = IRIS_IVORY_EBONY_SET_ID, name = "Ivory & Ebony", type = "DUAL_WEAPON SET", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(agi = 4, crit = 8),
      weapon = WeaponGameplayStats(24, "∞", null, listOf("Independent Dual Fire")),
      abilities = listOf(
        ability("Demonic Ammunition", "Mỗi viên đạn hình thành trực tiếp từ quỷ lực của Iris.", "ENE ∞ không tạo damage, RPM, độ bền hoặc accuracy vô hạn."),
        ability("Independent Dual Fire", "Ivory và Ebony có thể dùng riêng hoặc đồng thời; một khẩu hỏng không tự vô hiệu khẩu còn lại."),
        ability("Twosome Time", "Hai tuyến ngắm độc lập cho phép xử lý hai hướng hoặc hai mục tiêu khi điều kiện cho phép."),
        ability("Rain Storm", "Bắn song súng trong chuyển động trên không.", "Không cho phép bay hoặc lơ lửng."),
        ability("Honeycomb Fire", "Tập trung cả hai khẩu lên cùng vùng/mục tiêu.", "Bị giới hạn bởi cơ cấu súng, tư thế, ổn định, đường bắn và action economy."),
        ability("Charged Shot", "Nén thêm quỷ lực trước khi bắn để tăng uy lực.", "ENE ∞ không tạo Charged Shot DMG ∞; CombatRuntime áp trần gameplay mỗi Action.")
      ),
      components = listOf(EquipmentComponent("Ivory"), EquipmentComponent("Ebony")),
      canonRef = "IRIS-BELIAL-BLACKBLOOD-CODEX-20260817-R05"
    ),
    EquipmentDefinition(
      id = SYVIAL_GODKILLER_ID, name = "GodKiller", type = "MECHANICAL GREATSWORD", primarySlot = EquipmentSlot.WEAPON,
      bonuses = EquipmentBonuses(str = 14, crit = 6), weapon = WeaponGameplayStats(38),
      abilities = listOf(
        ability("Lucifer Core Synchronization", "GodKiller đồng bộ trực tiếp Lucifer Core."),
        ability("Demonic Edge Reinforcement", "Lucifer Demonic Energy gia cường kết cấu và cạnh chém.", "Không có quota energy slash hữu hạn được bịa thêm để cân bằng."),
        ability("Weapon Recall", "Syvial có thể gọi GodKiller trở lại nếu bị đánh văng.", "Mất kiếm tạm thời không khiến Syvial mất cận chiến, Lucifer Gauntlets hoặc Spatial Shift."),
        ability("GodKiller Override Compatibility", "Tương thích Twenty-Four Severance.", "Khi đủ canon: exactly 24 slashes.")
      ),
      restrictions = listOf("GodKiller không phải gunblade, firearm hoặc ranged cannon."),
      canonRef = "SYVIAL-LUCIFER-CODEX-20260816-R03"
    ),
    EquipmentDefinition(
      id = SYVIAL_LUCIFER_ARMOR_ID, name = "Lucifer Armor", type = "ARMOR", primarySlot = EquipmentSlot.ARMOR,
      bonuses = EquipmentBonuses(hp = 30, str = 8, df = 20, agi = 10),
      abilities = listOf(
        ability("Physical Enhancement", "Tăng sức mạnh, burst movement và hỗ trợ phản xạ vận động."),
        ability("GodKiller Stabilization", "Ổn định quỹ đạo GodKiller."),
        ability("Impact Dispersion", "Hấp thụ và phân tán lực va chạm."),
        ability("Environmental Protection", "Bảo vệ nhiệt, lạnh, độc tố và môi trường ô nhiễm."),
        ability("Combat Analysis", "Motion tracking và environmental analysis."),
        ability("Lucifer Synchronization", "Đồng bộ Lucifer Core và GodKiller."),
        ability("Self-Repair", "Tự sửa chữa bằng Lucifer Demonic Energy và hỗ trợ tái sinh cơ thể Syvial.", "Armor repair không đồng nghĩa HP được hồi tức thì."),
        ability("Soul Protection", "Bảo vệ mạnh trước tác động trực tiếp lên linh hồn."),
        ability("Short-Range Spatial Shift", "Greaves hỗ trợ Short-Range Spatial Shift.")
      ),
      restrictions = listOf("Lucifer Armor rất bền nhưng NOT ABSOLUTELY INDESTRUCTIBLE."),
      canonRef = "SYVIAL-LUCIFER-CODEX-20260816-R03"
    ),
    EquipmentDefinition(
      id = LUCIA_M4A1_ID, name = "M4A1 cá nhân hóa", type = "ASSAULT RIFLE", primarySlot = EquipmentSlot.WEAPON,
      weapon = WeaponGameplayStats(26, "60 / 90 reserve", 800, listOf("Semi", "Burst", "Auto")),
      abilities = listOf(
        ability("Green Laser 5mW", "Laser xanh chỉnh điểm danh 5mW hỗ trợ chỉ thị và quét bề mặt ở cự ly gần.", "Không biến laser thành cảm biến siêu nhiên."),
        ability("60-Round Main Magazine", "Băng chính mang 60 viên khi Lucia bắt đầu Level 0."),
        ability("Fire Discipline", "Lucia ưu tiên điểm xạ và tiết kiệm đạn trong môi trường chưa xác định.")
      ),
      restrictions = listOf("Đạn vật lý hữu hạn: 60 viên nạp + 90 viên dự phòng lúc bắt đầu."),
      canonRef = "LUCIA-LUC-FOLLOWER-20260823"
    ),
    EquipmentDefinition(
      id = LUCIA_KNIFE_ID, name = "Dao găm chiến đấu", type = "COMBAT KNIFE", primarySlot = EquipmentSlot.BLADE,
      weapon = WeaponGameplayStats(16),
      canonRef = "LUCIA-LUC-FOLLOWER-20260823"
    ),
    EquipmentDefinition(
      id = LUCIA_WATCH_ID, name = "Đồng hồ định vị quân sự", type = "MILITARY WATCH", primarySlot = EquipmentSlot.WRIST,
      abilities = listOf(
        ability("Local Time Reference", "Giữ mốc thời gian cục bộ để Lucia ghi chép hành trình."),
        ability("Navigation Hardware", "Phần cứng định vị vẫn tồn tại nhưng đã mất tín hiệu vệ tinh trong Backrooms.", "Không cung cấp GPS hoặc la bàn tuyệt đối ở Level 0.")
      ),
      canonRef = "LUCIA-LUC-FOLLOWER-20260823"
    ),
    EquipmentDefinition(
      id = AN_NHIEN_OUTFIT_ID, name = AnNhienCanon.OUTFIT_NAME, type = "OUTFIT", primarySlot = EquipmentSlot.OUTFIT,
      canonRef = "AN-NHIEN-CURRENT"
    ),
    EquipmentDefinition(
      id = AN_NHIEN_FOOTWEAR_ID, name = AnNhienCanon.FOOTWEAR_NAME, type = "FOOTWEAR", primarySlot = EquipmentSlot.FOOTWEAR,
      canonRef = "AN-NHIEN-CURRENT"
    ),
  )

  private val definitions = all.associateBy { it.id }

  fun definition(itemId: String): EquipmentDefinition? = when (itemId) {
    IRIS_IVORY_ID, IRIS_EBONY_ID -> definitions[IRIS_IVORY_EBONY_SET_ID]
    else -> definitions[itemId]
  }

  fun startingLoadout(characterId: String): Map<EquipmentSlot, String> = when (characterId) {
    KAI_ID -> linkedMapOf(
      EquipmentSlot.WEAPON to KAI_WHITE_WRAITH_ID,
      EquipmentSlot.ARMOR to KAI_BLACKBLOOD_ARMOR_ID,
      EquipmentSlot.HEAD to KAI_DEMON_JAW_MASK_ID,
      EquipmentSlot.GAUNTLETS to KAI_TALON_GAUNTLETS_ID,
      EquipmentSlot.GREAVES to KAI_PHANTOM_GREAVES_ID,
      EquipmentSlot.RING to KAI_OMNIVAULT_RING_ID
    )
    IRIS_ID -> linkedMapOf(EquipmentSlot.WEAPON to IRIS_IVORY_EBONY_SET_ID, EquipmentSlot.ARMOR to IRIS_RECON_FRAME_ID)
    SYVIAL_ID -> linkedMapOf(EquipmentSlot.WEAPON to SYVIAL_GODKILLER_ID, EquipmentSlot.ARMOR to SYVIAL_LUCIFER_ARMOR_ID)
    AN_NHIEN_ID -> linkedMapOf(EquipmentSlot.OUTFIT to AN_NHIEN_OUTFIT_ID, EquipmentSlot.FOOTWEAR to AN_NHIEN_FOOTWEAR_ID)
    LUCIA_ID -> linkedMapOf(
      EquipmentSlot.WEAPON to LUCIA_M4A1_ID,
      EquipmentSlot.BLADE to LUCIA_KNIFE_ID,
      EquipmentSlot.WRIST to LUCIA_WATCH_ID
    )
    else -> emptyMap()
  }

  fun stackFor(itemId: String): ItemStack {
    val def = definition(itemId)
    if (def == null) return ItemStack(itemId, itemId, metadata = mapOf("category" to "equipment"))
    val metadata = linkedMapOf(
      "category" to "equipment",
      "equipmentDefinitionId" to def.id,
      "slot" to def.primarySlot.key,
      "classification" to def.classification.name,
      "statItem" to (def.bonuses.any() || def.weapon != null).toString()
    )
    def.rarity?.let { metadata["rarity"] = it }
    return ItemStack(def.id, def.name, 1, "READY", metadata)
  }

  fun mergeDefinitionMetadata(stack: ItemStack): ItemStack {
    val def = definition(stack.itemId) ?: return stack
    val canonical = stackFor(def.id)
    return stack.copy(name = def.name, metadata = canonical.metadata + stack.metadata, archetypeId = def.id)
  }
}


object InventoryCapacityPolicy {
  fun maxSlots(state: GameState, characterId: String): Int = InventoryPolicy.profileFor(state, characterId).maxTypes

  fun equippedItemIds(state: GameState, characterId: String): Set<String> =
    state.equipment[characterId]?.slots.orEmpty().values.filter { it.isNotBlank() }.toSet()

  fun carriedItemIds(state: GameState, characterId: String): Set<String> =
    carriedItemIds(state, characterId, state.inventories[characterId] ?: InventoryState(characterId))

  fun carriedItemIds(state: GameState, characterId: String, inventory: InventoryState): Set<String> {
    val owned = inventory.items.filterValues { it.quantity > 0 }.keys
    return owned - equippedItemIds(state, characterId)
  }

  fun usedSlots(state: GameState, characterId: String): Int = carriedItemIds(state, characterId).size
  fun usedSlots(state: GameState, characterId: String, inventory: InventoryState): Int = carriedItemIds(state, characterId, inventory).size

  fun consumesSlot(state: GameState, characterId: String, itemId: String): Boolean =
    itemId in carriedItemIds(state, characterId)
}

object CharacterStatEngine {
  private fun regenAmount(rule: HpRegenRule, maxHp: Int): Int {
    val percentAmount = if (rule.percentOfMaxHp > 0) {
      ((maxHp.toLong() * rule.percentOfMaxHp.coerceIn(0, 100) + 99L) / 100L).toInt()
    } else 0
    return (rule.amountPerCompletedTurn.toLong() + percentAmount.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
  }

  fun effective(state: GameState, characterId: String): EffectiveCharacterStats {
    val character = state.characters[characterId] ?: return fallback(characterId)
    val definitions = state.equipment[character.equipmentId]?.slots.orEmpty().values
      .mapNotNull(EquipmentCatalog::definition).distinctBy { it.id }
    val hp = definitions.sumOf { it.bonuses.hp }
    val str = definitions.sumOf { it.bonuses.str }
    val df = definitions.sumOf { it.bonuses.df }
    val agi = definitions.sumOf { it.bonuses.agi }
    val crit = definitions.sumOf { it.bonuses.crit }
    return EffectiveCharacterStats(
      maxHp = (character.statProfile.baseMaxHp + hp).coerceAtLeast(1),
      equipmentHp = hp,
      str = character.statProfile.str + str,
      df = character.statProfile.df + df,
      agi = character.statProfile.agi + agi,
      crit = character.statProfile.crit + crit,
      energy = character.statProfile.energy,
      regenPerCompletedTurn = if (character.statProfile.regen.enabled) regenAmount(character.statProfile.regen, (character.statProfile.baseMaxHp + hp).coerceAtLeast(1)) else 0
    )
  }

  private fun fallback(characterId: String): EffectiveCharacterStats {
    val base = CharacterStatProfiles.forId(characterId)
    return EffectiveCharacterStats(base.baseMaxHp, 0, base.str, base.df, base.agi, base.crit, base.energy, if (base.regen.enabled) regenAmount(base.regen, base.baseMaxHp) else 0)
  }

  fun conditionFor(currentHp: Int, maxHp: Int, old: CharacterCondition? = null, presence: CharacterPresence? = null): CharacterCondition {
    if (presence == CharacterPresence.DEAD || old == CharacterCondition.DEAD) return CharacterCondition.DEAD
    if (currentHp <= 0) return CharacterCondition.DEFEATED
    val ratio = currentHp.toDouble() / maxHp.coerceAtLeast(1).toDouble()
    return when {
      ratio > .75 -> CharacterCondition.HEALTHY
      ratio > .50 -> CharacterCondition.HURT
      ratio > .25 -> CharacterCondition.WOUNDED
      else -> CharacterCondition.CRITICAL
    }
  }

  fun setCurrentHp(state: GameState, characterId: String, hp: Int): GameState {
    val character = state.characters[characterId] ?: return state
    val maxHp = effective(state, characterId).maxHp
    val nextHp = hp.coerceIn(0, maxHp)
    val vital = character.vitalState.copy(
      currentHp = nextHp,
      condition = conditionFor(nextHp, maxHp, character.vitalState.condition, character.presence)
    )
    return state.copy(characters = state.characters + (characterId to character.copy(vitalState = vital)))
  }

  fun preserveMissingHp(before: GameState, afterEquipment: GameState, characterId: String): GameState {
    val character = before.characters[characterId] ?: return afterEquipment
    val oldMax = effective(before, characterId).maxHp
    val newMax = effective(afterEquipment, characterId).maxHp
    val oldHp = character.vitalState.currentHp.coerceIn(0, oldMax)
    val newHp = if (oldHp <= 0) 0 else (newMax - (oldMax - oldHp)).coerceAtLeast(0)
    return setCurrentHp(afterEquipment, characterId, newHp)
  }

  fun applyCompletedTurnRegen(state: GameState, completedTurnId: String): GameState {
    var next = state
    state.characters.keys.sorted().forEach { id ->
      val character = next.characters[id] ?: return@forEach
      val effective = effective(next, id)
      val hp = character.vitalState.currentHp.coerceIn(0, effective.maxHp)
      val normalizedCondition = conditionFor(hp, effective.maxHp, character.vitalState.condition, character.presence)
      if (hp <= 0 || normalizedCondition == CharacterCondition.DEFEATED || normalizedCondition == CharacterCondition.DEAD) {
        val vital = character.vitalState.copy(currentHp = hp, condition = normalizedCondition)
        next = next.copy(characters = next.characters + (id to character.copy(vitalState = vital)))
        return@forEach
      }
      val rule = character.statProfile.regen
      val healAmount = regenAmount(rule, effective.maxHp)
      if (!rule.enabled || healAmount <= 0 || character.vitalState.lastRegenCompletedTurnId == completedTurnId) return@forEach
      val interval = rule.intervalCompletedTurns.coerceAtLeast(1)
      val progress = (character.vitalState.completedTurnsTowardRegen + 1).coerceAtMost(interval)
      val due = progress >= interval
      val healed = if (due) (hp.toLong() + healAmount.toLong()).coerceAtMost(effective.maxHp.toLong()).toInt() else hp
      val vital = character.vitalState.copy(
        currentHp = healed,
        condition = conditionFor(healed, effective.maxHp, character.vitalState.condition, character.presence),
        lastRegenCompletedTurnId = completedTurnId,
        completedTurnsTowardRegen = if (due) 0 else progress
      )
      next = next.copy(characters = next.characters + (id to character.copy(vitalState = vital)))
    }
    return next
  }

  fun weaponDamage(state: GameState, characterId: String): Int {
    val weaponId = state.equipment[characterId]?.slots?.get(EquipmentSlot.WEAPON.key) ?: return 18
    return EquipmentCatalog.definition(weaponId)?.weapon?.dmg ?: 18
  }
}

object CombatStatMath {
  fun critChancePercent(rating: Int): Int = (5 + rating.coerceAtLeast(0) / 12).coerceIn(5, 25)
  fun defenseReduction(dfRating: Int): Int = (dfRating.coerceAtLeast(0) / 18).coerceIn(0, 12)
  fun agilityDefense(agiRating: Int): Int = ((agiRating - 40).coerceAtLeast(0) / 18).coerceIn(0, 5)
}

object EquipmentEngine {
  fun isEquipped(state: GameState, characterId: String, itemId: String): Boolean =
    state.equipment[characterId]?.slots.orEmpty().values.any { it == itemId }

  fun equip(state: GameState, command: ItemCommand): ExecutionResult {
    if (command.actorId == AN_NHIEN_ID) return invalid(state, "an_nhien_equipment_locked")
    val inventory = state.inventories[command.actorId] ?: return invalid(state, "item_not_owned")
    val owned = inventory.items[command.itemId] ?: return invalid(state, "item_not_owned")
    if (owned.quantity < 1) return invalid(state, "item_not_owned")
    val def = EquipmentCatalog.definition(command.itemId)
    val requested = EquipmentSlot.fromRaw(command.slot)
    val targetSlots = if (def != null) def.occupiesSlots.map { it.key }.toSet() else setOfNotNull(requested?.key ?: command.slot?.trim()?.lowercase())
    if (targetSlots.isEmpty()) return invalid(state, "equipment_slot_required")
    if (def != null && requested != null && requested !in def.occupiesSlots && requested != def.primarySlot) return invalid(state, "equipment_slot_mismatch")

    val equipment = state.equipment[command.actorId] ?: EquipmentState(command.actorId)

    val nextSlots = equipment.slots.toMutableMap()
    targetSlots.forEach { nextSlots[it] = command.itemId }
    val raw = state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = nextSlots)))
    val adjusted = CharacterStatEngine.preserveMissingHp(state, raw, command.actorId)
    return changed(adjusted, "item_equipped")
  }

  fun unequip(state: GameState, command: ItemCommand): ExecutionResult {
    if (command.actorId == AN_NHIEN_ID) return invalid(state, "an_nhien_equipment_locked")
    val equipment = state.equipment[command.actorId] ?: return invalid(state, "equipment_missing")
    if (command.itemId !in equipment.slots.values) return invalid(state, "item_not_equipped")
    val nextSlots = equipment.slots.filterValues { it != command.itemId }
    val raw = state.copy(equipment = state.equipment + (command.actorId to equipment.copy(slots = nextSlots)))
    val adjusted = CharacterStatEngine.preserveMissingHp(state, raw, command.actorId)
    return changed(adjusted, "item_unequipped")
  }

  fun preview(state: GameState, characterId: String, itemId: String): EffectiveCharacterStats? {
    val item = state.inventories[characterId]?.items?.get(itemId) ?: return null
    val def = EquipmentCatalog.definition(item.itemId) ?: return null
    val equipment = state.equipment[characterId] ?: EquipmentState(characterId)
    if (itemId in equipment.slots.values) return CharacterStatEngine.effective(state, characterId)
    val next = equipment.slots.toMutableMap()
    def.occupiesSlots.forEach { next[it.key] = itemId }
    return CharacterStatEngine.effective(state.copy(equipment = state.equipment + (characterId to equipment.copy(slots = next))), characterId)
  }
}

object CharacterEquipmentSystem {
  private const val SCHEMA_VERSION = "1"

  fun seedFresh(state: GameState): GameState = normalizeInternal(state, true, fillStartingHp = true)

  fun normalize(state: GameState): GameState = normalizeInternal(state, state.metadata["characterEquipmentSchemaVersion"] != SCHEMA_VERSION, fillStartingHp = false)

  private fun normalizeInternal(source: GameState, seedStarting: Boolean, fillStartingHp: Boolean): GameState {
    val input = LuciaCanon.ensure(source)
    val inventories = input.inventories.toMutableMap()
    val equipment = input.equipment.toMutableMap()

    input.characters.keys.forEach { characterId ->
      var inv = inventories[characterId] ?: InventoryState(characterId)
      var eq = equipment[characterId] ?: EquipmentState(characterId)
      val slots = eq.slots.toMutableMap()

      // Collapse the historical two-slot Ivory/Ebony representation into one unique dual-weapon Item.
      if (characterId == IRIS_ID && slots.values.any { it == IRIS_IVORY_ID || it == IRIS_EBONY_ID }) {
        slots.remove("weapon_primary"); slots.remove("weapon_secondary")
        slots[EquipmentSlot.WEAPON.key] = IRIS_IVORY_EBONY_SET_ID
      }

      val loadout = EquipmentCatalog.startingLoadout(characterId)
      if (seedStarting) {
        loadout.forEach { (slot, itemId) ->
          if (itemId !in inv.items) inv = inv.copy(items = inv.items + (itemId to EquipmentCatalog.stackFor(itemId)))
        }
      }

      // Equipment references ownership. Never create a second Equipment-side Item object.
      slots.values.distinct().forEach { itemId ->
        val def = EquipmentCatalog.definition(itemId)
        val normalizedId = def?.id ?: itemId
        if (normalizedId !in inv.items) inv = inv.copy(items = inv.items + (normalizedId to EquipmentCatalog.stackFor(normalizedId)))
      }

      // Merge immutable definition metadata into the one inventory instance.
      val merged = inv.items.mapValues { (_, stack) -> EquipmentCatalog.mergeDefinitionMetadata(stack) }
      inv = inv.copy(items = merged)
      inventories[characterId] = inv
      equipment[characterId] = eq.copy(slots = slots)
    }

    var next = input.copy(
      inventories = inventories,
      equipment = equipment,
      metadata = input.metadata + ("characterEquipmentSchemaVersion" to SCHEMA_VERSION)
    )

    val characters = next.characters.toMutableMap()
    next.characters.forEach { (id, character) ->
      val effective = CharacterStatEngine.effective(next, id)
      val rawHp = character.vitalState.currentHp.coerceIn(0, effective.maxHp)
      val hp = if (
        fillStartingHp &&
        rawHp == character.statProfile.baseMaxHp &&
        character.vitalState.condition == CharacterCondition.HEALTHY &&
        character.vitalState.lastRegenCompletedTurnId == null
      ) effective.maxHp else rawHp
      characters[id] = character.copy(
        statProfile = character.statProfile.copy(
          regen = if (id == KAI_ID) CharacterStatProfiles.forId(KAI_ID).regen else character.statProfile.regen
        ),
        vitalState = character.vitalState.copy(
          currentHp = hp,
          condition = CharacterStatEngine.conditionFor(hp, effective.maxHp, character.vitalState.condition, character.presence)
        ),
        metadata = character.metadata + mapOf(
          "derived.equipmentHp" to effective.equipmentHp.toString(),
          "derived.effectiveMaxHp" to effective.maxHp.toString()
        )
      )
    }
    next = next.copy(characters = characters)
    return next
  }
}
