from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
CORE = ROOT / "app/src/main/java/com/rabpit/backroom/core"
TESTS = ROOT / "app/src/test/java/com/rabpit/backroom/core"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
INDEX = ROOT / "app/src/main/assets/index.html"
FACADE = CORE / "GameCoreFacade.kt"
ENGINES = CORE / "Engines.kt"
POLICY = CORE / "InventoryPolicy.kt"
REDUCER = CORE / "StateReducer.kt"
ITEM_TEST = TESTS / "ItemContentStateTest.kt"
PHYS_TEST = TESTS / "PhysiologyItemEffectTest.kt"
V4_TEST = TESTS / "InventoryV4ArchitectureTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 anchor, found {count}")
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Core item semantics: catalog-backed items are atomic whole units.
# ItemContent remains only as a compatibility reader for uncatalogued legacy data.
# ---------------------------------------------------------------------------
engines = ENGINES.read_text(encoding="utf-8")
add_start = engines.find("private fun addItem(")
add_end = engines.find("\nprivate fun removeItem(", add_start)
if add_start < 0 or add_end < 0:
    raise RuntimeError("Inventory addItem anchors missing")
atomic_add = r'''private fun addItem(inventory: InventoryState, rawItem: ItemStack): InventoryState {
  val definition = ItemCatalog.resolve(rawItem.itemId, rawItem.name)
  val item = if (definition != null) ItemCatalog.canonicalize(definition, rawItem)
    else ItemContentRules.normalize(rawItem)
  val old = inventory.items[item.itemId]
  val merged = if (old == null) item else old.copy(
    quantity = old.quantity + item.quantity,
    metadata = old.metadata + item.metadata,
    condition = null,
    archetypeId = item.itemId,
    contentState = ContentState.NONE
  )
  return inventory.copy(items = inventory.items + (item.itemId to merged))
}
'''
engines = engines[:add_start] + atomic_add + engines[add_end:]

use_start = engines.find("private fun useItem(")
use_end = engines.find("\nobject InventoryEngine", use_start)
if use_start < 0 or use_end < 0:
    raise RuntimeError("Inventory useItem anchors missing")
atomic_use = r'''private fun useItem(state: GameState, source: InventoryState, command: ItemCommand): ExecutionResult {
  val ownedRaw = source.items[command.itemId] ?: return invalid(state, "item_not_owned")
  if (ownedRaw.quantity < command.quantity) return invalid(state, "insufficient_item_quantity")
  val definition = ItemCatalog.resolve(ownedRaw.itemId, ownedRaw.name)
    ?: return invalid(state, "item_not_in_catalog")
  if (!definition.usable) return invalid(state, "item_use_not_supported")
  val owned = ItemCatalog.canonicalize(definition, ownedRaw)
  val physiologyEffects = parsePhysiologyEffects(owned.metadata["physiologyEffect"])
    ?: return invalid(state, "physiology_effect_invalid")
  val healingAmount = HealingItems.healAmount(owned)
  if (healingAmount > 0) {
    val actor = state.characters[command.actorId] ?: return invalid(state, "actor_unknown")
    if (actor.presence == CharacterPresence.DEAD || actor.vitalState.currentHp <= 0) {
      return invalid(state, "healing_target_defeated")
    }
  }
  val next = removeItem(source, command.itemId, command.quantity)
    ?: return invalid(state, "insufficient_item_quantity")
  val inventoryResult = changed(
    state.copy(inventories = state.inventories + (command.actorId to next)),
    "item_consumed"
  )
  return finishItemUse(state, inventoryResult, command, physiologyEffects, healingAmount)
}
'''
engines = engines[:use_start] + atomic_use + engines[use_end:]
ENGINES.write_text(engines, encoding="utf-8")

policy = POLICY.read_text(encoding="utf-8")
old_norm = '    val normalized = ItemContentRules.normalize(item)\n'
new_norm = '''    val normalized = ItemCatalog.resolve(item.itemId, item.name)?.let { ItemCatalog.canonicalize(it, item) }
      ?: ItemContentRules.normalize(item)
'''
policy = replace_once(policy, old_norm, new_norm, "InventoryPolicy catalog normalization")
POLICY.write_text(policy, encoding="utf-8")

