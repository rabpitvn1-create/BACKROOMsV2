package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CharacterProgressionCoreTest {
  @Test public void baseStartsAtFiveForAllFourStats() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore(bound -> 0);
    JSONObject stats = core.generateBaseStats(0);
    assertEquals(5, stats.getInt("STR"));
    assertEquals(5, stats.getInt("DF"));
    assertEquals(5, stats.getInt("AGI"));
    assertEquals(5, stats.getInt("CRIT"));
  }

  @Test public void featuredCharactersReceiveExactlyTwentyBonusPoints() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore(bound -> 0);
    JSONObject state = new JSONObject();
    core.normalizeState(state);
    for (String id : new String[]{"cao_minh", "iris", "syvial"}) {
      assertEquals(40, CharacterProgressionCore.sumBaseStats(
          core.profile(state, id).getJSONObject("baseStats")));
    }
  }

  @Test public void luciaAndFutureCharactersReceiveExactlyTenBonusPoints() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore(bound -> 1);
    JSONObject state = new JSONObject();
    core.normalizeState(state);
    assertEquals(30, CharacterProgressionCore.sumBaseStats(
        core.profile(state, "lucia").getJSONObject("baseStats")));
    assertEquals(30, CharacterProgressionCore.sumBaseStats(
        core.ensureProfile(state, "future-character").getJSONObject("baseStats")));
  }

  @Test public void normalizeDoesNotRerollExistingBaseStats() throws Exception {
    CountingRng rng = new CountingRng();
    CharacterProgressionCore core = new CharacterProgressionCore(rng);
    JSONObject state = new JSONObject();
    core.normalizeState(state);
    String first = state.getJSONObject("characterProgression").toString();
    int calls = rng.calls;
    core.normalizeState(state);
    assertEquals(calls, rng.calls);
    assertEquals(first, state.getJSONObject("characterProgression").toString());
  }

  @Test public void freshProfilesStartAtFiftyHpAndZeroProgression() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore(bound -> 0);
    JSONObject state = new JSONObject();
    core.normalizeState(state);
    JSONObject cao_minh = core.profile(state, "cao_minh");
    assertEquals(50, cao_minh.getInt("currentHp"));
    assertEquals(50, cao_minh.getInt("maxHp"));
    assertEquals(0, cao_minh.getInt("explorer"));
    assertEquals(0, cao_minh.getInt("exp"));
  }

  @Test public void explorerRequirementsAreFiftyPerNextExplorerIndex() {
    assertEquals(50, CharacterProgressionCore.requiredExp(0));
    assertEquals(100, CharacterProgressionCore.requiredExp(1));
    assertEquals(150, CharacterProgressionCore.requiredExp(2));
  }

  @Test public void everyKnownEntityCanRewardCoreOwnedExp() {
    assertEquals(10, CharacterProgressionCore.baseExpForEntity("hound"));
    assertEquals(10, CharacterProgressionCore.baseExpForEntity("smiler"));
    assertEquals(10, CharacterProgressionCore.baseExpForEntity("deathmoth", 195));
    assertEquals(83, CharacterProgressionCore.baseExpForEntity("diep_minh", 2000));
    assertEquals(0, CharacterProgressionCore.baseExpForEntity(""));
    assertEquals(10, CharacterProgressionCore.rewardExp(10, 0));
    assertEquals(12, CharacterProgressionCore.rewardExp(10, 1));
    assertEquals(14, CharacterProgressionCore.rewardExp(10, 2));
  }

  @Test public void expOverflowIsPreservedWithoutFullHeal() throws Exception {
    JSONObject profile = new JSONObject()
        .put("explorer", 0).put("exp", 45).put("currentHp", 17).put("maxHp", 50);
    CharacterProgressionCore.applyExp(profile, 10);
    assertEquals(1, profile.getInt("explorer"));
    assertEquals(5, profile.getInt("exp"));
    assertEquals(17, profile.getInt("currentHp"));
    assertEquals(65, profile.getInt("maxHp"));
  }

  @Test public void largeRewardCanAdvanceMultipleExplorerRanks() throws Exception {
    JSONObject profile = new JSONObject()
        .put("explorer", 0).put("exp", 0).put("currentHp", 12).put("maxHp", 50);
    int gained = CharacterProgressionCore.applyExp(profile, 400);
    assertEquals(3, gained);
    assertEquals(3, profile.getInt("explorer"));
    assertEquals(100, profile.getInt("exp"));
    assertEquals(12, profile.getInt("currentHp"));
    assertEquals(95, profile.getInt("maxHp"));
  }

  @Test public void caoMinhDeathHalvesExplorerAndExpAndRespawnsAtNewFullHp() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore(bound -> 0);
    JSONObject state = new JSONObject()
        .put("turn", 20)
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray());
    core.normalizeState(state);

    JSONObject cao_minh = core.profile(state, "cao_minh");
    cao_minh.put("explorer", 10).put("exp", 240).put("currentHp", 0).put("maxHp", 200);

    core.applyCaoMinhDeathPenalty(state);

    assertEquals(5, cao_minh.getInt("explorer"));
    assertEquals(120, cao_minh.getInt("exp"));
    assertEquals(125, cao_minh.getInt("maxHp"));
    assertEquals(125, cao_minh.getInt("currentHp"));
    assertEquals("Ổn định", state.getJSONObject("player").getString("condition"));
  }

  @Test public void caoMinhDeathRoundsOddExplorerDown() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore(bound -> 0);
    JSONObject state = new JSONObject().put("player", new JSONObject()).put("party", new JSONArray());
    core.normalizeState(state);
    JSONObject cao_minh = core.profile(state, "cao_minh");
    cao_minh.put("explorer", 9).put("exp", 101).put("currentHp", 0);

    core.applyCaoMinhDeathPenalty(state);

    assertEquals(4, cao_minh.getInt("explorer"));
    assertEquals(50, cao_minh.getInt("exp"));
  }

  @Test public void downedCompanionRevivesAfterExactlyTenExplorerTurnsAtOneHp() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore(bound -> 0);
    JSONObject state = new JSONObject()
        .put("turn", 4)
        .put("player", new JSONObject().put("name", "Cao Minh"))
        .put("party", new JSONArray().put(
            new JSONObject().put("id", "lucia").put("name", "Lucia Lục").put("joined", true)));
    core.normalizeState(state);

    core.markCompanionDown(state, "lucia");
    assertEquals(0, core.profile(state, "lucia").getInt("currentHp"));

    for (int turn = 5; turn <= 13; turn++) {
      state.put("turn", turn);
      core.applyExplorerTurnRecovery(state);
      assertEquals(0, core.profile(state, "lucia").getInt("currentHp"));
    }

    state.put("turn", 14);
    core.applyExplorerTurnRecovery(state);
    assertEquals(1, core.profile(state, "lucia").getInt("currentHp"));
    assertEquals(1, state.getJSONArray("party").getJSONObject(0).getInt("hp"));
    assertFalse(state.getJSONArray("party").getJSONObject(0).has("reviveTurnsRemaining"));
  }

  @Test public void combatTurnsDoNotAdvanceCompanionReviveTimer() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore(bound -> 0);
    JSONObject state = new JSONObject()
        .put("turn", 7)
        .put("party", new JSONArray().put(
            new JSONObject().put("id", "iris").put("name", "Iris").put("joined", true)));
    core.normalizeState(state);
    core.markCompanionDown(state, "iris");

    for (int i = 0; i < 20; i++) core.applyExplorerTurnRecovery(state);

    assertEquals(0, core.profile(state, "iris").getInt("currentHp"));
    assertEquals(10, state.getJSONArray("party").getJSONObject(0).getInt("reviveTurnsRemaining"));
  }

  @Test public void maxHpFormulaIsFiftyPlusFifteenPerExplorer() {
    assertEquals(50, CharacterProgressionCore.maxHpForExplorer(0));
    assertEquals(65, CharacterProgressionCore.maxHpForExplorer(1));
    assertEquals(95, CharacterProgressionCore.maxHpForExplorer(3));
  }

  @Test public void luciaProfileExistsBeforeEncounterAndIsNotPartyMembership() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore(bound -> 0);
    JSONObject state = new JSONObject().put("party", new JSONArray());
    core.normalizeState(state);
    assertEquals("lucia", core.profile(state, "lucia").getString("id"));
    assertEquals(0, state.getJSONArray("party").length());
  }

  @Test public void normalizationRemovesLegacyEquipmentState() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore(bound -> 0);
    JSONObject state = new JSONObject()
        .put("player", new JSONObject().put("name", "Cao Minh")
            .put("equipment", new JSONArray().put("legacy")))
        .put("party", new JSONArray().put(new JSONObject()
            .put("id", "lucia").put("name", "Lucia Lục").put("joined", true)
            .put("equipment", new JSONArray().put("legacy"))))
        .put("partyDetails", new JSONObject().put("members", new JSONArray().put(
            new JSONObject().put("id", "cao_minh").put("equipment", new JSONArray().put("legacy")))))
        .put("equipment", new JSONObject().put("legacy", true));

    core.normalizeState(state);
    core.profile(state, "cao_minh").put("equipment", new JSONArray().put("legacy"));
    core.normalizeState(state);

    assertFalse(state.has("equipment"));
    assertFalse(state.getJSONObject("player").has("equipment"));
    assertFalse(state.getJSONArray("party").getJSONObject(0).has("equipment"));
    assertFalse(state.getJSONObject("partyDetails").getJSONArray("members")
        .getJSONObject(0).has("equipment"));
    assertFalse(core.profile(state, "cao_minh").has("equipment"));
  }

  @Test public void candidateCannotMutateBaseExplorerOrExp() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore(bound -> 0);
    JSONObject before = new JSONObject();
    core.normalizeState(before);
    JSONObject expected = new JSONObject(core.profile(before, "cao_minh").toString());

    JSONObject candidate = new JSONObject(before.toString());
    JSONObject forged = candidate.getJSONObject("characterProgression")
        .getJSONObject("characters").getJSONObject("cao_minh");
    forged.getJSONObject("baseStats").put("STR", 999);
    forged.put("explorer", 99).put("exp", 9999);
    candidate.put("characterRpg", new JSONObject().put("characters", new JSONObject()));
    candidate.put("player", new JSONObject().put("explorer", 99).put("exp", 99));

    core.protectFromCandidate(before, candidate);
    JSONObject actual = candidate.getJSONObject("characterProgression")
        .getJSONObject("characters").getJSONObject("cao_minh");
    assertEquals(expected.getJSONObject("baseStats").toString(),
        actual.getJSONObject("baseStats").toString());
    assertEquals(expected.getInt("explorer"), actual.getInt("explorer"));
    assertEquals(expected.getInt("exp"), actual.getInt("exp"));
    assertFalse(candidate.has("characterRpg"));
    assertFalse(candidate.getJSONObject("player").has("explorer"));
    assertFalse(candidate.getJSONObject("player").has("exp"));
  }

  private static final class CountingRng implements CharacterProgressionCore.IntRng {
    int calls;
    @Override public int nextInt(int bound) {
      calls++;
      return calls % bound;
    }
  }
}
