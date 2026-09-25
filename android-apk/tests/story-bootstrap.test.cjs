const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');
const assert = require('node:assert/strict');

const assets = path.join(__dirname, '..', 'app', 'src', 'main', 'assets');
const gm = fs.readFileSync(path.join(assets, 'gm-choice-ui.js'), 'utf8');
const player = fs.readFileSync(path.join(assets, 'player-action-ui.js'), 'utf8');
const index = fs.readFileSync(path.join(assets, 'index.html'), 'utf8');
const facade = fs.readFileSync(path.join(__dirname, '..', 'app', 'src', 'main', 'java',
  'com', 'rabpit', 'backroom', 'core', 'GameCoreFacade.java'), 'utf8');
const activity = fs.readFileSync(path.join(__dirname, '..', 'app', 'src', 'main', 'java',
  'com', 'rabpit', 'backroom', 'MainActivity.java'), 'utf8');

const functionSource = (source, name) => {
  const start = source.indexOf('  function ' + name + '(');
  assert.ok(start >= 0, name);
  const end = source.indexOf('\n  function ', start + 2);
  return source.slice(start, end < 0 ? undefined : end);
};

const bootstrapState = () => ({
  story: {
    active:true, arcComplete:false, segmentDelivered:false,
    awaitingDecision:false, awaitingEntityAttack:false, pendingStoryAdvance:false,
    returnJourney:{active:false}
  },
  combat:{active:false},
  log:[{role:'gm', text:'CỐT TRUYỆN CHÍNH — LEVEL 0'}]
});

function element(tag) {
  return {
    tag, children:[], listeners:{}, attributes:{}, className:'', textContent:'', disabled:false,
    appendChild(node){this.children.push(node);},
    addEventListener(name, callback){this.listeners[name]=callback;},
    setAttribute(name, value){this.attributes[name]=String(value);}
  };
}

function context(state) {
  const submissions = [];
  const android = {
    submitTurn:(s,a)=>submissions.push(['submit',JSON.parse(s),a]),
    prepareStoryDecision:s=>submissions.push(['prepare-story',JSON.parse(s)]),
    prepareReturnJourneyTurn:s=>submissions.push(['prepare-return',JSON.parse(s)]),
    resolveReturnJourneyChoice:(s,id)=>submissions.push(['resolve-return',JSON.parse(s),id]),
    resolveStoryDecision:(s,id)=>submissions.push(['resolve-story',JSON.parse(s),id])
  };
  const ctx = {
    state,
    window:{Android:android,__combatBusy:false,__storyDecisionRequestKey:'',__returnJourneyRequestKey:'',render:()=>{}},
    Android:android,
    document:{createElement:element},
    status:{textContent:''},
    submit:{disabled:false},
    busy:false,
    log:null,
    setTimeout:fn=>fn(),
    storyCutawayActive:()=>false,
    storyAwaitingDecision:()=>!!(state.story&&state.story.awaitingDecision),
    storyAwaitingEntityAttack:()=>false,
    chestPresent:()=>false,
    storyPendingAdvance:()=>false,
    fallbackExplorerChoices:()=>[],
    makeChoiceButton:(prefix,text,entry,extra,disabled,selected,onClick)=>{
      const button=element('button');
      button.textContent=text;
      button.disabled=!!disabled;
      button.addEventListener('click',onClick);
      return button;
    },
    lastGmIndex:()=>state.log.length-1
  };
  vm.createContext(ctx);
  vm.runInContext([
    functionSource(gm,'storyReturnPending'),
    functionSource(gm,'returnJourneyNeedsProvider'),
    functionSource(gm,'returnJourneyReady'),
    functionSource(gm,'returnJourneyChoices'),
    functionSource(gm,'requestReturnJourneyTurn'),
    functionSource(gm,'submitReturnJourneyChoice'),
    functionSource(gm,'storyDecisionReady'),
    functionSource(gm,'storyDecisionChoices'),
    functionSource(gm,'storyDecisionNeedsProvider'),
    functionSource(gm,'requestStoryDecision'),
    functionSource(gm,'appendStoryChoiceLoadingOverlay'),
    functionSource(gm,'storyBootstrapPending'),
    functionSource(gm,'storyAdvanceAvailable'),
    functionSource(gm,'submitStoryBootstrap'),
    functionSource(gm,'storyHandoffPending'),
    functionSource(gm,'submitStoryHandoff'),
    functionSource(gm,'appendExplorerChoices')
  ].join('\n'), ctx);
  return {ctx,submissions};
}

