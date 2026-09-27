'use strict';

const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const root=path.resolve(__dirname,'../app/src/main');
const gmChoice=fs.readFileSync(path.join(root,'assets/gm-choice-ui.js'),'utf8');
const activity=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/MainActivity.java'),'utf8');

test('GM Explorer A B C choices feed the normal submit form',()=>{
  const body=gmChoice.split('function submitExplorerChoice')[1]
    .split('function chestPresent')[0];
  assert.match(body,/choice\.action \|\| choice\.text/);
  assert.match(body,/action\.value = text/);
  assert.match(body,/form\.requestSubmit\(\)/);
  assert.doesNotMatch(body,/resolveStoryDecision|prepareStoryDecision|returnJourney/);
});

test('Explorer rendering uses GM choices and keeps fallback choices',()=>{
  const body=gmChoice.split('function appendExplorerChoices')[1]
    .split('function captureLogAnchor')[0];
  assert.match(body,/Array\.isArray\(entry\.choices\)/);
  assert.match(body,/fallbackExplorerChoices\(\)/);
  assert.match(body,/choices\.slice\(0, 3\)/);
  assert.match(body,/submitExplorerChoice\(entry, choice\)/);
});

test('GM choice UI contains no authored Story state or Story bridge',()=>{
  assert.doesNotMatch(gmChoice,/state\.story|StoryCore|storyAwaiting|storyDecision|storyReturn|returnJourney/);
  assert.doesNotMatch(activity,/prepareStoryDecision|resolveStoryDecision|prepareReturnJourneyTurn|resolveReturnJourneyChoice|attackStoryEntity/);
});

test('combat and death remain the only composer gameplay locks',()=>{
  const body=gmChoice.split('function syncComposer')[1]
    .split("var dicePanel")[0];
  assert.match(body,/combat/);
  assert.match(body,/deathRestartPending\(\)/);
  assert.doesNotMatch(body,/story/i);
});
