from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATCH = ROOT / "android-apk/patch-lucia-follower.py"
text = PATCH.read_text(encoding="utf-8")

start_marker = "# ---------------------------------------------------------------------------\n# Android runtime: 50% encounter only while EXPLORE is active in Level 0.\n"
end_marker = "\n\n# ---------------------------------------------------------------------------\n# Regression tests: stats cap, HP scale, three equipment slots, and 3x100 gift\n"
start = text.find(start_marker)
end = text.find(end_marker, start)
if start < 0 or end < 0:
    raise RuntimeError("Lucia runtime section markers missing")

runtime = r"""# ---------------------------------------------------------------------------
# Android runtime: 50% encounter only while EXPLORE is active in Level 0.
# This is self-contained and does not depend on any retired follower helper.
# ---------------------------------------------------------------------------
main = MAIN.read_text(encoding="utf-8")
if 'rolls.put("luciaEncounter"' not in main:
    roll_anchor = '    JSONObject normalEntityRoll = thresholdRoll("entityEncounter", 10000, entityThresholds[level], exploreAction && entityAllowed, entitySuffix);\n'
    roll_line = roll_anchor + '    rolls.put("luciaEncounter", thresholdRoll("luciaEncounter", 10000, 5000, exploreAction && level == 0 && !flagSpawned(state, "lucia"), " Level 0 Lucia follower encounter"));\n'
    if roll_anchor not in main:
        raise RuntimeError("Lucia encounter roll anchor missing")
    main = main.replace(roll_anchor, roll_line, 1)

# Let validated party/flag operations represent Lucia when and only when her locked roll succeeds.
old_character_gate = '''    if (value.contains("syvial")) return presentCharacter(before, "syvial") || rollSuccess(rolls, "syvialReunion");
    return rollSuccess(rolls, "survivor");
'''
new_character_gate = '''    if (value.contains("syvial")) return presentCharacter(before, "syvial") || rollSuccess(rolls, "syvialReunion");
    if (value.contains("lucia") || value.contains("lục")) return flagSpawned(before, "lucia") || rollSuccess(rolls, "luciaEncounter");
    return rollSuccess(rolls, "survivor");
'''
if new_character_gate not in main:
    if old_character_gate not in main:
        raise RuntimeError("Lucia character operation gate anchor missing")
    main = main.replace(old_character_gate, new_character_gate, 1)

old_flag_gate = '''    if (root.equals("syvial")) return presentCharacter(before, "syvial") || rollSuccess(rolls, "syvialReunion");
    if (root.equals("jeff")'''
new_flag_gate = '''    if (root.equals("syvial")) return presentCharacter(before, "syvial") || rollSuccess(rolls, "syvialReunion");
    if (root.equals("lucia")) return flagSpawned(before, "lucia") || rollSuccess(rolls, "luciaEncounter");
    if (root.equals("jeff")'''
if new_flag_gate not in main:
    if old_flag_gate not in main:
        raise RuntimeError("Lucia flag operation gate anchor missing")
    main = main.replace(old_flag_gate, new_flag_gate, 1)

# A successful locked roll commits the encounter even if the model omits the corresponding ops.
commit_anchor = '''    flags.put("lastRolls", rolls);
    state.put("flags", flags);
    return state;
'''
commit_block = '''    if (rollSuccess(rolls, "luciaEncounter")) {
      JSONArray party = state.optJSONArray("party");
      if (party == null) party = new JSONArray();
      boolean joined = arrayIndexByName(party, "Lucia \\"Lục\\"") >= 0;
      if (!joined && party.length() < 3) {
        party.put(new JSONObject()
          .put("id", "lucia")
          .put("name", "Lucia \\"Lục\\"")
          .put("present", true)
          .put("joinConfirmed", true)
          .put("presence", "ACTIVE")
          .put("role", "follower")
          .put("nonCombat", false));
        joined = true;
      }
      state.put("party", party);
      JSONObject lucia = flags.optJSONObject("lucia");
      if (lucia == null) lucia = new JSONObject();
      lucia.put("exists", true)
        .put("encountered", true)
        .put("present", true)
        .put("spawned", true)
        .put("follower", true)
        .put("reunionEligible", false)
        .put("continuity", "RECRUITED_LEVEL_0")
        .put("levelEncountered", 0)
        .put("joinPending", !joined);
      flags.put("lucia", lucia);
    }
    flags.put("lastRolls", rolls);
    state.put("flags", flags);
    return state;
'''
if commit_block not in main:
    if commit_anchor not in main:
        raise RuntimeError("Lucia deterministic encounter commit anchor missing")
    main = main.replace(commit_anchor, commit_block, 1)

snapshot_old = 'rollSuccess(rolls, "survivor") || rollSuccess(rolls, "irisReunion") || rollSuccess(rolls, "syvialReunion")'
snapshot_new = snapshot_old + ' || rollSuccess(rolls, "luciaEncounter")'
if snapshot_new not in main and snapshot_old in main:
    main = main.replace(snapshot_old, snapshot_new, 1)

if 'LUCIA FOLLOWER HARD LOCK:' not in main:
    return_anchor = '    return actionDirective + "\\nACTION_RUNTIME: " + actionRuntimeContext + "\\n" +\n'
    return_new = '    return actionDirective + "\\nLUCIA FOLLOWER HARD LOCK: Lucia \\\"Lục\\\", nữ 19 tuổi, con người, binh nhì và chỉ huy cấp tiểu đội đặc nhiệm. luciaEncounter chỉ roll khi EXPLORE ở Level 0, xác suất 50%, và chỉ success=true mới cho cô xuất hiện. Sau lần gặp đầu, không roll lại. Nếu Party còn chỗ cô gia nhập follower; nếu đầy thì giữ present + joinPending, không đuổi thành viên khác. HP nền 100; STR 7, DF 7, AGI 8, CRIT 7. Trang bị đúng 3 slot: M4A1 cá nhân hóa với laser xanh 5mW, dao găm chiến đấu, đồng hồ định vị quân sự mất tín hiệu vệ tinh. Đạn khởi đầu 150 viên gồm 60 đang nạp và 90 dự phòng; đây là nguồn đạn riêng, không chiếm 3 loại vật phẩm quà tặng. Inventory quà tặng tối đa 3 loại, tối đa 100 mỗi loại. Ở Level 0, Lucia chỉ nghi ngờ tiếng động giờ thứ 4 là Hound; không được xác nhận Hound cư trú ở Level 0. Không tự thêm năng lực siêu nhiên hoặc lore.\\nACTION_RUNTIME: " + actionRuntimeContext + "\\n" +\n'
    if return_anchor not in main:
        raise RuntimeError("Lucia GM prompt anchor missing")
    main = main.replace(return_anchor, return_new, 1)

for marker in (
    'thresholdRoll("luciaEncounter", 10000, 5000, exploreAction && level == 0 && !flagSpawned(state, "lucia")',
    'root.equals("lucia")',
    'rollSuccess(rolls, "luciaEncounter")',
    'LUCIA FOLLOWER HARD LOCK:',
):
    if marker not in main:
        raise RuntimeError("Lucia runtime contract missing: " + marker)

MAIN.write_text(main, encoding="utf-8")
"""

text = text[:start] + runtime + text[end:]
compile(text, str(PATCH), "exec")
PATCH.write_text(text, encoding="utf-8")
print("Lucia runtime decoupled from retired follower helpers.")
