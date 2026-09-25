(function(){
  'use strict';
  if (window.__gmChoiceUiInstalled) return;
  window.__gmChoiceUiInstalled = true;

  var style = document.createElement('style');
  style.textContent = [
    "@font-face{font-family:'Play';font-style:normal;font-weight:400;src:url('file:///android_asset/fonts/Play-Regular.ttf') format('truetype');font-display:swap}",
    "@font-face{font-family:'Play';font-style:normal;font-weight:700;src:url('file:///android_asset/fonts/Play-Bold.ttf') format('truetype');font-display:swap}",
    ".message.gm .role{font-family:system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;font-weight:700}",
    ".combat-turn-label{font-family:'Play',system-ui,sans-serif;font-weight:700}",
    ".semantic{font-family:'Play',system-ui,sans-serif;font-weight:700;text-decoration:none}",
    ".semantic-character{color:#67d5ff}",
    ".semantic-entity{color:#ff6b6b}",
    ".semantic-item{color:#f6c85f}",
    ".semantic-skill{color:#c792ea}",
    ".semantic-effect{color:#ff9f43}",
    ".semantic-location{color:#7bd88f}",
    ".semantic-stat{color:#ffd166}",
    ".semantic-damage{color:#ff5c5c}",
    ".semantic-buff{color:#73e6a2}",
    ".message.gm .semantic-effect,.message.gm .semantic-damage,.message.gm .semantic-buff{font-family:inherit}",
    ".semantic-generic{color:#e5e9ed}",
    ".gm-choice:disabled .semantic{opacity:.72}",
    ".gm-main-text{white-space:pre-wrap;line-height:1.55}",
    ".battle-log{display:grid;gap:5px;margin-top:10px}",
    ".battle-line{white-space:pre-wrap;line-height:1.45}",
    ".gm-choices{display:grid;gap:7px;margin-top:12px}",
    ".combat-turn-label{font-size:12px;letter-spacing:.08em;color:#b5bec6;margin:2px 0 1px}",
    ".gm-choice{width:100%;text-align:left;padding:11px 12px;background:#171d22;border:1px solid #39424a;color:#f0f3f5;font-family:'Play',system-ui,sans-serif;font-weight:400;letter-spacing:normal;text-transform:none;white-space:normal;line-height:1.4;border-radius:8px}",
    ".story-decision .gm-choice,.story-return .gm-choice{border-color:#756d43;background-color:#171812;background-image:linear-gradient(90deg,rgba(8,9,7,.90),rgba(12,12,8,.60) 50%,rgba(8,9,7,.88)),url('hud/player_action_backrooms.png');background-size:cover;background-position:center,center 52%;color:#fff7cf;text-shadow:0 1px 2px #000,0 0 9px #000;box-shadow:inset 0 0 0 1px rgba(220,200,102,.08),inset 0 0 22px rgba(162,145,62,.10),0 5px 18px #0008}",
    ".gm-choice:disabled{opacity:.62}",
    ".gm-choice.selected{border-color:#7a858e;background:#20272d}",
    ".combat-skill-description{display:block;margin-top:5px;color:#9fa8af;font-size:11px;font-weight:400;line-height:1.4}",
    ".composer.battle-locked textarea{background:#101316;color:#697178;border-color:#262d33}",
    ".composer.battle-locked #submit{background:#24282c;color:#777e84;border-color:#30353a;opacity:.7}",
    ".message.gm{border-left-color:#59646d;border-radius:0}",
    ".battle-separator{height:1px;background:#262d33;margin-top:10px}",
    ".combat-dice-panel[hidden]{display:none}.combat-dice-panel{width:100%;box-sizing:border-box;margin-top:12px;background:#0e1114;border:1px solid #46515a;padding:14px;display:grid;gap:12px;touch-action:manipulation;border-radius:10px}",
    ".combat-dice-title{font-family:'Play',system-ui,sans-serif;font-size:14px;font-weight:700;letter-spacing:.06em}.combat-dice-meta{font-size:11px;color:#9ba6af}",
    ".combat-dice-row{display:grid;grid-template-columns:repeat(5,1fr);gap:5px;perspective:720px}.combat-die{padding:1px;aspect-ratio:1/1;border:1px solid #343d45;background:#151a1f;display:grid;place-items:center;min-width:0;border-radius:9px;transform-style:preserve-3d;will-change:transform}.combat-die img{width:104%;height:104%;object-fit:contain;pointer-events:none}.combat-die.held{border-color:#f6c85f;background:#211e14;box-shadow:inset 0 0 0 1px #f6c85f55}.combat-die.rolling{border-color:#72808b;box-shadow:0 0 12px #91a1ad33;animation:combat-die-roll .46s cubic-bezier(.25,.7,.35,1) infinite}.combat-die:disabled{opacity:.85}@keyframes combat-die-roll{0%{transform:rotateX(0deg) rotateY(0deg) rotateZ(0deg) scale(.94)}25%{transform:rotateX(120deg) rotateY(70deg) rotateZ(45deg) scale(1.04)}50%{transform:rotateX(230deg) rotateY(160deg) rotateZ(120deg) scale(.96)}75%{transform:rotateX(320deg) rotateY(260deg) rotateZ(220deg) scale(1.04)}100%{transform:rotateX(360deg) rotateY(360deg) rotateZ(360deg) scale(.94)}}@media(prefers-reduced-motion:reduce){.combat-die.rolling{animation:none}}",
    ".combat-dice-result{min-height:22px;text-align:center;font-family:'Play',system-ui,sans-serif;font-size:16px;font-weight:700;color:#f6c85f}",
    ".combat-dice-actions{display:grid;grid-template-columns:1fr 1fr;gap:8px}.combat-roll,.combat-finish{width:100%;padding:12px 8px;background:#1b2126;border:1px solid #46515a;color:#f0f3f5;font-family:'Play',system-ui,sans-serif;font-weight:700;letter-spacing:.12em;border-radius:8px}.combat-roll:disabled,.combat-finish:disabled{opacity:.45}"
  ].join('');
  document.head.appendChild(style);

  var log = document.getElementById('log');
  var form = document.getElementById('form');
  var action = document.getElementById('action');
  var submit = document.getElementById('submit');
  var status = document.getElementById('status');
  var defaultPlaceholder = action ? action.getAttribute('placeholder') : '';
  window.__combatBusy = false;
  window.__combatAnimationToken = 0;
  var COMBAT_PHASE_MS = 1000;

  var semanticPriority = {generic:0,location:1,item:2,effect:3,skill:4,character:5,entity:6};
  var knownSkills = [
    'Huyết Ma Tứ Liên','Ma Tâm Trấn Hồn','Huyết Ảnh Ma Độn','Thiên Ma Bộ','Huyết Ma Nhị Thập Tứ Trảm',
    'Twosome Time','Rain Storm','Honeycomb Fire','Charged Shot',
    'Rift Sever','Crimson Guillotine','Lucifer Breaker','Spatial Dominion',
    'Tịch Quang Hợp Kích','Tịch Quang Phản Kiếm','Nhất Tuyến Phá Vọng','Thiên Kiếm Chấn','Bạch Hồng Quán Nhật','Vạn Kiếm Quy Tâm','Thiên Kiếm Định Giới'
  ];
  var knownEffects = ['Choáng','Chảy máu','Trúng độc','Xuyên giáp','Phá giáp','Né tránh','Mất phương hướng'];
  var knownHandTokens = ['[NO HAND]','[PAIR]','[TWO PAIR]','[TRIPLE]','[STRAIGHT]','[FULL HOUSE]','[F.O.A.K]','[SSF]','[FSF]'];

  function normalizeSemanticType(value) {
    var type = String(value || '').trim().toLowerCase();
    if (type === 'npc' || type === 'ally' || type === 'player') type = 'character';
    if (type === 'enemy' || type === 'monster' || type === 'quái vật') type = 'entity';
    if (type === 'level' || type === 'area' || type === 'zone' || type === 'place') type = 'location';
    if (type === 'status' || type === 'buff' || type === 'debuff') type = 'effect';
    return semanticPriority.hasOwnProperty(type) ? type : 'generic';
  }

  function addTerm(map, value, type) {
    if (value === null || value === undefined) return;
    var text = String(value).trim();
    if (text.length < 2) return;
    var normalizedType = normalizeSemanticType(type);
    var key = text.toLocaleLowerCase('vi');
    var previous = map.get(key);
    if (!previous || semanticPriority[normalizedType] > semanticPriority[previous.type]) {
      map.set(key, {text:text,type:normalizedType});
    }
  }

  function addHighlightList(map, list) {
    (Array.isArray(list) ? list : []).forEach(function(x){
      if (typeof x === 'string') addTerm(map, x, 'generic');
      else if (x) addTerm(map, x.text || x.name, x.type || x.kind || 'generic');
    });
  }

  function entryHighlights(entry) {
    var map = new Map();
    ['Cao Minh','Vạn Giới Ma Tôn','Iris','Syvial','Lục Trầm'].forEach(function(x){ addTerm(map,x,'character'); });
    knownEffects.forEach(function(x){ addTerm(map,x,'effect'); });
    knownSkills.forEach(function(x){ addTerm(map,x,'skill'); });
    knownHandTokens.forEach(function(x){ addTerm(map,x,'stat'); });

    try {
      if (state && state.player) addTerm(map, state.player.name, 'character');
      if (state && state.location) {
        var locationHead = String(state.location).split('—')[0];
        locationHead.split('/').forEach(function(x){ addTerm(map, x, 'location'); });
      }
      if (state && Array.isArray(state.party)) state.party.forEach(function(x){ addTerm(map, typeof x === 'string' ? x : x && (x.name || x.id), 'character'); });
      if (state && Array.isArray(state.inventory)) state.inventory.forEach(function(x){ addTerm(map, typeof x === 'string' ? x : x && x.name, 'item'); });
      if (state && state.combat) {
        addTerm(map, state.combat.currentActor, 'character');
        if (state.combat.entity) addTerm(map, state.combat.entity.name, 'entity');
        if (state.combat.currentSkill) addTerm(map, state.combat.currentSkill.name, 'skill');
      }
    } catch (_) {}

    addHighlightList(map, entry && entry.highlights);
    return Array.from(map.values()).sort(function(a,b){ return b.text.length-a.text.length; });
  }

  function escapeRegex(value) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  function appendRichText(container, text, entry, extraHighlights) {
    var source = text === null || text === undefined ? '' : String(text);
    var baseTerms = entryHighlights(entry);
    var termMap = new Map();
    baseTerms.forEach(function(x){ addTerm(termMap, x.text, x.type); });
    addHighlightList(termMap, extraHighlights || []);
    var terms = Array.from(termMap.values()).sort(function(a,b){ return b.text.length-a.text.length; });
    var lookup = new Map();
    terms.forEach(function(x){ lookup.set(x.text.toLocaleLowerCase('vi'), x.type); });

    var patterns = terms.map(function(x){ return escapeRegex(x.text); });
    patterns.push('[+-]\\d+(?:\\.\\d+)?%?\\s*(?:HP|DEF)');
    patterns.push('(?:HP\\s*\\d+\\s*\\/\\s*\\d+|\\d+\\s*\\/\\s*\\d+\\s*HP)');
    patterns.push('Level\\s+\\d+(?:\\s*[-–—/]\\s*[A-Za-zÀ-ỹ0-9 _]+)?');
    var re = new RegExp('(' + patterns.join('|') + ')', 'gi');
    var cursor = 0;
    var match;
    while ((match = re.exec(source)) !== null) {
      if (match.index > cursor) container.appendChild(document.createTextNode(source.slice(cursor, match.index)));
      var matched = match[0];
      var type = lookup.get(matched.toLocaleLowerCase('vi')) || '';
      if (!type) {
        if (/^-/.test(matched) && /(?:HP|DEF)$/i.test(matched)) type = 'damage';
        else if (/^\+/.test(matched) && /(?:HP|DEF)$/i.test(matched)) type = 'buff';
        else if (/^(?:HP\s*\d+\s*\/\s*\d+|\d+\s*\/\s*\d+\s*HP)$/i.test(matched)) type = 'stat';
        else if (/^Level\s+\d+/i.test(matched)) type = 'location';
        else type = 'generic';
      }
      var span = document.createElement('span');
      span.className = 'semantic semantic-' + type;
      span.textContent = matched;
      container.appendChild(span);
      cursor = match.index + matched.length;
      if (matched.length === 0) re.lastIndex++;
    }
    if (cursor < source.length) container.appendChild(document.createTextNode(source.slice(cursor)));
  }

  function lastGmIndex() {
    if (!state || !Array.isArray(state.log)) return -1;
    for (var i = state.log.length - 1; i >= 0; i--) {
      var entry = state.log[i];
      if (entry && entry.role !== 'player') return i;
    }
    return -1;
  }

  function submitExplorerChoice(entry, choice) {
    if (!choice || window.__combatBusy || (state.combat && state.combat.active)) return;
    var text = String(choice.action || choice.text || '').trim();
    if (!text || !form || !action) return;
    if (Array.isArray(entry.choices)) entry.choices.forEach(function(x){ x.disabled = true; });
    choice.selected = true;
    try { localStorage.setItem('backroom-apk-state', JSON.stringify(state)); } catch (_) {}
    if (typeof window.render === 'function') window.render();
    action.value = text;
    if (typeof form.requestSubmit === 'function') form.requestSubmit();
    else form.dispatchEvent(new Event('submit', {bubbles:true,cancelable:true}));
  }

  function submitStoryDecisionChoice(choice) {
    if (!choice || !choice.id || !storyDecisionReady() || state.story.pendingChoiceId
        || window.__selectedStoryChoiceKey === (state.story.decisionPackage.choices[0] || {}).id
        || window.__combatBusy || (state.combat && state.combat.active)) return;
    if (!window.Android || typeof Android.resolveStoryDecision !== 'function') {
      if (status) status.textContent = 'Không tìm thấy Android story bridge.';
      return;
    }
    window.__combatBusy = true;
    window.__selectedStoryChoiceKey = (state.story.decisionPackage.choices[0] || {}).id;
    if (typeof busy !== 'undefined') busy = true;
    if (submit) submit.disabled = true;
    try { localStorage.setItem('backroom-apk-state', JSON.stringify(state)); } catch (_) {}
    if (typeof window.render === 'function') window.render();
    Android.resolveStoryDecision(JSON.stringify(state), String(choice.id));
  }

  function submitStoryEntityAttack() {
    if (!storyAwaitingEntityAttack() || window.__combatBusy
        || (state.combat && state.combat.active)) return;
    if (!window.Android || typeof Android.attackStoryEntity !== 'function') {
      if (status) status.textContent = 'Không tìm thấy Android story combat bridge.';
      return;
    }
    window.__combatBusy = true;
    if (typeof busy !== 'undefined') busy = true;
    if (submit) submit.disabled = true;
    if (typeof window.render === 'function') window.render();
    Android.attackStoryEntity(JSON.stringify(state));
  }

  function chestPresent() {
    try { return !!(state && state.flags && state.flags.chestPresent === true); } catch (_) { return false; }
  }

  function storyAwaitingDecision() {
    try {
      return !!(state && state.story && state.story.active === true
        && state.story.arcComplete !== true
        && state.story.awaitingDecision === true);
    } catch (_) { return false; }
  }

  function storyReturnPending() {
    try {
      var journey = state && state.story && state.story.returnJourney;
      return !!(journey && journey.active === true);
    } catch (_) { return false; }
  }

  function returnJourneyNeedsProvider() {
    try {
      var journey = state && state.story && state.story.returnJourney;
      return storyReturnPending() && (String(journey.turnStatus || '') === 'PROVIDER_REQUIRED'
        || (String(journey.turnStatus || '') === 'READY' && journey.lookaheadReady !== true));
    } catch (_) { return false; }
  }

  function returnJourneyReady() {
    try {
      var journey = state && state.story && state.story.returnJourney;
      var pack = journey && journey.turnPackage;
      return storyReturnPending()
        && String(journey.turnStatus || '') === 'READY' && journey.lookaheadReady === true
        && pack && Array.isArray(pack.choices) && pack.choices.length === 3;
    } catch (_) { return false; }
  }

  function returnJourneyChoices() {
    try {
      var pack = state.story.returnJourney.turnPackage;
      return storyReturnPending() && pack && Array.isArray(pack.choices) ? pack.choices : [];
    } catch (_) { return []; }
  }

  function requestReturnJourneyTurn(force) {
    if (!returnJourneyNeedsProvider() || window.__combatBusy) return;
    var journey = state.story.returnJourney || {};
    var key = String(journey.journeyId || '') + ':' + String(journey.turnIndex || 0);
    if (!force && window.__returnJourneyRequestKey === key) return;
    if (!window.Android || typeof Android.prepareReturnJourneyTurn !== 'function') {
      if (status) status.textContent = 'Không tìm thấy Android return journey bridge.';
      return;
    }
    window.__returnJourneyRequestKey = key;
    Android.prepareReturnJourneyTurn(JSON.stringify(state));
  }

  function submitReturnJourneyChoice(choice) {
    if (!choice || !choice.id || !returnJourneyReady() || state.story.returnJourney.pendingChoiceId
        || window.__selectedReturnChoiceKey === String(state.story.returnJourney.journeyId) + ':' + String(state.story.returnJourney.turnIndex)
        || window.__combatBusy
        || (typeof busy !== 'undefined' && busy)) return;
    if (!window.Android || typeof Android.resolveReturnJourneyChoice !== 'function') {
      if (status) status.textContent = 'Không tìm thấy Android return journey bridge.';
      return;
    }
    window.__combatBusy = true;
    window.__selectedReturnChoiceKey = String(state.story.returnJourney.journeyId) + ':' + String(state.story.returnJourney.turnIndex);
    if (typeof busy !== 'undefined') busy = true;
    if (submit) submit.disabled = true;
    Android.resolveReturnJourneyChoice(JSON.stringify(state), String(choice.id));
  }

  function resumePendingChoice(isReturn) {
    var journey = isReturn && state.story.returnJourney;
    var id = String(isReturn ? journey.pendingChoiceId || '' : state.story.pendingChoiceId || '');
    var ready = isReturn ? journey.turnStatus === 'READY' : state.story.decisionStatus === 'READY';
    if (!id || !ready || window.__combatBusy || !window.Android) return;
    var key = (isReturn ? String(journey.journeyId) + ':' + String(journey.turnIndex) :
      String((state.story.decisionPackage.choices[0] || {}).id)) + ':' + id;
    if (window.__pendingResolutionRequestKey === key) return;
    window.__pendingResolutionRequestKey = key;
    window.__combatBusy = true;
    if (isReturn) Android.resolveReturnJourneyChoice(JSON.stringify(state), id);
    else Android.resolveStoryDecision(JSON.stringify(state), id);
  }

  function storyAwaitingEntityAttack() {
    try {
      return !!(state && state.story && state.story.active === true
        && state.story.arcComplete !== true
        && state.story.awaitingEntityAttack === true);
    } catch (_) { return false; }
  }

  function storyPendingAdvance() {
    try {
      return !!(state && state.story && state.story.pendingStoryAdvance === true);
    } catch (_) { return false; }
  }

  function storyBootstrapPending() {
    try {
      var story = state && state.story;
      return !!(story && story.active === true && story.arcComplete !== true
        && !(story.returnJourney && story.returnJourney.active === true)
        && story.segmentDelivered !== true && story.awaitingDecision !== true
        && story.awaitingEntityAttack !== true && story.pendingStoryAdvance !== true
        && !(state.combat && state.combat.active));
    } catch (_) { return false; }
  }

  function submitStoryBootstrap() {
    if ((!storyBootstrapPending() && !storyAdvanceAvailable())
        || window.__combatBusy || (typeof busy !== 'undefined' && busy)) return;
    if (!window.Android || typeof Android.submitTurn !== 'function') {
      if (status) status.textContent = 'Không tìm thấy Android bridge.';
      return;
    }
    window.__combatBusy = true;
    if (typeof busy !== 'undefined') busy = true;
    if (submit) submit.disabled = true;
    if (typeof window.render === 'function') window.render();
    Android.submitTurn(JSON.stringify(state), 'tiếp tục cốt truyện');
  }

  function storyHandoffPending() {
    try {
      var story = state && state.story;
      var route = state && state.levelRoute;
      return !!(story && story.active === true && story.arcComplete === true
        && !storyReturnPending()
        && route && route.storyExitReady === true
        && story.awaitingDecision !== true && story.awaitingEntityAttack !== true
        && story.pendingStoryAdvance !== true
        && !(state.combat && state.combat.active));
    } catch (_) { return false; }
  }

  function submitStoryHandoff() {
    if (!storyHandoffPending() || window.__combatBusy || (typeof busy !== 'undefined' && busy)) return;
    if (!window.Android || typeof Android.submitTurn !== 'function') {
      if (status) status.textContent = 'Không tìm thấy Android bridge.';
      return;
    }
    window.__combatBusy = true;
    if (typeof busy !== 'undefined') busy = true;
    if (submit) submit.disabled = true;
    if (typeof window.render === 'function') window.render();
    Android.submitTurn(JSON.stringify(state), 'tiếp tục cốt truyện');
  }

  function storyEntityAttackChoice() {
    if (!storyAwaitingEntityAttack()) return null;
    var gate = state.story && state.story.entityGate ? state.story.entityGate : {};
    return {id:'story_attack',text:String(gate.attackText || 'Tấn công')};
  }

  function storyDecisionReady() {
    try {
      var pack = state && state.story && state.story.decisionPackage;
      return storyAwaitingDecision() && !storyReturnPending()
        && String(state.story.decisionStatus || '') === 'READY'
        && pack && Array.isArray(pack.choices) && pack.choices.length === 3;
    } catch (_) { return false; }
  }

  function storyDecisionChoices() {
    try {
      var pack = state.story.decisionPackage;
      return storyAwaitingDecision() && pack && Array.isArray(pack.choices) ? pack.choices : [];
    } catch (_) { return []; }
  }

  function storyDecisionNeedsProvider() {
    try {
      return storyAwaitingDecision() && !storyReturnPending()
        && String(state.story.decisionStatus || '') === 'PROVIDER_REQUIRED';
    } catch (_) { return false; }
  }

  function requestStoryDecision(force) {
    if (!storyDecisionNeedsProvider() || window.__combatBusy) return;
    var key = String((state.story.decisionPackage.choices[0] || {}).id || '');
    if (!force && window.__storyDecisionRequestKey === key) return;
    if (!window.Android || typeof Android.prepareStoryDecision !== 'function') {
      if (status) status.textContent = 'Không tìm thấy Android Story choice bridge.';
      return;
    }
    window.__storyDecisionRequestKey = key;
    Android.prepareStoryDecision(JSON.stringify(state));
  }

  function allowSingleAutomaticProviderRetry() {
    if (storyDecisionNeedsProvider()) {
      var decisionKey = String((state.story.decisionPackage.choices[0] || {}).id || '');
      if (decisionKey && window.__storyDecisionRetryKey !== decisionKey) {
        window.__storyDecisionRetryKey = decisionKey;
        window.__storyDecisionRequestKey = '';
      } else if (decisionKey) {
        window.__storyProviderFailedKey = decisionKey;
      }
    }
    if (returnJourneyNeedsProvider()) {
      var journey = state.story.returnJourney || {};
      var returnKey = String(journey.journeyId || '') + ':' + String(journey.turnIndex || 0);
      if (returnKey && window.__returnJourneyRetryKey !== returnKey) {
        window.__returnJourneyRetryKey = returnKey;
        window.__returnJourneyRequestKey = '';
      } else if (returnKey) {
        window.__returnProviderFailedKey = returnKey;
      }
    }
  }

  function storyAdvanceAvailable() {
    var story = state && state.story;
    return !!(story && story.active === true && story.arcComplete !== true
      && !storyReturnPending()
      && story.segmentDelivered === true && story.awaitingDecision !== true
      && story.awaitingEntityAttack !== true && story.pendingStoryAdvance !== true
      && !(state.combat && state.combat.active));
  }

  function storyCutawayActive() {
    try {
      return !!(state && state.story && state.story.active === true
        && state.story.arcComplete !== true
        && String(state.story.visibility || '') === 'cutaway');
    } catch (_) { return false; }
  }

  function fallbackExplorerChoices() {
    var level = Number.isInteger(state && state.currentLevel) ? state.currentLevel : 0;
    var levelText = 'Level ' + level;
    return [
      {text:'Quan sát kỹ khu vực xung quanh',action:'Quan sát kỹ khu vực xung quanh'},
      {text:'Kiểm tra các lối đi hoặc điểm bất thường gần nhất',action:'Kiểm tra các lối đi hoặc điểm bất thường gần nhất'},
      {text:'Tiếp tục khám phá ' + levelText,action:'Tiếp tục khám phá ' + levelText,highlights:[{text:levelText,type:'location'}]}
    ];
  }

  function submitChestChoice() {
    if (!chestPresent() || window.__combatBusy || (state.combat && state.combat.active)) return;
    if (!window.Android || typeof Android.submitTurn !== 'function') {
      if (status) status.textContent = 'Không tìm thấy Android bridge.';
      return;
    }
    window.__combatBusy = true;
    if (typeof busy !== 'undefined') busy = true;
    if (submit) submit.disabled = true;
    if (status) status.textContent = 'Đang mở Rương…';
    if (typeof window.render === 'function') window.render();
    Android.submitTurn(JSON.stringify(state), '__loot:open_chest');
  }


  function makeChoiceButton(prefix, text, entry, extra, disabled, selected, onClick) {
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'gm-choice' + (selected ? ' selected' : '');
    button.disabled = !!disabled;
    button.appendChild(document.createTextNode('• '));
    appendRichText(button, text, entry, extra);
    button.addEventListener('click', onClick);
    return button;
  }

  function appendBattleSection(article, entry, index) {
    if (Array.isArray(entry.battleLog) && entry.battleLog.length) {
      var separator = document.createElement('div');
      separator.className = 'battle-separator';
      article.appendChild(separator);
      var battleLog = document.createElement('div');
      battleLog.className = 'battle-log';
      entry.battleLog.forEach(function(line){
        var row = document.createElement('div');
        row.className = 'battle-line';
        if (typeof line === 'string') appendRichText(row, line, entry, []);
        else appendRichText(row, line && line.text, entry, line && line.highlights);
        battleLog.appendChild(row);
      });
      article.appendChild(battleLog);
    }
  }

  function appendExplorerChoices(article, entry, index) {
    if (!entry) return;
    if (state.combat && state.combat.active) return;
    var latest = index === lastGmIndex();
    if (latest && storyBootstrapPending()) {
      var bootstrapBox = document.createElement('div');
      bootstrapBox.className = 'gm-choices story-bootstrap';
      var bootstrapButton = document.createElement('button');
      bootstrapButton.type = 'button';
      bootstrapButton.className = 'gm-choice';
      bootstrapButton.textContent = 'Nhấn vào để bắt đầu khám phá thế giới Backrooms';
      bootstrapButton.disabled = !!window.__combatBusy || (typeof busy !== 'undefined' && busy);
      bootstrapButton.addEventListener('click', submitStoryBootstrap);
      bootstrapBox.appendChild(bootstrapButton);
      article.appendChild(bootstrapBox);
      return;
    }
    if (latest && storyHandoffPending()) {
      var handoffBox = document.createElement('div');
      handoffBox.className = 'gm-choices story-handoff';
      var handoffButton = document.createElement('button');
      handoffButton.type = 'button';
      handoffButton.className = 'gm-choice';
      handoffButton.textContent = 'Tiếp tục qua ranh giới';
      handoffButton.disabled = !!window.__combatBusy || (typeof busy !== 'undefined' && busy);
      handoffButton.addEventListener('click', submitStoryHandoff);
      handoffBox.appendChild(handoffButton);
      article.appendChild(handoffBox);
      return;
    }
    if (latest && storyAdvanceAvailable()) {
      var advanceBox = document.createElement('div');
      advanceBox.className = 'gm-choices story-advance';
      var advanceButton = document.createElement('button');
      advanceButton.type = 'button';
      advanceButton.className = 'gm-choice';
      advanceButton.textContent = 'Tiếp tục cốt truyện';
      advanceButton.disabled = !!window.__combatBusy || (typeof busy !== 'undefined' && busy);
      advanceButton.addEventListener('click', submitStoryBootstrap);
      advanceBox.appendChild(advanceButton);
      article.appendChild(advanceBox);
      return;
    }
    if (latest && storyReturnPending()) {
      var returnBox = document.createElement('div');
      returnBox.className = 'gm-choices story-return';
      if (returnJourneyReady()) {
        if (state.story.returnJourney.pendingChoiceId && state.story.returnJourney.turnStatus === 'READY')
          setTimeout(function(){ resumePendingChoice(true); }, 0);
        returnJourneyChoices().forEach(function(choice){
          returnBox.appendChild(makeChoiceButton('', choice.text || '', entry, choice.highlights || [],
            !returnJourneyReady() || !!window.__combatBusy || !!state.story.returnJourney.pendingChoiceId
              || window.__selectedReturnChoiceKey === String(state.story.returnJourney.journeyId) + ':' + String(state.story.returnJourney.turnIndex), false,
            function(){ submitReturnJourneyChoice(choice); }));
        });
        if (state.story.returnJourney.pendingChoiceId && state.story.returnJourney.turnStatus === 'READY'
            && window.__pendingResolutionFailedKey === window.__pendingResolutionRequestKey) {
          var resumeReturn = document.createElement('button');
          resumeReturn.type = 'button';
          resumeReturn.className = 'gm-choice';
          resumeReturn.textContent = 'Tiếp tục lựa chọn đã lưu';
          resumeReturn.addEventListener('click', function(){ window.__pendingResolutionRequestKey = ''; resumePendingChoice(true); });
          returnBox.appendChild(resumeReturn);
        }
      }
      if (returnJourneyNeedsProvider()) setTimeout(function(){ requestReturnJourneyTurn(false); }, 0);
      article.appendChild(returnBox);
      if (returnJourneyNeedsProvider() && window.__returnProviderFailedKey === String(state.story.returnJourney.journeyId) + ':' + String(state.story.returnJourney.turnIndex)) {
        var retryReturn = document.createElement('button');
        retryReturn.type = 'button';
        retryReturn.textContent = 'Thử lại phản hồi';
        retryReturn.addEventListener('click', function(){ window.__returnProviderFailedKey = ''; requestReturnJourneyTurn(true); });
        var returnRecovery = document.createElement('div');
        returnRecovery.className = 'story-provider-recovery';
        returnRecovery.appendChild(retryReturn);
        article.appendChild(returnRecovery);
      }
      return;
    }
    var cutaway = latest && storyCutawayActive();
    var awaitingDecision = latest && storyAwaitingDecision();
    var awaitingEntity = latest && storyAwaitingEntityAttack();
    var decisionReady = awaitingDecision && storyDecisionReady();
    if (latest && awaitingDecision && storyDecisionNeedsProvider())
      setTimeout(function(){ requestStoryDecision(false); }, 0);
    if (latest && awaitingDecision && decisionReady && state.story.pendingChoiceId
        && state.story.decisionStatus === 'READY')
      setTimeout(function(){ resumePendingChoice(false); }, 0);
    var hasChest = latest && chestPresent() && !cutaway && !awaitingDecision && !awaitingEntity;

    var choices = [];
    if (awaitingDecision) {
      choices = storyDecisionChoices();
    } else if (awaitingEntity) {
      var attack = storyEntityAttackChoice();
      if (attack) choices = [attack];
    } else if (!cutaway && !storyPendingAdvance() && Array.isArray(entry.choices)) {
      choices = entry.choices;
    }

    if (latest && !choices.length && !cutaway && !awaitingDecision && !awaitingEntity
        && !storyPendingAdvance() && !(state.combat && state.combat.active)
        && !(state.story && state.story.active === true && state.story.arcComplete !== true)) {
      choices = fallbackExplorerChoices();
    }
    if (!hasChest && !choices.length) return;

    var actionable = latest && !(state.combat && state.combat.active) && !window.__combatBusy;
    var box = document.createElement('div');
    box.className = 'gm-choices explorer-choices' + (awaitingDecision ? ' story-decision' : '');

    if (hasChest) {
      box.appendChild(makeChoiceButton('', 'Mở Rương', entry, [{text:'Rương',type:'item'}], !actionable, false,
        function(){ submitChestChoice(); }));
    }

    choices.slice(0, 3).forEach(function(choice){
      var disabled = !actionable || (awaitingDecision && !decisionReady)
        || !!choice.disabled || !!choice.selected
        || (awaitingDecision && (!!state.story.pendingChoiceId
          || window.__selectedStoryChoiceKey === (state.story.decisionPackage.choices[0] || {}).id));
      var onClick = awaitingEntity
        ? function(){ submitStoryEntityAttack(); }
        : (decisionReady
            ? function(){ submitStoryDecisionChoice(choice); }
            : function(){ submitExplorerChoice(entry, choice); });
      box.appendChild(makeChoiceButton('', choice.text || choice.action || '', entry, choice.highlights || [],
        disabled, !!choice.selected, onClick));
    });
    if (awaitingDecision && state.story.pendingChoiceId && state.story.decisionStatus === 'READY'
        && window.__pendingResolutionFailedKey === window.__pendingResolutionRequestKey) {
      var resumeStory = document.createElement('button');
      resumeStory.type = 'button';
      resumeStory.className = 'gm-choice';
      resumeStory.textContent = 'Tiếp tục lựa chọn đã lưu';
      resumeStory.addEventListener('click', function(){ window.__pendingResolutionRequestKey = ''; resumePendingChoice(false); });
      box.appendChild(resumeStory);
    }
    if (awaitingDecision && storyDecisionNeedsProvider()
        && window.__storyProviderFailedKey === (state.story.decisionPackage.choices[0] || {}).id) {
      var retryStory = document.createElement('button');
      retryStory.type = 'button';
      retryStory.className = 'gm-choice';
      retryStory.textContent = 'Thử lại phản hồi';
      retryStory.addEventListener('click', function(){ window.__storyProviderFailedKey = ''; requestStoryDecision(true); });
      var storyRecovery = document.createElement('div');
      storyRecovery.className = 'story-provider-recovery';
      storyRecovery.appendChild(retryStory);
      article.appendChild(storyRecovery);
    }
    article.appendChild(box);
  }

  function renderSemanticLog() {
    if (!log || !state || !Array.isArray(state.log)) return;
    var previousScrollTop = log.scrollTop;
    log.textContent = '';
    state.log.forEach(function(entry, index){
      if (!entry) return;
      var article = document.createElement('article');
      var player = entry.role === 'player';
      article.className = 'message ' + (player ? 'player' : 'gm');
      article.dataset.logIndex = String(index);
      var role = document.createElement('div');
      role.className = 'role';
      role.textContent = player ? 'BẠN' : 'GAME MASTER';
      article.appendChild(role);
      var text = document.createElement('div');
      text.className = 'text gm-main-text';
      appendRichText(text, entry.text || '', entry, []);
      article.appendChild(text);
      if (!player) {
        appendBattleSection(article, entry, index);
        appendCombatDiceSection(article, index);
        appendExplorerChoices(article, entry, index);
      }
      log.appendChild(article);
    });
    requestAnimationFrame(function(){
      log.scrollTop = Math.max(0, Math.min(previousScrollTop, log.scrollHeight - log.clientHeight));
    });
  }

  function scrollLatestGmToStart() {
    if (!log) return;
    requestAnimationFrame(function(){
      var messages = log.querySelectorAll('.message.gm');
      if (!messages.length) return;
      var latest = messages[messages.length - 1];
      var logRect = log.getBoundingClientRect();
      var messageRect = latest.getBoundingClientRect();
      var target = log.scrollTop + (messageRect.top - logRect.top);
      log.scrollTop = Math.max(0, target);
    });
  }

  function scrollCombatToBottom() {
    if (!log) return;
    requestAnimationFrame(function(){ log.scrollTop = log.scrollHeight; });
  }

  function scrollForCurrentMode() {
    if (state && state.combat && state.combat.active) scrollCombatToBottom();
    else scrollLatestGmToStart();
  }

  window.backroomScrollLatestGmToStart = scrollLatestGmToStart;
  window.backroomScrollCombatToBottom = scrollCombatToBottom;
  window.backroomScrollForCurrentMode = scrollForCurrentMode;

  function syncComposer() {
    if (!form || !action || !submit) return;
    var combat = !!(state && state.combat && state.combat.active);
    var storyLocked = storyReturnPending() || storyAwaitingDecision() || storyAwaitingEntityAttack()
      || storyPendingAdvance() || storyCutawayActive() || storyHandoffPending()
      || storyAdvanceAvailable();
    form.classList.toggle('battle-locked', combat || storyLocked);
    action.disabled = combat || storyLocked;
    action.readOnly = combat || storyLocked;
    if (combat) {
      action.value = '';
      action.placeholder = 'Đang chiến đấu — hoàn tất Poker Dice trong khung bên trên.';
      submit.disabled = true;
    } else if (storyLocked) {
      action.value = '';
      action.placeholder = storyReturnPending()
        ? 'Tiếp tục khám phá để tìm lại đoạn đường đang dở.'
        : storyAwaitingEntityAttack()
        ? 'Encounter cốt truyện: chọn Tấn công trong khung GAME MASTER.'
        : (storyAwaitingDecision()
            ? 'Đọc tình huống và chọn một hành động trong khung GAME MASTER.'
            : (storyPendingAdvance()
                ? 'Đang chờ kết quả cuối turn trước khi mở turn kế.'
                : (storyHandoffPending()
                    ? 'Ranh giới cốt truyện đã sẵn sàng. Chọn nút Tiếp tục qua ranh giới.'
                    : 'Đang ở đoạn cắt cảnh cốt truyện.')));
      submit.disabled = true;
    } else {
      action.placeholder = defaultPlaceholder || 'Cao Minh làm gì trong Turn hiện tại?';
      submit.disabled = !!window.__combatBusy || (typeof busy !== 'undefined' && !!busy);
    }
  }

  var dicePanel=document.createElement('section');
  dicePanel.className='combat-dice-panel';
  dicePanel.hidden=true;
  dicePanel.setAttribute('aria-label','Poker Dice Combat');
  dicePanel.innerHTML='<div class="combat-dice-title" id="combatDiceTitle"></div><div class="combat-dice-meta" id="combatDiceMeta"></div><div class="combat-dice-row" id="combatDiceRow"></div><div class="combat-dice-result" id="combatDiceResult"></div><div class="combat-dice-actions"><button type="button" class="combat-roll" id="combatDiceRoll">ROLL</button><button type="button" class="combat-finish" id="combatDiceFinish">FINISH</button></div>';
  var diceTitle=dicePanel.querySelector('#combatDiceTitle');
  var diceMeta=dicePanel.querySelector('#combatDiceMeta');
  var diceRow=dicePanel.querySelector('#combatDiceRow');
  var diceResult=dicePanel.querySelector('#combatDiceResult');
  var diceRoll=dicePanel.querySelector('#combatDiceRoll');
  var diceFinish=dicePanel.querySelector('#combatDiceFinish');
  var finalizeTimer=0;
  var finalizeKey='';
  var diceRollAnimating=false;
  var diceRollStartedAt=0;
  var diceRollToken=0;
  var DICE_ROLL_ANIMATION_MS=650;

  function diceAsset(value){
    return 'file:///android_asset/dice/die-'+String(value)+'.svg';
  }

  function allHeld(values){
    if(!Array.isArray(values)||values.length!==5)return false;
    for(var i=0;i<5;i++)if(values[i]!==true)return false;
    return true;
  }

  function handLabel(hand){
    var labels={
      'NO HAND':'No Hand',
      'ONE PAIR':'One Pair',
      'TWO PAIR':'Two Pair',
      'THREE OF A KIND':'Three of a Kind',
      'STRAIGHT':'Straight',
      'FULL HOUSE':'Full House',
      'FOUR OF A KIND':'Four of a Kind',
      'SSF':'SSF',
      'FSF':'FSF'
    };
    var key=String(hand||'NO HAND');
    return labels[key]||key;
  }

  function combatDiceState(){
    return state&&state.combat&&state.combat.active&&state.combat.diceState?state.combat.diceState:null;
  }

  function combatLogIndex(){
    if(!state||!Array.isArray(state.log))return -1;
    var combat=state.combat||{};
    var index=Number(combat.logIndex);
    if(Number.isInteger(index)&&index>=0&&index<state.log.length){
      var entry=state.log[index];
      if(entry&&entry.role!=='player')return index;
    }
    return lastGmIndex();
  }

  function appendCombatDiceSection(article,index){
    if(!state||!state.combat||!state.combat.active)return;
    if(index!==combatLogIndex())return;
    article.appendChild(dicePanel);
  }

  function sendCombatHold(index,held){
    if(window.__combatBusy||!window.Android||typeof Android.combatHold!=='function')return;
    window.__combatBusy=true;
    renderCombatPanel();
    Android.combatHold(JSON.stringify(state),index,!!held);
  }

  function sendCombatRoll(){
    if(window.__combatBusy||!window.Android||typeof Android.combatRoll!=='function')return;
    window.__combatBusy=true;
    diceRollAnimating=true;
    diceRollStartedAt=Date.now();
    ++diceRollToken;
    renderCombatPanel();
    Android.combatRoll(JSON.stringify(state));
  }

  function sendCombatFinish(){
    if(window.__combatBusy||!window.Android||typeof Android.combatFinish!=='function')return;
    window.__combatBusy=true;
    renderCombatPanel();
    Android.combatFinish(JSON.stringify(state));
  }

  function scheduleCombatResolve(combat,dice){
    var key=String(combat.round||1)+':'+String(combat.actorIndex||0)+':'+String(combat.rngSequence||0)+':'+String(dice.hand||'');
    if(finalizeKey===key&&finalizeTimer)return;
    if(finalizeTimer)clearTimeout(finalizeTimer);
    finalizeKey=key;
    finalizeTimer=setTimeout(function(){
      finalizeTimer=0;
      if(window.__combatBusy||!state||!state.combat||!state.combat.active)return;
      var current=state.combat.diceState;
      if(!current||current.finalized!==true||current.resolved===true)return;
      if(!window.Android||typeof Android.combatResolve!=='function')return;
      window.__combatBusy=true;
      renderCombatPanel();
      Android.combatResolve(JSON.stringify(state));
    },2000);
  }

  function renderCombatPanel(){
    var combat=state&&state.combat;
    var dice=combatDiceState();
    var visible=!!dice&&!window.__combatFeedbackBusy;
    dicePanel.hidden=!visible;
    if(!visible)return;

    diceTitle.textContent=String(combat.currentActor||'Nhân vật');
    var hasRolled=dice.hasRolled===true;
    var rerolls=Math.max(0,Number(dice.rerollsUsed)||0);
    var maxRerolls=Math.max(0,Number(dice.maxRerolls)||3);
    diceMeta.textContent='Reroll '+String(rerolls)+' / '+String(maxRerolls)+' · chạm die để HOLD';

    diceRow.textContent='';
    var values=Array.isArray(dice.values)?dice.values:[0,0,0,0,0];
    var held=Array.isArray(dice.held)?dice.held:[false,false,false,false,false];
    for(var i=0;i<5;i++){
      (function(index){
        var button=document.createElement('button');
        button.type='button';
        var rolling=diceRollAnimating&&held[index]!==true;
        button.className='combat-die'+(held[index]===true?' held':'')+(rolling?' rolling':'');
        if(rolling)button.style.animationDelay=String(index*-55)+'ms';
        button.disabled=!hasRolled||dice.finalized===true||window.__combatBusy;
        button.setAttribute('aria-label','Die '+String(index+1)+(held[index]===true?' HOLD':''));
        var value=Number(values[index])||0;
        var visualValue=value>=1&&value<=6?value:(rolling?(index%6)+1:0);
        if(visualValue>=1&&visualValue<=6){
          var img=document.createElement('img');
          img.src=diceAsset(visualValue);
          img.alt=value>=1&&value<=6?String(value):'';
          button.appendChild(img);
        }else{
          var blank=document.createElement('span');blank.textContent='?';button.appendChild(blank);
        }
        button.addEventListener('click',function(){sendCombatHold(index,held[index]!==true);});
        diceRow.appendChild(button);
      })(i);
    }

    diceResult.textContent=hasRolled?handLabel(dice.hand):'';
    diceRoll.textContent='ROLL';
    diceFinish.textContent='FINISH';
    diceRoll.disabled=window.__combatBusy||dice.finalized===true||!hasRolled||rerolls>=maxRerolls||allHeld(held);
    diceFinish.disabled=window.__combatBusy||dice.finalized===true||!hasRolled;
    if(dice.finalized===true)scheduleCombatResolve(combat,dice);
  }

  diceRoll.addEventListener('click',sendCombatRoll);
  diceFinish.addEventListener('click',sendCombatFinish);

  window.backroomCombatDiceState=function(json){
    try{
      var nextState=JSON.parse(json);
      var applyDiceState=function(){
        diceRollAnimating=false;
        state=nextState;
        if(typeof CURRENT_CHARACTER_CANON!=='undefined')state.characterCanon=CURRENT_CHARACTER_CANON;
        try{localStorage.setItem('backroom-apk-state',JSON.stringify(state));}catch(_){}
        window.__combatBusy=false;
        if(typeof busy!=='undefined')busy=false;
        if(typeof window.render==='function')window.render();
        syncComposer();
        renderCombatPanel();
        if(status){
          var dice=combatDiceState();
          status.textContent=dice&&dice.finalized===true
            ? 'Đã chốt '+String(dice.hand||'hand')+'.'
            : 'Poker Dice · '+String(state.combat&&state.combat.currentActor||'Nhân vật');
        }
      };
      if(diceRollAnimating){
        var token=diceRollToken;
        var wait=Math.max(0,DICE_ROLL_ANIMATION_MS-(Date.now()-diceRollStartedAt));
        if(wait>0){
          setTimeout(function(){if(token===diceRollToken)applyDiceState();},wait);
          return;
        }
      }
      applyDiceState();
    }catch(_){
      diceRollAnimating=false;
      ++diceRollToken;
      window.__combatBusy=false;
      if(status)status.textContent='Combat dice state không hợp lệ.';
    }
  };

  var previousRender = window.render;
  window.render = function(){
    if (typeof previousRender === 'function') previousRender();
    renderSemanticLog();
    syncComposer();
    if (state && state.combat && state.combat.active) scrollCombatToBottom();
    renderCombatPanel();
  };

  if (form) {
    form.addEventListener('submit', function(event){
      if (state && state.combat && state.combat.active) {
        event.preventDefault();
        event.stopImmediatePropagation();
        if (status) status.textContent = 'Đang chiến đấu. Hãy hoàn tất Poker Dice trong khung bên trên.';
        syncComposer();
      }
    }, true);
  }

  var previousTurn = window.backroomTurn;
  window.backroomTurn = function(json){
    window.__combatBusy = false;
    if (typeof previousTurn === 'function') previousTurn(json);
    if (!storyDecisionNeedsProvider()) window.__storyProviderFailedKey = '';
    if (!returnJourneyNeedsProvider()) window.__returnProviderFailedKey = '';
    if (state && state.story && window.__selectedStoryChoiceKey !== ((state.story.decisionPackage.choices || [])[0] || {}).id)
      window.__selectedStoryChoiceKey = '';
    if (!storyReturnPending() || window.__selectedReturnChoiceKey !== String(state.story.returnJourney.journeyId) + ':' + String(state.story.returnJourney.turnIndex))
      window.__selectedReturnChoiceKey = '';
    syncComposer();
    scrollForCurrentMode();
  };

  function playCombatPhase(events, phase) {
    var phaseEvents = (Array.isArray(events) ? events : []).filter(function(event){ return event && event.phase === phase; });
    phaseEvents.forEach(function(event, index){
      setTimeout(function(){
        if (typeof window.backroomPlayCombatFeedback === 'function') window.backroomPlayCombatFeedback(event);
      }, Math.min(index * 150, 450));
    });
  }

  function finishCombatAnimation(token) {
    if (token !== window.__combatAnimationToken) return;
    window.__combatFeedbackBusy = false;
    window.__combatBusy = false;
    if (typeof busy !== 'undefined') busy = false;
    if (typeof window.backroomClearCombatVisualActor === 'function') window.backroomClearCombatVisualActor();
    if (typeof window.render === 'function') window.render();
    scrollForCurrentMode();
    if (status) {
      status.textContent = state.combat && state.combat.active
        ? 'Lượt chiến đấu ' + state.combat.round + ' · ' + state.combat.currentActor
        : (state.combat && state.combat.outcome === 'victory'
            ? 'Entity bị tiêu diệt. Bắt đầu Turn ' + state.turn + '.'
            : 'Chiến đấu kết thúc. Turn ' + state.turn + '.');
    }
  }

  window.backroomCombatTurn = function(json){
    try {
      var nextState = JSON.parse(json);
      state = nextState;
      if (typeof CURRENT_CHARACTER_CANON !== 'undefined') state.characterCanon = CURRENT_CHARACTER_CANON;
      if (action) action.value = '';
      try { localStorage.setItem('backroom-apk-state', JSON.stringify(state)); } catch (_) {}

      var combat = state.combat || {};
      var events = Array.isArray(combat.feedbackEvents) ? combat.feedbackEvents : [];
      var hasResolvedActor = Number.isInteger(combat.resolvedActorIndex);
      if (!hasResolvedActor) {
        window.__combatBusy = false;
        if (typeof busy !== 'undefined') busy = false;
        if (typeof window.backroomClearCombatVisualActor === 'function') window.backroomClearCombatVisualActor();
        if (typeof window.render === 'function') window.render();
        syncComposer();
        scrollForCurrentMode();
        return;
      }

      window.__combatFeedbackBusy = true;
      window.__combatBusy = true;
      if (typeof busy !== 'undefined') busy = true;
      var token = ++window.__combatAnimationToken;
      var entityKey = combat.entity && combat.entity.key ? combat.entity.key : '';
      if (typeof window.backroomSetCombatVisualActor === 'function') {
        window.backroomSetCombatVisualActor(combat.resolvedActorIndex, entityKey);
      }
      if (typeof window.render === 'function') window.render();
      syncComposer();
      renderCombatPanel();
      scrollCombatToBottom();
      if (status) status.textContent = 'Đang xử lý lượt của ' + (combat.resolvedActorName || 'nhân vật') + '…';

      playCombatPhase(events, 'actor');
      var delay = COMBAT_PHASE_MS;
      if (combat.resolvedEntityTurn === true) {
        setTimeout(function(){
          if (token !== window.__combatAnimationToken) return;
          if (status) status.textContent = 'Entity đang phản hồi…';
          playCombatPhase(events, 'entity');
        }, COMBAT_PHASE_MS);
        delay += COMBAT_PHASE_MS;
      }
      setTimeout(function(){ finishCombatAnimation(token); }, delay);
    } catch (error) {
      ++window.__combatAnimationToken;
      window.__combatFeedbackBusy = false;
      window.__combatBusy = false;
      if (typeof busy !== 'undefined') busy = false;
      if (typeof window.backroomClearCombatVisualActor === 'function') window.backroomClearCombatVisualActor();
      if (status) status.textContent = 'Combat state không hợp lệ.';
      syncComposer();
    }
  };

  var previousError = window.backroomError;
  window.backroomError = function(message){
    diceRollAnimating=false;
    ++diceRollToken;
    window.__combatFeedbackBusy = false;
    window.__combatBusy = false;
    if (state && state.story && (state.story.pendingChoiceId
        || (state.story.returnJourney && state.story.returnJourney.pendingChoiceId)))
      window.__pendingResolutionFailedKey = window.__pendingResolutionRequestKey;
    allowSingleAutomaticProviderRetry();
    if (typeof previousError === 'function') previousError(message);
    syncComposer();
    if (typeof window.render === 'function') window.render();
  };

  window.render();
  scrollForCurrentMode();
})();
