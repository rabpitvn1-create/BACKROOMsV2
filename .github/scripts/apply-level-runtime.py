from pathlib import Path

main_path = Path('android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java')
main = main_path.read_text(encoding='utf-8')
changed = False

old_state = '          JSONObject state = new JSONObject(stateJson);\n          String levelContext = gameCore.levelPromptContext(stateJson);\n'
new_state = '          JSONObject state = localResult.optJSONObject("state");\n          if (state == null) state = new JSONObject(stateJson);\n          String levelContext = gameCore.levelPromptContext(state.toString());\n'
if old_state in main:
    main = main.replace(old_state, new_state, 1)
    changed = True
elif new_state not in main:
    raise SystemExit('Normalized Level state marker not found')

old_schema = '            "\\nJSON bắt buộc: {\\\"reply\\\":\\\"phản hồi Game Master\\\",\\\"title\\\":\\\"giữ nguyên hoặc cập nhật\\\",\\\"currentLevel\\\":0,\\\"location\\\":\\\"vị trí sau lượt\\\",\\\"player\\\":{},\\\"party\\\":[],\\\"inventory\\\":[],\\\"flags\\\":{}}";\n'
new_schema = '            "\\nJSON bắt buộc: {\\\"reply\\\":\\\"phản hồi Game Master\\\",\\\"title\\\":\\\"giữ nguyên hoặc cập nhật\\\",\\\"currentLevel\\\":" + state.optInt("currentLevel", 0) + ",\\\"location\\\":\\\"vị trí sau lượt\\\",\\\"player\\\":{},\\\"party\\\":[],\\\"inventory\\\":[],\\\"flags\\\":{}}";\n'
if old_schema in main:
    main = main.replace(old_schema, new_schema, 1)
    changed = True
elif new_schema not in main:
    raise SystemExit('Dynamic currentLevel schema marker not found')

if changed:
    main_path.write_text(main, encoding='utf-8')
else:
    print('Level runtime migration already applied.')
