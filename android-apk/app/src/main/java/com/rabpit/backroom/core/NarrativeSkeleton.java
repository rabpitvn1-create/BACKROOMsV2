package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Locale;

/**
 * Read-only long-horizon campaign projection.
 *
 * Contract: this projection may summarize committed Facts, active/dormant Threads,
 * ArchivedThreadResidue and authoritative CurrentState only. It must never describe
 * a next event, scheduled turn, chapter/scene/beat, phase gate, plot cursor, fixed
 * encounter order, fixed ending, spawn schedule, candidate or eligibility rule.
 * Director may read attention hints only to weight candidates that are already eligible.
 */
final class NarrativeSkeleton {
  static final String ROOT_KEY = "narrativeSkeleton";
  static final int SCHEMA_VERSION = 1;

  private static final String[] TOP_LEVEL_FIELDS = {
      "schemaVersion", "campaignIdentity", "longTermTensions", "anchorMysteries",
      "importantRelationships", "unresolvedWorldQuestions", "convergenceConditions",
      "endingPossibilities", "attentionHints", "derivedFromCommitSeq", "derivedFromTurn"
  };

  private static final String[] FORBIDDEN_PLANNING_KEYS = {
      "nextEvent", "next_event", "nextTurnEvent", "turnSchedule", "scheduledTurn", "dueTurn",
      "phase", "phaseLabel", "phaseGate", "chapter", "scene", "beat", "plotCursor",
      "fixedEncounterOrder", "encounterOrder", "fixedEnding", "spawnSchedule", "spawnTurn",
      "candidate", "candidateId", "eligibility", "eligibilityRuleId", "unlockEligibility"
  };

  private NarrativeSkeleton() {}

  /** Keeps the persisted projection schema-safe. Returns true when a full rebuild is required. */
  static boolean normalize(JSONObject root, JSONObject state) throws Exception {
    boolean reset = root.has("skeleton");
    root.remove("skeleton");
    JSONObject current = root.optJSONObject(ROOT_KEY);
    if (current == null || !contractValid(current)) {
      root.put(ROOT_KEY, empty(state));
      return true;
    }
    return reset;
  }

