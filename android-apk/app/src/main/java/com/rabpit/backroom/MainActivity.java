package com.rabpit.backroom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
  private WebView webView;

  @SuppressLint("SetJavaScriptEnabled")
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    webView = new WebView(this);
    WebSettings settings = webView.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(true);
    webView.setWebViewClient(new WebViewClient() {
      @Override public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        installUiEnhancements();
      }
    });
    setContentView(webView);
    webView.loadUrl("file:///android_asset/index.html");
  }

  @Override protected void onDestroy() {
    if (webView != null) webView.destroy();
    super.onDestroy();
  }

  // Offline payload: button micro-interactions, scroll, and a purely
  // local "snapshot" action — the VN stage above the story panel IS the
  // snapshot (level background + figures from local assets), so nothing
  // is generated and nothing leaves the device. The fixed-scene VN
  // engine runs entirely inside the page (its save lives in the page's
  // own storage), so there is no native bridge anymore: the old
  // submitTurn -> GameCoreFacade.processRule turn lane was removed
  // with the free-action/GM model.
  private void installUiEnhancements() {
    String script = """
      (function(){
      if(window.__backroomEnhancements)return;window.__backroomEnhancements=true;
      var st=document.createElement('style');
      st.textContent='button{transition:transform 80ms ease,background 120ms ease,border-color 120ms ease;touch-action:manipulation;-webkit-tap-highlight-color:rgba(255,255,255,.12)}button:active:not(:disabled){transform:scale(.965);background:#303840;border-color:#77828c}button:disabled{opacity:.48;cursor:not-allowed}';
      document.head.appendChild(st);
      function scrollBottom(){var l=document.getElementById('log');if(l)requestAnimationFrame(function(){l.scrollTop=l.scrollHeight;});}
      function showSnapshot(){var s=document.getElementById('status');if(s)s.textContent='Snapshot hiển thị trực tiếp ở khung phía trên — nền theo Level, nhân vật và thực thể đều là ảnh trên máy.';var box=document.getElementById('snapshot');if(box&&box.scrollIntoView)box.scrollIntoView({block:'start'});}
      window.requestSnapshot=showSnapshot;
      var actions=document.querySelector('.actions');if(actions&&!document.getElementById('snapshotButton')){var b=document.createElement('button');b.id='snapshotButton';b.type='button';b.textContent='Tạo Snapshot';b.addEventListener('click',showSnapshot);var wide=actions.querySelector('.wide');if(wide)actions.insertBefore(b,wide);else actions.appendChild(b);}
      var oldRender=window.render;if(typeof oldRender==='function'){window.render=function(){oldRender();scrollBottom();};}
      scrollBottom();
      })();
      """;
    webView.evaluateJavascript(script, null);
  }
}
