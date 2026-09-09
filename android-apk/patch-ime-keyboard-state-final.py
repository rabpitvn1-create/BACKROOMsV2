from pathlib import Path

ROOT = Path(__file__).resolve().parent
INDEX = ROOT / "app/src/main/assets/index.html"
html = INDEX.read_text(encoding="utf-8")

# Final Android/WebView IME state correction.
#
# Some Android 16 WebView/OEM combinations keep both innerHeight and
# visualViewport.height at the keyboard-free height while the keyboard overlays
# the WebView. In that state WindowInsets.Type.ime() is the only authoritative
# signal that the keyboard is actually visible. The previous resolver incorrectly
# classified that non-zero inset as stale and therefore left the composer behind
# the keyboard.
#
# Conversely, when the IME closes, the native inset can reach zero before one or
# both WebView viewport metrics recover. Once Android says the IME is closed, use
# the remembered keyboard-free baseline immediately instead of waiting for a stale
# reduced viewport to catch up.
old_helpers = r'''  function effectiveIme(nativeValue,inner,visual,baseline){
    return nativeValue>0&&baseline>0&&inner>=baseline-48&&visual>=baseline-48?0:nativeValue;
  }
  function chooseUsableHeight(inner,visual,ime,baseline){
    var usable=Math.min(inner>0?inner:Infinity,visual>0?visual:Infinity);
    if(ime<1&&baseline>0&&visual>=baseline-48)usable=Math.min(visual,baseline);
    if(ime>0&&baseline>ime+120)usable=Math.min(usable,baseline-ime);
    if(!Number.isFinite(usable)||usable<=0)usable=Math.max(inner,visual,baseline,320);
    return usable;
  }
'''
new_helpers = r'''  function chooseUsableHeight(inner,visual,ime,baseline){
    if(ime>0&&baseline>ime+120){
      var viewport=Math.min(inner>0?inner:Infinity,visual>0?visual:Infinity);
      var insetHeight=baseline-ime;
      if(!Number.isFinite(viewport)||viewport<=0)viewport=insetHeight;
      return Math.min(viewport,insetHeight);
    }
    if(ime<1&&baseline>0)return baseline;
    var usable=Math.min(inner>0?inner:Infinity,visual>0?visual:Infinity);
    if(!Number.isFinite(usable)||usable<=0)usable=Math.max(inner,visual,baseline,320);
    return usable;
  }
'''
count = html.count(old_helpers)
if count != 1:
    raise RuntimeError(f"IME state resolver: expected exactly 1 helper block, found {count}")
html = html.replace(old_helpers, new_helpers, 1)

old_call = "    var ime=effectiveIme(rawIme,inner,visual,fullHeight);\n"
new_call = "    var ime=rawIme;\n"
count = html.count(old_call)
if count != 1:
    raise RuntimeError(f"IME state resolver: expected exactly 1 effective IME call, found {count}")
html = html.replace(old_call, new_call, 1)

for marker in (
    "ANDROID_IME_VISUAL_VIEWPORT_V2",
    "window.syncAndroidImeViewport=schedule",
    "function chooseUsableHeight(inner,visual,ime,baseline)",
    "if(ime>0&&baseline>ime+120)",
    "if(ime<1&&baseline>0)return baseline",
    "var ime=rawIme;",
):
    if marker not in html:
        raise RuntimeError("Final IME state marker missing: " + marker)

if "function effectiveIme(nativeValue,inner,visual,baseline)" in html:
    raise RuntimeError("Legacy IME stale-inset heuristic must not survive")

INDEX.write_text(html, encoding="utf-8")
print("Final IME state correction applied: live native keyboard insets always shrink the shell; IME close restores the keyboard-free baseline immediately.")
