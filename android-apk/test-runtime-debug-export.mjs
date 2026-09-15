import fs from 'node:fs';
import path from 'node:path';

const root = process.argv[2] || 'android-apk';
const workflow = fs.readFileSync('.github/workflows/build-backroom-apk.yml', 'utf8');
const lowRiskPatch = fs.readFileSync(path.join(root, 'patch-low-risk-canon-fallback-final.py'), 'utf8');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');
const runtimeLog = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/core/RuntimeDebugLog.kt'), 'utf8');
const fallback = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/core/CanonFallbackPolicy.kt'), 'utf8');
const pipelineLogger = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/core/GamePipelineLogger.kt'), 'utf8');
const facade = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt'), 'utf8');
const html = fs.readFileSync(path.join(root, 'app/src/main/assets/index.html'), 'utf8');

function requireContract(value, message) {
  if (!value) throw new Error(message);
}

const chainStart = workflow.indexOf('scripts=(');
const chainEnd = workflow.indexOf('for script in', chainStart);
requireContract(chainStart >= 0 && chainEnd > chainStart, 'Android runtime patch chain not found');
const chain = workflow.slice(chainStart, chainEnd);
requireContract(chain.includes('patch-low-risk-canon-fallback-final.py'), 'terminal low-risk canon fallback missing from workflow');

const finalPatches = [
  'patch-runtime-debug-export-final.py',
  'patch-runtime-debug-provider-trace-final.py',
  'patch-runtime-debug-turn-trace-adaptive-final.py',
  'patch-runtime-debug-fallback-compat-final.py',
  'patch-runtime-debug-core-trace-final.py',
  'patch-runtime-debug-ui-events-final.py',
];
let previous = -1;
for (const patch of finalPatches) {
  const index = lowRiskPatch.indexOf(patch);
  requireContract(index > previous, `${patch} must be delegated in final debug authority order`);
  previous = index;
}

requireContract(!main.includes("b.textContent='Snapshot chưa cấu hình'"), 'legacy disabled Snapshot button survived generated Java');
requireContract(main.includes("b.textContent='XUẤT LOG'"), 'XUẤT LOG button missing');
requireContract(main.includes("b.id='debugLogButton'"), 'debug log button id missing');
requireContract(main.includes('Android.exportDebugLog(JSON.stringify(state))'), 'XUẤT LOG does not call native export bridge');
const buttonStart = main.indexOf("b.id='debugLogButton'");
const buttonEnd = main.indexOf('});', buttonStart);
const buttonBody = main.slice(buttonStart, buttonEnd + 3);
requireContract(!buttonBody.includes('requestSnapshot'), 'XUẤT LOG must not call Snapshot');
requireContract(main.includes('@JavascriptInterface public void exportDebugLog(String stateJson)'), 'native export bridge missing');
requireContract(main.includes('android.content.Intent.ACTION_CREATE_DOCUMENT'), 'ACTION_CREATE_DOCUMENT export missing');
requireContract(main.includes('application/json'), 'JSON document MIME type missing');
requireContract(main.includes('Backroom-Debug-'), 'debug export filename prefix missing');
requireContract(main.includes('RuntimeDebugLog.exportJson(current)'), 'native bridge does not export runtime buffer');
requireContract(main.includes('runtime-debug-pending.json'), 'debug export must persist payload across document-picker Activity recreation');
requireContract(main.includes('persistPendingDebugLog(pendingDebugLogJson)'), 'prepared debug payload is not persisted before launching the picker');
requireContract(main.includes('String exportJson = loadPendingDebugLog();'), 'document result must recover the persisted payload when the Activity instance was recreated');
requireContract(main.includes('openOutputStream(data.getData(), \"wt\")'), 'debug export must explicitly truncate/write the destination document');
requireContract(!main.includes('data.getData() == null || pendingDebugLogJson == null'), 'document result must not depend only on the volatile Activity field');

for (const marker of [
  'writerPromptText',
  'writerRaw',
  'writerParsed',
  'operationsInitial',
  'candidateInitial',
  'rolls',
  'auditInitial',
  'repairPromptText',
  'repairRaw',
  'repairParsed',
  'auditAfterRepair',
  'CanonFallbackPolicy.diagnostics(',
  'CanonFallbackPolicy.isEligible(',
  'canonFallback',
  'CANON FALLBACK REJECTED BECAUSE:',
  'candidateBeforeCore',
  'gameCoreValidatedCandidate',
  'finalCommittedState',
  'stateDiff',
  'debugProcessCombat',
  'providerRouting',
  'auditRaw',
]) requireContract(main.includes(marker), `generated runtime trace marker missing: ${marker}`);

for (const marker of [
  'candidateAfterStoryNormalization',
  'storyProgression',
  'gameCorePending',
  'gameCoreCommands',
  'gameCoreCommitResult',
  'coreCommittedState',
]) requireContract(facade.includes(marker), `GameCore trace marker missing: ${marker}`);
requireContract(!facade.includes('candidateBeforeStoryNormalization'), 'legacy pre-story-normalization trace marker must not return');
requireContract(!facade.includes('candidateNormalized'), 'legacy duplicate-normalization trace marker must not return');

for (const marker of [
  'fun diagnostics(',
  '"eligible"',
  '"reason"',
  '"changedTopLevelKeys"',
  '"changedFlagRoots"',
  '"dangerousRollConsequence"',
  '"dangerousStateChanged"',
]) requireContract(fallback.includes(marker), `Canon fallback diagnostic marker missing: ${marker}`);

for (const marker of [
  'schemaVersion',
  'session',
  'events',
  'turns',
  'stateBefore',
  'stateAfter',
  'sanitizeValue',
  'authorization',
  'apikey',
  'token',
  'secret',
  'credential',
  'password',
  '[REDACTED]',
]) requireContract(runtimeLog.includes(marker), `runtime debug log contract missing: ${marker}`);

requireContract(pipelineLogger.includes('RuntimeDebugLog.recordEvent('), 'GameCore pipeline events are not mirrored to runtime export');
requireContract(!runtimeLog.includes('GEMINI_API_KEY_1'), 'RuntimeDebugLog must not reference Gemini API key values');
requireContract(!runtimeLog.includes('HAIKU_API'), 'RuntimeDebugLog must not reference Haiku API key values');

for (const marker of [
  'RUNTIME_DEBUG_UI_V1',
  'Android.debugUiEvent',
  'submitAction',
  'saveButton',
  'loadButton',
  'newGameButton',
  'deleteSaveButton',
  'debugLogButton',
  'combat_actor_swap',
  'backroomTurn_received',
  'backroomError_received',
  'backroomDebugExport',
]) requireContract(html.includes(marker), `runtime debug UI marker missing: ${marker}`);

console.log('Runtime debug export generated regression checks passed.');
