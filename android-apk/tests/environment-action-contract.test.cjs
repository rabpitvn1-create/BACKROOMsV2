'use strict';

const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const root=path.resolve(__dirname,'../app/src/main');
const activity=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/MainActivity.java'),'utf8');
const facade=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/core/GameCoreFacade.java'),'utf8');
const storyCore=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/core/StoryCore.java'),'utf8');
const playerActionUi=fs.readFileSync(path.join(root,'assets/player-action-ui.js'),'utf8');

test('PLAYER ACTION uses environment bridge and never commits a client-safe state snapshot',()=>{
  const body=activity.split('@JavascriptInterface public void submitEnvironmentAction')[1]
    .split('@JavascriptInterface')[0];
  assert.match(body,/gameCore\.commitEnvironmentExchange\(text, reply\)/);
  assert.doesNotMatch(body,/commitRuntimeState/);
  assert.doesNotMatch(body,/processRule/);
  assert.doesNotMatch(body,/processValidatedCandidate/);
});

test('environment exchange appends to persisted full Core state before returning client-safe state',()=>{
  const body=facade.split('public synchronized String commitEnvironmentExchange')[1]
    .split('public synchronized String commitRuntimeState')[0];
  assert.match(body,/preferences\.getString\(STATE_KEY, "\{\}"\)/);
  assert.match(body,/EnvironmentActionPacket\.appendExchange\(state, action, reply\)/);
  assert.match(body,/persist\(state\)/);
  assert.match(body,/clientSafeState\(state\)\.toString\(\)/);
});

test('Story recent context excludes environment-only exchanges',()=>{
  const body=activity.split('private String recentContext')[1]
    .split('private String appendEncounterDialogue')[0];
  assert.match(body,/!includeEnvironment && EnvironmentActionPacket\.SCOPE\.equals/);
  assert.match(body,/recentStoryContext\(JSONObject state\)[\s\S]*recentContext\(state, false\)/);
});


test('PLAYER ACTION binds its visible button and locks while a Story decision is preparing',()=>{
  assert.match(playerActionUi,/openButton\.addEventListener\('click', openPlayerAction\)/);
  assert.match(playerActionUi,/function storyDecisionPreparing\(\)/);
  assert.match(playerActionUi,/story\.awaitingDecision === true/);
  assert.match(playerActionUi,/String\(story\.decisionStatus \|\| ''\) !== 'READY'/);
  assert.match(playerActionUi,/environmentLocked\(\)[\s\S]*storyDecisionPreparing\(\)/);
});

test('PLAYER ACTION maps only a committed current Story choice and does not stale-fallthrough',()=>{
  const body=activity.split('@JavascriptInterface public void submitEnvironmentAction')[1]
    .split('@JavascriptInterface')[0];
  assert.match(body,/throw new Exception\("story_decision_preparing"\)/);
  assert.match(body,/semanticStoryChoiceMatch\(story, text\)/);
  assert.match(body,/gameCore\.selectStoryDecision\(submitted\.toString\(\), choiceId\)/);
  assert.match(body,/emit\("backroomTurn", selected\.getJSONObject\("state"\)\.toString\(\)\);\s*return;/);
  assert.ok(body.indexOf('selectStoryDecision') < body.indexOf('EnvironmentActionPacket.build'));
  assert.match(activity,/FULL_COMMIT/);
  assert.match(activity,/NOT_FULL_COMMIT/);
  assert.match(activity,/NO_MATCH\|AMBIGUOUS\|PARTIAL\|CONDITIONAL/);
  assert.match(activity,/question, inspection, hesitation, future intent, negation/);
});

test('RETURN and STAY generation stays non-progressing but produces meaningful horror detours',()=>{
  const body=storyCore.split('JSONObject decisionGenerationRequest')[1]
    .split('void installDecisionPackage')[0];
  assert.match(body,/short spatial\/horror detour beat/);
  assert.match(body,/short observation\/tension beat/);
  assert.match(body,/meaningful survival-horror responses/);
  assert.match(body,/do not advance authored progression/);
  assert.match(body,/Do not grant rewards/);
  assert.match(body,/persistent facts the Core would need to remember/);
});
