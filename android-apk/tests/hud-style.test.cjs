const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');

const assets=path.join(__dirname,'../app/src/main/assets');
const index=fs.readFileSync(path.join(assets,'index.html'),'utf8');
const snapshot=fs.readFileSync(path.join(assets,'snapshot-ui.js'),'utf8');

function pngSize(file){
  const data=fs.readFileSync(file);
  assert.equal(data.toString('ascii',1,4),'PNG',file);
  return [data.readUInt32BE(16),data.readUInt32BE(20)];
}

test('generated Tu Tien HUD assets are present at expected dimensions',()=>{
  assert.deepEqual(pngSize(path.join(assets,'hud/snapshot_frame.png')),[1536,1152]);
  assert.deepEqual(pngSize(path.join(assets,'hud/action_plate.png')),[1400,300]);
});

test('snapshot frame is a real HUD image layer between sprites and status feedback',()=>{
  assert.match(snapshot,/\.snapshot-hud-frame\{[^}]*z-index:6/);
  assert.match(snapshot,/file:\/\/\/android_asset\/hud\/snapshot_frame\.png/);
  assert.match(snapshot,/appendSnapshotOverlay\(box\);appendSnapshotHudFrame\(box\);setTimeout\(renderCombatStatusHuds,0\)/);
  assert.match(snapshot,/\.combat-status-hud\{[^}]*z-index:7/);
  assert.match(snapshot,/\.combat-float\{[^}]*z-index:8/);
});

test('PLAYER ACTION and THUC HIEN use the generated action plate without changing button semantics',()=>{
  assert.match(index,/file:\/\/\/android_asset\/hud\/action_plate\.png/);
  assert.match(index,/\.player-action-bar #playerActionOpen,#submit\{/);
  assert.match(index,/<button id="playerActionOpen" type="button">PLAYER ACTION<\/button>/);
  assert.match(index,/<button id="submit" type="submit">THỰC HIỆN<\/button>/);
  assert.match(index,/#playerActionOpen\{min-height:64px/);
  assert.match(index,/#submit\{min-height:52px/);
});