  static void rebuild(JSONObject root, JSONObject state) throws Exception {
    JSONObject skeleton = empty(state);
    JSONArray tensions = skeleton.getJSONArray("longTermTensions");
    JSONArray mysteries = skeleton.getJSONArray("anchorMysteries");
    JSONArray relationships = skeleton.getJSONArray("importantRelationships");
    JSONArray questions = skeleton.getJSONArray("unresolvedWorldQuestions");
    JSONArray convergence = skeleton.getJSONArray("convergenceConditions");
    JSONArray endings = skeleton.getJSONArray("endingPossibilities");
    JSONArray hints = skeleton.getJSONArray("attentionHints");

    JSONArray threads = root.optJSONArray("threads");
    if (threads != null) {
      for (int i = 0; i < threads.length(); i++) {
        JSONObject thread = threads.optJSONObject(i);
        if (thread == null || !isUnresolved(thread.optString("status", ""))) continue;
        String threadId = thread.optString("threadId", "").trim();
        String threadType = thread.optString("threadType", "").trim();
        JSONArray refs = copyArray(thread.optJSONArray("keyRefs"));
        if (!threadId.isEmpty()) {
          tensions.put(new JSONObject()
              .put("tensionKey", "thread:" + threadId)
              .put("sourceType", "THREAD")
              .put("sourceRef", threadId)
              .put("threadType", threadType)
              .put("keyRefs", refs)
              .put("status", thread.optString("status", "ACTIVE")));
          questions.put(new JSONObject()
              .put("questionKey", "thread:" + threadId)
              .put("sourceThreadId", threadId)
              .put("threadType", threadType)
              .put("keyRefs", copyArray(refs)));
        }
        if (isMysteryType(threadType) && !threadId.isEmpty()) {
          mysteries.put(new JSONObject()
              .put("mysteryKey", "thread:" + threadId)
              .put("sourceThreadId", threadId)
              .put("threadType", threadType)
              .put("keyRefs", copyArray(refs)));
        }
        if (isRelationshipType(threadType) && !threadId.isEmpty()) {
          addRelationship(relationships, refs, "thread:" + threadId);
        }
        addHint(hints, attentionTag(threadType),
            "DORMANT".equals(thread.optString("status", "")) ? 0.35d : 0.65d,
            threadId.isEmpty() ? "" : "thread:" + threadId);
      }
    }

    JSONArray archive = root.optJSONArray("threadArchive");
    int currentTurn = Math.max(1, state == null ? 1 : state.optInt("turn", 1));
    if (archive != null) {
      for (int i = 0; i < archive.length(); i++) {
        JSONObject residue = archive.optJSONObject(i);
        if (residue == null || !residueRelevant(residue, currentTurn)) continue;
        String threadId = residue.optString("threadId", "").trim();
        JSONArray tags = residue.optJSONArray("tags");
        String threadType = tags == null ? "" : tags.optString(0, "");
        tensions.put(new JSONObject()
            .put("tensionKey", "residue:" + threadId)
            .put("sourceType", "ARCHIVED_THREAD_RESIDUE")
            .put("sourceRef", threadId)
            .put("threadType", threadType)
            .put("keyRefs", copyArray(residue.optJSONArray("keyRefs")))
            .put("resurfacePolicy", residue.optString("resurfacePolicy", "NEVER")));
        addHint(hints, attentionTag(threadType), 0.30d,
            threadId.isEmpty() ? "" : "residue:" + threadId);
      }
    }

    JSONArray facts = root.optJSONArray("historicalFacts");
    if (facts != null) {
      for (int i = 0; i < facts.length(); i++) {
        JSONObject fact = facts.optJSONObject(i);
        if (fact == null) continue;
        String predicate = fact.optString("predicate", "");
        String factId = fact.optString("factId", "");
        if (fact.optBoolean("impactEligible", true)
            && !"LOCAL".equals(fact.optString("impactScope", "LOCAL"))) {
          tensions.put(new JSONObject()
              .put("tensionKey", "fact:" + factId)
              .put("sourceType", "FACT")
              .put("sourceRef", factId)
              .put("predicate", predicate)
              .put("impactScope", fact.optString("impactScope", "LOCAL")));
        }
        if (isEndingPossibility(predicate)) {
          endings.put(new JSONObject()
              .put("possibilityKey", "fact:" + factId)
              .put("sourceFactId", factId)
              .put("predicate", predicate)
              .put("value", copyValue(fact.opt("value")))
              .put("fixed", false));
        }
        if (fact.optBoolean("impactEligible", true)) {
          addHint(hints, attentionTag(predicate), impactStrength(fact.optString("impactScope", "LOCAL")),
              factId.isEmpty() ? "" : "fact:" + factId);
        }
      }
    }

    JSONArray party = state == null ? null : state.optJSONArray("party");
    if (party != null) {
      for (int i = 0; i < party.length(); i++) {
        JSONObject member = party.optJSONObject(i);
        String actorId = member == null ? "" : member.optString("id", "").trim();
        if (!actorId.isEmpty()) {
          addRelationship(relationships,
              new JSONArray().put("cao_minh").put(actorId), "state:party:" + actorId);
        }
      }
    }

    double resourcePressure = survivalPressure(state);
    if (resourcePressure > 0.0d) {
      addHint(hints, "RESOURCE", resourcePressure, "state:survival");
      if (resourcePressure >= 0.35d) {
        tensions.put(new JSONObject()
            .put("tensionKey", "state:survival_resource_pressure")
            .put("sourceType", "CURRENT_STATE")
            .put("sourceRef", "state:survival")
            .put("pressure", resourcePressure));
      }
    }
    JSONObject combat = state == null ? null : state.optJSONObject("combat");
    if (combat != null && combat.optBoolean("active", false)) {
      addHint(hints, "DANGER", 1.0d, "state:combat");
    }
    JSONObject flags = state == null ? null : state.optJSONObject("flags");
    if (flags != null && !flags.optString("entityEncounterKey", "").trim().isEmpty()) {
      addHint(hints, "DANGER", 0.90d, "state:entityEncounter");
    }

    deriveConvergence(questions, convergence);
    skeleton.put("derivedFromCommitSeq", Math.max(0, root.optInt("commitSequence", 0)));
    skeleton.put("derivedFromTurn", currentTurn);
    root.put(ROOT_KEY, skeleton);
  }

