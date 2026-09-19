package com.rabpit.backroom.core;

import org.json.JSONObject;

/**
 * Read-only character stat projection.
 *
 * Effective STR/DF/AGI/CRIT = Base + Explorer growth + Equipment. Explorer stat growth and
 * equipment bonuses are intentionally zero until their formulas are explicitly approved.
 */
final class CharacterStatCore {
  static final int EXPLORER_STAT_GROWTH = 0;

  JSONObject project(JSONObject state, String characterId, CharacterProgressionCore progressionCore,
                     EquipmentStatCore equipmentCore) throws Exception {
    JSONObject profile = progressionCore.profile(state, characterId);
    JSONObject base = profile.getJSONObject("baseStats");
    EquipmentStatCore.Bonus equipment = equipmentCore.aggregate(state, characterId);

    JSONObject stats = new JSONObject()
        .put("STR", line(base.optInt("STR", CharacterProgressionCore.BASE_STAT), 0, equipment.str))
        .put("DF", line(base.optInt("DF", CharacterProgressionCore.BASE_STAT), 0, equipment.df))
        .put("AGI", line(base.optInt("AGI", CharacterProgressionCore.BASE_STAT), 0, equipment.agi))
        .put("CRIT", line(base.optInt("CRIT", CharacterProgressionCore.BASE_STAT), 0, equipment.crit));

    int explorer = profile.optInt("explorer", 0);
    int exp = profile.optInt("exp", 0);
    return new JSONObject()
        .put("explorer", explorer)
        .put("exp", exp)
        .put("requiredExp", CharacterProgressionCore.requiredExp(explorer))
        .put("currentHp", profile.optInt("currentHp", CharacterProgressionCore.BASE_MAX_HP))
        .put("maxHp", profile.optInt("maxHp", CharacterProgressionCore.maxHpForExplorer(explorer)))
        .put("stats", stats)
        .put("equipment", equipmentCore.projection(state, characterId))
        .put("source", CharacterProgressionCore.ROOT_KEY);
  }

  private JSONObject line(int base, int explorerGrowth, int equipment) throws Exception {
    return new JSONObject()
        .put("base", base)
        .put("explorer", explorerGrowth)
        .put("equipment", equipment)
        .put("effective", base + explorerGrowth + equipment);
  }
}
