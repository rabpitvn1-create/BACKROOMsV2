from pathlib import Path
import runpy

ROOT = Path(__file__).resolve().parent
COMBAT_TURN_AUTHORITY = ROOT / "app/src/main/java/com/rabpit/backroom/core/CombatTurnAuthority.kt"

CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
settled_core = all(
    marker in (CORE / filename).read_text(encoding="utf-8")
    for filename, marker in (
        ("CharacterStats.kt", "object CharacterStatProfiles"),
        ("CharacterEquipmentSystem.kt", "object CharacterStatEngine"),
        ("GameStateCodec.kt", "CharacterEquipmentSystem.normalize"),
        ("TurnCoordinator.kt", "applyCompletedTurnRegen"),
        ("CombatTurnAuthority.kt", "object CombatTurnAuthority"),
    )
)

if settled_core:
    authority = COMBAT_TURN_AUTHORITY.read_text(encoding="utf-8")
    for marker in (
        "object CombatTurnAuthority",
        "CharacterStatEngine.applyCompletedTurnRegen(next, completedTurnRegenToken(state))",
        'return "COMBAT_TURN_$turnNumber"',
    ):
        if marker not in authority:
            raise RuntimeError("Kotlin combat regeneration authority missing: " + marker)
    status_patch = ROOT / "patch-character-status-equipment-system.py"
    status_source = status_patch.read_text(encoding="utf-8")
    declarations_end = status_source.index("SYSTEM.write_text")
    ui_start = status_source.index("# --- Shared Character + Inventory Item Detail UI")
    tests_start = status_source.index("# --- Required regression tests")
    ui_source = status_source[:declarations_end] + status_source[ui_start:tests_start]
    exec(compile(ui_source, str(status_patch), "exec"), {"__name__": "__main__", "__file__": str(status_patch)})
    print("Character Stat/Equipment authority verified in checked-in Kotlin; no source rewrite performed.")
else:
    # Compatibility path for pre-materialization branches.
    runpy.run_path(str(ROOT / "patch-character-stat-schema.py"), run_name="__main__")
    status_patch = ROOT / "patch-character-status-equipment-system.py"
    status_source = status_patch.read_text(encoding="utf-8")
    legacy_combat_start = "# Combat actions are completed gameplay turns too. Regen token derives from the UI turn so save/load cannot reapply it.\n"
    legacy_combat_end = "# Gemini candidate inventory can never silently delete equipment definitions. Equipment remains one owned Item.\n"
    legacy_start = status_source.find(legacy_combat_start)
    legacy_end = status_source.find(legacy_combat_end, legacy_start)
    if legacy_start < 0 or legacy_end < 0:
        raise RuntimeError("Legacy combat regeneration block missing from character status patch")
    status_source = (
        status_source[:legacy_start]
        + "# Combat completed-turn regeneration is source-owned by CombatTurnAuthority.\n"
        + 'facade = FACADE.read_text(encoding="utf-8")\n\n'
        + status_source[legacy_end:]
    )
    exec(compile(status_source, str(status_patch), "exec"), {"__name__": "__main__", "__file__": str(status_patch)})
    runpy.run_path(str(ROOT / "patch-combat-hp-metadata-cleanup.py"), run_name="__main__")
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

# Final New Game + inventory capacity contract. Kotlin and its regression test are checked in;
# retain only the Java/WebView compatibility projection from the historical materializer.
capacity_patch = ROOT / "patch-newgame-inventory-capacity.py"
capacity_source = capacity_patch.read_text(encoding="utf-8")
capacity_markers = (
    (CORE / "CharacterEquipmentSystem.kt", "object InventoryCapacityPolicy"),
    (CORE / "InventoryPolicy.kt", "InventoryCapacityPolicy.usedSlots(state, ownerId, inventory)"),
    (CORE / "GameCoreFacade.kt", "fun resetNewGame(): String"),
    (ROOT / "app/src/test/java/com/rabpit/backroom/core/InventoryCapacityNewGameTest.kt", "equippedItemsConsumeZeroCapacityForAllFourCharacters"),
)
if all(marker in path.read_text(encoding="utf-8") for path, marker in capacity_markers):
    core_start = capacity_source.index("# ---------------------------------------------------------------------------\n# 1) Inventory capacity")
    java_start = capacity_source.index("main = MAIN.read_text")
    regression_start = capacity_source.index("# Regression gates.")
    compatibility_source = capacity_source[:core_start] + capacity_source[java_start:regression_start]
    exec(compile(compatibility_source, str(capacity_patch), "exec"), {"__name__": "__main__", "__file__": str(capacity_patch)})
    print("New Game/Inventory Kotlin authority verified; Java and WebView compatibility staged.")
else:
    runpy.run_path(str(capacity_patch), run_name="__main__")

# Final candidate-inventory semantics: capacity validation must evaluate the Inventory instance being
# mutated, while equipped Items cost zero slots and displaced equipment costs a carried slot.
runpy.run_path(str(ROOT / "patch-inventory-capacity-final-fix.py"), run_name="__main__")

# Final Entity authority pass. Run after status/equipment/visual-state patches so their anchors remain intact.
runpy.run_path(str(ROOT / "patch-unified-entity-spawn-pool.py"), run_name="__main__")
