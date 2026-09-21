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

  private static final int HUYET_MA_24_TOTAL_DAMAGE = 240;
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

  private static final class Ultimate {
    final String name;
    final int exactDamage;

    Ultimate(String name, int exactDamage) {
      this.name = name;
      this.exactDamage = exactDamage;
    }
  }

  private static final class ActionResult {
    String summary;
    boolean evadeResponse;
  }

  private static final Map<String, EntityProfile> ENTITIES = new LinkedHashMap<>();
  private static final Map<String, List<Skill>> SKILLS = new LinkedHashMap<>();
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
        skill("Honeycomb Fire", "", 185, "Phá giáp", 2, 20),
        skill("Charged Shot", "", 175, "", 0, 0));

    skills("syvial",
        skill("Rift Sever", "", 175, "", 0, 0),
        skill("Crimson Guillotine", "", 190, "Chảy máu", 3, 4),
        skill("Lucifer Breaker", "", 155, "Choáng", 1, 0),
        skill("Spatial Dominion", "", 210, "Mất phương hướng", 2, 25));

    skills("lucia",
        skill("M4A1 Joint Attack", "", 150, "", 0, 0));

    // Canonical runtime data currently defines both identity and gameplay damage for Cao Minh's
    // Ultimate. Other characters intentionally remain unmapped until authoritative damage exists.
    ULTIMATES.put("cao_minh",
        new Ultimate("Huyết Ma Nhị Thập Tứ Trảm", HUYET_MA_24_TOTAL_DAMAGE));
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

  static Map<String, String> semanticCatalog() {
    Map<String, String> output = new LinkedHashMap<>();
    for (EntityProfile profile : ENTITIES.values()) output.put(profile.name, "entity");
    for (List<Skill> pool : SKILLS.values()) {
      for (Skill skill : pool) {
        output.put(skill.name, "skill");
        if (!skill.effect.trim().isEmpty()) output.put(skill.effect, "effect");
      }
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

  static int ultimateDamage(int baseDamage, int skl, int handPercent) {
    return scaledDamage(baseDamage, CharacterProgressionCore.statPercent(skl), handPercent);
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
    }

    String hand = dice.optString("hand", "NO HAND");
    ActionResult result = resolveHandAction(combat, actor, entity, hand);
    dice.put("resolved", true);

    if (entity.optInt("hp", 0) > 0) {
      combat.put("resolvedEntityTurn", true);
      resolveEntityResponse(combat, actor, entity, result.evadeResponse);
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
      int handPercent = "ONE PAIR".equals(hand) ? 125 : 100;
      int damage = basicDamage(baseAttack, str, handPercent);
      applyEntityDamage(combat, entity, damage);
      if ("TWO PAIR".equals(hand)) {
        result.evadeResponse = true;
        result.summary = "[TWO PAIR] " + actorName + " né và phản công.";
      } else if ("ONE PAIR".equals(hand)) {
        result.summary = "[PAIR] " + actorName + " đánh thường · 125% DMG.";
      } else {
        result.summary = "[NO HAND] " + actorName + " đánh thường.";
      }
      return result;
    }

    if ("SSF".equals(hand) || "FSF".equals(hand)) {
      int handPercent = "FSF".equals(hand) ? 200 : 100;
      Ultimate ultimate = ULTIMATES.get(actor.optString("id", ""));
      if (ultimate == null || ultimate.exactDamage <= 0) {
        result.summary = "[" + hand + "] " + actorName + " chưa có Ultimate authoritative.";
        return result;
      }
      int damage = ultimateDamage(ultimate.exactDamage, skl, handPercent);
      applyEntityDamage(combat, entity, damage);
      result.summary = "[" + hand + "] " + actorName + " dùng " + ultimate.name
          + ("FSF".equals(hand) ? " · 200% DMG." : ".");
      return result;
    }

    int handPercent = skillHandPercent(hand);
    JSONObject selected = combat.optJSONObject("currentSkill");
    if (selected == null || selected.optString("name", "").isEmpty()) {
      result.summary = "[" + handToken(hand) + "] " + actorName + " không có Skill authoritative.";
      return result;
    }

    int damage = skillDamage(baseAttack, selected.optInt("damagePercent", 100), skl, handPercent);
    applyEntityDamage(combat, entity, damage);
    applySkillEffect(entity, selected);
    result.summary = "[" + handToken(hand) + "] " + actorName + " dùng "
        + selected.optString("name", "Skill")
        + (handPercent == 100 ? "." : " · " + handPercent + "% DMG.");
    return result;
  }

  private static int skillHandPercent(String hand) {
    if ("STRAIGHT".equals(hand)) return 150;
    if ("FULL HOUSE".equals(hand)) return 200;
    if ("FOUR OF A KIND".equals(hand)) return 250;
    return 100;
  }

  private static String handToken(String hand) {
    if ("THREE OF A KIND".equals(hand)) return "TRIPLE";
    return hand;
  }

  private static void applyEntityDamage(JSONObject combat, JSONObject entity, int damage)
      throws Exception {
    int hp = Math.max(0, entity.optInt("hp", 0) - Math.max(1, damage));
    entity.put("hp", hp);
    addFeedback(combat, "actor", "entity", "damage", "-" + Math.max(1, damage) + " HP", true);
  }

  private static void applySkillEffect(JSONObject entity, JSONObject selected) throws Exception {
    String effect = selected.optString("effect", "");
    int turns = Math.max(0, selected.optInt("effectTurns", 0));
    int value = Math.max(0, selected.optInt("effectValue", 0));
    if ("Choáng".equals(effect) && turns > 0) {
      entity.put("stunTurns", Math.max(entity.optInt("stunTurns", 0), turns));
    } else if ("Chảy máu".equals(effect) && turns > 0 && value > 0) {
      entity.put("bleedTurns", Math.max(entity.optInt("bleedTurns", 0), turns));
      entity.put("bleedPercent", Math.max(entity.optInt("bleedPercent", 0), value));
    }
    // Armor/evasion/accuracy effects are intentionally not projected into hidden stats. Poker
    // Dice owns evade and Entity has no DEF/AGI system in this ruleset.
  }

  private static void resolveEntityResponse(JSONObject combat, JSONObject actor, JSONObject entity,
                                            boolean evade) throws Exception {
    int stunTurns = Math.max(0, entity.optInt("stunTurns", 0));
    if (stunTurns > 0) {
      entity.put("stunTurns", stunTurns - 1);
      addFeedback(combat, "entity", "actor", "miss", "STUN", false);
      return;
    }

    if (evade) {
      addFeedback(combat, "entity", "actor", "miss", "EVADE", false);
      return;
    }

    int rawDamage = Math.max(1, entity.optInt("attack", 1));
    int def = actor.optInt("DEF", CharacterProgressionCore.BASE_STAT);
    int damage = defendedIncomingDamage(rawDamage, def);
    actor.put("hp", Math.max(0, actor.optInt("hp", 0) - damage));
    addFeedback(combat, "entity", "actor", "damage", "-" + damage + " HP", true);
  }

  private static void tickRoundStartEffects(JSONObject combat, JSONObject entity) throws Exception {
    int turns = Math.max(0, entity.optInt("bleedTurns", 0));
    int percent = Math.max(0, entity.optInt("bleedPercent", 0));
    if (turns <= 0 || percent <= 0 || entity.optInt("hp", 0) <= 0) return;
    int damage = Math.max(1, entity.optInt("maxHp", 1) * percent / 100);
    entity.put("hp", Math.max(0, entity.optInt("hp", 0) - damage));
    entity.put("bleedTurns", turns - 1);
    addFeedback(combat, "actor", "entity", "damage", "-" + damage + " HP", true);
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
          .put("exactDamage", ultimate.exactDamage));
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
    battleLog.put(new JSONObject().put("text", text).put("highlights", new JSONArray()));
    entry.put("battleLog", battleLog);
  }
}
