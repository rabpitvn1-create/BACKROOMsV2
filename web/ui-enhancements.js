/*
 * ui-enhancements.js — static recreation of the UI that the APK's
 * MainActivity.installUiEnhancements() injects via evaluateJavascript at
 * onPageFinished. The payload below mirrors the Java string in
 * MainActivity.java function for function, with the adaptations listed
 * here — nothing else differs from the native payload.
 *
 * Load order: this file must load AFTER the game's inline <script> in
 * index.html (the assembler's job). That is the static equivalent of
 * onPageFinished: the page globals the payload wraps or reads (state,
 * render) already exist.
 * (Adaptation A1 — timing only, no code change.)
 *
 * Adaptation A3 — owner-requested cosmetic (2026-11-03, web only): the
 * injected <style> gains one rule the native payload does not have —
 * #snapshot .stage-bg{border-radius:20px} — so the snapshot stage's
 * background frame renders with 20 px rounded corners. Since the VN
 * stage port (2026-11-09) only .stage-bg is clipped/rounded (Hui rule);
 * the figures above it are never clipped. The APK frame is unchanged.
 *
 * Offline payload (parity with native): there is no snapshot image
 * machinery at all — no cache, no generated art, no bridge call. The VN
 * stage above the story panel IS the snapshot (level background +
 * figures from local assets), so window.requestSnapshot is the purely
 * local showSnapshot: it points the status line at the stage and
 * scrolls it into view. The "Tạo Snapshot" button calls exactly that.
 * The fixed-scene VN engine runs entirely inside the page, so the
 * payload no longer carries any turn machinery: the pending bubbles,
 * the backroomTurn/backroomError wrappers and the form-submit hook of
 * the free-action/GM model were removed on both sides.
 *
 * Globals: this file defines exactly the globals the native payload
 * defines — window.__backroomEnhancements (its double-injection guard)
 * and window.requestSnapshot — and wraps the page's own window.render
 * the same way native does. No other names are introduced.
 *
 * The injected <style> travels inside the payload (the native code builds
 * it as a style element), so there is no separate .css file.
 */
(function(){if(window.__backroomEnhancements)return;window.__backroomEnhancements=true;var st=document.createElement('style');st.textContent='button{transition:transform 80ms ease,background 120ms ease,border-color 120ms ease;touch-action:manipulation;-webkit-tap-highlight-color:rgba(255,255,255,.12)}button:active:not(:disabled){transform:scale(.965);background:#303840;border-color:#77828c}button:disabled{opacity:.48;cursor:not-allowed}#snapshot .stage-bg{border-radius:20px}';document.head.appendChild(st);function scrollBottom(){var l=document.getElementById('log');if(l)requestAnimationFrame(function(){l.scrollTop=l.scrollHeight;});}function showSnapshot(){var s=document.getElementById('status');if(s)s.textContent='Snapshot hiển thị trực tiếp ở khung phía trên — nền theo Level, nhân vật và thực thể đều là ảnh trên máy.';var box=document.getElementById('snapshot');if(box&&box.scrollIntoView)box.scrollIntoView({block:'start'});}window.requestSnapshot=showSnapshot;var actions=document.querySelector('.actions');if(actions&&!document.getElementById('snapshotButton')){var b=document.createElement('button');b.id='snapshotButton';b.type='button';b.textContent='Tạo Snapshot';b.addEventListener('click',showSnapshot);var wide=actions.querySelector('.wide');if(wide)actions.insertBefore(b,wide);else actions.appendChild(b);}var oldRender=window.render;if(typeof oldRender==='function'){window.render=function(){oldRender();scrollBottom();};}scrollBottom();})();
