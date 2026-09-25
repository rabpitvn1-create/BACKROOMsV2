const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

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
  const calls = { prepare: [], resolve: [] };
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
  context.backroomError = () => { context.busy = false; };
  vm.runInNewContext(fs.readFileSync(path.join(assets, 'gm-choice-ui.js'), 'utf8'), context);
  return { context, log, style, calls };
}
for (const isReturn of [false, true]) {
  test(`${isReturn ? 'Return Journey' : 'Story'} renders three choices before provider and queues one request`, async () => {
    const { context, log, style, calls } = scenario(isReturn);
    const choiceButtons = buttons(log);
    assert.equal(choiceButtons.length, isReturn ? 0 : 3);
    assert.ok(choiceButtons.every(button => button.disabled));
    if (!isReturn) {
      assert.equal(choiceButtons[0].textContent.includes('Cao Minh'), true);
      const semantic = choiceButtons[0].querySelectorAll('.semantic');
      assert.equal(semantic.some(span => span.className.includes('semantic-character') && span.textContent === 'Cao Minh'), true);
    }
    assert.match(style.textContent, /Play-Regular\.ttf/);
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
    context.backroomError('provider failed again');
    await new Promise(resolve => setTimeout(resolve, 10));
    assert.equal(calls.prepare.length, 2);
    const manual = buttons(log).find(button => button.textContent === 'Thử lại phản hồi');
    assert.ok(manual);
    assert.equal(buttons(log).length, isReturn ? 1 : 4);
    manual.click();
    assert.equal(calls.prepare.length, 3);
  });
}

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
