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
    for (String id : new String[]{"kai", "iris", "syvial"}) {
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
    JSONObject kai = core.profile(state, "kai");
    assertEquals(50, kai.getInt("currentHp"));
    assertEquals(50, kai.getInt("maxHp"));
    assertEquals(0, kai.getInt("explorer"));
    assertEquals(0, kai.getInt("exp"));
  }

  @Test public void explorerRequirementsAreFiftyPerNextExplorerIndex() {
    assertEquals(50, CharacterProgressionCore.requiredExp(0));
    assertEquals(100, CharacterProgressionCore.requiredExp(1));
    assertEquals(150, CharacterProgressionCore.requiredExp(2));
  }

  @Test public void houndBaseAndExplorerScaledExpAreCoreOwned() {
    assertEquals(10, CharacterProgressionCore.baseExpForEntity("hound"));
    assertEquals(0, CharacterProgressionCore.baseExpForEntity("smiler"));
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

  @Test public void candidateCannotMutateBaseExplorerOrExp() throws Exception {
    CharacterProgressionCore core = new CharacterProgressionCore(bound -> 0);
    JSONObject before = new JSONObject();
    core.normalizeState(before);
    JSONObject expected = new JSONObject(core.profile(before, "kai").toString());

    JSONObject candidate = new JSONObject(before.toString());
    JSONObject forged = candidate.getJSONObject("characterProgression")
        .getJSONObject("characters").getJSONObject("kai");
    forged.getJSONObject("baseStats").put("STR", 999);
    forged.put("explorer", 99).put("exp", 9999);
    candidate.put("characterRpg", new JSONObject().put("characters", new JSONObject()));
    candidate.put("player", new JSONObject().put("explorer", 99).put("exp", 99));

    core.protectFromCandidate(before, candidate);
    JSONObject actual = candidate.getJSONObject("characterProgression")
        .getJSONObject("characters").getJSONObject("kai");
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
