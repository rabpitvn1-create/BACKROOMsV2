const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const assets=path.join(__dirname,'../app/src/main/assets');
const index=fs.readFileSync(path.join(assets,'index.html'),'utf8');
const snapshot=fs.readFileSync(path.join(assets,'snapshot-ui.js'),'utf8');
const gmChoice=fs.readFileSync(path.join(assets,'gm-choice-ui.js'),'utf8');
const inventory=fs.readFileSync(path.join(assets,'inventory-ui.js'),'utf8');
const party=fs.readFileSync(path.join(assets,'party-ui.js'),'utf8');

function pngSize(file){
  const data=fs.readFileSync(file);
  assert.equal(data.toString('ascii',1,4),'PNG',file);
  return [data.readUInt32BE(16),data.readUInt32BE(20)];
}

test('generated Tu Tien HUD asset files remain present on disk at expected dimensions',()=>{
  assert.deepEqual(pngSize(path.join(assets,'hud/snapshot_frame.png')),[1536,1152]);
  assert.deepEqual(pngSize(path.join(assets,'hud/action_plate.png')),[1400,300]);
});

test('snapshot returns to clean dark frame without decorative/status HUD layers',()=>{
  assert.doesNotMatch(snapshot,/snapshot_frame\.png/);
  assert.doesNotMatch(snapshot,/\.snapshot-hud-frame/);
  assert.doesNotMatch(snapshot,/\.combat-status-hud/);
  assert.doesNotMatch(snapshot,/__combatStatusIcons/);
  assert.match(snapshot,/\.combat-float\{[^}]*z-index:8/);
});

test('PLAYER ACTION and THUC HIEN return to standard dark button style without action_plate.png background',()=>{
  assert.doesNotMatch(index,/action_plate\.png/);
  assert.match(index,/\.player-action-bar #playerActionOpen,#submit\{/);
  assert.match(index,/<button id="playerActionOpen" type="button">PLAYER ACTION<\/button>/);
  assert.match(index,/<button id="submit" type="submit">THỰC HIỆN<\/button>/);
  assert.match(index,/#playerActionOpen\{min-height:64px/);
  assert.match(index,/#submit\{min-height:52px/);
});

test('normal rectangular UI elements use consistent light rounded corners while GM message frame remains square',()=>{
  // Normal UI boxes have rounded corners
  assert.match(index,/\.game,\.card\{[^}]*border-radius:/);
  assert.match(index,/\.snapshot\{[^}]*border-radius:[^;]+;overflow:hidden/);
  assert.match(index,/\.player-action-sheet\{[^}]*border-radius:/);
  assert.match(index,/textarea\{[^}]*border-radius:/);
  assert.match(index,/button\{[^}]*border-radius:/);
  assert.match(index,/\.chips span\{[^}]*border-radius:/);

  assert.match(gmChoice,/\.gm-choice\{[^}]*border-radius:/);
  assert.match(gmChoice,/\.combat-dice-panel\{[^}]*border-radius:/);
  assert.match(gmChoice,/\.combat-die\{[^}]*border-radius:/);

  assert.match(inventory,/\.inventory-item\{[^}]*border-radius:/);
  assert.match(inventory,/\.inventory-sheet\{[^}]*border-radius:/);

  assert.match(party,/\.party-member-card\{[^}]*border-radius:/);
  assert.match(party,/\.party-detail-section\{[^}]*border-radius:/);

  // GAME MASTER frame exception: explicit border-radius:0
  assert.match(index,/\.message\{[^}]*border-radius:0/);
  assert.match(gmChoice,/\.message\.gm\{[^}]*border-radius:0/);
});

test('mobile header uses packaged Backrooms artwork without changing snapshot assets',()=>{
  assert.match(index,/BACKROOM_HEADER_ART_V2/);
  assert.match(index,/url\('level_snapshots\/drive\/level_0\/01\.webp'\)/);
  assert.match(index,/\.topbar\{[^}]*background-image:/);
  assert.match(index,/\.topbar \.eyebrow,\.topbar h1\{[^}]*text-shadow:/);
  assert.doesNotMatch(snapshot,/BACKROOM_HEADER_ART_V2/);
});
