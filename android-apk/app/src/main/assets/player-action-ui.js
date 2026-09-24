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
  if (!modal || !openButton || !form || !action) return;

  function combatActive(){
    try {
      return !!(state && state.combat && state.combat.active);
    } catch (_) {
      return false;
    }
  }

  function processing(){
    return !!window.__combatBusy || (typeof busy !== 'undefined' && !!busy);
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

  function storyReturnActive(){
    try {
      return !!(state && state.story && state.story.returnJourney
        && state.story.returnJourney.active === true);
    } catch (_) {
      return false;
    }
  }

  function storyDecisionActive(){
    try {
      return !!(state && state.story && state.story.active === true
        && state.story.arcComplete !== true
        && !storyReturnActive()
        && state.story.awaitingDecision === true);
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
    if (combatActive() || processing() || storyReturnActive() || storyBootstrapPending()
        || storyCutawayActive() || storyDecisionActive()
        || storyEntityAttackActive() || storyAdvancePending()) {
      if (typeof statusEl !== 'undefined' && statusEl) {
        if (combatActive()) statusEl.textContent = 'Đang chiến đấu. Hãy chọn hành động trong khung GAME MASTER.';
        else if (storyReturnActive()) statusEl.textContent = 'Hãy chọn một hướng đi trong khung GAME MASTER.';
        else if (storyBootstrapPending()) statusEl.textContent = 'Hãy bắt đầu khám phá thế giới Backrooms trong khung GAME MASTER.';
        else if (storyEntityAttackActive()) statusEl.textContent = 'Encounter cốt truyện: hãy chọn Tấn công trong khung GAME MASTER.';
        else if (storyDecisionActive()) statusEl.textContent = 'Đang ở điểm quyết định cốt truyện. Hãy chọn một hành động trong khung GAME MASTER.';
        else if (storyCutawayActive()) statusEl.textContent = 'Đang ở đoạn cắt cảnh cốt truyện.';
        else statusEl.textContent = 'Đang xử lý lượt hiện tại.';
      }
      return;
    }
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
    var locked = combatActive() || processing() || storyReturnActive() || storyBootstrapPending()
      || storyCutawayActive() || storyDecisionActive() || storyEntityAttackActive() || storyAdvancePending();
    openButton.disabled = locked;
    openButton.setAttribute('aria-disabled', String(locked));
    if ((combatActive() || storyReturnActive() || storyBootstrapPending() || storyCutawayActive()
        || storyDecisionActive() || storyEntityAttackActive() || storyAdvancePending())
        && !modal.hidden) closePlayerAction(true);
  }

  openButton.addEventListener('click', openPlayerAction);
  form.addEventListener('submit', function(event){
    if (!storyBootstrapPending()) return;
    event.preventDefault();
    event.stopImmediatePropagation();
    if (typeof statusEl !== 'undefined' && statusEl)
      statusEl.textContent = 'Hãy bắt đầu khám phá thế giới Backrooms trong khung GAME MASTER.';
    closePlayerAction(true);
  }, true);
  if (closeButton) closeButton.addEventListener('click', function(){ if (!processing()) closePlayerAction(true); });
  if (cancelButton) cancelButton.addEventListener('click', function(){ if (!processing()) closePlayerAction(true); });
  if (backdrop) backdrop.addEventListener('click', function(){ if (!processing()) closePlayerAction(true); });

  form.addEventListener('submit', function(){
    setTimeout(function(){
      if (typeof busy !== 'undefined' && busy) closePlayerAction(true);
      syncPlayerAction();
    }, 0);
  });

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

  var previousRender = window.render;
  window.render = function(){
    if (typeof previousRender === 'function') previousRender();
    syncPlayerAction();
  };

  var previousTurn = window.backroomTurn;
  window.backroomTurn = function(json){
    if (typeof previousTurn === 'function') previousTurn(json);
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
