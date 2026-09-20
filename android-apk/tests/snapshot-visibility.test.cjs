const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/snapshot-ui.js'),'utf8');
const geometry=require('../app/src/main/assets/snapshot-ui.js');
function boot({unknown=false,unloaded=false,canvasContextMissing=false}={}){
 const elements=[],styles=[],pending=[];let reads=0;
 const box={clientWidth:350,clientHeight:250,appendChild(el){el.parentElement=this;elements.push(el);},querySelectorAll(selector){return elements.filter(e=>selector.includes('img.')&&e.tagName==='IMG'&&e.className.includes('snapshot-grounded'));}};
 Object.defineProperty(box,'textContent',{set(){elements.length=0;}});
 const document={readyState:'complete',head:{appendChild(el){styles.push(el.textContent);}},getElementById(id){return id==='snapshot'?box:null;},querySelectorAll(){return [];},createElement(tag){
  if(tag==='canvas')return {getContext(){reads++;if(canvasContextMissing)return null;return {drawImage(){},getImageData(){throw new Error('SecurityError: canvas has been tainted by cross-origin data');}};}};
  return {tagName:tag.toUpperCase(),style:{},dataset:{},className:'',complete:!unloaded,naturalWidth:0,naturalHeight:0,setAttribute(){},addEventListener(type,cb){if(type==='load')pending.push({el:this,cb});},set src(url){this.url=url;const m=geometry.assetMetric(url)||geometry.assetMetric('lucia_entity_overlay.png');this.naturalWidth=m.width;this.naturalHeight=m.height;},get src(){return this.url;}};
 }};
 const ctx={document,console:{warn(){}},localStorage:{removeItem(){}},setTimeout,clearTimeout,Image:function(){throw Error('Detached image preload must not gate overlays');},state:{flags:{},combat:{active:true,participants:[{id:'kai',name:'Cao Minh'},{id:'lucia'}]}}};
 ctx.window=ctx;ctx.addEventListener=()=>{};
 vm.createContext(ctx);
 let js=source;if(unknown)js=js.replaceAll('file:///android_asset/lucia_entity_overlay.png','file:///android_asset/unregistered.png');
 vm.runInContext(js,ctx);
 return {ctx,box,elements,styles,pending,reads:()=>reads};
}
function visible(img){assert.notEqual(img.style.visibility,'hidden');assert.ok(parseFloat(img.style.width)>0);assert.ok(parseFloat(img.style.height)>0);}
test('bundled combat/Entity render without any readable canvas or detached preloads',()=>{
 const r=boot();
 r.ctx.backroomSetCombatVisualActor(1,'deathmoth');
 visible(r.elements.find(e=>e.className.includes('snapshot-character')));
 for(const actor of [0,1]){r.ctx.backroomSetCombatVisualActor(actor,'deathmoth');r.elements.filter(e=>e.className.includes('snapshot-grounded')).forEach(visible);}
 assert.equal(r.reads(),0);assert.ok(!r.styles.join('').includes('visibility:hidden'));
});
test('unknown sprite remains visible when canvas throws SecurityError',()=>{
 const r=boot({unknown:true});r.ctx.backroomSetCombatVisualActor(1,'deathmoth');visible(r.elements.find(e=>e.className.includes('snapshot-character')));assert.equal(r.reads(),1);
});
test('unknown sprite remains visible when canvas context is unavailable',()=>{
 const r=boot({unknown:true,canvasContextMissing:true});r.ctx.backroomSetCombatVisualActor(1,'deathmoth');visible(r.elements.find(e=>e.className.includes('snapshot-character')));
});
test('late image load aligns independently, without waiting for other characters',()=>{
 const r=boot({unloaded:true});r.ctx.backroomSetCombatVisualActor(1,'deathmoth');assert.ok(r.pending.length>0);
 for(const {el,cb} of r.pending){el.complete=true;cb();visible(el);}
 assert.equal(r.reads(),0);
});
test('generated bounds exist for all registered overlays and match PNG dimensions',()=>{
 const assets=path.join(__dirname,'../app/src/main/assets');
 const names=['lucia_entity_overlay.png',...fs.readdirSync(path.join(assets,'entity')).filter(n=>n.endsWith('.png')).map(n=>'entity/'+n)];
 for(const name of names){const m=geometry.assetMetric('file:///android_asset/'+name),data=fs.readFileSync(path.join(assets,name));assert.ok(m,name);assert.equal(m.width,data.readUInt32BE(16));assert.equal(m.height,data.readUInt32BE(20));assert.ok(m.paint.right<=m.width&&m.body.bottom<=m.height);}
});