test('fresh story has one bootstrap CTA before the first authored beat', () => {
  const state=bootstrapState();
  const {ctx,submissions}=context(state);
  const article=element('article');
  ctx.appendExplorerChoices(article,state.log[0],0);
  assert.equal(article.children.length,1);
  const buttons=article.children[0].children;
  assert.equal(buttons.length,1);
  assert.equal(buttons[0].textContent,'Nhấn vào để bắt đầu khám phá thế giới Backrooms');
  buttons[0].listeners.click();
  buttons[0].listeners.click();
  assert.equal(submissions.length,1);
  assert.deepEqual(submissions[0].slice(0,1),['submit']);
  assert.equal(submissions[0][2],'tiếp tục cốt truyện');
  assert.doesNotMatch(index,/Nhấn “Tiếp tục cốt truyện” để bắt đầu Chương 01\./);
});

test('a ready return journey renders exactly three current-turn choices', () => {
  const state=bootstrapState();
  state.story.segmentDelivered=true;
  state.story.awaitingDecision=true;
  state.story.returnJourney={
    active:true,journeyId:'j1',turnIndex:4,turnStatus:'READY',lookaheadReady:true,
    turnPackage:{choices:[
      {id:'opaque-1',text:'Đi theo dấu tường'},
      {id:'opaque-2',text:'Rẽ theo tiếng đèn'},
      {id:'opaque-3',text:'Đứng lại kiểm tra'}
    ]}
  };
  const {ctx,submissions}=context(state);
  const article=element('article');
  ctx.appendExplorerChoices(article,state.log[0],0);
  const buttons=article.children[0].children;
  assert.equal(buttons.length,3);
  assert.deepEqual(buttons.map(x=>x.textContent),
    ['Đi theo dấu tường','Rẽ theo tiếng đèn','Đứng lại kiểm tra']);
  buttons[1].listeners.click();
  buttons[1].listeners.click();
  assert.equal(submissions.length,1);
  assert.equal(submissions[0][0],'resolve-return');
  assert.equal(submissions[0][2],'opaque-2');
});

test('provider generation keeps current Story choices covered while loading', () => {
  const state=bootstrapState();
  Object.assign(state.story,{
    segmentDelivered:true,awaitingDecision:true,decisionStatus:'PROVIDER_REQUIRED',
    decisionId:'L0_current',
    decisionPackage:{contextHash:'story-hash',choices:[
      {id:'story-1',text:'Quan sát dấu hiệu phía trước'},
      {id:'story-2',text:'Kiểm tra khoảng lối bên cạnh'},
      {id:'story-3',text:'Xem kỹ khu vực gần nhất'}
    ]}
  });
  const {ctx,submissions}=context(state);
  const article=element('article');
  ctx.appendExplorerChoices(article,state.log[0],0);
  assert.equal(submissions.filter(x=>x[0]==='prepare-story').length,1);
  assert.equal(submissions.find(x=>x[0]==='prepare-story')[1].story.decisionId,'L0_current');
  const box=article.children[0];
  const buttons=box.children.filter(x=>x.tag==='button');
  assert.equal(buttons.length,3);
  assert.deepEqual(buttons.map(x=>x.textContent),[
    'Quan sát dấu hiệu phía trước',
    'Kiểm tra khoảng lối bên cạnh',
    'Xem kỹ khu vực gần nhất'
  ]);
  assert.ok(buttons.every(x=>x.disabled));
  const loading=box.children.find(x=>x.className==='story-choice-loading');
  assert.ok(loading);
  assert.equal(loading.attributes.role,'status');
  assert.equal(box.attributes['aria-busy'],'true');
  assert.equal(loading.children[0].children[0].textContent,'⌛');
  assert.equal(loading.children[0].children[1].textContent,'ĐANG TẢI LỰA CHỌN…');
});

test('provider generation is requested while unprepared return choices stay hidden', () => {
  const state=bootstrapState();
  state.story.segmentDelivered=true;
  state.story.returnJourney={
    active:true,journeyId:'journey-current',turnIndex:7,turnStatus:'PROVIDER_REQUIRED',
    turnPackage:{contextHash:'return-hash',choices:[
      {id:'return-1',text:'Theo dấu hiệu phía trước'},
      {id:'return-2',text:'Thử lối gần góc rẽ'},
      {id:'return-3',text:'Quan sát khu vực hiện tại'}
    ]}
  };
  const {ctx,submissions}=context(state);
  const article=element('article');
  ctx.appendExplorerChoices(article,state.log[0],0);
  assert.equal(submissions.filter(x=>x[0]==='prepare-return').length,1);
  const submitted=submissions.find(x=>x[0]==='prepare-return')[1];
  assert.equal(submitted.story.returnJourney.journeyId,'journey-current');
  assert.equal(submitted.story.returnJourney.turnIndex,7);
  const buttons=article.children[0].children;
  assert.equal(buttons.length,0);
  assert.equal(buttons.some(x=>x.textContent==='Đang chuẩn bị ba hướng đi…'),false);
});

