from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)


# Inventory acquisition has a stable early-preview Kotlin bridge. Party, Player and Flag preview
# shapes are intentionally left alone here because later historical compatibility patches still
# match those legacy blocks. The terminal story/candidate bridge normalizes them to Kotlin after
# those compatibility layers have finished, while checked-in GameCoreFacade rechecks everything
# again at the trusted commit boundary.
old_inventory = r'''        boolean allowedNew = acquisitionIntent(action);
        JSONObject beforeFlagsForItem = before.optJSONObject("flags");
        JSONObject beforeMadGodForItem = beforeFlagsForItem != null ? beforeFlagsForItem.optJSONObject("madGod") : null;
        boolean madGodAlreadySpawned = beforeMadGodForItem != null && beforeMadGodForItem.optBoolean("spawned", false);
        if (madGod && !madGodAlreadySpawned) allowedNew = false;
        if (almond) {
          JSONObject waterRoll = rolls.optJSONObject("almondWater");
          if (waterRoll != null && waterRoll.optBoolean("eligible", false) && !waterRoll.optBoolean("success", false) && existing < 0) allowedNew = false;
        }
'''
new_inventory = r'''        // Kotlin Game Core owns acquisition eligibility. Java only applies the decision early so
        // rejected provider ops keep the existing audit/repair behavior before Core commit.
        boolean allowedNew = com.rabpit.backroom.core.InventoryAcquisitionPolicy.allows(
          before.toString(), rolls.toString(), action, name, existing >= 0,
          op.optString("basis", ""));
'''
replace_once(old_inventory, new_inventory, "Kotlin inventory acquisition authority")

old_risk_tail = r'''    if (hasParty && containsAny(reply, "yêu", "thích", "ghen", "tin tưởng", "phản bội", "người yêu", "hẹn hò", "quan hệ", "love", "trust", "betray", "relationship")) score += 2;
    return score;
  }
'''
new_risk_tail = r'''    if (hasParty && containsAny(reply, "yêu", "thích", "ghen", "tin tưởng", "phản bội", "người yêu", "hẹn hò", "quan hệ", "love", "trust", "betray", "relationship")) score += 2;
    JSONArray proposed = generated.optJSONArray("ops");
    if (proposed != null && proposed.length() > 0) {
      for (int i = 0; i < Math.min(24, proposed.length()); i++) {
        JSONObject op = proposed.optJSONObject(i);
        if (op == null) continue;
        String type = lower(op.optString("type", ""));
        if (type.equals("set_level") && currentLevel(before) == currentLevel(candidate)) score = Math.max(score, 4);
        if ((type.equals("party_upsert") || type.equals("party_remove")) && !jsonChanged(before.optJSONArray("party"), candidate.optJSONArray("party"))) score = Math.max(score, 4);
        if ((type.equals("inventory_upsert") || type.equals("inventory_remove")) && !jsonChanged(before.optJSONArray("inventory"), candidate.optJSONArray("inventory"))) score = Math.max(score, 4);
        if (type.equals("patch_player") && !jsonChanged(before.optJSONObject("player"), candidate.optJSONObject("player"))) score = Math.max(score, 4);
        if (type.equals("flag_patch")) {
          String root = op.optString("root", "");
          JSONObject beforeFlagsLocal = before.optJSONObject("flags");
          JSONObject afterFlagsLocal = candidate.optJSONObject("flags");
          Object beforeRoot = beforeFlagsLocal != null ? beforeFlagsLocal.opt(root) : null;
          Object afterRoot = afterFlagsLocal != null ? afterFlagsLocal.opt(root) : null;
          if (!jsonChanged(beforeRoot, afterRoot)) score = Math.max(score, 4);
        }
      }
    }
    return score;
  }
'''
replace_once(old_risk_tail, new_risk_tail, "rejected proposal audit risk")

fast_http = r'''  private String postJsonFast(String endpoint, String key, String authHeader, JSONObject payload) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(5000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty(authHeader, authHeader.equals("Authorization") ? "Bearer " + key : key);
    try (OutputStream output = connection.getOutputStream()) {
      output.write(payload.toString().getBytes("UTF-8"));
    }
    int status = connection.getResponseCode();
    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
    StringBuilder body = new StringBuilder();
    if (stream != null) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
        String line;
        while ((line = reader.readLine()) != null) body.append(line);
      }
    }
    connection.disconnect();
    if (status < 200 || status >= 300) {
      String detail = body.length() > 220 ? body.substring(0, 220) : body.toString();
      throw new HttpError(status, "Provider HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
    }
    return body.toString();
  }

'''
http_anchor = "  private long geminiWorkerScoreUnsafe(int index) {\n"
if "private String postJsonFast(" not in text:
    replace_once(http_anchor, fast_http + http_anchor, "fast text HTTP helper")

old_call = r'''              JSONObject result = new JSONObject(postJson(
                "https://generativelanguage.googleapis.com/v1beta/models/" + GEMINI_MODEL + ":generateContent",
                key,
                "x-goog-api-key",
                body
              ));
'''
new_call = old_call.replace("postJson(", "postJsonFast(")
replace_once(old_call, new_call, "Gemini fast HTTP call")

for required in [
    "InventoryAcquisitionPolicy.allows",
    "op.optString(\"basis\", \"\")",
    "JSONArray proposed",
    "private String postJsonFast(",
    "setReadTimeout(5000)",
]:
    if required not in text:
        raise RuntimeError(f"final authority hardening missing marker: {required}")

for retired in [
    "establishedStructured",
    "gm_confirmed_pickup",
    "confirmedMundanePickup",
    "mundanePickupName(",
]:
    if retired in text:
        raise RuntimeError(f"retired inventory/authority marker survived: {retired}")

MAIN.write_text(text, encoding="utf-8")
print("Final Android hardening keeps Inventory acquisition on Kotlin and leaves Party/Player/Flag legacy shapes for terminal bridge normalization.")
