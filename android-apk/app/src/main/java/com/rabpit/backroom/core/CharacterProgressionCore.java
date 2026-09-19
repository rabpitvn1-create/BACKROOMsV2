package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Core-owned character progression. Character Level does not exist in this system. */
final class CharacterProgressionCore {
  static final String ROOT_KEY = "characterProgression";
  static final String CHARACTERS_KEY = "characters";
  static final String LEGACY_ROOT_KEY = "characterRpg";

  static final int BASE_STAT = 5;
  static final int FEATURED_BONUS_POOL = 20;
  static final int DEFAULT_BONUS_POOL = 10;
  static final int BASE_MAX_HP = 50;
  static final int HP_PER_EXPLORER = 15;
  static final int HOUND_BASE_EXP = 10;
  static final int COMPANION_REVIVE_EXPLORER_TURNS = 10;

  interface IntRng {
    int nextInt(int bound);
  }

  private final IntRng rng;

  CharacterProgressionCore() {
    this(bound -> ThreadLocalRandom.current().nextInt(bound));
  }

  CharacterProgressionCore(IntRng rng) {
    if (rng == null) throw new IllegalArgumentException("rng is required");
    this.rng = rng;
  }

  void normalizeState(JSONObject state) throws Exception {
    if (state == null) return;
    JSONObject root = state.optJSONObject(ROOT_KEY);
    if (root == null) root = new JSONObject();
    JSONObject characters = root.optJSONObject(CHARACTERS_KEY);
    if (characters == null) characters = new JSONObject();

    ensureProfileObject(characters, "kai");
    ensureProfileObject(characters, "lucia");
    ensureProfileObject(characters, "iris");
    ensureProfileObject(characters, "syvial");

    root.put(CHARACTERS_KEY, characters);
    state.put(ROOT_KEY, root);
    state.remove(LEGACY_ROOT_KEY);
  }

  JSONObject ensureProfile(JSONObject state, String rawId) throws Exception {
    if (state == null) throw new IllegalArgumentException("state is required");
    normalizeState(state);
    String id = normalizeCharacterId(rawId);
    if (id.isEmpty()) throw new IllegalArgumentException("character id is required");
    JSONObject characters = state.getJSONObject(ROOT_KEY).getJSONObject(CHARACTERS_KEY);
    return ensureProfileObject(characters, id);
  }

  JSONObject profile(JSONObject state, String rawId) throws Exception {
    return ensureProfile(state, rawId);
  }

  JSONObject generateBaseStats(int bonusPool) throws Exception {
    JSONObject stats = new JSONObject()
        .put("STR", BASE_STAT)
        .put("DF", BASE_STAT)
        .put("AGI", BASE_STAT)
        .put("CRIT", BASE_STAT);
    String[] keys = {"STR", "DF", "AGI", "CRIT"};
    for (int i = 0; i < Math.max(0, bonusPool); i++) {
      int roll = rng.nextInt(keys.length);
      if (roll < 0 || roll >= keys.length) {
        throw new IllegalStateException("RNG returned an out-of-range value");
      }
      String key = keys[roll];
      stats.put(key, stats.getInt(key) + 1);
    }
    return stats;
  }

  static int requiredExp(int explorer) {
    return 50 * (Math.max(0, explorer) + 1);
  }

  static int maxHpForExplorer(int explorer) {
    return BASE_MAX_HP + Math.max(0, explorer) * HP_PER_EXPLORER;
  }

  static int baseExpForEntity(String entityKey) {
    return "hound".equals(normalizeEntityKey(entityKey)) ? HOUND_BASE_EXP : 0;
  }

  static int rewardExp(int baseExp, int explorer) {
    if (baseExp <= 0) return 0;
    return (int)Math.round(baseExp * (1.0d + 0.20d * Math.max(0, explorer)));
  }

  static int applyExp(JSONObject profile, int reward) throws Exception {
    if (profile == null || reward <= 0) return 0;
    int explorer = Math.max(0, profile.optInt("explorer", 0));
    int exp = Math.max(0, profile.optInt("exp", 0)) + reward;
    int gained = 0;
    while (exp >= requiredExp(explorer)) {
      exp -= requiredExp(explorer);
      explorer++;
      gained++;
    }
    profile.put("explorer", explorer);
    profile.put("exp", exp);
    int maxHp = maxHpForExplorer(explorer);
    int currentHp = Math.max(0, Math.min(profile.optInt("currentHp", BASE_MAX_HP), maxHp));
    profile.put("currentHp", currentHp);
    profile.put("maxHp", maxHp);
    return gained;
  }

