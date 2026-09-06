from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
main = MAIN.read_text(encoding="utf-8")

# Final-layer fix: historical runtime patches depend on the legacy Gemini JSON contract, so Level
# transition synchronization is deliberately applied only after the entire existing patch chain.
lines = main.splitlines()
schema_indexes = [i for i, line in enumerate(lines) if "JSON bắt buộc:" in line]
if len(schema_indexes) != 1:
    raise RuntimeError(f"Level transition final schema: expected 1 line, found {len(schema_indexes)}")
schema_index = schema_indexes[0]
schema_line = lines[schema_index]
level_schema = r'\"level\":{\"number\":0,\"name\":\"Level 0\"}'
if level_schema not in schema_line:
    suffix = '}}";'
    end = schema_line.rfind(suffix)
    if end < 0:
        raise RuntimeError("Level transition final schema: JSON object suffix not found")
    schema_line = schema_line[:end] + r'},\"level\":{\"number\":0,\"name\":\"Level 0\"}}";' + schema_line[end + len(suffix):]
    lines[schema_index] = schema_line

instruction = '            "LEVEL TRANSITION STATE LOCK: Nếu phản hồi xác nhận môi trường đã chuyển hẳn sang Level khác, phải đồng bộ title, location và level trong cùng JSON; không được mô tả đã hoàn tất chuyển Level nhưng giữ state ở Level cũ. " +'
if "LEVEL TRANSITION STATE LOCK" not in "\n".join(lines):
    lines.insert(schema_index, instruction)
main = "\n".join(lines) + ("\n" if main.endswith("\n") else "")

# The Core already accepts levelJson, but the Gemini fallback bridge previously discarded generated.level
# before processValidatedCandidate(). Preserve that object in the legacy candidate so Core can persist it.
location_write = '          if (!location.isEmpty()) state.put("location", location);'
if "LEVEL_TRANSITION_STATE_SYNC" not in main:
    count = main.count(location_write)
    if count != 1:
        raise RuntimeError(f"Level transition final bridge: expected 1 location write, found {count}")
    level_write = '''          if (!location.isEmpty()) state.put("location", location);
          JSONObject generatedLevel = generated.optJSONObject("level");
          if (generatedLevel != null && generatedLevel.length() > 0) state.put("level", generatedLevel); // LEVEL_TRANSITION_STATE_SYNC'''
    main = main.replace(location_write, level_write, 1)

# Fail closed if a later edit removes any leg of the transaction. These markers are the focused
# regression guard for: GM says Level 1 -> structured Level 1 -> Core candidate -> Snapshot sees new state.
for marker in (
    "LEVEL TRANSITION STATE LOCK",
    level_schema,
    "generated.optJSONObject(\"level\")",
    "LEVEL_TRANSITION_STATE_SYNC",
    "gameCore.processValidatedCandidate",
):
    if marker not in main:
        raise RuntimeError("Level transition final regression marker missing: " + marker)

MAIN.write_text(main, encoding="utf-8")
print("Level transition final bridge verified: GM narrative, structured level, Core candidate and Snapshot state stay synchronized.")
