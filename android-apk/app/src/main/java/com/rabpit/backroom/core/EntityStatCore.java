package com.rabpit.backroom.core;

import org.json.JSONObject;

/**
 * Independent Entity stat reference. CombatChoiceEngine keeps its legacy HP/attack/defense adapter
 * until STR/DF/AGI/CRIT combat formulas are explicitly approved.
 */
final class EntityStatCore {
  static final int LUCIA_REFERENCE_PERCENT = 93;

  JSONObject profile(JSONObject state, String entityKey, CharacterProgressionCore progressionCore)
      throws Exception {
    JSONObject modifier = new JSONObject()
        .put("STR", 0)
        .put("DF", 0)
        .put("AGI", 0)
        .put("CRIT", 0)
        .put("maxHp", 0);
    return new JSONObject()
        .put("entityKey", entityKey == null ? "" : entityKey)
        .put("reference", "luciaBaseStats")
        .put("baseline", baseline(state, progressionCore))
        .put("modifier", modifier);
  }

  JSONObject baseline(JSONObject state, CharacterProgressionCore progressionCore) throws Exception {
    JSONObject lucia = progressionCore.profile(state, "lucia");
    JSONObject base = lucia.getJSONObject("baseStats");
    return new JSONObject()
        .put("STR", scaleFromLuciaBase(base.optInt("STR", CharacterProgressionCore.BASE_STAT)))
        .put("DF", scaleFromLuciaBase(base.optInt("DF", CharacterProgressionCore.BASE_STAT)))
        .put("AGI", scaleFromLuciaBase(base.optInt("AGI", CharacterProgressionCore.BASE_STAT)))
        .put("CRIT", scaleFromLuciaBase(base.optInt("CRIT", CharacterProgressionCore.BASE_STAT)))
        .put("maxHp", scaleFromLuciaBase(CharacterProgressionCore.BASE_MAX_HP));
  }

  static int scaleFromLuciaBase(int value) {
    double scaled = Math.max(0, value) * (LUCIA_REFERENCE_PERCENT / 100.0d);
    int floor = (int)Math.floor(scaled);
    double fraction = scaled - floor;
    return fraction < 0.5d ? floor : (int)Math.ceil(scaled);
  }
}
