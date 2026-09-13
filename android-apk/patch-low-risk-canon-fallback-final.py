from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

submit_signature = "    private void submitTurnInternal("
submit_start = text.find(submit_signature)
if submit_start < 0:
    raise RuntimeError("Low-risk canon fallback: submitTurnInternal anchor missing")
submit_end = text.find("    @JavascriptInterface public void requestSnapshot", submit_start)
if submit_end < 0:
    raise RuntimeError("Low-risk canon fallback: submitTurnInternal end anchor missing")

submit = text[submit_start:submit_end]
failure = '            throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.");\n'
if submit.count(failure) != 1:
    raise RuntimeError(
        "Low-risk canon fallback: expected final fail-closed throw once inside "
        f"submitTurnInternal, found {submit.count(failure)}"
    )

replacement = r'''            boolean safeFallback = com.rabpit.backroom.core.CanonFallbackPolicy.isEligible(
              before, candidateState, generated, rolls, action, meta, repaired);
            if (!safeFallback) {
              throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.");
            }

            generated = new JSONObject()
              .put("reply", "Kai tiếp tục theo hướng đã chọn với nhịp di chuyển thận trọng, kiểm tra các góc và khoảng trống trước khi đi qua. Quãng hành động này không cho thấy dữ kiện mới đủ chắc để kết luận thêm; môi trường trước mắt vẫn chưa xác nhận thêm người, vật thể hay lối chuyển tầng.")
              .put("ops", new JSONArray())
              .put("choices", new JSONArray())
              .put("snapshotEvent", new JSONObject()
                .put("shouldGenerate", false)
                .put("kind", "")
                .put("reason", "canon_safe_fallback"));
            reply = generated.optString("reply", "");
            candidateState = new JSONObject(before.toString());
            risk = 0;
            audits = new JSONArray();
            hardIssues = new JSONArray();
            if (BuildConfig.DEBUG) {
              android.util.Log.w("BackroomCanonFallback", "Recovered low-risk Level 0 exploration turn without model ops.");
            }
'''

submit = submit.replace(failure, replacement, 1)
text = text[:submit_start] + submit + text[submit_end:]

# patch-lucia-story-gate-final.py injects these operation examples into Java string
# literals. Preserve Java-level escaping after Python evaluates the generator string.
lucia_prompt_tokens = {
    'lucia_story{stage:"first_contact"}': 'lucia_story{stage:\\"first_contact\\"}',
    'lucia_story{stage:"join"}': 'lucia_story{stage:\\"join\\"}',
}
for malformed, escaped in lucia_prompt_tokens.items():
    text = text.replace(malformed, escaped)
    if malformed in text:
        raise RuntimeError("Lucia Java prompt escaping failed for: " + malformed)
    if escaped not in text:
        raise RuntimeError("Lucia Java prompt escaped marker missing: " + escaped)

for marker in (
    "CanonFallbackPolicy.isEligible(",
    "candidateState = new JSONObject(before.toString());",
    'android.util.Log.w("BackroomCanonFallback"',
    'put("reason", "canon_safe_fallback")',
    'put("choices", new JSONArray())',
):
    if marker not in submit:
        raise RuntimeError("Low-risk canon fallback marker missing: " + marker)

MAIN.write_text(text, encoding="utf-8")
print(
    "Low-risk canon fallback applied after the settled runtime: only harmless Level 0 "
    "exploration can recover after writer and repair canon failures; Lucia prompt Java escaping verified."
)

# This script is already the terminal authority in build-backroom-apk.yml. Keep the debug/export
# stack immediately after it so later legacy/UI patches cannot overwrite the visible export button
# or remove diagnostics. Each finalizer fails loudly if its expected settled-runtime anchor moves.
for finalizer in (
    "patch-runtime-debug-export-final.py",
    "patch-runtime-debug-provider-trace-final.py",
    "patch-runtime-debug-turn-trace-final.py",
    "patch-runtime-debug-fallback-compat-final.py",
    "patch-runtime-debug-core-trace-final.py",
    "patch-runtime-debug-ui-events-final.py",
):
    runpy.run_path(str(ROOT / finalizer), run_name="__main__")
