const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto');

const assets=path.join(__dirname,'../app/src/main/assets');
const registry=JSON.parse(fs.readFileSync(path.join(assets,'npc/registry.json'),'utf8'));
const manifest=JSON.parse(fs.readFileSync(path.join(assets,'story/generated/story_visuals.json'),'utf8'));

function canonical(text){
  if(text.startsWith('\ufeff'))text=text.slice(1);
  return text.replace(/\r\n/g,'\n').replace(/\r/g,'\n').trim()+'\n';
}
function splitMarkdown(markdown,target=1900,maximum=2400){
  const source=canonical(markdown).trim();
  const paragraphs=source.split(/\n\s*\n+/).map(v=>v.trim()).filter(Boolean)
    .map(v=>v.startsWith('# ')?v.slice(2).trim():v);
  const out=[];let current='';
  for(const paragraph of paragraphs){
    const added=paragraph.length+(current?2:0);
    const over=current.length>=target;
    const exceed=!!current&&current.length+added>maximum;
    if(current&&(over||exceed)){out.push(current);current='';}
    if(current)current+='\n\n';
    current+=paragraph;
  }
  if(current)out.push(current);
  return out;
}
function stableSegments(chapterId,markdown){
  const seen=new Map();
  return splitMarkdown(markdown).map(text=>{
    const digest=crypto.createHash('sha256').update(text,'utf8').digest('hex').slice(0,16);
    const occurrence=(seen.get(digest)||0)+1;seen.set(digest,occurrence);
    return {id:chapterId+'_S'+digest+(occurrence===1?'':'_R'+occurrence),text};
  });
}
function walk(dir){
  return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>{
    const p=path.join(dir,e.name);return e.isDirectory()?walk(p):[p];
  });
}
function manifestChapter(chapterId){
  for(const story of Object.values(manifest.stories||{})){
    if(story.chapters&&story.chapters[chapterId])return story.chapters[chapterId];
  }
  return null;
}

test('NPC registry only references bundled local assets',()=>{
  assert.equal(registry.schemaVersion,1);
  for(const [id,spec] of Object.entries(registry.characters)){
    assert.match(id,/^[a-z0-9_]+$/);
    assert.ok(spec.displayName);
    assert.match(spec.asset,/^npc\//);
    assert.ok(fs.statSync(path.join(assets,spec.asset)).size>0,spec.asset);
  }
});

test('chapter visual sources compile to dense chapter-local mappings',()=>{
  const sourceRoot=path.join(assets,'story/source');
  const visualFiles=walk(sourceRoot).filter(p=>p.endsWith('.visual.json'));
  assert.ok(visualFiles.length>0);
  const seen=new Set();
  for(const visualPath of visualFiles){
    const payload=JSON.parse(fs.readFileSync(visualPath,'utf8'));
    assert.equal(payload.schemaVersion,1);
    const chapterId=payload.chapterId;
    assert.ok(chapterId&&!seen.has(chapterId),chapterId);seen.add(chapterId);
    const manuscriptPath=visualPath.replace(/\.visual\.json$/,'.md');
    assert.ok(fs.existsSync(manuscriptPath),manuscriptPath);
    const manuscript=fs.readFileSync(manuscriptPath,'utf8');
    const segments=stableSegments(chapterId,manuscript);
    const declarations=new Map();let previous=-1;
    for(const item of payload.visuals){
      assert.ok(Object.prototype.hasOwnProperty.call(item,'primaryNpcId'));
      if(item.primaryNpcId!==null)assert.ok(registry.characters[item.primaryNpcId],item.primaryNpcId);
      let index=0;
      if(item.when==='AFTER_TEXT'){
        assert.ok(item.anchor);
        assert.equal(canonical(manuscript).split(item.anchor).length-1,1,item.anchor);
        const matches=segments.map((s,i)=>s.text.includes(item.anchor)?i:-1).filter(i=>i>=0);
        assert.equal(matches.length,1,item.anchor);index=matches[0];
      }else assert.equal(item.when,'ENTER');
      assert.ok(index>=previous,'visual declarations out of order');
      assert.equal(declarations.has(index),false,'multiple visual declarations in one segment');
      previous=index;declarations.set(index,item.primaryNpcId);
    }
    let focus=null;const expected={};
    segments.forEach((segment,index)=>{if(declarations.has(index))focus=declarations.get(index);expected[segment.id]={primaryNpcId:focus};});
    const generated=manifestChapter(chapterId);
    assert.ok(generated,'missing generated visual chapter '+chapterId);
    assert.deepEqual(generated.segments,expected);
  }
  const generatedCount=Object.values(manifest.stories||{}).reduce((n,s)=>n+Object.keys(s.chapters||{}).length,0);
  assert.equal(generatedCount,visualFiles.length);
});

test('generated Story visual manifest is schema-versioned and dense only within authored chapters',()=>{
  assert.equal(manifest.schemaVersion,1);
  assert.match(manifest.visualCompilerFingerprint,/^[0-9a-f]{64}$/);
  for(const story of Object.values(manifest.stories||{})){
    assert.ok(story.sourceRevision);
    for(const chapter of Object.values(story.chapters||{})){
      const ids=Object.keys(chapter.segments||{});
      assert.ok(ids.length>0);
      for(const id of ids){
        const value=chapter.segments[id].primaryNpcId;
        assert.ok(value===null||registry.characters[value],id);
      }
    }
  }
});
