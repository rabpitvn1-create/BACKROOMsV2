package com.rabpit.backroom.core;

import org.json.JSONObject;

/** Read-only projection of authoritative Poker Dice combat stats and derived combat status. */
final class CharacterStatCore {
  static final int BASE_CRITICAL_PERCENT = 5;
  static final int CRITICAL_PER_SKL = 2;
  static final int MAX_CRITICAL_PERCENT = 50;
  static final int EVASION_PER_VIT = 2;
  static final int MAX_EVASION_PERCENT = 35;
  static final int RESIST_PER_STAT = 2;
  static final int MAX_RESIST_PERCENT = 50;

  JSONObject project(JSONObject state, String characterId, CharacterProgressionCore progressionCore)
      throws Exception {
    return project(state, null, characterId, progressionCore);
  }

  JSONObject project(JSONObject state, JSONObject runtimeSource, String characterId,
                     CharacterProgressionCore progressionCore) throws Exception {
    JSONObject profile = progressionCore.profile(state, characterId);
    JSONObject source = profile.getJSONObject("stats");

    int str = source.optInt("STR", CharacterProgressionCore.BASE_STAT);
    int def = source.optInt("DEF", CharacterProgressionCore.BASE_STAT);
    int skl = source.optInt("SKL", CharacterProgressionCore.BASE_STAT);
    int vit = source.optInt("VIT", CharacterProgressionCore.BASE_STAT);

    JSONObject stats = new JSONObject()
        .put("STR", line(str))
        .put("DEF", line(def))
        .put("SKL", line(skl))
        .put("VIT", line(vit));

    int baseAttack = CombatChoiceEngine.baseAttackFor(runtimeSource, characterId);
    JSONObject combatStatus = new JSONObject()
        .put("damage", CombatChoiceEngine.basicDamage(baseAttack, str, 100))
        .put("defendPercent", defendPercent(def))
        .put("criticalChancePercent", criticalChancePercent(skl))
        .put("evasionPercent", evasionPercent(vit))
        .put("resCriticalPercent", criticalResistancePercent(def))
        .put("resEvasionPercent", evasionResistancePercent(skl));

    return new JSONObject()
        .put("currentHp", profile.getInt("currentHp"))
        .put("maxHp", profile.getInt("maxHp"))
        .put("baseMaxHp", profile.getInt("baseMaxHp"))
        .put("stats", stats)
        .put("combatStatus", combatStatus)
        .put("source", CharacterProgressionCore.ROOT_KEY);
  }

  static int criticalChancePercent(int skl) {
    return Math.min(MAX_CRITICAL_PERCENT,
        BASE_CRITICAL_PERCENT + Math.max(0, skl - CharacterProgressionCore.BASE_STAT)
            * CRITICAL_PER_SKL);
  }

  static int evasionPercent(int vit) {
    return Math.min(MAX_EVASION_PERCENT,
        Math.max(0, vit - CharacterProgressionCore.BASE_STAT) * EVASION_PER_VIT);
  }

  static int criticalResistancePercent(int def) {
    return Math.min(MAX_RESIST_PERCENT,
        Math.max(0, def - CharacterProgressionCore.BASE_STAT) * RESIST_PER_STAT);
  }

  static int evasionResistancePercent(int skl) {
    return Math.min(MAX_RESIST_PERCENT,
        Math.max(0, skl - CharacterProgressionCore.BASE_STAT) * RESIST_PER_STAT);
  }

  static double defendPercent(int def) {
    int multiplier = CharacterProgressionCore.statPercent(def);
    double percent = 100.0d - (10_000.0d / Math.max(100, multiplier));
    return Math.max(0.0d, Math.round(percent * 10.0d) / 10.0d);
  }

  private JSONObject line(int value) throws Exception {
    int normalized = Math.max(CharacterProgressionCore.BASE_STAT, value);
    return new JSONObject()
        .put("base", normalized)
        .put("effective", normalized)
        .put("nextCoreCost", CharacterProgressionCore.upgradeCost(normalized));
  }
}
