from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

old = '''          if (hardIssues.length() > 0) {
            if (BuildConfig.DEBUG && getIntent().getBooleanExtra("emuLevel1Progression", false)) {
              android.util.Log.e("LevelProgressionProbe", "hardIssues=" + hardIssues.toString() +
                " ops=" + (generated.optJSONArray("ops") == null ? "[]" : generated.optJSONArray("ops").toString()));
            }
            throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.");
          }
'''

new = '''          if (hardIssues.length() > 0) {
            if (BuildConfig.DEBUG) {
              android.util.Log.e("BackroomCanon", "action=" + action +
                " hardIssues=" + hardIssues.toString() +
                " ops=" + (generated.optJSONArray("ops") == null ? "[]" : generated.optJSONArray("ops").toString()));
            }

            JSONObject activeCombat = before.optJSONObject("combat");
            boolean combatActive = activeCombat != null && activeCombat.optBoolean("active", false);
            boolean safeExplorationAction = containsAny(lower(action),
              "khám phá", "thăm dò", "dò đường", "tìm kiếm", "quan sát", "kiểm tra", "lắng nghe", "nghe ngóng",
              "di chuyển", "đi tiếp", "tiến sâu", "đi quanh", "tìm đường", "tiếp tục",
              "explore", "survey", "search", "inspect", "listen", "move", "walk", "advance", "continue", "check");
            boolean deterministicConsequence = rollSuccess(rolls, "exitProbe") || rollSuccess(rolls, "levelExit") ||
              rollSuccess(rolls, "entityEncounter") || rollSuccess(rolls, "hazard");
            boolean safeFallback = !meta && currentLevel(before) == 0 && !combatActive &&
              !transitionReadyAndroid(before) && !deterministicConsequence && safeExplorationAction;

            if (!safeFallback) {
              throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.");
            }

            generated = new JSONObject()
              .put("reply", "Kai tiếp tục theo hướng đã chọn với nhịp di chuyển thận trọng, kiểm tra các góc và khoảng trống trước khi đi qua. Quãng hành động này không cho thấy dữ kiện mới đủ chắc để kết luận thêm; môi trường trước mắt vẫn chưa xác nhận thêm người, vật thể hay lối chuyển tầng.")
              .put("ops", new JSONArray())
              .put("snapshotEvent", new JSONObject()
                .put("shouldGenerate", false)
                .put("kind", "")
                .put("reason", "canon_safe_fallback"));
            reply = generated.optString("reply", "");
            candidateState = new JSONObject(before.toString());
            lockSuccessfulExitReadyAndroid(before, candidateState, rolls);
            risk = 0;
            audits = new JSONArray();
            hardIssues = new JSONArray();
            repaired = true;
            if (BuildConfig.DEBUG) {
              android.util.Log.w("BackroomCanonFallback", "Recovered low-risk Level 0 exploration turn without model ops.");
            }
          }
'''

if "BackroomCanonFallback" not in text:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Canon safe fallback: expected final LevelProgressionProbe failure block once, found {count}")
    text = text.replace(old, new, 1)

for marker in (
    'android.util.Log.e("BackroomCanon"',
    'boolean safeFallback = !meta && currentLevel(before) == 0',
    '!transitionReadyAndroid(before)',
    'rollSuccess(rolls, "entityEncounter")',
    'candidateState = new JSONObject(before.toString());',
    'android.util.Log.w("BackroomCanonFallback"',
    'canon_safe_fallback',
):
    if marker not in text:
        raise RuntimeError("Canon safe fallback marker missing: " + marker)

MAIN.write_text(text, encoding="utf-8")
print("Canon safe fallback applied: persistent low-risk Level 0 exploration audit failures no longer kill the turn; high-risk/combat/transition consequences still fail closed.")
