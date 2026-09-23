package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CombatChoiceEngineTest {
  @Test public void startProducesInitialFiveD6ValuesAndProjectsHand() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject dice = state.getJSONObject("combat").getJSONObject("diceState");
    JSONArray values = dice.getJSONArray("values");
    assertTrue(dice.getBoolean("hasRolled"));
    assertEquals(0, dice.getInt("rerollsUsed"));
    assertEquals(5, values.length());
    int[] rolled = new int[5];
    for (int i = 0; i < 5; i++) {
      rolled[i] = values.getInt(i);
      assertTrue(rolled[i] >= 1 && rolled[i] <= 6);
    }
    assertEquals(CombatChoiceEngine.classify(rolled), dice.getString("hand"));
  }

  @Test public void holdPersistsAndRerollTouchesOnlyUnheldDice() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject dice = combat.getJSONObject("diceState");
    JSONArray before = new JSONArray(dice.getJSONArray("values").toString());
    CombatChoiceEngine.setHold(state, 0, true);
    CombatChoiceEngine.setHold(state, 3, true);
    int sequenceBefore = combat.getInt("rngSequence");
    int seed = combat.getInt("seed");

    CombatChoiceEngine.roll(state);

    JSONArray after = dice.getJSONArray("values");
    assertEquals(before.getInt(0), after.getInt(0));
    assertEquals(before.getInt(3), after.getInt(3));
    int sequence = sequenceBefore;
    for (int slot : new int[]{1,2,4}) {
      assertEquals(CombatChoiceEngine.deterministicDie(seed, sequence, slot), after.getInt(slot));
      sequence++;
    }
    assertEquals(sequenceBefore + 3, combat.getInt("rngSequence"));
    assertEquals(1, dice.getInt("rerollsUsed"));
    assertEquals(CombatChoiceEngine.classify(
        after.getInt(0), after.getInt(1), after.getInt(2), after.getInt(3), after.getInt(4)),
        dice.getString("hand"));
  }

  @Test public void normalizeActiveLegacyTurnBackfillsInitialRoll() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject dice = combat.getJSONObject("diceState");
    dice.put("values", new JSONArray().put(0).put(0).put(0).put(0).put(0))
        .put("hasRolled", false)
        .put("rerollsUsed", 0)
        .put("hand", "");
    combat.put("rngSequence", 0);

    CombatChoiceEngine.normalizeTerminalEncounter(state);

    JSONArray values = dice.getJSONArray("values");
    assertTrue(dice.getBoolean("hasRolled"));
    assertEquals(0, dice.getInt("rerollsUsed"));
    assertEquals(CombatChoiceEngine.classify(
        values.getInt(0), values.getInt(1), values.getInt(2), values.getInt(3), values.getInt(4)),
        dice.getString("hand"));
  }

  @Test public void exactlyThreeRerollsThenRollStopsUntilFinish() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject dice = state.getJSONObject("combat").getJSONObject("diceState");
    assertFalse(dice.getBoolean("finalized"));

    CombatChoiceEngine.roll(state);
    CombatChoiceEngine.roll(state);
    CombatChoiceEngine.roll(state);

    assertEquals(3, dice.getInt("rerollsUsed"));
    assertFalse(dice.getBoolean("finalized"));
    String frozen = dice.toString();
    CombatChoiceEngine.roll(state);
    assertEquals(frozen, dice.toString());

    CombatChoiceEngine.finishHand(state);
    assertTrue(dice.getBoolean("finalized"));
  }

  @Test public void holdingAllFiveStopsRerollUntilFinishWithoutSpendingReroll() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    for (int i = 0; i < 5; i++) CombatChoiceEngine.setHold(state, i, true);
    JSONObject dice = state.getJSONObject("combat").getJSONObject("diceState");
    CombatChoiceEngine.roll(state);
    assertFalse(dice.getBoolean("finalized"));
    assertEquals(0, dice.getInt("rerollsUsed"));

    CombatChoiceEngine.finishHand(state);
    assertTrue(dice.getBoolean("finalized"));
    assertEquals(0, dice.getInt("rerollsUsed"));
  }

  @Test public void finishCanFinalizeCurrentHandBeforeAnyReroll() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject dice = state.getJSONObject("combat").getJSONObject("diceState");
    dice.put("values", new JSONArray().put(2).put(2).put(1).put(4).put(6));

    CombatChoiceEngine.finishHand(state);

    assertTrue(dice.getBoolean("finalized"));
    assertEquals(0, dice.getInt("rerollsUsed"));
    assertEquals("ONE PAIR", dice.getString("hand"));
  }

  @Test public void serializedReloadCannotProduceFreeDifferentReroll() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    CombatChoiceEngine.setHold(state, 1, true);
    JSONObject reloaded = new JSONObject(state.toString());

    CombatChoiceEngine.roll(state);
    CombatChoiceEngine.roll(reloaded);

    assertEquals(
        state.getJSONObject("combat").getJSONObject("diceState").toString(),
        reloaded.getJSONObject("combat").getJSONObject("diceState").toString());
    assertEquals(state.getJSONObject("combat").getInt("rngSequence"),
        reloaded.getJSONObject("combat").getInt("rngSequence"));
  }

  @Test public void orderedStraightClassificationIsExact() {
    assertEquals("SSF", CombatChoiceEngine.classify(1,2,3,4,5));
    assertEquals("SSF", CombatChoiceEngine.classify(5,4,3,2,1));
    assertEquals("STRAIGHT", CombatChoiceEngine.classify(2,3,4,5,6));
    assertEquals("STRAIGHT", CombatChoiceEngine.classify(6,5,4,3,2));
    assertNotEquals("STRAIGHT", CombatChoiceEngine.classify(5,1,4,2,3));
    assertNotEquals("SSF", CombatChoiceEngine.classify(5,1,4,2,3));
  }

  @Test public void pairTwoPairTripleFullHouseFourAndFiveKindClassifyCorrectly() {
    assertEquals("ONE PAIR", CombatChoiceEngine.classify(2,2,1,4,6));
    assertEquals("TWO PAIR", CombatChoiceEngine.classify(2,2,4,4,6));
    assertEquals("THREE OF A KIND", CombatChoiceEngine.classify(3,3,3,1,6));
    assertEquals("FULL HOUSE", CombatChoiceEngine.classify(3,3,3,6,6));
    assertEquals("FOUR OF A KIND", CombatChoiceEngine.classify(4,4,4,4,2));
    assertEquals("FSF", CombatChoiceEngine.classify(6,6,6,6,6));
  }

  @Test public void fsfPriorityOverridesFourOfAKind() {
    assertEquals("FSF", CombatChoiceEngine.classify(1,1,1,1,1));
  }

  @Test public void handAndStatsApplyExactlyOnceToDamage() {
    assertEquals(30, CombatChoiceEngine.basicDamage(30, 5, 100));
    assertEquals(38, CombatChoiceEngine.basicDamage(30, 5, 125));
    assertEquals(33, CombatChoiceEngine.basicDamage(30, 6, 100));
    assertEquals(77, CombatChoiceEngine.skillDamage(30, 170, 5, 150));
    assertEquals(84, CombatChoiceEngine.skillDamage(30, 170, 6, 150));
    assertEquals(35, CombatChoiceEngine.ultimateDamage(30, 1, 15, 100));
    assertEquals(840, CombatChoiceEngine.ultimateDamage(30, 24, 15, 100));
    assertEquals(1680, CombatChoiceEngine.ultimateDamage(30, 24, 15, 200));
    assertEquals(2100, CombatChoiceEngine.ultimateDamage(30, 60, 15, 100));
  }

  @Test public void defenseUsesDiminishingDivisionAndNeverImmunity() {
    assertEquals(20, CombatChoiceEngine.defendedIncomingDamage(20, 5));
    assertEquals(18, CombatChoiceEngine.defendedIncomingDamage(20, 6));
    assertEquals(10, CombatChoiceEngine.defendedIncomingDamage(20, 15));
    assertEquals(1, CombatChoiceEngine.defendedIncomingDamage(1, 999));
  }

  @Test public void downedCharacterIsSkippedAndFourSlotOrderWrapsRound() throws Exception {
    JSONArray party = new JSONArray()
        .put(member("luc_tram", "Lục Trầm"))
        .put(member("iris", "Iris"))
        .put(member("syvial", "Syvial"));
    JSONObject state = combatState(party);
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);
    progression.setCurrentHp(state, "iris", 0);

    CombatChoiceEngine.start(state, "diep_minh", 0);
    assertEquals(4, state.getJSONObject("combat").getJSONArray("participants").length());

    finalizeAs(state, 2,2,1,4,6);
    CombatChoiceEngine.resolveFinalized(state);
    assertEquals("Lục Trầm", state.getJSONObject("combat").getString("currentActor"));

    finalizeAs(state, 2,2,1,4,6);
    CombatChoiceEngine.resolveFinalized(state);
    assertEquals("Syvial", state.getJSONObject("combat").getString("currentActor"));

    int round = state.getJSONObject("combat").getInt("round");
    finalizeAs(state, 2,2,1,4,6);
    CombatChoiceEngine.resolveFinalized(state);
    assertEquals("Cao Minh", state.getJSONObject("combat").getString("currentActor"));
    assertEquals(round + 1, state.getJSONObject("combat").getInt("round"));
  }

  @Test public void rerollsDoNotSpamGmLogAndResolveAddsOrderedActorAndEntityLines() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    CombatChoiceEngine.roll(state);
    CombatChoiceEngine.roll(state);
    CombatChoiceEngine.roll(state);
    JSONArray gmLog = state.getJSONArray("log");
    assertFalse(gmLog.getJSONObject(0).has("battleLog"));

    CombatChoiceEngine.finishHand(state);
    CombatChoiceEngine.resolveFinalized(state);

    JSONArray battleLog = gmLog.getJSONObject(0).getJSONArray("battleLog");
    assertEquals(2, battleLog.length());
    String actorLine = battleLog.getJSONObject(0).getString("text");
    String entityLine = battleLog.getJSONObject(1).getString("text");
    assertTrue(actorLine.startsWith("["));
    assertTrue(actorLine.contains("Cao Minh"));
    assertTrue(actorLine.contains("Hound -"));
    assertTrue(actorLine.matches(".*\\[\\d+/\\d+ HP\\].*"));
    assertTrue(entityLine.startsWith("Hound "));
    assertTrue(entityLine.contains("Cao Minh -"));
    assertTrue(entityLine.matches(".*\\[\\d+/\\d+ HP\\].*"));
    assertFalse(actorLine.toLowerCase().contains("reroll"));
    assertFalse(actorLine.toLowerCase().contains("dice"));
  }

  @Test public void asyncEnemiesHaveDistinctCombatProfiles() throws Exception {
    String[] keys = {"async_rifleman", "async_vanguard", "async_tactical", "async_decon"};
    String[] names = {"ASYNC Rifleman", "ASYNC Vanguard", "ASYNC Tactical Operative", "ASYNC Decon Specialist"};
    int[] hp = {300, 330, 270, 315};
    int[] damage = {20, 21, 18, 19};

    for (int i = 0; i < keys.length; i++) {
      assertTrue(CombatChoiceEngine.isKnownEntity(keys[i]));
      JSONObject state = combatState(new JSONArray());
      state.getJSONObject("flags").put("entityEncounterKey", keys[i]);

      CombatChoiceEngine.start(state, keys[i], 0);

      JSONObject entity = state.getJSONObject("combat").getJSONObject("entity");
      assertEquals(keys[i], entity.getString("key"));
      assertEquals(names[i], entity.getString("name"));
      assertEquals(hp[i], entity.getInt("baseHp"));
      assertEquals(damage[i], entity.getInt("baseDamage"));
      assertEquals(hp[i], entity.getInt("maxHp"));
      assertEquals(damage[i], entity.getInt("attack"));
    }
  }

  @Test public void caoMinhAndLucTramHaveAuthoritativeUltimateMappings() {
    assertTrue(CombatChoiceEngine.hasAuthoritativeUltimate("cao_minh"));
    assertTrue(CombatChoiceEngine.hasAuthoritativeUltimate("luc_tram"));
    assertFalse(CombatChoiceEngine.hasAuthoritativeUltimate("iris"));
    assertFalse(CombatChoiceEngine.hasAuthoritativeUltimate("syvial"));
  }

  @Test public void lucTramSsfUsesDynamicThienKiemDinhGioiUltimate() throws Exception {
    JSONObject state = combatState(new JSONArray().put(member("luc_tram", "Lục Trầm")));
    CombatChoiceEngine.start(state, "diep_minh", 0);

    finalizeAs(state, 2,2,4,4,6);
    CombatChoiceEngine.resolveFinalized(state);
    assertEquals("Lục Trầm", state.getJSONObject("combat").getString("currentActor"));

    JSONObject entity = state.getJSONObject("combat").getJSONObject("entity");
    int before = entity.getInt("hp");
    int expected = CombatChoiceEngine.ultimateDamage(24, 60, 15, 100);

    finalizeAs(state, 1,2,3,4,5);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(before - expected, entity.getInt("hp"));
    JSONArray battleLog = state.getJSONArray("log").getJSONObject(0).getJSONArray("battleLog");
    boolean foundUltimate = false;
    for (int i = 0; i < battleLog.length(); i++) {
      if (battleLog.getJSONObject(i).getString("text").contains("Thiên Kiếm Định Giới")) {
        foundUltimate = true;
        break;
      }
    }
    assertTrue(foundUltimate);
  }

  @Test public void firstEntityRotationHasExactlyThreeSkillsEach() {
  assertEquals(3, CombatChoiceEngine.entitySkillCount("hound"));
  assertEquals(3, CombatChoiceEngine.entitySkillCount("clump"));
  assertEquals(3, CombatChoiceEngine.entitySkillCount("duller"));
}
@Test public void entitySkillDamageIsBounded() {
  assertEquals(18, CombatChoiceEngine.entitySkillDamage(15,120));
  assertEquals(17, CombatChoiceEngine.entitySkillDamage(15,115));
  assertEquals(21, CombatChoiceEngine.entitySkillDamage(17,125));
}
@Test public void entitySkillProcRollIsStableWithinTurn() {
  int r=CombatChoiceEngine.entitySkillProcRoll(12345,4,2,1); assertTrue(r>=0&&r<100);
  assertEquals(r,CombatChoiceEngine.entitySkillProcRoll(12345,4,2,1));
}

  @Test public void onePairUsesCompactPairTokenInBattleLog() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);

    finalizeAs(state, 2,2,1,4,6);
    CombatChoiceEngine.resolveFinalized(state);

    JSONArray battleLog = state.getJSONArray("log").getJSONObject(0).getJSONArray("battleLog");
    String actorLine = battleLog.getJSONObject(0).getString("text");
    assertTrue(actorLine.startsWith("[PAIR] Cao Minh "));
    assertFalse(actorLine.contains("[ONE PAIR]"));
  }

  @Test public void fourOfAKindUsesCompactDetailedBattleLineAndNoProcTextFloater() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "diep_minh", 0);

    finalizeAs(state, 4,4,4,4,2);
    CombatChoiceEngine.resolveFinalized(state);

    JSONArray battleLog = state.getJSONArray("log").getJSONObject(0).getJSONArray("battleLog");
    String actorLine = battleLog.getJSONObject(0).getString("text");
    assertTrue(actorLine.startsWith("[F.O.A.K] Cao Minh "));
    assertFalse(actorLine.contains("FOUR OF A KIND"));
    assertTrue(actorLine.contains("Diệp Minh -"));
    assertTrue(actorLine.matches(".*\\[\\d+/\\d+ HP\\].*"));

    JSONArray feedback = state.getJSONObject("combat").getJSONArray("feedbackEvents");
    for (int i = 0; i < feedback.length(); i++) {
      String text = feedback.getJSONObject(i).optString("text", "");
      if (text.isEmpty()) continue;
      assertTrue("Unexpected combat floater: " + text, text.matches("-\\d+ HP"));
      assertFalse(text.contains("PROC"));
    }
  }

  @Test public void caoMinhAndLucTramEachHaveFiveCharacterProcsAndKaiHasNone() {
    assertEquals(5, CombatChoiceEngine.characterProcCount("cao_minh"));
    assertEquals(5, CombatChoiceEngine.characterProcCount("luc_tram"));
    assertEquals(0, CombatChoiceEngine.characterProcCount("kai"));

    for (String id : new String[]{"cao_minh", "luc_tram"}) {
      for (int i = 0; i < 5; i++) {
        int chance = CombatChoiceEngine.characterProcPercent(id, i);
        assertTrue(chance >= 45 && chance <= 55);
      }
    }
  }

  @Test public void lethalBleedAtRoundStartEndsCombatBeforeActorAction() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject entity = combat.getJSONObject("entity");

    combat.put("round", 2).put("actorIndex", 0);
    entity.put("hp", 1)
        .put("bleedTurns", 1).put("bleedPercent", 3)
        .put("poisonTurns", 0).put("poisonPercent", 0);
    finalizeAs(state, 1,2,3,4,6);

    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(0, entity.getInt("hp"));
    assertFalse(combat.getBoolean("active"));
    assertEquals("victory", combat.getString("outcome"));
    assertTrue(combat.getJSONObject("diceState").getBoolean("resolved"));
    assertFalse(combat.getBoolean("resolvedEntityTurn"));
    assertEquals(1, combat.getJSONArray("feedbackEvents").length());
    assertFalse(state.getJSONArray("log").getJSONObject(0).has("battleLog"));
  }

  @Test public void lethalPoisonAtRoundStartEndsCombatBeforeActorAction() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject entity = combat.getJSONObject("entity");

    combat.put("round", 2).put("actorIndex", 0);
    entity.put("hp", 1)
        .put("bleedTurns", 0).put("bleedPercent", 0)
        .put("poisonTurns", 1).put("poisonPercent", 3);
    finalizeAs(state, 1,2,3,4,6);

    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(0, entity.getInt("hp"));
    assertFalse(combat.getBoolean("active"));
    assertEquals("victory", combat.getString("outcome"));
    assertTrue(combat.getJSONObject("diceState").getBoolean("resolved"));
    assertFalse(combat.getBoolean("resolvedEntityTurn"));
    assertEquals(1, combat.getJSONArray("feedbackEvents").length());
    assertFalse(state.getJSONArray("log").getJSONObject(0).has("battleLog"));
  }

  @Test public void stackingRulesIncreasePotencyButOnlyStunIncreasesDuration() throws Exception {
    JSONObject entity = new JSONObject()
        .put("bleedTurns", 2).put("bleedPercent", 3)
        .put("poisonTurns", 2).put("poisonPercent", 3)
        .put("armorBreakTurns", 2).put("armorBreakPercent", 10)
        .put("stunTurns", 1);

    CombatChoiceEngine.applyStackingEffect(entity, "Chảy máu", 3, 4);
    assertEquals(2, entity.getInt("bleedTurns"));
    assertEquals(7, entity.getInt("bleedPercent"));

    CombatChoiceEngine.applyStackingEffect(entity, "Trúng độc", 3, 5);
    assertEquals(2, entity.getInt("poisonTurns"));
    assertEquals(8, entity.getInt("poisonPercent"));

    CombatChoiceEngine.applyStackingEffect(entity, "Xuyên giáp", 3, 15);
    assertEquals(2, entity.getInt("armorBreakTurns"));
    assertEquals(25, entity.getInt("armorBreakPercent"));

    CombatChoiceEngine.applyStackingEffect(entity, "Choáng", 1, 0);
    assertEquals(2, entity.getInt("stunTurns"));
  }

  @Test public void expiredProcPotencyDoesNotCarryIntoAnewApplication() throws Exception {
    JSONObject entity = new JSONObject()
        .put("bleedTurns", 0).put("bleedPercent", 25)
        .put("poisonTurns", 0).put("poisonPercent", 25)
        .put("armorBreakTurns", 0).put("armorBreakPercent", 75)
        .put("stunTurns", 0);

    CombatChoiceEngine.applyStackingEffect(entity, "Chảy máu", 2, 4);
    assertEquals(2, entity.getInt("bleedTurns"));
    assertEquals(4, entity.getInt("bleedPercent"));

    CombatChoiceEngine.applyStackingEffect(entity, "Trúng độc", 2, 5);
    assertEquals(2, entity.getInt("poisonTurns"));
    assertEquals(5, entity.getInt("poisonPercent"));

    CombatChoiceEngine.applyStackingEffect(entity, "Xuyên giáp", 2, 10);
    assertEquals(2, entity.getInt("armorBreakTurns"));
    assertEquals(10, entity.getInt("armorBreakPercent"));
  }

  @Test public void basicAttackCanTriggerCharacterProc() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "diep_minh", 0);
    JSONObject combat = state.getJSONObject("combat");

    int seed = 1;
    while (CombatChoiceEngine.characterProcRoll(
        seed, 0, "cao_minh", "Huyết Sát Kiếm Ấn") >= 50) seed++;
    combat.put("seed", seed).put("rngSequence", 0);

    finalizeAs(state, 2,2,1,4,6);
    CombatChoiceEngine.resolveFinalized(state);

    JSONObject entity = combat.getJSONObject("entity");
    assertTrue(entity.getInt("bleedTurns") > 0);
    assertTrue(entity.getInt("bleedPercent") > 0);
  }

  @Test public void normalSkillCanTriggerCharacterProc() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "diep_minh", 0);
    JSONObject combat = state.getJSONObject("combat");

    int seed = 1;
    while (CombatChoiceEngine.characterProcRoll(
        seed, 0, "cao_minh", "Huyết Sát Kiếm Ấn") >= 50) seed++;
    combat.put("seed", seed).put("rngSequence", 0);

    finalizeAs(state, 3,3,3,1,6);
    CombatChoiceEngine.resolveFinalized(state);

    JSONObject entity = combat.getJSONObject("entity");
    assertTrue(entity.getInt("bleedTurns") > 0);
    assertTrue(entity.getInt("bleedPercent") > 0);
  }

  @Test public void ultimateNeverTriggersCharacterProc() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "diep_minh", 0);
    JSONObject combat = state.getJSONObject("combat");

    int seed = 1;
    while (CombatChoiceEngine.characterProcRoll(
        seed, 0, "cao_minh", "Huyết Sát Kiếm Ấn") >= 50) seed++;
    combat.put("seed", seed).put("rngSequence", 0);

    finalizeAs(state, 1,2,3,4,5);
    CombatChoiceEngine.resolveFinalized(state);

    JSONObject entity = combat.getJSONObject("entity");
    assertEquals(0, entity.getInt("bleedTurns"));
    assertEquals(0, entity.getInt("poisonTurns"));
    assertEquals(0, entity.getInt("armorBreakTurns"));
    assertEquals(0, entity.getInt("stunTurns"));
  }

  @Test public void lethalSkillDoesNotApplyStatusToDefeatedEntity() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject entity = combat.getJSONObject("entity");
    entity.put("hp", 1);
    combat.put("currentSkill", new JSONObject()
        .put("name", "Lethal Bleed")
        .put("damagePercent", 100)
        .put("effect", "Chảy máu")
        .put("effectTurns", 3)
        .put("effectValue", 5));

    finalizeAs(state, 2,2,2,4,6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(0, entity.getInt("hp"));
    assertEquals(0, entity.getInt("bleedTurns"));
    assertEquals(0, entity.getInt("bleedPercent"));
  }

  @Test public void lethalCharacterProcDoesNotApplyStatusToDefeatedEntity() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject entity = combat.getJSONObject("entity");
    entity.put("hp", 35);

    int seed = 1;
    while (CombatChoiceEngine.characterProcRoll(
        seed, 0, "cao_minh", "Huyết Sát Kiếm Ấn") >= 50) seed++;
    combat.put("seed", seed).put("rngSequence", 0);

    finalizeAs(state, 1,2,3,4,6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(0, entity.getInt("hp"));
    assertEquals(0, entity.getInt("bleedTurns"));
    assertEquals(0, entity.getInt("bleedPercent"));
  }

  @Test public void legacyPhaGiapAliasUsesArmorBreakRules() throws Exception {
    JSONObject entity = new JSONObject()
        .put("armorBreakTurns", 0)
        .put("armorBreakPercent", 0);

    CombatChoiceEngine.applyStackingEffect(entity, "Phá giáp", 2, 20);

    assertEquals(2, entity.getInt("armorBreakTurns"));
    assertEquals(20, entity.getInt("armorBreakPercent"));
  }

  @Test public void spatialDominionRestoresAccuracyPenaltyMissAndDuration() throws Exception {
    JSONObject state = combatState(new JSONArray().put(member("syvial", "Syvial")));
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject actor = combat.getJSONArray("participants").getJSONObject(1);
    JSONObject entity = combat.getJSONObject("entity");

    combat.put("actorIndex", 1).put("seed", 2).put("rngSequence", 0);
    combat.put("currentSkill", new JSONObject()
        .put("name", "Spatial Dominion")
        .put("damagePercent", 210)
        .put("effect", "Mất phương hướng")
        .put("effectTurns", 2)
        .put("effectValue", 25));
    int hpBefore = actor.getInt("hp");

    finalizeAs(state, 2,2,2,4,6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(hpBefore, actor.getInt("hp"));
    assertEquals(1, entity.getInt("accuracyPenaltyTurns"));
    assertEquals(25, entity.getInt("accuracyPenalty"));
    assertTrue(combat.getBoolean("resolvedEntityTurn"));
  }

  @Test public void armorBreakPercentIncreasesDamageWithoutExtendingDuration() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "diep_minh", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject actor = combat.getJSONArray("participants").getJSONObject(0);
    JSONObject entity = combat.getJSONObject("entity");

    actor.put("id", "iris");
    entity.put("armorBreakTurns", 2).put("armorBreakPercent", 20);
    int before = entity.getInt("hp");

    finalizeAs(state, 2,2,1,4,6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(before - 46, entity.getInt("hp"));
    assertEquals(2, entity.getInt("armorBreakTurns"));
  }

  @Test public void secondaryCombatMathUsesResistanceAndCriticalMultiplier() {
    assertEquals(150, CombatChoiceEngine.criticalDamage(100));
    assertEquals(5, CombatChoiceEngine.effectiveChance(15, 10));
    assertEquals(0, CombatChoiceEngine.effectiveChance(5, 10));
    assertEquals(100, CombatChoiceEngine.effectiveChance(200, 0));

    int roll = CombatChoiceEngine.secondaryStatRoll(12345, 3, 1, "critical");
    assertTrue(roll >= 0 && roll < 100);
    assertEquals(roll, CombatChoiceEngine.secondaryStatRoll(12345, 3, 1, "critical"));
  }

  @Test public void forcedCharacterCriticalUsesProjectedCriticalPath() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject actor = combat.getJSONArray("participants").getJSONObject(0);
    JSONObject entity = combat.getJSONObject("entity");

    actor.put("id", "iris").put("criticalChancePercent", 100);
    entity.put("evasionPercent", 0).put("resCriticalPercent", 0);
    int before = entity.getInt("hp");

    finalizeAs(state, 1,3,4,5,6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(before - 45, entity.getInt("hp"));
    JSONArray battleLog = state.getJSONArray("log").getJSONObject(0).getJSONArray("battleLog");
    assertTrue(battleLog.getJSONObject(0).getString("text").contains("[CRITICAL]"));
  }

  @Test public void forcedPassiveEvasionSkipsEntityDamage() throws Exception {
    JSONObject state = combatState(new JSONArray());
    CombatChoiceEngine.start(state, "hound", 0);
    JSONObject combat = state.getJSONObject("combat");
    JSONObject actor = combat.getJSONArray("participants").getJSONObject(0);
    JSONObject entity = combat.getJSONObject("entity");

    actor.put("id", "iris").put("evasionPercent", 100);
    entity.put("resEvasionPercent", 0).put("evasionPercent", 0);
    int hpBefore = actor.getInt("hp");

    finalizeAs(state, 1,3,4,5,6);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(hpBefore, actor.getInt("hp"));
    JSONArray battleLog = state.getJSONArray("log").getJSONObject(0).getJSONArray("battleLog");
    assertTrue(battleLog.getJSONObject(1).getString("text").contains("Evasion"));
  }

  @Test public void derivedCombatFieldsDoNotChangeStableSeed() throws Exception {
    JSONObject state = combatState(new JSONArray());
    JSONObject legacy = new JSONObject()
        .put("id", "cao_minh")
        .put("name", "Cao Minh")
        .put("sourceIndex", -1)
        .put("hp", 50)
        .put("maxHp", 50)
        .put("baseAttack", 30)
        .put("STR", 5)
        .put("DEF", 5)
        .put("SKL", 5)
        .put("VIT", 5);
    JSONObject enriched = new JSONObject(legacy.toString())
        .put("criticalChancePercent", 5)
        .put("evasionPercent", 0)
        .put("resCriticalPercent", 0)
        .put("resEvasionPercent", 0);

    assertEquals(
        CombatChoiceEngine.stableSeed(state, "hound", new JSONArray().put(legacy)),
        CombatChoiceEngine.stableSeed(state, "hound", new JSONArray().put(enriched)));
  }

  private static void finalizeAs(JSONObject state, int... values) throws Exception {
    JSONObject dice = state.getJSONObject("combat").getJSONObject("diceState");
    JSONArray array = new JSONArray();
    for (int value : values) array.put(value);
    dice.put("values", array)
        .put("hasRolled", true)
        .put("finalized", true)
        .put("resolved", false)
        .put("hand", CombatChoiceEngine.classify(values));
  }

  private static JSONObject member(String id, String name) throws Exception {
    return new JSONObject().put("id", id).put("name", name).put("joined", true);
  }

  private static JSONObject combatState(JSONArray party) throws Exception {
    return new JSONObject()
        .put("turn", 4)
        .put("currentLevel", 0)
        .put(LevelCore.LEVEL_KEY, "0")
        .put("location", LevelCore.LEVEL_ZERO_START_LOCATION)
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", party)
        .put("flags", new JSONObject().put("entityEncounterKey", "hound"))
        .put("log", new JSONArray().put(
            new JSONObject().put("role", "gm").put("text", "Hound xuất hiện")));
  }
}
