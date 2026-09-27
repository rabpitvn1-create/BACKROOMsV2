'use strict';

const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const root=path.resolve(__dirname,'../app/src/main');
const activity=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/MainActivity.java'),'utf8');
const ui=fs.readFileSync(path.join(root,'assets/player-action-ui.js'),'utf8');
const facade=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/core/GameCoreFacade.java'),'utf8');

test('PLAYER ACTION uses the same submitTurn bridge as Explorer choices',()=>{
  assert.match(ui,/Android\.submitTurn\(JSON\.stringify\(state\), text\)/);
  assert.doesNotMatch(ui,/submitEnvironmentAction|backroomEnvironment/);
  assert.match(activity,/@JavascriptInterface public void submitTurn\(String stateJson, String action\)/);
  assert.doesNotMatch(activity,/@JavascriptInterface public void submitEnvironmentAction/);
});

test('PLAYER ACTION is not gated by authored Story state',()=>{
  assert.doesNotMatch(ui,/state\.story|storyCutaway|storyBootstrap|storyDecision|storyEntity|storyAdvance/);
  const lock=ui.split('function playerActionLocked')[1].split('function fitVisualViewport')[0];
  assert.match(lock,/combatActive\(\)/);
  assert.match(lock,/deathRestartPending\(\)/);
  assert.match(lock,/processing\(\)/);
});

test('Core fallback still prepares modern exploration systems before GM narration',()=>{
  const body=facade.split('public synchronized String processRule')[1]
    .split('public synchronized String processValidatedCandidate')[0];
  assert.match(body,/levelCore\.rollRouteForExplorerAction/);
  assert.match(body,/itemCore\.prepareExplorationLoot/);
  assert.match(body,/entityCore\.prepareEncounter/);
  assert.match(body,/characterEncounterCore\.rollForExplorerAction/);
  assert.match(body,/fallback_required/);
  assert.doesNotMatch(body,/StoryCore|advanceAndRender|awaitingDecision|returnJourney/);
});
