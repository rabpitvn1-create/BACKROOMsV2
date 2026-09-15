import fs from 'node:fs';
import path from 'node:path';

const root = process.argv[2] || 'android-apk';
const workflow = fs.readFileSync('.github/workflows/build-backroom-apk.yml', 'utf8');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');
const fallback = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/core/CanonFallbackPolicy.kt'), 'utf8');
const gmPolicy = fs.readFileSync(path.join(root, 'patch-gm-action-policy-final.py'), 'utf8');
const chainStart = workflow.indexOf('scripts=(');
const chainEnd = workflow.indexOf('for script in', chainStart);
const patchChain = workflow.slice(chainStart, chainEnd);

function requireContract(value, message) {
  if (!value) throw new Error(message);
}

const ordered = [
  'patch-level-transition-final.py',
  'patch-haiku-provider-final.py',
  'patch-party-turn-combat-final.py',
  'patch-entity-rates-drops-final.py',
  'patch-gm-choices-freedom-final.py',
  'patch-lucia-story-gate-final.py',
  'patch-low-risk-canon-fallback-final.py',
];
for (let index = 1; index < ordered.length; index += 1) {
  requireContract(patchChain.indexOf(ordered[index - 1]) < patchChain.indexOf(ordered[index]), `patch ordering is wrong: ${ordered[index]}`);
}
requireContract(!gmPolicy.includes('patch-low-risk-canon-fallback-final.py'), 'fallback must not run from nested action-policy patch');
requireContract(main.includes('CanonFallbackPolicy.isEligible('), 'runtime fallback policy call missing');
requireContract(
  main.includes('candidateState = com.rabpit.backroom.core.StoryProgressionPolicy.normalizeCandidate('),
  'fallback must discard model candidate state while preserving deterministic engine story state',
);
requireContract(main.includes('StoryProgressionPolicy.directive(before, action)'), 'fallback must derive narration from the same story directive');
requireContract(main.includes('.put("ops", new JSONArray())'), 'fallback must discard model operations');
requireContract(main.includes('.put("choices", new JSONArray())'), 'fallback must discard unsafe model choices');
requireContract(main.includes('canon_safe_fallback'), 'fallback snapshot marker missing');
requireContract(fallback.includes('staleEntityContext'), 'stale non-combat Entity context recovery gate missing');
requireContract(fallback.includes('engineStoryFlagRoots'), 'engine-owned story roots must be separated from model-owned canon mutations');
requireContract(fallback.includes('engineStoryPartyDelta'), 'engine-owned Lucia Party transition must be distinguishable from arbitrary Party mutation');
requireContract(fallback.includes('StoryProgressionPolicy.normalizeCandidate('), 'fallback diagnostics must derive the deterministic story baseline from StoryProgressionPolicy');
requireContract(!fallback.includes('entityBefore -> "entity_present_before"'), 'pre-existing non-combat Entity context must not reject a no-op fallback by itself');
requireContract(fallback.includes('transitionBefore -> "transition_ready_before"'), 'transition-ready state must remain fail-closed');
requireContract(fallback.includes('dangerousState -> "accepted_authoritative_state_change"'), 'non-story authoritative state changes must remain fail-closed');
requireContract(!main.includes('type.equals("lucia_story")'), 'terminal fallback must not resurrect retired Lucia story transport');
requireContract(main.includes('throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.")'), 'fail-closed branch missing');

console.log('Low-risk canon fallback integration regression checks passed.');
await import('./test-runtime-debug-export.mjs');
