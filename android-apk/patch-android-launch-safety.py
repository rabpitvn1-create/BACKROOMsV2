from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    return source.replace(old, new, 1)


if "ANDROID_DISPLAY_GEOMETRY_V1" not in text:
    raise RuntimeError("Canonical Android display geometry must run before launch safety")

# The display path must never be able to kill MainActivity. Keep immersive mode best-effort and
# let the WebView/game continue even if an OEM rejects one of the window calls.
text = replace_once(
    text,
    "    super.onCreate(savedInstanceState);\n    applyAndroidDisplayMode();\n",
    "    super.onCreate(savedInstanceState);\n    safeApplyAndroidDisplayMode(); // ANDROID_DISPLAY_LAUNCH_SAFE_V2\n",
    "safe display bootstrap",
)

# Android's WebView inset guidance expects an attached WebView. Do not bind the listener while the
# WebView is still unattached; install it only after setContentView and always return original insets.
listener = '''    webView.setOnApplyWindowInsetsListener((view, insets) -> {
      pushAndroidDisplayGeometry(insets);
      return insets;
    });
'''
if listener not in text:
    raise RuntimeError("Pre-attach WebView insets listener anchor missing")
text = text.replace(listener, "", 1)

attached_listener = '''    setContentView(webView);
    webView.setOnApplyWindowInsetsListener((view, insets) -> {
      safePushAndroidDisplayGeometry(insets);
      return insets;
    });
    webView.requestApplyInsets();
'''
text = replace_once(
    text,
    "    setContentView(webView);\n",
    attached_listener,
    "attached WebView insets listener",
)

text = replace_once(
    text,
    "        pushAndroidDisplayGeometry(view.getRootWindowInsets());\n",
    "        safePushAndroidDisplayGeometry(view.getRootWindowInsets());\n",
    "safe page-finished display geometry",
)

# Android 15+ already enforces edge-to-edge for targetSdk 35. The legacy color APIs are deprecated
# and disabled there, so only use them on Android 14 and older.
old_bar_colors = '''    getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
    getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
'''
new_bar_colors = '''    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
      getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
      getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
    }
'''
text = replace_once(text, old_bar_colors, new_bar_colors, "Android 15 system bar API guard")

# Android 15 documentation requires non-floating targetSdk 35 windows to behave as ALWAYS for
# display cutouts. Use ALWAYS where the constant exists, retaining SHORT_EDGES only on API 28-29.
old_cutout = '''    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      WindowManager.LayoutParams attributes = getWindow().getAttributes();
      attributes.layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
      getWindow().setAttributes(attributes);
    }
'''
new_cutout = '''    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      WindowManager.LayoutParams attributes = getWindow().getAttributes();
      attributes.layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
      getWindow().setAttributes(attributes);
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      WindowManager.LayoutParams attributes = getWindow().getAttributes();
      attributes.layoutInDisplayCutoutMode =
          WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
      getWindow().setAttributes(attributes);
    }
'''
text = replace_once(text, old_cutout, new_cutout, "Android 15 cutout mode")

# setDecorFitsSystemWindows is deprecated/disabled for targetSdk 35 on Android 15+. Edge-to-edge is
# already platform-enforced there, so call it only where it still has a defined effect.
old_insets_controller = '''    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      getWindow().setDecorFitsSystemWindows(false);
      WindowInsetsController controller = getWindow().getInsetsController();
      if (controller != null) {
        controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        controller.setSystemBarsBehavior(
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      }
    } else {
'''
new_insets_controller = '''    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        getWindow().setDecorFitsSystemWindows(false);
      }
      WindowInsetsController controller = getWindow().getInsetsController();
      if (controller != null) {
        controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        controller.setSystemBarsBehavior(
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      }
    } else {
'''
text = replace_once(text, old_insets_controller, new_insets_controller, "Android 15 decor fits guard")

safe_helpers = '''
  private void safeApplyAndroidDisplayMode() {
    try {
      applyAndroidDisplayMode();
    } catch (Throwable error) {
      Log.w("BackroomDisplay", "Display mode setup failed; continuing with the game UI.", error);
    }
  }

  private void safePushAndroidDisplayGeometry(WindowInsets insets) {
    try {
      pushAndroidDisplayGeometry(insets);
    } catch (Throwable error) {
      Log.w("BackroomDisplay", "Display geometry unavailable; using CSS fallback geometry.", error);
    }
  }

'''
helper_anchor = "  private float displayCssPx(int devicePx) {\n"
if "private void safeApplyAndroidDisplayMode()" not in text:
    if helper_anchor not in text:
        raise RuntimeError("Display helper insertion anchor missing")
    text = text.replace(helper_anchor, safe_helpers + helper_anchor, 1)

text = text.replace(
    "    if (hasFocus) applyAndroidDisplayMode();\n",
    "    if (hasFocus) safeApplyAndroidDisplayMode();\n",
    1,
)
text = replace_once(
    text,
    "    super.onResume();\n    applyAndroidDisplayMode();\n    if (webView != null) webView.requestApplyInsets();\n",
    '''    super.onResume();
    safeApplyAndroidDisplayMode();
    if (webView != null) {
      try { webView.requestApplyInsets(); }
      catch (Throwable error) { Log.w("BackroomDisplay", "Insets refresh failed; continuing.", error); }
    }
''',
    "safe resume display path",
)

required = (
    "ANDROID_DISPLAY_LAUNCH_SAFE_V2",
    "private void safeApplyAndroidDisplayMode()",
    "private void safePushAndroidDisplayGeometry(WindowInsets insets)",
    "LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS",
    "Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM",
    "safePushAndroidDisplayGeometry(insets)",
    "safePushAndroidDisplayGeometry(view.getRootWindowInsets())",
    "WindowInsets.Type.ime()",
    "getSystemWindowInsetBottom() - insets.getStableInsetBottom()",
    "--android-ime-bottom",
    'Log.w("BackroomDisplay"',
)
for marker in required:
    if marker not in text:
        raise RuntimeError("Android launch-safety contract missing: " + marker)

# Regression guards: never restore the risky startup call or pre-attach geometry callback.
if "    super.onCreate(savedInstanceState);\n    applyAndroidDisplayMode();\n" in text:
    raise RuntimeError("Unguarded display setup still runs directly from onCreate")
pre_attach = text.find("webView.setOnApplyWindowInsetsListener") < text.find("setContentView(webView)")
if pre_attach:
    raise RuntimeError("WebView inset listener is still installed before attachment")

MAIN.write_text(text, encoding="utf-8")
print("Android launch safety V2 applied: OEM-safe immersive startup, Android 15+ cutout mode, attached WebView insets, geometry fallbacks.")

# apply-android-ui.py runs immediately before this finalizer, so install the Header artwork only
# after the canonical topbar CSS exists. Keeping it separate prevents the launch-safety logic from
# owning presentation details while still using the established post-UI ordering.
header_patch = ROOT / "patch-header-image.py"
if not header_patch.is_file():
    raise RuntimeError("Header image patch missing")
exec(compile(header_patch.read_text(encoding="utf-8"), str(header_patch), "exec"), {
    "__name__": "__main__",
    "__file__": str(header_patch),
})

# Release-branch CI trigger marker.
