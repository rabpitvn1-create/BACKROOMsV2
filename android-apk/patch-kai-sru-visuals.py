from pathlib import Path

ROOT = Path(__file__).resolve().parent
ASSETS = ROOT / "app/src/main/assets"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ASSETS / "index.html"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return text.replace(old, new, 1)


required_assets = [
    ASSETS / "kai_snapshot_overlay.png",
    ASSETS / "kai_entity_overlay.png",
    ASSETS / "avatars/kai_avatar.png",
]
for asset in required_assets:
    if not asset.is_file() or asset.stat().st_size <= 0:
        raise RuntimeError(f"Missing Kai SRU visual asset: {asset}")

text = MAIN.read_text(encoding="utf-8")

old = "var key=activeEntityKey();if(!key){"
new = (
    "var key=activeEntityKey();"
    "var kai=box.querySelector('.snapshot-character');"
    "if(kai){"
    "if(key){"
    "if(!kai.dataset.kaiBaseSrc)kai.dataset.kaiBaseSrc=kai.getAttribute('src')||'';"
    "kai.src='file:///android_asset/kai_entity_overlay.png';"
    "}else if(kai.dataset.kaiBaseSrc){"
    "kai.src=kai.dataset.kaiBaseSrc;delete kai.dataset.kaiBaseSrc;"
    "}"
    "}"
    "if(!key){"
)
count = text.count(old)
if count != 1:
    raise RuntimeError(f"Kai Entity overlay switch anchor: expected exactly 1 match, found {count}")
text = text.replace(old, new, 1)

# The default Kai overlay may be rewritten by later Snapshot/MadGod composition patches, so
# validate its packaged asset separately rather than requiring one brittle literal URI here.
# This finalizer only owns the temporary Entity-encounter swap and restoration of whatever
# authoritative base overlay is already selected by the existing runtime.
for marker in [
    "file:///android_asset/kai_entity_overlay.png",
    "box.querySelector('.snapshot-character')",
    "kai.dataset.kaiBaseSrc",
    "function activeEntityKey()",
    "window.backroomEntityOverlay=function(payload)",
]:
    if marker not in text:
        raise RuntimeError(f"Kai SRU runtime marker missing: {marker}")

MAIN.write_text(text, encoding="utf-8")

# Character Detail is the only layer changed below. Legacy equipment IDs, gameplay stats,
# save migration, combat math and slot ownership remain untouched. This matters because the
# old Kai item IDs are still part of save compatibility even though the current Character Codex
# presents SRU-MK20/MK19 and integrates the old mask/gauntlet/greave functions into the armor.
index = INDEX.read_text(encoding="utf-8")

old_static = '<span>W.W Magnum</span><span>Blackblood Armor & linked modules</span><span>Omnivault Ring</span>'
new_static = '<span>SRU Assault Rifle MK19</span><span>SRU-MK20</span><span>Omnivault Ring</span>'
if old_static in index:
    index = replace_once(index, old_static, new_static, "Kai static Character Detail equipment")

old_signature = "const signatureEquipment={weapon:'W.W Magnum',armor:'Blackblood Armor & linked modules',ring:'Omnivault Ring'};"
new_signature = "const signatureEquipment={weapon:'SRU Assault Rifle MK19',armor:'SRU-MK20',ring:'Omnivault Ring'};"
if old_signature in index:
    index = replace_once(index, old_signature, new_signature, "Kai legacy Character Detail fallback")

helper_anchor = "  function itemById(member,id){return (member&&member.inventory||[]).find(x=>String(x.id)===String(id))||(member&&member.equipmentItems||[]).find(x=>String(x.id)===String(id))}\n"
helpers = r'''  function canonicalCharacterKey(member){
    const raw=String((member&&member.id)||(member&&member.name)||'').toLocaleLowerCase('vi-VN');
    if(raw==='kai'||raw.includes('kai akechi'))return 'kai';
    if(raw.includes('syvial'))return 'syvial';
    if(raw.includes('iris'))return 'iris';
    if(raw.includes('lucia')||raw.includes('hứa thuý mai')||raw.includes('hua thuy mai'))return 'lucia';
    return raw;
  }
  const canonicalEquipmentDefaults={
    kai:[['weapon','SRU Assault Rifle MK19'],['armor','SRU-MK20'],['ring','Omnivault Ring']],
    syvial:[['weapon','GodKiller'],['armor','Lucifer Armor']],
    iris:[['weapon','IVORY & EBONY'],['armor','Project 07']],
    lucia:[['weapon','M4A1 cá nhân hóa'],['sidearm','Súng ngắn màu đen (model chưa khóa)'],['blade','Dao găm chiến đấu'],['wrist','Đồng hồ định vị quân sự']]
  };
  function canonicalFallbackEquipment(member){return canonicalEquipmentDefaults[canonicalCharacterKey(member)]||[]}
  function integratedKaiLegacyItem(member,item){
    if(canonicalCharacterKey(member)!=='kai'||!item)return false;
    const raw=(String(item.id||'')+' '+String(item.name||'')).toLowerCase();
    return raw.includes('demon-jaw')||raw.includes('demon jaw')||raw.includes('talon-gaunt')||raw.includes('talon gaunt')||raw.includes('phantom-greave')||raw.includes('phantom greave');
  }
  function canonicalDisplayAlias(member,item){
    if(!item)return null;
    const who=canonicalCharacterKey(member);
    const raw=(String(item.id||'')+' '+String(item.name||'')).toLocaleLowerCase('vi-VN');
    if(who==='kai'){
      if(raw.includes('white-wraith')||raw.includes('white wraith')||raw.includes('w.w magnum'))return 'SRU Assault Rifle MK19';
      if(raw.includes('blackblood')||raw.includes('black blood'))return 'SRU-MK20';
      if(raw.includes('omnivault')||raw.includes('vạn tàng')||raw.includes('van tang'))return 'Omnivault Ring';
    }
    if(who==='iris'){
      if(raw.includes('ivory')&&raw.includes('ebony'))return 'IVORY & EBONY';
      if(raw.includes('recon-frame')||raw.includes('recon frame')||raw.includes('blackblood recon'))return 'Project 07';
    }
    if(who==='lucia'){
      if(raw.includes('m4a1'))return 'M4A1 cá nhân hóa';
      if(raw.includes('combat-knife')||raw.includes('dao găm'))return 'Dao găm chiến đấu';
      if(raw.includes('military-watch')||raw.includes('đồng hồ'))return 'Đồng hồ định vị quân sự';
    }
    return null;
  }
  function displayOnlyEquipmentCard(name,slot){
    return '<div class="equipment-card canon-display-only"><div class="equipment-card-icon">EQ</div><div class="equipment-card-main"><strong>'+e(name)+'</strong><small>'+e(String(slot).toUpperCase())+'</small></div><div class="equipment-badges"><span class="equipment-badge equipped">CANON</span></div></div>';
  }
'''
if "function canonicalFallbackEquipment(member)" not in index:
    if helper_anchor not in index:
        raise RuntimeError("Canonical Character Detail helper anchor missing")
    index = index.replace(helper_anchor, helper_anchor + helpers, 1)

