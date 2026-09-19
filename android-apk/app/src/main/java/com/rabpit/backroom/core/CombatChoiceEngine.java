package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Small deterministic combat state machine used by the Android/WebView build.
 *
 * Explorer turn is intentionally owned by the outer game state. This engine never changes state.turn.
 * One player click resolves exactly one character action followed by that character's Entity response,
 * then advances to the next living party member. A combat round advances only after the last living
 * character has received its Entity response.
 */
public final class CombatChoiceEngine {
  public static final String ACTION_A = "__combat:A";
  public static final String ACTION_B = "__combat:B";
  public static final String ACTION_C = "__combat:C";
  private static final int GUILTY_CROWN_SHOTS = 24;
  private static final int GUILTY_CROWN_DAMAGE_PER_SHOT = 10;
  private static final int GUILTY_CROWN_TOTAL_DAMAGE = GUILTY_CROWN_SHOTS * GUILTY_CROWN_DAMAGE_PER_SHOT;
  private static final int MAX_COMBAT_PARTICIPANTS = 4;
  static final int KAI_DEFAULT_ATTACK = 30;
  static final int KAI_DEFAULT_DEFENSE = 10;
  static final double CRITICAL_DAMAGE_MULTIPLIER = 3.5d;

  private static final class EntityProfile {
    final String key;
    final String name;
    final int maxHp;
    final int attack;
    final int defense;

    EntityProfile(String key, String name, int maxHp, int attack, int defense) {
      this.key = key;
      this.name = name;
      this.maxHp = maxHp;
      this.attack = attack;
      this.defense = defense;
    }
  }

  private static final class Skill {
    final String name;
    final int procPercent;
    final int damagePercent;
    final String effect;
    final int effectTurns;
    final int effectValue;
    final boolean offensive;

    Skill(String name, int procPercent, int damagePercent, String effect, int effectTurns, int effectValue, boolean offensive) {
      this.name = name;
      this.procPercent = procPercent;
      this.damagePercent = damagePercent;
      this.effect = effect;
      this.effectTurns = effectTurns;
      this.effectValue = effectValue;
      this.offensive = offensive;
    }
  }

  private static final Map<String, EntityProfile> ENTITIES = new LinkedHashMap<>();
  private static final Map<String, List<Skill>> SKILLS = new LinkedHashMap<>();

  static {
    entity("hound", "Hound", 240, 15, 2);
    entity("clump", "Clump", 315, 17, 5);
    entity("duller", "Duller", 270, 14, 3);
    entity("deathmoth", "Deathmoth", 195, 13, 1);
    entity("hostile_faceling", "Hostile Faceling", 225, 14, 2);
    entity("false_puddle", "False Puddle", 285, 16, 4);
    entity("paintings", "Paintings", 210, 12, 1);
    entity("smiler", "Smiler", 255, 18, 2);
    entity("skin-stealer", "Skin-Stealer", 300, 18, 4);
    entity("predatory_window", "Predatory Window", 345, 17, 6);
    entity("biological_pipeline", "Biological Pipeline", 360, 18, 7);
    entity("wretch", "Wretch", 255, 16, 2);
    entity("cable_mimic", "Cable Mimic", 300, 17, 5);
    entity("the_beast_of_level_5", "The Beast of Level 5", 435, 22, 8);
    entity("hotel_corpse_lure", "Hotel Corpse Lure", 330, 18, 5);
    entity("jeff_the_killer", "Jeff", 360, 20, 4);
    entity("jane_the_killer", "Jane", 360, 20, 4);
    entity("slenderman", "Slenderman", 480, 23, 8);
    entity("diep_minh", "Diệp Minh", 2000, 42, 14);

    // Kai's combat skills are candidates for choice C. Pressing C still rolls the selected skill's
    // proc gate. Guilty Crown Override is now a 40% proc while preserving its exact 24 x 10 HP contract.
    skills("kai",
      skill("The Last Requiem", 30, 170, "Chảy máu", 3, 5, true),
      skill("Silent Lullaby", 20, 130, "Choáng", 1, 0, true),
      skill("Salvation", 20, 147, "", 0, 0, true),
      skill("Quick Step", 30, 0, "Né tránh", 3, 50, false),
      skill("Guilty Crown Override", 40, 0, "", 0, 0, true));

    skills("iris",
      skill("Twosome Time", 30, 155, "", 0, 0, true),
      skill("Rain Storm", 20, 145, "", 0, 0, true),
      skill("Honeycomb Fire", 20, 185, "Phá giáp", 2, 20, true),
      skill("Charged Shot", 25, 175, "", 0, 0, true));

    skills("syvial",
      skill("Rift Sever", 30, 175, "", 0, 0, true),
      skill("Crimson Guillotine", 20, 190, "Chảy máu", 3, 4, true),
      skill("Lucifer Breaker", 20, 155, "Choáng", 1, 0, true),
      skill("Spatial Dominion", 20, 210, "Mất phương hướng", 2, 25, true));

    // Lucia has one established command skill. It remains eligible for C, but the Entity can evade it.
    skills("lucia",
      skill("M4A1 Joint Attack", 100, 150, "", 0, 0, true));
  }

