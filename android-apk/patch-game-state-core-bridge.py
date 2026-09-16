from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
FACADE = ROOT / "app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt"
INDEX = ROOT / "app/src/main/assets/index.html"
INTENT = ROOT / "app/src/main/java/com/rabpit/backroom/core/IntentPipeline.kt"

text = MAIN.read_text(encoding="utf-8")

core_import = "import com.rabpit.backroom.core.GameCoreFacade;\n"
if core_import.strip() not in text:
    anchor = "import android.webkit.WebView;\n"
    if anchor not in text:
        raise RuntimeError("GameCoreFacade import anchor not found")
    text = text.replace(anchor, anchor + core_import, 1)

field = "  private GameCoreFacade gameCore;\n"
if field.strip() not in text:
    anchor = "  private WebView webView;\n"
    if anchor not in text:
        raise RuntimeError("GameCoreFacade field anchor not found")
    text = text.replace(anchor, anchor + field, 1)

initialization = "    gameCore = GameCoreFacade.create(this, BuildConfig.DEBUG);\n"
if initialization.strip() not in text:
    anchor = "    webView = new WebView(this);\n"
    if anchor not in text:
        raise RuntimeError("GameCoreFacade initialization anchor not found")
    text = text.replace(anchor, initialization + anchor, 1)

close_line = "    if (gameCore != null) gameCore.close();\n"
if close_line.strip() not in text:
    anchor = "    super.onDestroy();\n"
    if anchor not in text:
        raise RuntimeError("GameCoreFacade close anchor not found")
    text = text.replace(anchor, close_line + anchor, 1)

clear_method = '''    @JavascriptInterface public void clearCoreState() {
      runOnUiThread(() -> {
        if (gameCore != null) gameCore.clear();
      });
    }

'''
if "@JavascriptInterface public void clearCoreState()" not in text:
    anchor = "  private class GameBridge {\n"
    if anchor not in text:
        raise RuntimeError("clearCoreState GameBridge anchor not found")
    text = text.replace(anchor, anchor + clear_method, 1)

rule_bridge = '''          String coreRaw = requireGameCore().processRule(stateJson, action);
          JSONObject coreResult = new JSONObject(coreRaw);
          if (coreResult.optBoolean("handled", false)) {
            emit("backroomTurn", coreResult.getJSONObject("state").toString());
            return;
          }
'''
if ".processRule(stateJson, action)" not in text:
    # Historical Android patches may rebuild submitTurn and remove the earlier Core fast pass.
    # Re-anchor at submitTurn itself instead of depending on a particular beginAction shape.
    submit_signature = "    @JavascriptInterface public void submitTurn(String stateJson, String action) {\n"
    submit = text.find(submit_signature)
    if submit < 0:
        raise RuntimeError("submitTurn bridge anchor not found")
    try_anchor = "        try {\n"
    position = text.find(try_anchor, submit)
    if position < 0:
        raise RuntimeError("submitTurn try anchor not found")
    position += len(try_anchor)
    text = text[:position] + rule_bridge + text[position:]

# Candidate state/ops emitted by the writer are advisory until Kotlin validates and commits them.
# Keep the existing five-argument Core boundary: before, candidate, rolls, accepted ops, action.
gemini_commit = '''          JSONArray coreOps = generated.optJSONArray("ops");
          JSONObject coreCommit = new JSONObject(requireGameCore().processValidatedCandidate(
            before.toString(), candidateState.toString(), rolls.toString(),
            coreOps == null ? "[]" : coreOps.toString(), action));
          if (!coreCommit.optBoolean("handled", false)) {
            throw new Exception("Game State Core từ chối Gemini delta: " + coreCommit.optString("error", "invalid_delta"));
          }
          candidateState = coreCommit.getJSONObject("state");

'''
validated_bridge_present = (
    "processValidatedCandidate(" in text
    and 'before.toString(), candidateState.toString(), rolls.toString(),' in text
    and 'coreOps == null ? "[]" : coreOps.toString(), action' in text
)
if not validated_bridge_present:
    # This is the stable terminal location used by the historical provider pipeline after
    # candidate audit/repair. It is intentionally later than provider JSON construction.
    anchor = "          JSONObject state = candidateState;\n"
    if anchor not in text:
        raise RuntimeError("validated Gemini candidate anchor not found")
    text = text.replace(anchor, gemini_commit + anchor, 1)

