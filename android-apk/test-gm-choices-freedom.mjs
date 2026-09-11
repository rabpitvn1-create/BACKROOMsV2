import fs from 'node:fs';
import assert from 'node:assert/strict';

const root = process.argv[2] || 'android-apk';
const html = fs.readFileSync(`${root}/app/src/main/assets/index.html`, 'utf8');
const java = fs.readFileSync(`${root}/app/src/main/java/com/rabpit/backroom/MainActivity.java`, 'utf8');
const runtime = fs.readFileSync(`${root}/app/src/main/java/com/rabpit/backroom/core/ActionRuntime.kt`, 'utf8');

assert.match(java, /@JavascriptInterface public void submitFreedom\(String stateJson, String action\)/);
assert.match(java, /classifyFreedomActionKind\(action\)/);
assert.match(java, /submitTurnInternal\(stateJson, actionKind, action, "CHOICE"\)/);
assert.match(java, /FREEDOM HARD LOCK:/);
assert.match(java, /GM CHOICE CONTRACT:/);
assert.match(java, /flags\.storyContinuity/);
assert.match(java, /sanitizeGmChoices\(generated, meta\)/);
assert.match(java, /emit\("backroomChoices", gmChoicePayload\.toString\(\)\)/);
assert.doesNotMatch(java, /ActionKind\.FREEDOM/);
assert.match(runtime, /enum class ActionKind \{ SEARCH, EXECUTE, EXPLORE \}/);
assert.doesNotMatch(runtime, /FREEDOM/);

// Final interaction authority: only EXPLORE may start a new Entity encounter.
// SEARCH/EXECUTE remain valid ActionKinds but must not reopen the legacy all-actions gate.
assert.match(java, /boolean entityEncounterAction = exploreAction;/);
assert.doesNotMatch(java, /entityEncounterAction = exploreAction \|\|/);
assert.match(java, /SEARCH không được khởi tạo encounter Entity mới/);
assert.match(java, /đây là action duy nhất được phép kích hoạt roll encounter Entity mới/);
assert.match(java, /không tự đổi mục tiêu và không khởi tạo encounter Entity mới/);

assert.match(html, /GM_CHOICES_FREEDOM_V1/);
assert.match(html, /#searchActionButton,#exploreActionButton\{display:none!important\}/);
assert.match(html, /\.primary-action-row\{grid-template-columns:minmax\(0,1fr\)!important\}/);
assert.match(html, /window\.backroomChoices=function\(payload\)/);
assert.match(html, /button\.textContent=choice\.id\+"\. "\+choice\.label\.toLocaleUpperCase\("vi-VN"\)/);
assert.match(html, /window\.Android\.submitAction\(JSON\.stringify\(state\),choice\.actionKind,choice\.action\)/);
assert.match(html, /Android\.submitFreedom\(JSON\.stringify\(state\),a\)/);
assert.doesNotMatch(html, /Android\.submitAction\(JSON\.stringify\(state\),"EXECUTE",a\)/);
assert.match(html, /text-decoration:underline/);
assert.match(html, /button\.textContent=/);
assert.doesNotMatch(html, /innerHTML=choice\./);

console.log('GM choices/Freedom regression contract OK');