  private CombatChoiceEngine() {}

  private static void entity(String key, String name, int hp, int attack, int defense) {
    ENTITIES.put(key, new EntityProfile(key, name, hp, attack, defense));
  }

  private static Skill skill(String name, int proc, int damage, String effect, int turns, int value, boolean offensive) {
    return new Skill(name, proc, damage, effect, turns, value, offensive);
  }

  private static void skills(String id, Skill... definitions) {
    List<Skill> list = new ArrayList<>();
    for (Skill definition : definitions) list.add(definition);
    SKILLS.put(id, list);
  }

  static Map<String, String> semanticCatalog() {
    Map<String, String> output = new LinkedHashMap<>();
    for (EntityProfile profile : ENTITIES.values()) output.put(profile.name, "entity");
    for (List<Skill> pool : SKILLS.values()) {
      for (Skill skill : pool) {
        output.put(skill.name, "skill");
        if (skill.effect != null && !skill.effect.trim().isEmpty()) output.put(skill.effect, "effect");
      }
    }
    return output;
  }

  public static boolean isKnownEntity(String key) {
    return key != null && ENTITIES.containsKey(key.trim().toLowerCase(Locale.ROOT));
  }

  public static boolean isCombatAction(String action) {
    return ACTION_A.equals(action) || ACTION_B.equals(action) || ACTION_C.equals(action);
  }

  public static boolean isActive(JSONObject state) {
    JSONObject combat = state == null ? null : state.optJSONObject("combat");
    return combat != null && combat.optBoolean("active", false);
  }

  /** Neutral normal attack uses the maximum value of the legacy 18 + 4..12 roll, then armor. */
  public static int maxNormalDamage(int attackMax, int entityDefense) {
    return Math.max(1, Math.max(1, attackMax) - Math.max(0, entityDefense));
  }

  public static int defendedIncomingDamage(int entityAttack, int baseDefense) {
    return Math.max(1, Math.max(1, entityAttack) - Math.max(0, baseDefense) * 2);
  }

  public static int normalIncomingDamage(int entityAttack, int baseDefense) {
    return Math.max(1, Math.max(1, entityAttack) - Math.max(0, baseDefense));
  }

  public static int counterDamage(int normalAttackDamage) {
    return Math.max(1, (Math.max(1, normalAttackDamage) + 1) / 2);
  }

  static int configuredProcPercent(String characterId, String skillName) {
    List<Skill> pool = SKILLS.get(normalizeCharacterId(characterId));
    if (pool == null || skillName == null) return -1;
    for (Skill skill : pool) if (skillName.equals(skill.name)) return skill.procPercent;
    return -1;
  }

  static int exactDamageForSkill(String skillName) {
    return "Guilty Crown Override".equals(skillName) ? GUILTY_CROWN_TOTAL_DAMAGE : 0;
  }

  static int maxCombatParticipants() {
    return MAX_COMBAT_PARTICIPANTS;
  }

  public static JSONObject start(JSONObject state, String entityKey, int gmLogIndex) throws Exception {
    if (state == null) throw new IllegalArgumentException("state is required");
    String normalized = entityKey == null ? "" : entityKey.trim().toLowerCase(Locale.ROOT);
    EntityProfile profile = ENTITIES.get(normalized);
    if (profile == null) return state;
    if (isActive(state)) return state;

    CharacterProgressionCore progressionCore = new CharacterProgressionCore();
    progressionCore.normalizeState(state);
    JSONArray participants = buildParticipants(state, progressionCore);
    if (participants.length() == 0) return state;

    JSONObject combat = new JSONObject();
    combat.put("active", true);
    combat.put("round", 1);
    combat.put("actorIndex", 0);
    combat.put("sequence", 0);
    combat.put("seed", stableSeed(state, normalized, participants));
    combat.put("logIndex", Math.max(0, gmLogIndex));
    combat.put("participants", participants);

    JSONObject entityState = new JSONObject()
      .put("key", profile.key)
      .put("name", profile.name)
      .put("hp", profile.maxHp)
      .put("maxHp", profile.maxHp)
      .put("attack", profile.attack)
      .put("defense", profile.defense)
      .put("bleedTurns", 0)
      .put("bleedPercent", 0)
      .put("stunTurns", 0)
      .put("armorBreakTurns", 0)
      .put("armorBreakPercent", 0);
    JSONObject statProfile = new EntityStatCore().profile(state, normalized, progressionCore);
    entityState.put("statBaseline", statProfile.getJSONObject("baseline"));
    entityState.put("statModifier", statProfile.getJSONObject("modifier"));
    combat.put("entity", entityState);

    state.put("combat", combat);
    prepareCurrentTurn(combat);
    return state;
  }

