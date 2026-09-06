from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")

old = '''    try {\n      return geminiText(prompt);\n    } catch (Exception error) {\n'''
new = '''    try {\n      String geminiResult = geminiText(prompt);\n      emit("backroomProvider", "Gemini K" + (lastGeminiWorker + 1));\n      return geminiResult;\n    } catch (Exception error) {\n'''

count = text.count(old)
if count != 1:
    raise RuntimeError(f"Gemini provider success label: expected exactly 1 match, found {count}")

text = text.replace(old, new, 1)
MAIN.write_text(text, encoding="utf-8")
print("Gemini successful key lane label enabled without Luna fallback.")