  void grantEntityKillExp(JSONObject state, String entityKey, JSONArray participants, JSONObject combat)
      throws Exception {
    if (state == null) return;
    if (combat != null && combat.optBoolean("expResolved", false)) return;
    normalizeState(state);

    int baseExp = baseExpForEntity(entityKey);
    JSONArray awards = new JSONArray();
    Set<String> seen = new LinkedHashSet<>();
    if (participants != null) {
      for (int i = 0; i < participants.length(); i++) {
        JSONObject participant = participants.optJSONObject(i);
        if (participant == null) continue;
        String id = normalizeCharacterId(
            participant.optString("id", participant.optString("name", "")));
        if (id.isEmpty() || !seen.add(id)) continue;
        JSONObject profile = ensureProfile(state, id);
        int explorerBefore = Math.max(0, profile.optInt("explorer", 0));
        int reward = rewardExp(baseExp, explorerBefore);
        int gained = applyExp(profile, reward);
        awards.put(new JSONObject()
            .put("id", id)
            .put("rewardExp", reward)
            .put("explorerBefore", explorerBefore)
            .put("explorerAfter", profile.getInt("explorer"))
            .put("explorerGained", gained)
            .put("expAfter", profile.getInt("exp")));
      }
    }
    if (combat != null) {
      combat.put("baseExp", baseExp);
      combat.put("expAwards", awards);
      combat.put("expResolved", true);
    }
  }

  void applyKaiDeathPenalty(JSONObject state) throws Exception {
    normalizeState(state);
    JSONObject kai = profile(state, "kai");
    int explorer = Math.max(0, kai.optInt("explorer", 0));
    int exp = Math.max(0, kai.optInt("exp", 0));
    int reducedExplorer = explorer / 2;
    int reducedExp = exp / 2;
    int maxHp = maxHpForExplorer(reducedExplorer);

    kai.put("explorer", reducedExplorer);
    kai.put("exp", reducedExp);
    kai.put("maxHp", maxHp);
    kai.put("currentHp", maxHp);
    kai.remove("downedAtTurn");
    kai.remove("reviveAtTurn");

    JSONObject player = state.optJSONObject("player");
    if (player != null) {
      player.put("hp", maxHp);
      player.put("maxHp", maxHp);
      player.put("condition", "Ổn định");
    }
  }

  void markCompanionDown(JSONObject state, String rawId) throws Exception {
    normalizeState(state);
    String id = normalizeCharacterId(rawId);
    if (id.isEmpty() || "kai".equals(id)) return;

    JSONObject profile = profile(state, id);
    profile.put("currentHp", 0);
    int turn = Math.max(1, state.optInt("turn", 1));
    if (!profile.has("reviveAtTurn")) {
      profile.put("downedAtTurn", turn);
      profile.put("reviveAtTurn", turn + COMPANION_REVIVE_EXPLORER_TURNS);
    }
    syncPartyRecoveryState(state, id, profile, turn);
  }

  void applyExplorerTurnRecovery(JSONObject state) throws Exception {
    normalizeState(state);
    int turn = Math.max(1, state.optInt("turn", 1));
    JSONArray party = state.optJSONArray("party");
    if (party == null) return;

    for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null || !CharacterEncounterCore.isJoinedMember(member)) continue;
      String id = normalizeCharacterId(member.optString("id", member.optString("name", "")));
      if (id.isEmpty() || "kai".equals(id)) continue;

      JSONObject profile = profile(state, id);
      int hp = Math.max(0, profile.optInt("currentHp", 0));
      int reviveAtTurn = profile.optInt("reviveAtTurn", -1);