reducer = REDUCER.read_text(encoding="utf-8")
remember_start = reducer.find("  private fun rememberedItemAfter(")
remember_end = reducer.find("\n\n  fun executeAll", remember_start)
if remember_start < 0 or remember_end < 0:
    raise RuntimeError("StateReducer remembered item anchors missing")
remember = r'''  private fun rememberedItemAfter(before: GameState, after: GameState, command: ItemCommand): String {
    val definition = ItemCatalog.resolve(command.itemId, command.itemName)
    return definition?.id ?: command.itemId
  }
'''
reducer = reducer[:remember_start] + remember + reducer[remember_end:]
REDUCER.write_text(reducer, encoding="utf-8")

# ---------------------------------------------------------------------------
# GameCore authority:
# - player prose cannot USE / TRANSFER / DISCARD Inventory items;
# - GM may only add positive catalog-backed rewards;
# - unknown narrative nouns never become authoritative item IDs;
# - UI mutations use a direct deterministic bridge and produce Vietnamese system messages.
# ---------------------------------------------------------------------------
facade = FACADE.read_text(encoding="utf-8")

pickup_block = '''    if (isDirectPlayerPickupAction(action) || interpreted.candidates.any { it.intent == GameIntent.PICKUP_ITEM }) {
      val result = syncLegacy(legacy, state, incrementTurn = false)
      val reply = validationReply("player_pickup_unavailable")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "player_pickup_unavailable")))
      return response(true, result, "player_pickup_unavailable", "validation_rejected", reply)
    }
'''
if pickup_block not in facade:
    raise RuntimeError("Player pickup authority block missing")
ui_lock = pickup_block + '''
    val uiOnlyItemIntent = interpreted.candidates.firstOrNull {
      it.intent in setOf(GameIntent.USE_ITEM, GameIntent.TRANSFER_ITEM, GameIntent.DROP_ITEM)
    }
    if (uiOnlyItemIntent != null) {
      val result = syncLegacy(legacy, state, incrementTurn = false)
      val reply = validationReply("inventory_ui_required")
      appendLog(result, action, reply)
      logger.log(PipelineLogEvent("REJECT", turnId = turnId, details = mapOf("reason" to "inventory_ui_required")))
      return response(true, result, "inventory_ui_required", "validation_rejected", reply)
    }
'''
facade = facade.replace(pickup_block, ui_lock, 1)

start = facade.find("    val desiredById = mutableMapOf<String, ItemStack>()")
end = facade.find("    val desiredParty = mutableMapOf<String, JSONObject>()", start)
if start < 0 or end < 0:
    raise RuntimeError("Gemini inventory delta anchors missing")
reward_block = r'''    val desiredById = current.toMutableMap()
    if (!inventoryLocked) {
      val desiredInventory = candidate.optJSONArray("inventory") ?: JSONArray()
      for (index in 0 until desiredInventory.length()) {
        val json = desiredInventory.optJSONObject(index) ?: continue
        val name = json.optString("name").trim(); if (name.isEmpty()) continue
        val requestedId = json.optString("id").trim().takeIf(String::isNotEmpty)
        val definition = ItemCatalog.resolve(requestedId, name) ?: continue
        if (!definition.rewardable) continue
        val old = desiredById[definition.id]
        val oldQuantity = old?.quantity ?: 0
        val proposedQuantity = json.optInt("quantity", 1).coerceAtLeast(1)
        if (proposedQuantity <= oldQuantity) continue
        val metadata = old?.metadata.orEmpty() + jsonObjectStrings(json.optJSONObject("metadata")) + definition.metadata
        desiredById[definition.id] = ItemCatalog.canonicalize(
          definition,
          ItemStack(definition.id, definition.displayName, proposedQuantity, metadata = metadata)
        )
      }
    }

    (current.keys + desiredById.keys).sorted().forEachIndexed { index, id ->
      val old = current[id]?.quantity ?: 0
      val desired = desiredById[id]?.quantity ?: 0
      if (desired <= old) return@forEachIndexed
      val stack = desiredById.getValue(id)
      commands += ItemCommand(
        "$turnId:GEMINI:INV:$index", turnId, KAI_ID, source = CommandSource.GEMINI,
        operation = ItemCommand.Operation.PICKUP,
        itemId = id, itemName = stack.name, quantity = desired - old, metadata = stack.metadata
      )
    }

'''
facade = facade[:start] + reward_block + facade[end:]