# The final capacity patch groups multi-slot equipment before this finalizer runs. Patch that
# authoritative final renderer, not the earlier per-slot renderer. Existing dynamic equipment
# such as MadGod still renders normally; only legacy items whose display identity is superseded
# by current canon are replaced with non-clickable canon cards so stale legacy abilities are not
# exposed under a new name.
old_equipment_render = "    const eq=member.equipment||{},details=member.equipmentItems||[];\n    if(equipment){const grouped=new Map();Object.keys(eq).sort().forEach(slot=>{const id=String(eq[slot]||'');if(!id)return;const slots=grouped.get(id)||[];slots.push(slot);grouped.set(id,slots)});const rendered=[];grouped.forEach((slots,id)=>{const item=details.find(x=>String(x.id)===id)||itemById(member,id);if(item)rendered.push(card(item,slots.join(' / ')))});equipment.innerHTML=rendered.length?rendered.join(''):'<span>Không có trang bị được ghi nhận.</span>'}\n    if(inventory){inventory.innerHTML=(member.inventory||[]).map(x=>card(x,null)).join('')||'<span>Trống.</span>'}\n"
new_equipment_render = "    const eq=member.equipment||{},details=member.equipmentItems||[];\n    if(equipment){const grouped=new Map();Object.keys(eq).sort().forEach(slot=>{const id=String(eq[slot]||'');if(!id)return;const slots=grouped.get(id)||[];slots.push(slot);grouped.set(id,slots)});const rendered=[],represented=new Set();grouped.forEach((slots,id)=>{const item=details.find(x=>String(x.id)===id)||itemById(member,id);if(item&&integratedKaiLegacyItem(member,item))return;const slotLabel=slots.join(' / ');const alias=canonicalDisplayAlias(member,item);if(alias)rendered.push(displayOnlyEquipmentCard(alias,slotLabel));else if(item)rendered.push(card(item,slotLabel));else rendered.push(displayOnlyEquipmentCard(id,slotLabel));slots.forEach(slot=>represented.add(String(slot)))});canonicalFallbackEquipment(member).forEach(x=>{const slot=String(x[0]);if(!represented.has(slot)){rendered.push(displayOnlyEquipmentCard(x[1],slot));represented.add(slot)}});equipment.innerHTML=rendered.length?rendered.join(''):'<span>Không có trang bị được ghi nhận.</span>'}\n    if(inventory){const visible=(member.inventory||[]).filter(x=>!integratedKaiLegacyItem(member,x));inventory.innerHTML=visible.map(x=>card(x,null)).join('')||'<span>Trống.</span>'}\n"
if new_equipment_render not in index:
    if old_equipment_render not in index:
        raise RuntimeError("Final grouped Character Detail equipment renderer anchor missing")
    index = index.replace(old_equipment_render, new_equipment_render, 1)

for marker in [
    "SRU Assault Rifle MK19",
    "SRU-MK20",
    "Omnivault Ring",
    "GodKiller",
    "Lucifer Armor",
    "IVORY & EBONY",
    "Project 07",
    "M4A1 cá nhân hóa",
    "Súng ngắn màu đen (model chưa khóa)",
    "Dao găm chiến đấu",
    "Đồng hồ định vị quân sự",
    "function integratedKaiLegacyItem(member,item)",
    "const grouped=new Map();Object.keys(eq).sort()",
]:
    if marker not in index:
        raise RuntimeError(f"Canonical equipment display marker missing: {marker}")

INDEX.write_text(index, encoding="utf-8")
print("Kai SRU visuals and current-canon equipment display applied for Kai, Syvial, Iris and Lucia without changing gameplay IDs/stats.")
