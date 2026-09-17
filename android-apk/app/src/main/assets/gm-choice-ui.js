(function(){
  'use strict';
  if (window.__gmChoiceUiInstalled) return;
  window.__gmChoiceUiInstalled = true;

  var style = document.createElement('style');
  style.textContent = [
    "@font-face{font-family:'Play';font-style:normal;font-weight:700;src:url('file:///android_asset/fonts/Play-Bold.ttf') format('truetype');font-display:swap}",
    ".message.gm .role,.gm-choice,.combat-turn-label{font-family:'Play',system-ui,sans-serif;font-weight:700}",
    ".semantic{font-family:'Play',system-ui,sans-serif;font-weight:700;text-decoration:underline;text-decoration-thickness:1.5px;text-underline-offset:2px}",
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

  function addTerm(set, value) {
    if (value === null || value === undefined) return;
    var text = String(value).trim();
    if (text.length > 1) set.add(text);
  }

  function entryHighlights(entry) {
    var set = new Set();
    addTerm(set, 'Kai');
    addTerm(set, 'Kai Akechi');
    addTerm(set, 'Iris');
    addTerm(set, 'Syvial');
    addTerm(set, 'Lucia Lục');
    addTerm(set, 'Hứa Thuý Mai');
    ['Choáng','Chảy máu','Phá giáp','Né tránh','Mất phương hướng'].forEach(function(x){ addTerm(set,x); });

    try {
      if (state && state.player) addTerm(set, state.player.name);
      if (state && Array.isArray(state.party)) state.party.forEach(function(x){ addTerm(set, typeof x === 'string' ? x : x && (x.name || x.id)); });
      if (state && Array.isArray(state.inventory)) state.inventory.forEach(function(x){ addTerm(set, typeof x === 'string' ? x : x && x.name); });
      if (state && state.combat) {
        addTerm(set, state.combat.currentActor);
        if (state.combat.entity) addTerm(set, state.combat.entity.name);
        if (state.combat.currentSkill) addTerm(set, state.combat.currentSkill.name);
      }
    } catch (_) {}

    var list = entry && Array.isArray(entry.highlights) ? entry.highlights : [];
    list.forEach(function(x){ addTerm(set, typeof x === 'string' ? x : x && (x.text || x.name)); });
    return Array.from(set).sort(function(a,b){ return b.length-a.length; });
  }

  function escapeRegex(value) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  function appendRichText(container, text, entry, extraHighlights) {
    var source = text === null || text === undefined ? '' : String(text);
    var terms = entryHighlights(entry);
    (extraHighlights || []).forEach(function(x){
      var value = typeof x === 'string' ? x : x && (x.text || x.name);
      if (value && terms.indexOf(String(value)) < 0) terms.push(String(value));
    });
    terms.sort(function(a,b){ return b.length-a.length; });

    var patterns = terms.map(escapeRegex);
    patterns.push('[+-]\\d+(?:\\.\\d+)?%?\\s*(?:HP|DEF)');
    patterns.push('HP\\s*\\d+\\s*\\/\\s*\\d+');
    patterns.push('Level\\s+\\d+(?:\\s*[-–—/]\\s*[A-Za-zÀ-ỹ0-9 _]+)?');
    var re = new RegExp('(' + patterns.join('|') + ')', 'gi');
    var cursor = 0;
    var match;
    while ((match = re.exec(source)) !== null) {
      if (match.index > cursor) container.appendChild(document.createTextNode(source.slice(cursor, match.index)));
      var span = document.createElement('span');
      span.className = 'semantic';
      span.textContent = match[0];
      container.appendChild(span);
      cursor = match.index + match[0].length;
      if (match[0].length === 0) re.lastIndex++;
    }
    if (cursor < source.length) container.appendChild(document.createTextNode(source.slice(cursor)));
  }

  function lastGmChoiceIndex() {
    if (!state || !Array.isArray(state.log)) return -1;
    for (var i = state.log.length - 1; i >= 0; i--) {
      var entry = state.log[i];
      if (entry && entry.role !== 'player' && Array.isArray(entry.choices) && entry.choices.length) return i;
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

  function submitCombatChoice(choice) {
    if (!choice || choice.disabled || window.__combatBusy) return;
    if (!window.Android || typeof Android.submitTurn !== 'function') {
      if (status) status.textContent = 'Không tìm thấy Android bridge.';
      return;
    }
    window.__combatBusy = true;
    syncComposer();
    if (typeof window.render === 'function') window.render();
    if (status) status.textContent = 'Đang xử lý Combat Turn ' + ((state.combat && state.combat.round) || 1) + '…';
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
    label.textContent = 'COMBAT TURN ' + (combat.round || 1) + ' · ' + (combat.currentActor || 'Nhân vật');
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
    if (!entry || !Array.isArray(entry.choices) || !entry.choices.length) return;
    if (state.combat && state.combat.active && Number(state.combat.logIndex) === Number(index)) return;
    var actionable = index === lastGmChoiceIndex() && !(state.combat && state.combat.active) && !window.__combatBusy;
    var box = document.createElement('div');
    box.className = 'gm-choices explorer-choices';
    entry.choices.slice(0,3).forEach(function(choice, choiceIndex){
      var id = choice.id || String.fromCharCode(65 + choiceIndex);
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
          ? 'Combat Turn ' + state.combat.round + ' · ' + state.combat.currentActor
          : 'Combat kết thúc. Explorer Turn vẫn là ' + state.turn + '.';
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