load_old = '''  private fun loadOrMigrate(legacy: JSONObject): GameState {
    if (repository.exists()) return repository.load()
    val migrated = GameStateCodec.decode(legacy)
    repository.save(migrated)
    return migrated
  }
'''
load_new = '''  private fun loadOrMigrate(legacy: JSONObject): GameState {
    val loaded = if (repository.exists()) repository.load() else GameStateCodec.decode(legacy)
    val normalized = InventoryV4State.normalize(loaded)
    if (!repository.exists() || normalized != loaded) repository.save(normalized)
    return normalized
  }
'''
facade = replace_once(facade, load_old, load_new, "Inventory V4 load normalization")

clear_anchor = "  fun clear() = repository.clear()\n"
ui_method = r'''  fun processInventoryUiAction(
    legacyStateJson: String,
    actorId: String,
    operation: String,
    itemId: String,
    quantity: Int,
    targetId: String?
  ): String {
    val legacy = JSONObject(legacyStateJson)
    val core = loadOrMigrate(legacy)
    val outcome = InventoryUiActions.execute(core, actorId, operation, itemId, quantity, targetId)
    val synchronized = syncLegacy(legacy, outcome.state, incrementTurn = false)
    if (outcome.applied) {
      repository.save(outcome.state)
      appendSystemLog(synchronized, outcome.message)
    }
    return JSONObject().apply {
      put("handled", true)
      put("applied", outcome.applied)
      put("state", synchronized)
      put("message", outcome.message)
      outcome.reason?.let { put("reason", it) }
    }.toString()
  }

'''
if "fun processInventoryUiAction(" not in facade:
    facade = replace_once(facade, clear_anchor, clear_anchor + ui_method, "Inventory UI facade method")

append_anchor = '''  private fun appendLog(state: JSONObject, action: String, reply: String) {
    val log = state.optJSONArray("log") ?: JSONArray().also { state.put("log", it) }
    log.put(JSONObject().put("role", "player").put("text", action))
    log.put(JSONObject().put("role", "gm").put("text", reply))
  }
'''
append_new = append_anchor + '''
  private fun appendSystemLog(state: JSONObject, message: String) {
    val log = state.optJSONArray("log") ?: JSONArray().also { state.put("log", it) }
    log.put(JSONObject().put("role", "system").put("text", message))
  }
'''
facade = replace_once(facade, append_anchor, append_new, "System inventory log helper")

# Player-facing text is Vietnamese only.
validation_start = facade.find("  private fun validationReply(reason: String): String {")
validation_end = facade.find("\n\n  companion object", validation_start)
if validation_start < 0 or validation_end < 0:
    raise RuntimeError("validationReply anchors missing")
validation = r'''  private fun validationReply(reason: String): String {
    val message = when (reason) {
      "player_pickup_unavailable" -> "Vật phẩm được phát trực tiếp qua sự kiện trong câu chuyện; không cần nhặt thủ công."
      "inventory_ui_required" -> "Hãy mở kho đồ của nhân vật để sử dụng, chuyển hoặc vứt bỏ vật phẩm."
      "restore_narrative_only" -> "Thao tác này không thể thay đổi vật phẩm trong kho đồ."
      "precise_content_amount_forbidden" -> "Kho đồ chỉ quản lý vật phẩm nguyên vẹn theo số lượng nguyên."
      "item_not_in_catalog" -> "Vật phẩm này không có trong danh mục vật phẩm."
      "item_use_not_supported" -> "Vật phẩm này không thể sử dụng trực tiếp từ kho đồ."
      "scan_source_missing", "scan_template_missing" -> "Không có vật phẩm hợp lệ để quét hoặc sao chép."
      "insufficient_item_quantity", "item_not_owned" -> "Nhân vật không có đủ vật phẩm này trong kho đồ."
      "party_full" -> "Đội đã đủ tối đa bốn thành viên."
      "join_not_confirmed" -> "Yêu cầu gia nhập chưa đủ điều kiện hoặc chưa được nhân vật xác nhận."
      "living_target_forbidden" -> "Nhẫn Vạn Tàng không thể tác động lên sinh vật sống."
      "restore_cooldown_active" -> "Vật phẩm này vẫn đang trong thời gian chờ Hoàn Nguyên 24 giờ."
      else -> "Không thể thực hiện hành động này."
    }
    return "[Cảnh báo] $message"
  }
'''
facade = facade[:validation_start] + validation + facade[validation_end:]
FACADE.write_text(facade, encoding="utf-8")

