from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
COMBAT_TURN_AUTHORITY = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatTurnAuthority.kt"

# Step 1: Character stat/vital schema.
runpy.run_path(str(ROOT / "patch-character-stat-schema.py"), run_name="__main__")

# Steps 2-10: Equipment definitions, slot semantics, normalized item stats, effective-stat resolver,
# HP-preserving equip/unequip, completed-turn regeneration, shared Item Detail UI, persistence,
# Pressure Combat integration, and regression tests.
#
# CombatTurnAuthority now owns combat time + completed-turn regeneration in source Kotlin. The
# historical status patch still carries the retired facade injection, so strip only that block at
# execution time while preserving its generic TurnCoordinator regeneration and equipment work.
status_patch = ROOT / "patch-character-status-equipment-system.py"
status_source = status_patch.read_text(encoding="utf-8")
legacy_combat_start = "# Combat actions are completed gameplay turns too. Regen token derives from the UI turn so save/load cannot reapply it.\n"
legacy_combat_end = "# Gemini candidate inventory can never silently delete equipment definitions. Equipment remains one owned Item.\n"
legacy_start = status_source.find(legacy_combat_start)
legacy_end = status_source.find(legacy_combat_end, legacy_start)
if legacy_start < 0 or legacy_end < 0:
    raise RuntimeError("Legacy combat regeneration block missing from character status patch")
authority = COMBAT_TURN_AUTHORITY.read_text(encoding="utf-8")
for marker in (
    "object CombatTurnAuthority",
    "CharacterStatEngine.applyCompletedTurnRegen(next, completedTurnRegenToken(state))",
    'return "COMBAT_TURN_$turnNumber"',
):
    if marker not in authority:
        raise RuntimeError("Kotlin combat regeneration authority missing: " + marker)
status_source = (
    status_source[:legacy_start]
    + "# Combat completed-turn regeneration is source-owned by CombatTurnAuthority.\n"
    + 'facade = FACADE.read_text(encoding="utf-8")\n\n'
    + status_source[legacy_end:]
)
exec(compile(status_source, str(status_patch), "exec"), {"__name__": "__main__", "__file__": str(status_patch)})

# Hard cleanup after the large status patch: no retired combat HP metadata reference may survive.
runpy.run_path(str(ROOT / "patch-combat-hp-metadata-cleanup.py"), run_name="__main__")

# Fresh canonical loadouts begin at full Effective HP. This is initialization only; later equipment
# changes keep Missing HP through CharacterStatEngine.preserveMissingHp.
runpy.run_path(str(ROOT / "patch-fresh-effective-hp.py"), run_name="__main__")

INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)

profile_old = '''    <img id="characterInventoryAvatar" src="avatars/kai_avatar.png" alt="Kai Akechi">
    <div><div class="inventory-capacity" id="characterInventoryCapacity">0 / 9 loại vật phẩm</div><div class="inventory-limit">Inventory của nhân vật đang chọn</div></div>
'''
profile_new = '''    <img id="characterInventoryAvatar" src="avatars/kai_avatar.png" alt="Kai Akechi">
    <div>
      <div class="character-hp" aria-label="Health">
        <div class="character-hp-track"><div class="character-hp-fill" id="characterHpFill"></div><div class="character-hp-segments" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div></div>
        <div class="character-hp-value"><span class="character-hp-heart">♥</span><strong id="characterHpValue">100/100</strong></div>
      </div>
      <div class="inventory-capacity" id="characterInventoryCapacity">0 / 9 loại vật phẩm</div><div class="inventory-limit">Inventory của nhân vật đang chọn</div>
    </div>
'''
if 'id="characterHpFill"' not in html:
    html = replace_once(html, profile_old, profile_new, "character healthbar markup")

