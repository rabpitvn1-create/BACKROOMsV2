package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Authoritative 5D6 Poker Dice combat state machine.
 *
 * The WebView only displays values already produced here. Dice values, holds, reroll count,
 * finalized hand and PRNG sequence are serializable combat state, so reload cannot grant a free
 * reroll. One finalized hand resolves exactly one Character action and at most one Entity response.
 */
public final class CombatChoiceEngine {
  static final int MAX_COMBAT_PARTICIPANTS = 4;
  static final int MAX_REROLLS = 3;
  static final int DICE_COUNT = 5;

  private static final int ULTIMATE_BONUS_DAMAGE_PERCENT = 15;
  private static final int HUYET_MA_24_HIT_COUNT = 24;
  private static final int LUCIA_TOO_YOUNG_TO_DIE_SHOT_COUNT = 60;
  private static final int CAO_MINH_BASE_ATTACK = 30;

  private static final class EntityProfile {
    final String key;
    final String name;
    final int maxHp;
    final int damage;

    EntityProfile(String key, String name, int maxHp, int damage) {
      this.key = key;
      this.name = name;
      this.maxHp = maxHp;
      this.damage = damage;
    }
  }

  private static final class Skill {
    final String name;
    final String description;
    final int damagePercent;
    final String effect;
    final int effectTurns;
    final int effectValue;

    Skill(String name, String description, int damagePercent, String effect, int effectTurns,
          int effectValue) {
      this.name = name;
      this.description = description;
      this.damagePercent = damagePercent;
      this.effect = effect;
      this.effectTurns = effectTurns;
      this.effectValue = effectValue;
    }
  }

  private static final class EntitySkill {
  final String name; final int damagePercent; final int procPercent;
  EntitySkill(String name, int damagePercent, int procPercent) {
    this.name=name; this.damagePercent=damagePercent; this.procPercent=procPercent;
  }
}

  private static final class CharacterProc {
    final String name;
    final int procPercent;
    final int bonusDamagePercent;
    final String effect;
    final int effectTurns;
    final int effectValue;

    CharacterProc(String name, int procPercent, int bonusDamagePercent, String effect,
                  int effectTurns, int effectValue) {
      this.name = name;
      this.procPercent = procPercent;
      this.bonusDamagePercent = bonusDamagePercent;
      this.effect = effect;
      this.effectTurns = effectTurns;
      this.effectValue = effectValue;
    }
  }

  private static final class Ultimate {
    final String name;
    final int hitCount;
    final int bonusPercent;

    Ultimate(String name, int hitCount, int bonusPercent) {
      this.name = name;
      this.hitCount = hitCount;
      this.bonusPercent = bonusPercent;
    }
  }

  private static final class ActionResult {
    String summary;
    boolean evadeResponse;
  }

  private static final Map<String, EntityProfile> ENTITIES = new LinkedHashMap<>();
  private static final Map<String, List<Skill>> SKILLS = new LinkedHashMap<>();
  private static final Map<String, List<EntitySkill>> ENTITY_SKILLS = new LinkedHashMap<>();
  private static final Map<String, List<CharacterProc>> CHARACTER_PROCS = new LinkedHashMap<>();
  private static final Map<String, Ultimate> ULTIMATES = new LinkedHashMap<>();

  static {
    entity("hound", "Hound", 240, 15);
    entity("clump", "Clump", 315, 17);
    entity("duller", "Duller", 270, 14);
    entity("deathmoth", "Deathmoth", 195, 13);
    entity("hostile_faceling", "Hostile Faceling", 225, 14);
    entity("false_puddle", "False Puddle", 285, 16);
    entity("paintings", "Paintings", 210, 12);
    entity("smiler", "Smiler", 255, 18);
    entity("skin-stealer", "Skin-Stealer", 300, 18);
    entity("predatory_window", "Predatory Window", 345, 17);
    entity("biological_pipeline", "Biological Pipeline", 360, 18);
    entity("wretch", "Wretch", 255, 16);
    entity("cable_mimic", "Cable Mimic", 300, 17);
    entity("the_beast_of_level_5", "The Beast of Level 5", 435, 22);
    entity("hotel_corpse_lure", "Hotel Corpse Lure", 330, 18);
    entity("jeff_the_killer", "Jeff", 360, 20);
    entity("async_rifleman", "ASYNC Rifleman", 300, 20);
    entity("async_vanguard", "ASYNC Vanguard", 330, 21);
    entity("async_tactical", "ASYNC Tactical Operative", 270, 18);
    entity("async_decon", "ASYNC Decon Specialist", 315, 19);
    entity("jane_the_killer", "Jane", 360, 20);
    entity("slenderman", "Slenderman", 480, 23);
    entity("diep_minh", "Diệp Minh", 2000, 42);

    entitySkills("hound",
    entitySkill("Dead Bite",120,35), entitySkill("Rending Pounce",115,32), entitySkill("Pack Maul",110,38));
  entitySkills("clump",
    entitySkill("Grasping Crush",120,34), entitySkill("Limb Barrage",115,36), entitySkill("Drag Down",110,31));
  entitySkills("duller",
    entitySkill("Blindside Strike",125,33), entitySkill("Distorted Lunge",115,37), entitySkill("Column Ambush",120,30));

    // Only offensive canonical skills participate in Poker Dice Skill hands. Passive/evasion
    // skills do not silently replace or modify dice outcomes.
    skills("cao_minh",
        skill("Huyết Ma Tứ Liên",
            "Bốn trảm liên hoàn; tổng 170% weapon damage và Chảy máu.", 170, "Chảy máu", 3, 5),
        skill("Ma Tâm Trấn Hồn",
            "Ma uy trấn áp thần hồn; tổng 130% weapon damage và Choáng.", 130, "Choáng", 1, 0),
        skill("Huyết Ảnh Ma Độn",
            "Dịch chuyển theo Huyết Ma Kiếm rồi chém đúng hai kiếm; tổng 147% weapon damage.",
            147, "", 0, 0));

    skills("iris",
        skill("Twosome Time", "", 155, "", 0, 0),
        skill("Rain Storm", "", 145, "", 0, 0),
        skill("Honeycomb Fire", "", 185, "Xuyên giáp", 2, 20),
        skill("Charged Shot", "", 175, "", 0, 0));

    skills("syvial",
        skill("Rift Sever", "", 175, "", 0, 0),
        skill("Crimson Guillotine", "", 190, "Chảy máu", 3, 4),
        skill("Lucifer Breaker", "", 155, "Choáng", 1, 0),
        skill("Spatial Dominion", "", 210, "Mất phương hướng", 2, 25));

    skills("lucia",
        skill("M4A1 Joint Attack", "", 150, "", 0, 0));

    // Independent proc skills may trigger after a basic attack or normal Skill, never an Ultimate.
    // Kai is Legacy and deliberately has no entry in this authoritative pool.
    characterProcs("cao_minh",
        characterProc("Huyết Sát Kiếm Ấn", 50, 25, "Chảy máu", 2, 3),
        characterProc("Phá Giáp Ma Kiếm", 48, 20, "Xuyên giáp", 2, 10),
        characterProc("Ma Tâm Chấn", 45, 15, "Choáng", 1, 0),
        characterProc("Huyết Độc Ma Khí", 47, 20, "Trúng độc", 2, 3),
        characterProc("Huyết Liệt Ma Ấn", 51, 20, "Chảy máu", 2, 4));
    characterProcs("lucia",
        characterProc("Toxic Burst", 52, 25, "Trúng độc", 2, 3),
        characterProc("Armor-Piercing Burst", 55, 20, "Xuyên giáp", 2, 10),
        characterProc("Concussive Burst", 46, 15, "Choáng", 1, 0),
        characterProc("Rending Burst", 49, 20, "Chảy máu", 2, 3),
        characterProc("Corrosive Burst", 51, 20, "Trúng độc", 2, 4));

    // Ultimate damage derives from each character's current basic DMG instead of a fixed HP value.
    // SSF uses the listed total; FSF keeps the Poker Dice 200% Ultimate multiplier.
    ULTIMATES.put("cao_minh",
        new Ultimate("Huyết Ma Nhị Thập Tứ Trảm", HUYET_MA_24_HIT_COUNT,
            ULTIMATE_BONUS_DAMAGE_PERCENT));
    ULTIMATES.put("lucia",
        new Ultimate("Too Young To Die", LUCIA_TOO_YOUNG_TO_DIE_SHOT_COUNT,
            ULTIMATE_BONUS_DAMAGE_PERCENT));
  }