# ---------------------------------------------------------------------------
# Android bridge + writer contract.
# Discovery/reward is direct acquisition. There is no world-item pickup step.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
item_import = "import com.rabpit.backroom.core.ItemCatalog;\n"
if item_import not in main:
    core_import = "import com.rabpit.backroom.core.GameCoreFacade;\n"
    if core_import not in main:
        raise RuntimeError("GameCoreFacade import missing")
    main = main.replace(core_import, core_import + item_import, 1)

bridge_marker = "  private class GameBridge {\n"
helpers = r'''  private JSONArray confirmedInventoryHighlights(JSONObject before, JSONObject after) throws Exception {
    JSONArray result = new JSONArray();
    JSONArray oldInventory = before.optJSONArray("inventory");
    JSONArray newInventory = after.optJSONArray("inventory");
    java.util.HashMap<String, Integer> oldCounts = new java.util.HashMap<>();
    if (oldInventory != null) for (int i = 0; i < oldInventory.length(); i++) {
      JSONObject item = oldInventory.optJSONObject(i); if (item == null) continue;
      String id = item.optString("id", item.optString("name", ""));
      oldCounts.put(id, item.optInt("quantity", 1));
    }
    if (newInventory != null) for (int i = 0; i < newInventory.length(); i++) {
      JSONObject item = newInventory.optJSONObject(i); if (item == null) continue;
      String id = item.optString("id", item.optString("name", ""));
      int quantity = item.optInt("quantity", 1);
      if (quantity > oldCounts.getOrDefault(id, 0)) result.put(item.optString("name", id));
    }
    return result;
  }

'''
if "private JSONArray confirmedInventoryHighlights(" not in main:
    main = replace_once(main, bridge_marker, helpers + bridge_marker, "Confirmed item highlight helper")

inventory_bridge = r'''    @JavascriptInterface public void inventoryAction(String stateJson, String actorId, String operation, String itemId, int quantity, String targetId) {
      io.execute(() -> {
        try {
          String result = gameCore.processInventoryUiAction(
            stateJson,
            actorId,
            operation,
            itemId,
            quantity,
            targetId == null || targetId.trim().isEmpty() ? null : targetId
          );
          emit("backroomInventoryAction", result);
        } catch (Exception e) {
          emit("backroomInventoryAction", new JSONObject()
            .put("handled", true)
            .put("applied", false)
            .put("message", "Không thể thực hiện thao tác vật phẩm.")
            .toString());
        }
      });
    }

'''
if "@JavascriptInterface public void inventoryAction(" not in main:
    main = replace_once(main, bridge_marker, bridge_marker + inventory_bridge, "Inventory Android bridge")

# Add the whitelist to the final writer prompt and invert old manual-pickup language.
if "String itemCatalogDirective =" not in main:
    bridge_pos = main.find("  private class GameBridge {")
    prompt_pos = main.find("          String prompt =", bridge_pos)
    if prompt_pos < 0:
        raise RuntimeError("Writer prompt anchor missing")
    directive = '''          String itemCatalogDirective = "DANH MỤC VẬT PHẨM HARD LOCK: chỉ các vật phẩm sau mới được tạo trong kho đồ: " + ItemCatalog.promptCatalog() + ". Vật ngoài danh mục chỉ là bối cảnh, không được tự tạo ID hay thêm vào kho đồ. Khi Kai phát hiện hoặc nhận một vật phẩm hợp lệ từ loot/reward đã được xác nhận, thêm trực tiếp vật phẩm đó vào inventory trong cùng lượt; không viết bước nhặt, lượm, cúi xuống lấy hoặc chờ người chơi loot. ";\n'''
    main = main[:prompt_pos] + directive + main[prompt_pos:]
    main = main[:main.find("          String prompt =", prompt_pos + len(directive))] + main[main.find("          String prompt =", prompt_pos + len(directive)):]
    prompt_pos = main.find("          String prompt =", prompt_pos)
    main = main[:prompt_pos] + main[prompt_pos:].replace("          String prompt = ", "          String prompt = itemCatalogDirective + ", 1)

