const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

const uiPath = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'gm-choice-ui.js');
const source = fs.readFileSync(uiPath, 'utf8');
const coreFacadePath = path.join(__dirname, '..', 'app', 'src', 'main', 'java', 'com', 'rabpit', 'backroom', 'core', 'GameCoreFacade.java');
const coreFacadeSource = fs.readFileSync(coreFacadePath, 'utf8');

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


test('combat completion scrolls to the start of the next GM narration', () => {
  const start = source.indexOf('function finishCombatAnimation');
  const end = source.indexOf('window.backroomCombatTurn', start);
  assert.ok(start >= 0 && end > start);
  const finishBlock = source.slice(start, end);
  assert.match(finishBlock, /scrollForCurrentMode\(\)/);
  assert.doesNotMatch(finishBlock, /scrollCombatToBottom\(\)/);
});

test('Core upgrades use persisted state and are locked only by active combat', () => {
  const start = coreFacadeSource.indexOf('public synchronized String processCoreUpgrade');
  const end = coreFacadeSource.indexOf('public synchronized String levelSnapshotDescriptor', start);
  assert.ok(start >= 0 && end > start);
  const upgradeBlock = coreFacadeSource.slice(start, end);
  assert.match(upgradeBlock, /JSONObject state = parseState\(preferences\.getString\(STATE_KEY, "\{\}"\)\)/);
  assert.match(upgradeBlock, /if \(state\.length\(\) == 0\) state = submitted;/);
  assert.match(upgradeBlock, /CombatChoiceEngine\.isActive\(state\)/);
  assert.doesNotMatch(upgradeBlock, /storyCore\.awaitingDecision|storyCore\.awaitingEntityAttack|storyCore\.hasPendingStoryAdvance/);
});


test('dice fill their cells and animate visibly while ROLL is resolving', () => {
  assert.match(source, /\.combat-dice-row\{[^}]*gap:5px;perspective:720px/);
  assert.match(source, /\.combat-die\{padding:1px;/);
  assert.match(source, /\.combat-die img\{width:104%;height:104%/);
  assert.match(source, /@keyframes combat-die-roll/);
  assert.match(source, /var DICE_ROLL_ANIMATION_MS=650;/);
  assert.match(source, /diceRollAnimating=true;/);
  assert.match(source, /rolling=diceRollAnimating&&held\[index\]!==true/);
  assert.match(source, /animationDelay=String\(index\*-55\)\+'ms'/);
  assert.match(source, /DICE_ROLL_ANIMATION_MS-\(Date\.now\(\)-diceRollStartedAt\)/);
});


test('GM effect highlights keep the normal narration font', () => {
  assert.match(source, /\.message\.gm \.semantic-effect,\.message\.gm \.semantic-damage,\.message\.gm \.semantic-buff\{font-family:inherit\}/);
  assert.match(source, /\.semantic-effect\{color:#ff9f43\}/);
  assert.match(source, /\.semantic-damage\{color:#ff5c5c\}/);
  assert.match(source, /\.semantic-buff\{color:#73e6a2\}/);
});


test('encounter stays on the current Explorer Turn until victory opens the next turn', () => {
  const decisionStart = coreFacadeSource.indexOf('public synchronized String processStoryDecision');
  const decisionEnd = coreFacadeSource.indexOf('public synchronized String processStoryEntityAttack', decisionStart);
  const decisionBlock = coreFacadeSource.slice(decisionStart, decisionEnd);
  const encounterRoll = decisionBlock.indexOf('entityCore.prepareEncounter(state);');
  const turnAdvance = decisionBlock.indexOf('incrementTurn(state);');
  assert.ok(encounterRoll >= 0 && turnAdvance > encounterRoll);
  assert.match(decisionBlock, /if \(CombatChoiceEngine\.isKnownEntity\(encounter\)\)[\s\S]*CombatChoiceEngine\.start\(state, encounter[\s\S]*\} else \{[\s\S]*incrementTurn\(state\)/);

  const validatedStart = coreFacadeSource.indexOf('public synchronized String processValidatedCandidate');
  const validatedEnd = coreFacadeSource.indexOf('public synchronized String storyDecisionPrefetchRequest', validatedStart);
  const validatedBlock = coreFacadeSource.slice(validatedStart, validatedEnd);
  assert.match(validatedBlock, /CombatChoiceEngine\.isKnownEntity\(encounterKey\(before\)\)[\s\S]*sanitized\.put\("turn", Math\.max\(1, before\.optInt\("turn", 1\)\)\)/);

  const combatStart = coreFacadeSource.indexOf('public synchronized String processCombatResolution');
  const combatEnd = coreFacadeSource.indexOf('public synchronized String levelPromptContext', combatStart);
  const combatBlock = coreFacadeSource.slice(combatStart, combatEnd);
  assert.match(combatBlock, /wasActive && !active && "victory"\.equals\(outcome\)[\s\S]*incrementTurn\(state\)/);
  assert.match(combatBlock, /storyCore\.awaitingDecision\(state\)[\s\S]*storyCore\.refreshLoopDecisionContext\(state\)/);
  assert.match(source, /Entity bị tiêu diệt\. Bắt đầu Turn/);
});
