from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

old = "    JSONObject previous = new JSONObject(before.toString());\n"
new = "    JSONObject previous = applyModelOperations(before, new JSONArray(), rolls, action);\n"
count = text.count(old)
if count != 1:
    raise RuntimeError(f"operation diagnostic bookkeeping baseline expected once, found {count}")
text = text.replace(old, new, 1)

if "JSONObject previous = applyModelOperations(before, new JSONArray(), rolls, action);" not in text:
    raise RuntimeError("operation diagnostic baseline marker missing")

MAIN.write_text(text, encoding="utf-8")
print("Per-operation debug acceptance now compares against reducer bookkeeping baseline instead of raw pre-turn state.")
