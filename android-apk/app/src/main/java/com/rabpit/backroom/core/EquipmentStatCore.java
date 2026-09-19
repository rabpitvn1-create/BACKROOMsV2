package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/** Equipment catalog/stat layer. Official stat bonuses are not configured yet. */
final class EquipmentStatCore {
  static final String MK19_ID = "sru-assault-rifle-mk19";
  static final String MK20_ID = "sru-mk20-powered-armor";
  static final String OMNIVAULT_ID = "omnivault-ring";

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

  private static final Bonus ZERO = new Bonus(0, 0, 0, 0);

  private static final class Definition {
    final String id;
    final String name;
    final Bonus bonus;

    Definition(String id, String name) {
      this.id = id;
      this.name = name;
      this.bonus = ZERO;
    }
  }

  private final Map<String, Definition> definitions = new LinkedHashMap<>();

  EquipmentStatCore() {
    add(new Definition(MK19_ID, "SRU Assault Rifle MK19"));
    add(new Definition(MK20_ID, "SRU-MK20 Powered Armor"));
    add(new Definition(OMNIVAULT_ID, "Omnivault Ring / Nhẫn Vạn Tàng"));
  }

  Bonus aggregate(JSONObject state, String characterId) {
    Bonus total = ZERO;
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

  private JSONArray equipmentIds(JSONObject state, String rawCharacterId) {
    String characterId = CharacterProgressionCore.normalizeCharacterId(rawCharacterId);
    JSONObject root = state == null ? null : state.optJSONObject(CharacterProgressionCore.ROOT_KEY);
    JSONObject characters = root == null ? null : root.optJSONObject(CharacterProgressionCore.CHARACTERS_KEY);
    JSONObject profile = characters == null ? null : characters.optJSONObject(characterId);
    JSONArray stored = profile == null ? null : profile.optJSONArray("equipment");
    if (stored != null) return stored;
    if ("kai".equals(characterId)) {
      return new JSONArray().put(MK19_ID).put(MK20_ID).put(OMNIVAULT_ID);
    }
    return new JSONArray();
  }

  private void add(Definition definition) {
    definitions.put(definition.id, definition);
  }
}