  static boolean contractValid(JSONObject skeleton) {
    if (skeleton == null || skeleton.optInt("schemaVersion", -1) != SCHEMA_VERSION) return false;
    if (!(skeleton.opt("campaignIdentity") instanceof JSONObject)
        || !skeleton.has("derivedFromCommitSeq") || !skeleton.has("derivedFromTurn")) return false;
    String[] arrays = {
        "longTermTensions", "anchorMysteries", "importantRelationships",
        "unresolvedWorldQuestions", "convergenceConditions", "endingPossibilities", "attentionHints"
    };
    for (String key : arrays) if (!(skeleton.opt(key) instanceof JSONArray)) return false;

    Iterator<String> keys = skeleton.keys();
    while (keys.hasNext()) if (!isTopLevelField(keys.next())) return false;
    return !containsForbiddenPlanningKey(skeleton);
  }

  static double attentionStrength(JSONObject root, String tag) {
    JSONObject skeleton = root == null ? null : root.optJSONObject(ROOT_KEY);
    JSONArray hints = skeleton == null ? null : skeleton.optJSONArray("attentionHints");
    String expected = safe(tag).toUpperCase(Locale.ROOT);
    if (hints == null || expected.isEmpty()) return 0.0d;
    for (int i = 0; i < hints.length(); i++) {
      JSONObject hint = hints.optJSONObject(i);
      if (hint != null && expected.equals(hint.optString("tag", "").toUpperCase(Locale.ROOT))) {
        return clamp(hint.optDouble("strength", 0.0d));
      }
    }
    return 0.0d;
  }

  private static JSONObject empty(JSONObject state) throws Exception {
    return new JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("campaignIdentity", new JSONObject()
            .put("campaignKey", "BACKROOMS_V2")
            .put("premise", "Survive and explore the Backrooms; continuity emerges only from committed play.")
            .put("playerRef", "cao_minh"))
        .put("longTermTensions", new JSONArray())
        .put("anchorMysteries", new JSONArray())
        .put("importantRelationships", new JSONArray())
        .put("unresolvedWorldQuestions", new JSONArray())
        .put("convergenceConditions", new JSONArray())
        .put("endingPossibilities", new JSONArray())
        .put("attentionHints", new JSONArray())
        .put("derivedFromCommitSeq", 0)
        .put("derivedFromTurn", Math.max(1, state == null ? 1 : state.optInt("turn", 1)));
  }

  private static boolean isTopLevelField(String key) {
    for (String allowed : TOP_LEVEL_FIELDS) if (allowed.equals(key)) return true;
    return false;
  }

  private static boolean containsForbiddenPlanningKey(Object value) {
    if (value instanceof JSONObject) {
      JSONObject object = (JSONObject) value;
      Iterator<String> keys = object.keys();
      while (keys.hasNext()) {
        String key = keys.next();
        if (isForbiddenKey(key) || containsForbiddenPlanningKey(object.opt(key))) return true;
      }
    } else if (value instanceof JSONArray) {
      JSONArray array = (JSONArray) value;
      for (int i = 0; i < array.length(); i++) {
        if (containsForbiddenPlanningKey(array.opt(i))) return true;
      }
    }
    return false;
  }

  private static boolean isForbiddenKey(String key) {
    for (String forbidden : FORBIDDEN_PLANNING_KEYS) {
      if (forbidden.equalsIgnoreCase(safe(key))) return true;
    }
    return false;
  }

  private static boolean isUnresolved(String status) {
    return "ACTIVE".equals(status) || "DORMANT".equals(status);
  }

