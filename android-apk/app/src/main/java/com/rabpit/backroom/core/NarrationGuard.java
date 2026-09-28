package com.rabpit.backroom.core;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministic guard for the non-authoritative narration payload. */
public final class NarrationGuard {
  private static final Set<String> FORBIDDEN_ROOT_KEYS = new HashSet<>(Arrays.asList(
      "transitionTarget", "sceneLabel", "state", "stateDelta", "currentLevel",
      "currentLevelKey", "location", "flags", "inventory", "party", "combat",
      "facts", "historicalFacts", "beliefs", "threads", "threadRegistry", "narrativeSkeleton", "emergent"));
  private static final Pattern REPORT = Pattern.compile(
      "(?iu)(?:tiến độ|nhiệm vụ (?:đã |bắt đầu )|trạng thái (?:đã |hiện |của )|"
          + "(?:đã |được )?kích hoạt (?:trạng thái|nhiệm vụ|cơ chế)|"
          + "(?:reset|cập nhật) (?:tiến độ|nhiệm vụ|trạng thái)|"
          + "(?:hệ thống|game master|debug|stateDelta|JSON|prompt|token|API)\\s*[:=])");
  private static final Pattern META = Pattern.compile(
      "(?iu)(?:\\b(?:debug|stateDelta|JSON|prompt|token|API|checkpoint)\\b|"
          + "\\b(?:Core|system)\\s*[:=]|\\[(?:system|debug|core)\\])");
  private static final Pattern MECHANICAL_END = Pattern.compile(
      "(?iu)(?:bạn|ngươi|cao minh)\\s+(?:sẽ|muốn|định)\\s+"
          + "(?:làm gì|chọn gì|đi đâu|hành động gì)(?:\\s+(?:tiếp|tiếp theo|bây giờ))?\\s*[?？!。.]?$");
  private static final Pattern AGENCY = Pattern.compile(
      "(?iu)(?:cao minh|hắn)\\s+(?:quyết định|tự nhủ|nghĩ thầm|thầm nghĩ)\\b");
  private static final Pattern CHOICE_ACTION = Pattern.compile(
      "(?iu)^(?:cao minh\\s+|hắn\\s+)?(?:quan sát|kiểm tra|khảo sát|xem xét|"
          + "lắng nghe|nghe|nhìn|tìm|tìm kiếm|khám phá|lục|dò|theo dõi|đi|bước|tiến|rẽ|quay|"
          + "lùi|tránh|né|ẩn|nấp|chờ|đợi|dừng|mở|đóng|chạm|gõ|dùng|sử dụng|"
          + "hỏi|gọi|nói|đọc|nhặt|đặt|thử|thăm dò|leo|cúi|soi|đánh dấu|"
          + "men theo|đi theo|tiếp tục đi|tiếp tục quan sát|tiếp tục kiểm tra)\\s+.+$");
  private static final Pattern GENERIC_CHOICE = Pattern.compile(
      "(?iu)^(?:tiếp tục\\s+)?(?:khám phá|di chuyển|đi tiếp|tiếp tục đi|tiếp tục khám phá)"
          + "(?:\\s+(?:level|tầng)\\s*\\d+)?[.!?]?$|^tiếp tục khám phá\\s+(?:level|tầng)\\s*\\d+[.!?]?$" );
  private static final Pattern CHOICE_OUTCOME = Pattern.compile(
      "(?iu)(?:\\b(?:và|rồi|để)\\s+(?:phát hiện|tìm thấy|nhận được|thoát khỏi|đánh bại)\\b|"
          + "\\bsẽ\\s+(?:phát hiện|tìm thấy|nhận được|thoát khỏi|đánh bại)\\b|"
          + "\\bđã\\s+(?:phát hiện|tìm thấy|nhận được|thoát khỏi|đánh bại)\\b)");

  private NarrationGuard() {}

  /** Returns an empty string when valid, otherwise a deterministic rejection reason. */
  public static String validate(JSONObject generated, JSONObject committedState) {
    return validate(generated, committedState, "");
  }

  public static String validate(JSONObject generated, JSONObject committedState, String playerAction) {
    if (generated == null) return "Narration payload is missing.";
    String reply = generated.optString("reply", "").trim();
    if (reply.isEmpty()) return "reply is required.";

    for (String key : FORBIDDEN_ROOT_KEYS) {
      if (generated.has(key)) return "Narration attempted authoritative field: " + key;
    }

    JSONArray choices = generated.optJSONArray("choices");
    JSONArray dialogue = generated.optJSONArray("encounterDialogue");
    int dialogueCount = dialogue == null ? 0 : dialogue.length();
    int pendingCount = pendingEncounterCount(committedState);
    if (pendingCount == 0 && dialogueCount != 0) {
      return "encounterDialogue is forbidden without a committed pending encounter.";
    }
    if (pendingCount > 0 && (dialogueCount < 2 || dialogueCount > 5)) {
      return "Committed character encounter requires 2-5 dialogue lines.";
    }
    if (dialogue != null) {
      for (int i = 0; i < dialogue.length(); i++) {
        if (dialogue.optString(i, "").trim().isEmpty()) {
          return "encounterDialogue cannot contain empty lines.";
        }
      }
    }

    if (hasActiveEntityEncounter(committedState) && choices != null && choices.length() > 0) {
      return "choices are forbidden while a committed Entity encounter is active.";
    }

    String narrativeViolation = validateNarrative(reply, playerAction);
    if (!narrativeViolation.isEmpty()) return narrativeViolation;
    String choiceViolation = validateChoices(choices, playerAction);
    if (!choiceViolation.isEmpty()) return choiceViolation;
    return "";
  }

