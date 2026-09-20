package com.rabpit.backroom.core;

import org.json.JSONObject;

/**
 * Entity stat reference derived from Lucia. Combat uses the scaled HP immediately while
 * attack/defense keep their existing species values until STR/DF/AGI/CRIT formulas are approved.
 */
final class EntityStatCore {
  static final int LUCIA_REFERENCE_PERCENT = 93;

  JSONObject profile(JSONObject state, String entityKey, CharacterProgressionCore progressionCore)
      throws Exception {
    JSONObject baseline = baseline(state, progressionCore);
    JSONObject modifier = new JSONObject()
        .put("STR", 0)
        .put("DF", 0)
        .put("AGI", 0)
        .put("CRIT", 0)
        .put("maxHp", 0);
    return new JSONObject()
        .put("entityKey", entityKey == null ? "" : entityKey)
        .put("reference", "luciaProgression")
        .put("baseline", baseline)
        .put("modifier", modifier)
        .put("effective", new JSONObject(baseline.toString()));
  }

  JSONObject profile(JSONObject state, String entityKey, CharacterProgressionCore progressionCore,
                     int referenceMaxHp) throws Exception {
    JSONObject baseline = baseline(state, progressionCore);
    int scaledMaxHp = scaleSpeciesMaxHp(referenceMaxHp, baseline.getInt("maxHp"));
    JSONObject modifier = new JSONObject()
        .put("STR", 0)
        .put("DF", 0)
        .put("AGI", 0)
        .put("CRIT", 0)
        .put("maxHp", scaledMaxHp - baseline.getInt("maxHp"));
    JSONObject effective = new JSONObject(baseline.toString()).put("maxHp", scaledMaxHp);
    return new JSONObject()
        .put("entityKey", entityKey == null ? "" : entityKey)
        .put("reference", "luciaProgression")
        .put("baseline", baseline)
        .put("modifier", modifier)
        .put("effective", effective);
  }

  JSONObject baseline(JSONObject state, CharacterProgressionCore progressionCore) throws Exception {
    JSONObject lucia = progressionCore.profile(state, "lucia");
    JSONObject base = lucia.getJSONObject("baseStats");
    return new JSONObject()
        .put("STR", scaleFromLuciaBase(base.optInt("STR", CharacterProgressionCore.BASE_STAT)))
        .put("DF", scaleFromLuciaBase(base.optInt("DF", CharacterProgressionCore.BASE_STAT)))
        .put("AGI", scaleFromLuciaBase(base.optInt("AGI", CharacterProgressionCore.BASE_STAT)))
        .put("CRIT", scaleFromLuciaBase(base.optInt("CRIT", CharacterProgressionCore.BASE_STAT)))
        .put("maxHp", scaleFromLuciaBase(lucia.optInt("maxHp", CharacterProgressionCore.BASE_MAX_HP)));
  }

  static int scaleSpeciesMaxHp(int referenceMaxHp, int currentLuciaBaselineHp) {
    int referenceLuciaBaselineHp = scaleFromLuciaBase(CharacterProgressionCore.BASE_MAX_HP);
    if (referenceLuciaBaselineHp <= 0) return Math.max(1, referenceMaxHp);
    double ratio = Math.max(1, currentLuciaBaselineHp) / (double) referenceLuciaBaselineHp;
    return Math.max(1, (int)Math.round(Math.max(1, referenceMaxHp) * ratio));
  }

  static int scaleFromLuciaBase(int value) {
    double scaled = Math.max(0, value) * (LUCIA_REFERENCE_PERCENT / 100.0d);
    int floor = (int)Math.floor(scaled);
    double fraction = scaled - floor;
    return fraction < 0.5d ? floor : (int)Math.ceil(scaled);
  }
}