main = main.replace("Nhìn thấy không đồng nghĩa sở hữu.", "Phát hiện một vật phẩm hợp lệ đồng nghĩa vật phẩm đó được cấp trực tiếp vào kho đồ trong cùng lượt.")
main = main.replace("không tự đặt vật phẩm vào Inventory", "phải được cấp trực tiếp vào kho đồ khi Game Master xác nhận phát hiện hợp lệ")
main = main.replace("không tự đặt vật phẩm vào inventory", "phải được cấp trực tiếp vào kho đồ khi Game Master xác nhận phát hiện hợp lệ")

# Attach only confirmed acquired item names to the GM log entry for safe cyan-green highlighting.
log_line = '          log.put(new JSONObject().put("role", "gm").put("text", reply));\n'
if log_line in main and "confirmedInventoryHighlights(before, state)" not in main:
    main = main.replace(log_line, '          log.put(new JSONObject().put("role", "gm").put("text", reply).put("itemHighlights", confirmedInventoryHighlights(before, state)));\n', 1)
MAIN.write_text(main, encoding="utf-8")

# ---------------------------------------------------------------------------
# Final Inventory UI. Inventory cards open a Vietnamese action sheet.
# Actions never submit player prose to the GM.
# ---------------------------------------------------------------------------
html = INDEX.read_text(encoding="utf-8")
if 'id="inventoryV4ActionSheet"' not in html:
    modal = r'''
<div id="inventoryV4ActionSheet" class="inventory-v4-modal" hidden>
  <div class="inventory-v4-sheet">
    <button type="button" id="inventoryV4Close" class="inventory-v4-close">×</button>
    <div class="eyebrow">VẬT PHẨM</div>
    <h2 id="inventoryV4Name">Vật phẩm</h2>
    <div id="inventoryV4Quantity" class="inventory-v4-count">Số lượng: 1</div>
    <div class="inventory-v4-actions">
      <button type="button" id="inventoryV4Use">SỬ DỤNG</button>
      <button type="button" id="inventoryV4Transfer">CHUYỂN</button>
      <button type="button" id="inventoryV4Discard">VỨT BỎ</button>
    </div>
    <div id="inventoryV4TransferPanel" class="inventory-v4-panel" hidden>
      <label>Chuyển cho<select id="inventoryV4Target"></select></label>
      <label>Số lượng<input id="inventoryV4TransferQuantity" type="number" min="1" value="1"></label>
      <button type="button" id="inventoryV4TransferConfirm">XÁC NHẬN CHUYỂN</button>
    </div>
    <div id="inventoryV4DiscardPanel" class="inventory-v4-panel" hidden>
      <label>Số lượng<input id="inventoryV4DiscardQuantity" type="number" min="1" value="1"></label>
      <button type="button" id="inventoryV4DiscardConfirm">XÁC NHẬN VỨT BỎ</button>
    </div>
    <div id="inventoryV4Message" class="inventory-v4-message"></div>
  </div>
</div>
'''
    if "</body>" not in html:
        raise RuntimeError("Inventory V4 body anchor missing")
    html = html.replace("</body>", modal + "</body>", 1)

if ".inventory-v4-modal{" not in html:
    css = r'''<style id="inventoryV4Styles">
.inventory-v4-modal{position:fixed;inset:0;z-index:180;background:rgba(0,0,0,.76);display:flex;align-items:flex-end;justify-content:center}.inventory-v4-modal[hidden]{display:none}.inventory-v4-sheet{position:relative;width:min(680px,100%);background:#0b0e11;border:1px solid #3b464e;border-bottom:0;padding:18px}.inventory-v4-close{position:absolute;right:10px;top:10px;width:38px;height:38px}.inventory-v4-count{color:#8c99a2;margin:6px 0 14px}.inventory-v4-actions{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.inventory-v4-panel{margin-top:12px;padding-top:12px;border-top:1px solid #2f383e;display:grid;gap:10px}.inventory-v4-panel[hidden]{display:none}.inventory-v4-panel label{display:grid;gap:5px;color:#9aa6ad;font-size:12px}.inventory-v4-panel select,.inventory-v4-panel input{background:#0a0d10;color:#e1e7ea;border:1px solid #3a454c;padding:10px}.inventory-v4-message{min-height:18px;margin-top:12px;color:#e5c37d;font-size:12px}.game-item-confirmed{color:#38e6c5;font-weight:800;text-decoration:underline;text-underline-offset:2px}.message.system{border-left-color:#38e6c5}.message.system .role{color:#38e6c5}.message.system .text{color:#dce7e5}
</style>
'''
    if "</head>" not in html:
        raise RuntimeError("Inventory V4 head anchor missing")
    html = html.replace("</head>", css + "</head>", 1)

