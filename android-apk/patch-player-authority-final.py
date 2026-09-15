from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"


# Replace the historical Java player mutation rules with one call into Kotlin.
java = MAIN.read_text(encoding="utf-8")
legacy_player = r'''        JSONObject current = state.optJSONObject("player");
        if (current == null) current = new JSONObject();
        boolean worldConsequence = rollSuccess(rolls, "hazard") || rollSuccess(rolls, "entityEncounter");
        boolean recoveryIntent = containsAny(action, "ăn", "uống", "nghỉ", "ngủ", "băng bó", "chữa", "hồi phục", "eat", "drink", "rest", "sleep", "heal");
        boolean gearIntent = containsAny(action, "rút", "cất", "trang bị", "mặc", "cởi", "tháo", "đeo", "draw", "equip", "unequip", "wear");
        if (patch.has("hp") && current.has("hp") && !current.isNull("hp")) {
          double beforeHp = current.optDouble("hp", Double.NaN);
          double afterHp = patch.optDouble("hp", Double.NaN);
          if (!Double.isNaN(beforeHp) && !Double.isNaN(afterHp) && afterHp >= 0 &&
              ((afterHp < beforeHp && worldConsequence) || (afterHp >= beforeHp && recoveryIntent))) current.put("hp", afterHp);
        }
        if (patch.has("condition") && (worldConsequence || recoveryIntent)) current.put("condition", patch.optString("condition", current.optString("condition", "")));
        if (patch.optJSONObject("needs") != null && recoveryIntent) {
          JSONObject needs = current.optJSONObject("needs");
          if (needs == null) needs = new JSONObject();
          for (String needKey : new String[] {"thirst", "hunger", "fatigue", "sleepDeprivation"}) {
            if (patch.optJSONObject("needs").has(needKey)) needs.put(needKey, patch.optJSONObject("needs").get(needKey));
          }
          current.put("needs", needs);
        }
        JSONArray ownedGear = state.optJSONArray("inventory");
        for (String key : new String[] {"weapon", "armor"}) {
          if (!patch.has(key) || !gearIntent) continue;
          String proposedGear = patch.optString(key, "").trim();
          boolean owned = false;
          if (ownedGear != null) for (int gearIndex = 0; gearIndex < ownedGear.length(); gearIndex++) {
            String ownedName = itemName(ownedGear.opt(gearIndex));
            if (!ownedName.isEmpty() && lower(proposedGear).contains(lower(ownedName))) { owned = true; break; }
          }
          if (owned) current.put(key, proposedGear);
        }
'''
kotlin_player = r'''        JSONObject current = state.optJSONObject("player");
        if (current == null) current = new JSONObject();
        JSONArray ownedGear = state.optJSONArray("inventory");
        current = new JSONObject(com.rabpit.backroom.core.PlayerCandidatePolicy.applyPatch(
          before.toString(), current.toString(), patch.toString(), rolls.toString(), action,
          ownedGear == null ? "[]" : ownedGear.toString()));
'''
if kotlin_player not in java:
    if java.count(legacy_player) != 1:
        raise RuntimeError(f"Java player authority anchor count != 1: {java.count(legacy_player)}")
    java = java.replace(legacy_player, kotlin_player, 1)

for retired in (
    "boolean worldConsequence = rollSuccess(rolls",
    "boolean recoveryIntent = containsAny(action",
    "boolean gearIntent = containsAny(action",
):
    if retired in java:
        raise RuntimeError("Retired Java player authority survived: " + retired)
if "PlayerCandidatePolicy.applyPatch(" not in java:
    raise RuntimeError("Kotlin PlayerCandidatePolicy Java bridge missing")
MAIN.write_text(java, encoding="utf-8")


# The trusted Core boundary independently sanitizes the final candidate. It derives gear ownership
# from the already-authorized desired Inventory rather than trusting the provider's inventory JSON.
facade = FACADE.read_text(encoding="utf-8")
legacy_line = '      playerJson = candidate.optJSONObject("player")?.toString(),\n'
kotlin_line = '      playerJson = sanitizedPlayer?.toString(),\n'
sanitizer = '''    val sanitizedPlayer = PlayerCandidatePolicy.sanitizeCandidate(
      before = before,
      candidatePlayer = candidate.optJSONObject("player"),
      rolls = rolls,
      action = action,
      ownedGearNames = desiredById.values.map { it.name }.toSet(),
    )
'''
command_anchor = '    commands += ValidatedLegacyStateCommand(\n'
if sanitizer not in facade:
    if facade.count(command_anchor) != 1:
        raise RuntimeError(f"Validated state command anchor count != 1: {facade.count(command_anchor)}")
    facade = facade.replace(command_anchor, sanitizer + command_anchor, 1)
if kotlin_line not in facade:
    if facade.count(legacy_line) != 1:
        raise RuntimeError(f"Legacy playerJson commit anchor count != 1: {facade.count(legacy_line)}")
    facade = facade.replace(legacy_line, kotlin_line, 1)

for marker in (
    "PlayerCandidatePolicy.sanitizeCandidate(",
    "ownedGearNames = desiredById.values.map { it.name }.toSet()",
    "playerJson = sanitizedPlayer?.toString()",
):
    if marker not in facade:
        raise RuntimeError("Game Core player authority missing: " + marker)
FACADE.write_text(facade, encoding="utf-8")

print("Player authority final applied: Kotlin sanitizes provider player deltas in preview and Core commit.")

flag_authority = ROOT / "patch-flag-authority-final.py"
if not flag_authority.is_file():
    raise RuntimeError("Flag authority finalizer missing: " + flag_authority.name)
runpy.run_path(str(flag_authority), run_name="__main__")
