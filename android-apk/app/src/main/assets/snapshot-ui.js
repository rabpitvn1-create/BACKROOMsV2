/* Shared sprite geometry. Logical bounds ignore faint export residue/shadows;
 * paint bounds retain translucent hair, weapons and clothing for safe-area fitting. */
var SnapshotOverlayLayout = (function(){
  var CHARACTER_HEIGHT=0.84,GROUND=0.92,SIDE_MARGIN=0.025,SAFE_EDGE=0.02;
  var ENTITY_LANE_WIDTH=0.46;
  // BEGIN GENERATED OVERLAY METRICS
  var bundledMetrics={
    "cao_minh_entity_overlay.png":{"width":1122,"height":1402,"paint":{"left":1,"top":0,"right":1122,"bottom":1386},"body":{"left":2,"top":3,"right":1122,"bottom":1376},"sha256":"980aaaf8a8575d41ab66b95d8ab24ca7052b66677a1a15e0eb6433c8b716eb47"},
    "cao_minh_snapshot_overlay.png":{"width":1122,"height":1402,"paint":{"left":54,"top":16,"right":1020,"bottom":1389},"body":{"left":54,"top":17,"right":1017,"bottom":1385},"sha256":"e02f27c3125c3c4c3c3eb4b4314c93bab89d0ac18c92f8038ee2533d616bbd6e"},
    "entity/async_rifleman.webp":{"width":941,"height":1672,"paint":{"left":140,"top":66,"right":841,"bottom":1623},"body":{"left":142,"top":67,"right":840,"bottom":1599},"sha256":"bd0b726251943b8f6afb18a5f2896bdbe44a8ad70c88bb18d50b2b075cf5621d"},
    "entity/biological_pipeline.webp":{"width":1536,"height":1024,"paint":{"left":26,"top":0,"right":1532,"bottom":1004},"body":{"left":53,"top":4,"right":1530,"bottom":1000},"sha256":"e3e7a003eb080aefc57d7963924d9cd8f610db40ada84b9f3ae1d226823589ae"},
    "entity/cable_mimic.webp":{"width":1536,"height":1024,"paint":{"left":8,"top":10,"right":1527,"bottom":1004},"body":{"left":9,"top":11,"right":1526,"bottom":997},"sha256":"1bb78a1e792e6e0a99beb62648e3f06d609be690b5fde0df7de71a9d502b3071"},
    "entity/clump.webp":{"width":1122,"height":1402,"paint":{"left":45,"top":51,"right":1093,"bottom":1304},"body":{"left":50,"top":52,"right":1090,"bottom":1294},"sha256":"8bca2d3b2adde8e48475e3d374bd6ce99a621b8b7aa2e2648a0760175b625673"},
    "entity/copx.webp":{"width":900,"height":1200,"paint":{"left":14,"top":1,"right":893,"bottom":1190},"body":{"left":18,"top":2,"right":892,"bottom":1187},"sha256":"0054ece794361d86ab4bad071848682bff5a29c78ff091a9732fa55a5312258e"},
    "entity/deathmoth.webp":{"width":1122,"height":1402,"paint":{"left":25,"top":42,"right":1122,"bottom":1366},"body":{"left":26,"top":42,"right":1117,"bottom":1364},"sha256":"c4d27d59aae7491304464df044b783ba63f172bbee75d0fb5bd680f15311a7fb"},
    "entity/diep_minh.webp":{"width":960,"height":1280,"paint":{"left":1,"top":24,"right":956,"bottom":1255},"body":{"left":6,"top":26,"right":954,"bottom":1177},"sha256":"2346392ac68864da3ef2e764f78e6042ec76623dba2954a8e08b80d8077794c1"},
    "entity/duller.webp":{"width":1024,"height":1536,"paint":{"left":251,"top":14,"right":726,"bottom":1525},"body":{"left":384,"top":15,"right":652,"bottom":1500},"sha256":"91f7d14aaf69b03a4ff10f39ebed5a7a42d6353e1b225ec2518e9af51621ef97"},
    "entity/false_puddle.webp":{"width":1536,"height":1024,"paint":{"left":1,"top":0,"right":1536,"bottom":1024},"body":{"left":4,"top":0,"right":1535,"bottom":1024},"sha256":"2437b611b1287edef5bf6e78c96dcdce2e0d8673d5948616b70ed06e1c1ebbe6"},
    "entity/hostile_faceling.webp":{"width":1024,"height":1536,"paint":{"left":20,"top":40,"right":1007,"bottom":1491},"body":{"left":20,"top":42,"right":1006,"bottom":1477},"sha256":"243b74d7e9d733cf95b791e0b44a615359885de2b61ae012d6aed2b00df9fa89"},
    "entity/hotel_corpse_lure.webp":{"width":1086,"height":1448,"paint":{"left":108,"top":0,"right":1085,"bottom":1415},"body":{"left":155,"top":4,"right":1084,"bottom":1409},"sha256":"637c49276672b06c8ebe0858720a1e22a5bf1723c5aa21ec72d9649567fd5110"},
    "entity/hound.webp":{"width":1122,"height":1402,"paint":{"left":23,"top":185,"right":1118,"bottom":1234},"body":{"left":27,"top":187,"right":1116,"bottom":1233},"sha256":"678e404efd0fe9f7c2955fac92627810e9b4e637609d25f1aad2719585bddea4"},
    "entity/jane_the_killer.webp":{"width":1122,"height":1402,"paint":{"left":77,"top":8,"right":1099,"bottom":1391},"body":{"left":104,"top":9,"right":1098,"bottom":1382},"sha256":"dd5b51f4e1d8437bb21a57ac1b9cfc0f297f284b9b90e419eb337913d5122dc1"},
    "entity/jeff_the_killer.webp":{"width":1536,"height":1024,"paint":{"left":37,"top":8,"right":1529,"bottom":1006},"body":{"left":82,"top":12,"right":1528,"bottom":994},"sha256":"d66ddd290ad1ac4c54271a2288305282c8d121056214c23c7c8ccdb2086bebaf"},
    "entity/paintings.webp":{"width":1536,"height":1024,"paint":{"left":97,"top":0,"right":1497,"bottom":990},"body":{"left":109,"top":0,"right":1496,"bottom":976},"sha256":"d31655663e99b4837f0abb06dc24c7783ff8ebe8c0a958e915f46f2051a0a116"},
    "entity/predatory_window.webp":{"width":1122,"height":1402,"paint":{"left":4,"top":61,"right":1117,"bottom":1304},"body":{"left":7,"top":62,"right":1114,"bottom":1280},"sha256":"f38b98e7cc07db147f105d8d88e7dbcc5f12965ed26e41fb8c0e0657d23e227a"},
    "entity/skin-stealer.webp":{"width":1024,"height":1536,"paint":{"left":33,"top":34,"right":941,"bottom":1474},"body":{"left":80,"top":35,"right":940,"bottom":1472},"sha256":"03d39e948bf69d73f6e6e92cb28b249ba3e042e3b532a62f95dd829dd6c30ee5"},
    "entity/slenderman.webp":{"width":1086,"height":1448,"paint":{"left":13,"top":0,"right":1086,"bottom":1431},"body":{"left":19,"top":6,"right":1085,"bottom":1413},"sha256":"4774a9598a32d2b7890f50ce93e8433efa99400c5ebe6dafc42accb5c8e4e661"},
    "entity/smiler.webp":{"width":1086,"height":1448,"paint":{"left":277,"top":10,"right":863,"bottom":1442},"body":{"left":314,"top":14,"right":777,"bottom":1421},"sha256":"6d968594d465011857330d41044f7bee04705b21ca0260bd8942f3c5afd40132"},
    "entity/tam_ma_cao_minh.webp":{"width":768,"height":960,"paint":{"left":2,"top":5,"right":768,"bottom":940},"body":{"left":3,"top":6,"right":764,"bottom":937},"sha256":"723ce2b7db8ae6e0407eb80ed9727428ca08439433fd577c2b6667691dfdd482"},
    "entity/the_beast_of_level_5.webp":{"width":1536,"height":1024,"paint":{"left":67,"top":0,"right":1535,"bottom":1007},"body":{"left":68,"top":2,"right":1532,"bottom":987},"sha256":"9f572f22c8ed63efa04e7f170202ad65ac4ba9d0f3e514a09c7b9faca83dc9a9"},
    "entity/wretch.webp":{"width":1086,"height":1448,"paint":{"left":11,"top":19,"right":1079,"bottom":1433},"body":{"left":28,"top":20,"right":1071,"bottom":1424},"sha256":"396842e5974b097c5817aadbf68034fe9e46bc47347628c7c8e7110cc6a69307"},
    "luctram_overlay.png":{"width":1024,"height":1536,"paint":{"left":2,"top":0,"right":1016,"bottom":1482},"body":{"left":3,"top":4,"right":1016,"bottom":1469},"sha256":"838a4d6b14797aa1e906789725de1a67dbeb176f7ca4ea4e6155c17a07c78d4b"}
  };
  // END GENERATED OVERLAY METRICS
  function assetMetric(src){
    var clean=String(src||'').split(/[?#]/)[0],marker=clean.lastIndexOf('/entity/');
    var key=marker>=0?'entity/'+clean.slice(marker+8):clean.slice(clean.lastIndexOf('/')+1);
    return bundledMetrics[key]||null;
  }
  function characterMetrics(){
    return Object.keys(bundledMetrics).filter(function(key){return key.indexOf('entity/')!==0;}).map(function(key){return bundledMetrics[key];});
  }
  function canvasMetric(width,height){
    var box={left:0,top:0,right:width,bottom:height};
    return {width:width,height:height,paint:box,body:box};
  }
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
  function floorKey(levelKey,level){
    var key=String(levelKey==null?'':levelKey).trim().toLowerCase().replace(/[\s-]+/g,'_');
    var sublevels={
      '0.1':'sublevel_0_1',
      '0.2':'sublevel_0_2',
      '0.5':'sublevel_0_5',
      '0.7':'sublevel_0_7',
      'manila_room':'sublevel_manila_room',
      'the_torment':'sublevel_the_torment',
      'red_rooms':'sublevel_red_rooms'
    };
    if(sublevels[key])return sublevels[key];
    var numeric=Number(level);
    if(Number.isInteger(numeric)&&numeric>=0&&numeric<=6)return 'level_'+numeric;
    var main=key.match(/^(?:level_)?([0-6])$/);
    return main?'level_'+main[1]:'level_0';
  }
  function swordGlowForFloor(key){
    return key==='level_6'||key==='sublevel_the_torment'||key==='sublevel_red_rooms';
  }
  return {bounds:bounds,envelope:envelope,layout:layout,assetMetric:assetMetric,canvasMetric:canvasMetric,characterMetrics:characterMetrics,floorKey:floorKey,swordGlowForFloor:swordGlowForFloor};
})();
if(typeof module!=='undefined'&&module.exports)module.exports=SnapshotOverlayLayout;
(function(){
  if(typeof document==='undefined')return;
  if(window.__backroomEnhancements)return;
  window.__backroomEnhancements=true;
  var st=document.createElement('style');
  st.textContent='button{transition:transform 80ms ease,background 120ms ease,border-color 120ms ease;touch-action:manipulation;-webkit-tap-highlight-color:rgba(255,255,255,.12)}button:active:not(:disabled){transform:scale(.965);background:#303840;border-color:#77828c}button:disabled{opacity:.48;cursor:not-allowed}.snapshot-placeholder{display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}.snapshot-placeholder b{font-size:12px;letter-spacing:.16em}.snapshot-placeholder small{color:#56616a}.message.pending{opacity:.72}.message.pending .text{color:#aeb7be}.snapshot{position:relative;overflow:hidden;isolation:isolate}.snapshot>img.snapshot-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;z-index:1}.snapshot>img.snapshot-floor{position:absolute;left:0;right:0;bottom:0;width:100%;height:18%;object-fit:cover;object-position:center bottom;z-index:1;pointer-events:none}.snapshot-sword-glow{position:absolute;right:5%;bottom:3%;width:34%;height:15%;z-index:1;pointer-events:none;border-radius:50%;background:radial-gradient(ellipse at center,rgba(255,250,244,.78) 0%,rgba(255,244,238,.5) 22%,rgba(221,72,57,.24) 48%,rgba(140,22,20,.08) 68%,rgba(0,0,0,0) 100%);filter:blur(1.5px);opacity:.82}.snapshot>img.snapshot-map{object-fit:contain;background:#050607}.snapshot>img.snapshot-grounded{position:absolute;left:auto;right:2.5%;top:8%;width:auto;height:84%;max-width:95%;max-height:none;object-fit:contain;pointer-events:none;transform:none}.snapshot>img.snapshot-character{z-index:2;filter:drop-shadow(0 0 10px rgba(0,0,0,.45))}.snapshot>img.snapshot-entity{left:2.5%;right:auto;max-width:46%;z-index:3;filter:drop-shadow(0 0 10px rgba(0,0,0,.55))}.snapshot-character-placeholder{position:absolute;right:2.5%;bottom:8%;width:38%;height:84%;z-index:2;pointer-events:none;opacity:.46;filter:drop-shadow(0 0 10px rgba(0,0,0,.5));animation:combat-overlay-enter .18s ease-out}.snapshot-character-placeholder:before{content:"";position:absolute;left:50%;top:2%;width:27%;aspect-ratio:1;border-radius:50%;transform:translateX(-50%);background:#fff}.snapshot-character-placeholder:after{content:"";position:absolute;left:13%;right:13%;bottom:0;height:78%;background:#fff;clip-path:polygon(38% 0,62% 0,72% 10%,82% 25%,88% 51%,76% 100%,24% 100%,12% 51%,18% 25%,28% 10%);border-radius:18% 18% 9% 9%}.snapshot-combat-character{animation:combat-overlay-enter .18s ease-out}.combat-hit-flash{animation:combat-hit-flash .14s ease-out!important}.combat-float{position:absolute;z-index:8;pointer-events:none;transform:translate(-50%,0);font-family:Play,system-ui,sans-serif;font-weight:700;font-size:19px;color:#fff;white-space:nowrap;text-shadow:0 2px 3px #000,0 0 6px #000;animation:combat-float-up 1.6s ease-out forwards}@keyframes combat-hit-flash{0%,100%{opacity:1}50%{filter:brightness(0) invert(1) drop-shadow(0 0 8px #fff);opacity:1}}@keyframes combat-float-up{0%{opacity:0;transform:translate(-50%,8px) scale(.96)}12%{opacity:1}80%{opacity:1}100%{opacity:0;transform:translate(-50%,-34px) scale(1.04)}}@keyframes combat-overlay-enter{from{opacity:0}to{opacity:1}}.snapshot>img.snapshot-chest{position:absolute;left:50%;bottom:-5%;transform:translateX(-50%);width:auto;max-width:58%;height:92%;object-fit:contain;object-position:center bottom;z-index:3;pointer-events:none;filter:drop-shadow(0 10px 16px rgba(0,0,0,.65))}';
  document.head.appendChild(st);
  var __overlayBoundsCache={};
  // Synchronous metadata avoids coupling any sprite to other images' load events.
  var __characterFamily=SnapshotOverlayLayout.envelope(SnapshotOverlayLayout.characterMetrics());
  function spriteMetric(img){
    var src=img.currentSrc||img.src;
    var bundled=SnapshotOverlayLayout.assetMetric(src);
    if(bundled&&bundled.width===img.naturalWidth&&bundled.height===img.naturalHeight)return bundled;
    if(__overlayBoundsCache[src])return __overlayBoundsCache[src];
    try{
      var canvas=document.createElement('canvas');
      canvas.width=img.naturalWidth;canvas.height=img.naturalHeight;
      var ctx=canvas.getContext('2d',{willReadFrequently:true});
      ctx.drawImage(img,0,0);
      var metric=SnapshotOverlayLayout.bounds(ctx.getImageData(0,0,canvas.width,canvas.height).data,canvas.width,canvas.height);
      if(metric){__overlayBoundsCache[src]=metric;return metric;}
    }catch(error){
      console.warn('Cannot measure overlay alpha bounds; using visible fallback',src,error);
    }
    // Unknown or replaced assets must remain visible even when canvas is tainted.
    var fallback=SnapshotOverlayLayout.canvasMetric(img.naturalWidth,img.naturalHeight);
    __overlayBoundsCache[src]=fallback;return fallback;
  }
  function alignOverlayToGround(img,side,category){
    img.dataset.groundSide=side;img.dataset.overlayCategory=category;
    function apply(){
      var box=img.parentElement;
      if(!box||!img.complete||!img.naturalWidth||!__characterFamily)return;
      var metric=spriteMetric(img);
      var result=SnapshotOverlayLayout.layout(metric,box.clientWidth,box.clientHeight,side,category,__characterFamily);
      if(!result)return;
      img.style.maxWidth='none';img.style.right='auto';
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
  function scrollBottom(){var l=document.getElementById('log');if(l)requestAnimationFrame(function(){l.scrollTop=l.scrollHeight;});}
  try{localStorage.removeItem('backroom-apk-snapshot');}catch(_){}
  function localLevelSnapshot(){try{if(!window.Android||typeof Android.levelSnapshot!=='function')return null;return JSON.parse(Android.levelSnapshot(JSON.stringify(state)));}catch(e){return null;}}
  var __entityKeys=['hound','clump','duller','deathmoth','hostile_faceling','false_puddle','paintings','smiler','skin-stealer','predatory_window','biological_pipeline','wretch','cable_mimic','the_beast_of_level_5','hotel_corpse_lure','jeff_the_killer','async_rifleman','copx','jane_the_killer','slenderman','diep_minh'];
  var __combatCharacterOverlays={cao_minh:'file:///android_asset/cao_minh_entity_overlay.png',luc_tram:'file:///android_asset/luctram_overlay.png'};
  window.__combatVisualActorIndex=null;
  window.__combatVisualEntityKey='';
  function normalizeEntityKey(v){if(v===null||v===undefined)return '';var k=String(v).trim().toLowerCase().replace(/\s+/g,'_');if(k==='skin_stealer')k='skin-stealer';return __entityKeys.indexOf(k)>=0?k:'';}
  function activeEntityKey(){try{var forced=normalizeEntityKey(window.__combatVisualEntityKey||'');if(forced)return forced;var s=(typeof state!=='undefined'&&state)?state:{};var f=s.flags||{},c=s.combat||{};var combatKey=c.active?((c.entity&&c.entity.key)||c.entityKey||c.enemyKey||c.enemy||''):'';var k=normalizeEntityKey(f.entityEncounterKey||f.currentEntityKey||s.entityEncounterKey||s.currentEntityKey||combatKey);if(k)return k;if(f.jeff&&(f.jeff.present===true||f.jeff.spawned===true))return 'jeff_the_killer';if(f.jane&&(f.jane.present===true||f.jane.spawned===true))return 'jane_the_killer';return '';}catch(e){return '';}}
  function chestPresent(){try{var s=(typeof state!=='undefined'&&state)?state:{};return !!(s.flags&&s.flags.chestPresent===true);}catch(e){return false;}}
  function shouldShowCaoMinhOverlay(){try{var s=(typeof state!=='undefined'&&state)?state:{};return !(s.specialMode||s.debug||activeEntityKey()||chestPresent());}catch(e){return false;}}
  function normalizeActorId(v){var k=String(v||'').trim().toLowerCase();if(k.indexOf('cao_minh')>=0||k.indexOf('cao minh')>=0)return 'cao_minh';if(k.indexOf('lục trầm')>=0||k.indexOf('luc tram')>=0||k.indexOf('luc_tram')>=0)return 'luc_tram';if(k.indexOf('iris')>=0||k.indexOf('argus')>=0)return 'iris';if(k.indexOf('syvial')>=0)return 'syvial';return k.replace(/\s+/g,'_');}
  function combatVisualParticipant(){try{var c=state&&state.combat;if(!c||!Array.isArray(c.participants)||!c.participants.length)return null;var idx;if(Number.isInteger(window.__combatVisualActorIndex)){idx=window.__combatVisualActorIndex;}else{var currentId=normalizeActorId(c.currentActor||'');if(currentId){for(var i=0;i<c.participants.length;i++){var candidate=c.participants[i];if(candidate&&normalizeActorId(candidate.id||candidate.name)===currentId)return candidate;}}idx=Number(c.actorIndex||0);}if(idx<0||idx>=c.participants.length)idx=0;return c.participants[idx]||null;}catch(_){return null;}}

  function appendCombatCharacter(box,participant){
    var actor=participant||{id:'cao_minh',name:'Cao Minh'},id=normalizeActorId(actor.id||actor.name),src=__combatCharacterOverlays[id]||'';
    if(src){
      var img=document.createElement('img');img.className='snapshot-character snapshot-grounded snapshot-combat-character';img.src=src;img.alt=actor.name||actor.id||'Nhân vật';img.dataset.combatActor=id;box.appendChild(img);alignOverlayToGround(img,'right','character');return img;
    }
    var placeholder=document.createElement('div');placeholder.className='snapshot-character-placeholder snapshot-combat-character';placeholder.setAttribute('role','img');placeholder.setAttribute('aria-label',actor.name||actor.id||'Nhân vật');box.appendChild(placeholder);alignPlaceholder(placeholder);return placeholder;
  }
  function appendSnapshotOverlay(box){
    var key=activeEntityKey(),img;
    if(key){
      appendCombatCharacter(box,combatVisualParticipant());
      img=document.createElement('img');img.className='snapshot-entity snapshot-grounded';img.src='file:///android_asset/entity/'+key+'.webp';img.alt=key;box.appendChild(img);alignOverlayToGround(img,'left','entity');return;
    }
    if(chestPresent()){img=document.createElement('img');img.className='snapshot-chest';img.src='file:///android_asset/chest_overlay.png';img.alt='Rương';box.appendChild(img);return;}
    if(shouldShowCaoMinhOverlay()){
      img=document.createElement('img');img.className='snapshot-character snapshot-grounded';img.src='file:///android_asset/cao_minh_snapshot_overlay.png';img.alt='Cao Minh';box.appendChild(img);alignOverlayToGround(img,'right','character');
    }
  }
  function floorAssetKey(local){
    var stateKey='';
    try{
      var s=(typeof state!=='undefined'&&state)?state:{};
      stateKey=String(s.currentLevelKey||s.levelKey||'');
    }catch(_){}
    return SnapshotOverlayLayout.floorKey(local&&local.levelKey!=null?local.levelKey:stateKey,local&&local.level);
  }
  function appendSnapshotFloor(box,local){
    if(!box)return null;
    var floor=floorAssetKey(local),img=document.createElement('img');
    img.className='snapshot-floor';
    img.src='file:///android_asset/floors/'+floor+'.webp';
    img.alt='';
    img.setAttribute('aria-hidden','true');
    box.appendChild(img);
    return img;
  }
  function appendSwordFloorGlow(box,local){
    if(!box)return null;
    var floor=floorAssetKey(local);
    if(!SnapshotOverlayLayout.swordGlowForFloor(floor))return null;
    var caoMinh=box.querySelector('img.snapshot-character[alt="Cao Minh"],img.snapshot-character[data-combat-actor="cao_minh"]');
    if(!caoMinh)return null;
    var glow=document.createElement('div');
    glow.className='snapshot-sword-glow';
    glow.dataset.floorKey=floor;
    glow.setAttribute('aria-hidden','true');
    box.appendChild(glow);
    return glow;
  }
  function renderSnapshot(){var box=document.getElementById('snapshot');if(!box)return;box.textContent='';var local=localLevelSnapshot();if(local&&local.path){var img=document.createElement('img');img.className='snapshot-bg'+(local.visualType==='map'?' snapshot-map':'');img.src=local.path;img.alt='Level '+local.level+' Snapshot';box.appendChild(img);}else{var p=document.createElement('div');p.className='snapshot-placeholder';p.innerHTML='<b>LEVEL SNAPSHOT</b><small>Không có ảnh local cho Level hiện tại.</small>';box.appendChild(p);}appendSnapshotFloor(box,local);appendSnapshotOverlay(box);appendSwordFloorGlow(box,local);}
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
      var e=event||{},text=String(e.text||'').trim();if(!/^-\d+ HP$/i.test(text))return;
      var target=e.target==='entity'?'entity':'actor',anchor=targetAnchor(target);if(!anchor)return;
      if(e.flash){
        anchor.el.classList.remove('combat-hit-flash');void anchor.el.offsetWidth;anchor.el.classList.add('combat-hit-flash');
        setTimeout(function(){anchor.el&&anchor.el.classList.remove('combat-hit-flash');},170);
      }
      var lane=anchor.box.querySelectorAll('.combat-float[data-target="'+target+'"]').length;
      var floater=document.createElement('div');floater.className='combat-float';floater.dataset.target=target;floater.textContent=text;floater.style.left=anchor.x+'px';floater.style.top=(anchor.y-lane*26)+'px';anchor.box.appendChild(floater);
      floater.addEventListener('animationend',function(){floater.remove();},{once:true});setTimeout(function(){floater.remove();},1800);
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
  renderSnapshot();
})();
