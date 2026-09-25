'use strict';

const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const root=path.resolve(__dirname,'../app/src/main');
const activity=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/MainActivity.java'),'utf8');
const facade=fs.readFileSync(path.join(root,'java/com/rabpit/backroom/core/GameCoreFacade.java'),'utf8');

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
