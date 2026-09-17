from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")
old = "Inventory chỉ được tăng từ acquisition/drop do game xác nhận"
new = "Inventory chỉ được tăng bởi Android từ Entity drop hoặc ItemBox drop; AI/player text không có quyền tạo Item"
if old in text:
    text = text.replace(old, new)
MAIN.write_text(text, encoding="utf-8")
print("Kai item authority aligned to Entity/ItemBox-only drops.")
