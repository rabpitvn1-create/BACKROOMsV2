"""Install GM A/B/C choices and Freedom text input after the settled Android patch chain.

This patch intentionally does not change GameState.world or introduce a World simulation layer.
SEARCH / EXECUTE / EXPLORE remain the only ActionRuntime kinds. Freedom is an interaction
origin which is deterministically classified onto those existing kinds before ActionRuntime.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"


def once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return source.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Java bridge: preserve the existing typed ActionRuntime contract, but separate
# interaction origin (curated GM choice vs player-authored Freedom action).
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")

old_bridge = '''    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      submitAction(stateJson, "EXECUTE", action);
    }

    @JavascriptInterface public void submitAction(String stateJson, String actionKind, String action) {
      submitTurnInternal(stateJson, actionKind, action);
    }

    private void submitTurnInternal(String stateJson, String actionKind, String action) {
'''
new_bridge = '''    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      submitFreedom(stateJson, action);
    }

    @JavascriptInterface public void submitFreedom(String stateJson, String action) {
      submitTurnInternal(stateJson, classifyFreedomActionKind(action), action, "FREEDOM");
    }

    @JavascriptInterface public void submitAction(String stateJson, String actionKind, String action) {
      submitTurnInternal(stateJson, actionKind, action, "CHOICE");
    }

    private boolean freedomContainsAny(String text, String... needles) {
      if (text == null) return false;
      for (String needle : needles) if (needle != null && !needle.isEmpty() && text.contains(needle)) return true;
      return false;
    }

    private String classifyFreedomActionKind(String action) {
      String text = action == null ? "" : action.trim().toLowerCase(java.util.Locale.ROOT);
      // Moving into a new/alternate route keeps the existing EXPLORE encounter/progression semantics.
      // A plain retreat such as "lùi lại" is intentionally not enough by itself to create a new
      // EXPLORE roll; the action must also express continued/new traversal.
      boolean explores = freedomContainsAny(text,
        "rẽ trái", "rẽ phải", "đi tiếp", "bước vào", "đi vào", "tiến vào", "đi qua hành lang",
        "theo hành lang", "sang hành lang", "hành lang khác", "lối khác", "đường khác", "tuyến khác",
        "đổi hành lang", "đổi lối", "đổi đường", "another corridor", "another route", "turn left",
        "turn right", "move into", "go down the corridor", "continue forward");
      if (explores) return "EXPLORE";

      boolean searches = freedomContainsAny(text,
        "tìm kiếm", "kiểm tra", "quan sát", "điều tra", "xem xét", "soi ", "soi kỹ", "lắng nghe",
        "nghe ngóng", "dò xét", "quét ", "scan", "search", "inspect", "investigate", "observe",
        "listen", "check");
      return searches ? "SEARCH" : "EXECUTE";
    }

    private void submitTurnInternal(String stateJson, String actionKind, String action, String actionOrigin) {
'''
main = once(main, old_bridge, new_bridge, "Freedom bridge")

writer_sig_old = "  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback) throws Exception {\n"
writer_sig_new = "  private String writerPrompt(JSONObject before, String action, JSONObject rolls, JSONArray auditFeedback, String actionOrigin) throws Exception {\n"
main = once(main, writer_sig_old, writer_sig_new, "writerPrompt origin signature")

call_pattern = re.compile(r'writerPrompt\(before, action, rolls, (null|hardIssues)\)')
main, call_count = call_pattern.subn(r'writerPrompt(before, action, rolls, \1, actionOrigin)', main)
if call_count != 2:
    raise RuntimeError(f"writerPrompt origin calls: expected 2, found {call_count}")

writer_pos = main.index(writer_sig_new)
choice_helpers = r'''  private JSONArray sanitizeGmChoices(JSONObject generated, boolean meta) throws Exception {
    JSONArray safe = new JSONArray();
    if (meta || generated == null) return safe;
    JSONArray proposed = generated.optJSONArray("choices");
    if (proposed == null) return safe;
    String[] ids = {"A", "B", "C"};
    for (int i = 0; i < proposed.length() && safe.length() < 3; i++) {
      JSONObject raw = proposed.optJSONObject(i);
      if (raw == null) continue;
      String label = raw.optString("label", "").trim();
      String action = raw.optString("action", "").trim();
      String kind = raw.optString("actionKind", "").trim().toUpperCase(java.util.Locale.ROOT);
      if (label.isEmpty() || action.isEmpty()) continue;
      if (label.length() > 120 || action.length() > 420) continue;
      if (!("SEARCH".equals(kind) || "EXECUTE".equals(kind) || "EXPLORE".equals(kind))) continue;
      safe.put(new JSONObject()
        .put("id", ids[safe.length()])
        .put("label", label)
        .put("actionKind", kind)
        .put("action", action));
    }
    // One surviving button is a malformed decision point, not a useful A/B(/C) set.
    return safe.length() >= 2 ? safe : new JSONArray();
  }

'''
main = main[:writer_pos] + choice_helpers + main[writer_pos:]

# Append the origin/choice contract to writerPrompt itself. Do not use the last string literal
# before GameBridge: later authority patches add helper methods in that region, which previously
# placed actionOrigin at class scope and broke javac.
writer_pos = main.index(writer_sig_new)
bridge_pos = main.index("\n  private class GameBridge", writer_pos)
writer = main[writer_pos:bridge_pos]
prompt_marker = '"JSON bắt buộc:'
if writer.count(prompt_marker) != 1:
    raise RuntimeError(f"writerPrompt JSON contract anchor: expected 1, found {writer.count(prompt_marker)}")
prompt_start = writer.index(prompt_marker)
return_tail = writer.find('";', prompt_start)
writer_method_end = writer.find("\n  }\n", prompt_start)
if return_tail < 0 or writer_method_end < 0 or return_tail > writer_method_end:
    raise RuntimeError("writerPrompt JSON contract terminator escaped writerPrompt scope")
absolute_tail = writer_pos + return_tail

origin_contract = r''' +
      "\n\nINTERACTION ORIGIN = " + actionOrigin + ". " +
      ("FREEDOM".equals(actionOrigin)
        ? "FREEDOM HARD LOCK: đây là quyết định riêng do người chơi tự viết, không phải một lựa chọn GM. Giữ đúng mục tiêu, thứ tự, điều kiện và phương án dự phòng mà người chơi nêu; không kéo họ trở lại A/B/C. Freedom không được ưu ái, không tự thành công và không miễn rủi ro. Nếu kế hoạch có nhiều bước, chỉ giải quyết đến điểm mà state/roll hiện tại cho phép; dừng khi có gián đoạn hoặc cần một quyết định mới. "
        : "CHOICE HARD LOCK: đây là một lựa chọn nhanh do GM đã đưa ra. Giải quyết đúng intent được chọn, không tự thêm kế hoạch nhiều bước hay kết quả bí mật. ") +
      "STORY CONTINUITY: nếu CURRENT STATE có flags.storyContinuity, dùng objective/open thread/knowledge hiện tại để giữ mạch truyện nhất quán, nhưng không tiết lộ tương lai, hidden state hay kiến thức Kai chưa có. " +
      "GM CHOICE CONTRACT: ngoài reply/ops/snapshotEvent, output phải có top-level choices. Nếu không có decision point có ý nghĩa, meta request, hoặc đang ở combat thì choices=[]. Nếu có, trả 2 hoặc 3 phần tử dạng {id:'A',label:'...',actionKind:'SEARCH|EXECUTE|EXPLORE',action:'...'}. " +
      "Mỗi choice chỉ là một hành động hiển nhiên/ngắn hạn mà Kai có thể cân nhắc từ thông tin đang biết; không được ghi kết quả, phần thưởng, Entity sẽ gặp, xác suất, 'an toàn', 'tốt nhất' hay hậu quả ẩn vào label/action. Các choice có thể đều rủi ro hoặc đều không tối ưu. Không cố bao phủ mọi khả năng: người chơi luôn có thể bỏ toàn bộ choices và tự nhập Freedom action. " +
      "Schema bổ sung bắt buộc: choices:[{id,label,actionKind,action}]."'''
main = main[:absolute_tail + 1] + origin_contract + main[absolute_tail + 1:]

# Fail during the Python patch step, before javac, if the injected origin contract ever escapes
# the writerPrompt method again.
writer_pos = main.index(writer_sig_new)
writer_method_end = main.index("\n  }\n", writer_pos)
writer_method = main[writer_pos:writer_method_end]
for scoped_marker in ("INTERACTION ORIGIN =", "FREEDOM HARD LOCK:", "GM CHOICE CONTRACT:"):
    if scoped_marker not in writer_method:
        raise RuntimeError("writerPrompt scoped contract missing: " + scoped_marker)

# Emit sanitized choices as ephemeral UI data after authoritative state commit. They are intentionally
# not persisted into GameState/world/log, so loading an old save cannot revive a stale decision.
submit_start = main.index("    private void submitTurnInternal(String stateJson, String actionKind, String action, String actionOrigin) {")
submit_end = main.index("    @JavascriptInterface public void requestSnapshot", submit_start)
submit = main[submit_start:submit_end]
final_emit = '          emit("backroomTurn", state.toString());\n'
if submit.count(final_emit) != 1:
    raise RuntimeError(f"final backroomTurn emit: expected 1, found {submit.count(final_emit)}")
choice_emit = '''          JSONArray gmChoices = sanitizeGmChoices(generated, meta);
          JSONObject gmChoicePayload = new JSONObject()
            .put("turn", state.optInt("turn", 1))
            .put("choices", gmChoices);
          emit("backroomTurn", state.toString());
          emit("backroomChoices", gmChoicePayload.toString());
'''
submit = submit.replace(final_emit, choice_emit, 1)
main = main[:submit_start] + submit + main[submit_end:]

for required in (
    '@JavascriptInterface public void submitFreedom(String stateJson, String action)',
    'classifyFreedomActionKind(action)',
    'submitTurnInternal(stateJson, actionKind, action, "CHOICE")',
    'String actionOrigin) throws Exception',
    'FREEDOM HARD LOCK:',
    'GM CHOICE CONTRACT:',
    'flags.storyContinuity',
    'sanitizeGmChoices(generated, meta)',
    'emit("backroomChoices", gmChoicePayload.toString())',
):
    if required not in main:
        raise RuntimeError("GM/Freedom Java contract missing: " + required)
if "ActionKind.FREEDOM" in main:
    raise RuntimeError("Freedom must not become an ActionRuntime kind")

MAIN.write_text(main, encoding="utf-8")


# ---------------------------------------------------------------------------
# Web UI: Search/Explore disappear from the player-facing controls. Execute stays
# as the Freedom submit button. A/B/C are ephemeral, uppercase, underlined and
# dispatch through the existing typed submitAction bridge.
# ---------------------------------------------------------------------------
html = INDEX.read_text(encoding="utf-8")
if "GM_CHOICES_FREEDOM_V1" in html:
    raise RuntimeError("GM choice/Freedom UI already installed")

# The canonical Android UI currently sends typed text as a fixed EXECUTE macro. Route that
# form through submitFreedom so player-authored text is classified independently, while the
# curated A/B/C buttons below continue to use submitAction with an explicit ActionKind.
typed_submit_old = 'Android.submitAction(JSON.stringify(state),"EXECUTE",a)'
typed_submit_new = 'Android.submitFreedom(JSON.stringify(state),a)'
html = once(html, typed_submit_old, typed_submit_new, "Freedom typed submit route")

ui = r'''
<style id="gmChoicesFreedomStyle">
/* GM_CHOICES_FREEDOM_V1 */
#searchActionButton,#exploreActionButton{display:none!important}
.primary-action-row{grid-template-columns:minmax(0,1fr)!important}
#submit{width:100%}
.gm-choice-list{display:grid;gap:7px;margin-top:10px;width:100%}
.gm-choice-button{appearance:none;width:100%;min-height:42px;padding:9px 10px;border:1px solid #46525b;border-radius:9px;background:#11171b;color:#eef2f4;text-align:left;font:700 11px/1.35 var(--gameplay-font,system-ui,sans-serif);letter-spacing:.055em;text-transform:uppercase;text-decoration:underline;text-decoration-thickness:1px;text-underline-offset:.18em;cursor:pointer;white-space:normal}
.gm-choice-button:disabled{opacity:.45;cursor:not-allowed}
</style>
<script id="gmChoicesFreedomRuntime">
(function(){
  "use strict";
  let gmChoiceDispatchLocked=false;
  function removeLegacyMacroButtons(){
    const search=document.getElementById("searchActionButton");
    const explore=document.getElementById("exploreActionButton");
    if(search)search.remove();
    if(explore)explore.remove();
  }
  function clearGmChoices(){
    document.querySelectorAll(".gm-choice-list").forEach(node=>node.remove());
    gmChoiceDispatchLocked=false;
  }
  function combatIsActive(){
    const hud=document.getElementById("combatHud");
    if(hud&&hud.classList.contains("active"))return true;
    const popup=document.getElementById("combatPopup");
    return !!(popup&&!popup.hidden);
  }
  function latestGmMessage(){
    const entries=document.querySelectorAll("#log .message:not(.player)");
    return entries.length?entries[entries.length-1]:null;
  }
  function submitChoice(choice,list){
    if(gmChoiceDispatchLocked)return;
    if(typeof busy!=="undefined"&&busy)return;
    if(!window.Android||typeof window.Android.submitAction!=="function"){
      if(typeof statusEl!=="undefined"&&statusEl)statusEl.textContent="Không tìm thấy Android action bridge.";
      return;
    }
    gmChoiceDispatchLocked=true;
    list.querySelectorAll("button").forEach(button=>button.disabled=true);
    if(typeof busy!=="undefined")busy=true;
    if(typeof syncPrimaryActions==="function")syncPrimaryActions();
    if(typeof statusEl!=="undefined"&&statusEl)statusEl.textContent="Đang xử lý lựa chọn…";
    if(typeof appendMacroPending==="function")appendMacroPending(choice.label);
    list.remove();
    window.Android.submitAction(JSON.stringify(state),choice.actionKind,choice.action);
  }
  window.backroomChoices=function(payload){
    clearGmChoices();
    removeLegacyMacroButtons();
    let parsed;
    try{parsed=JSON.parse(payload);}catch(_){return;}
    if(!parsed||Number(parsed.turn)!==Number(state&&state.turn)||combatIsActive())return;
    const allowed=new Set(["SEARCH","EXECUTE","EXPLORE"]);
    const source=Array.isArray(parsed.choices)?parsed.choices:[];
    const choices=source.slice(0,3).filter(choice=>choice&&allowed.has(String(choice.actionKind||"").toUpperCase())&&String(choice.label||"").trim()&&String(choice.action||"").trim());
    if(choices.length<2)return;
    const host=latestGmMessage();
    if(!host)return;
    const list=document.createElement("div");
    list.className="gm-choice-list";
    choices.forEach((raw,index)=>{
      const choice={
        id:String(raw.id||String.fromCharCode(65+index)).slice(0,1).toUpperCase(),
        label:String(raw.label||"").trim(),
        actionKind:String(raw.actionKind||"").toUpperCase(),
        action:String(raw.action||"").trim()
      };
      const button=document.createElement("button");
      button.type="button";
      button.className="gm-choice-button";
      button.textContent=choice.id+". "+choice.label.toLocaleUpperCase("vi-VN");
      button.addEventListener("click",()=>submitChoice(choice,list),{once:true});
      list.appendChild(button);
    });
    host.appendChild(list);
    const body=document.querySelector(".storytelling-body")||document.getElementById("log");
    if(body)requestAnimationFrame(()=>{body.scrollTop=body.scrollHeight;});
  };

  const priorTurn=window.backroomTurn;
  if(typeof priorTurn==="function"){
    window.backroomTurn=function(json){clearGmChoices();return priorTurn(json);};
  }
  const priorError=window.backroomError;
  if(typeof priorError==="function"){
    window.backroomError=function(message){gmChoiceDispatchLocked=false;return priorError(message);};
  }
  const form=document.getElementById("form")||document.getElementById("commandForm");
  if(form)form.addEventListener("submit",clearGmChoices,true);
  removeLegacyMacroButtons();
})();
</script>
'''

body_close = "</body>"
if html.count(body_close) != 1:
    raise RuntimeError(f"HTML body close: expected 1, found {html.count(body_close)}")
html = html.replace(body_close, ui + "\n" + body_close, 1)

for required in (
    "GM_CHOICES_FREEDOM_V1",
    "#searchActionButton,#exploreActionButton{display:none!important}",
    ".primary-action-row{grid-template-columns:minmax(0,1fr)!important}",
    "window.backroomChoices=function(payload)",
    "button.textContent=choice.id+\". \"+choice.label.toLocaleUpperCase(\"vi-VN\")",
    "window.Android.submitAction(JSON.stringify(state),choice.actionKind,choice.action)",
    "Android.submitFreedom(JSON.stringify(state),a)",
):
    if required not in html:
        raise RuntimeError("GM/Freedom UI contract missing: " + required)
if typed_submit_old in html:
    raise RuntimeError("Canonical typed form still bypasses Freedom classification")

INDEX.write_text(html, encoding="utf-8")
print("GM choices + Freedom input installed without changing World state: Search/Explore hidden, typed text uses Freedom, A/B/C use existing ActionRuntime kinds.")