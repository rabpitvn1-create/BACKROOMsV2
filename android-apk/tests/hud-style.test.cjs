const fs=require('node:fs');
const path=require('node:path');
const test=require('node:test');
const assert=require('node:assert/strict');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/index.html'),'utf8');

test('snapshot uses the Tu Tien x Backroom ritual frame without changing its DOM contract',()=>{
  assert.match(source,/\.snapshot\{[^}]*isolation:isolate/);
  assert.match(source,/\.snapshot::before\{/);
  assert.match(source,/\.snapshot::after\{/);
  assert.match(source,/rgba\(166,126,59,\.72\)/);
  assert.match(source,/rgba\(124,32,38,\.72\)/);
  assert.match(source,/<div class="snapshot" id="snapshot">/);
});

test('THUC HIEN is a distinct primary ritual action without changing submit behavior',()=>{
  assert.match(source,/#submit\{[^}]*min-height:50px/);
  assert.match(source,/#submit\{[^}]*linear-gradient\(180deg,#65171d/);
  assert.match(source,/#submit::before\{/);
  assert.match(source,/#submit::after\{/);
  assert.match(source,/<button id="submit" type="submit">THỰC HIỆN<\/button>/);
});