css_anchor = '.character-profile img{width:110px;height:110px;object-fit:cover;border:1px solid #3b444c}'
css_extra = '''.character-hp{margin:0 0 14px;max-width:520px}.character-hp-track{position:relative;height:22px;border:2px solid #30383d;background:#090c0d;box-shadow:0 0 0 2px rgba(0,0,0,.45),0 0 8px rgba(80,210,145,.16);overflow:hidden}.character-hp-fill{position:absolute;inset:0 auto 0 0;width:100%;background:linear-gradient(90deg,#a8e4c7 0%,#68c997 35%,#20b978 68%,#008f58 100%);transition:width .2s ease}.character-hp-segments{position:absolute;inset:0;display:grid;grid-template-columns:repeat(5,1fr);pointer-events:none}.character-hp-segments i{border-right:2px solid rgba(8,22,16,.23)}.character-hp-segments i:last-child{border-right:0}.character-hp-value{display:flex;align-items:center;gap:8px;margin-top:6px;font-size:18px;line-height:1}.character-hp-heart{color:#e6394f;font-size:28px;text-shadow:0 1px 2px #000}.character-hp-value strong{font-weight:900;letter-spacing:.02em;color:#dce4e7}'''
if css_extra not in html:
    html = replace_once(html, css_anchor, css_anchor + css_extra, "character healthbar CSS")

refs_old = '''  const detailAvatar=document.getElementById('characterInventoryAvatar');
  const equipment=document.getElementById('characterEquipmentList');
'''
refs_new = '''  const detailAvatar=document.getElementById('characterInventoryAvatar');
  const hpFill=document.getElementById('characterHpFill');
  const hpValue=document.getElementById('characterHpValue');
  const equipment=document.getElementById('characterEquipmentList');
'''
if "const hpFill=document.getElementById('characterHpFill');" not in html:
    html = replace_once(html, refs_old, refs_new, "character healthbar JS refs")

render_anchor = "    detailAvatar.alt=member.name||member.id||'Nhân vật';\n"
health_render = '''    const rawMaxHp=Number(member.maxHp),rawCurrentHp=Number(member.currentHp);
    const maxHp=Number.isFinite(rawMaxHp)&&rawMaxHp>0?rawMaxHp:100;
    const currentHp=Number.isFinite(rawCurrentHp)?Math.max(0,Math.min(maxHp,rawCurrentHp)):maxHp;
    const hpPercent=Math.max(0,Math.min(100,currentHp*100/maxHp));
    if(hpFill)hpFill.style.width=hpPercent+'%';
    if(hpValue)hpValue.textContent=Math.round(currentHp)+'/'+Math.round(maxHp);
'''
if "const rawMaxHp=Number(member.maxHp),rawCurrentHp=Number(member.currentHp);" not in html:
    html = replace_once(html, render_anchor, render_anchor + health_render, "selected character HP render")

for marker in (
    'id="characterHpFill"',
    'id="characterHpValue"',
    'character-hp-segments',
    "const hpFill=document.getElementById('characterHpFill');",
    "const rawMaxHp=Number(member.maxHp),rawCurrentHp=Number(member.currentHp);",
    "if(hpFill)hpFill.style.width=hpPercent+'%';",
    "hpValue.textContent=Math.round(currentHp)+'/'+Math.round(maxHp)",
    'id="equipmentDetailModal"',
    'window.renderCharacterStatusEquipment=render;',
):
    if marker not in html:
        raise RuntimeError("Character Status UI contract missing: " + marker)

INDEX.write_text(html, encoding="utf-8")
print("Character Status + Equipment + Inventory Detail UI applied.")

# Final live-render bridge: authoritative Status/HP/Inventory must work on first Character open,
# before any gameplay turn has synchronized legacy state.partyDetails.
runpy.run_path(str(ROOT / "patch-character-detail-live-ui-fix.py"), run_name="__main__")

# Final New Game + inventory capacity contract. Equipped Items remain the one owned Inventory Item,
# but consume zero backpack slots for every CharacterState.
runpy.run_path(str(ROOT / "patch-newgame-inventory-capacity.py"), run_name="__main__")

# Final candidate-inventory semantics: capacity validation must evaluate the Inventory instance being
# mutated, while equipped Items cost zero slots and displaced equipment costs a carried slot.
runpy.run_path(str(ROOT / "patch-inventory-capacity-final-fix.py"), run_name="__main__")
