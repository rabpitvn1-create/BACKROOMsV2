(function(){
  if(window.__backroomPartyUi)return;
  window.__backroomPartyUi=true;

  var AVATARS={
    kai:'file:///android_asset/avatars/kai_avatar.jpg',
    lucia:'file:///android_asset/avatars/lucia_avatar.jpg',
    iris:'file:///android_asset/avatars/Iris_avatar.jpg',
    syvial:'file:///android_asset/avatars/Syvial_avatar.jpg'
  };
  var META={
    kai:{name:'Kai Akechi',role:'Đội trưởng SRU',equipment:['SRU Assault Rifle MK19','SRU-MK20 Powered Armor','Omnivault Ring / Nhẫn Vạn Tàng']},
    lucia:{name:'Lucia Lục',role:'Tactical Riflewoman',equipment:['M4A1','Combat dagger','Military navigation watch']},
    iris:{name:'Iris',role:'Scout / Target Eliminator',equipment:['SRU Recon Frame R03','Ivory & Ebony']},
    syvial:{name:'Syvial',role:'Đội phó SRU',equipment:['Lucifer Armor','GodKiller']}
  };

  var style=document.createElement('style');
  style.textContent='.party-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px}.party-member-card{appearance:none;-webkit-appearance:none;border:1px solid #313940;background:#111519;color:#eef1f3;padding:6px;display:grid;gap:5px;text-align:center;min-width:0;touch-action:manipulation}.party-member-card:active{transform:scale(.97)}.party-member-card[aria-expanded="true"]{border-color:#707b84;background:#171c20}.party-member-avatar{width:100%;aspect-ratio:1/1;object-fit:cover;border:1px solid #333b42;background:#0b0e11}.party-member-name{font-size:10px;font-weight:800;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.party-member-state{font-size:8px;color:#8f9aa4;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.party-detail{margin-top:10px;border-top:1px solid #2b3137;padding-top:10px;animation:party-detail-in .16s ease-out}.party-detail-head{display:grid;grid-template-columns:92px 1fr auto;gap:11px;align-items:start}.party-detail-avatar{width:92px;height:92px;object-fit:cover;border:1px solid #3b444c;background:#0b0e11}.party-detail-name{font-size:18px;font-weight:800;line-height:1.2}.party-detail-role{margin-top:4px;color:#9aa4ad;font-size:11px}.party-detail-close{padding:7px 9px;min-width:38px}.party-detail-sections{display:grid;gap:8px;margin-top:10px}.party-detail-section{border:1px solid #2b3137;background:#0b0e11;padding:10px}.party-detail-section h3{margin:0 0 8px;font-size:10px;letter-spacing:.12em;color:#8f9aa4}.party-detail-row{display:grid;grid-template-columns:minmax(82px,35%) 1fr;gap:8px;padding:5px 0;border-bottom:1px solid #20262b;font-size:12px}.party-detail-row:last-child{border-bottom:0}.party-detail-row b{color:#7f8b95;font-weight:500}.party-detail-tags{display:flex;flex-wrap:wrap;gap:6px}.party-detail-tag{border:1px solid #313940;padding:5px 7px;font-size:11px}.party-avatar-placeholder{width:100%;aspect-ratio:1/1;display:grid;place-items:center;border:1px solid #333b42;background:#0b0e11;color:#8f9aa4;font-size:20px;font-weight:800}@keyframes party-detail-in{from{opacity:0;transform:translateY(-4px)}to{opacity:1;transform:translateY(0)}}@media(max-width:520px){.party-grid{grid-template-columns:repeat(4,minmax(0,1fr));gap:5px}.party-member-card{padding:4px}.party-member-name{font-size:9px}.party-detail-head{grid-template-columns:76px 1fr auto}.party-detail-avatar{width:76px;height:76px}}';
  document.head.appendChild(style);

  var party=document.getElementById('party');
  if(!party)return;
  party.className='party-grid';
  var card=party.closest?party.closest('.card'):party.parentElement;
  var detail=document.createElement('div');
  detail.className='party-detail';
  detail.hidden=true;
  detail.id='partyCharacterDetail';
  party.insertAdjacentElement('afterend',detail);
  var selectedId='';

  function norm(raw){
    var value=String(raw||'').trim().toLowerCase();
    if(value.indexOf('kai')>=0||value.indexOf('twilight')>=0)return 'kai';
    if(value.indexOf('lucia')>=0||value.indexOf('hứa thuý mai')>=0||value.indexOf('hứa thúy mai')>=0||value.indexOf('hua thuy mai')>=0)return 'lucia';
    if(value.indexOf('iris')>=0||value.indexOf('argus')>=0)return 'iris';
    if(value.indexOf('syvial')>=0)return 'syvial';
    return value.replace(/\s+/g,'_');
  }

  function clonePlayer(){
    var p=(state&&state.player)||{};
    var result={};
    Object.keys(p).forEach(function(k){result[k]=p[k];});
    result.id='kai';
    result.name=result.name||'Kai Akechi';
    result.joined=true;
    result.__player=true;
    return result;
  }

  function members(){
    var out=[clonePlayer()];
    var seen={kai:true};
    (Array.isArray(state&&state.party)?state.party:[]).forEach(function(member){
      if(!member||member.joined!==true)return;
      var id=norm(member.id||member.name);
      if(!id||id==='kai'||seen[id])return;
      seen[id]=true;
      out.push(member);
    });
    return out.slice(0,4);
  }

  function displayName(member){
    var id=norm(member&&((member.id||'')+' '+(member.name||'')));
    return String((member&&member.name)||(META[id]&&META[id].name)||id||'Nhân vật');
  }

  function avatarFor(member){
    var id=norm(member&&((member.id||'')+' '+(member.name||'')));
    var custom=member&&(member.avatar||member.avatarRef);
    return custom?String(custom):AVATARS[id]||'';
  }

  function conditionFor(member){
    if(member&&member.condition)return String(member.condition);
    if(member&&member.healthState)return String(member.healthState);
    var hp=Number(member&&member.hp);
    if(Number.isFinite(hp)&&hp<=0)return 'Bị hạ';
    return 'Hoạt động';
  }

  function appendText(el,text){el.appendChild(document.createTextNode(String(text)));}

  function makeAvatar(member,className){
    var src=avatarFor(member);
    if(src){
      var img=document.createElement('img');
      img.className=className;
      img.src=src;
      img.alt=displayName(member);
      img.loading='eager';
      return img;
    }
    var ph=document.createElement('div');
    ph.className='party-avatar-placeholder';
    ph.textContent=displayName(member).slice(0,1).toUpperCase();
    return ph;
  }

  function addRow(container,label,value){
    if(value===undefined||value===null||String(value).trim()==='')return;
    var row=document.createElement('div');row.className='party-detail-row';
    var b=document.createElement('b');b.textContent=label;
    var span=document.createElement('span');span.textContent=String(value);
    row.appendChild(b);row.appendChild(span);container.appendChild(row);
  }

  function section(title){
    var box=document.createElement('section');box.className='party-detail-section';
    var h=document.createElement('h3');h.textContent=title;box.appendChild(h);
    return box;
  }

  function statEntries(member){
    var stats=member&&member.stats&&typeof member.stats==='object'?member.stats:{};
    var result=[],seen={};
    function add(k,v){if(v===undefined||v===null||v===''||seen[k])return;seen[k]=true;result.push([k,v]);}
    ['STR','DF','AGI','CRIT','ATK','DEF'].forEach(function(k){if(Object.prototype.hasOwnProperty.call(stats,k))add(k,stats[k]);});
    if(member){
      if(member.attack!==undefined)add('ATK',member.attack);
      if(member.attackMax!==undefined)add('ATK MAX',member.attackMax);
      if(member.defense!==undefined)add('DEF',member.defense);
    }
    return result;
  }

  function equipmentFor(member,id){
    var eq=member&&member.equipment;
    if(Array.isArray(eq))return eq.map(function(x){return typeof x==='string'?x:(x&&x.name)||String(x);}).filter(Boolean);
    if(eq&&typeof eq==='object')return Object.keys(eq).map(function(k){var v=eq[k];return typeof v==='string'?v:(v&&v.name)||k;}).filter(Boolean);
    return (META[id]&&META[id].equipment)||[];
  }

  function inventoryFor(member,id){
    if(Array.isArray(member&&member.inventory))return member.inventory;
    if(id==='kai'&&Array.isArray(state&&state.inventory))return state.inventory;
    return [];
  }

  function tagsSection(title,items){
    var box=section(title),wrap=document.createElement('div');wrap.className='party-detail-tags';
    if(!items.length){var empty=document.createElement('span');empty.className='party-detail-tag';empty.textContent='Chưa có dữ liệu.';wrap.appendChild(empty);}
    items.forEach(function(item){var tag=document.createElement('span');tag.className='party-detail-tag';tag.textContent=typeof item==='string'?item:String((item&&item.name)||item);wrap.appendChild(tag);});
    box.appendChild(wrap);return box;
  }

  function renderDetail(member){
    if(!member){detail.hidden=true;selectedId='';return;}
    var id=norm((member.id||'')+' '+(member.name||'')),meta=META[id]||{};
    detail.textContent='';
    var head=document.createElement('div');head.className='party-detail-head';
    var avatar=makeAvatar(member,'party-detail-avatar');
    var identity=document.createElement('div');
    var name=document.createElement('div');name.className='party-detail-name';name.textContent=displayName(member);
    var role=document.createElement('div');role.className='party-detail-role';role.textContent=meta.role||String(member.role||'Party member');
    identity.appendChild(name);identity.appendChild(role);
    var close=document.createElement('button');close.type='button';close.className='party-detail-close';close.textContent='×';close.setAttribute('aria-label','Đóng thông tin nhân vật');
    close.addEventListener('click',function(){selectedId='';detail.hidden=true;renderPartyUi();});
    head.appendChild(avatar);head.appendChild(identity);head.appendChild(close);detail.appendChild(head);

    var sections=document.createElement('div');sections.className='party-detail-sections';
    var status=section('TRẠNG THÁI');
    addRow(status,'Tình trạng',conditionFor(member));
    var hp=member.hp,maxHp=member.maxHp||member.maxHP;
    if(hp!==undefined)addRow(status,'HP',maxHp!==undefined?String(hp)+' / '+String(maxHp):hp);
    addRow(status,'Vai trò',meta.role||member.role);
    status.appendChild(document.createComment('status'));
    sections.appendChild(status);

    var stats=statEntries(member);
    if(stats.length){
      var statBox=section('CHỈ SỐ');
      stats.forEach(function(pair){addRow(statBox,pair[0],pair[1]);});
      sections.appendChild(statBox);
    }

    var effects=[];
    ['injuries','statuses','effects'].forEach(function(k){
      if(Array.isArray(member&&member[k]))member[k].forEach(function(x){effects.push(typeof x==='string'?x:(x&&x.type)||(x&&x.name)||(x&&x.id)||'');});
    });
    effects=effects.filter(Boolean);
    if(effects.length)sections.appendChild(tagsSection('HIỆU ỨNG / THƯƠNG TÍCH',effects));
    sections.appendChild(tagsSection('TRANG BỊ',equipmentFor(member,id)));
    sections.appendChild(tagsSection('INVENTORY',inventoryFor(member,id)));
    detail.appendChild(sections);
    detail.hidden=false;
  }

  function openMember(member){
    var id=norm((member.id||'')+' '+(member.name||''));
    if(selectedId===id&&!detail.hidden){selectedId='';detail.hidden=true;renderPartyUi();return;}
    selectedId=id;renderDetail(member);renderPartyUi();
  }

  function renderPartyUi(){
    var list=members();
    party.textContent='';
    list.forEach(function(member){
      var id=norm((member.id||'')+' '+(member.name||''));
      var button=document.createElement('button');
      button.type='button';button.className='party-member-card';button.dataset.character=id;
      button.setAttribute('aria-expanded',String(selectedId===id&&!detail.hidden));
      button.appendChild(makeAvatar(member,'party-member-avatar'));
      var name=document.createElement('div');name.className='party-member-name';name.textContent=displayName(member);button.appendChild(name);
      var stateText=document.createElement('div');stateText.className='party-member-state';stateText.textContent=conditionFor(member);button.appendChild(stateText);
      button.addEventListener('click',function(){openMember(member);});
      party.appendChild(button);
    });
    if(selectedId){
      var selected=list.find(function(member){return norm((member.id||'')+' '+(member.name||''))===selectedId;});
      if(selected&&!detail.hidden)renderDetail(selected);
      else if(!selected){selectedId='';detail.hidden=true;}
    }
  }

  window.renderPartyUi=renderPartyUi;
  var oldRender=window.render;
  if(typeof oldRender==='function'){
    window.render=function(){oldRender();renderPartyUi();};
  }
  renderPartyUi();
})();