  private CombatChoiceEngine() {}

  private static void entity(String key, String name, int hp, int damage) {
    ENTITIES.put(key, new EntityProfile(key, name, hp, damage));
  }

  private static Skill skill(String name, String description, int damage, String effect, int turns,
                             int value) {
    return new Skill(name, description, damage, effect, turns, value);
  }

  private static void skills(String id, Skill... definitions) {
    List<Skill> list = new ArrayList<>();
    for (Skill definition : definitions) list.add(definition);
    SKILLS.put(id, list);
  }

  private static EntitySkill entitySkill(String name,int damagePercent,int procPercent) {
  return new EntitySkill(name,damagePercent,procPercent);
}
private static void entitySkills(String id,EntitySkill... definitions) {
  List<EntitySkill> list=new ArrayList<>(); for(EntitySkill d:definitions) list.add(d); ENTITY_SKILLS.put(id,list);
}
static int entitySkillCount(String key) {
  List<EntitySkill> pool=ENTITY_SKILLS.get(key==null?"":key.trim().toLowerCase(Locale.ROOT)); return pool==null?0:pool.size();
}
static int entitySkillDamage(int rawDamage,int percent) {
  return Math.max(1,(int)(((long)Math.max(1,rawDamage)*Math.max(0,percent)+50L)/100L));
}
static int entitySkillProcRoll(int seed,int round,int actorIndex,int skillIndex) {
  long x=(seed&0xffffffffL)*1664525L+(long)Math.max(1,round)*1013904223L+(long)(actorIndex+1)*2654435761L+(long)(skillIndex+1)*97531L;
  x^=x>>>16; x^=x<<11; return (int)Math.floorMod(x,100L);
}

  private static CharacterProc characterProc(String name, int procPercent, int bonusDamagePercent,
                                             String effect, int turns, int value) {
    return new CharacterProc(name, procPercent, bonusDamagePercent, effect, turns, value);
  }

  private static void characterProcs(String id, CharacterProc... definitions) {
    List<CharacterProc> list = new ArrayList<>();
    for (CharacterProc definition : definitions) list.add(definition);
    CHARACTER_PROCS.put(id, list);
  }

  static int characterProcCount(String id) {
    List<CharacterProc> pool = CHARACTER_PROCS.get(CharacterProgressionCore.normalizeCharacterId(id));
    return pool == null ? 0 : pool.size();
  }

  static int characterProcPercent(String id, int index) {
    List<CharacterProc> pool = CHARACTER_PROCS.get(CharacterProgressionCore.normalizeCharacterId(id));
    if (pool == null || index < 0 || index >= pool.size()) return -1;
    return pool.get(index).procPercent;
  }

  static int characterProcRoll(int seed, int sequence, String actorId, String procName) {
    long mixed = (seed & 0xffffffffL) * 31L + (long)(sequence + 1) * 131L
        + (long)(actorId + ":" + procName).hashCode() * 17L;
    return (int)Math.floorMod(mixed, 100L);
  }

  static Map<String, String> semanticCatalog() {
    Map<String, String> output = new LinkedHashMap<>();
    for (EntityProfile profile : ENTITIES.values()) output.put(profile.name, "entity");
    for (List<Skill> pool : SKILLS.values()) {
      for (Skill skill : pool) {
        output.put(skill.name, "skill");
        if (!skill.effect.trim().isEmpty()) output.put(skill.effect, "effect");
      }
    }
    for (List<CharacterProc> pool : CHARACTER_PROCS.values()) {
      for (CharacterProc proc : pool) {
        output.put(proc.name, "skill");
        if (!proc.effect.trim().isEmpty()) output.put(proc.effect, "effect");
      }
    }
    for (List<EntitySkill> pool : ENTITY_SKILLS.values()) {
      for (EntitySkill skill : pool) output.put(skill.name, "skill");
    }
    for (String token : new String[]{"[NO HAND]","[PAIR]","[TWO PAIR]","[TRIPLE]",
        "[STRAIGHT]","[FULL HOUSE]","[F.O.A.K]","[SSF]","[FSF]"}) {
      output.put(token, "stat");
    }
    for (Ultimate ultimate : ULTIMATES.values()) output.put(ultimate.name, "skill");
    // Canonical non-offensive skill retained for GM semantic recognition only. It is deliberately
    // excluded from Poker Dice Skill selection so it cannot alter evade/dice outcomes.
    output.put("Thiên Ma Bộ", "skill");
    return output;
  }

  public static boolean isKnownEntity(String key) {
    return key != null && ENTITIES.containsKey(key.trim().toLowerCase(Locale.ROOT));
  }

  public static boolean isActive(JSONObject state) {
    JSONObject combat = state == null ? null : state.optJSONObject("combat");
    return combat != null && combat.optBoolean("active", false);
  }

  static int maxCombatParticipants() {
    return MAX_COMBAT_PARTICIPANTS;
  }

  static boolean hasAuthoritativeUltimate(String characterId) {
    return ULTIMATES.containsKey(CharacterProgressionCore.normalizeCharacterId(characterId));
  }