if 'id="inventoryV4Runtime"' not in html:
    script = r'''<script id="inventoryV4Runtime">
(function(){
  const sheet=document.getElementById('inventoryV4ActionSheet');
  const nameEl=document.getElementById('inventoryV4Name');
  const countEl=document.getElementById('inventoryV4Quantity');
  const messageEl=document.getElementById('inventoryV4Message');
  const transferPanel=document.getElementById('inventoryV4TransferPanel');
  const discardPanel=document.getElementById('inventoryV4DiscardPanel');
  const targetEl=document.getElementById('inventoryV4Target');
  const transferQty=document.getElementById('inventoryV4TransferQuantity');
  const discardQty=document.getElementById('inventoryV4DiscardQuantity');
  let current=null;
  function escText(s){return String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
  function members(){return state&&state.partyDetails&&Array.isArray(state.partyDetails.members)?state.partyDetails.members:[]}
  function selectedId(){const v=document.getElementById('characterInventoryView');return v&&v.dataset.characterId||'kai'}
  function selectedMember(){const id=selectedId();return members().find(m=>String(m.id)===String(id))}
  function inventoryItem(id){const m=selectedMember();return m&&Array.isArray(m.inventory)?m.inventory.find(x=>String(x.id)===String(id)):null}
  function activeTargets(actorId){return members().filter(m=>String(m.id)!==String(actorId)&&String(m.presence||'ACTIVE').toUpperCase()==='ACTIVE')}
  function close(){sheet.hidden=true;transferPanel.hidden=true;discardPanel.hidden=true;messageEl.textContent='';current=null}
  function open(item){if(!item)return;current={actorId:selectedId(),itemId:String(item.id),name:String(item.name||item.id),quantity:Math.max(1,Number(item.quantity)||1)};nameEl.textContent=current.name;countEl.textContent='Số lượng: '+current.quantity;transferQty.max=String(current.quantity);discardQty.max=String(current.quantity);transferQty.value='1';discardQty.value='1';const targets=activeTargets(current.actorId);targetEl.innerHTML=targets.map(m=>'<option value="'+escText(m.id)+'">'+escText(m.name||m.id)+'</option>').join('');document.getElementById('inventoryV4Transfer').disabled=targets.length===0;sheet.hidden=false}
  function send(operation,quantity,target){if(!current)return;if(!window.Android||typeof Android.inventoryAction!=='function'){messageEl.textContent='Không tìm thấy cầu nối kho đồ.';return}messageEl.textContent='Đang xử lý…';Android.inventoryAction(JSON.stringify(state),current.actorId,operation,current.itemId,Math.max(1,Number(quantity)||1),target||'')}
  document.addEventListener('click',ev=>{const card=ev.target.closest('.inventory-item-card[data-item-id]');if(!card)return;const view=document.getElementById('characterInventoryView');if(!view||view.hidden)return;ev.preventDefault();ev.stopImmediatePropagation();open(inventoryItem(card.getAttribute('data-item-id')))},true);
  document.getElementById('inventoryV4Close').addEventListener('click',close);
  sheet.addEventListener('click',ev=>{if(ev.target===sheet)close()});
  document.getElementById('inventoryV4Use').addEventListener('click',()=>send('USE',1,''));
  document.getElementById('inventoryV4Transfer').addEventListener('click',()=>{transferPanel.hidden=!transferPanel.hidden;discardPanel.hidden=true});
  document.getElementById('inventoryV4Discard').addEventListener('click',()=>{discardPanel.hidden=!discardPanel.hidden;transferPanel.hidden=true});
  document.getElementById('inventoryV4TransferConfirm').addEventListener('click',()=>send('TRANSFER',transferQty.value,targetEl.value));
  document.getElementById('inventoryV4DiscardConfirm').addEventListener('click',()=>send('DISCARD',discardQty.value,''));
  function decorateLog(){const entries=state&&Array.isArray(state.log)?state.log:[];const nodes=document.querySelectorAll('#log .message');for(let i=0;i<Math.min(entries.length,nodes.length);i++){const entry=entries[i],node=nodes[i],role=node.querySelector('.role'),text=node.querySelector('.text');if(!text)continue;const kind=String(entry.role||'gm');node.classList.toggle('system',kind==='system');if(role)role.textContent=kind==='player'?'BẠN':kind==='system'?'HỆ THỐNG':'GAME MASTER';let rendered=escText(entry.text||'');const highlights=Array.isArray(entry.itemHighlights)?entry.itemHighlights:[];highlights.sort((a,b)=>String(b).length-String(a).length).forEach(name=>{const safe=escText(name);if(!safe)return;rendered=rendered.split(safe).join('<strong class="game-item-confirmed"><u>'+safe+'</u></strong>')});text.innerHTML=rendered}}
  const previousRender=window.render;if(typeof previousRender==='function')window.render=function(){previousRender();decorateLog()};
  window.backroomInventoryAction=function(payload){try{const r=JSON.parse(payload);if(r.state){state=r.state;try{localStorage.setItem('backroom-apk-state',JSON.stringify(state))}catch(ignore){}if(typeof window.render==='function')window.render()}messageEl.textContent=r.message||'';if(r.applied){setTimeout(close,260)}}catch(e){messageEl.textContent='Không thể cập nhật kho đồ.'}};
  setTimeout(decorateLog,0);
})();
</script>
'''
    html = html.replace("</body>", script + "</body>", 1)
