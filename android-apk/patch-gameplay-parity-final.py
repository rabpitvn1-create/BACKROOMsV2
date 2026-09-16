from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

# GameplayRollPolicy is the only gameplay-roll authority. Historical Android patches still
# construct MainActivity from an older source snapshot, so this step only materializes the
# current bridge shape. Probability tables, eligibility and RNG draw order stay in Kotlin.
start_marker = "  private JSONObject makeGameplayRolls(JSONObject state, String action, boolean meta) throws Exception {\n"
end_marker = "  private boolean rollSuccess(JSONObject rolls, String key) {"
if text.count(start_marker) != 1 or text.count(end_marker) != 1:
    raise RuntimeError("Gameplay roll bridge anchors are not unique")
start = text.index(start_marker)
end = text.index(end_marker, start)

bridge = '''  private JSONObject makeGameplayRolls(JSONObject state, String action, boolean meta) throws Exception {
    return makeGameplayRolls(state, "EXECUTE", action, meta);
  }

  private JSONObject makeGameplayRolls(JSONObject state, String actionKind, String action, boolean meta) throws Exception {
    boolean emuLevel1Progression = BuildConfig.DEBUG &&
      getIntent().getBooleanExtra("emuLevel1Progression", false);
    boolean emuLevel06Traversal = BuildConfig.DEBUG &&
      getIntent().getBooleanExtra("emuLevel06Traversal", false);
    return com.rabpit.backroom.core.GameplayRollPolicy.roll(
      state.toString(), actionKind, action, meta, GAME_RNG,
      emuLevel1Progression, emuLevel06Traversal);
  }

'''
text = text[:start] + bridge + text[end:]

settled = text[start:text.index(end_marker, start)]
for required in (
    "GameplayRollPolicy.roll(",
    'getIntent().getBooleanExtra("emuLevel1Progression", false)',
    'getIntent().getBooleanExtra("emuLevel06Traversal", false)',
    "state.toString(), actionKind, action, meta, GAME_RNG",
):
    if required not in settled:
        raise RuntimeError("Kotlin gameplay-roll bridge missing: " + required)
for forbidden in (
    "thresholdRoll(",
    "hazardThresholds",
    "entityThresholds",
    "lootThresholds",
    "waterThresholds",
    "nextInt(",
    "exitThresholdAndroid(",
    "reunionEligibleAndroid(",
):
    if forbidden in settled:
        raise RuntimeError("Java gameplay-roll authority survived bridge materialization: " + forbidden)

MAIN.write_text(text, encoding="utf-8")
print("Gameplay roll bridge materialized: Kotlin GameplayRollPolicy owns probabilities, eligibility and draw order.")
