'use strict';

const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const assets=path.join(__dirname,'../app/src/main/assets');
const index=fs.readFileSync(path.join(assets,'index.html'),'utf8');
const playerAction=fs.readFileSync(path.join(assets,'player-action-ui.js'),'utf8');

test('game shell uses one visual viewport height and keeps page scrolling locked',()=>{
  assert.match(index,/html,body\{height:100%;overflow:hidden;overscroll-behavior:none\}/);
  assert.match(index,/\.shell\{height:var\(--app-height,100dvh\);min-height:0;overflow:hidden;/);
  assert.match(index,/\.shell\{height:var\(--app-height,100dvh\);min-height:0;overflow:hidden;padding:0 max\(8px,env\(safe-area-inset-right\)\)/);
  assert.match(index,/\.topbar\{padding-top:max\(6px,env\(safe-area-inset-top\)\)\}/);
  assert.match(index,/safe-area-inset-right/);
  assert.match(index,/safe-area-inset-bottom/);
  assert.match(index,/safe-area-inset-left/);
  assert.match(index,/function syncViewportHeight\(\)/);
  assert.match(index,/window\.visualViewport/);
  assert.match(index,/setProperty\("--app-height",height\+"px"\)/);
});

test('gameplay frame is fixed while only the narrative log consumes leftover height',()=>{
  assert.match(index,/\.game\{height:100%;min-height:0;display:grid;grid-template-rows:auto auto minmax\(0,1fr\) auto auto;overflow:hidden\}/);
  assert.match(index,/\.log\{height:auto;min-height:0;overflow:auto;overscroll-behavior:contain;-webkit-overflow-scrolling:touch;/);
  assert.doesNotMatch(index,/\.log\{height:clamp\(320px,48vh,540px\)/);
});

test('keyboard-sensitive controls use the shared viewport budget',()=>{
  assert.match(index,/textarea\{min-height:150px;max-height:calc\(var\(--app-height,100dvh\) - 150px\);/);
  assert.match(index,/\.game-menu-sheet\{[^}]*max-height:min\(calc\(var\(--app-height,100dvh\) - 12px\),760px\)/);
  assert.match(playerAction,/window\.visualViewport\.addEventListener\('resize', fitVisualViewport\)/);
  assert.match(playerAction,/window\.visualViewport\.addEventListener\('scroll', fitVisualViewport\)/);
});

test('display cutout is painted while header controls remain below the notch',()=>{
  assert.doesNotMatch(index,/padding:max\(8px,env\(safe-area-inset-top\)\)/);
  assert.match(index,/\.topbar\{padding-top:max\(6px,env\(safe-area-inset-top\)\)\}/);
});
