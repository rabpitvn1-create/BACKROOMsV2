package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Core-owned RPG equipment-stat catalog.
 *
 * These are gameplay-normalized numbers, not canon power claims. The current V2 loadout maps the
 * former BACKROOMS equipment contribution onto the current MK19/MK20/Omnivault loadout so the old
 * normalized effective STR/DF/AGI/CRIT totals are preserved without touching CombatChoiceEngine.
 */
final class EquipmentStatCore {
  static final String MK19_ID = "sru-assault-rifle-mk19";
  static final String MK20_ID = "sru-mk20-powered-armor";
  static final String OMNIVAULT_ID = "omnivault-ring";

  static final int MK19_CRIT = 8;
  static final int MK20_STR = 25;
  static final int MK20_DF = 31;
  static final int MK20_AGI = 20;
  static final int MK20_CRIT = 6;

  static final class Bonus {
    final int str;
    final int df;
    final int agi;
    final int crit;

    Bonus(int str, int df, int agi, int crit) {
      this.str = str;
      this.df = df;
      this.agi = agi;
      this.crit = crit;
    }

    Bonus plus(Bonus other) {
      return new Bonus(str + other.str, df + other.df, agi + other.agi, crit + other.crit);
    }

    JSONObject toJson() throws Exception {
      return new JSONObject()
          .put("STR", str)
          .put("DF", df)
          .put("AGI", agi)
          .put("CRIT", crit);
    }
  }

  private static final class Definition {
    final String id;
    final String name;
    final Bonus bonus;

    Definition(String id, String name, Bonus bonus) {
      this.id = id;
      this.name = name;
      this.bonus = bonus;
    }
  }

  private final Map<String, Definition> definitions = new LinkedHashMap<>();

  EquipmentStatCore() {
    add(new Definition(MK19_ID, "SRU Assault Rifle MK19", new Bonus(0, 0, 0, MK19_CRIT)));
    add(new Definition(MK20_ID, "SRU-MK20 Powered Armor", new Bonus(MK20_STR, MK20_DF, MK20_AGI, MK20_CRIT)));
    add(new Definition(OMNIVAULT_ID, "Omnivault Ring / Nhẫn Vạn Tàng", new Bonus(0, 0, 0, 0)));
  }

  private void add(Definition definition) {
    definitions.put(definition.id, definition);
  }

  void normalizeState(JSONObject state) throws Exception {
    if (state == null) return;
    JSONObject root = state.optJSONObject(CharacterLevelCore.ROOT_KEY);
    if (root == null) root = new JSONObject();
    JSONObject characters = root.optJSONObject(CharacterLevelCore.CHARACTERS_KEY);
    if (characters == null) characters = new JSONObject();
    JSONObject kai = characters.optJSONObject(CharacterLevelCore.KAI_ID);
    if (kai == null) kai = new JSONObject().put("level", CharacterLevelCore.DEFAULT_LEVEL);

    if (!kai.has("equipment") || !(kai.opt("equipment") instanceof JSONArray)) {
      kai.put("equipment", defaultKaiLoadout());
    } else {
      kai.put("equipment", normalizeEquipmentIds(kai.optJSONArray("equipment")));
    }

    characters.put(CharacterLevelCore.KAI_ID, kai);
    root.put(CharacterLevelCore.CHARACTERS_KEY, characters);
    state.put(CharacterLevelCore.ROOT_KEY, root);
  }

  Bonus aggregate(JSONObject state, String characterId) {
    Bonus total = new Bonus(0, 0, 0, 0);
    JSONArray ids = equipmentIds(state, characterId);
    for (int i = 0; i < ids.length(); i++) {
      Definition definition = definitions.get(ids.optString(i, ""));
      if (definition != null) total = total.plus(definition.bonus);
    }
    return total;
  }

  JSONArray projection(JSONObject state, String characterId) throws Exception {
    JSONArray output = new JSONArray();
    JSONArray ids = equipmentIds(state, characterId);
    for (int i = 0; i < ids.length(); i++) {
      Definition definition = definitions.get(ids.optString(i, ""));
      if (definition == null) continue;
      output.put(new JSONObject()
          .put("id", definition.id)
          .put("name", definition.name)
          .put("stats", definition.bonus.toJson()));
    }
    return output;
  }

  private JSONArray equipmentIds(JSONObject state, String characterId) {
    JSONObject root = state == null ? null : state.optJSONObject(CharacterLevelCore.ROOT_KEY);
    JSONObject characters = root == null ? null : root.optJSONObject(CharacterLevelCore.CHARACTERS_KEY);
    JSONObject character = characters == null ? null : characters.optJSONObject(characterId);
    JSONArray ids = character == null ? null : character.optJSONArray("equipment");
    if (ids != null) return ids;
    return CharacterLevelCore.KAI_ID.equals(characterId) ? defaultKaiLoadout() : new JSONArray();
  }

  private JSONArray defaultKaiLoadout() {
    return new JSONArray().put(MK19_ID).put(MK20_ID).put(OMNIVAULT_ID);
  }

  private JSONArray normalizeEquipmentIds(JSONArray input) {
    JSONArray output = new JSONArray();
    if (input == null) return output;
    Set<String> seen = new LinkedHashSet<>();
    for (int i = 0; i < input.length(); i++) {
      String id = input.optString(i, "").trim();
      if (id.isEmpty() || !definitions.containsKey(id) || !seen.add(id)) continue;
      output.put(id);
    }
    return output;
  }
}