  private static boolean isMysteryType(String type) {
    String upper = safe(type).toUpperCase(Locale.ROOT);
    return upper.contains("MYSTERY") || upper.contains("UNKNOWN")
        || upper.contains("SECRET") || upper.contains("ORIGIN") || upper.contains("IDENTITY");
  }

  private static boolean isRelationshipType(String type) {
    String upper = safe(type).toUpperCase(Locale.ROOT);
    return upper.contains("SOCIAL") || upper.contains("CHARACTER") || upper.contains("RELATIONSHIP");
  }

  private static boolean isEndingPossibility(String predicate) {
    String lower = safe(predicate).toLowerCase(Locale.ROOT);
    return lower.startsWith("ending_possibility") || lower.startsWith("campaign_ending_possibility");
  }

  private static boolean residueRelevant(JSONObject residue, int currentTurn) {
    String policy = residue.optString("resurfacePolicy", "NEVER");
    if ("PERSISTENT".equals(policy) || "CONDITIONAL".equals(policy)) return true;
    return "DECAYING".equals(policy)
        && currentTurn <= residue.optInt("resolvedTurn", 0)
            + Math.max(0, residue.optInt("decayWindowTurns", 0));
  }

  private static String attentionTag(String source) {
    String upper = safe(source).toUpperCase(Locale.ROOT);
    if (upper.contains("ENTITY") || upper.contains("COMBAT")) return "DANGER";
    if (upper.contains("CHEST") || upper.contains("ITEM") || upper.contains("RESOURCE")
        || upper.contains("SURVIVAL")) return "RESOURCE";
    if (upper.contains("SOCIAL") || upper.contains("CHARACTER") || upper.contains("PARTY")
        || upper.contains("RELATIONSHIP")) return "SOCIAL";
    if (upper.contains("LEVEL") || upper.contains("ROUTE") || upper.contains("ENVIRONMENT")
        || upper.contains("LOCATION")) return "ENVIRONMENTAL";
    return upper.isEmpty() ? "" : "CONSEQUENCE";
  }

  private static double impactStrength(String scope) {
    switch (safe(scope).toUpperCase(Locale.ROOT)) {
      case "PERSISTENT_WORLD": return 0.60d;
      case "REGIONAL": return 0.50d;
      case "SOCIAL": return 0.45d;
      default: return 0.25d;
    }
  }

  private static double survivalPressure(JSONObject state) {
    try {
      JSONObject survival = state == null ? null : state.optJSONObject(SurvivalCore.ROOT_KEY);
      JSONObject chars = survival == null ? null : survival.optJSONObject(SurvivalCore.CHARACTERS_KEY);
      JSONObject player = chars == null ? null : chars.optJSONObject("cao_minh");
      if (player == null) return 0.0d;
      long elapsed = SurvivalCore.elapsedMinutes(state);
      long food = Math.max(0L, elapsed - player.optLong("lastFoodMinute", elapsed));
      long water = Math.max(0L, elapsed - player.optLong("lastWaterMinute", elapsed));
      long rest = Math.max(0L, elapsed - player.optLong("lastRestMinute", elapsed));
      double worst = Math.max(
          (double) food / SurvivalCore.FOOD_CRITICAL_MINUTES,
          Math.max((double) water / SurvivalCore.WATER_CRITICAL_MINUTES,
              (double) rest / SurvivalCore.REST_CRITICAL_MINUTES));
      return clamp(worst);
    } catch (Exception ignored) {
      return 0.0d;
    }
  }

  private static void addRelationship(JSONArray relationships, JSONArray refs, String sourceRef)
      throws Exception {
    if (refs == null || refs.length() == 0) return;
    JSONArray actors = copyArray(refs);
    if (actors.length() == 1 && !"cao_minh".equals(actors.optString(0, ""))) actors.put("cao_minh");
    if (actors.length() < 2) return;
    String key = sortedRefKey(actors);
    if (key.isEmpty() || containsObjectKey(relationships, "relationshipKey", key)) return;
    relationships.put(new JSONObject()
        .put("relationshipKey", key)
        .put("actorRefs", actors)
        .put("status", "IMPORTANT")
        .put("sourceRef", sourceRef));
  }

