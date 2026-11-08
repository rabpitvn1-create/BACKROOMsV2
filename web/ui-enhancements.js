/*
 * ui-enhancements.js — static recreation of the UI that the APK's
 * MainActivity.installUiEnhancements() injects via evaluateJavascript at
 * onPageFinished. The payload below is lifted VERBATIM from the Java string
 * in MainActivity.java (v1.1.65 tree), with the adaptations listed here —
 * nothing else differs from the native payload.
 *
 * Load order: this file must load AFTER the game's inline <script> in
 * index.html (the assembler's job, cluster C5). That is the static
 * equivalent of onPageFinished: the page globals the payload wraps or
 * reads (state, render, backroomTurn, backroomError) already exist.
 * (Adaptation A1 — timing only, no code change.)
 *
 * Adaptation A2 — snapshot cache routing (cluster C4): the native payload
 * keeps the snapshot image in localStorage["backroom-apk-snapshot"]. On the
 * web build nothing game-owned may remain in localStorage, so the two
 * touch-points route to C4's adapter instead:
 *   read : cachedSnapshot() was synchronous over localStorage; it now
 *          takes a callback fed by SaveBoot.loadSnapshot() (a promise).
 *          The turn-matching logic (Number(turn) equality + dataUri
 *          presence) is unchanged, applied to the resolved object.
 *          renderSnapshot() and the load-time auto-snapshot check became
 *          callback-based as a direct consequence.
 *   write: window.backroomSnapshot stores via
 *          SaveBoot.saveSnapshot({turn, model, dataUri}) instead of
 *          localStorage.setItem("backroom-apk-snapshot", JSON.stringify(…)).
 *   If SaveBoot is absent the cache reads as empty and writes are dropped;
 *   there is deliberately no localStorage fallback.
 *
 * Adaptation A3 — owner-requested cosmetic (2026-11-03, web only): the
 * injected <style> gains one rule the native payload does not have —
 * #snapshot .stage-bg{border-radius:20px} — so the snapshot stage's
 * background frame renders with 20 px rounded corners. Since the VN
 * stage port (2026-11-09) only .stage-bg is clipped/rounded (Hui rule);
 * the figures above it are never clipped. The APK frame is unchanged.
 *
 * Adaptation A4 — VN stage port (2026-11-09, BOTH payloads): #snapshot is
 * now the composed VN stage (level background + figures), so
 * renderSnapshot() no longer replaces the box content. A cached Gemini
 * image becomes a .snapshot-art layer inserted as the box's first child
 * (above the level background, below the figures); with no cache the
 * layer is removed and the composed stage shows. The native payload in
 * MainActivity.java carries the identical logic change.
 *
 * Inert-safety, as native: with window.Android absent the UI still
 * installs in full; only requestSnapshot() reports the missing bridge on
 * the status line ('Không tìm thấy Android snapshot bridge.'), exactly as
 * the native payload does.
 *
 * Globals: this file defines exactly the globals the native payload
 * defines — window.__backroomEnhancements (its double-injection guard),
 * window.requestSnapshot, window.backroomSnapshot,
 * window.backroomSnapshotError — and wraps the page's own window.render /
 * window.backroomTurn / window.backroomError the same way native does.
 * No other names are introduced; index.html defines none of the
 * payload-only names (checked against its inline script).
 *
 * The injected <style> travels inside the payload (the native code builds
 * it as a style element), so there is no separate .css file.
 */
