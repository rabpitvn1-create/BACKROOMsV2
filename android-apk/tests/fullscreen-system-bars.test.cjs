const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const mainPath = path.join(__dirname, '..', 'app', 'src', 'main', 'java', 'com', 'rabpit', 'backroom', 'MainActivity.java');
const source = fs.readFileSync(mainPath, 'utf8');

test('immersive fullscreen is re-applied after WebView attachment and page load', () => {
  const setContent = source.indexOf('setContentView(webView);');
  const postedHide = source.indexOf('webView.post(() -> safeApplyImmersiveFullscreen("webViewAttached"));');
  const loadUrl = source.indexOf('webView.loadUrl("file:///android_asset/index.html");');

  assert.ok(setContent >= 0);
  assert.ok(postedHide > setContent, 'fullscreen must be posted after setContentView');
  assert.ok(loadUrl > postedHide, 'posted fullscreen must be registered before page load');
  assert.match(source, /installUiScripts\(\);\s*safeApplyImmersiveFullscreen\("onPageFinished"\);/);
});

test('immersive contract still hides both bars without breaking keyboard resize', () => {
  assert.match(source, /SOFT_INPUT_ADJUST_RESIZE/);
  assert.match(source, /WindowInsets\.Type\.statusBars\(\) \| WindowInsets\.Type\.navigationBars\(\)/);
  assert.match(source, /BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE/);
  assert.match(source, /safeApplyImmersiveFullscreen\("onResume"\)/);
  assert.match(source, /safeApplyImmersiveFullscreen\("onWindowFocusChanged"\)/);
});
