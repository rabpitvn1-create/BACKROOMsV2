import fs from 'node:fs';
import path from 'node:path';

const root = process.argv[2] || 'android-apk';
const workflow = fs.readFileSync('.github/workflows/build-backroom-apk.yml', 'utf8');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');
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
  'patch-low-risk-canon-fallback-final.py',
];
for (let index = 1; index < ordered.length; index += 1) {
  requireContract(patchChain.indexOf(ordered[index - 1]) < patchChain.indexOf(ordered[index]), `patch ordering is wrong: ${ordered[index]}`);
}
requireContract(!gmPolicy.includes('patch-low-risk-canon-fallback-final.py'), 'fallback must not run from nested action-policy patch');
requireContract(main.includes('CanonFallbackPolicy.isEligible('), 'runtime fallback policy call missing');
requireContract(main.includes('candidateState = new JSONObject(before.toString());'), 'fallback must discard model candidate state');
requireContract(main.includes('.put("ops", new JSONArray())'), 'fallback must discard model operations');
requireContract(main.includes('.put("choices", new JSONArray())'), 'fallback must discard unsafe model choices');
requireContract(main.includes('canon_safe_fallback'), 'fallback snapshot marker missing');
requireContract(main.includes('throw new Exception("Lượt chơi không vượt qua kiểm tra canon; state không được thay đổi.")'), 'fail-closed branch missing');

console.log('Low-risk canon fallback integration regression checks passed.');
