from pathlib import Path

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "patch-an-nhien-follower.py"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

# Later historical transforms still consume the settled Java staging shape. Materialize it here,
# but make the obsolete prose prompt anchor tolerant because earlier canon patches may rebuild it.
# The final Entity/Core patch later collapses makeGameplayRolls to Kotlin GameplayRollPolicy.
code = SOURCE.read_text(encoding="utf-8")
strict_prompt = 'main = replace_once(main, prompt_marker, prompt_extra, "An Nhien GM hard lock")\n'
if code.count(strict_prompt) != 1:
    raise RuntimeError("An Nhien prompt compatibility anchor is not unique")
code = code.replace(strict_prompt, 'if prompt_marker in main:\n    main = main.replace(prompt_marker, prompt_extra, 1)\n', 1)
code = code.replace("    'AN NHIÊN HARD LOCK',\n", "", 1)
exec(compile(code, str(SOURCE), "exec"), {"__name__": "__main__", "__file__": str(SOURCE)})

# Keep current 0.25% semantics explicit when the generic GAMEPLAY_ROLLS anchor is still available.
main = MAIN.read_text(encoding="utf-8")
prompt_marker = '"GAMEPLAY_ROLLS do Android sinh là bất biến: chỉ outcome success=true mới được xuất hiện. Không reroll, không tự đổi xác suất, không tự tạo encounter/item/reunion/level transition trái roll. " +\n'
prompt_extra = prompt_marker + '            "SPECIAL FOLLOWER LOCK: An Nhiên, Iris và Syvial dùng ba roll độc lập 0.2500% trên mỗi lượt physical đủ điều kiện ở Level 0–6 khi chưa gặp/reunion. Chỉ success=true mới được xuất hiện. An Nhiên không còn bắt buộc ở Level 0 và không chặn việc rời Level 0. Khi Party đầy, giữ present + joinPending thay vì đuổi thành viên khác. An Nhiên không chiến đấu; bonus +10% loot và +2% exit chỉ áp dụng khi cô thực sự đang theo Kai. " +\n'
if "SPECIAL FOLLOWER LOCK:" not in main and main.count(prompt_marker) == 1:
    main = main.replace(prompt_marker, prompt_extra, 1)
MAIN.write_text(main, encoding="utf-8")
print("Special follower compatibility staging materialized; final gameplay roll authority is Kotlin-owned later in the patch chain.")