  static String classify(int... dice) {
    if (dice == null || dice.length != DICE_COUNT) return "NO HAND";
    int[] values = dice.clone();
    for (int value : values) if (value < 1 || value > 6) return "NO HAND";

    boolean allSame = true;
    for (int i = 1; i < values.length; i++) allSame &= values[i] == values[0];
    if (allSame) return "FSF";

    if (matches(values, 1, 2, 3, 4, 5) || matches(values, 5, 4, 3, 2, 1)) return "SSF";
    if (matches(values, 2, 3, 4, 5, 6) || matches(values, 6, 5, 4, 3, 2)) return "STRAIGHT";

    int[] counts = new int[7];
    for (int value : values) counts[value]++;
    boolean four = false;
    boolean triple = false;
    int pairs = 0;
    for (int value = 1; value <= 6; value++) {
      if (counts[value] == 4) four = true;
      if (counts[value] == 3) triple = true;
      if (counts[value] == 2) pairs++;
    }
    if (four) return "FOUR OF A KIND";
    if (triple && pairs == 1) return "FULL HOUSE";
    if (triple) return "THREE OF A KIND";
    if (pairs >= 2) return "TWO PAIR";
    if (pairs == 1) return "ONE PAIR";
    return "NO HAND";
  }

  private static boolean matches(int[] values, int a, int b, int c, int d, int e) {
    return values[0] == a && values[1] == b && values[2] == c && values[3] == d
        && values[4] == e;
  }

  static int basicDamage(int baseDamage, int str, int handPercent) {
    return scaledDamage(baseDamage, CharacterProgressionCore.statPercent(str), handPercent);
  }

  static int skillDamage(int baseDamage, int skillPercent, int skl, int handPercent) {
    long skillBase = ((long)Math.max(1, baseDamage) * Math.max(0, skillPercent) + 50L) / 100L;
    return scaledDamage((int)Math.max(1L, skillBase),
        CharacterProgressionCore.statPercent(skl), handPercent);
  }

  static int ultimateDamage(int currentDamage, int hitCount, int bonusPercent, int handPercent) {
    long perHit = ((long)Math.max(1, currentDamage) * (100L + Math.max(0, bonusPercent)) + 50L)
        / 100L;
    long total = perHit * Math.max(1, hitCount);
    long scaled = (total * Math.max(0, handPercent) + 50L) / 100L;
    return Math.max(1, (int)Math.min(Integer.MAX_VALUE, scaled));
  }

  private static int scaledDamage(int baseDamage, int statPercent, int handPercent) {
    long scaled = (long)Math.max(1, baseDamage) * Math.max(0, statPercent) * Math.max(0, handPercent);
    return Math.max(1, (int)((scaled + 5_000L) / 10_000L));
  }

  static int defendedIncomingDamage(int rawDamage, int def) {
    int defPercent = CharacterProgressionCore.statPercent(def);
    long numerator = (long)Math.max(1, rawDamage) * 100L;
    return Math.max(1, (int)((numerator + defPercent / 2L) / defPercent));
  }

  static int deterministicDie(int seed, int sequence, int slot) {
    long mixed = (seed & 0xffffffffL) * 1_103_515_245L
        + (long)(sequence + 1) * 12_345L
        + (long)(slot + 1) * 2_654_435_761L;
    mixed ^= mixed >>> 17;
    mixed ^= mixed << 13;
    return 1 + (int)Math.floorMod(mixed, 6L);
  }

  public static JSONObject start(JSONObject state, String entityKey, int gmLogIndex) throws Exception {
    if (state == null) throw new IllegalArgumentException("state is required");
    String normalized = entityKey == null ? "" : entityKey.trim().toLowerCase(Locale.ROOT);
    EntityProfile profile = ENTITIES.get(normalized);
    if (profile == null || isActive(state)) return state;

    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);
    JSONArray participants = buildParticipants(state, progression);
    if (!hasLivingParticipant(participants)) return state;

    int stageIndex = LevelCore.stageIndex(state);
    JSONObject scaled = new EntityStatCore().profile(
        normalized, profile.maxHp, profile.damage, stageIndex);

    JSONObject combat = new JSONObject()
        .put("active", true)
        .put("round", 1)
        .put("actorIndex", firstLivingIndex(participants))
        .put("rngSequence", 0)
        .put("seed", stableSeed(state, normalized, participants))
        .put("logIndex", Math.max(0, gmLogIndex))
        .put("participants", participants)
        .put("stageIndex", stageIndex)
        .put("entity", new JSONObject()
            .put("key", profile.key)
            .put("name", profile.name)
            .put("hp", scaled.getInt("maxHp"))
            .put("maxHp", scaled.getInt("maxHp"))
            .put("attack", scaled.getInt("damage"))
            .put("baseHp", profile.maxHp)
            .put("baseDamage", profile.damage)
            .put("stagePercent", scaled.getInt("stagePercent"))
            .put("bleedTurns", 0)
            .put("bleedPercent", 0)
            .put("poisonTurns", 0)
            .put("poisonPercent", 0)
            .put("armorBreakTurns", 0)
            .put("armorBreakPercent", 0)
            .put("accuracyPenaltyTurns", 0)
            .put("accuracyPenalty", 0)
            .put("stunTurns", 0));

