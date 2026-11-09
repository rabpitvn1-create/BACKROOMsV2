package com.rabpit.backroom;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.rabpit.backroom.core.GameCoreFacade;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
  private WebView webView;
  private final ExecutorService io = Executors.newSingleThreadExecutor();
  private GameCoreFacade gameCore;

  @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
  @Override public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    gameCore = GameCoreFacade.create(getApplicationContext(), BuildConfig.DEBUG);
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
    webView.addJavascriptInterface(new GameBridge(), "Android");
    setContentView(webView);
    webView.loadUrl("file:///android_asset/index.html");
  }

  @Override protected void onDestroy() {
    if (gameCore != null) gameCore.close();
    io.shutdownNow();
    if (webView != null) webView.destroy();
    super.onDestroy();
  }

  // Offline payload: button micro-interactions, pending bubbles, scroll,
  // and a purely local "snapshot" action — the VN stage above the story
  // panel IS the snapshot (level background + figures from local assets),
  // so nothing is generated and nothing leaves the device.
  private void installUiEnhancements() {
    String script = """
      (function(){
      if(window.__backroomEnhancements)return;window.__backroomEnhancements=true;
      var st=document.createElement('style');
      st.textContent='button{transition:transform 80ms ease,background 120ms ease,border-color 120ms ease;touch-action:manipulation;-webkit-tap-highlight-color:rgba(255,255,255,.12)}button:active:not(:disabled){transform:scale(.965);background:#303840;border-color:#77828c}button:disabled{opacity:.48;cursor:not-allowed}.message.pending{opacity:.72}.message.pending .text{color:#aeb7be}';
      document.head.appendChild(st);
      function scrollBottom(){var l=document.getElementById('log');if(l)requestAnimationFrame(function(){l.scrollTop=l.scrollHeight;});}
      function showSnapshot(){var s=document.getElementById('status');if(s)s.textContent='Snapshot hiển thị trực tiếp ở khung phía trên — nền theo Level, nhân vật và thực thể đều là ảnh trên máy.';var box=document.getElementById('snapshot');if(box&&box.scrollIntoView)box.scrollIntoView({block:'start'});}
      window.requestSnapshot=showSnapshot;
      var actions=document.querySelector('.actions');if(actions&&!document.getElementById('snapshotButton')){var b=document.createElement('button');b.id='snapshotButton';b.type='button';b.textContent='Tạo Snapshot';b.addEventListener('click',showSnapshot);var wide=actions.querySelector('.wide');if(wide)actions.insertBefore(b,wide);else actions.appendChild(b);}
      var oldRender=window.render;if(typeof oldRender==='function'){window.render=function(){oldRender();scrollBottom();};}
      var oldTurn=window.backroomTurn;window.backroomTurn=function(json){if(typeof oldTurn==='function')oldTurn(json);document.querySelectorAll('[data-pending="1"]').forEach(function(n){n.remove();});var s=document.getElementById('status');if(s)s.textContent='Turn '+state.turn+' đã lưu trên máy.';scrollBottom();};
      var oldError=window.backroomError;window.backroomError=function(message){document.querySelectorAll('[data-pending="1"]').forEach(function(n){n.remove();});if(typeof oldError==='function')oldError(message);scrollBottom();};
      var f=document.getElementById('form');if(f){f.addEventListener('submit',function(){var a=document.getElementById('action');var text=a?a.value.trim():'';if(!text)return;var l=document.getElementById('log');if(!l)return;var player=document.createElement('article');player.className='message player pending';player.setAttribute('data-pending','1');player.innerHTML='<div class="role">BẠN</div><div class="text"></div>';player.querySelector('.text').textContent=text;l.appendChild(player);var gm=document.createElement('article');gm.className='message pending';gm.setAttribute('data-pending','1');gm.innerHTML='<div class="role">GAME MASTER</div><div class="text">Đang xử lý lượt…</div>';l.appendChild(gm);scrollBottom();},true);}
      scrollBottom();
      })();
      """;
    webView.evaluateJavascript(script, null);
  }

  private void emit(String function, String json) {
    String script = "window." + function + "(" + JSONObject.quote(json) + ")";
    runOnUiThread(() -> webView.evaluateJavascript(script, null));
  }

  private class GameBridge {
    @JavascriptInterface public void submitTurn(String stateJson, String action) {
      io.execute(() -> {
        try {
          JSONObject localResult = new JSONObject(gameCore.processRule(stateJson, action));
          if (localResult.optBoolean("handled", false)) {
            emit("backroomTurn", localResult.getJSONObject("state").toString());
            return;
          }
          String error = localResult.optString("error", "");
          emit("backroomError", error.isEmpty() ? "Không thể xử lý lượt." : error);
        } catch (Exception e) {
          emit("backroomError", e.getMessage() == null ? "Không thể xử lý lượt." : e.getMessage());
        }
      });
    }
  }
}
