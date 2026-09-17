from pathlib import Path

main_path = Path('android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java')
main = main_path.read_text(encoding='utf-8')

replacements = [
    (
        ".snapshot>img.snapshot-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;z-index:1}",
        ".snapshot>img.snapshot-bg{position:absolute;inset:0;width:100%;height:100%;object-fit:cover;z-index:1}.snapshot>img.snapshot-map{object-fit:contain;background:#050607}"
    ),
    (
        '      "function cachedSnapshot(){try{var r=JSON.parse(localStorage.getItem(\'backroom-apk-snapshot\')||\'null\');return r&&Number(r.turn)===Number(state&&state.turn)&&r.dataUri?r:null;}catch(e){return null;}}" +\n',
        '      "function cachedSnapshot(){try{var r=JSON.parse(localStorage.getItem(\'backroom-apk-snapshot\')||\'null\');return r&&Number(r.turn)===Number(state&&state.turn)&&r.dataUri?r:null;}catch(e){return null;}}" +\n'
        '      "function localLevelSnapshot(){try{if(!window.Android||typeof Android.levelSnapshot!==\'function\')return null;return JSON.parse(Android.levelSnapshot(JSON.stringify(state)));}catch(e){return null;}}" +\n'
    ),
    (
        '      "function renderSnapshot(){var box=document.getElementById(\'snapshot\');if(!box)return;box.textContent=\'\';var r=cachedSnapshot();if(r){var img=document.createElement(\'img\');img.className=\'snapshot-bg\';img.src=r.dataUri;img.alt=\'Snapshot Turn \'+(state.turn||\'\');box.appendChild(img);}else{var p=document.createElement(\'div\');p.className=\'snapshot-placeholder\';p.innerHTML=\'<b>GEMINI SNAPSHOT</b><small>Chưa có ảnh của turn hiện tại.</small>\';box.appendChild(p);}appendSnapshotOverlay(box);}" +\n',
        '      "function renderSnapshot(){var box=document.getElementById(\'snapshot\');if(!box)return;box.textContent=\'\';var r=cachedSnapshot();if(r){var img=document.createElement(\'img\');img.className=\'snapshot-bg\';img.src=r.dataUri;img.alt=\'Snapshot Turn \'+(state.turn||\'\');box.appendChild(img);}else{var local=localLevelSnapshot();if(local&&local.path){var img=document.createElement(\'img\');img.className=\'snapshot-bg\'+(local.visualType===\'map\'?\' snapshot-map\':\'\');img.src=local.path;img.alt=\'Level \'+local.level+\' Snapshot\';box.appendChild(img);}else{var p=document.createElement(\'div\');p.className=\'snapshot-placeholder\';p.innerHTML=\'<b>LEVEL SNAPSHOT</b><small>Không có ảnh cho Level hiện tại.</small>\';box.appendChild(p);}}appendSnapshotOverlay(box);}" +\n'
    ),
    (
        '  private String snapshotPrompt(JSONObject state) {\n    StringBuilder recent = new StringBuilder();\n',
        '  private String snapshotPrompt(JSONObject state) {\n    String levelContext = gameCore.levelPromptContext(state.toString());\n    StringBuilder recent = new StringBuilder();\n'
    ),
    (
        '      "Do not invent NPCs, monsters, exits, loot, injuries, weapons, text, HUD, blood or props that are not explicitly present in the state. " +\n      "If party is empty, Kai is alone. Level 0 uses stale yellow wallpaper, damp carpet, fluorescent ceiling panels and oppressive empty office-like geometry. " +\n      "Photorealistic cinematic game concept art, grounded anatomy and materials, no written text in the image.\\n\\n" +\n',
        '      "Do not invent NPCs, monsters, exits, loot, injuries, weapons, text, HUD, blood or props that are not explicitly present in the state. " +\n      "If party is empty, Kai is alone. Follow the CURRENT LEVEL CANON exactly and do not borrow architecture from another Level. " +\n      "Photorealistic cinematic game concept art, grounded anatomy and materials, no written text in the image.\\n\\n" +\n      levelContext + "\\n\\n" +\n'
    ),
    (
        '          JSONObject state = new JSONObject(stateJson);\n          String prompt = "Bạn là Game Master của text game Backrooms. Xử lý đúng một lượt và trả DUY NHẤT JSON hợp lệ, không markdown. " +\n',
        '          JSONObject state = new JSONObject(stateJson);\n          String levelContext = gameCore.levelPromptContext(stateJson);\n          String prompt = "Bạn là Game Master của text game Backrooms. Xử lý đúng một lượt và trả DUY NHẤT JSON hợp lệ, không markdown. " +\n'
    ),
    (
        '            "ENTITY OVERLAY STATE CONTRACT: flags.entityEncounterKey bắt buộc có ở mọi lượt AI. Nếu một Entity đang trực tiếp hiện diện hoặc đối đầu, đặt canonical key local tương ứng; nếu không còn Entity trực tiếp hiện diện thì đặt chuỗi rỗng. Canonical keys: hound, clump, duller, deathmoth, hostile_faceling, false_puddle, paintings, smiler, skin-stealer, predatory_window, biological_pipeline, wretch, cable_mimic, the_beast_of_level_5, hotel_corpse_lure, jeff_the_killer, jane_the_killer, slenderman, diep_minh. Không dùng đường dẫn, URL hoặc alias khác. " +\n            "State hiện tại: " + state.toString() + "\\nHành động: " + action +\n            "\\nJSON bắt buộc: {\\\"reply\\\":\\\"phản hồi Game Master\\\",\\\"title\\\":\\\"giữ nguyên hoặc cập nhật\\\",\\\"location\\\":\\\"vị trí sau lượt\\\",\\\"player\\\":{},\\\"party\\\":[],\\\"inventory\\\":[],\\\"flags\\\":{}}";\n',
        '            "ENTITY OVERLAY STATE CONTRACT: flags.entityEncounterKey bắt buộc có ở mọi lượt AI. Nếu một Entity đang trực tiếp hiện diện hoặc đối đầu, đặt canonical key local tương ứng; nếu không còn Entity trực tiếp hiện diện thì đặt chuỗi rỗng. Canonical keys: hound, clump, duller, deathmoth, hostile_faceling, false_puddle, paintings, smiler, skin-stealer, predatory_window, biological_pipeline, wretch, cable_mimic, the_beast_of_level_5, hotel_corpse_lure, jeff_the_killer, jane_the_killer, slenderman, diep_minh. Không dùng đường dẫn, URL hoặc alias khác. " +\n            "LEVEL CORE CONTRACT: currentLevel bắt buộc là số nguyên 0-6. Nếu chưa thực sự đi qua một route/boundary hợp lệ thì giữ nguyên currentLevel. Không được teleport sang Level không kết nối. " +\n            levelContext + "\\n" +\n            "State hiện tại: " + state.toString() + "\\nHành động: " + action +\n            "\\nJSON bắt buộc: {\\\"reply\\\":\\\"phản hồi Game Master\\\",\\\"title\\\":\\\"giữ nguyên hoặc cập nhật\\\",\\\"currentLevel\\\":0,\\\"location\\\":\\\"vị trí sau lượt\\\",\\\"player\\\":{},\\\"party\\\":[],\\\"inventory\\\":[],\\\"flags\\\":{}}";\n'
    ),
    (
        '          String location = generated.optString("location", "").trim();\n          if (!title.isEmpty()) state.put("title", title);\n          if (!location.isEmpty()) state.put("location", location);\n',
        '          String location = generated.optString("location", "").trim();\n          if (!title.isEmpty()) state.put("title", title);\n          if (generated.has("currentLevel")) state.put("currentLevel", generated.optInt("currentLevel", state.optInt("currentLevel", 0)));\n          if (!location.isEmpty()) state.put("location", location);\n'
    ),
    (
        '    @JavascriptInterface public void requestSnapshot(String stateJson) {\n      imageIo.execute(() -> requestSnapshotInternal(stateJson));\n    }\n',
        '    @JavascriptInterface public void requestSnapshot(String stateJson) {\n      imageIo.execute(() -> requestSnapshotInternal(stateJson));\n    }\n\n    @JavascriptInterface public String levelSnapshot(String stateJson) {\n      return gameCore.levelSnapshotDescriptor(stateJson);\n    }\n'
    ),
]

