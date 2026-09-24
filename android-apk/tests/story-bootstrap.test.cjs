const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const assert = require('node:assert/strict');
const assets = path.join(__dirname, '..', 'app', 'src', 'main', 'assets');
const gm = fs.readFileSync(path.join(assets, 'gm-choice-ui.js'), 'utf8');
const player = fs.readFileSync(path.join(assets, 'player-action-ui.js'), 'utf8');
const index = fs.readFileSync(path.join(assets, 'index.html'), 'utf8');
const functionSource = (source, name) => {
  const start = source.indexOf('  function ' + name + '(');
  assert.ok(start >= 0, name);
  const end = source.indexOf('\n  function ', start + 2);
  return source.slice(start, end < 0 ? undefined : end);
};
const bootstrapState = () => ({story: {active:true, arcComplete:false, segmentDelivered:false,
  awaitingDecision:false, awaitingEntityAttack:false, pendingStoryAdvance:false},
  combat:{active:false}, log:[{role:'gm', text:'CỐT TRUYỆN CHÍNH — LEVEL 0'}]});
function element(tag) {
  return {tag, children:[], listeners:{}, className:'', textContent:'', disabled:false,
    appendChild(node){this.children.push(node);},
    addEventListener(name, callback){this.listeners[name]=callback;}};
}
function context(state) {
  const submissions = [];
  const ctx = {state, window:{Android:{submitTurn:(s,a)=>submissions.push([JSON.parse(s),a])}},
    Android:null, document:{createElement:element}, status:null, submit:null,
    busy:false, log:null, storyDecisionNeedsPrefetch:()=>false,
    requestStoryDecisionPrefetch:()=>{}, storyCutawayActive:()=>false,
    storyAwaitingDecision:()=>false, storyAwaitingEntityAttack:()=>false,
    storyDecisionReady:()=>false, chestPresent:()=>false,
    storyPendingAdvance:()=>false, fallbackExplorerChoices:()=>[],
    makeChoiceButton:()=>element('button'), lastGmIndex:()=>state.log.length-1};
  ctx.Android = ctx.window.Android;
  vm.createContext(ctx);
  vm.runInContext([functionSource(gm,'storyBootstrapPending'),
    functionSource(gm,'submitStoryBootstrap'),
    functionSource(gm,'storyHandoffPending'),
    functionSource(gm,'submitStoryHandoff'),
    functionSource(gm,'appendExplorerChoices')].join('\n'), ctx);
  return {ctx,submissions};
}
test('fresh story has exactly one unbulleted CTA and sends only the local Core command', () => {
  const state=bootstrapState();
  const {ctx,submissions}=context(state);
  const article=element('article');
  ctx.appendExplorerChoices(article,state.log[0],0);
  assert.equal(article.children.length,1);
  const buttons=article.children[0].children;
  assert.equal(buttons.length,1);
  assert.equal(buttons[0].textContent,'Nhấn vào để bắt đầu khám phá thế giới Backrooms');
  assert.ok(!buttons[0].textContent.startsWith('• '));
  buttons[0].listeners.click();
  buttons[0].listeners.click();
  assert.equal(submissions.length,1);
  assert.equal(submissions[0][1],'tiếp tục cốt truyện');
  assert.doesNotMatch(index,/Nhấn “Tiếp tục cốt truyện” để bắt đầu Chương 01\./);
  assert.doesNotMatch(gm,/text:'Tiếp tục'|text:'Tiếp tục cốt truyện'/);
});
test('CTA disappears once Turn 1 is delivered and cannot bypass another story gate', () => {
  const state=bootstrapState();
  const {ctx}=context(state);
  for (const patch of [{segmentDelivered:true,awaitingDecision:true,decisionStatus:'PREFETCH_REQUIRED'},
    {awaitingEntityAttack:true}, {pendingStoryAdvance:true}, {arcComplete:true}]) {
    Object.assign(state.story,bootstrapState().story,patch);
    assert.equal(ctx.storyBootstrapPending(),false);
  }
  Object.assign(state.story,bootstrapState().story);
  state.combat.active=true;
  assert.equal(ctx.storyBootstrapPending(),false);
  assert.equal(vm.runInNewContext('('+functionSource(player,'storyBootstrapPending')+')()', {state}),false);
  state.combat.active=false;
  assert.equal(vm.runInNewContext('('+functionSource(player,'storyBootstrapPending')+')()', {state}),true);
  assert.match(player,/form\.addEventListener\('submit', function\(event\)\{[\s\S]*event\.stopImmediatePropagation\(\)/);
  assert.match(player,/openButton\.disabled = locked/);
  assert.match(player,/storyBootstrapPending\(\) \|\| storyCutawayActive\(\)/);
});

test('arc boundary exposes one handoff CTA instead of random explorer choices', () => {
  const state=bootstrapState();
  state.story.arcComplete=true;
  state.story.segmentDelivered=true;
  state.levelRoute={storyExitReady:true,exitAvailable:true};
  const {ctx,submissions}=context(state);
  const article=element('article');
  ctx.appendExplorerChoices(article,state.log[0],0);
  assert.equal(article.children.length,1);
  const buttons=article.children[0].children;
  assert.equal(buttons.length,1);
  assert.equal(buttons[0].textContent,'Tiếp tục qua ranh giới');
  buttons[0].listeners.click();
  assert.equal(submissions.length,1);
  assert.equal(submissions[0][1],'tiếp tục cốt truyện');
});

