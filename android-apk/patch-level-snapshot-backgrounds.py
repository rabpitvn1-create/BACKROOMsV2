from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
main = MAIN.read_text(encoding="utf-8")

# Keep the Game Master's narrative and authoritative Level state in the same transaction.
# Previously the required Gemini JSON omitted `level`, and MainActivity copied only title/location
# before handing the candidate to Game State Core. The GM could therefore describe a completed
# Level transition while the persisted state (and Snapshot input) remained on the previous Level.
lines = main.splitlines()
schema_indexes = [index for index, line in enumerate(lines) if "JSON bắt buộc:" in line]
if len(schema_indexes) != 1:
    raise RuntimeError(f"Level transition GM schema anchor: expected 1 line, found {len(schema_indexes)}")
schema_index = schema_indexes[0]
schema_line = lines[schema_index]
schema_tail = r'\"flags\":{}}";'
schema_replacement = r'\"flags\":{},\"level\":{\"number\":0,\"name\":\"Level 0\"}}";'
if schema_tail not in schema_line:
    raise RuntimeError("Level transition GM schema flags tail not found")
lines[schema_index] = schema_line.replace(schema_tail, schema_replacement, 1)
lines.insert(
    schema_index,
    '            "Nếu phản hồi xác nhận môi trường đã chuyển hẳn sang Level khác, phải đồng bộ title, location và level trong cùng JSON; không được mô tả đã hoàn tất chuyển Level nhưng giữ state ở Level cũ. " +',
)
main = "\n".join(lines) + ("\n" if main.endswith("\n") else "")

bridge_old = '''          String location = generated.optString("location", "").trim();
          if (!title.isEmpty()) state.put("title", title);
          if (!location.isEmpty()) state.put("location", location);
          JSONObject coreCommit = new JSONObject(gameCore.processValidatedCandidate(stateJson, state.toString(), action));'''
bridge_new = '''          String location = generated.optString("location", "").trim();
          if (!title.isEmpty()) state.put("title", title);
          if (!location.isEmpty()) state.put("location", location);
          JSONObject level = generated.optJSONObject("level");
          if (level != null && level.length() > 0) state.put("level", level); // LEVEL_TRANSITION_STATE_SYNC
          JSONObject coreCommit = new JSONObject(gameCore.processValidatedCandidate(stateJson, state.toString(), action));'''
count = main.count(bridge_old)
if count != 1:
    raise RuntimeError(f"Level transition core bridge anchor: expected 1 match, found {count}")
main = main.replace(bridge_old, bridge_new, 1)

old = "if(r){var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r.dataUri;bg.alt='Snapshot Turn '+(state.turn||'');box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);}else{"

new = "var refs={0:'file:///android_asset/level_snapshots/level_0.webp',1:'file:///android_asset/level_snapshots/level_1.webp',2:'file:///android_asset/level_snapshots/level_2.webp',3:'file:///android_asset/level_snapshots/level_3.webp',4:'file:///android_asset/level_snapshots/level_4.webp',5:'file:///android_asset/level_snapshots/level_5.webp',6:'file:///android_asset/level_snapshots/level_6.webp'};var explicit=Number(state&&state.level&&state.level.number);var where=String(state&&state.location||'')+' '+String(state&&state.title||'');var lm=where.match(/Level[^0-9]*([0-6])/i);var lv=Number.isFinite(explicit)&&explicit>=0&&explicit<=6?explicit:(lm?Number(lm[1]):0);var bg=document.createElement('img');bg.className='snapshot-bg';bg.src=r?r.dataUri:(refs[lv]||refs[0]);bg.alt=r?'Snapshot Turn '+(state.turn||''):'Level '+lv+' — Escape the Backrooms Wiki';if(!r)bg.onerror=function(){this.onerror=null;this.src=refs[0];};box.appendChild(bg);var kai=document.createElement('img');kai.className='snapshot-character';kai.src='file:///android_asset/kai_snapshot_overlay.webp';kai.alt='Kai Akechi';box.appendChild(kai);if(!r){"

count = main.count(old)
if count != 1:
    raise RuntimeError(f"Snapshot layered renderer anchor: expected 1 match, found {count}")
main = main.replace(old, new, 1)

old_css = ".snapshot-placeholder{position:relative;z-index:3;width:100%;height:100%;display:grid;place-items:center;gap:7px;text-align:center;color:#69737c}"
new_css = ".snapshot-placeholder{display:none}"
count = main.count(old_css)
if count != 1:
    raise RuntimeError(f"Snapshot placeholder style: expected 1 match, found {count}")
main = main.replace(old_css, new_css, 1)

for marker in (
    "LEVEL_TRANSITION_STATE_SYNC",
    r'\"level\":{\"number\":0,\"name\":\"Level 0\"}',
    "var explicit=Number(state&&state.level&&state.level.number)",
):
    if marker not in main:
        raise RuntimeError("Level transition regression marker missing: " + marker)

MAIN.write_text(main, encoding="utf-8")
print("Level transition state sync + local Level 0-6 Snapshot backgrounds enabled; Kai stays overlaid on top.")