    state.put("combat", combat);
    prepareCurrentTurn(combat);
    return state;
  }

  public static JSONObject setHold(JSONObject state, int dieIndex, boolean held) throws Exception {
    if (!isActive(state)) throw new IllegalStateException("Không có trận chiến đang hoạt động.");
    if (dieIndex < 0 || dieIndex >= DICE_COUNT) throw new IllegalArgumentException("Die index không hợp lệ.");
    JSONObject dice = diceState(state.getJSONObject("combat"));
    if (!dice.optBoolean("hasRolled", false)) {
      throw new IllegalStateException("Phải ROLL trước khi HOLD.");
    }
    if (dice.optBoolean("finalized", false)) {
      throw new IllegalStateException("Hand đã được chốt.");
    }
    dice.getJSONArray("held").put(dieIndex, held);
    return state;
  }

  public static JSONObject roll(JSONObject state) throws Exception {
    if (!isActive(state)) throw new IllegalStateException("Không có trận chiến đang hoạt động.");
    JSONObject combat = state.getJSONObject("combat");
    JSONObject dice = diceState(combat);
    if (dice.optBoolean("finalized", false)) return state;

    boolean hasRolled = dice.optBoolean("hasRolled", false);
    if (!hasRolled) {
      rollDice(combat, dice, false);
      dice.put("hasRolled", true).put("rerollsUsed", 0);
      updateHand(dice);
      return state;
    }

    int rerollsUsed = Math.max(0, dice.optInt("rerollsUsed", 0));
    if (rerollsUsed >= MAX_REROLLS || allHeld(dice.getJSONArray("held"))) return state;

    rollDice(combat, dice, true);
    dice.put("rerollsUsed", rerollsUsed + 1);
    updateHand(dice);
    return state;
  }

  public static JSONObject finishHand(JSONObject state) throws Exception {
    if (!isActive(state)) throw new IllegalStateException("Không có trận chiến đang hoạt động.");
    JSONObject dice = diceState(state.getJSONObject("combat"));
    if (!dice.optBoolean("hasRolled", false)) {
      throw new IllegalStateException("Dice chưa có Initial Roll.");
    }
    if (!dice.optBoolean("finalized", false)) finalizeHand(dice);
    return state;
  }

  public static JSONObject resolveFinalized(JSONObject state) throws Exception {
    if (!isActive(state)) return state;
    JSONObject combat = state.getJSONObject("combat");
    JSONObject dice = diceState(combat);
    if (!dice.optBoolean("finalized", false)) {
      throw new IllegalStateException("Hand chưa được chốt.");
    }
    if (dice.optBoolean("resolved", false)) return state;

    combat.put("feedbackEvents", new JSONArray());
    combat.put("resolvedEntityTurn", false);

    JSONArray participants = combat.getJSONArray("participants");
    int actorIndex = currentLivingIndex(combat, participants);
    if (actorIndex < 0) {
      finishDefeat(state, combat);
      return state;
    }
    JSONObject actor = participants.getJSONObject(actorIndex);
    combat.put("actorIndex", actorIndex);
    combat.put("resolvedActorIndex", actorIndex);
    combat.put("resolvedActorName", actor.optString("name", "Nhân vật"));
    combat.put("resolvedRound", Math.max(1, combat.optInt("round", 1)));

    JSONObject entity = combat.getJSONObject("entity");
    if (actorIndex == firstLivingIndex(participants) && combat.optInt("round", 1) > 1) {
      tickRoundStartEffects(combat, entity);
      if (entity.optInt("hp", 0) <= 0) {
        dice.put("resolved", true);
        syncParticipants(state, participants);
        finishVictory(state, combat, entity);
        return state;
      }
    }

    String hand = dice.optString("hand", "NO HAND");
    ActionResult result = resolveHandAction(combat, actor, entity, hand);
    dice.put("resolved", true);

    String entitySummary = "";
    if (entity.optInt("hp", 0) > 0) {
      combat.put("resolvedEntityTurn", true);
      entitySummary = resolveEntityResponse(combat, actor, entity, result.evadeResponse);
    }

    syncParticipants(state, participants);

    if (entity.optInt("hp", 0) <= 0) {
      finishVictory(state, combat, entity);
    } else if (isCaoMinhDown(participants) || !hasLivingParticipant(participants)) {
      finishDefeat(state, combat);
    } else {
      advanceActor(combat);
      combat.put("nextActorIndex", combat.optInt("actorIndex", 0));
      prepareCurrentTurn(combat);
    }

    appendBattleLine(state, combat, result.summary);
    appendBattleLine(state, combat, entitySummary);
    return state;
  }

  private static ActionResult resolveHandAction(JSONObject combat, JSONObject actor, JSONObject entity,
                                                String hand) throws Exception {
    ActionResult result = new ActionResult();
    String actorName = actor.optString("name", "Nhân vật");
    int str = actor.optInt("STR", CharacterProgressionCore.BASE_STAT);
    int skl = actor.optInt("SKL", CharacterProgressionCore.BASE_STAT);
    int baseAttack = Math.max(1, actor.optInt("baseAttack", CAO_MINH_BASE_ATTACK));

    if ("NO HAND".equals(hand) || "ONE PAIR".equals(hand) || "TWO PAIR".equals(hand)) {
      int hpBefore = Math.max(0, entity.optInt("hp", 0));
      int handPercent = "ONE PAIR".equals(hand) ? 125 : 100;
      int damage = basicDamage(baseAttack, str, handPercent);
      applyEntityDamage(combat, entity, damage);
      List<String> effects = applyCharacterProcs(combat, actor, entity, baseAttack);
      String action = "TWO PAIR".equals(hand) ? "né và phản công" : "đánh thường";
      result.evadeResponse = "TWO PAIR".equals(hand);
      result.summary = actorBattleSummary(hand, actorName, action, false, entity, hpBefore, effects);
      return result;
    }

    if ("SSF".equals(hand) || "FSF".equals(hand)) {
      int handPercent = "FSF".equals(hand) ? 200 : 100;
      Ultimate ultimate = ULTIMATES.get(actor.optString("id", ""));
      if (ultimate == null || ultimate.hitCount <= 0) {
        result.summary = "[" + handToken(hand) + "] " + actorName + " chưa có Ultimate authoritative.";
        return result;
      }
      int hpBefore = Math.max(0, entity.optInt("hp", 0));
      int currentDamage = basicDamage(baseAttack, str, 100);
      int damage = ultimateDamage(currentDamage, ultimate.hitCount, ultimate.bonusPercent, handPercent);
      applyEntityDamage(combat, entity, damage);
      result.summary = actorBattleSummary(
          hand, actorName, ultimate.name, true, entity, hpBefore, new ArrayList<String>());
      return result;
    }

    int handPercent = skillHandPercent(hand);
    JSONObject selected = combat.optJSONObject("currentSkill");
    if (selected == null || selected.optString("name", "").isEmpty()) {
      result.summary = "[" + handToken(hand) + "] " + actorName + " không có Skill authoritative.";
      return result;
    }

    int hpBefore = Math.max(0, entity.optInt("hp", 0));
    int damage = skillDamage(baseAttack, selected.optInt("damagePercent", 100), skl, handPercent);
    applyEntityDamage(combat, entity, damage);

    List<String> effects = new ArrayList<>();
    String selectedEffect = selected.optString("effect", "");
    applySkillEffect(entity, selected);
    if (isTrackedStatusEffect(selectedEffect)) effects.add(selectedEffect);
    effects.addAll(applyCharacterProcs(combat, actor, entity, baseAttack));

    result.summary = actorBattleSummary(
        hand, actorName, selected.optString("name", "Skill"), true, entity, hpBefore, effects);
    return result;
  }

  private static int skillHandPercent(String hand) {
    if ("STRAIGHT".equals(hand)) return 150;
    if ("FULL HOUSE".equals(hand)) return 200;
    if ("FOUR OF A KIND".equals(hand)) return 250;
    return 100;
  }

  private static String handToken(String hand) {
    if ("ONE PAIR".equals(hand)) return "PAIR";
    if ("THREE OF A KIND".equals(hand)) return "TRIPLE";
    if ("FOUR OF A KIND".equals(hand)) return "F.O.A.K";
    return hand;
  }

  private static String canonicalStatusEffect(String effect) {
    if ("Chảy máu".equals(effect)
        || "Trúng độc".equals(effect)
        || "Xuyên giáp".equals(effect)
        || "Choáng".equals(effect)
        || "Mất phương hướng".equals(effect)) {
      return effect;
    }
    if ("Phá giáp".equals(effect)) return "Xuyên giáp";
    return "";
  }

  private static boolean isTrackedStatusEffect(String effect) {
    return !canonicalStatusEffect(effect).isEmpty();
  }

  private static String actorBattleSummary(String hand, String actorName, String action,
                                           boolean usesSkillVerb, JSONObject entity, int hpBefore,
                                           List<String> effects) {
    int hp = Math.max(0, entity.optInt("hp", 0));
    int maxHp = Math.max(1, entity.optInt("maxHp", 1));
    int dealt = Math.max(0, hpBefore - hp);
    StringBuilder text = new StringBuilder()
        .append("[").append(handToken(hand)).append("] ")
        .append(actorName).append(usesSkillVerb ? " dùng " : " ")
        .append(action).append(", ")
        .append(entity.optString("name", "Entity"))
        .append(" -").append(dealt).append(" HP [")
        .append(hp).append("/").append(maxHp).append(" HP]");
    String effectText = effectSummary(entity, effects);
    if (!effectText.isEmpty()) text.append(" và bị ").append(effectText);
    return text.append(".").toString();
  }

  private static String effectSummary(JSONObject entity, List<String> effects) {
    List<String> unique = new ArrayList<>();
    if (effects != null) {
      for (String effect : effects) {
        String canonical = canonicalStatusEffect(effect);
        if (!canonical.isEmpty() && !unique.contains(canonical)) unique.add(canonical);
      }
    }

    List<String> details = new ArrayList<>();
    for (String effect : unique) {
      if ("Chảy máu".equals(effect)) {
        int turns = Math.max(0, entity.optInt("bleedTurns", 0));
        if (turns > 0) details.add("Chảy máu trong " + turns + " lượt");
      } else if ("Trúng độc".equals(effect)) {
        int turns = Math.max(0, entity.optInt("poisonTurns", 0));
        if (turns > 0) details.add("Trúng độc trong " + turns + " lượt");
      } else if ("Xuyên giáp".equals(effect)) {
        int turns = Math.max(0, entity.optInt("armorBreakTurns", 0));
        int percent = Math.max(0, entity.optInt("armorBreakPercent", 0));
        if (turns > 0 && percent > 0) {
          details.add("Xuyên giáp " + percent + "% trong " + turns + " lượt");
        }
      } else if ("Choáng".equals(effect)) {
        int turns = Math.max(0, entity.optInt("stunTurns", 0));
        if (turns > 0) details.add("Choáng trong " + turns + " lượt");
      } else if ("Mất phương hướng".equals(effect)) {
        int turns = Math.max(0, entity.optInt("accuracyPenaltyTurns", 0));
        int percent = Math.max(0, entity.optInt("accuracyPenalty", 0));
        if (turns > 0 && percent > 0) {
          details.add("Mất phương hướng -" + percent + "% chính xác trong " + turns + " lượt");
        }
      }
    }
    return String.join(", ", details);
  }

  private static void applyEntityDamage(JSONObject combat, JSONObject entity, int damage)
      throws Exception {
    int raw = Math.max(1, damage);
    int armorBreak = entity.optInt("armorBreakTurns", 0) > 0
        ? Math.max(0, entity.optInt("armorBreakPercent", 0)) : 0;
    int resolved = Math.max(1, (int)(((long)raw * (100L + armorBreak) + 50L) / 100L));
    int hp = Math.max(0, entity.optInt("hp", 0) - resolved);
    entity.put("hp", hp);
    addFeedback(combat, "actor", "entity", "damage", "-" + resolved + " HP", true);
  }

  private static void applySkillEffect(JSONObject entity, JSONObject selected) throws Exception {
    if (entity.optInt("hp", 0) <= 0) return;
    applyStackingEffect(entity,
        selected.optString("effect", ""),
        Math.max(0, selected.optInt("effectTurns", 0)),
        Math.max(0, selected.optInt("effectValue", 0)));
  }

  private static List<String> applyCharacterProcs(JSONObject combat, JSONObject actor,
                                                   JSONObject entity, int baseAttack) throws Exception {
    List<String> effects = new ArrayList<>();
    if (entity.optInt("hp", 0) <= 0) return effects;
    String actorId = CharacterProgressionCore.normalizeCharacterId(actor.optString("id", ""));
    List<CharacterProc> pool = CHARACTER_PROCS.get(actorId);
    if (pool == null || pool.isEmpty()) return effects;

    for (CharacterProc proc : pool) {
      int sequence = combat.optInt("rngSequence", 0);
      int roll = characterProcRoll(combat.optInt("seed", 1), sequence, actorId, proc.name);
      combat.put("rngSequence", sequence + 1);
      if (roll >= proc.procPercent) continue;

      int bonus = Math.max(1,
          (int)(((long)Math.max(1, baseAttack) * proc.bonusDamagePercent + 50L) / 100L));
      applyEntityDamage(combat, entity, bonus);
      if (entity.optInt("hp", 0) <= 0) return effects;
      applyStackingEffect(entity, proc.effect, proc.effectTurns, proc.effectValue);
      if (isTrackedStatusEffect(proc.effect)) effects.add(proc.effect);
    }
    return effects;
  }

  static void applyStackingEffect(JSONObject entity, String effect, int turns, int value)
      throws Exception {
    effect = canonicalStatusEffect(effect);
    if ("Choáng".equals(effect) && turns > 0) {
      // Stun is the only effect whose duration stacks. Cap prevents permanent stun-lock.
      entity.put("stunTurns", Math.min(3,
          Math.max(0, entity.optInt("stunTurns", 0)) + turns));
      return;
    }

    if ("Chảy máu".equals(effect) && turns > 0 && value > 0) {
      int currentTurns = Math.max(0, entity.optInt("bleedTurns", 0));
      int currentPercent = currentTurns > 0 ? Math.max(0, entity.optInt("bleedPercent", 0)) : 0;
      entity.put("bleedTurns", currentTurns > 0 ? currentTurns : turns);
      entity.put("bleedPercent", Math.min(25, currentPercent + value));
      return;
    }

    if ("Trúng độc".equals(effect) && turns > 0 && value > 0) {
      int currentTurns = Math.max(0, entity.optInt("poisonTurns", 0));
      int currentPercent = currentTurns > 0 ? Math.max(0, entity.optInt("poisonPercent", 0)) : 0;
      entity.put("poisonTurns", currentTurns > 0 ? currentTurns : turns);
      entity.put("poisonPercent", Math.min(25, currentPercent + value));
      return;
    }

    if ("Xuyên giáp".equals(effect) && turns > 0 && value > 0) {
      int currentTurns = Math.max(0, entity.optInt("armorBreakTurns", 0));
      int currentPercent = currentTurns > 0 ? Math.max(0, entity.optInt("armorBreakPercent", 0)) : 0;
      entity.put("armorBreakTurns", currentTurns > 0 ? currentTurns : turns);
      entity.put("armorBreakPercent", Math.min(75, currentPercent + value));
      return;
    }

    if ("Mất phương hướng".equals(effect) && turns > 0 && value > 0) {
      entity.put("accuracyPenaltyTurns",
          Math.max(Math.max(0, entity.optInt("accuracyPenaltyTurns", 0)), turns));
      entity.put("accuracyPenalty",
          Math.max(Math.max(0, entity.optInt("accuracyPenalty", 0)), Math.min(95, value)));
    }
  }

  private static void consumeAccuracyPenaltyTurn(JSONObject entity) throws Exception {
    int turns = Math.max(0, entity.optInt("accuracyPenaltyTurns", 0));
    if (turns <= 0) return;
    int remaining = turns - 1;
    entity.put("accuracyPenaltyTurns", remaining);
    if (remaining == 0) entity.put("accuracyPenalty", 0);
  }

  private static String resolveEntityResponse(JSONObject combat, JSONObject actor, JSONObject entity,
                                              boolean evade) throws Exception {
    String entityName = entity.optString("name", "Entity");
    String actorName = actor.optString("name", "Nhân vật");
    int stunTurns = Math.max(0, entity.optInt("stunTurns", 0));
    if (stunTurns > 0) {
      entity.put("stunTurns", stunTurns - 1);
      consumeAccuracyPenaltyTurn(entity);
      addFeedback(combat, "entity", "actor", "miss", "", false);
      return entityName + " bị Choáng, không thể tấn công " + actorName + ".";
    }

    if (evade) {
      consumeAccuracyPenaltyTurn(entity);
      addFeedback(combat, "entity", "actor", "miss", "", false);
      return entityName + " tấn công " + actorName + " nhưng " + actorName + " né được.";
    }

    int accuracyTurns = Math.max(0, entity.optInt("accuracyPenaltyTurns", 0));
    int accuracyPenalty = accuracyTurns > 0
        ? Math.max(0, Math.min(95, entity.optInt("accuracyPenalty", 0))) : 0;
    if (accuracyPenalty > 0) {
      int roll = nextPercent(combat,
          "accuracy:" + entity.optString("key", "") + ":" + actor.optString("id", ""));
      if (roll < accuracyPenalty) {
        consumeAccuracyPenaltyTurn(entity);
        addFeedback(combat, "entity", "actor", "miss", "", false);
        return entityName + " bị Mất phương hướng và đánh trượt " + actorName + ".";
      }
    }

    int before = Math.max(0, actor.optInt("hp", 0));
    int rawDamage = Math.max(1, entity.optInt("attack", 1));
    int def = actor.optInt("DEF", CharacterProgressionCore.BASE_STAT);
    List<EntitySkill> pool = ENTITY_SKILLS.get(entity.optString("key", ""));
    List<String> triggeredSkills = new ArrayList<>();
    if (pool != null) {
      int seed = combat.optInt("seed", 1);
      int round = Math.max(1, combat.optInt("round", 1));
      int actorIndex = Math.max(0, combat.optInt("actorIndex", 0));
      for (int i = 0; i < pool.size(); i++) {
        EntitySkill skill = pool.get(i);
        if (entitySkillProcRoll(seed, round, actorIndex, i) >= skill.procPercent) continue;
        triggeredSkills.add(skill.name);
        int damage = defendedIncomingDamage(entitySkillDamage(rawDamage, skill.damagePercent), def);
        actor.put("hp", Math.max(0, actor.optInt("hp", 0) - damage));
        addFeedback(combat, "entity", "actor", "damage", "-" + damage + " HP", true);
      }
    }

    if (triggeredSkills.isEmpty()) {
      int damage = defendedIncomingDamage(rawDamage, def);
      actor.put("hp", Math.max(0, actor.optInt("hp", 0) - damage));
      addFeedback(combat, "entity", "actor", "damage", "-" + damage + " HP", true);
    }

    int hp = Math.max(0, actor.optInt("hp", 0));
    int maxHp = Math.max(1, actor.optInt("maxHp", 1));
    int dealt = Math.max(0, before - hp);
    String action = triggeredSkills.isEmpty()
        ? "tấn công"
        : "dùng " + String.join(" + ", triggeredSkills);
    consumeAccuracyPenaltyTurn(entity);
    return entityName + " " + action + ", " + actorName + " -" + dealt
        + " HP [" + hp + "/" + maxHp + " HP].";
  }

  private static void tickRoundStartEffects(JSONObject combat, JSONObject entity) throws Exception {
    if (entity.optInt("hp", 0) <= 0) return;

    int bleedTurns = Math.max(0, entity.optInt("bleedTurns", 0));
    int bleedPercent = Math.max(0, entity.optInt("bleedPercent", 0));
    if (bleedTurns > 0 && bleedPercent > 0) {
      int damage = Math.max(1, entity.optInt("maxHp", 1) * bleedPercent / 100);
      entity.put("hp", Math.max(0, entity.optInt("hp", 0) - damage));
      int remaining = bleedTurns - 1;
      entity.put("bleedTurns", remaining);
      if (remaining == 0) entity.put("bleedPercent", 0);
      addFeedback(combat, "actor", "entity", "damage", "-" + damage + " HP", true);
    }

    if (entity.optInt("hp", 0) > 0) {
      int poisonTurns = Math.max(0, entity.optInt("poisonTurns", 0));
      int poisonPercent = Math.max(0, entity.optInt("poisonPercent", 0));
      if (poisonTurns > 0 && poisonPercent > 0) {
        int damage = Math.max(1, entity.optInt("maxHp", 1) * poisonPercent / 100);
        entity.put("hp", Math.max(0, entity.optInt("hp", 0) - damage));
        int remaining = poisonTurns - 1;
        entity.put("poisonTurns", remaining);
        if (remaining == 0) entity.put("poisonPercent", 0);
        addFeedback(combat, "actor", "entity", "damage", "-" + damage + " HP", true);
      }
    }

    int armorTurns = Math.max(0, entity.optInt("armorBreakTurns", 0));
    if (armorTurns > 0) {
      int remaining = armorTurns - 1;
      entity.put("armorBreakTurns", remaining);
      if (remaining == 0) entity.put("armorBreakPercent", 0);
    }
  }

  private static JSONArray buildParticipants(JSONObject state, CharacterProgressionCore progression)
      throws Exception {
    JSONArray output = new JSONArray();
    JSONObject player = state.optJSONObject("player");
    String playerName = player == null ? "Cao Minh" : player.optString("name", "Cao Minh");
    JSONObject cao = progression.profile(state, "cao_minh");
    output.put(participant("cao_minh", playerName, -1, player, cao, CAO_MINH_BASE_ATTACK));

    JSONArray party = state.optJSONArray("party");
    if (party == null) return output;
    for (int i = 0; i < party.length() && output.length() < MAX_COMBAT_PARTICIPANTS; i++) {
      JSONObject member = party.optJSONObject(i);
      if (member == null || !CharacterEncounterCore.isJoinedMember(member)) continue;
      String id = CharacterProgressionCore.normalizeCharacterId(
          member.optString("id", member.optString("name", "")));
      if (id.isEmpty() || "cao_minh".equals(id)) continue;
      String name = member.optString("name", id);
      int baseAttack = "syvial".equals(id) ? 32 : "iris".equals(id) ? 28 : 24;
      output.put(participant(id, name, i, member, progression.profile(state, id), baseAttack));
    }
    return output;
  }

  private static JSONObject participant(String id, String name, int sourceIndex, JSONObject source,
                                        JSONObject profile, int fallbackAttack) throws Exception {
    JSONObject stats = profile.getJSONObject("stats");
    int maxHp = Math.max(1, profile.getInt("maxHp"));
    int hp = Math.max(0, Math.min(profile.getInt("currentHp"), maxHp));
    int baseAttack = firstPositive(source, fallbackAttack, "attackMax", "attack", "ATK");
    return new JSONObject()
        .put("id", id)
        .put("name", name)
        .put("sourceIndex", sourceIndex)
        .put("hp", hp)
        .put("maxHp", maxHp)
        .put("baseAttack", baseAttack)
        .put("STR", stats.getInt("STR"))
        .put("DEF", stats.getInt("DEF"))
        .put("SKL", stats.getInt("SKL"))
        .put("VIT", stats.getInt("VIT"));
  }

  private static int firstPositive(JSONObject source, int fallback, String... keys) {
    if (source != null) {
      for (String key : keys) {
        int value = source.optInt(key, 0);
        if (value > 0) return value;
      }
    }
    return fallback;
  }

  private static int stableSeed(JSONObject state, String entityKey, JSONArray participants) {
    String basis = entityKey + "|" + state.optInt("turn", 1) + "|"
        + state.optString(LevelCore.LEVEL_KEY, "0") + "|" + participants.toString();
    int hash = basis.hashCode();
    return hash == Integer.MIN_VALUE ? 1 : Math.abs(hash);
  }

  private static JSONObject newDiceState() throws Exception {
    JSONArray values = new JSONArray();
    JSONArray held = new JSONArray();
    for (int i = 0; i < DICE_COUNT; i++) {
      values.put(0);
      held.put(false);
    }
    return new JSONObject()
        .put("values", values)
        .put("held", held)
        .put("hasRolled", false)
        .put("rerollsUsed", 0)
        .put("maxRerolls", MAX_REROLLS)
        .put("finalized", false)
        .put("resolved", false)
        .put("hand", "");
  }

  private static JSONObject diceState(JSONObject combat) throws Exception {
    JSONObject dice = combat.optJSONObject("diceState");
    if (dice == null) throw new IllegalStateException("Combat dice state bị thiếu.");
    JSONArray values = dice.optJSONArray("values");
    JSONArray held = dice.optJSONArray("held");
    if (values == null || held == null || values.length() != DICE_COUNT || held.length() != DICE_COUNT) {
      throw new IllegalStateException("Combat dice state không hợp lệ.");
    }
    return dice;
  }

  private static void ensureInitialRoll(JSONObject combat) throws Exception {
    JSONObject dice = combat.optJSONObject("diceState");
    if (dice == null) {
      dice = newDiceState();
      combat.put("diceState", dice);
    }
    dice = diceState(combat);
    if (!dice.optBoolean("hasRolled", false)) {
      rollDice(combat, dice, false);
      dice.put("hasRolled", true).put("rerollsUsed", 0);
    }
    if (dice.optString("hand", "").trim().isEmpty()) updateHand(dice);
  }

  private static void rollDice(JSONObject combat, JSONObject dice, boolean respectHeld)
      throws Exception {
    JSONArray values = dice.getJSONArray("values");
    JSONArray held = dice.getJSONArray("held");
    for (int i = 0; i < DICE_COUNT; i++) {
      if (respectHeld && held.optBoolean(i, false)) continue;
      int sequence = combat.optInt("rngSequence", 0);
      int value = deterministicDie(combat.optInt("seed", 1), sequence, i);
      combat.put("rngSequence", sequence + 1);
      values.put(i, value);
    }
  }

  private static void updateHand(JSONObject dice) throws Exception {
    JSONArray values = dice.getJSONArray("values");
    int[] hand = new int[DICE_COUNT];
    for (int i = 0; i < DICE_COUNT; i++) hand[i] = values.optInt(i, 0);
    dice.put("hand", classify(hand));
  }

  private static boolean allHeld(JSONArray held) {
    if (held == null || held.length() != DICE_COUNT) return false;
    for (int i = 0; i < DICE_COUNT; i++) if (!held.optBoolean(i, false)) return false;
    return true;
  }

  private static void finalizeHand(JSONObject dice) throws Exception {
    updateHand(dice);
    dice.put("finalized", true);
  }

  private static void prepareCurrentTurn(JSONObject combat) throws Exception {
    JSONArray participants = combat.getJSONArray("participants");
    int actorIndex = currentLivingIndex(combat, participants);
    if (actorIndex < 0) return;
    combat.put("actorIndex", actorIndex);
    JSONObject actor = participants.getJSONObject(actorIndex);
    String id = actor.optString("id", "");

    List<Skill> pool = SKILLS.get(id);
    if (pool != null && !pool.isEmpty()) {
      int index = Math.floorMod(
          combat.optInt("seed", 1) + combat.optInt("round", 1) * 31 + actorIndex * 17,
          pool.size());
      combat.put("currentSkill", skillJson(pool.get(index)));
    } else {
      combat.remove("currentSkill");
    }

    Ultimate ultimate = ULTIMATES.get(id);
    if (ultimate != null) {
      combat.put("currentUltimate", new JSONObject()
          .put("name", ultimate.name)
          .put("hitCount", ultimate.hitCount)
          .put("bonusPercent", ultimate.bonusPercent)
          .put("damageFormula", "100% current DMG + " + ultimate.bonusPercent + "% Bonus DMG per hit"));
    } else {
      combat.remove("currentUltimate");
    }

    combat.put("currentActor", actor.optString("name", "Nhân vật"));
    combat.put("diceState", newDiceState());
    ensureInitialRoll(combat);
  }

  private static JSONObject skillJson(Skill skill) throws Exception {
    return new JSONObject()
        .put("name", skill.name)
        .put("description", skill.description)
        .put("damagePercent", skill.damagePercent)
        .put("effect", skill.effect)
        .put("effectTurns", skill.effectTurns)
        .put("effectValue", skill.effectValue);
  }

  private static int currentLivingIndex(JSONObject combat, JSONArray participants) {
    int current = combat.optInt("actorIndex", 0);
    if (current >= 0 && current < participants.length()) {
      JSONObject actor = participants.optJSONObject(current);
      if (actor != null && actor.optInt("hp", 0) > 0) return current;
    }
    for (int step = 1; step <= participants.length(); step++) {
      int candidate = Math.floorMod(current + step, participants.length());
      JSONObject actor = participants.optJSONObject(candidate);
      if (actor != null && actor.optInt("hp", 0) > 0) return candidate;
    }
    return -1;
  }

  private static int firstLivingIndex(JSONArray participants) {
    for (int i = 0; i < participants.length(); i++) {
      JSONObject actor = participants.optJSONObject(i);
      if (actor != null && actor.optInt("hp", 0) > 0) return i;
    }
    return 0;
  }

  private static void advanceActor(JSONObject combat) throws Exception {
    JSONArray participants = combat.getJSONArray("participants");
    int current = combat.optInt("actorIndex", 0);
    for (int step = 1; step <= participants.length(); step++) {
      int candidate = (current + step) % participants.length();
      JSONObject participant = participants.optJSONObject(candidate);
      if (participant == null || participant.optInt("hp", 0) <= 0) continue;
      if (candidate <= current) {
        combat.put("round", Math.max(1, combat.optInt("round", 1)) + 1);
      }
      combat.put("actorIndex", candidate);
      return;
    }
  }

  private static boolean hasLivingParticipant(JSONArray participants) {
    for (int i = 0; i < participants.length(); i++) {
      JSONObject participant = participants.optJSONObject(i);
      if (participant != null && participant.optInt("hp", 0) > 0) return true;
    }
    return false;
  }

  private static boolean isCaoMinhDown(JSONArray participants) {
    for (int i = 0; i < participants.length(); i++) {
      JSONObject participant = participants.optJSONObject(i);
      if (participant != null && "cao_minh".equals(participant.optString("id", ""))) {
        return participant.optInt("hp", 0) <= 0;
      }
    }
    return false;
  }

  private static int nextPercent(JSONObject combat, String salt) throws Exception {
    int sequence = combat.optInt("rngSequence", 0);
    int seed = combat.optInt("seed", 1);
    long mixed = (seed & 0xffffffffL) * 31L + (long)(sequence + 1) * 131L
        + (long)salt.hashCode() * 17L;
    int value = (int)Math.floorMod(mixed, 100L);
    combat.put("rngSequence", sequence + 1);
    return value;
  }

  private static void finishVictory(JSONObject state, JSONObject combat, JSONObject entity)
      throws Exception {
    String entityKey = entity.optString("key", "entity");

    if (!combat.optBoolean("lootResolved", false)) {
      int rate = ItemCore.entityDropRatePercent(entityKey);
      int roll = nextPercent(combat, "loot-drop:" + entityKey);
      combat.put("entityLootRatePercent", rate).put("entityLootRoll", roll);
      if (ItemCore.shouldDropEntityLoot(roll, rate)) {
        String itemName = ItemCore.grantEntityLootItem(
            state, nextPercent(combat, "loot-item:" + entityKey));
        combat.put("droppedItem", itemName);
      }
      combat.put("lootResolved", true);
    }

    if (!combat.optBoolean("coreDropResolved", false)) {
      int roll = nextPercent(combat, "core-drop:" + entityKey);
      int reward = 0;
      if (ItemCore.shouldDropCore(roll, ItemCore.CORE_ENTITY_DROP_PERCENT)) {
        reward = CharacterProgressionCore.bundleSize(LevelCore.stageIndex(state));
        new CharacterProgressionCore().grantCore(state, reward);
      }
      combat.put("coreDropRoll", roll)
          .put("coreDropReward", reward)
          .put("coreDropResolved", true);
    }

    combat.put("active", false).put("outcome", "victory");
    clearEncounterFlag(state);
  }

  private static void finishDefeat(JSONObject state, JSONObject combat) throws Exception {
    combat.put("active", false).put("outcome", "defeat");
    if (!combat.optBoolean("deathRecoveryApplied", false)) {
      CharacterProgressionCore progression = new CharacterProgressionCore();
      progression.applyCaoMinhDeathPenalty(state);
      LevelCore.resetToLevelZeroStart(state);
      combat.put("deathRecoveryApplied", true).put("playerRespawned", true);
    }
    clearEncounterFlag(state);
  }

  static void normalizeTerminalEncounter(JSONObject state) throws Exception {
    if (state == null) return;
    JSONObject combat = state.optJSONObject("combat");
    if (combat == null) return;
    if (combat.optBoolean("active", false)) {
      ensureInitialRoll(combat);
      return;
    }
    String outcome = combat.optString("outcome", "");
    if ("defeat".equals(outcome) && !combat.optBoolean("deathRecoveryApplied", false)) {
      finishDefeat(state, combat);
      return;
    }
    if ("victory".equals(outcome) || "defeat".equals(outcome)) clearEncounterFlag(state);
  }

  private static void clearEncounterFlag(JSONObject state) throws Exception {
    JSONObject flags = state.optJSONObject("flags");
    if (flags != null) flags.put("entityEncounterKey", "");
  }

  private static void syncParticipants(JSONObject state, JSONArray participants) throws Exception {
    CharacterProgressionCore progression = new CharacterProgressionCore();
    progression.normalizeState(state);
    JSONArray party = state.optJSONArray("party");

    for (int i = 0; i < participants.length(); i++) {
      JSONObject participant = participants.optJSONObject(i);
      if (participant == null) continue;
      String id = participant.optString("id", "");
      progression.setCurrentHp(state, id, participant.optInt("hp", 0));
      if (!"cao_minh".equals(id) && participant.optInt("hp", 0) <= 0) {
        progression.markCompanionDown(state, id);
      }
      JSONObject profile = progression.profile(state, id);
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

  private static void addFeedback(JSONObject combat, String phase, String target, String kind,
                                  String text, boolean flash) throws Exception {
    JSONArray events = combat.optJSONArray("feedbackEvents");
    if (events == null) events = new JSONArray();
    events.put(new JSONObject()
        .put("phase", phase)
        .put("target", target)
        .put("kind", kind)
        .put("text", text == null ? "" : text)
        .put("flash", flash)
        .put("actorIndex", combat.optInt("resolvedActorIndex", combat.optInt("actorIndex", 0))));
    combat.put("feedbackEvents", events);
  }

  private static void appendBattleLine(JSONObject state, JSONObject combat, String text)
      throws Exception {
    JSONArray log = state.optJSONArray("log");
    if (log == null || log.length() == 0 || text == null || text.trim().isEmpty()) return;
    int index = Math.max(0, Math.min(combat.optInt("logIndex", log.length() - 1), log.length() - 1));
    JSONObject entry = log.optJSONObject(index);
    if (entry == null) return;
    JSONArray battleLog = entry.optJSONArray("battleLog");
    if (battleLog == null) battleLog = new JSONArray();
    battleLog.put(new JSONObject()
        .put("text", text)
        .put("highlights", GmChoiceContract.semanticHighlights(text, state)));
    entry.put("battleLog", battleLog);
  }
}