  private static String validateNarrative(String reply, String playerAction) {
    // ponytail: These local patterns catch explicit violations, not literary quality or implied canon;
    // expand only with observed false negatives and grounded tests.
    if (reply.length() < 12) return "reply is unusually short; narrate a concrete event.";
    if (REPORT.matcher(reply).find() || META.matcher(reply).find()) {
      return "reply reports game/system state instead of showing the event in the world.";
    }
    if (MECHANICAL_END.matcher(reply).find()) return "reply ends with a mechanical player question.";
    if (AGENCY.matcher(reply).find() && !AGENCY.matcher(playerAction == null ? "" : playerAction).find()) {
      return "reply invents a decision or inner monologue for Cao Minh.";
    }
    Set<String> sentences = new HashSet<>();
    for (String sentence : reply.split("[.!?。！？]+")) {
      String normalized = normalize(sentence);
      if (normalized.length() >= 14 && !sentences.add(normalized)) {
        return "reply repeats the same sentence.";
      }
    }
    return "";
  }

  private static String validateChoices(JSONArray choices, String playerAction) {
    if (choices == null) return "";
    if (choices.length() > 3) return "choices must contain at most 3 suggestions.";
    Set<String> seen = new HashSet<>();
    String action = normalize(playerAction).replaceFirst("^cao minh ", "");
    for (int i = 0; i < choices.length(); i++) {
      JSONObject choice = choices.optJSONObject(i);
      String text = choice == null ? "" : choice.optString("text", "").trim();
      String id = "choice " + (char) ('A' + i);
      if (text.isEmpty()) return id + " must contain non-empty text.";
      if (text.length() > 100 || text.split("[.!?。！？]", -1).length > 3) {
        return id + " is too long; use one short action.";
      }
      if (REPORT.matcher(text).find() || META.matcher(text).find()) return id + " contains system/meta language.";
      if (GENERIC_CHOICE.matcher(text).matches()) return id + " is generic; name a concrete action or target.";
      if (CHOICE_OUTCOME.matcher(text).find()) return id + " states an outcome instead of an action.";
      if (!CHOICE_ACTION.matcher(text).matches()) return id + " must be an actionable suggestion.";
      String normalized = normalize(text).replaceFirst("^cao minh ", "");
      if (!action.isEmpty() && similar(normalized, action)) return id + " repeats the player's action.";
      for (String previous : seen) {
        if (similar(normalized, previous)) return id + " duplicates another choice.";
      }
      seen.add(normalized);
    }
    return "";
  }

  private static String normalize(String value) {
    return (value == null ? "" : value).toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
  }

  private static boolean similar(String first, String second) {
    if (first.equals(second)) return true;
    Set<String> a = new HashSet<>(Arrays.asList(first.split(" ")));
    Set<String> b = new HashSet<>(Arrays.asList(second.split(" ")));
    if (Math.min(a.size(), b.size()) < 3) return false;
    if (first.startsWith(second + " ") || second.startsWith(first + " ")) return true;
    Set<String> common = new HashSet<>(a);
    common.retainAll(b);
    return common.size() >= 3 && common.size() * 4 >= Math.max(a.size(), b.size()) * 3;
  }

  @FunctionalInterface public interface Regenerator {
    JSONObject regenerate(String violation) throws Exception;
  }

  /** One bounded rewrite; both attempts run the complete authority, reply and choice checks. */
  public static JSONObject regenerateIfInvalid(JSONObject generated, JSONObject state,
                                                String action, Regenerator regenerator) throws Exception {
    String violation = validate(generated, state, action);
    if (violation.isEmpty()) return generated;
    JSONObject rewritten = regenerator.regenerate(violation);
    JSONArray originalDialogue = generated == null ? null : generated.optJSONArray("encounterDialogue");
    JSONArray rewrittenDialogue = rewritten == null ? null : rewritten.optJSONArray("encounterDialogue");
    if (originalDialogue != null && (rewrittenDialogue == null
        || !originalDialogue.toString().equals(rewrittenDialogue.toString()))) {
      throw new IllegalArgumentException("Narration validation failed: encounterDialogue changed during rewrite.");
    }
    violation = validate(rewritten, state, action);
    if (!violation.isEmpty()) throw new IllegalArgumentException("Narration validation failed: " + violation);
    return rewritten;
  }

  /** Returns only independently valid A/B/C outputs; never repairs or requests another branch. */
  public static Map<String, JSONObject> validPrefetchBranches(JSONObject branches,
      Map<String, JSONObject> states, Map<String, String> actions) {
    Map<String, JSONObject> valid = new LinkedHashMap<>();
    if (branches == null) return valid;
    for (String id : actions.keySet()) {
      JSONObject branch = branches.optJSONObject(id);
      if (branch != null && states.get(id) != null
          && validate(branch, states.get(id), actions.get(id)).isEmpty()) valid.put(id, branch);
    }
    return valid;
  }

  private static int pendingEncounterCount(JSONObject state) {
    JSONObject encounter = state == null ? null : state.optJSONObject("characterEncounter");
    JSONArray pending = encounter == null ? null : encounter.optJSONArray("pendingIntro");
    return pending == null ? 0 : pending.length();
  }

  private static boolean hasActiveEntityEncounter(JSONObject state) {
    JSONObject flags = state == null ? null : state.optJSONObject("flags");
    return flags != null && !flags.optString("entityEncounterKey", "").trim().isEmpty();
  }
}