INDEX.write_text(html, encoding="utf-8")

# ---------------------------------------------------------------------------
# Regression tests: whole-unit consumption, UI-only player management,
# catalog whitelist, direct transfer/discard semantics, no residual containers.
# ---------------------------------------------------------------------------
ITEM_TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class ItemContentStateTest {
  private fun grant(name: String, id: String, quantity: Int = 1): GameState {
    val result = StateReducer.execute(GameState.initial(), ItemCommand(
      "grant-$id", "TURN_1", KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP, itemId = id, itemName = name, quantity = quantity
    ))
    assertTrue(result.applied)
    return result.state
  }

  @Test fun waterBottleIsAtomicAndVanishesWhenUsed() {
    val state = grant("Chai nước", "water-bottle", 2)
    val first = StateReducer.execute(state, ItemCommand(
      "use-water-1", "TURN_1", KAI_ID, source = CommandSource.UI,
      operation = ItemCommand.Operation.USE, itemId = "water-bottle", itemName = "Chai nước"
    ))
    assertTrue(first.applied)
    assertEquals(1, first.state.inventories.getValue(KAI_ID).items.getValue("water-bottle").quantity)
    val second = StateReducer.execute(first.state, ItemCommand(
      "use-water-2", "TURN_1", KAI_ID, source = CommandSource.UI,
      operation = ItemCommand.Operation.USE, itemId = "water-bottle", itemName = "Chai nước"
    ))
    assertTrue(second.applied)
    assertFalse(second.state.inventories.getValue(KAI_ID).items.containsKey("water-bottle"))
    assertTrue(second.state.inventories.getValue(KAI_ID).items.keys.none { it.contains(":empty") || it.contains(":low") })
  }

  @Test fun legacyResidualObjectsAreRemovedDuringV4Normalization() {
    val legacy = GameState.initial().copy(inventories = mapOf(KAI_ID to InventoryState(KAI_ID, mapOf(
      "water-bottle:empty" to ItemStack("water-bottle:empty", "Chai rỗng", contentState = ContentState.EMPTY),
      "ammo-cartridge:empty" to ItemStack("ammo-cartridge:empty", "Vỏ đạn", contentState = ContentState.EMPTY),
      "water-bottle:low" to ItemStack("water-bottle:low", "Chai nước còn ít nước")
    ))))
    val normalized = InventoryV4State.normalize(legacy)
    val items = normalized.inventories.getValue(KAI_ID).items
    assertEquals(setOf("water-bottle"), items.keys)
    assertEquals(1, items.getValue("water-bottle").quantity)
    assertEquals(ContentState.NONE, items.getValue("water-bottle").contentState)
  }
}
''', encoding="utf-8")

phys = PHYS_TEST.read_text(encoding="utf-8")
phys = phys.replace('"water-bottle:full"', '"water-bottle"')
phys = phys.replace('"food-container:full"', '"food-container"')
# Retire the old empty-container regression; empty containers no longer exist as inventory state.
empty_start = phys.find("  @Test fun failedUseDoesNotApplyPhysiologyEffect()")
if empty_start >= 0:
    empty_end = phys.find("\n\n  @Test", empty_start + 10)
    if empty_end < 0:
        raise RuntimeError("Physiology empty-item test end anchor missing")
    phys = phys[:empty_start] + phys[empty_end + 2:]
PHYS_TEST.write_text(phys, encoding="utf-8")

V4_TEST.write_text(r'''package com.rabpit.backroom.core