      if (hp <= 0 && reviveAtTurn > 0 && turn >= reviveAtTurn) {
        profile.put("currentHp", 1);
        profile.remove("downedAtTurn");
        profile.remove("reviveAtTurn");
      }
      syncPartyRecoveryState(state, id, profile, turn);
    }
  }

  private void syncPartyRecoveryState(JSONObject state, String id, JSONObject profile, int turn)
      throws Exception {
    JSONArray party = state.optJSONArray("party");
    if (party == null) return;
    for (int i = 0; i < party.length(); i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null) continue;
      String memberId = normalizeCharacterId(member.optString("id", member.optString("name", "")));
      if (!id.equals(memberId)) continue;

      int hp = Math.max(0, profile.optInt("currentHp", 0));
      int maxHp = Math.max(1, profile.optInt("maxHp", BASE_MAX_HP));
      member.put("hp", hp).put("maxHp", maxHp);
      if (hp <= 0) {
        int reviveAtTurn = profile.optInt("reviveAtTurn", turn + COMPANION_REVIVE_EXPLORER_TURNS);
        member.put("condition", "Bị hạ");
        member.put("reviveTurnsRemaining", Math.max(0, reviveAtTurn - turn));
      } else {
        member.remove("reviveTurnsRemaining");
        if ("Bị hạ".equals(member.optString("condition", ""))) member.put("condition", "Ổn định");
      }
      return;
    }
  }

  void setCurrentHp(JSONObject state, String rawId, int currentHp) throws Exception {
    JSONObject profile = ensureProfile(state, rawId);
    int maxHp = maxHpForExplorer(profile.optInt("explorer", 0));
    profile.put("maxHp", maxHp);
    profile.put("currentHp", Math.max(0, Math.min(currentHp, maxHp)));
  }

  void protectFromCandidate(JSONObject before, JSONObject candidate) throws Exception {
    if (before == null || candidate == null) return;
    normalizeState(before);
    candidate.put(ROOT_KEY, new JSONObject(before.getJSONObject(ROOT_KEY).toString()));
    candidate.remove(LEGACY_ROOT_KEY);
    stripShadowProgression(candidate.optJSONObject("player"));
    JSONArray party = candidate.optJSONArray("party");
    if (party != null) {
      for (int i = 0; i < party.length(); i++) stripShadowProgression(party.optJSONObject(i));
    }
  }

  private JSONObject ensureProfileObject(JSONObject characters, String id) throws Exception {
    JSONObject profile = characters.optJSONObject(id);
    if (profile == null) {
      profile = new JSONObject()
          .put("id", id)
          .put("baseStats", generateBaseStats(bonusPoolFor(id)))
          .put("explorer", 0)
          .put("exp", 0)
          .put("currentHp", BASE_MAX_HP)
          .put("maxHp", BASE_MAX_HP);
    } else {
      profile.put("id", id);
      JSONObject baseStats = profile.optJSONObject("baseStats");
      if (baseStats == null) baseStats = generateBaseStats(bonusPoolFor(id));
      else normalizeExistingBaseStats(baseStats);
      profile.put("baseStats", baseStats);

      int explorer = Math.max(0, profile.optInt("explorer", 0));
      profile.put("explorer", explorer);
      profile.put("exp", Math.max(0, profile.optInt("exp", 0)));
      int maxHp = maxHpForExplorer(explorer);
      int currentHp = profile.has("currentHp")
          ? profile.optInt("currentHp", BASE_MAX_HP) : BASE_MAX_HP;
      profile.put("currentHp", Math.max(0, Math.min(currentHp, maxHp)));
      profile.put("maxHp", maxHp);
    }
    characters.put(id, profile);
    return profile;
  }

  private static void normalizeExistingBaseStats(JSONObject baseStats) throws Exception {
    String[] keys = {"STR", "DF", "AGI", "CRIT"};
    for (String key : keys) {
      if (!baseStats.has(key)) baseStats.put(key, BASE_STAT);
    }
  }

  private static int bonusPoolFor(String id) {
    return "kai".equals(id) || "iris".equals(id) || "syvial".equals(id)
        ? FEATURED_BONUS_POOL : DEFAULT_BONUS_POOL;
  }

  static int sumBaseStats(JSONObject stats) {
    if (stats == null) return 0;
    return stats.optInt("STR", 0) + stats.optInt("DF", 0)
        + stats.optInt("AGI", 0) + stats.optInt("CRIT", 0);
  }

  static String normalizeCharacterId(String raw) {
    String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    if (value.contains("kai") || value.contains("twilight")) return "kai";
    if (value.contains("lucia") || value.contains("hứa thuý mai") || value.contains("hứa thúy mai")
        || value.contains("hua thuy mai")) return "lucia";
    if (value.contains("iris") || value.contains("argus")) return "iris";
    if (value.contains("syvial")) return "syvial";
    return value.replace(' ', '_');
  }

  private static String normalizeEntityKey(String raw) {
    return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
  }

  private static void stripShadowProgression(JSONObject character) {
    if (character == null) return;
    character.remove("baseStats");
    character.remove("explorer");
    character.remove("exp");
    character.remove("level");
  }
}
