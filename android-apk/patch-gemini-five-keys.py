from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

text = MAIN.read_text(encoding="utf-8")

old = r'''  private String[] geminiKeys() {
    return new String[] {
      BuildConfig.GEMINI_API_KEY_1,
      BuildConfig.GEMINI_API_KEY_2,
      BuildConfig.GEMINI_API_KEY_3
    };
  }
'''
new = r'''  private String[] geminiKeys() {
    return new String[] {
      BuildConfig.GEMINI_API_KEY_1,
      BuildConfig.GEMINI_API_KEY_2,
      BuildConfig.GEMINI_API_KEY_3,
      BuildConfig.GEMINI_API_KEY_4,
      BuildConfig.GEMINI_API_KEY_5
    };
  }
'''

count = text.count(old)
if count != 1:
    raise RuntimeError(f"five Gemini Game Master keys: expected exactly 1 match, found {count}")

MAIN.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Game Master provider pool: Gemini keys 1 through 5.")
