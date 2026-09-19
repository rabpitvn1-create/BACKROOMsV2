package com.rabpit.backroom.core;

import org.json.JSONObject;

/** Read-only character stat projection. */
final class CharacterStatCore {
  static final int EXPLORER_STAT_GROWTH = 0;

  JSONObject project(JSONObject state, String characterId, CharacterProgressionCore progressionCore)
      throws Exception {
    JSONObject profile = progressionCore.profile(state, characterId);
    JSONObject base = profile.getJSONObject("baseStats");

    JSONObject stats = new JSONObject()
        .put("STR", line(base.optInt("STR", CharacterProgressionCore.BASE_STAT), 0))
        .put("DF", line(base.optInt("DF", CharacterProgressionCore.BASE_STAT), 0))
        .put("AGI", line(base.optInt("AGI", CharacterProgressionCore.BASE_STAT), 0))
        .put("CRIT", line(base.optInt("CRIT", CharacterProgressionCore.BASE_STAT), 0));

    int explorer = profile.optInt("explorer", 0);
    int exp = profile.optInt("exp", 0);
    return new JSONObject()
        .put("explorer", explorer)
        .put("exp", exp)
        .put("requiredExp", CharacterProgressionCore.requiredExp(explorer))
        .put("currentHp", profile.optInt("currentHp", CharacterProgressionCore.BASE_MAX_HP))
        .put("maxHp", profile.optInt("maxHp", CharacterProgressionCore.maxHpForExplorer(explorer)))
        .put("stats", stats)
        .put("source", CharacterProgressionCore.ROOT_KEY);
  }

  private JSONObject line(int base, int explorerGrowth) throws Exception {
    return new JSONObject()
        .put("base", base)
        .put("explorer", explorerGrowth)
        .put("effective", base + explorerGrowth);
  }
}
