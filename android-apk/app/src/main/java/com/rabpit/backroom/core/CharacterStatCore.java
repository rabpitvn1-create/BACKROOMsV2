package com.rabpit.backroom.core;

import org.json.JSONObject;

/**
 * Independent character RPG stat projection.
 *
 * This subsystem does not drive CombatChoiceEngine. It exists for character progression/status UI
 * and can be integrated into combat later through an explicit adapter rather than silently changing
 * the current damage model.
 */
final class CharacterStatCore {
  static final int KAI_BASE_MAX_HP = 100;
  static final int KAI_BASE_STR = 82;
  static final int KAI_BASE_DF = 78;
  static final int KAI_BASE_AGI = 92;
  static final int KAI_BASE_CRIT = 95;
  static final int KAI_HP_REGEN_PER_COMPLETED_TURN = 4;
  static final String KAI_ENERGY_DISPLAY = "∞";

  JSONObject project(JSONObject state, String characterId, CharacterLevelCore levelCore,
                     EquipmentStatCore equipmentCore) throws Exception {
    if (!CharacterLevelCore.KAI_ID.equals(characterId)) return new JSONObject();

    int level = levelCore.levelFor(state, characterId);
    EquipmentStatCore.Bonus equipment = equipmentCore.aggregate(state, characterId);

    // No level-growth formula exists yet. Keep an explicit zero levelBonus so the projection schema
    // is stable when progression rules are added later.
    JSONObject stats = new JSONObject()
        .put("STR", line(KAI_BASE_STR, 0, equipment.str))
        .put("DF", line(KAI_BASE_DF, 0, equipment.df))
        .put("AGI", line(KAI_BASE_AGI, 0, equipment.agi))
        .put("CRIT", line(KAI_BASE_CRIT, 0, equipment.crit));

    return new JSONObject()
        .put("level", level)
        .put("baseMaxHp", KAI_BASE_MAX_HP)
        .put("energy", KAI_ENERGY_DISPLAY)
        .put("hpRegen", KAI_HP_REGEN_PER_COMPLETED_TURN)
        .put("stats", stats)
        .put("equipment", equipmentCore.projection(state, characterId))
        .put("levelGrowthConfigured", false)
        .put("source", "gameplay_normalized_v2");
  }

  private JSONObject line(int base, int levelBonus, int equipment) throws Exception {
    return new JSONObject()
        .put("base", base)
        .put("levelBonus", levelBonus)
        .put("equipment", equipment)
        .put("effective", base + levelBonus + equipment);
  }
}
