(function(){
  'use strict';
  if (window.__gmChoiceUiInstalled) return;
  window.__gmChoiceUiInstalled = true;

  var style = document.createElement('style');
  style.textContent = [
    "@font-face{font-family:'Play';font-style:normal;font-weight:700;src:url('file:///android_asset/fonts/Play-Bold.ttf') format('truetype');font-display:swap}",
    ".message.gm .role,.gm-choice,.combat-turn-label{font-family:'Play',system-ui,sans-serif;font-weight:700}",
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
    ".semantic-generic{color:#e5e9ed}",
    ".gm-choice:disabled .semantic{opacity:.72}",
    ".gm-main-text{white-space:pre-wrap;line-height:1.55}",
    ".battle-log{display:grid;gap:5px;margin-top:10px}",
    ".battle-line{white-space:pre-wrap;line-height:1.45}",
    ".gm-choices{display:grid;gap:7px;margin-top:12px}",
    ".combat-turn-label{font-size:12px;letter-spacing:.08em;color:#b5bec6;margin:2px 0 1px}",
    ".gm-choice{width:100%;text-align:left;padding:11px 12px;background:#171d22;border:1px solid #39424a;color:#f0f3f5;line-height:1.35}",
    ".gm-choice:disabled{background:#111519;color:#747d85;border-color:#272e34;opacity:.75}",
    ".gm-choice.selected{border-color:#7a858e;background:#20272d}",
    ".composer.battle-locked textarea{background:#101316;color:#697178;border-color:#262d33}",
    ".composer.battle-locked #submit{background:#24282c;color:#777e84;border-color:#30353a;opacity:.7}",
    ".message.gm{border-left-color:#59646d}",
    ".battle-separator{height:1px;background:#262d33;margin-top:10px}"
  ].join('');
  document.head.appendChild(style);

  var log = document.getElementById('log');
  var form = document.getElementById('form');
  var action = document.getElementById('action');
  var submit = document.getElementById('submit');
  var status = document.getElementById('status');
  var defaultPlaceholder = action ? action.getAttribute('placeholder') : '';
  window.__combatBusy = false;

  var semanticPriority = {generic:0,location:1,item:2,effect:3,skill:4,character:5,entity:6};
  var knownSkills = [
    'The Last Requiem','Silent Lullaby','Salvation','Quick Step','Guilty Crown Override',
    'Twosome Time','Rain Storm','Honeycomb Fire','Charged Shot',
    'Rift Sever','Crimson Guillotine','Lucifer Breaker','Spatial Dominion','M4A1 Joint Attack'
  ];
  var knownEffects = ['Choáng','Chảy máu','Phá giáp','Né tránh','Mất phương hướng'];

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
    ['Kai','Kai Akechi','Iris','Syvial','Lucia Lục','Hứa Thuý Mai'].forEach(function(x){ addTerm(map,x,'character'); });
    knownEffects.forEach(function(x){ addTerm(map,x,'effect'); });
    knownSkills.forEach(function(x){ addTerm(map,x,'skill'); });

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
    patterns.push('HP\\s*\\d+\\s*\\/\\s*\\d+');
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
        else if (/^HP\s*\d+\s*\/\s*\d+$/i.test(matched)) type = 'stat';
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

  function chestPresent() {
    try { return !!(state && state.flags && state.flags.chestPresent === true); } catch (_) { return false; }
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

  function submitCombatChoice(choice) {
    if (!choice || choice.disabled || window.__combatBusy) return;
    if (!window.Android || typeof Android.submitTurn !== 'function') {
      if (status) status.textContent = 'Không tìm thấy Android bridge.';
      return;
    }
    window.__combatBusy = true;
    syncComposer();
    if (typeof window.render === 'function') window.render();
    if (status) status.textContent = 'Đang xử lý Lượt chiến đấu ' + ((state.combat && state.combat.round) || 1) + '…';
    Android.submitTurn(JSON.stringify(state), '__combat:' + String(choice.id || '').toUpperCase());
  }

  function makeChoiceButton(prefix, text, entry, extra, disabled, selected, onClick) {
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'gm-choice' + (selected ? ' selected' : '');
    button.disabled = !!disabled;
    button.appendChild(document.createTextNode(prefix + '. '));
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

    var combat = state && state.combat;
    if (!combat || !combat.active || Number(combat.logIndex) !== Number(index)) return;
    var box = document.createElement('div');
    box.className = 'gm-choices';
    var label = document.createElement('div');
    label.className = 'combat-turn-label';
    label.appendChild(document.createTextNode('LƯỢT CHIẾN ĐẤU ' + (combat.round || 1) + ' · '));
    appendRichText(label, combat.currentActor || 'Nhân vật', entry, [{text:combat.currentActor || 'Nhân vật',type:'character'}]);
    box.appendChild(label);
    var choices = Array.isArray(combat.choices) ? combat.choices : [];
    choices.forEach(function(choice){
      var disabled = !!choice.disabled || window.__combatBusy;
      box.appendChild(makeChoiceButton(choice.id || '?', choice.text || '', entry,
        combat.currentSkill ? [combat.currentSkill.name] : [], disabled, false,
        function(){ submitCombatChoice(choice); }));
    });
    article.appendChild(box);
  }

  function appendExplorerChoices(article, entry, index) {
    if (!entry) return;
    if (state.combat && state.combat.active && Number(state.combat.logIndex) === Number(index)) return;
    var latest = index === lastGmIndex();
    var hasChest = latest && chestPresent();
    var choices = Array.isArray(entry.choices) ? entry.choices : [];
    if (!hasChest && !choices.length) return;
    var actionable = latest && !(state.combat && state.combat.active) && !window.__combatBusy;
    var box = document.createElement('div');
    box.className = 'gm-choices explorer-choices';
    var choiceOffset = 0;
    if (hasChest) {
      box.appendChild(makeChoiceButton('A', 'Mở Rương', entry, [{text:'Rương',type:'item'}], !actionable, false,
        function(){ submitChestChoice(); }));
      choiceOffset = 1;
    }
    choices.slice(0, 3 - choiceOffset).forEach(function(choice, choiceIndex){
      var id = String.fromCharCode(65 + choiceOffset + choiceIndex);
      var disabled = !actionable || !!choice.disabled || !!choice.selected;
      box.appendChild(makeChoiceButton(id, choice.text || choice.action || '', entry, choice.highlights || [],
        disabled, !!choice.selected, function(){ submitExplorerChoice(entry, choice); }));
    });
    article.appendChild(box);
  }

  function renderSemanticLog() {
    if (!log || !state || !Array.isArray(state.log)) return;
    log.textContent = '';
    state.log.forEach(function(entry, index){
      if (!entry) return;
      var article = document.createElement('article');
      var player = entry.role === 'player';
      article.className = 'message ' + (player ? 'player' : 'gm');
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
        appendExplorerChoices(article, entry, index);
      }
      log.appendChild(article);
    });
    requestAnimationFrame(function(){ log.scrollTop = log.scrollHeight; });
  }

  function syncComposer() {
    if (!form || !action || !submit) return;
    var combat = !!(state && state.combat && state.combat.active);
    form.classList.toggle('battle-locked', combat);
    action.disabled = combat;
    action.readOnly = combat;
    if (combat) {
      action.value = '';
      action.placeholder = 'Đang chiến đấu — hãy chọn A, B hoặc C trong khung GAME MASTER.';
      submit.disabled = true;
    } else {
      action.placeholder = defaultPlaceholder || 'Kai làm gì trong Turn hiện tại?';
      submit.disabled = !!window.__combatBusy || (typeof busy !== 'undefined' && !!busy);
    }
  }

  var previousRender = window.render;
  window.render = function(){
    if (typeof previousRender === 'function') previousRender();
    renderSemanticLog();
    syncComposer();
  };

  if (form) {
    form.addEventListener('submit', function(event){
      if (state && state.combat && state.combat.active) {
        event.preventDefault();
        event.stopImmediatePropagation();
        if (status) status.textContent = 'Đang chiến đấu. Hãy chọn A, B hoặc C trong khung GAME MASTER.';
        syncComposer();
      }
    }, true);
  }

  var previousTurn = window.backroomTurn;
  window.backroomTurn = function(json){
    window.__combatBusy = false;
    if (typeof previousTurn === 'function') previousTurn(json);
    syncComposer();
  };

  window.backroomCombatTurn = function(json){
    try {
      state = JSON.parse(json);
      if (typeof CURRENT_CHARACTER_CANON !== 'undefined') state.characterCanon = CURRENT_CHARACTER_CANON;
      if (typeof busy !== 'undefined') busy = false;
      window.__combatBusy = false;
      if (action) action.value = '';
      try { localStorage.setItem('backroom-apk-state', JSON.stringify(state)); } catch (_) {}
      if (typeof window.render === 'function') window.render();
      if (status) {
        status.textContent = state.combat && state.combat.active
          ? 'Lượt chiến đấu ' + state.combat.round + ' · ' + state.combat.currentActor
          : 'Chiến đấu kết thúc. Explorer Turn vẫn là ' + state.turn + '.';
      }
    } catch (error) {
      window.__combatBusy = false;
      if (status) status.textContent = 'Combat state không hợp lệ.';
      syncComposer();
    }
  };

  var previousError = window.backroomError;
  window.backroomError = function(message){
    window.__combatBusy = false;
    if (typeof previousError === 'function') previousError(message);
    syncComposer();
    if (typeof window.render === 'function') window.render();
  };

  window.render();
})();