  public static JSONObject resolve(JSONObject state, String action) throws Exception {
    if (!isActive(state)) return state;
    if (!isCombatAction(action)) throw new IllegalArgumentException("Đang chiến đấu. Hãy chọn A, B hoặc C.");

    JSONObject combat = state.getJSONObject("combat");
    combat.put("feedbackEvents", new JSONArray());
    combat.put("resolvedEntityTurn", false);
    JSONArray participants = combat.getJSONArray("participants");
    int actorIndex = combat.optInt("actorIndex", 0);
    if (actorIndex < 0 || actorIndex >= participants.length()) actorIndex = 0;
    JSONObject actor = participants.getJSONObject(actorIndex);
    if (actor.optInt("hp", 0) <= 0) {
      advanceActor(combat);
      actorIndex = combat.optInt("actorIndex", 0);
      actor = participants.getJSONObject(actorIndex);
    }

    combat.put("resolvedActorIndex", actorIndex);
    combat.put("resolvedActorName", actor.optString("name", "Nhân vật"));
    combat.put("resolvedRound", Math.max(1, combat.optInt("round", 1)));
    JSONObject entity = combat.getJSONObject("entity");
    if (actorIndex == 0 && combat.optInt("round", 1) > 1) {
      tickRoundStartEffects(state, combat, entity);
      if (entity.optInt("hp", 0) <= 0) {
        finishVictory(state, combat, entity);
        return state;
      }
    }

    boolean defending = ACTION_B.equals(action);
    if (ACTION_A.equals(action)) {
      resolveAttack(state, combat, actor, entity);
    } else if (defending) {
      appendBattleLine(state, combat,
        actor.optString("name", "Nhân vật") + " phòng thủ. +100% DEF.",
        actor.optString("name", ""), "+100% DEF");
    } else {
      resolveSkill(state, combat, actor, entity);
    }

    if (entity.optInt("hp", 0) <= 0) {
      finishVictory(state, combat, entity);
      syncParticipants(state, participants);
      return state;
    }

    combat.put("resolvedEntityTurn", true);
    resolveEntityResponse(state, combat, actor, entity, defending);
    syncParticipants(state, participants);

    if (isKaiDown(participants)) {
      finishPlayerDefeat(state, combat);
      return state;
    }

    if (entity.optInt("hp", 0) <= 0) {
      finishVictory(state, combat, entity);
      return state;
    }
    if (!hasLivingParticipant(participants)) {
      finishDefeat(state, combat);
      return state;
    }

    advanceActor(combat);
    combat.put("nextActorIndex", combat.optInt("actorIndex", 0));
    prepareCurrentTurn(combat);
    return state;
  }

