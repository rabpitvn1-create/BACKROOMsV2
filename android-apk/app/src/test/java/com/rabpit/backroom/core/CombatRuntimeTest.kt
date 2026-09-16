package com.rabpit.backroom.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatRuntimeTest {
  @Test fun entityTriggerStartsOneAuthoritativeEncounterWithHealth() {
    val started = CombatRuntime.start(GameState.initial(), "hound")
    val combat = CombatRuntime.active(started)
    assertNotNull(combat)
    assertEquals("hound", combat!!.entityKey)
    assertEquals(110, combat.entityMaxHp)
    assertEquals(110, combat.entityHp)
    val expectedMaxHp = CharacterStatEngine.effective(GameState.initial(), KAI_ID).maxHp
    assertEquals(140, expectedMaxHp)
    assertEquals(expectedMaxHp, combat.playerMaxHp)
    assertEquals(expectedMaxHp, combat.playerHp)

    val duplicate = CombatRuntime.start(started, "smiler")
    assertEquals("hound", CombatRuntime.active(duplicate)!!.entityKey)
  }

  @Test fun repeatedAuthoritativeAttacksEventuallyDestroyAndClearEntity() {
    var state = CombatRuntime.start(GameState.initial(), "hound")
    var destroyed = false
    repeat(24) {
      if (destroyed) return@repeat
      val result = CombatRuntime.resolve(state, "EXECUTE", "bắn Hound bằng Magnum")
      assertTrue(result.handled)
      state = result.state
      destroyed = result.entityDestroyed
    }
    assertTrue("Entity must be destroyable by authoritative combat resolution", destroyed)
    assertNull(CombatRuntime.active(state))
  }

  @Test fun combatExploreIsMovementNotAnotherEncounter() {
    val started = CombatRuntime.start(GameState.initial(), "skin-stealer")
    val before = CombatRuntime.active(started)!!
    val result = CombatRuntime.resolve(started, "EXPLORE", "lùi lại tìm vật che chắn")
    assertTrue(result.handled)
    val after = CombatRuntime.active(result.state)
    if (after != null) {
      assertEquals("skin-stealer", after.entityKey)
      assertTrue(after.escapeProgress >= before.escapeProgress)
      assertTrue(after.range.ordinal >= before.range.ordinal)
    }
  }

  @Test fun escapeResolutionClearsEncounterWithoutDestroyingRequirement() {
    val started = CombatRuntime.start(GameState.initial(), "smiler")
    val state = started.copy(metadata = started.metadata + ("combat.escapeProgress" to "95"))
    val result = CombatRuntime.resolve(state, "EXECUTE", "chạy thoát khỏi encounter")
    assertTrue(result.handled)
    assertTrue(result.escaped)
    assertFalse(result.entityDestroyed)
    assertNull(CombatRuntime.active(result.state))
  }

  @Test fun readActionRevealsTelegraphAndBuildsOpeningWhenEncounterSurvives() {
    val state = CombatRuntime.start(GameState.initial(), "clump")
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát kỹ chuyển động của nó")
    assertTrue(result.handled)
    val after = CombatRuntime.active(result.state)
    assertNotNull(after)
    assertTrue(after!!.opening >= 1)
    assertTrue(after.momentum >= 0)
    assertFalse(after.telegraph.isBlank())
  }

  @Test fun survivingEntityRegeneratesOneHpPerCombatTurnUpToMax() {
    var state = CombatRuntime.start(GameState.initial(), "slenderman")
    val full = CombatRuntime.active(state)!!
    state = state.copy(metadata = state.metadata + ("combat.entityHp" to (full.entityMaxHp - 5).toString()))
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát chuyển động")
    val after = CombatRuntime.active(result.state)!!
    assertTrue(result.reply.contains("hồi +1 HP"))
    assertTrue(after.entityHp <= after.entityMaxHp)
  }

  @Test fun allEntityProfilesReceiveThirtyBonusHp() {
    val expected = mapOf(
      "hound" to 110, "clump" to 135, "duller" to 120, "deathmoth" to 95,
      "hostile_faceling" to 105, "false_puddle" to 125, "paintings" to 100,
      "smiler" to 115, "skin-stealer" to 130, "predatory_window" to 145,
      "biological_pipeline" to 150, "wretch" to 115, "cable_mimic" to 130,
      "the_beast_of_level_5" to 175, "hotel_corpse_lure" to 140,
      "jeff_the_killer" to 150, "jane_the_killer" to 150, "slenderman" to 190
    )
    expected.forEach { (key, hp) ->
      assertEquals("+30 HP must apply to $key", hp, CombatRuntime.active(CombatRuntime.start(GameState.initial(), key))!!.entityMaxHp)
    }
  }

  @Test fun guiltyCrownOverrideTriggersAutomaticallyOnEveryThirdCombatTurn() {
    var state = CombatRuntime.start(GameState.initial(), "slenderman")

    repeat(2) { index ->
      val result = CombatRuntime.resolve(state, "SEARCH", "quan sát nhịp di chuyển")
      assertTrue(result.handled)
      assertFalse("Override must not fire before combat turn 3", result.reply.contains("Guilty Crown Override"))
      state = result.state
      val active = CombatRuntime.active(state)
      assertNotNull(active)
      assertEquals(index + 1, active!!.eventCounter)
    }

    val third = CombatRuntime.resolve(state, "SEARCH", "tiếp tục quan sát mục tiêu")
    assertTrue(third.handled)
    assertTrue(third.entityDestroyed)
    assertNull(CombatRuntime.active(third.state))
    assertTrue(third.reply.contains("Guilty Crown Override"))
    assertTrue(third.reply.contains("24/24 phát trúng liên tiếp"))
    assertTrue(third.reply.contains("Accuracy 200%"))
    assertTrue(third.reply.contains("bỏ qua toàn bộ hiệu ứng né"))
    assertTrue(third.reply.contains("mỗi phát -10 HP"))
    assertTrue(third.reply.contains("tổng -240 HP"))
  }

  @Test fun guiltyCrownOverrideAppliesExactTwentyFourTimesTenHpBeforeNormalRegen() {
    var state = CombatRuntime.start(GameState.initial(), "hound")
    state = state.copy(metadata = state.metadata + mapOf(
      "combat.entityHp" to "500",
      "combat.entityMaxHp" to "500",
      "combat.eventCounter" to "2"
    ))

    val third = CombatRuntime.resolve(state, "SEARCH", "giữ mục tiêu trong tầm quan sát")
    assertFalse(third.entityDestroyed)
    val after = CombatRuntime.active(third.state)!!
    // 500 - (24 * 10) + the existing surviving-Entity 1 HP end-of-turn regeneration.
    assertEquals(261, after.entityHp)
    assertTrue(third.reply.contains("tổng -240 HP"))
    assertTrue(third.reply.contains("Accuracy 200%"))
    assertTrue(third.reply.contains("bỏ qua toàn bộ hiệu ứng né"))
  }

  @Test fun diepMinhHasExact2999HpAndRegeneratesThirtyPerSurvivingTurn() {
    var state = CombatRuntime.start(GameState.initial(), "diep_minh")
    val started = CombatRuntime.active(state)!!
    assertEquals(2999, started.entityMaxHp)
    assertEquals(2999, started.entityHp)

    state = state.copy(metadata = state.metadata + ("combat.entityHp" to "2900"))
    val result = CombatRuntime.resolve(state, "SEARCH", "quan sát Diệp Minh")
    val after = CombatRuntime.active(result.state)!!
    assertEquals(2930, after.entityHp)
    assertTrue(result.reply.contains("hồi +30 HP"))
  }

  @Test fun diepMinhDevilsAndGoldHitsEveryActivePartyMemberForFivePercentMaxHpOnTurnFive() {
    val initial = GameState.initial()
    val iris = CharacterState(
      id = "iris",
      name = "Iris",
      statProfile = CharacterStatProfiles.forId("iris"),
      vitalState = CharacterStatProfiles.initialVitals("iris")
    )
    var state = initial.copy(
      characters = initial.characters + ("iris" to iris),
      party = PartyState(memberIds = listOf(KAI_ID, "iris"))
    )
    state = CombatRuntime.start(state, "diep_minh")
    state = state.copy(metadata = state.metadata + ("combat.eventCounter" to "4"))

    val kaiBefore = state.characters.getValue(KAI_ID).vitalState.currentHp
    val irisBefore = state.characters.getValue("iris").vitalState.currentHp
    val kaiMax = CharacterStatEngine.effective(state, KAI_ID).maxHp
    val irisMax = CharacterStatEngine.effective(state, "iris").maxHp
    val result = CombatRuntime.resolve(state, "SEARCH", "giữ đội hình")

    assertTrue(result.reply.contains("Devils And Gold"))
    assertEquals(kaiBefore - maxOf(1, (kaiMax * 5 + 99) / 100), result.state.characters.getValue(KAI_ID).vitalState.currentHp)
    assertEquals(irisBefore - maxOf(1, (irisMax * 5 + 99) / 100), result.state.characters.getValue("iris").vitalState.currentHp)
  }

  @Test fun kaiAutomaticGunSkillsExposeAllFourIndependentProcContracts() {
    val seen = mutableSetOf<String>()
    for (counter in 0..240) {
      if (seen.size == 4) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "giữ mục tiêu trong tầm quan sát")
      if (result.reply.contains("The Last Requiem tự động kích hoạt")) seen += "requiem"
      if (result.reply.contains("Silent Lullaby tự động kích hoạt")) seen += "lullaby"
      if (result.reply.contains("Salvation tự động kích hoạt")) seen += "salvation"
      if (result.reply.contains("Quick Step tự động kích hoạt")) seen += "quick_step"
    }
    assertEquals(setOf("requiem", "lullaby", "salvation", "quick_step"), seen)
  }

  @Test fun lastRequiemBleedingPersistsAndTicksFivePercentMaxHp() {
    var verified = false
    for (counter in 0..240) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + mapOf(
        "combat.eventCounter" to counter.toString(),
        "combat.entityHp" to "2000",
        "combat.kaiBleedTurns" to "3"
      ))
      val result = CombatRuntime.resolve(state, "SEARCH", "theo dõi mục tiêu")
      if (result.reply.contains("The Last Requiem tự động kích hoạt")) continue
      val after = CombatRuntime.active(result.state) ?: continue
      assertTrue(result.reply.contains("Bleeding từ The Last Requiem gây -150 HP"))
      assertEquals("2", result.state.metadata["combat.kaiBleedTurns"])
      assertTrue(after.entityHp <= 1880)
      verified = true
    }
    assertTrue("Expected a deterministic turn without Last Requiem refresh", verified)
  }

  @Test fun silentLullabyStunSuppressesCurrentEnemyResponse() {
    var verified = false
    for (counter in 0..240) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "theo dõi nhịp phản công")
      if (!result.reply.contains("Silent Lullaby tự động kích hoạt")) continue
      assertTrue(result.reply.contains("bị Stun và mất lượt phản ứng hiện tại"))
      assertFalse(result.reply.contains("Diệp Minh phản công:"))
      assertFalse(result.reply.contains("Devils And Gold kích hoạt"))
      verified = true
    }
    assertTrue("Expected a deterministic Silent Lullaby proc", verified)
  }

  @Test fun quickStepGrantsFiftyEvasionForThreeTurnsAndCountsDown() {
    var verified = false
    for (counter in 0..240) {
      if (verified) break
      var state = CombatRuntime.start(GameState.initial(), "diep_minh")
      state = state.copy(metadata = state.metadata + ("combat.eventCounter" to counter.toString()))
      val result = CombatRuntime.resolve(state, "SEARCH", "đổi góc quan sát")
      if (!result.reply.contains("Quick Step tự động kích hoạt")) continue
      assertTrue(result.reply.contains("+50% Evasion trong 3 turn"))
      assertEquals("2", result.state.metadata["combat.kaiQuickStepTurns"])
      verified = true
    }
    assertTrue("Expected a deterministic Quick Step proc", verified)
  }

  @Test fun guiltyCrownTurnKeepsPriorityOverAutomaticGunSkillRolls() {
    var state = CombatRuntime.start(GameState.initial(), "diep_minh")
    state = state.copy(metadata = state.metadata + ("combat.eventCounter" to "2"))
    val result = CombatRuntime.resolve(state, "SEARCH", "giữ mục tiêu trong tầm quan sát")
    assertTrue(result.reply.contains("Guilty Crown Override"))
    assertFalse(result.reply.contains("The Last Requiem tự động kích hoạt"))
    assertFalse(result.reply.contains("Silent Lullaby tự động kích hoạt"))
    assertFalse(result.reply.contains("Salvation tự động kích hoạt"))
    assertFalse(result.reply.contains("Quick Step tự động kích hoạt"))
  }

  @Test fun luciaGetsAnIndependentCombatResolutionWhenBothAttack() {
    val initial = LuciaCanon.ensure(GameState.initial())
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    state = state.copy(metadata = state.metadata + ("combat.eventCounter" to "0"))

    val result = CombatRuntime.resolve(state, "EXECUTE", "Cả 2 cùng tấn công")
    assertTrue(result.handled)
    assertTrue(result.reply.contains("Lucia \"Lục\""))
    assertTrue(
      result.reply.contains("bắn hỗ trợ bằng M4A1") ||
        result.reply.contains("cũng khai hỏa nhưng phát bắn không trúng mục tiêu")
    )
  }

  @Test fun luciaAutoAttacksOnAPlainAttackRound() {
    val initial = LuciaCanon.ensure(GameState.initial())
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "diep_minh")

    val result = CombatRuntime.resolve(state, "AUTO_COMBAT", "Tự động tấn công")
    assertTrue(result.handled)
    assertTrue(
      result.reply.contains("Lucia \"Lục\" bắn hỗ trợ") ||
        result.reply.contains("Lucia \"Lục\" cũng khai hỏa")
    )
    assertEquals(1, CombatRuntime.active(result.state)!!.eventCounter)
  }

  @Test fun trueTurnAutoCombatCommitsKaiEntityLuciaEntityAsIndependentSubturns() {
    val initial = LuciaCanon.ensure(GameState.initial())
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    assertEquals("kai", CombatRuntime.toJson(state)!!.getString("activeActorId"))
    assertEquals("file:///android_asset/kai_entity_overlay.png", CombatRuntime.toJson(state)!!.getString("activeActorOverlayUri"))

    val kai = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
    assertTrue(kai.handled)
    assertFalse(kai.roundCompleted)
    assertFalse(kai.reply.contains("Lucia \"Lục\""))
    assertFalse(kai.reply.contains("phản công: Kai"))
    assertEquals(1, CombatRuntime.active(kai.state)!!.eventCounter)
    assertEquals("entity:kai", CombatRuntime.toJson(kai.state)!!.getString("activeActorId"))
    assertEquals("file:///android_asset/kai_entity_overlay.png", CombatRuntime.toJson(kai.state)!!.getString("activeActorOverlayUri"))

    val entityKai = CombatRuntime.resolve(kai.state, "AUTO_COMBAT_STEP", "auto")
    assertTrue(entityKai.handled)
    assertFalse(entityKai.roundCompleted)
    assertEquals(1, CombatRuntime.active(entityKai.state)!!.eventCounter)
    assertEquals("lucia", CombatRuntime.toJson(entityKai.state)!!.getString("activeActorId"))
    assertEquals("file:///android_asset/lucia_entity_overlay.png", CombatRuntime.toJson(entityKai.state)!!.getString("activeActorOverlayUri"))

    val lucia = CombatRuntime.resolve(entityKai.state, "AUTO_COMBAT_STEP", "auto")
    assertTrue(lucia.handled)
    assertFalse(lucia.roundCompleted)
    assertTrue(lucia.reply.contains("Lucia \"Lục\""))
    assertEquals(1, CombatRuntime.active(lucia.state)!!.eventCounter)
    assertEquals("entity:lucia", CombatRuntime.toJson(lucia.state)!!.getString("activeActorId"))
    assertEquals("file:///android_asset/lucia_entity_overlay.png", CombatRuntime.toJson(lucia.state)!!.getString("activeActorOverlayUri"))

    val entityLucia = CombatRuntime.resolve(lucia.state, "AUTO_COMBAT_STEP", "auto")
    assertTrue(entityLucia.handled)
    assertTrue(entityLucia.roundCompleted)
    assertEquals(1, CombatRuntime.active(entityLucia.state)!!.eventCounter)
    assertEquals("kai", CombatRuntime.toJson(entityLucia.state)!!.getString("activeActorId"))
  }

  @Test fun trueTurnAutoCombatExposesSyvialOverlayAndIndependentSubturns() {
    val initial = SpecialFollowersCanon.ensure(GameState.initial())
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, SYVIAL_ID)))
    state = CombatRuntime.start(state, "diep_minh")

    val kai = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
    val entityKai = CombatRuntime.resolve(kai.state, "AUTO_COMBAT_STEP", "auto")
    val syvialJson = CombatRuntime.toJson(entityKai.state)!!
    assertEquals("syvial", syvialJson.getString("activeActorId"))
    assertEquals("file:///android_asset/syvial_entity_overlay.png", syvialJson.getString("activeActorOverlayUri"))

    val syvial = CombatRuntime.resolve(entityKai.state, "AUTO_COMBAT_STEP", "auto")
    assertFalse(syvial.roundCompleted)
    assertTrue(syvial.reply.contains("Syvial"))
    assertEquals("entity:syvial", CombatRuntime.toJson(syvial.state)!!.getString("activeActorId"))

    val entitySyvial = CombatRuntime.resolve(syvial.state, "AUTO_COMBAT_STEP", "auto")
    assertTrue(entitySyvial.roundCompleted)
    assertEquals("kai", CombatRuntime.toJson(entitySyvial.state)!!.getString("activeActorId"))
  }

  @Test fun trueTurnAutoCombatWithoutLuciaCompletesAfterEntityTargetsKai() {
    var state = CombatRuntime.start(GameState.initial(), "diep_minh")
    val kai = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
    assertFalse(kai.roundCompleted)
    assertEquals("entity:kai", CombatRuntime.toJson(kai.state)!!.getString("activeActorId"))

    val entityKai = CombatRuntime.resolve(kai.state, "AUTO_COMBAT_STEP", "auto")
    assertTrue(entityKai.roundCompleted)
    assertEquals(1, CombatRuntime.active(entityKai.state)!!.eventCounter)
    assertEquals("kai", CombatRuntime.toJson(entityKai.state)!!.getString("activeActorId"))
  }

  @Test fun trueTurnAutoCombatUsesLivePartySlotOrderForEveryAttackResponsePair() {
    val withLucia = LuciaCanon.ensure(GameState.initial())
    val initial = SpecialFollowersCanon.ensure(withLucia)
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, SYVIAL_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    val order = CombatRuntime.toJson(state)!!.getJSONArray("autoOrder")
    assertEquals(
      listOf("kai", "entity:kai", "syvial", "entity:syvial", "lucia", "entity:lucia"),
      (0 until order.length()).map { order.getString(it) }
    )

    val kai = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
    val entityKai = CombatRuntime.resolve(kai.state, "AUTO_COMBAT_STEP", "auto")
    assertEquals("syvial", CombatRuntime.toJson(entityKai.state)!!.getString("activeActorId"))
    val syvial = CombatRuntime.resolve(entityKai.state, "AUTO_COMBAT_STEP", "auto")
    assertEquals("entity:syvial", CombatRuntime.toJson(syvial.state)!!.getString("activeActorId"))
    val entitySyvial = CombatRuntime.resolve(syvial.state, "AUTO_COMBAT_STEP", "auto")
    assertFalse(entitySyvial.roundCompleted)
    assertEquals("lucia", CombatRuntime.toJson(entitySyvial.state)!!.getString("activeActorId"))
  }

  @Test fun trueTurnAutoCursorSurvivesCodecSaveLoadBetweenActors() {
    val initial = LuciaCanon.ensure(GameState.initial())
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    val kai = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
    val loaded = GameStateCodec.decode(GameStateCodec.encode(kai.state))
    assertEquals("entity:kai", CombatRuntime.toJson(loaded)!!.getString("activeActorId"))
    assertEquals(1, CombatRuntime.active(loaded)!!.eventCounter)
  }

  @Test fun trueTurnAutoCombatBuildsFourMemberOrderAndUsesRealPartySlots() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial()))
    var state = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID)))
    state = CombatRuntime.start(state, "diep_minh")
    val expected = listOf("kai", "entity:kai", "lucia", "entity:lucia", "syvial", "entity:syvial", "iris", "entity:iris")
    val order = CombatRuntime.toJson(state)!!.getJSONArray("autoOrder")
    assertEquals(expected, (0 until order.length()).map { order.getString(it) })

    expected.forEachIndexed { cursor, actorId ->
      val projected = state.copy(metadata = state.metadata + ("combat.autoCursor" to cursor.toString()))
      val json = CombatRuntime.toJson(projected)!!
      assertEquals(actorId, json.getString("activeActorId"))
      assertEquals(cursor / 2, json.getInt("activeActorSlot"))
      assertEquals(if (actorId.startsWith("entity:")) actorId.removePrefix("entity:") else "diep_minh", json.getString("activeActorTargetId"))
    }
    assertEquals("", CombatRuntime.toJson(state.copy(metadata = state.metadata + ("combat.autoCursor" to "6")))!!.getString("activeActorOverlayUri"))
  }

  @Test fun trueTurnAutoCombatPreservesReorderedLivePartyOrder() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial()))
    val reordered = initial.copy(party = PartyState(memberIds = listOf(KAI_ID, SYVIAL_ID, LUCIA_ID, IRIS_ID)))
    val state = CombatRuntime.start(reordered, "diep_minh")
    val order = CombatRuntime.toJson(state)!!.getJSONArray("autoOrder")
    assertEquals(
      listOf("kai", "entity:kai", "syvial", "entity:syvial", "lucia", "entity:lucia", "iris", "entity:iris"),
      (0 until order.length()).map { order.getString(it) }
    )
  }

  @Test fun trueTurnAutoCombatSkipsDeadSeparatedAndMissingPartyMembers() {
    val ensured = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial()))
    val deadIris = ensured.characters.getValue(IRIS_ID).copy(
      vitalState = ensured.characters.getValue(IRIS_ID).vitalState.copy(currentHp = 0)
    )
    val separatedLucia = ensured.characters.getValue(LUCIA_ID).copy(presence = CharacterPresence.SEPARATED)
    val filtered = ensured.copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID)),
      characters = ensured.characters + (IRIS_ID to deadIris) + (LUCIA_ID to separatedLucia)
    )
    val state = CombatRuntime.start(filtered, "diep_minh")
    val order = CombatRuntime.toJson(state)!!.getJSONArray("autoOrder")
    assertEquals(listOf("kai", "entity:kai", "syvial", "entity:syvial"), (0 until order.length()).map { order.getString(it) })

    val missingState = CombatRuntime.start(
      ensured.copy(party = PartyState(memberIds = listOf(KAI_ID, SYVIAL_ID, "missing-member"))),
      "diep_minh"
    )
    val missingOrder = CombatRuntime.toJson(missingState)!!.getJSONArray("autoOrder")
    assertEquals(listOf("kai", "entity:kai", "syvial", "entity:syvial"), (0 until missingOrder.length()).map { missingOrder.getString(it) })
  }

  @Test fun trueTurnEntityResponseDamagesOnlyItsEncodedPartyMember() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial())).copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID))
    )
    val base = CombatRuntime.start(initial, "slenderman")
    val targets = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID)
    fun hp(state: GameState, id: String): Int = if (id == KAI_ID) {
      CombatRuntime.active(state)!!.playerHp
    } else {
      state.characters.getValue(id).vitalState.currentHp
    }

    targets.forEachIndexed { slot, targetId ->
      var observedDamage = false
      for (counter in 1..128) {
        val candidate = base.copy(metadata = base.metadata + mapOf(
          "combat.autoCursor" to (slot * 2 + 1).toString(),
          "combat.eventCounter" to counter.toString()
        ))
        val before = targets.associateWith { hp(candidate, it) }
        val response = CombatRuntime.resolve(candidate, "AUTO_COMBAT_STEP", "auto")
        val after = targets.associateWith { hp(response.state, it) }
        targets.filter { it != targetId }.forEach { otherId -> assertEquals(before[otherId], after[otherId]) }
        if (after.getValue(targetId) < before.getValue(targetId)) {
          observedDamage = true
          break
        }
      }
      assertTrue("Expected an Entity response to damage $targetId", observedDamage)
    }
  }

  @Test fun trueTurnAutoCursorSurvivesSaveLoadBeforeFourthMemberResponse() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial())).copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID))
    )
    val started = CombatRuntime.start(initial, "diep_minh")
    val irisTurn = started.copy(metadata = started.metadata + ("combat.autoCursor" to "6"))
    val iris = CombatRuntime.resolve(irisTurn, "AUTO_COMBAT_STEP", "auto")
    assertEquals("entity:iris", CombatRuntime.toJson(iris.state)!!.getString("activeActorId"))
    val loaded = GameStateCodec.decode(GameStateCodec.encode(iris.state))
    assertEquals("entity:iris", CombatRuntime.toJson(loaded)!!.getString("activeActorId"))
    assertEquals(3, CombatRuntime.toJson(loaded)!!.getInt("activeActorSlot"))
  }

  @Test fun trueTurnKaiSubturnDoesNotRunLegacyCompanionAssists() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial())).copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID))
    )
    val state = CombatRuntime.start(initial, "diep_minh")
    val kai = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
    for (legacyAssist in listOf("Lucia \"Lục\"", "ARGUS", "Syvial", "Rift Sever", "Crimson Guillotine")) {
      assertFalse("Kai subturn must not execute $legacyAssist", kai.reply.contains(legacyAssist))
    }

    val irisTurn = state.copy(metadata = state.metadata + ("combat.autoCursor" to "6"))
    val iris = CombatRuntime.resolve(irisTurn, "AUTO_COMBAT_STEP", "auto")
    assertTrue(iris.reply.contains("Iris"))
    assertEquals("entity:iris", CombatRuntime.toJson(iris.state)!!.getString("activeActorId"))
  }

  @Test fun trueTurnAutoCombatExcludesNonCombatPartyMembers() {
    val initial = AnNhienCanon.ensure(GameState.initial()).copy(
      party = PartyState(memberIds = listOf(KAI_ID, AN_NHIEN_ID))
    )
    val state = CombatRuntime.start(initial, "diep_minh")
    val order = CombatRuntime.toJson(state)!!.getJSONArray("autoOrder")
    assertEquals(listOf("kai", "entity:kai"), (0 until order.length()).map { order.getString(it) })
    assertFalse((0 until order.length()).map { order.getString(it) }.any { it.contains(AN_NHIEN_ID) })
  }

  @Test fun trueTurnAutoCombatNeverRunsAnNhienWeaponFallback() {
    val initial = AnNhienCanon.ensure(GameState.initial()).copy(
      party = PartyState(memberIds = listOf(KAI_ID, AN_NHIEN_ID))
    )
    var state = CombatRuntime.start(initial, "diep_minh")
    repeat(2) {
      val resolution = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
      assertFalse(resolution.reply.contains("An Nhiên tấn công"))
      state = resolution.state
    }
    assertEquals("kai", CombatRuntime.toJson(state)!!.getString("activeActorId"))
  }

  @Test fun trueTurnCounterMissesBelongOnlyToEncodedTarget() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial())).copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID))
    )
    val base = CombatRuntime.start(initial, "slenderman")

    fun findProc(cursor: Int, marker: String): CombatRuntime.Resolution? {
      for (counter in 1..2048) {
        val candidate = base.copy(metadata = base.metadata + mapOf(
          "combat.autoCursor" to cursor.toString(),
          "combat.eventCounter" to counter.toString()
        ))
        val result = CombatRuntime.resolve(candidate, "AUTO_COMBAT_STEP", "auto")
        if (result.reply.contains(marker)) return result
      }
      return null
    }

    val iris = findProc(7, "Dead Angle")
    assertNotNull("entity:iris miss must be able to proc Dead Angle", iris)
    assertFalse(iris!!.reply.contains("Counterphase"))

    val syvial = findProc(5, "Counterphase")
    assertNotNull("entity:syvial miss must be able to proc Counterphase", syvial)
    assertFalse(syvial!!.reply.contains("Dead Angle"))

    for (counter in 1..512) {
      val kaiTurn = base.copy(metadata = base.metadata + mapOf(
        "combat.autoCursor" to "1",
        "combat.eventCounter" to counter.toString()
      ))
      val kai = CombatRuntime.resolve(kaiTurn, "AUTO_COMBAT_STEP", "auto")
      assertFalse(kai.reply.contains("Dead Angle"))
      assertFalse(kai.reply.contains("Counterphase"))
    }
  }

  @Test fun trueTurnEntityResponseHonorsPersistedAccuracyPenalty() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial())).copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID))
    )
    val base = CombatRuntime.start(initial, "slenderman")
    var observed = false
    for (counter in 1..2048) {
      val common = base.metadata + mapOf(
        "combat.autoCursor" to "3",
        "combat.eventCounter" to counter.toString()
      )
      val without = CombatRuntime.resolve(base.copy(metadata = common), "AUTO_COMBAT_STEP", "auto")
      val withPenalty = CombatRuntime.resolve(
        base.copy(metadata = common + ("combat.autoAccuracyPenalty" to "80")),
        "AUTO_COMBAT_STEP",
        "auto"
      )
      val beforeHp = base.characters.getValue(LUCIA_ID).vitalState.currentHp
      val withoutHp = without.state.characters.getValue(LUCIA_ID).vitalState.currentHp
      val withHp = withPenalty.state.characters.getValue(LUCIA_ID).vitalState.currentHp
      if (withoutHp < beforeHp && withHp == beforeHp) {
        observed = true
        break
      }
    }
    assertTrue("persisted companion penalty must change the Entity subturn hit outcome", observed)
  }

  @Test fun trueTurnIrisDeadAngleLethalCounterEndsCombatImmediately() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial())).copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID))
    )
    val base = CombatRuntime.start(initial, "slenderman")
    var observed: CombatRuntime.Resolution? = null
    for (counter in 1..4096) {
      val candidate = base.copy(metadata = base.metadata + mapOf(
        "combat.autoCursor" to "7",
        "combat.eventCounter" to counter.toString(),
        "combat.entityHp" to "1"
      ))
      val result = CombatRuntime.resolve(candidate, "AUTO_COMBAT_STEP", "auto")
      if (result.reply.contains("Dead Angle")) {
        observed = result
        break
      }
    }
    assertNotNull("entity:iris miss must deterministically reach Dead Angle", observed)
    val result = observed!!
    assertTrue(result.entityDestroyed)
    assertTrue(result.roundCompleted)
    assertNull(CombatRuntime.active(result.state))
    assertNull(CombatRuntime.toJson(result.state))
    assertFalse(result.state.metadata.containsKey("combat.autoCursor"))
  }

  @Test fun trueTurnSyvialCounterphaseLethalCounterEndsCombatImmediately() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial())).copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID))
    )
    val base = CombatRuntime.start(initial, "slenderman")
    var observed: CombatRuntime.Resolution? = null
    for (counter in 1..4096) {
      val candidate = base.copy(metadata = base.metadata + mapOf(
        "combat.autoCursor" to "5",
        "combat.eventCounter" to counter.toString(),
        "combat.entityHp" to "1"
      ))
      val result = CombatRuntime.resolve(candidate, "AUTO_COMBAT_STEP", "auto")
      if (result.reply.contains("Counterphase")) {
        observed = result
        break
      }
    }
    assertNotNull("entity:syvial miss must deterministically reach Counterphase", observed)
    val result = observed!!
    assertTrue(result.entityDestroyed)
    assertTrue(result.roundCompleted)
    assertNull(CombatRuntime.active(result.state))
    assertNull(CombatRuntime.toJson(result.state))
    assertFalse(result.state.metadata.containsKey("combat.autoCursor"))
  }

  @Test fun trueTurnPendingAccuracyPenaltyIsConsumedByFirstEntityResponse() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial())).copy(
      party = PartyState(memberIds = listOf(KAI_ID, LUCIA_ID, SYVIAL_ID, IRIS_ID))
    )
    val base = CombatRuntime.start(initial, "slenderman")
    var consumed: GameState? = null
    for (counter in 1..2048) {
      val common = base.metadata + mapOf(
        "combat.autoCursor" to "3",
        "combat.eventCounter" to counter.toString()
      )
      val without = CombatRuntime.resolve(base.copy(metadata = common), "AUTO_COMBAT_STEP", "auto")
      val withPenalty = CombatRuntime.resolve(
        base.copy(metadata = common + ("combat.autoAccuracyPenalty" to "80")),
        "AUTO_COMBAT_STEP",
        "auto"
      )
      val beforeHp = base.characters.getValue(LUCIA_ID).vitalState.currentHp
      val withoutHp = without.state.characters.getValue(LUCIA_ID).vitalState.currentHp
      val withHp = withPenalty.state.characters.getValue(LUCIA_ID).vitalState.currentHp
      if (withoutHp < beforeHp && withHp == beforeHp) {
        consumed = withPenalty.state
        break
      }
    }
    assertNotNull("pending accuracy penalty must affect the first Entity response", consumed)
    val afterFirst = consumed!!
    assertFalse(afterFirst.metadata.containsKey("combat.autoAccuracyPenalty"))

    var secondResponseProvedUnpenalized = false
    for (counter in 1..2048) {
      val common = afterFirst.metadata + mapOf(
        "combat.autoCursor" to "5",
        "combat.eventCounter" to counter.toString()
      )
      val unpenalizedState = afterFirst.copy(metadata = common)
      val unpenalized = CombatRuntime.resolve(unpenalizedState, "AUTO_COMBAT_STEP", "auto")
      val rePenalized = CombatRuntime.resolve(
        afterFirst.copy(metadata = common + ("combat.autoAccuracyPenalty" to "80")),
        "AUTO_COMBAT_STEP",
        "auto"
      )
      val beforeHp = afterFirst.characters.getValue(SYVIAL_ID).vitalState.currentHp
      val unpenalizedHp = unpenalized.state.characters.getValue(SYVIAL_ID).vitalState.currentHp
      val rePenalizedHp = rePenalized.state.characters.getValue(SYVIAL_ID).vitalState.currentHp
      if (unpenalizedHp < beforeHp && rePenalizedHp == beforeHp) {
        secondResponseProvedUnpenalized = true
        break
      }
    }
    assertTrue("the next Entity response must not inherit the consumed An Nhien penalty", secondResponseProvedUnpenalized)
  }

  @Test fun trueTurnNoKaiPartyCountsExactlyOncePerCycleAcrossSaveLoad() {
    val initial = SpecialFollowersCanon.ensure(GameState.initial()).copy(
      party = PartyState(leaderId = IRIS_ID, memberIds = listOf(IRIS_ID, SYVIAL_ID))
    )
    var state = CombatRuntime.start(initial, "diep_minh")
    val order = CombatRuntime.toJson(state)!!.getJSONArray("autoOrder")
    assertEquals(listOf("iris", "entity:iris", "syvial", "entity:syvial"), (0 until order.length()).map { order.getString(it) })

    val iris = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
    assertEquals(1, CombatRuntime.active(iris.state)!!.eventCounter)
    assertEquals("1", iris.state.metadata["combat.autoRoundCounted"])

    val loaded = GameStateCodec.decode(GameStateCodec.encode(iris.state))
    assertEquals("1", loaded.metadata["combat.autoRoundCounted"])
    val entityIris = CombatRuntime.resolve(loaded, "AUTO_COMBAT_STEP", "auto")
    assertEquals(1, CombatRuntime.active(entityIris.state)!!.eventCounter)
    val syvial = CombatRuntime.resolve(entityIris.state, "AUTO_COMBAT_STEP", "auto")
    assertEquals(1, CombatRuntime.active(syvial.state)!!.eventCounter)
    val entitySyvial = CombatRuntime.resolve(syvial.state, "AUTO_COMBAT_STEP", "auto")
    assertTrue(entitySyvial.roundCompleted)
    assertEquals(1, CombatRuntime.active(entitySyvial.state)!!.eventCounter)
    assertFalse(entitySyvial.state.metadata.containsKey("combat.autoRoundCounted"))

    state = CombatRuntime.resolve(entitySyvial.state, "AUTO_COMBAT_STEP", "auto").state
    assertEquals(2, CombatRuntime.active(state)!!.eventCounter)
  }

  @Test fun trueTurnFirstLiveAttackerCountsRoundWhenEarlierPartySlotIsSeparated() {
    val ensured = SpecialFollowersCanon.ensure(GameState.initial())
    val separatedSyvial = ensured.characters.getValue(SYVIAL_ID).copy(presence = CharacterPresence.SEPARATED)
    val initial = ensured.copy(
      party = PartyState(leaderId = IRIS_ID, memberIds = listOf(SYVIAL_ID, IRIS_ID)),
      characters = ensured.characters + (SYVIAL_ID to separatedSyvial)
    )
    val state = CombatRuntime.start(initial, "diep_minh")
    val order = CombatRuntime.toJson(state)!!.getJSONArray("autoOrder")
    assertEquals(listOf("iris", "entity:iris"), (0 until order.length()).map { order.getString(it) })
    val iris = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
    assertEquals(1, CombatRuntime.active(iris.state)!!.eventCounter)
    assertEquals("1", iris.state.metadata["combat.autoRoundCounted"])
  }

  @Test fun trueTurnNoKaiLuciaFirstCountsExactlyOncePerCycle() {
    val initial = SpecialFollowersCanon.ensure(LuciaCanon.ensure(GameState.initial())).copy(
      party = PartyState(leaderId = LUCIA_ID, memberIds = listOf(LUCIA_ID, SYVIAL_ID))
    )
    val state = CombatRuntime.start(initial, "diep_minh")
    val order = CombatRuntime.toJson(state)!!.getJSONArray("autoOrder")
    assertEquals(listOf("lucia", "entity:lucia", "syvial", "entity:syvial"), (0 until order.length()).map { order.getString(it) })

    val lucia = CombatRuntime.resolve(state, "AUTO_COMBAT_STEP", "auto")
    assertEquals(1, CombatRuntime.active(lucia.state)!!.eventCounter)
    assertEquals("1", lucia.state.metadata["combat.autoRoundCounted"])

    val loaded = GameStateCodec.decode(GameStateCodec.encode(lucia.state))
    val entityLucia = CombatRuntime.resolve(loaded, "AUTO_COMBAT_STEP", "auto")
    assertEquals(1, CombatRuntime.active(entityLucia.state)!!.eventCounter)
    val syvial = CombatRuntime.resolve(entityLucia.state, "AUTO_COMBAT_STEP", "auto")
    assertEquals(1, CombatRuntime.active(syvial.state)!!.eventCounter)
    val entitySyvial = CombatRuntime.resolve(syvial.state, "AUTO_COMBAT_STEP", "auto")
    assertTrue(entitySyvial.roundCompleted)
    assertEquals(1, CombatRuntime.active(entitySyvial.state)!!.eventCounter)
    assertFalse(entitySyvial.state.metadata.containsKey("combat.autoRoundCounted"))
  }
}
