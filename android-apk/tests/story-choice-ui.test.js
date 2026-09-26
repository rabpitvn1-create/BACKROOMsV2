const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const crypto = require('node:crypto');

const assets = path.resolve(__dirname, '../app/src/main/assets');
class Element {
  constructor(tag = 'div') {
    this.tag = tag; this.children = []; this.handlers = {}; this.dataset = {};
    this.className = ''; this.disabled = false; this.scrollTop = 0;
    this.classList = { toggle() {} };
  }
  set textContent(value) { this.children = []; this.value = String(value); }
  get textContent() { return (this.value || '') + this.children.map(child => child.textContent).join(''); }
  set innerHTML(value) { this.children = []; this.value = String(value); }
  appendChild(child) { this.children.push(child); return child; }
  addEventListener(name, callback) { this.handlers[name] = callback; }
  getAttribute() { return ''; }
  setAttribute() {}
  querySelector() { return new Element(); }
  querySelectorAll(selector) {
    const found = [];
    const matches = el => selector.startsWith('.')
      ? selector.slice(1).split('.').every(x => el.className.split(' ').includes(x)) : false;
    const walk = el => { for (const child of el.children) { if (matches(child)) found.push(child); walk(child); } };
    walk(this); return found;
  }
  getBoundingClientRect() { return { top: 0 }; }
  click() { if (!this.disabled && this.handlers.click) this.handlers.click(); }
}
function buttons(root) {
  const result = [];
  const walk = el => { for (const child of el.children) { if (child.tag === 'button') result.push(child); walk(child); } };
  walk(root); return result;
}
function scenario(isReturn) {
  const log = new Element(), form = new Element(), action = new Element(), submit = new Element(), status = new Element();
  const style = new Element();
  const calls = { prepare: [], resolve: [], errors: [] };
  const choicePack = { contextHash: 'hash', choices: [
    { id: 'choice_ab1', text: 'Cao Minh xem dấu vết cạnh Lục Trầm' },
    { id: 'choice_cd2', text: 'Thử lối ở Level 0' },
    { id: 'choice_ef3', text: 'Kiểm tra món đồ trong góc' }
  ] };
  const story = { active: true, arcComplete: false, awaitingDecision: !isReturn,
    decisionStatus: 'PROVIDER_REQUIRED', decisionId: 'beat', decisionPackage: choicePack,
    returnJourney: { active: isReturn, journeyId: 'journey', turnIndex: 0,
      turnStatus: 'PROVIDER_REQUIRED', turnPackage: choicePack } };
  const context = { state: { story, combat: { active: false }, log: [{ role: 'gm', text: 'Cao Minh đứng lại.' }] }, busy: false,
    document: { head: { appendChild() {} }, createElement(tag) { return tag === 'style' ? style : new Element(tag); },
      createTextNode(text) { const node = new Element('#text'); node.textContent = text; return node; },
      getElementById(id) { return { log, form, action, submit, status }[id]; } },
    localStorage: { setItem() {} }, setTimeout, clearTimeout,
    requestAnimationFrame(callback) { callback(); },
    Android: { prepareStoryDecision(data) { calls.prepare.push(JSON.parse(data)); },
      prepareReturnJourneyTurn(data) { calls.prepare.push(JSON.parse(data)); },
      resolveStoryDecision(data, id) { calls.resolve.push(id); },
      resolveReturnJourneyChoice(data, id) { calls.resolve.push(id); } }
  };
  context.window = context;
  context.render = () => {};
  context.backroomTurn = json => { context.state = JSON.parse(json); context.busy = false; context.render(); };
  context.backroomError = message => { calls.errors.push(message); context.busy = false; };
  vm.runInNewContext(fs.readFileSync(path.join(assets, 'gm-choice-ui.js'), 'utf8'), context);
  return { context, log, style, calls };
}
for (const isReturn of [false, true]) {
  test(`${isReturn ? 'Return Journey' : 'Story'} covers unavailable choices while provider is loading`, async () => {
    const { context, log, style, calls } = scenario(isReturn);
    const choiceButtons = buttons(log);
    assert.equal(choiceButtons.length, isReturn ? 0 : 3);
    assert.ok(choiceButtons.every(button => button.disabled));
    if (!isReturn) {
      assert.equal(choiceButtons[0].textContent.includes('Cao Minh'), true);
      const semantic = choiceButtons[0].querySelectorAll('.semantic');
      assert.equal(semantic.some(span => span.className.includes('semantic-character') && span.textContent === 'Cao Minh'), true);
      assert.equal(log.querySelectorAll('.story-choice-loading').length, 1);
      assert.equal(log.querySelectorAll('.story-choice-hourglass')[0].tag, 'img');
      assert.match(log.querySelectorAll('.story-choice-loading-label')[0].textContent, /ĐANG TẢI LỰA CHỌN/);
    } else {
      assert.equal(log.querySelectorAll('.story-choice-loading').length, 0);
    }
    assert.match(style.textContent, /Play-Regular\.ttf/);
    assert.match(style.textContent, /story_choice_loading_backrooms\.webp/);
    assert.match(style.textContent, /story-choice-hourglass-pulse/);
    assert.match(style.textContent, /border:2px solid #d8b84a/);
    assert.match(style.textContent, /background-image:linear-gradient\(180deg,rgba\(8,9,6,.52\),rgba\(5,6,4,.82\)\)/);
    assert.match(style.textContent, /\.gm-choice\{[^}]*font-weight:400/);
    assert.match(style.textContent, /\.semantic\{[^}]*font-weight:700/);
    await new Promise(resolve => setTimeout(resolve, 10));
    assert.equal(calls.prepare.length, 1);
    context.render();
    await new Promise(resolve => setTimeout(resolve, 10));
    assert.equal(calls.prepare.length, 1);
    if (choiceButtons.length) choiceButtons[0].click();
    assert.equal(calls.resolve.length, 0);
    context.backroomTurn(JSON.stringify({ ...context.state, story: {
      ...context.state.story, ...(isReturn ? { returnJourney: { ...context.state.story.returnJourney, turnStatus: 'READY', lookaheadReady: true } }
        : { decisionStatus: 'READY' }) } }));
    assert.equal(log.querySelectorAll('.story-choice-loading').length, 0);
    buttons(log)[0].click();
    assert.equal(calls.resolve.length, 1);
    context.backroomTurn(JSON.stringify({ ...context.state, story: {
      ...context.state.story, ...(isReturn ? { returnJourney: { ...context.state.story.returnJourney, pendingChoiceId: 'choice_ab1' } }
        : { pendingChoiceId: 'choice_ab1' }) } }));
    assert.ok(buttons(log).slice(0, 3).every(button => button.disabled));
    assert.equal(calls.prepare.length, 1);
  });
  test(`${isReturn ? 'Return Journey' : 'Story'} retries once automatically and keeps manual recovery`, async () => {
    const { context, log, calls } = scenario(isReturn);
    await new Promise(resolve => setTimeout(resolve, 10));
    assert.equal(calls.prepare.length, 1);
    context.backroomError('provider failed');
    await new Promise(resolve => setTimeout(resolve, 10));
    assert.equal(calls.prepare.length, 2);
    assert.equal(calls.errors.length, 0);
    if (!isReturn) assert.equal(log.querySelectorAll('.story-choice-loading').length, 1);
    context.backroomError('provider failed again');
    await new Promise(resolve => setTimeout(resolve, 10));
    assert.equal(calls.prepare.length, 2);
    assert.equal(calls.errors.length, 1);
    const manual = buttons(log).find(button => button.textContent === 'Thử lại phản hồi');
    assert.ok(manual);
    assert.equal(buttons(log).length, isReturn ? 1 : 4);
    if (!isReturn) assert.equal(log.querySelectorAll('.story-choice-loading').length, 0);
    manual.click();
    assert.equal(calls.prepare.length, 3);
    if (!isReturn) assert.equal(log.querySelectorAll('.story-choice-loading').length, 1);
  });
}

test('Return Journey keeps the prepared current choices usable while lookahead loads', async () => {
  const { context, log, calls } = scenario(true);
  await new Promise(resolve => setTimeout(resolve, 10));
  calls.prepare.length = 0;
  calls.resolve.length = 0;
  calls.errors.length = 0;

  context.backroomTurn(JSON.stringify({ ...context.state, story: {
    ...context.state.story,
    returnJourney: {
      ...context.state.story.returnJourney,
      turnIndex: 1,
      turnStatus: 'READY',
      lookaheadReady: false,
      pendingChoiceId: ''
    }
  } }));

  const currentChoices = buttons(log).slice(0, 3);
  assert.equal(currentChoices.length, 3);
  assert.ok(currentChoices.every(button => !button.disabled));

  await new Promise(resolve => setTimeout(resolve, 10));
  assert.equal(calls.prepare.length, 1);

  currentChoices[0].click();
  assert.equal(calls.resolve.length, 1);

  context.backroomError('lookahead failed');
  await new Promise(resolve => setTimeout(resolve, 10));
  assert.equal(calls.errors.length, 0);
  assert.equal(log.querySelectorAll('.message.gm-error').length, 0);
});

test('Story and return choices use three distinct IMG_API Backrooms artworks', () => {
  const artFiles = [1, 2, 3].map(index =>
    path.join(assets, `hud/story_choice_${index}_backrooms.png`));
  for (const art of artFiles) {
    assert.equal(fs.existsSync(art), true);
    assert.equal(fs.statSync(art).size > 10000, true);
  }
  const hashes = artFiles.map(art =>
    crypto.createHash('sha256').update(fs.readFileSync(art)).digest('hex'));
  assert.equal(new Set(hashes).size, 3);

  const metadata = path.join(assets, 'hud/story_choice_backrooms.generated.json');
  assert.equal(fs.existsSync(metadata), true);

  const gmChoice = fs.readFileSync(path.join(assets, 'gm-choice-ui.js'), 'utf8');
  assert.match(gmChoice, /story-decision>\.gm-choice:nth-child\(1\).*story_choice_1_backrooms\.png/);
  assert.match(gmChoice, /story-decision>\.gm-choice:nth-child\(2\).*story_choice_2_backrooms\.png/);
  assert.match(gmChoice, /story-decision>\.gm-choice:nth-child\(3\).*story_choice_3_backrooms\.png/);
  assert.match(gmChoice, /story-return>\.gm-choice:nth-child\(1\).*story_choice_1_backrooms\.png/);
  assert.match(gmChoice, /story-return>\.gm-choice:nth-child\(2\).*story_choice_2_backrooms\.png/);
  assert.match(gmChoice, /story-return>\.gm-choice:nth-child\(3\).*story_choice_3_backrooms\.png/);
});

test('Story choice loading overlay uses its dedicated IMG_API Backrooms artwork', () => {
  const art = path.join(assets, 'hud/story_choice_loading_backrooms.webp');
  const hourglass = path.join(assets, 'hud/story_choice_hourglass_backrooms.webp');
  const metadata = path.join(assets, 'hud/story_choice_loading_backrooms.generated.json');
  assert.equal(fs.existsSync(art), true);
  assert.equal(fs.statSync(art).size <= 320 * 1024, true);
  assert.equal(fs.existsSync(hourglass), true);
  assert.equal(fs.statSync(hourglass).size <= 320 * 1024, true);
  assert.equal(fs.existsSync(metadata), true);
  const gmChoice = fs.readFileSync(path.join(assets, 'gm-choice-ui.js'), 'utf8');
  assert.match(gmChoice, /url\('hud\/story_choice_loading_backrooms\.webp'\)/);
  assert.match(gmChoice, /story_choice_hourglass_backrooms\.webp/);
  assert.match(gmChoice, /story-choice-loading\{[^}]*border:2px solid #d8b84a/);
  assert.match(gmChoice, /@keyframes story-choice-hourglass-pulse/);
});

test('selection bridges contain no provider request and native state hides outcome tables', () => {
  const java = fs.readFileSync(path.resolve(__dirname,
    '../app/src/main/java/com/rabpit/backroom/MainActivity.java'), 'utf8');
  const facade = fs.readFileSync(path.resolve(__dirname,
    '../app/src/main/java/com/rabpit/backroom/core/GameCoreFacade.java'), 'utf8');
  for (const method of ['resolveStoryDecision', 'resolveReturnJourneyChoice']) {
    const body = java.split(`@JavascriptInterface public void ${method}`)[1]
      .split('@JavascriptInterface')[0];
    assert.doesNotMatch(body, /geminiText|haikuText|prepareStoryDecision|prepareReturnJourneyTurn/);
  }
  const safe = facade.split('private JSONObject clientSafeState(')[1]
    .split('private void restoreHiddenDecisionPackage')[0];
  assert.match(safe, /story\.remove\("decisionContract"\)/);
  assert.match(safe, /pack\.remove\("outcomes"\)/);
  assert.match(safe, /turnPack\.remove\("outcomes"\)/);
  assert.match(safe, /"pausedStory".*"lookahead"/s);
});


test('semantic rerender captures the visible message before base render destroys the DOM', () => {
  const gmChoice = fs.readFileSync(path.join(assets, 'gm-choice-ui.js'), 'utf8');
  const wrapper = gmChoice.split('var previousRender = window.render;')[1].split('if (form) {')[0];
  assert.ok(wrapper.indexOf('var viewportAnchor = captureLogAnchor();') >= 0);
  assert.ok(wrapper.indexOf('var viewportAnchor = captureLogAnchor();') < wrapper.indexOf('previousRender'));
  assert.match(gmChoice, /function captureLogAnchor\(\)/);
  assert.match(gmChoice, /data-log-index/);
  assert.match(gmChoice, /function restoreLogAnchor\(anchor\)/);
  assert.doesNotMatch(
    gmChoice.split('function renderSemanticLog(anchor)')[1].split('function scrollLatestGmToStart')[0],
    /previousScrollTop/
  );
});

test('environment-only GM replies do not become the owner of Story choices', () => {
  const gmChoice = fs.readFileSync(path.join(assets, 'gm-choice-ui.js'), 'utf8');
  assert.match(gmChoice, /entry\.role !== 'player' && entry\.scope !== 'environment'/);
});
