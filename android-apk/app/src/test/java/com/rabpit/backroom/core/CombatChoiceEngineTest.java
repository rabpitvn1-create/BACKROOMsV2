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
        .put(member("lucia", "Lucia Lục"))
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
    assertEquals("Lucia Lục", state.getJSONObject("combat").getString("currentActor"));

    finalizeAs(state, 2,2,1,4,6);
    CombatChoiceEngine.resolveFinalized(state);
    assertEquals("Syvial", state.getJSONObject("combat").getString("currentActor"));

    int round = state.getJSONObject("combat").getInt("round");
    finalizeAs(state, 2,2,1,4,6);
    CombatChoiceEngine.resolveFinalized(state);
    assertEquals("Cao Minh", state.getJSONObject("combat").getString("currentActor"));
    assertEquals(round + 1, state.getJSONObject("combat").getInt("round"));
  }

  @Test public void rerollsDoNotSpamGmLogAndResolveAddsOneCombatLine() throws Exception {
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
    assertEquals(1, battleLog.length());
    String text = battleLog.getJSONObject(0).getString("text");
    assertFalse(text.toLowerCase().contains("reroll"));
    assertFalse(text.toLowerCase().contains("dice"));
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

  @Test public void caoMinhAndLuciaHaveAuthoritativeUltimateMappings() {
    assertTrue(CombatChoiceEngine.hasAuthoritativeUltimate("cao_minh"));
    assertTrue(CombatChoiceEngine.hasAuthoritativeUltimate("lucia"));
    assertFalse(CombatChoiceEngine.hasAuthoritativeUltimate("iris"));
    assertFalse(CombatChoiceEngine.hasAuthoritativeUltimate("syvial"));
  }

  @Test public void luciaSsfUsesDynamicTooYoungToDieUltimate() throws Exception {
    JSONObject state = combatState(new JSONArray().put(member("lucia", "Lucia Lục")));
    CombatChoiceEngine.start(state, "diep_minh", 0);

    finalizeAs(state, 2,2,4,4,6);
    CombatChoiceEngine.resolveFinalized(state);
    assertEquals("Lucia Lục", state.getJSONObject("combat").getString("currentActor"));

    JSONObject entity = state.getJSONObject("combat").getJSONObject("entity");
    int before = entity.getInt("hp");
    int expected = CombatChoiceEngine.ultimateDamage(24, 60, 15, 100);

    finalizeAs(state, 1,2,3,4,5);
    CombatChoiceEngine.resolveFinalized(state);

    assertEquals(before - expected, entity.getInt("hp"));
    JSONArray battleLog = state.getJSONArray("log").getJSONObject(0).getJSONArray("battleLog");
    assertTrue(battleLog.getJSONObject(battleLog.length() - 1).getString("text")
        .contains("Too Young To Die"));
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