test('a wrong Story choice returns before time and random encounter processing', () => {
  const start=facade.indexOf('if (resolution.looped) {');
  const end=facade.indexOf('grantStoryProgressCore(state, resolution);',start);
  assert.ok(start>=0 && end>start);
  const wrongBranch=facade.slice(start,end);
  assert.match(wrongBranch,/appendDecisionLog\(state, resolution\)/);
  assert.match(wrongBranch,/return response\(true, state, null, "story_decision_looped"/);
  assert.doesNotMatch(wrongBranch,/advanceGameTime|prepareEncounter|incrementTurn/);
});

test('old one-step return and speculative prefetch bridges are gone', () => {
  assert.doesNotMatch(gm,/Android\.resumeStoryReturn|Android\.prefetchStoryDecision/);
  assert.doesNotMatch(activity,/void resumeStoryReturn\(|void prefetchStoryDecision\(/);
  assert.match(activity,/void prepareStoryDecision\(String stateJson\)/);
  assert.match(activity,/void prepareReturnJourneyTurn\(String stateJson\)/);
  assert.match(activity,/rawOutput = geminiText\(prompt\)/);
  assert.match(activity,/rawOutput = haikuText\(prompt\)/);
  assert.doesNotMatch(gm,/text:'Tiếp tục'|action:'Tiếp tục cốt truyện'/);
});

test('bootstrap CTA cannot bypass Story, return, combat, or handoff gates', () => {
  const state=bootstrapState();
  const {ctx}=context(state);
  for (const patch of [
    {segmentDelivered:true,awaitingDecision:true,decisionStatus:'PROVIDER_REQUIRED'},
    {awaitingEntityAttack:true},
    {pendingStoryAdvance:true},
    {arcComplete:true}
  ]) {
    Object.assign(state.story,bootstrapState().story,patch);
    assert.equal(ctx.storyBootstrapPending(),false);
  }
  Object.assign(state.story,bootstrapState().story);
  state.story.returnJourney={active:true,turnStatus:'PROVIDER_REQUIRED'};
  assert.equal(ctx.storyBootstrapPending(),false);
  state.story.returnJourney={active:false};
  state.combat.active=true;
  assert.equal(ctx.storyBootstrapPending(),false);
  assert.equal(vm.runInNewContext('('+functionSource(player,'storyBootstrapPending')+')()', {state}),false);
  assert.match(player,/form\.addEventListener\('submit', function\(event\)\{[\s\S]*event\.stopImmediatePropagation\(\)/);
  assert.match(player,/openButton\.disabled = locked/);
});

test('arc boundary still exposes the handoff CTA', () => {
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
  assert.equal(submissions[0][0],'submit');
  assert.equal(submissions[0][2],'tiếp tục cốt truyện');
});

test('active return journey blocks arc handoff in both UI and Core dispatch order', () => {
  const state=bootstrapState();
  state.story.arcComplete=true;
  state.story.segmentDelivered=true;
  state.story.returnJourney={
    active:true,journeyId:'journey-handoff-guard',turnIndex:0,turnStatus:'PROVIDER_REQUIRED',
    turnPackage:{contextHash:'return-handoff-hash',choices:[
      {id:'guard-1',text:'Theo dấu hiệu phía trước'},
      {id:'guard-2',text:'Thử lối gần nhất'},
      {id:'guard-3',text:'Quan sát khu vực hiện tại'}
    ]}
  };
  state.levelRoute={storyExitReady:true,exitAvailable:true};

  const {ctx,submissions}=context(state);
  assert.equal(ctx.storyHandoffPending(),false);

  const article=element('article');
  ctx.appendExplorerChoices(article,state.log[0],0);
  assert.equal(article.children.length,1);
  assert.equal(article.children[0].className,'gm-choices story-return');
  assert.equal(article.children[0].children.length,0);
  assert.equal(submissions.some(x=>x[0]==='submit'),false);
  assert.equal(submissions.filter(x=>x[0]==='prepare-return').length,1);

  const returnGuard=facade.indexOf('if (storyCore.returnJourneyActive(legacy)) {');
  const handoff=facade.indexOf('if (storyArcComplete(legacy) && StoryCore.isAdvanceAction(text)) {');
  assert.ok(returnGuard>=0);
  assert.ok(handoff>returnGuard);
  const guardBlock=facade.slice(returnGuard,handoff);
  assert.match(guardBlock,/return_journey_choice_required/);
  assert.match(guardBlock,/return response\(true, legacy/);
});