  private static JSONArray buildParticipants(JSONObject state, CharacterProgressionCore progressionCore)
      throws Exception {
    JSONArray output = new JSONArray();
    JSONObject player = state.optJSONObject("player");
    String playerName = player == null ? "Kai Akechi" : player.optString("name", "Kai Akechi");
    JSONObject kaiProfile = progressionCore.profile(state, "kai");
    output.put(participant("kai", playerName, -1, player,
        kaiProfile.getInt("currentHp"), kaiProfile.getInt("maxHp"),
        KAI_DEFAULT_ATTACK, KAI_DEFAULT_DEFENSE));

    JSONArray party = state.optJSONArray("party");
    if (party == null) return output;
    for (int i = 0; i < party.length() && output.length() < MAX_COMBAT_PARTICIPANTS; i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null || !CharacterEncounterCore.isJoinedMember(member)) continue;
      String name = member.optString("name", member.optString("id", "")).trim();
      if (name.isEmpty() || normalizeCharacterId(name).equals("kai")) continue;
      String id = normalizeCharacterId(member.optString("id", name));
      int defaultAttack = "syvial".equals(id) ? 32 : "iris".equals(id) ? 28 : "lucia".equals(id) ? 24 : 24;
      int defaultDefense = "syvial".equals(id) ? 10 : "iris".equals(id) ? 8 : "lucia".equals(id) ? 7 : 7;
      JSONObject profile = progressionCore.profile(state, id);
      output.put(participant(id, name, i, member,
          profile.getInt("currentHp"), profile.getInt("maxHp"), defaultAttack, defaultDefense));
    }
    return output;
  }

  private static JSONObject participant(String id, String name, int sourceIndex, JSONObject source,
                                        int currentHp, int maxHp, int fallbackAttack, int fallbackDefense)
      throws Exception {
    int normalizedMaxHp = Math.max(1, maxHp);
    int hp = Math.max(0, Math.min(currentHp, normalizedMaxHp));
    int attack = firstPositive(source, fallbackAttack, "attackMax", "attack", "ATK", "str", "STR");
    int defense = firstPositive(source, fallbackDefense, "defense", "DEF", "df", "DF");
    return new JSONObject()
      .put("id", id)
      .put("name", name)
      .put("sourceIndex", sourceIndex)
      .put("hp", hp)
      .put("maxHp", normalizedMaxHp)
      .put("attack", attack)
      .put("defense", defense)
      .put("evasionBonus", 0)
      .put("evasionTurns", 0);
  }

  private static int firstPositive(JSONObject source, int fallback, String... keys) {
    if (source == null) return fallback;
    for (String key : keys) {
      int value = source.optInt(key, 0);
      if (value > 0) return value;
    }
    JSONObject stats = source.optJSONObject("stats");
    if (stats != null) {
      for (String key : keys) {
        int value = stats.optInt(key, 0);
        if (value > 0) return value;
      }
    }
    return fallback;
  }

  private static int stableSeed(JSONObject state, String entityKey, JSONArray participants) {
    String basis = entityKey + "|" + state.optInt("turn", 1) + "|" + participants.toString();
    int hash = basis.hashCode();
    return hash == Integer.MIN_VALUE ? 1 : Math.abs(hash);
  }

  private static void resolveAttack(JSONObject state, JSONObject combat, JSONObject actor, JSONObject entity) throws Exception {
    int damage = maxNormalDamage(actor.optInt("attack", 30), effectiveEntityDefense(entity));
    int hp = Math.max(0, entity.optInt("hp", 0) - damage);
    entity.put("hp", hp);
    String actorName = actor.optString("name", "Nhân vật");
    String entityName = entity.optString("name", "Entity");
    String damageText = "-" + damage + " HP";
    String hpText = "HP " + hp + "/" + entity.optInt("maxHp", hp);
    appendBattleLine(state, combat,
      actorName + " tấn công " + entityName + ". " + damageText + " (" + hpText + ")",
      actorName, entityName, damageText, hpText);
    addFeedback(combat, "actor", "entity", "damage", damageText, true);
  }

  private static void resolveSkill(JSONObject state, JSONObject combat, JSONObject actor, JSONObject entity) throws Exception {
    JSONObject selected = combat.optJSONObject("currentSkill");
    String actorName = actor.optString("name", "Nhân vật");
    String entityName = entity.optString("name", "Entity");
    if (selected == null || selected.optString("name", "").isEmpty()) {
      appendBattleLine(state, combat, actorName + " không có kỹ năng khả dụng.", actorName);
      return;
    }

    String skillName = selected.optString("name", "Kỹ năng");
    int procPercent = selected.optInt("procPercent", 100);
    if (nextRoll(combat, "proc:" + skillName) >= procPercent) {
      appendBattleLine(state, combat,
        actorName + " dùng " + skillName + " lên " + entityName + ". Trượt.",
        actorName, skillName, entityName);
      addFeedback(combat, "actor", "entity", "miss", "MISS", false);
      return;
    }

    boolean offensive = selected.optBoolean("offensive", true);
    int exactDamage = exactDamageForSkill(skillName);
    if (exactDamage > 0) {
      int hp = Math.max(0, entity.optInt("hp", 0) - exactDamage);
      entity.put("hp", hp);
      String damageText = "-" + exactDamage + " HP";
      String hpText = "HP " + hp + "/" + entity.optInt("maxHp", hp);
      appendBattleLine(state, combat,
        actorName + " dùng " + skillName + ": " + GUILTY_CROWN_SHOTS + "/" + GUILTY_CROWN_SHOTS +
          " phát trúng khi ngoại giới dừng thời gian. " + damageText + " (" + hpText + ")",
        actorName, skillName, entityName, damageText, hpText);
      addFeedback(combat, "actor", "entity", "damage", damageText, true);
      return;
    }
    if (offensive) {
      // Lucia's command historically still passes through Entity evasion. Other former AUTO proc skills
      // retain their old proc as the gate, so we do not add a second accuracy penalty to them.
      if ("M4A1 Joint Attack".equals(skillName) && nextRoll(combat, "lucia-hit") >= 80) {
        appendBattleLine(state, combat,
          actorName + " dùng " + skillName + " lên " + entityName + ". Trượt.",
          actorName, skillName, entityName);
        addFeedback(combat, "actor", "entity", "miss", "MISS", false);
        return;
      }
      int raw = Math.max(1, actor.optInt("attack", 30) * selected.optInt("damagePercent", 100) / 100);
      int damage = Math.max(1, raw - effectiveEntityDefense(entity));
      int hp = Math.max(0, entity.optInt("hp", 0) - damage);
      entity.put("hp", hp);
      String damageText = "-" + damage + " HP";
      String hpText = "HP " + hp + "/" + entity.optInt("maxHp", hp);
      appendBattleLine(state, combat,
        actorName + " dùng " + skillName + " lên " + entityName + ". " + damageText + " (" + hpText + ")",
        actorName, skillName, entityName, damageText, hpText);
      addFeedback(combat, "actor", "entity", "damage", damageText, true);
    } else {
      appendBattleLine(state, combat,
        actorName + " dùng " + skillName + ".",
        actorName, skillName);
    }

    applySkillEffect(state, combat, actor, entity, selected);
  }

  private static void applySkillEffect(JSONObject state, JSONObject combat, JSONObject actor,
                                       JSONObject entity, JSONObject selected) throws Exception {
    String effect = selected.optString("effect", "").trim();
    if (effect.isEmpty() || entity.optInt("hp", 0) <= 0) return;
    int turns = Math.max(0, selected.optInt("effectTurns", 0));
    int value = Math.max(0, selected.optInt("effectValue", 0));
    String entityName = entity.optString("name", "Entity");
    String actorName = actor.optString("name", "Nhân vật");

    if ("Choáng".equals(effect)) {
      entity.put("stunTurns", Math.max(entity.optInt("stunTurns", 0), turns));
      appendBattleLine(state, combat, entityName + " bị Choáng " + turns + " lượt.", entityName, "Choáng");
    } else if ("Chảy máu".equals(effect)) {
      entity.put("bleedTurns", Math.max(entity.optInt("bleedTurns", 0), turns));
      entity.put("bleedPercent", Math.max(entity.optInt("bleedPercent", 0), value));
      appendBattleLine(state, combat, entityName + " bị Chảy máu " + turns + " lượt.", entityName, "Chảy máu");
    } else if ("Phá giáp".equals(effect)) {
      entity.put("armorBreakTurns", Math.max(entity.optInt("armorBreakTurns", 0), turns));
      entity.put("armorBreakPercent", Math.max(entity.optInt("armorBreakPercent", 0), value));
      appendBattleLine(state, combat, entityName + " bị Phá giáp " + turns + " lượt.", entityName, "Phá giáp");
    } else if ("Né tránh".equals(effect)) {
      actor.put("evasionBonus", value).put("evasionTurns", turns);
      appendBattleLine(state, combat,
        actorName + " nhận +" + value + "% Né tránh trong " + turns + " lượt.",
        actorName, "+" + value + "% Né tránh", "Né tránh");
    } else if ("Mất phương hướng".equals(effect)) {
      entity.put("accuracyPenalty", value).put("accuracyPenaltyTurns", turns);
      appendBattleLine(state, combat,
        entityName + " bị Mất phương hướng " + turns + " lượt.", entityName, "Mất phương hướng");
    }
  }

  private static void resolveEntityResponse(JSONObject state, JSONObject combat, JSONObject actor,
                                            JSONObject entity, boolean defending) throws Exception {
    String entityName = entity.optString("name", "Entity");
    String actorName = actor.optString("name", "Nhân vật");

    int stunTurns = entity.optInt("stunTurns", 0);
    if (stunTurns > 0) {
      entity.put("stunTurns", stunTurns - 1);
      appendBattleLine(state, combat, entityName + " mất lượt vì Choáng.", entityName, "Choáng");
      decrementActorEvasion(actor);
      decrementEntityTemporaryEffects(entity);
      return;
    }

    int dodgeChance = defending ? 20 : 0;
    dodgeChance += actor.optInt("evasionTurns", 0) > 0 ? actor.optInt("evasionBonus", 0) : 0;
    dodgeChance = Math.min(95, Math.max(0, dodgeChance));
    int accuracyPenalty = entity.optInt("accuracyPenaltyTurns", 0) > 0 ? entity.optInt("accuracyPenalty", 0) : 0;
    int hitRoll = nextRoll(combat, "entity:" + actorName);
    boolean dodged = hitRoll < Math.min(95, dodgeChance + accuracyPenalty);

    if (dodged) {
      appendBattleLine(state, combat,
        entityName + " tấn công " + actorName + ". " + actorName + " né thành công.",
        entityName, actorName);
      addFeedback(combat, "entity", "actor", "miss", "MISS", false);
      if (defending) {
        int normal = maxNormalDamage(actor.optInt("attack", 30), effectiveEntityDefense(entity));
        int damage = counterDamage(normal);
        int hp = Math.max(0, entity.optInt("hp", 0) - damage);
        entity.put("hp", hp);
        String damageText = "-" + damage + " HP";
        String hpText = "HP " + hp + "/" + entity.optInt("maxHp", hp);
        appendBattleLine(state, combat,
          actorName + " phản công " + entityName + ". " + damageText + " (" + hpText + ")",
          actorName, entityName, damageText, hpText);
        addFeedback(combat, "entity", "entity", "damage", damageText, true);
      }
    } else {
      int damage = defending
        ? defendedIncomingDamage(entity.optInt("attack", 1), actor.optInt("defense", 0))
        : normalIncomingDamage(entity.optInt("attack", 1), actor.optInt("defense", 0));
      int hp = Math.max(0, actor.optInt("hp", 0) - damage);
      actor.put("hp", hp);
      String damageText = "-" + damage + " HP";
      String hpText = "HP " + hp + "/" + actor.optInt("maxHp", hp);
      appendBattleLine(state, combat,
        entityName + " tấn công " + actorName + ". " + damageText + " (" + hpText + ")",
        entityName, actorName, damageText, hpText);
      addFeedback(combat, "entity", "actor", "damage", damageText, true);
      if (hp <= 0) appendBattleLine(state, combat, actorName + " bị hạ.", actorName);
    }

    decrementActorEvasion(actor);
    decrementEntityTemporaryEffects(entity);
  }

  private static void tickRoundStartEffects(JSONObject state, JSONObject combat, JSONObject entity) throws Exception {
    int bleedTurns = entity.optInt("bleedTurns", 0);
    int bleedPercent = entity.optInt("bleedPercent", 0);
    if (bleedTurns <= 0 || bleedPercent <= 0 || entity.optInt("hp", 0) <= 0) return;
    int damage = Math.max(1, entity.optInt("maxHp", 1) * bleedPercent / 100);
    int hp = Math.max(0, entity.optInt("hp", 0) - damage);
    entity.put("hp", hp).put("bleedTurns", bleedTurns - 1);
    String entityName = entity.optString("name", "Entity");
    String damageText = "-" + damage + " HP";
    String hpText = "HP " + hp + "/" + entity.optInt("maxHp", hp);
    appendBattleLine(state, combat,
      entityName + " chịu Chảy máu. " + damageText + " (" + hpText + ")",
      entityName, "Chảy máu", damageText, hpText);
    addFeedback(combat, "actor", "entity", "damage", damageText, true);
  }

  private static int effectiveEntityDefense(JSONObject entity) {
    int defense = Math.max(0, entity.optInt("defense", 0));
    int turns = entity.optInt("armorBreakTurns", 0);
    int percent = turns > 0 ? Math.max(0, entity.optInt("armorBreakPercent", 0)) : 0;
    return Math.max(0, defense - defense * percent / 100);
  }

  private static void decrementActorEvasion(JSONObject actor) throws Exception {
    int turns = actor.optInt("evasionTurns", 0);
    if (turns > 0) actor.put("evasionTurns", turns - 1);
    if (turns <= 1) actor.put("evasionBonus", 0);
  }

  private static void decrementEntityTemporaryEffects(JSONObject entity) throws Exception {
    int armorTurns = entity.optInt("armorBreakTurns", 0);
    if (armorTurns > 0) entity.put("armorBreakTurns", armorTurns - 1);
    int accuracyTurns = entity.optInt("accuracyPenaltyTurns", 0);
    if (accuracyTurns > 0) entity.put("accuracyPenaltyTurns", accuracyTurns - 1);
  }

  private static boolean isKaiDown(JSONArray participants) {
    for (int i = 0; i < participants.length(); i++) {
      JSONObject participant = participants.optJSONObject(i);
      if (participant == null) continue;
      String id = normalizeCharacterId(
          participant.optString("id", participant.optString("name", "")));
      if ("kai".equals(id)) return participant.optInt("hp", 0) <= 0;
    }
    return false;
  }

  private static boolean hasLivingParticipant(JSONArray participants) {
    for (int i = 0; i < participants.length(); i++) {
      JSONObject participant = participants.optJSONObject(i);
      if (participant != null && participant.optInt("hp", 0) > 0) return true;
    }
    return false;
  }

  private static void advanceActor(JSONObject combat) throws Exception {
    JSONArray participants = combat.getJSONArray("participants");
    int current = combat.optInt("actorIndex", 0);
    int next = current;
    boolean found = false;
    for (int step = 1; step <= participants.length(); step++) {
      int candidate = (current + step) % participants.length();
      JSONObject participant = participants.optJSONObject(candidate);
      if (participant != null && participant.optInt("hp", 0) > 0) {
        next = candidate;
        found = true;
        break;
      }
    }
    if (!found) return;
    if (next <= current) combat.put("round", Math.max(1, combat.optInt("round", 1)) + 1);
    combat.put("actorIndex", next);
  }

  private static void prepareCurrentTurn(JSONObject combat) throws Exception {
    JSONArray participants = combat.getJSONArray("participants");
    int actorIndex = combat.optInt("actorIndex", 0);
    JSONObject actor = participants.getJSONObject(actorIndex);
    String id = normalizeCharacterId(actor.optString("id", actor.optString("name", "")));
    List<Skill> pool = SKILLS.get(id);
    Skill selected = null;
    if (pool != null && !pool.isEmpty()) {
      int seed = combat.optInt("seed", 1);
      int round = combat.optInt("round", 1);
      int index = Math.floorMod(seed + round * 31 + actorIndex * 17, pool.size());
      selected = pool.get(index);
    }

    JSONArray choices = new JSONArray();
    choices.put(choice("A", "Tấn công", false));
    choices.put(choice("B", "Phòng thủ", false));
    if (selected != null) {
      JSONObject skillJson = skillJson(selected);
      combat.put("currentSkill", skillJson);
      choices.put(choice("C", selected.name, false));
    } else {
      combat.remove("currentSkill");
      choices.put(choice("C", "Không có kỹ năng", true));
    }
    combat.put("choices", choices);
    combat.put("currentActor", actor.optString("name", "Nhân vật"));
  }

  private static JSONObject choice(String id, String text, boolean disabled) throws Exception {
    return new JSONObject().put("id", id).put("text", text).put("disabled", disabled);
  }

  private static JSONObject skillJson(Skill skill) throws Exception {
    return new JSONObject()
      .put("name", skill.name)
      .put("procPercent", skill.procPercent)
      .put("damagePercent", skill.damagePercent)
      .put("effect", skill.effect)
      .put("effectTurns", skill.effectTurns)
      .put("effectValue", skill.effectValue)
      .put("offensive", skill.offensive);
  }

  private static int nextRoll(JSONObject combat, String salt) throws Exception {
    int sequence = combat.optInt("sequence", 0);
    int seed = combat.optInt("seed", 1);
    int value = Math.floorMod(seed * 31 + sequence * 131 + salt.hashCode() * 17, 100);
    combat.put("sequence", sequence + 1);
    return value;
  }

  private static void addFeedback(JSONObject combat, String phase, String target, String kind,
                                  String text, boolean flash) throws Exception {
    JSONArray events = combat.optJSONArray("feedbackEvents");
    if (events == null) events = new JSONArray();
    JSONObject event = new JSONObject()
      .put("phase", phase)
      .put("target", target)
      .put("kind", kind)
      .put("text", text == null ? "" : text)
      .put("flash", flash)
      .put("actorIndex", combat.optInt("resolvedActorIndex", combat.optInt("actorIndex", 0)));
    events.put(event);
    combat.put("feedbackEvents", events);
  }

  private static void appendBattleLine(JSONObject state, JSONObject combat, String text, String... highlights) throws Exception {
    JSONArray log = state.optJSONArray("log");
    if (log == null || log.length() == 0) return;
    int index = combat.optInt("logIndex", log.length() - 1);
    index = Math.max(0, Math.min(index, log.length() - 1));
    JSONObject entry = log.optJSONObject(index);
    if (entry == null) return;
    JSONArray battleLog = entry.optJSONArray("battleLog");
    if (battleLog == null) battleLog = new JSONArray();
    JSONArray semantic = new JSONArray();
    for (String highlight : highlights) {
      if (highlight != null && !highlight.trim().isEmpty()) semantic.put(highlight.trim());
    }
    battleLog.put(new JSONObject().put("text", text).put("highlights", semantic));
    entry.put("battleLog", battleLog);
  }

  private static void finishVictory(JSONObject state, JSONObject combat, JSONObject entity) throws Exception {
    String entityName = entity.optString("name", "Entity");
    if (!combat.optBoolean("victoryLogged", false)) {
      appendBattleLine(state, combat, entityName + " bị tiêu diệt.", entityName);
      combat.put("victoryLogged", true);
    }
    if (!combat.optBoolean("lootResolved", false)) {
      String entityKey = entity.optString("key", "entity");
      int rate = ItemCore.entityDropRatePercent(entityKey);
      int dropRoll = nextRoll(combat, "loot-drop:" + entityKey);
      combat.put("entityLootRatePercent", rate).put("entityLootRoll", dropRoll);
      if (ItemCore.shouldDropEntityLoot(dropRoll, rate)) {
        String itemName = ItemCore.grantLootItem(state, nextRoll(combat, "loot-item:" + entityKey));
        appendBattleLine(state, combat, entityName + " rơi " + itemName + " x1.", entityName, itemName);
        combat.put("droppedItem", itemName);
      }
      combat.put("lootResolved", true);
    }

    new CharacterProgressionCore().grantEntityKillExp(
        state, entity.optString("key", ""), combat.optJSONArray("participants"), combat);

    combat.put("active", false).put("outcome", "victory").put("choices", new JSONArray());
    clearEncounterFlag(state);
  }

  private static void finishDefeat(JSONObject state, JSONObject combat) throws Exception {
    finishPlayerDefeat(state, combat);
  }

  private static void finishPlayerDefeat(JSONObject state, JSONObject combat) throws Exception {
    combat.put("active", false).put("outcome", "defeat").put("choices", new JSONArray());
    if (!combat.optBoolean("deathRecoveryApplied", false)) {
      CharacterProgressionCore progressionCore = new CharacterProgressionCore();
      progressionCore.applyKaiDeathPenalty(state);
      LevelCore.resetToLevelZeroStart(state);
      combat.put("deathRecoveryApplied", true);
      combat.put("playerRespawned", true);
      appendBattleLine(state, combat,
          "Kai bị hạ. Kai trở lại điểm bắt đầu Level 0 và chịu hình phạt tiến trình.", "Kai Akechi");
    }
    clearEncounterFlag(state);
  }

  static void normalizeTerminalEncounter(JSONObject state) throws Exception {
    if (state == null) return;
    JSONObject combat = state.optJSONObject("combat");
    if (combat == null || combat.optBoolean("active", false)) return;
    String outcome = combat.optString("outcome", "");
    if ("defeat".equals(outcome) && !combat.optBoolean("deathRecoveryApplied", false)) {
      finishPlayerDefeat(state, combat);
      return;
    }
    if ("victory".equals(outcome) || "defeat".equals(outcome)) clearEncounterFlag(state);
  }

  private static void clearEncounterFlag(JSONObject state) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    if (flags != null) flags.put("entityEncounterKey", "");
  }

  private static void syncParticipants(JSONObject state, JSONArray participants) throws Exception {
    CharacterProgressionCore progressionCore = new CharacterProgressionCore();
    progressionCore.normalizeState(state);
    JSONArray party = state.optJSONArray("party");
    for (int i = 0; i < participants.length(); i++) {
      JSONObject participant = participants.optJSONObject(i);
      if (participant == null) continue;
      String id = normalizeCharacterId(
          participant.optString("id", participant.optString("name", "")));
      progressionCore.setCurrentHp(state, id, participant.optInt("hp", 0));
      if (!"kai".equals(id) && participant.optInt("hp", 0) <= 0) {
        progressionCore.markCompanionDown(state, id);
      }
      JSONObject profile = progressionCore.profile(state, id);
      int hp = profile.getInt("currentHp");
      int maxHp = profile.getInt("maxHp");
      int sourceIndex = participant.optInt("sourceIndex", -1);
      if (sourceIndex < 0) {
        JSONObject player = state.optJSONObject("player");
        if (player != null) {
          player.put("hp", hp).put("maxHp", maxHp);
          if (hp <= 0) player.put("condition", "Bị hạ");
        }
      } else if (party != null && sourceIndex < party.length()) {
        JSONObject member = party.optJSONObject(sourceIndex);
        if (member != null) member.put("hp", hp).put("maxHp", maxHp);
      }
    }
  }

  private static String normalizeCharacterId(String raw) {
    String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    if (value.contains("kai") || value.contains("twilight")) return "kai";
    if (value.contains("iris") || value.contains("argus")) return "iris";
    if (value.contains("syvial")) return "syvial";
    if (value.contains("lucia") || value.contains("hứa thuý mai") || value.contains("hua thuy mai")) return "lucia";
    return value.replace(' ', '_');
  }
}