for old, new in replacements:
    if old not in main:
        raise SystemExit('MainActivity marker not found: ' + old[:120])
    main = main.replace(old, new, 1)

main_path.write_text(main, encoding='utf-8')

index_path = Path('android-apk/app/src/main/assets/index.html')
index = index_path.read_text(encoding='utf-8')

index_replacements = [
    (
        '  mode:"local APK",\n  location:"Level 0 / The Lobby — khu phòng vàng ban đầu sau no-clip",\n',
        '  mode:"local APK",\n  currentLevel:0,\n  location:"Level 0 / The Lobby — khu phòng vàng ban đầu sau no-clip",\n'
    ),
    (
        'let state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;\nlet busy=false;\n\nstate.characterCanon=CURRENT_CHARACTER_CANON;\n',
        'let state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;\nlet busy=false;\n\nfunction ensureCurrentLevel(s){if(!Number.isInteger(s?.currentLevel)||s.currentLevel<0||s.currentLevel>6){const m=String(s?.location||"").match(/\\bLevel\\s*([0-6])\\b/i);s.currentLevel=m?Number(m[1]):0}return s}\nensureCurrentLevel(state);\nstate.characterCanon=CURRENT_CHARACTER_CANON;\n'
    ),
    (
        'function load(){state=JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial;state.characterCanon=CURRENT_CHARACTER_CANON;render()}\n',
        'function load(){state=ensureCurrentLevel(JSON.parse(localStorage.getItem("backroom-apk-state")||"null")||initial);state.characterCanon=CURRENT_CHARACTER_CANON;render()}\n'
    ),
    (
        'window.backroomTurn=json=>{state=JSON.parse(json);state.characterCanon=CURRENT_CHARACTER_CANON;actionEl.value="";busy=false;submitEl.disabled=false;save();statusEl.textContent="Turn "+state.turn+" đã lưu trên máy.";render()};\n',
        'window.backroomTurn=json=>{state=ensureCurrentLevel(JSON.parse(json));state.characterCanon=CURRENT_CHARACTER_CANON;actionEl.value="";busy=false;submitEl.disabled=false;save();statusEl.textContent="Turn "+state.turn+" · Level "+state.currentLevel+" đã lưu trên máy.";render()};\n'
    ),
]

for old, new in index_replacements:
    if old not in index:
        raise SystemExit('index.html marker not found: ' + old[:120])
    index = index.replace(old, new, 1)

index_path.write_text(index, encoding='utf-8')
