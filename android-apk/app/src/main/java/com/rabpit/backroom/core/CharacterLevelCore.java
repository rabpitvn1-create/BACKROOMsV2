package com.rabpit.backroom.core;

import org.json.JSONObject;

/**
 * Character RPG level state, intentionally separate from Backrooms LevelCore.
 *
 * No XP curve or automatic level-up is defined yet. Level 1 is the safe baseline until a
 * progression rule is explicitly approved.
 */
final class CharacterLevelCore {
  static final String ROOT_KEY = "characterRpg";
  static final String CHARACTERS_KEY = "characters";
  static final String KAI_ID = "kai";
  static final int DEFAULT_LEVEL = 1;

  void normalizeState(JSONObject state) throws Exception {
    if (state == null) return;
    JSONObject root = state.optJSONObject(ROOT_KEY);
    if (root == null) root = new JSONObject();
    JSONObject characters = root.optJSONObject(CHARACTERS_KEY);
    if (characters == null) characters = new JSONObject();
    JSONObject kai = characters.optJSONObject(KAI_ID);
    if (kai == null) kai = new JSONObject();
    kai.put("level", Math.max(DEFAULT_LEVEL, kai.optInt("level", DEFAULT_LEVEL)));
    characters.put(KAI_ID, kai);
    root.put(CHARACTERS_KEY, characters);
    state.put(ROOT_KEY, root);
  }

  int levelFor(JSONObject state, String characterId) {
    if (state == null || characterId == null) return DEFAULT_LEVEL;
    JSONObject root = state.optJSONObject(ROOT_KEY);
    JSONObject characters = root == null ? null : root.optJSONObject(CHARACTERS_KEY);
    JSONObject character = characters == null ? null : characters.optJSONObject(characterId);
    return Math.max(DEFAULT_LEVEL, character == null ? DEFAULT_LEVEL : character.optInt("level", DEFAULT_LEVEL));
  }
}