for required in [
    core_import.strip(),
    field.strip(),
    initialization.strip(),
    close_line.strip(),
    "@JavascriptInterface public void clearCoreState()",
    "gameCore.clear();",
    ".processRule(stateJson, action)",
    "JSONArray coreOps = generated.optJSONArray(\"ops\")",
    'before.toString(), candidateState.toString(), rolls.toString(),',
    'coreOps == null ? "[]" : coreOps.toString(), action',
]:
    if required not in text:
        raise RuntimeError(f"Game State Core integration missing: {required}")

MAIN.write_text(text, encoding="utf-8")

intent = INTENT.read_text(encoding="utf-8")
if "|nhặt|được|lượm|" not in intent:
    anchor = "|nhặt|lượm|"
    if anchor not in intent:
        raise RuntimeError("Item resolver noise anchor not found")
    intent = intent.replace(anchor, "|nhặt|được|lượm|", 1)
INTENT.write_text(intent, encoding="utf-8")

# GameCoreFacade is now checked in as the settled post-patch source. This patch may
# verify the Core contract required by its Java/UI bridge, but it must not rewrite
# gameplay authority at build time.
facade = FACADE.read_text(encoding="utf-8")
if "StoryProgressionPolicy.normalizeCandidate(" in facade:
    raise RuntimeError("Duplicate StoryProgressionPolicy normalization survived inside GameCoreFacade")
for marker in (
    "private fun validationReply(reason: String): String",
    'return "[Cảnh báo] $message"',
    "private fun response(handled: Boolean, state: JSONObject, error: String?, reason: String, reply: String? = null): String",
):
    if marker not in facade:
        raise RuntimeError("Materialized GameCoreFacade validation reply contract is missing: " + marker)

html = INDEX.read_text(encoding="utf-8")
old_chips = 'function chips(items){return items&&items.length?items.map(x=>"<span>"+esc(typeof x==="string"?x:x.name||"—")+"</span>").join(""):"<span>Trống.</span>"}'
quantity_chips = 'function chips(items){return items&&items.length?items.map(x=>{if(typeof x==="string")return "<span>"+esc(x)+"</span>";const q=Math.max(1,Number(x.quantity)||1);return "<span>"+esc(x.name||"—")+" ×"+q+"</span>"}).join(""):"<span>Trống.</span>"}'
clean_chips = 'function itemDisplayName(x){let n=String((x&&x.name)||"—").replace(/^\\s*được\\s+/i,"").trim();if(n)n=n.charAt(0).toLocaleUpperCase("vi-VN")+n.slice(1);return n} function chips(items){return items&&items.length?items.map(x=>{if(typeof x==="string")return "<span>"+esc(x)+"</span>";const q=Math.max(1,Number(x.quantity)||1);return "<span>"+esc(itemDisplayName(x))+" ×"+q+"</span>"}).join(""):"<span>Trống.</span>"}'
if clean_chips not in html:
    if quantity_chips in html:
        html = html.replace(quantity_chips, clean_chips, 1)
    elif old_chips in html:
        html = html.replace(old_chips, clean_chips, 1)
    else:
        raise RuntimeError("Inventory item renderer anchor not found")

warning_css = ".message.warning{border-left-color:#d99a2b;background:#231a0b}.message.warning .role{color:#e3a83a}.message.warning .text{color:#ffd27a;font-weight:650}"
if warning_css not in html:
    anchor = ".message.player{"
    if anchor not in html:
        raise RuntimeError("warning style anchor not found")
    html = html.replace(anchor, warning_css + anchor, 1)

warning_render = 'const isWarning=role==="gm"&&text.startsWith("[Cảnh báo]"); if(isWarning){role="warning";text=text.replace(/^\\[Cảnh báo\\]\\s*/,"")} '
if warning_render not in html:
    anchor = 'function msg(role,text){'
    if anchor not in html:
        raise RuntimeError("warning render anchor not found")
    html = html.replace(anchor, anchor + warning_render, 1)

INDEX.write_text(html, encoding="utf-8")
print("Game State Core bridge applied; checked-in GameCoreFacade authority verified without source rewrite.")
