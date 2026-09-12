import fs from 'node:fs';
import path from 'node:path';

const root = process.argv[2] || 'android-apk';
const workflow = fs.readFileSync('.github/workflows/build-backroom-apk.yml', 'utf8');
const main = fs.readFileSync(path.join(root, 'app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');

function requireContract(value, message) {
  if (!value) throw new Error(message);
}

function methodBody(source, name) {
  const signature = source.indexOf(`private boolean ${name}(`);
  requireContract(signature >= 0, `generated Java helper missing: ${name}`);
  const open = source.indexOf('{', signature);
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
  throw new Error(`generated Java helper has no closing brace: ${name}`);
}

const chainStart = workflow.indexOf('scripts=(');
const chainEnd = workflow.indexOf('for script in', chainStart);
const patchChain = workflow.slice(chainStart, chainEnd);
requireContract(patchChain.includes('patch-lucia-story-gate-final.py'), 'Lucia story gate patch is not in the Android runtime patch chain');
requireContract(
  patchChain.indexOf('patch-gm-choices-freedom-final.py') < patchChain.indexOf('patch-lucia-story-gate-final.py'),
  'Lucia story gate must run after the GM choice patch so it audits the final writer path',
);
requireContract(
  patchChain.indexOf('patch-lucia-story-gate-final.py') < patchChain.indexOf('patch-low-risk-canon-fallback-final.py'),
  'Lucia story gate must run before the final low-risk fallback integration check',
);

for (const marker of [
  'LUCIA ENCOUNTER STORY GATE:',
  'STORY.LEVEL0.ARRIVAL -> STORY.LEVEL0.FIRST_CONTACT_COMPLETE -> STORY.LEVEL0.LUCIA_DECISION_COMPLETE',
  'luciaFirstContactLockedAndroid',
  'luciaPartyLockedAndroid',
  'luciaJoinConfirmedAndroid',
  'premature_lucia_first_contact',
  'premature_lucia_party',
]) {
  requireContract(main.includes(marker), `generated Lucia story gate marker missing: ${marker}`);
}

requireContract(!main.includes('private boolean luciaEncounterLockedAndroid('), 'legacy combined Lucia encounter lock survived generated Java');
requireContract(
  !main.includes('if (state == null || storyArcCompletedAndroid(state, "STORY.LEVEL0.ARRIVAL")) return false;'),
  'ARRIVAL completion must not disable all Lucia locks',
);

const firstContactLock = methodBody(main, 'luciaFirstContactLockedAndroid');
requireContract(firstContactLock.includes('!storyArcCompletedAndroid(state, "STORY.LEVEL0.ARRIVAL")'), 'first-contact lock must remain closed until Arrival is already complete');

const partyLock = methodBody(main, 'luciaPartyLockedAndroid');
requireContract(partyLock.includes('storyArcCompletedAndroid(before, "STORY.LEVEL0.FIRST_CONTACT_COMPLETE")'), 'party lock must require first-contact in the state that began the turn');
requireContract(partyLock.includes('luciaJoinConfirmedAndroid(before)'), 'party lock must preserve an already-confirmed join');
requireContract(partyLock.includes('luciaJoinConfirmedAndroid(candidate)'), 'party lock must allow the later decision turn to confirm join');

const joinConfirmation = methodBody(main, 'luciaJoinConfirmedAndroid');
for (const marker of [
  'STORY.LEVEL0.FIRST_CONTACT_COMPLETE',
  'STORY.LEVEL0.LUCIA_DECISION_COMPLETE',
  '"joined".equals(status)',
  'encounter.optBoolean("partyEligible", false)',
  '!encounter.optBoolean("joinPending", true)',
]) {
  requireContract(joinConfirmation.includes(marker), `Lucia join confirmation is incomplete: ${marker}`);
}

console.log('Lucia story gate integration regression checks passed.');
