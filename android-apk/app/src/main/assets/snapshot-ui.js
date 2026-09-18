(function(){
  if(window.__backroomEnhancements)return;
  window.__backroomEnhancements=true;
  var st=document.createElement('style');
  st.textContent='button{transition:transform 80ms ease,background 120ms ease,border-color 120ms ease;touch-action:manipulation;-webkit-tap-highlight-color:rgba(255,255,255,.12)}button:active:not(:disabled){transform:scale(.965);background:#303840;border-color:#77828c}button:disabled{opacity:.48;cursor:not-allowed}.snapshot-placeholder{display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}.snapshot-placeholder b{font-size:12px;letter-spacing:.16em}.snapshot-placeholder small{color:#56616a}.message.pending{opacity:.72}.message.pending .text{color:#aeb7be}.snapshot{position:relative;overflow:hidden}.snapshot>img.snapshot-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;z-index:1}.snapshot>img.snapshot-map{object-fit:contain;background:#050607}.snapshot>img.snapshot-grounded{position:absolute;left:0;top:0;width:auto;height:auto;max-width:none;max-height:none;object-fit:fill;pointer-events:none;transform:none}.snapshot>img.snapshot-character{z-index:2;filter:drop-shadow(0 0 10px rgba(0,0,0,.45))}.snapshot>img.snapshot-entity{z-index:3;filter:drop-shadow(0 0 10px rgba(0,0,0,.55))}.snapshot-character-placeholder{position:absolute;right:2.5%;bottom:8%;width:42%;height:78%;z-index:2;pointer-events:none;opacity:.46;filter:drop-shadow(0 0 10px rgba(0,0,0,.5));animation:combat-overlay-enter .18s ease-out}.snapshot-character-placeholder:before{content:"";position:absolute;left:50%;top:2%;width:27%;aspect-ratio:1;border-radius:50%;transform:translateX(-50%);background:#fff}.snapshot-character-placeholder:after{content:"";position:absolute;left:13%;right:13%;bottom:0;height:78%;background:#fff;clip-path:polygon(38% 0,62% 0,72% 10%,82% 25%,88% 51%,76% 100%,24% 100%,12% 51%,18% 25%,28% 10%);border-radius:18% 18% 9% 9%}.snapshot-combat-character{animation:combat-overlay-enter .18s ease-out}.combat-hit-flash{animation:combat-hit-flash .14s ease-out!important}.combat-float{position:absolute;z-index:8;pointer-events:none;transform:translate(-50%,0);font-family:Play,system-ui,sans-serif;font-weight:700;font-size:20px;color:#fff;white-space:nowrap;text-shadow:0 2px 3px #000,0 0 6px #000;animation:combat-float-up .86s ease-out forwards}@keyframes combat-hit-flash{0%,100%{opacity:1}50%{filter:brightness(0) invert(1) drop-shadow(0 0 8px #fff);opacity:1}}@keyframes combat-float-up{0%{opacity:0;transform:translate(-50%,8px) scale(.96)}12%{opacity:1}75%{opacity:1}100%{opacity:0;transform:translate(-50%,-34px) scale(1.04)}}@keyframes combat-overlay-enter{from{opacity:0;transform:translateX(10px)}to{opacity:1;transform:translateX(0)}}.snapshot>img.snapshot-chest{position:absolute;left:50%;bottom:-5%;transform:translateX(-50%);width:auto;max-width:58%;height:92%;object-fit:contain;object-position:center bottom;z-index:3;pointer-events:none;filter:drop-shadow(0 10px 16px rgba(0,0,0,.65))}';
  document.head.appendChild(st);
  var VIRTUAL_GROUND_RATIO=0.92;
  var VIRTUAL_GROUND_SIDE_MARGIN=0.025;
  var __overlayBoundsCache={};
  function visibleAlphaBounds(img,done){
    var src=img.currentSrc||img.src||'';
    if(__overlayBoundsCache[src]){done(__overlayBoundsCache[src]);return;}
    try{
      var nw=Math.max(1,img.naturalWidth||1),nh=Math.max(1,img.naturalHeight||1),limit=192;
      var sample=Math.min(1,limit/nw,limit/nh),cw=Math.max(1,Math.round(nw*sample)),ch=Math.max(1,Math.round(nh*sample));
      var canvas=document.createElement('canvas');canvas.width=cw;canvas.height=ch;
      var ctx=canvas.getContext('2d',{willReadFrequently:true});ctx.clearRect(0,0,cw,ch);ctx.drawImage(img,0,0,cw,ch);
      var pixels=ctx.getImageData(0,0,cw,ch).data,minX=cw,minY=ch,maxX=-1,maxY=-1;
      for(var y=0;y<ch;y++){for(var x=0;x<cw;x++){if(pixels[(y*cw+x)*4+3]>8){if(x<minX)minX=x;if(x>maxX)maxX=x;if(y<minY)minY=y;if(y>maxY)maxY=y;}}}
      var inv=1/sample,bounds=maxX>=minX&&maxY>=minY?{left:minX*inv,top:minY*inv,right:(maxX+1)*inv,bottom:(maxY+1)*inv}:{left:0,top:0,right:nw,bottom:nh};
      __overlayBoundsCache[src]=bounds;done(bounds);
    }catch(_){
      var fallback={left:0,top:0,right:Math.max(1,img.naturalWidth||1),bottom:Math.max(1,img.naturalHeight||1)};
      __overlayBoundsCache[src]=fallback;done(fallback);
    }
  }
  function alignOverlayToGround(img,side,visibleHeightRatio,maxWidthRatio){
    function apply(){
      var box=img.parentElement;if(!box||!box.clientWidth||!box.clientHeight)return;
      visibleAlphaBounds(img,function(bounds){
        var bw=box.clientWidth,bh=box.clientHeight,visibleW=Math.max(1,bounds.right-bounds.left),visibleH=Math.max(1,bounds.bottom-bounds.top);
        var scale=Math.min((bh*visibleHeightRatio)/visibleH,(bw*maxWidthRatio)/visibleW);
        var groundY=bh*VIRTUAL_GROUND_RATIO,margin=bw*VIRTUAL_GROUND_SIDE_MARGIN;
        img.style.width=(Math.max(1,img.naturalWidth)*scale)+'px';
        img.style.height=(Math.max(1,img.naturalHeight)*scale)+'px';
        img.style.top=(groundY-bounds.bottom*scale)+'px';
        img.style.left=(side==='right'?bw-margin-bounds.right*scale:margin-bounds.left*scale)+'px';
        img.dataset.groundSide=side;img.dataset.groundHeight=String(visibleHeightRatio);img.dataset.groundWidth=String(maxWidthRatio);
        img.dataset.visibleLeftPx=String(bounds.left*scale);img.dataset.visibleTopPx=String(bounds.top*scale);img.dataset.visibleRightPx=String(bounds.right*scale);img.dataset.visibleBottomPx=String(bounds.bottom*scale);
      });
    }
    if(img.complete&&img.naturalWidth)apply();else img.addEventListener('load',apply,{once:true});
  }
  function realignGroundedOverlays(){
    var box=document.getElementById('snapshot');if(!box)return;
    box.querySelectorAll('img.snapshot-grounded').forEach(function(img){alignOverlayToGround(img,img.dataset.groundSide||'left',Number(img.dataset.groundHeight||0.90),Number(img.dataset.groundWidth||0.49));});
  }
  function scrollBottom(){var l=document.getElementById('log');if(l)requestAnimationFrame(function(){l.scrollTop=l.scrollHeight;});}
  function cachedSnapshot(){try{var r=JSON.parse(localStorage.getItem('backroom-apk-snapshot')||'null');return r&&Number(r.turn)===Number(state&&state.turn)&&r.dataUri?r:null;}catch(e){return null;}}
  function localLevelSnapshot(){try{if(!window.Android||typeof Android.levelSnapshot!=='function')return null;return JSON.parse(Android.levelSnapshot(JSON.stringify(state)));}catch(e){return null;}}
  var __entityKeys=['hound','clump','duller','deathmoth','hostile_faceling','false_puddle','paintings','smiler','skin-stealer','predatory_window','biological_pipeline','wretch','cable_mimic','the_beast_of_level_5','hotel_corpse_lure','jeff_the_killer','jane_the_killer','slenderman','diep_minh'];
  var __combatCharacterOverlays={kai:'file:///android_asset/kai_entity_overlay.png',lucia:'file:///android_asset/lucia_entity_overlay.png'};
  window.__combatVisualActorIndex=null;
  window.__combatVisualEntityKey='';
  function normalizeEntityKey(v){if(v===null||v===undefined)return '';var k=String(v).trim().toLowerCase().replace(/\s+/g,'_');if(k==='skin_stealer')k='skin-stealer';return __entityKeys.indexOf(k)>=0?k:'';}
  function activeEntityKey(){try{var forced=normalizeEntityKey(window.__combatVisualEntityKey||'');if(forced)return forced;var s=(typeof state!=='undefined'&&state)?state:{};var f=s.flags||{},c=s.combat||{};var combatKey=c.active?((c.entity&&c.entity.key)||c.entityKey||c.enemyKey||c.enemy||''):'';var k=normalizeEntityKey(f.entityEncounterKey||f.currentEntityKey||s.entityEncounterKey||s.currentEntityKey||combatKey);if(k)return k;if(f.jeff&&(f.jeff.present===true||f.jeff.spawned===true))return 'jeff_the_killer';if(f.jane&&(f.jane.present===true||f.jane.spawned===true))return 'jane_the_killer';return '';}catch(e){return '';}}
  function chestPresent(){try{var s=(typeof state!=='undefined'&&state)?state:{};return !!(s.flags&&s.flags.chestPresent===true);}catch(e){return false;}}
  function shouldShowKaiOverlay(){try{var s=(typeof state!=='undefined'&&state)?state:{};return !(s.specialMode||s.debug||activeEntityKey()||chestPresent());}catch(e){return false;}}
  function normalizeActorId(v){var k=String(v||'').trim().toLowerCase();if(k.indexOf('kai')>=0||k.indexOf('twilight')>=0)return 'kai';if(k.indexOf('lucia')>=0||k.indexOf('hứa thuý mai')>=0||k.indexOf('hua thuy mai')>=0)return 'lucia';if(k.indexOf('iris')>=0||k.indexOf('argus')>=0)return 'iris';if(k.indexOf('syvial')>=0)return 'syvial';return k.replace(/\s+/g,'_');}
  function combatVisualParticipant(){try{var c=state&&state.combat;if(!c||!Array.isArray(c.participants)||!c.participants.length)return null;var idx=Number.isInteger(window.__combatVisualActorIndex)?window.__combatVisualActorIndex:Number(c.actorIndex||0);if(idx<0||idx>=c.participants.length)idx=0;return c.participants[idx]||null;}catch(_){return null;}}
  function appendCombatCharacter(box,participant){
    var actor=participant||{id:'kai',name:'Kai Akechi'},id=normalizeActorId(actor.id||actor.name),src=__combatCharacterOverlays[id]||'';
    if(src){
      var img=document.createElement('img');img.className='snapshot-character snapshot-grounded snapshot-combat-character';img.src=src;img.alt=actor.name||actor.id||'Nhân vật';box.appendChild(img);alignOverlayToGround(img,'right',0.90,0.49);return img;
    }
    var placeholder=document.createElement('div');placeholder.className='snapshot-character-placeholder snapshot-combat-character';placeholder.setAttribute('role','img');placeholder.setAttribute('aria-label',actor.name||actor.id||'Nhân vật');box.appendChild(placeholder);return placeholder;
  }
  function appendSnapshotOverlay(box){
    var key=activeEntityKey(),img;
    if(key){
      appendCombatCharacter(box,combatVisualParticipant());
      img=document.createElement('img');img.className='snapshot-entity snapshot-grounded';img.src='file:///android_asset/entity/'+key+'.png';img.alt=key;box.appendChild(img);alignOverlayToGround(img,'left',0.90,0.49);return;
    }
    if(chestPresent()){img=document.createElement('img');img.className='snapshot-chest';img.src='file:///android_asset/chest_overlay.png';img.alt='Rương';box.appendChild(img);return;}
    if(shouldShowKaiOverlay()){
      img=document.createElement('img');img.className='snapshot-character snapshot-grounded';img.src='file:///android_asset/kai_snapshot_overlay.png';img.alt='Kai Akechi';box.appendChild(img);alignOverlayToGround(img,'right',0.90,0.58);
    }
  }
  function renderSnapshot(){var box=document.getElementById('snapshot');if(!box)return;box.textContent='';var r=cachedSnapshot();if(r){var img=document.createElement('img');img.className='snapshot-bg';img.src=r.dataUri;img.alt='Snapshot Turn '+(state.turn||'');box.appendChild(img);}else{var local=localLevelSnapshot();if(local&&local.path){var img=document.createElement('img');img.className='snapshot-bg'+(local.visualType==='map'?' snapshot-map':'');img.src=local.path;img.alt='Level '+local.level+' Snapshot';box.appendChild(img);}else{var p=document.createElement('div');p.className='snapshot-placeholder';p.innerHTML='<b>LEVEL SNAPSHOT</b><small>Không có ảnh cho Level hiện tại.</small>';box.appendChild(p);}}appendSnapshotOverlay(box);}
  function combatTargetElement(target){var box=document.getElementById('snapshot');if(!box)return null;return target==='entity'?box.querySelector('.snapshot-entity'):box.querySelector('.snapshot-combat-character');}
  function targetAnchor(target){
    var box=document.getElementById('snapshot'),el=combatTargetElement(target);if(!box||!el)return null;
    var br=box.getBoundingClientRect(),er=el.getBoundingClientRect(),x=er.left-br.left+er.width/2,y=er.top-br.top+er.height*.34;
    if(el.tagName==='IMG'&&el.dataset.visibleLeftPx){
      var left=Number(el.dataset.visibleLeftPx||0),top=Number(el.dataset.visibleTopPx||0),right=Number(el.dataset.visibleRightPx||el.clientWidth),bottom=Number(el.dataset.visibleBottomPx||el.clientHeight);
      x=er.left-br.left+(left+right)/2;y=er.top-br.top+top+(bottom-top)*.34;
    }
    return {box:box,el:el,x:x,y:y};
  }
  window.backroomSetCombatVisualActor=function(index,entityKey){window.__combatVisualActorIndex=Number(index);window.__combatVisualEntityKey=String(entityKey||'');renderSnapshot();};
  window.backroomClearCombatVisualActor=function(){window.__combatVisualActorIndex=null;window.__combatVisualEntityKey='';renderSnapshot();};
  window.backroomPlayCombatFeedback=function(event){
    try{
      var e=event||{},anchor=targetAnchor(e.target==='entity'?'entity':'actor');if(!anchor)return;
      if(e.flash){
        anchor.el.classList.remove('combat-hit-flash');void anchor.el.offsetWidth;anchor.el.classList.add('combat-hit-flash');
        setTimeout(function(){anchor.el&&anchor.el.classList.remove('combat-hit-flash');},170);
      }
      var text=String(e.text||'').trim();if(!text)return;
      var floater=document.createElement('div');floater.className='combat-float';floater.textContent=text;floater.style.left=anchor.x+'px';floater.style.top=anchor.y+'px';anchor.box.appendChild(floater);
      floater.addEventListener('animationend',function(){floater.remove();},{once:true});setTimeout(function(){floater.remove();},1000);
    }catch(_){}
  };
  function requestSnapshot(){if(!window.Android||typeof Android.requestSnapshot!=='function'){var s=document.getElementById('status');if(s)s.textContent='Không tìm thấy Android snapshot bridge.';return;}var s=document.getElementById('status');if(s)s.textContent='Gemini đang tạo snapshot…';Android.requestSnapshot(JSON.stringify(state));}
  window.requestSnapshot=requestSnapshot;
  var oldRender=window.render;if(typeof oldRender==='function'){window.render=function(){oldRender();renderSnapshot();scrollBottom();};}
  var actions=document.querySelector('.actions');if(actions&&!document.getElementById('snapshotButton')){var b=document.createElement('button');b.id='snapshotButton';b.type='button';b.textContent='Tạo Snapshot';b.addEventListener('click',requestSnapshot);var wide=actions.querySelector('.wide');if(wide)actions.insertBefore(b,wide);else actions.appendChild(b);}
  var oldTurn=window.backroomTurn;window.backroomTurn=function(json){var before=Number(state&&state.turn||0);if(typeof oldTurn==='function')oldTurn(json);document.querySelectorAll('[data-pending="1"]').forEach(function(n){n.remove();});var s=document.getElementById('status');if(s)s.textContent='Turn '+state.turn+' đã lưu trên máy.';renderSnapshot();scrollBottom();if(Number(state&&state.turn||0)!==before)requestSnapshot();};
  var oldError=window.backroomError;window.backroomError=function(message){document.querySelectorAll('[data-pending="1"]').forEach(function(n){n.remove();});if(typeof oldError==='function')oldError(message);scrollBottom();};
  window.backroomSnapshot=function(payload){try{var r=JSON.parse(payload);if(!state||Number(r.turn)!==Number(state.turn))return;if(!r.dataUri)return;localStorage.setItem('backroom-apk-snapshot',JSON.stringify({turn:r.turn,model:r.model||'Gemini',dataUri:r.dataUri}));renderSnapshot();var s=document.getElementById('status');if(s)s.textContent='Snapshot Turn '+state.turn+' đã tạo bằng '+(r.model||'Gemini')+'.';}catch(e){var s=document.getElementById('status');if(s)s.textContent='Snapshot trả về không hợp lệ.';}};
  window.backroomSnapshotError=function(payload){try{var r=JSON.parse(payload);if(state&&Number(r.turn)!==Number(state.turn))return;var s=document.getElementById('status');if(s)s.textContent='Snapshot lỗi: '+(r.message||'Không thể tạo ảnh.');}catch(e){var s=document.getElementById('status');if(s)s.textContent='Snapshot lỗi.';}};
  var f=document.getElementById('form');if(f){f.addEventListener('submit',function(){if(state&&state.combat&&state.combat.active)return;var a=document.getElementById('action');var text=a?a.value.trim():'';if(!text)return;var l=document.getElementById('log');if(!l)return;var player=document.createElement('article');player.className='message player pending';player.setAttribute('data-pending','1');player.innerHTML='<div class="role">BẠN</div><div class="text"></div>';player.querySelector('.text').textContent=text;l.appendChild(player);var gm=document.createElement('article');gm.className='message pending';gm.setAttribute('data-pending','1');gm.innerHTML='<div class="role">GAME MASTER</div><div class="text">Đang xử lý lượt…</div>';l.appendChild(gm);scrollBottom();},true);}
  var __groundResizeTimer=0;
  window.addEventListener('resize',function(){clearTimeout(__groundResizeTimer);__groundResizeTimer=setTimeout(realignGroundedOverlays,80);});
  renderSnapshot();scrollBottom();if(typeof state!=='undefined'&&state&&!cachedSnapshot())setTimeout(requestSnapshot,700);
})();
