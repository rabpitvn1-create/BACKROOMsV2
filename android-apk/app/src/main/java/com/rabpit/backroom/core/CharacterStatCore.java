package com.rabpit.backroom.core;

import org.json.JSONObject;

/** Read-only projection of the four authoritative Poker Dice combat stats. */
final class CharacterStatCore {
  JSONObject project(JSONObject state, String characterId, CharacterProgressionCore progressionCore)
      throws Exception {
    JSONObject profile = progressionCore.profile(state, characterId);
    JSONObject source = profile.getJSONObject("stats");

    JSONObject stats = new JSONObject()
        .put("STR", line(source.optInt("STR", CharacterProgressionCore.BASE_STAT)))
        .put("DEF", line(source.optInt("DEF", CharacterProgressionCore.BASE_STAT)))
        .put("SKL", line(source.optInt("SKL", CharacterProgressionCore.BASE_STAT)))
        .put("VIT", line(source.optInt("VIT", CharacterProgressionCore.BASE_STAT)));

    return new JSONObject()
        .put("currentHp", profile.getInt("currentHp"))
        .put("maxHp", profile.getInt("maxHp"))
        .put("baseMaxHp", profile.getInt("baseMaxHp"))
        .put("stats", stats)
        .put("source", CharacterProgressionCore.ROOT_KEY);
  }

  private JSONObject line(int value) throws Exception {
    int normalized = Math.max(CharacterProgressionCore.BASE_STAT, value);
    return new JSONObject()
        .put("base", normalized)
        .put("effective", normalized)
        .put("nextCoreCost", CharacterProgressionCore.upgradeCost(normalized));
  }
}
