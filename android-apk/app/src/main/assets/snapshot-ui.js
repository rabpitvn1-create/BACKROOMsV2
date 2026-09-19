/* Shared sprite geometry. Logical bounds ignore faint export residue/shadows;
 * paint bounds retain translucent hair, weapons and clothing for safe-area fitting. */
var SnapshotOverlayLayout = (function(){
  var CHARACTER_HEIGHT=0.84,GROUND=0.92,SIDE_MARGIN=0.025,SAFE_EDGE=0.02;
  var ENTITY_LANE_WIDTH=0.46;
  function bounds(pixels,width,height){
    var paint={left:width,top:height,right:0,bottom:0};
    var body={left:width,top:height,right:0,bottom:0};
    function include(b,x,y){b.left=Math.min(b.left,x);b.top=Math.min(b.top,y);b.right=Math.max(b.right,x+1);b.bottom=Math.max(b.bottom,y+1);}
    for(var y=0;y<height;y++)for(var x=0;x<width;x++){
      var alpha=pixels[(y*width+x)*4+3];
      if(alpha>8)include(paint,x,y);
      if(alpha>128)include(body,x,y);
    }
    if(paint.right<=paint.left||paint.bottom<=paint.top)return null;
    if(body.right<=body.left||body.bottom<=body.top)body=paint;
    return {paint:paint,body:body,width:width,height:height};
  }
  function envelope(metrics){
    return metrics.reduce(function(e,m){
      var h=m.body.bottom-m.body.top;
      e.width=Math.max(e.width,(m.paint.right-m.paint.left)/h);
      e.above=Math.max(e.above,(m.body.bottom-m.paint.top)/h);
      e.below=Math.max(e.below,(m.paint.bottom-m.body.bottom)/h);
      return e;
    },{width:0,above:1,below:0});
  }
  function layout(metric,width,height,side,category,family){
    if(!metric||!(width>0&&height>0))return null;
    var b=metric.body,p=metric.paint,h=b.bottom-b.top;
    var e=category==='character'?family:envelope([metric]);
    // A whole-snapshot width budget is shared by ALL Character poses. Narrow
    // viewports reduce the family together, never only the pose with a rifle.
    var target=Math.min(height*CHARACTER_HEIGHT,width*(category==='character'?1-2*SIDE_MARGIN:ENTITY_LANE_WIDTH)/e.width,height*(GROUND-SAFE_EDGE)/e.above);
    if(e.below>0)target=Math.min(target,height*(1-GROUND-SAFE_EDGE)/e.below);
    var scale=target/h,ground=height*GROUND,margin=width*SIDE_MARGIN;
    return {scale:scale,width:metric.width*scale,height:metric.height*scale,
      top:ground-b.bottom*scale,
      left:side==='right'?width-margin-p.right*scale:margin-p.left*scale,
      baseline:ground,bodyHeight:target};
  }
  return {bounds:bounds,envelope:envelope,layout:layout};
})();
if(typeof module!=='undefined'&&module.exports)module.exports=SnapshotOverlayLayout;
(function(){
  if(typeof document==='undefined')return;
  if(window.__backroomEnhancements)return;
  window.__backroomEnhancements=true;
  var st=document.createElement('style');
  st.textContent='button{transition:transform 80ms ease,background 120ms ease,border-color 120ms ease;touch-action:manipulation;-webkit-tap-highlight-color:rgba(255,255,255,.12)}button:active:not(:disabled){transform:scale(.965);background:#303840;border-color:#77828c}button:disabled{opacity:.48;cursor:not-allowed}.snapshot-placeholder{display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}.snapshot-placeholder b{font-size:12px;letter-spacing:.16em}.snapshot-placeholder small{color:#56616a}.message.pending{opacity:.72}.message.pending .text{color:#aeb7be}.snapshot{position:relative;overflow:hidden}.snapshot>img.snapshot-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;z-index:1}.snapshot>img.snapshot-map{object-fit:contain;background:#050607}.snapshot>img.snapshot-grounded{position:absolute;left:0;top:0;width:auto;height:auto;max-width:none;max-height:none;object-fit:contain;pointer-events:none;transform:none;visibility:hidden}.snapshot>img.snapshot-character{z-index:2;filter:drop-shadow(0 0 10px rgba(0,0,0,.45))}.snapshot>img.snapshot-entity{z-index:3;filter:drop-shadow(0 0 10px rgba(0,0,0,.55))}.snapshot-character-placeholder{position:absolute;right:2.5%;bottom:8%;width:38%;height:84%;z-index:2;pointer-events:none;opacity:.46;filter:drop-shadow(0 0 10px rgba(0,0,0,.5));animation:combat-overlay-enter .18s ease-out}.snapshot-character-placeholder:before{content:"";position:absolute;left:50%;top:2%;width:27%;aspect-ratio:1;border-radius:50%;transform:translateX(-50%);background:#fff}.snapshot-character-placeholder:after{content:"";position:absolute;left:13%;right:13%;bottom:0;height:78%;background:#fff;clip-path:polygon(38% 0,62% 0,72% 10%,82% 25%,88% 51%,76% 100%,24% 100%,12% 51%,18% 25%,28% 10%);border-radius:18% 18% 9% 9%}.snapshot-combat-character{animation:combat-overlay-enter .18s ease-out}.combat-hit-flash{animation:combat-hit-flash .14s ease-out!important}.combat-float{position:absolute;z-index:8;pointer-events:none;transform:translate(-50%,0);font-family:Play,system-ui,sans-serif;font-weight:700;font-size:20px;color:#fff;white-space:nowrap;text-shadow:0 2px 3px #000,0 0 6px #000;animation:combat-float-up .86s ease-out forwards}@keyframes combat-hit-flash{0%,100%{opacity:1}50%{filter:brightness(0) invert(1) drop-shadow(0 0 8px #fff);opacity:1}}@keyframes combat-float-up{0%{opacity:0;transform:translate(-50%,8px) scale(.96)}12%{opacity:1}75%{opacity:1}100%{opacity:0;transform:translate(-50%,-34px) scale(1.04)}}@keyframes combat-overlay-enter{from{opacity:0}to{opacity:1}}.snapshot>img.snapshot-chest{position:absolute;left:50%;bottom:-5%;transform:translateX(-50%);width:auto;max-width:58%;height:92%;object-fit:contain;object-position:center bottom;z-index:3;pointer-events:none;filter:drop-shadow(0 10px 16px rgba(0,0,0,.65))}';
  document.head.appendChild(st);
  var __overlayBoundsCache={};
  var __characterFamily=null;
  function spriteMetric(img){
    var src=img.currentSrc||img.src;
    if(__overlayBoundsCache[src])return __overlayBoundsCache[src];
    try{
      var canvas=document.createElement('canvas');
      canvas.width=img.naturalWidth;canvas.height=img.naturalHeight;
      var ctx=canvas.getContext('2d',{willReadFrequently:true});
      ctx.drawImage(img,0,0);
      var metric=SnapshotOverlayLayout.bounds(ctx.getImageData(0,0,canvas.width,canvas.height).data,canvas.width,canvas.height);
      if(metric)__overlayBoundsCache[src]=metric;
      return metric;
    }catch(error){
      console.warn('Cannot measure overlay alpha bounds',src,error);
      return null;
    }
  }
  function alignOverlayToGround(img,side,category){
    img.dataset.groundSide=side;img.dataset.overlayCategory=category;
    function apply(){
      var box=img.parentElement;
      if(!box||!img.complete||!img.naturalWidth||!__characterFamily)return;
      var metric=spriteMetric(img);
      var result=SnapshotOverlayLayout.layout(metric,box.clientWidth,box.clientHeight,side,category,__characterFamily);
      if(!result)return;
      ['width','height','top','left'].forEach(function(key){img.style[key]=result[key]+'px';});
      var bounds=metric.paint;
      ['left','top','right','bottom'].forEach(function(key){img.dataset['visible'+key[0].toUpperCase()+key.slice(1)+'Px']=String(bounds[key]*result.scale);});
      img.style.visibility='visible';
    }
    if(img.complete&&img.naturalWidth)apply();else img.addEventListener('load',apply,{once:true});
  }
  function alignPlaceholder(el){
    var box=el.parentElement;if(!box||!__characterFamily)return;
    var metric={width:1,height:1,body:{left:0,top:0,right:1,bottom:1},paint:{left:0,top:0,right:1,bottom:1}};
    var result=SnapshotOverlayLayout.layout(metric,box.clientWidth,box.clientHeight,'right','character',__characterFamily);
    if(result)el.style.height=result.bodyHeight+'px';
  }
  function realignGroundedOverlays(){
    var box=document.getElementById('snapshot');if(!box)return;
    box.querySelectorAll('.snapshot-character-placeholder').forEach(alignPlaceholder);
    box.querySelectorAll('img.snapshot-grounded').forEach(function(img){alignOverlayToGround(img,img.dataset.groundSide||'left',img.dataset.overlayCategory||'entity');});
  }
  function prepareCharacterFamily(){
    var sources=['file:///android_asset/kai_snapshot_overlay.png'];
    Object.keys(__combatCharacterOverlays).forEach(function(id){var src=__combatCharacterOverlays[id];if(sources.indexOf(src)<0)sources.push(src);});
    var remaining=sources.length,metrics=[];
    sources.forEach(function(src){
      var image=new Image();
      function finish(){
        var metric=image.naturalWidth?spriteMetric(image):null;
        if(metric)metrics.push(metric);
        if(--remaining===0){__characterFamily=SnapshotOverlayLayout.envelope(metrics);realignGroundedOverlays();}
      }
      image.onload=finish;image.onerror=finish;image.src=src;
    });
  }
  function scrollBottom(){var l=document.getElementById('log');if(l)requestAnimationFrame(function(){l.scrollTop=l.scrollHeight;});}
  try{localStorage.removeItem('backroom-apk-snapshot');}catch(_){}
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
  function combatVisualParticipant(){try{var c=state&&state.combat;if(!c||!Array.isArray(c.participants)||!c.participants.length)return null;var idx;if(Number.isInteger(window.__combatVisualActorIndex)){idx=window.__combatVisualActorIndex;}else{var currentId=normalizeActorId(c.currentActor||'');if(currentId){for(var i=0;i<c.participants.length;i++){var candidate=c.participants[i];if(candidate&&normalizeActorId(candidate.id||candidate.name)===currentId)return candidate;}}idx=Number(c.actorIndex||0);}if(idx<0||idx>=c.participants.length)idx=0;return c.participants[idx]||null;}catch(_){return null;}}
  function appendCombatCharacter(box,participant){
    var actor=participant||{id:'kai',name:'Kai Akechi'},id=normalizeActorId(actor.id||actor.name),src=__combatCharacterOverlays[id]||'';
    if(src){
      var img=document.createElement('img');img.className='snapshot-character snapshot-grounded snapshot-combat-character';img.src=src;img.alt=actor.name||actor.id||'Nhân vật';img.dataset.combatActor=id;box.appendChild(img);alignOverlayToGround(img,'right','character');return img;
    }
    var placeholder=document.createElement('div');placeholder.className='snapshot-character-placeholder snapshot-combat-character';placeholder.setAttribute('role','img');placeholder.setAttribute('aria-label',actor.name||actor.id||'Nhân vật');box.appendChild(placeholder);alignPlaceholder(placeholder);return placeholder;
  }
  function appendSnapshotOverlay(box){
    var key=activeEntityKey(),img;
    if(key){
      appendCombatCharacter(box,combatVisualParticipant());
      img=document.createElement('img');img.className='snapshot-entity snapshot-grounded';img.src='file:///android_asset/entity/'+key+'.png';img.alt=key;box.appendChild(img);alignOverlayToGround(img,'left','entity');return;
    }
    if(chestPresent()){img=document.createElement('img');img.className='snapshot-chest';img.src='file:///android_asset/chest_overlay.png';img.alt='Rương';box.appendChild(img);return;}
    if(shouldShowKaiOverlay()){
      img=document.createElement('img');img.className='snapshot-character snapshot-grounded';img.src='file:///android_asset/kai_snapshot_overlay.png';img.alt='Kai Akechi';box.appendChild(img);alignOverlayToGround(img,'right','character');
    }
  }
  function renderSnapshot(){var box=document.getElementById('snapshot');if(!box)return;box.textContent='';var local=localLevelSnapshot();if(local&&local.path){var img=document.createElement('img');img.className='snapshot-bg'+(local.visualType==='map'?' snapshot-map':'');img.src=local.path;img.alt='Level '+local.level+' Snapshot';box.appendChild(img);}else{var p=document.createElement('div');p.className='snapshot-placeholder';p.innerHTML='<b>LEVEL SNAPSHOT</b><small>Không có ảnh local cho Level hiện tại.</small>';box.appendChild(p);}appendSnapshotOverlay(box);}
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
  var oldTurn=window.backroomTurn;window.backroomTurn=function(json){if(typeof oldTurn==='function')oldTurn(json);document.querySelectorAll('[data-pending="1"]').forEach(function(n){n.remove();});var s=document.getElementById('status');if(s)s.textContent='Turn '+state.turn+' đã lưu trên máy.';renderSnapshot();};
  var f=document.getElementById('form');if(f){f.addEventListener('submit',function(){if(state&&state.combat&&state.combat.active)return;var a=document.getElementById('action');var text=a?a.value.trim():'';if(!text)return;var l=document.getElementById('log');if(!l)return;var player=document.createElement('article');player.className='message player pending';player.setAttribute('data-pending','1');player.innerHTML='<div class="role">BẠN</div><div class="text"></div>';player.querySelector('.text').textContent=text;l.appendChild(player);var gm=document.createElement('article');gm.className='message pending';gm.setAttribute('data-pending','1');gm.innerHTML='<div class="role">GAME MASTER</div><div class="text">Đang xử lý lượt…</div>';l.appendChild(gm);scrollBottom();},true);}
  var __groundResizeTimer=0;
  window.addEventListener('resize',function(){clearTimeout(__groundResizeTimer);__groundResizeTimer=setTimeout(realignGroundedOverlays,80);});
  if(typeof ResizeObserver!=='undefined'){
    var snapshotBox=document.getElementById('snapshot');
    if(snapshotBox)new ResizeObserver(realignGroundedOverlays).observe(snapshotBox);
  }
  prepareCharacterFamily();
  renderSnapshot();
})();
