import fs from 'node:fs';
import assert from 'node:assert/strict';

const root = process.argv[2] || 'android-apk';
const html = fs.readFileSync(`${root}/app/src/main/assets/index.html`, 'utf8');
const java = fs.readFileSync(`${root}/app/src/main/java/com/rabpit/backroom/MainActivity.java`, 'utf8');
const classifier = fs.readFileSync(`${root}/app/src/main/java/com/rabpit/backroom/FreedomActionClassifier.java`, 'utf8');
const runtime = fs.readFileSync(`${root}/app/src/main/java/com/rabpit/backroom/core/ActionRuntime.kt`, 'utf8');
const core = fs.readFileSync(`${root}/app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt`, 'utf8');
const partyPolicy = fs.readFileSync(`${root}/app/src/main/java/com/rabpit/backroom/core/PartyCandidatePolicy.kt`, 'utf8');

assert.match(java, /@JavascriptInterface public void submitFreedom\(String stateJson, String action\)/);
assert.match(java, /classifyFreedomActionKind\(action\)/);
assert.match(java, /return FreedomActionClassifier\.classify\(action\);/);
assert.doesNotMatch(java, /freedomContainsAny/);
assert.match(java, /submitTurnInternal\(stateJson, actionKind, action, "CHOICE"\)/);
assert.match(java, /FREEDOM HARD LOCK:/);
assert.match(java, /GM CHOICE CONTRACT:/);
assert.match(java, /flags\.storyContinuity/);
assert.match(java, /sanitizeGmChoices\(generated, meta\)/);
assert.match(java, /emit\("backroomChoices", gmChoicePayload\.toString\(\)\)/);
assert.doesNotMatch(java, /ActionKind\.FREEDOM/);
assert.match(runtime, /enum class ActionKind \{ SEARCH, EXECUTE, EXPLORE \}/);
assert.doesNotMatch(runtime, /FREEDOM/);

// Freedom classification is frozen in pure Java: trained/hash-based weights plus a small semantic
// safety guard. It must not gain a LiteRT/network/model-asset dependency or a fourth runtime kind.
assert.match(classifier, /public final class FreedomActionClassifier/);
assert.match(classifier, /private static final int N = 4096;/);
assert.match(classifier, /\{"EXECUTE","EXPLORE","SEARCH"\}/);
assert.match(classifier, /frozen int4/);
assert.match(classifier, /feature\.hashCode\(\)/);
assert.match(classifier, /semanticGuard|String guarded = guard/);
assert.match(classifier, /continue /);
assert.match(classifier, /return "EXPLORE"/);
assert.doesNotMatch(classifier, /LiteRT|TFLite|TensorFlow|\.tflite|GameState\.world|HttpURLConnection|https?:\/\//);
assert.doesNotMatch(classifier, /ActionKind\.FREEDOM/);

// Final gameplay RNG authority belongs to Kotlin. MainActivity may pass Android debug-fixture flags
// and the RNG instance, but must not contain probability tables, an Entity pool or direct Entity rolls.
assert.match(java, /GameplayRollPolicy\.roll\(/);
assert.match(java, /state\.toString\(\), actionKind, action, meta, GAME_RNG/);
assert.match(java, /getIntent\(\)\.getBooleanExtra\("emuLevel1Progression", false\)/);
assert.match(java, /getIntent\(\)\.getBooleanExtra\("emuLevel06Traversal", false\)/);
assert.doesNotMatch(java, /EntityEncounterPolicy\.roll\(/);
assert.doesNotMatch(java, /String\[\]\s+(?:entityPool|roamingPool)\s*=/);
assert.doesNotMatch(java, /int\[\]\s+(?:hazardThresholds|entityThresholds|lootThresholds|waterThresholds)\s*=/);
assert.doesNotMatch(java, /entityEncounterAction/);
assert.match(java, /SEARCH không được khởi tạo encounter Entity mới/);
assert.match(java, /đây là action duy nhất được phép kích hoạt roll encounter Entity mới/);
assert.match(java, /không tự đổi mục tiêu và không khởi tạo encounter Entity mới/);

// Provider candidate preview is bridge-only in Java. The actual Party/Player/Flag decisions are
// materialized in checked-in Kotlin Core and rechecked there before command commit.
assert.match(java, /PartyCandidatePolicy\.allowsProviderAddition\(/);
assert.match(java, /PartyCandidatePolicy\.allowsRemoval\(action\)/);
assert.match(java, /PlayerCandidatePolicy\.applyPatch\(/);
assert.match(java, /FlagCandidatePolicy\.applyOperation\(/);
assert.doesNotMatch(java, /characterAddAllowed\(/);
assert.doesNotMatch(java, /flagRootAllowed\(/);
assert.doesNotMatch(java, /boolean worldConsequence = rollSuccess\(rolls/);
assert.doesNotMatch(java, /boolean recoveryIntent = containsAny\(action/);
assert.doesNotMatch(java, /boolean gearIntent = containsAny\(action/);

assert.match(core, /PartyCandidatePolicy\.allowsCoreAddition\(/);
assert.match(core, /PartyCandidatePolicy\.allowsRemoval\(action\)/);
assert.match(core, /PlayerCandidatePolicy\.sanitizeCandidate\(/);
assert.match(core, /FlagCandidatePolicy\.sanitizeCandidate\(/);
assert.match(core, /playerJson = sanitizedPlayer\?\.toString\(\)/);
assert.match(core, /flagsJson = sanitizedFlags\?\.toString\(\)/);
assert.doesNotMatch(core, /Backward-compatible adapter/);
assert.doesNotMatch(core, /fun processValidatedCandidate\(beforeJson: String, candidateJson: String, action: String\)/);

for (const retired of [
  'patch-party-authority-final.py',
  'patch-player-authority-final.py',
  'patch-flag-authority-final.py',
]) {
  assert.equal(fs.existsSync(`${root}/${retired}`), false, `${retired} must stay retired after Kotlin materialization`);
}

assert.match(partyPolicy, /object PartyCandidatePolicy/);
assert.match(partyPolicy, /allowsCoreAddition\(/);
assert.match(partyPolicy, /anNhienEncounter/);
assert.match(partyPolicy, /irisReunion/);
assert.match(partyPolicy, /syvialReunion/);
assert.match(partyPolicy, /survivor/);

assert.match(html, /GM_CHOICES_FREEDOM_V1/);
assert.match(html, /#searchActionButton,#exploreActionButton\{display:none!important\}/);
assert.match(html, /if\(search\)search\.remove\(\)/);
assert.match(html, /if\(explore\)explore\.remove\(\)/);
assert.match(html, /\.primary-action-row\{grid-template-columns:minmax\(0,1fr\)!important\}/);
assert.match(html, /#submit\{width:100%\}/);
assert.match(html, /window\.backroomChoices=function\(payload\)/);
assert.match(html, /button\.textContent=choice\.id\+"\. "\+choice\.label\.toLocaleUpperCase\("vi-VN"\)/);
assert.match(html, /window\.Android\.submitAction\(JSON\.stringify\(state\),choice\.actionKind,choice\.action\)/);
assert.match(html, /Android\.submitFreedom\(JSON\.stringify\(state\),a\)/);
assert.doesNotMatch(html, /Android\.submitAction\(JSON\.stringify\(state\),"EXECUTE",a\)/);
assert.match(html, /text-decoration:underline/);
assert.match(html, /button\.textContent=/);
assert.doesNotMatch(html, /innerHTML=choice\./);

console.log('GM choices/Freedom regression contract OK');
