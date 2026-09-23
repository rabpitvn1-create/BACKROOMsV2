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

  function storyInteractionActive(){
    try {
      return !!(state && state.story && state.story.active === true
        && state.story.arcComplete !== true
        && state.story.awaitingInteraction === true);
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
    if (combatActive() || processing() || storyCutawayActive() || storyInteractionActive()) {
      if (typeof statusEl !== 'undefined' && statusEl) {
        statusEl.textContent = combatActive()
          ? 'Đang chiến đấu. Hãy chọn A, B hoặc C trong khung GAME MASTER.'
          : (storyInteractionActive()
              ? 'Đang ở điểm tương tác cốt truyện. Hãy chọn A/B/C trong khung GAME MASTER.'
              : (storyCutawayActive()
                  ? 'Đang ở đoạn cắt cảnh. Hãy chọn “Tiếp tục cốt truyện” trong khung GAME MASTER.'
                  : 'Đang xử lý lượt hiện tại.'));
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
    var locked = combatActive() || processing() || storyCutawayActive() || storyInteractionActive();
    openButton.disabled = locked;
    openButton.setAttribute('aria-disabled', String(locked));
    if ((combatActive() || storyCutawayActive() || storyInteractionActive()) && !modal.hidden) closePlayerAction(true);
  }

  openButton.addEventListener('click', openPlayerAction);
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