  private static void addHint(JSONArray hints, String tag, double strength, String sourceRef)
      throws Exception {
    String normalized = safe(tag).toUpperCase(Locale.ROOT);
    if (normalized.isEmpty() || strength <= 0.0d) return;
    JSONObject target = null;
    for (int i = 0; i < hints.length(); i++) {
      JSONObject hint = hints.optJSONObject(i);
      if (hint != null && normalized.equals(hint.optString("tag", ""))) {
        target = hint;
        break;
      }
    }
    if (target == null) {
      target = new JSONObject()
          .put("tag", normalized)
          .put("strength", clamp(strength))
          .put("advisoryOnly", true)
          .put("sourceRefs", new JSONArray());
      hints.put(target);
    } else {
      target.put("strength", Math.max(target.optDouble("strength", 0.0d), clamp(strength)));
    }
    if (!safe(sourceRef).isEmpty()) addUniqueString(target.getJSONArray("sourceRefs"), sourceRef);
  }

  private static void deriveConvergence(JSONArray questions, JSONArray output) throws Exception {
    for (int i = 0; i < questions.length(); i++) {
      JSONObject left = questions.optJSONObject(i);
      JSONArray leftRefs = left == null ? null : left.optJSONArray("keyRefs");
      if (leftRefs == null) continue;
      for (int r = 0; r < leftRefs.length(); r++) {
        String ref = leftRefs.optString(r, "").trim();
        if (ref.isEmpty()) continue;
        JSONArray threadIds = new JSONArray();
        for (int j = 0; j < questions.length(); j++) {
          JSONObject question = questions.optJSONObject(j);
          JSONArray refs = question == null ? null : question.optJSONArray("keyRefs");
          if (refs != null && containsString(refs, ref)) {
            addUniqueString(threadIds, question.optString("sourceThreadId", ""));
          }
        }
        if (threadIds.length() < 2) continue;
        String conditionKey = "shared_unresolved_ref:" + ref;
        if (containsObjectKey(output, "conditionKey", conditionKey)) continue;
        output.put(new JSONObject()
            .put("conditionKey", conditionKey)
            .put("conditionType", "SHARED_UNRESOLVED_KEY_REF")
            .put("keyRef", ref)
            .put("sourceThreadIds", threadIds)
            .put("met", true)
            .put("advisoryOnly", true));
      }
    }
  }

  private static String sortedRefKey(JSONArray refs) {
    java.util.ArrayList<String> values = new java.util.ArrayList<>();
    for (int i = 0; i < refs.length(); i++) {
      String value = refs.optString(i, "").trim();
      if (!value.isEmpty() && !values.contains(value)) values.add(value);
    }
    java.util.Collections.sort(values);
    return "relationship:" + String.join("|", values);
  }

  private static boolean containsObjectKey(JSONArray values, String key, String expected) {
    for (int i = 0; i < values.length(); i++) {
      JSONObject value = values.optJSONObject(i);
      if (value != null && expected.equals(value.optString(key, ""))) return true;
    }
    return false;
  }

  private static boolean containsString(JSONArray values, String expected) {
    for (int i = 0; i < values.length(); i++) {
      if (expected.equals(values.optString(i, ""))) return true;
    }
    return false;
  }

  private static void addUniqueString(JSONArray values, String value) {
    String normalized = safe(value);
    if (!normalized.isEmpty() && !containsString(values, normalized)) values.put(normalized);
  }

  private static JSONArray copyArray(JSONArray source) throws Exception {
    return source == null ? new JSONArray() : new JSONArray(source.toString());
  }

  private static Object copyValue(Object value) throws Exception {
    if (value instanceof JSONObject) return new JSONObject(value.toString());
    if (value instanceof JSONArray) return new JSONArray(value.toString());
    return value == null ? JSONObject.NULL : value;
  }

  private static double clamp(double value) {
    return Math.max(0.0d, Math.min(1.0d, value));
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }
}
