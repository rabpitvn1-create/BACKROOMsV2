const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const uiPath = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'gm-choice-ui.js');
const source = fs.readFileSync(uiPath, 'utf8');

test('Poker Dice uses one reusable inline panel inside the GM log', () => {
  assert.equal((source.match(/dicePanel=document\.createElement\('section'\)/g) || []).length, 1);
  assert.match(source, /article\.appendChild\(dicePanel\)/);
  assert.doesNotMatch(source, /document\.body\.appendChild\(dicePanel\)/);
  assert.doesNotMatch(source, /combat-dice-modal/);
  assert.doesNotMatch(source, /\.combat-dice-panel\{[^}]*position:fixed/);
});

test('combat hides Explorer A B C choices while the inline dice panel is active', () => {
  const start = source.indexOf('function appendExplorerChoices');
  const end = source.indexOf('function renderSemanticLog', start);
  assert.ok(start >= 0 && end > start);
  assert.match(source.slice(start, end), /if \(state\.combat && state\.combat\.active\) return;/);
});

test('reroll UI does not append GM messages', () => {
  const start = source.indexOf('function sendCombatRoll()');
  const end = source.indexOf('function sendCombatFinish()', start);
  assert.ok(start >= 0 && end > start);
  const rollBlock = source.slice(start, end);
  assert.match(rollBlock, /Android\.combatRoll/);
  assert.doesNotMatch(rollBlock, /state\.log|appendBattleSection|appendLog/);
});

test('current hand is shown before FINISH', () => {
  assert.match(source, /diceResult\.textContent=hasRolled\?handLabel\(dice\.hand\):'';/);
  assert.match(source, /'TWO PAIR':'Two Pair'/);
});

test('ROLL and FINISH are separate controls', () => {
  assert.match(source, /id="combatDiceRoll">ROLL<\/button>/);
  assert.match(source, /id="combatDiceFinish">FINISH<\/button>/);
  assert.match(source, /Android\.combatFinish/);
  assert.match(source, /diceRoll\.disabled=.*rerolls>=maxRerolls\|\|allHeld\(held\)/);
});

test('production dice UI uses local assets and no Unicode dice glyphs', () => {
  assert.match(source, /file:\/\/\/android_asset\/dice\/die-/);
  assert.doesNotMatch(source, /[⚀⚁⚂⚃⚄⚅]/);
  assert.doesNotMatch(source, /Math\.random/);
});

test('finished hand waits two seconds before Core resolve', () => {
  assert.match(source, /setTimeout\(function\(\)[\s\S]*Android\.combatResolve\(JSON\.stringify\(state\)\)[\s\S]*},2000\)/);
});


test('battle log uses semantic font for Cao Minh title and compact hand tokens', () => {
  assert.match(source, /Vạn Giới Ma Tôn/);
  assert.match(source, /\[F\.O\.A\.K\]/);
  assert.match(source, /knownHandTokens\.forEach\(function\(x\)\{ addTerm\(map,x,'stat'\); \}\)/);
  assert.match(source, /Trúng độc/);
  assert.match(source, /Xuyên giáp/);
});
