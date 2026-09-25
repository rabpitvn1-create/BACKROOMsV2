(function(){
  'use strict';
  if (window.__playerActionUiInstalled) return;
  window.__playerActionUiInstalled = true;

  var modal = document.getElementById('playerActionModal');
  var openButton = document.getElementById('playerActionOpen');
  var closeButton = document.getElementById('playerActionClose');
  var cancelButton = document.getElementById('playerActionCancel');
  var backdrop = document.getElementById('playerActionBackdrop');
  var form = document.getElementById('form');
  var action = document.getElementById('action');
  var submit = document.getElementById('submit');
  if (!modal || !openButton || !form || !action) return;

  window.__playerEnvironmentBusy = false;

  function combatActive(){
    try {
      return !!(state && state.combat && state.combat.active);
    } catch (_) {
      return false;
    }
  }

  function processing(){
    return !!window.__combatBusy || !!window.__playerEnvironmentBusy
      || (typeof busy !== 'undefined' && !!busy);
  }

  function storyCutawayActive(){
    try {
      return !!(state && state.story && state.story.active === true
        && state.story.arcComplete !== true
        && String(state.story.visibility || '') === 'cutaway');
    } catch (_) {
      return false;
    }
  }

  function storyBootstrapPending(){
    try {
      var story = state && state.story;
      return !!(story && story.active === true && story.arcComplete !== true
        && !(story.returnJourney && story.returnJourney.active === true)
        && story.segmentDelivered !== true && story.awaitingDecision !== true
        && story.awaitingEntityAttack !== true && story.pendingStoryAdvance !== true
        && !(state.combat && state.combat.active));
    } catch (_) {
      return false;
    }
  }

  function storyEntityAttackActive(){
    try {
      return !!(state && state.story && state.story.active === true
        && state.story.arcComplete !== true
        && state.story.awaitingEntityAttack === true);
    } catch (_) {
      return false;
    }
  }

  function storyAdvancePending(){
    try {
      return !!(state && state.story && state.story.pendingStoryAdvance === true);
    } catch (_) {
      return false;
    }
  }

  function environmentLocked(){
    return combatActive() || processing() || storyBootstrapPending() || storyCutawayActive()
      || storyEntityAttackActive() || storyAdvancePending();
  }

  function fitVisualViewport(){
    if (modal.hidden) return;
    var vv = window.visualViewport;
    if (!vv) {
      modal.style.top = '0px';
      modal.style.height = '100%';
      return;
    }
    modal.style.top = Math.max(0, vv.offsetTop || 0) + 'px';
    modal.style.height = Math.max(1, vv.height || window.innerHeight) + 'px';
  }

  function resetViewport(){
    modal.style.removeProperty('top');
    modal.style.removeProperty('height');
  }

  function closePlayerAction(keepDraft){
    if (modal.hidden) return;
    modal.hidden = true;
    modal.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('player-action-open');
    resetViewport();
    try { action.blur(); } catch (_) {}
    if (!keepDraft) action.value = '';
  }

  function openPlayerAction(){
    if (environmentLocked()) return;
    modal.hidden = false;
    modal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('player-action-open');
    fitVisualViewport();
    setTimeout(function(){
      try {
        action.focus({preventScroll:true});
        var length = action.value.length;
        action.setSelectionRange(length, length);
      } catch (_) {
        try { action.focus(); } catch (_) {}
      }
    }, 40);
  }

  function syncPlayerAction(){
    var locked = environmentLocked();
    openButton.disabled = locked;
    openButton.setAttribute('aria-disabled', String(locked));
    if ((combatActive() || storyBootstrapPending() || storyCutawayActive()
        || storyEntityAttackActive() || storyAdvancePending()) && !modal.hidden) {
      closePlayerAction(true);
    }
  }

  function showEnvironmentError(message){
    window.__playerEnvironmentBusy = false;
    window.__gmEnvironmentLoading = false;
    window.__gmErrorMessage = 'Đang gặp lỗi. Vui lòng thử lại.';
    if (typeof busy !== 'undefined') busy = false;
    if (submit) submit.disabled = false;
    if (message && window.console && typeof console.error === 'function')
      console.error('Environment PLAYER ACTION:', message);
    if (typeof window.render === 'function') window.render();
    syncPlayerAction();
    if (typeof window.backroomScrollGmSystemMessage === 'function')
      window.backroomScrollGmSystemMessage();
  }

  form.addEventListener('submit', function(event){
    if (modal.hidden) return;
    event.preventDefault();
    event.stopImmediatePropagation();

    var text = String(action.value || '').trim();
    if (!text || environmentLocked()) return;
    if (!window.Android || typeof Android.submitEnvironmentAction !== 'function') {
      showEnvironmentError('Không tìm thấy Android environment bridge.');
      return;
    }

    window.__playerEnvironmentBusy = true;
    window.__gmEnvironmentLoading = true;
    window.__gmErrorMessage = '';
    if (typeof busy !== 'undefined') busy = true;
    if (submit) submit.disabled = true;
    closePlayerAction(true);
    if (typeof window.render === 'function') window.render();
    Android.submitEnvironmentAction(JSON.stringify(state), text);
  }, true);

  if (closeButton) closeButton.addEventListener('click', function(){ if (!processing()) closePlayerAction(true); });
  if (cancelButton) cancelButton.addEventListener('click', function(){ if (!processing()) closePlayerAction(true); });
  if (backdrop) backdrop.addEventListener('click', function(){ if (!processing()) closePlayerAction(true); });

  document.addEventListener('keydown', function(event){
    if (event.key === 'Escape' && !modal.hidden && !processing()) closePlayerAction(true);
  });

  if (window.visualViewport) {
    window.visualViewport.addEventListener('resize', fitVisualViewport);
    window.visualViewport.addEventListener('scroll', fitVisualViewport);
  }
  window.addEventListener('resize', fitVisualViewport);

  window.backroomOpenPlayerAction = openPlayerAction;
  window.backroomClosePlayerAction = closePlayerAction;
  window.backroomSyncPlayerAction = syncPlayerAction;

  window.backroomEnvironmentTurn = function(json){
    try {
      state = typeof ensureCurrentLevel === 'function'
        ? ensureCurrentLevel(JSON.parse(json))
        : JSON.parse(json);
      if (typeof CURRENT_CHARACTER_CANON !== 'undefined') state.characterCanon = CURRENT_CHARACTER_CANON;
      try { localStorage.setItem('backroom-apk-state', JSON.stringify(state)); } catch (_) {}
      action.value = '';
      window.__playerEnvironmentBusy = false;
      window.__gmEnvironmentLoading = false;
      window.__gmErrorMessage = '';
      if (typeof busy !== 'undefined') busy = false;
      if (submit) submit.disabled = false;
      closePlayerAction(false);
      if (typeof window.render === 'function') window.render();
      syncPlayerAction();
      if (typeof window.backroomScrollLatestGmToStart === 'function')
        window.backroomScrollLatestGmToStart();
    } catch (error) {
      showEnvironmentError(error && error.message ? error.message : 'Environment state không hợp lệ.');
    }
  };

  window.backroomEnvironmentError = showEnvironmentError;

  var previousRender = window.render;
  window.render = function(){
    if (typeof previousRender === 'function') previousRender();
    syncPlayerAction();
  };

  var previousTurn = window.backroomTurn;
  window.backroomTurn = function(json){
    if (typeof previousTurn === 'function') previousTurn(json);
    window.__playerEnvironmentBusy = false;
    window.__gmEnvironmentLoading = false;
    closePlayerAction(false);
    syncPlayerAction();
  };

  var previousError = window.backroomError;
  window.backroomError = function(message){
    if (typeof previousError === 'function') previousError(message);
    syncPlayerAction();
  };

  var previousCommittedError = window.backroomCommittedError;
  window.backroomCommittedError = function(json){
    if (typeof previousCommittedError === 'function') previousCommittedError(json);
    syncPlayerAction();
  };

  syncPlayerAction();
})();