(function(){if(window.__backroomEnhancements)return;window.__backroomEnhancements=true;var st=document.createElement('style');st.textContent='button{transition:transform 80ms ease,background 120ms ease,border-color 120ms ease;touch-action:manipulation;-webkit-tap-highlight-color:rgba(255,255,255,.12)}button:active:not(:disabled){transform:scale(.965);background:#303840;border-color:#77828c}button:disabled{opacity:.48;cursor:not-allowed}.snapshot-placeholder{display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}.snapshot-placeholder b{font-size:12px;letter-spacing:.16em}.snapshot-placeholder small{color:#56616a}.message.pending{opacity:.72}.message.pending .text{color:#aeb7be}#snapshot .stage-bg{border-radius:20px}';document.head.appendChild(st);function scrollBottom(){var l=document.getElementById('log');if(l)requestAnimationFrame(function(){l.scrollTop=l.scrollHeight;});}function cachedSnapshot(cb){var done=function(r){cb(r&&Number(r.turn)===Number(state&&state.turn)&&r.dataUri?r:null);};try{if(window.SaveBoot&&typeof SaveBoot.loadSnapshot==='function'){SaveBoot.loadSnapshot().then(done,function(){cb(null);});}else{cb(null);}}catch(e){cb(null);}}function renderSnapshot(){var box=document.getElementById('snapshot');if(!box)return;cachedSnapshot(function(r){var art=box.querySelector('.snapshot-art');if(r){if(!art){art=document.createElement('img');art.className='snapshot-art';box.insertBefore(art,box.firstChild);}art.src=r.dataUri;art.alt='Snapshot Turn '+(state.turn||'');}else if(art){art.remove();}});}function requestSnapshot(){if(!window.Android||typeof Android.requestSnapshot!=='function'){var s=document.getElementById('status');if(s)s.textContent='Không tìm thấy Android snapshot bridge.';return;}var s=document.getElementById('status');if(s)s.textContent='Gemini đang tạo snapshot…';Android.requestSnapshot(JSON.stringify(state));}window.requestSnapshot=requestSnapshot;var oldRender=window.render;if(typeof oldRender==='function'){window.render=function(){oldRender();renderSnapshot();scrollBottom();};}var actions=document.querySelector('.actions');if(actions&&!document.getElementById('snapshotButton')){var b=document.createElement('button');b.id='snapshotButton';b.type='button';b.textContent='Tạo Snapshot';b.addEventListener('click',requestSnapshot);var wide=actions.querySelector('.wide');if(wide)actions.insertBefore(b,wide);else actions.appendChild(b);}var oldTurn=window.backroomTurn;window.backroomTurn=function(json){if(typeof oldTurn==='function')oldTurn(json);document.querySelectorAll('[data-pending="1"]').forEach(function(n){n.remove();});var s=document.getElementById('status');if(s)s.textContent='Turn '+state.turn+' đã lưu trên máy. Đang tạo snapshot…';renderSnapshot();scrollBottom();requestSnapshot();};var oldError=window.backroomError;window.backroomError=function(message){document.querySelectorAll('[data-pending="1"]').forEach(function(n){n.remove();});if(typeof oldError==='function')oldError(message);scrollBottom();};window.backroomSnapshot=function(payload){try{var r=JSON.parse(payload);if(!state||Number(r.turn)!==Number(state.turn))return;if(!r.dataUri)return;if(window.SaveBoot&&typeof SaveBoot.saveSnapshot==='function')SaveBoot.saveSnapshot({turn:r.turn,model:r.model||'Gemini',dataUri:r.dataUri});renderSnapshot();var s=document.getElementById('status');if(s)s.textContent='Snapshot Turn '+state.turn+' đã tạo bằng '+(r.model||'Gemini')+'.';}catch(e){var s=document.getElementById('status');if(s)s.textContent='Snapshot trả về không hợp lệ.';}};window.backroomSnapshotError=function(payload){try{var r=JSON.parse(payload);if(state&&Number(r.turn)!==Number(state.turn))return;var s=document.getElementById('status');if(s)s.textContent='Snapshot lỗi: '+(r.message||'Không thể tạo ảnh.');}catch(e){var s=document.getElementById('status');if(s)s.textContent='Snapshot lỗi.';}};var f=document.getElementById('form');if(f){f.addEventListener('submit',function(){var a=document.getElementById('action');var text=a?a.value.trim():'';if(!text)return;var l=document.getElementById('log');if(!l)return;var player=document.createElement('article');player.className='message player pending';player.setAttribute('data-pending','1');player.innerHTML='<div class="role">BẠN</div><div class="text"></div>';player.querySelector('.text').textContent=text;l.appendChild(player);var gm=document.createElement('article');gm.className='message pending';gm.setAttribute('data-pending','1');gm.innerHTML='<div class="role">GAME MASTER</div><div class="text">Đang xử lý lượt…</div>';l.appendChild(gm);scrollBottom();},true);}renderSnapshot();scrollBottom();if(typeof state!=='undefined'&&state)cachedSnapshot(function(r){if(!r)setTimeout(requestSnapshot,700);});})();