import org.junit.Assert.*
import org.junit.Test

class InventoryV4ArchitectureTest {
  private fun partyState(): GameState {
    val base = GameState.initial()
    val iris = CharacterState("iris", "Iris")
    return base.copy(
      characters = base.characters + ("iris" to iris),
      party = base.party.copy(memberIds = listOf(KAI_ID, "iris")),
      inventories = base.inventories + ("iris" to InventoryState("iris"))
    )
  }

  @Test fun catalogRejectsUnknownNarrativeItems() {
    assertNull(ItemCatalog.resolve(null, "Máy quay mini không xác định"))
    assertNotNull(ItemCatalog.resolve(null, "Chai nước"))
  }

  @Test fun uiTransferMovesWholeUnitsBetweenAuthoritativeInventories() {
    var state = partyState()
    state = StateReducer.execute(state, ItemCommand(
      "grant-water", "TURN_1", KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP, itemId = "water-bottle", itemName = "Chai nước", quantity = 2
    )).state
    val outcome = InventoryUiActions.execute(state, KAI_ID, "TRANSFER", "water-bottle", 1, "iris")
    assertTrue(outcome.applied)
    assertEquals("Kai Akechi đưa Chai nước x1 cho Iris", outcome.message)
    assertEquals(1, outcome.state.inventories.getValue(KAI_ID).items.getValue("water-bottle").quantity)
    assertEquals(1, outcome.state.inventories.getValue("iris").items.getValue("water-bottle").quantity)
  }

  @Test fun uiDiscardDestroysUnitInsteadOfCreatingWorldLoot() {
    var state = partyState()
    state = StateReducer.execute(state, ItemCommand(
      "grant-food", "TURN_1", KAI_ID, source = CommandSource.SYSTEM,
      operation = ItemCommand.Operation.PICKUP, itemId = "food-container", itemName = "Hộp đồ hộp"
    )).state
    val outcome = InventoryUiActions.execute(state, KAI_ID, "DISCARD", "food-container", 1, null)
    assertTrue(outcome.applied)
    assertFalse(outcome.state.inventories.getValue(KAI_ID).items.containsKey("food-container"))
    assertTrue(outcome.state.world.keys.none { it.contains("item", true) || it.contains("loot", true) })
  }
}
''', encoding="utf-8")

# Fail closed if any architectural contract disappears from the final generated runtime.
combined = "\n".join([
    ENGINES.read_text(encoding="utf-8"),
    POLICY.read_text(encoding="utf-8"),
    REDUCER.read_text(encoding="utf-8"),
    FACADE.read_text(encoding="utf-8"),
    MAIN.read_text(encoding="utf-8"),
    INDEX.read_text(encoding="utf-8"),
    ITEM_TEST.read_text(encoding="utf-8"),
    V4_TEST.read_text(encoding="utf-8"),
])
for marker in (
    "ItemCatalog.resolve(rawItem.itemId, rawItem.name)",
    '"item_consumed"',
    "InventoryV4State.normalize(loaded)",
    "inventory_ui_required",
    "fun processInventoryUiAction(",
    "@JavascriptInterface public void inventoryAction(",
    "ItemCatalog.promptCatalog()",
    "confirmedInventoryHighlights(before, state)",
    'id="inventoryV4ActionSheet"',
    "window.backroomInventoryAction=function(payload)",
    "game-item-confirmed",
    "class InventoryV4ArchitectureTest",
):
    if marker not in combined:
        raise RuntimeError("Inventory V4 final contract missing: " + marker)

for forbidden in (
    "Chai rỗng x",
    "Vỏ đạn x",
    "This action is not available.",
    "There is no object available for scanning or multiplying.",
):
    if forbidden in FACADE.read_text(encoding="utf-8"):
        raise RuntimeError("Player-facing non-Vietnamese/residual inventory text survived: " + forbidden)

print("Inventory V4 final applied: catalog rewards, atomic consumption, UI-only management, Vietnamese feedback, confirmed item highlighting.")
