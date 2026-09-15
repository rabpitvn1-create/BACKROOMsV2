import fs from 'node:fs';
import path from 'node:path';

const root = process.argv[2] || 'android-apk';
const workflow = fs.readFileSync('.github/workflows/build-backroom-apk.yml', 'utf8');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');
const core = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.kt'), 'utf8');
const story = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/core/StoryProgressionPolicy.kt'), 'utf8');

function requireContract(value, message) {
  if (!value) throw new Error(message);
}

function methodBodyBySignature(source, signatureText, label) {
  const signature = source.indexOf(signatureText);
  requireContract(signature >= 0, `generated helper missing: ${label}`);
  const open = source.indexOf('{', signature);
  requireContract(open >= 0, `generated helper has no opening brace: ${label}`);
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let index = open; index < source.length; index += 1) {
    const ch = source[index];
    if (inString) {
      if (escaped) escaped = false;
      else if (ch === '\\') escaped = true;
      else if (ch === '"') inString = false;
      continue;
    }
    if (ch === '"') {
      inString = true;
      continue;
    }
    if (ch === '{') depth += 1;
    else if (ch === '}') {
      depth -= 1;
      if (depth === 0) return source.slice(open + 1, index);
    }
  }
  throw new Error(`generated helper has no closing brace: ${label}`);
}

const chainStart = workflow.indexOf('scripts=(');
const chainEnd = workflow.indexOf('for script in', chainStart);
const patchChain = workflow.slice(chainStart, chainEnd);
requireContract(patchChain.includes('patch-lucia-story-gate-final.py'), 'story authority finalizer is not in the Android runtime patch chain');
requireContract(
  patchChain.indexOf('patch-gm-choices-freedom-final.py') < patchChain.indexOf('patch-lucia-story-gate-final.py'),
  'story authority must run after the final GM writer patch',
);
requireContract(
  patchChain.indexOf('patch-lucia-story-gate-final.py') < patchChain.indexOf('patch-low-risk-canon-fallback-final.py'),
  'story authority must settle before the final canon fallback integration',
);

for (const marker of [
  'STORY ENGINE AUTHORITY:',
  'STORY ENGINE DIRECTIVE:',
  'AUTHORITATIVE STORY DIRECTIVE:',
  'StoryProgressionPolicy.directive(before, action)',
  'StoryProgressionPolicy.normalizeCandidate(before, candidateState, action)',
]) {
  requireContract(main.includes(marker), `generated single-story-authority marker missing: ${marker}`);
}

for (const retired of [
  'type.equals("lucia_story")',
  'applyLuciaStoryOperationAndroid',
  'luciaFirstContactLockedAndroid',
  'luciaPartyLockedAndroid',
  'luciaJoinConfirmedAndroid',
  'premature_lucia_first_contact',
  'premature_lucia_party',
  'LUCIA STATE TRANSPORT:',
]) {
  requireContract(!main.includes(retired), `retired duplicate story authority survived generated Java: ${retired}`);
}

const writerPrompt = methodBodyBySignature(main, 'private String writerPrompt(', 'writerPrompt');
requireContract(writerPrompt.includes('STORY ENGINE AUTHORITY:'), 'writer is not bound to engine-owned story state');
requireContract(writerPrompt.includes('STORY ENGINE DIRECTIVE:'), 'writer does not receive the deterministic story directive');
requireContract(writerPrompt.includes('không phát operation lucia_story'), 'writer is not explicitly forbidden from transporting Lucia story state');

const auditPrompt = methodBodyBySignature(main, 'private JSONObject runAudit(', 'runAudit');
requireContract(auditPrompt.includes('AUTHORITATIVE STORY DIRECTIVE:'), 'auditor does not receive the authoritative story directive');
requireContract(auditPrompt.includes('Do not report it as a model canon conflict'), 'auditor is not told to distinguish engine story delta from model delta');

const normalizeCall = 'StoryProgressionPolicy.normalizeCandidate(before, candidateState, action)';
requireContract(main.split(normalizeCall).length - 1 >= 2, 'story preview must normalize both initial and repaired candidates before canon risk');

for (const marker of [
  '@JvmStatic\n  fun directive(',
  '@JvmStatic\n  fun normalizeCandidate(',
  'restoreStoryOwnedState',
  'Directive.LUCIA_FIRST_CONTACT',
  'Directive.LUCIA_JOIN_DECISION',
  'applyFirstContact(',
  'applyJoinDecision(',
  'playerAgency", "mutual-party-decision"',
]) {
  requireContract(story.includes(marker), `StoryProgressionPolicy single-authority contract missing: ${marker}`);
}
requireContract(!story.includes('isValidFirstContactCandidate'), 'provider-authored first-contact validation survived StoryProgressionPolicy');
requireContract(!story.includes('candidateJoinAllowed'), 'provider-authored Lucia join gate survived StoryProgressionPolicy');

for (const marker of [
  'StoryProgressionPolicy.normalizeCandidate(before, JSONObject(candidateJson), action)',
  'internal fun synchronizeValidatedLuciaCharacter',
  'StoryProgressionPolicy.LEVEL0_FIRST_CONTACT',
  'StoryProgressionPolicy.LEVEL0_LUCIA_DECISION_COMPLETE',
  '"joinEligible" to joined.toString()',
  'val preparedCore = synchronizeValidatedLuciaCharacter(core, candidate)',
]) {
  requireContract(core.includes(marker), `Game State Core story commit contract missing: ${marker}`);
}

console.log('Single StoryProgressionPolicy authority and Lucia Party integration regression checks passed.');
