from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

lazy_sequence = "        gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);\n    initializeRuntimeDebugLog();\n"
if lazy_sequence in text:
    text = text.replace(
        lazy_sequence,
        "        gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);\n",
        1,
    )
else:
    # Keep this fail-loud: export-final currently locates the constructor in the lazy accessor.
    raise RuntimeError("debug session init was not attached to the lazy GameCore constructor as expected")

on_create = '  @Override public void onCreate(Bundle savedInstanceState) {\n    super.onCreate(savedInstanceState);\n'
if text.count(on_create) != 1:
    raise RuntimeError(f"debug session onCreate anchor expected once, found {text.count(on_create)}")
text = text.replace(on_create, on_create + "    initializeRuntimeDebugLog();\n", 1)

start = text.index('  @Override public void onCreate(Bundle savedInstanceState) {')
end = text.index('\n  @Override protected void onDestroy()', start)
body = text[start:end]
if body.count("initializeRuntimeDebugLog();") != 1:
    raise RuntimeError("debug session must initialize exactly once from final onCreate")

MAIN.write_text(text, encoding="utf-8")
print("Runtime debug session initialization moved out of lazy GameCore and into final Activity onCreate.")
