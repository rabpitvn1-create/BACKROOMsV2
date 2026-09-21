const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const uiPath = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'gm-choice-ui.js');
const source = fs.readFileSync(uiPath, 'utf8');

test('Poker Dice uses one reusable modal outside GM log', () => {
  assert.equal((source.match(/document\.createElement\('div'\);\n  diceModal\.className='combat-dice-modal'/g) || []).length, 1);
  assert.match(source, /document\.body\.appendChild\(diceModal\)/);
  assert.doesNotMatch(source, /article\.appendChild\(diceModal\)/);
});

test('reroll UI does not append GM messages', () => {
  const start = source.indexOf('function sendCombatRoll()');
  const end = source.indexOf('function scheduleCombatResolve', start);
  assert.ok(start >= 0 && end > start);
  const rollBlock = source.slice(start, end);
  assert.match(rollBlock, /Android\.combatRoll/);
  assert.doesNotMatch(rollBlock, /state\.log|appendBattleSection|appendLog/);
});

test('production dice UI uses local assets and no Unicode dice glyphs', () => {
  assert.match(source, /file:\/\/\/android_asset\/dice\/die-/);
  assert.doesNotMatch(source, /[⚀⚁⚂⚃⚄⚅]/);
  assert.doesNotMatch(source, /Math\.random/);
});

test('finalized hand waits two seconds before Core resolve', () => {
  assert.match(source, /setTimeout\(function\(\)[\s\S]*Android\.combatResolve\(JSON\.stringify\(state\)\)[\s\S]*},2000\)/);
});
