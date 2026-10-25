package com.mattheadland.aurora;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Full-screen host for the Aurora page (bundled in assets/www). */
public class MainActivity extends Activity {
    static volatile boolean visible = false;
    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        w.getDecorView().setBackgroundColor(Color.BLACK);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);                  // local footage lives in app storage (file://)
        s.setAllowFileAccessFromFileURLs(true);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                Log.write(getApplicationContext(), "js: " + m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(this, web), "AuroraAndroid");
        setContentView(web);
        hideSystemUi();
        web.loadUrl("file:///android_asset/www/index.html");
        Footage.get(this).start();
        Log.write(this, "MainActivity created");
    }

    private void hideSystemUi() {
        web.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    /** Fire remote keys the page can't see by itself: Back and Menu become the page's Back and colour keys. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.KEYCODE_BACK || k == KeyEvent.KEYCODE_MENU) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                int jsKey = k == KeyEvent.KEYCODE_BACK ? 10009 : 405;   // Tizen Back / blue key codes the page already handles
                web.evaluateJavascript("(function(){var ev=new KeyboardEvent('keydown',{bubbles:true});"
                        + "Object.defineProperty(ev,'keyCode',{get:function(){return " + jsKey + ";}});"
                        + "document.dispatchEvent(ev);})();", null);
            }
            return true;   // never let Back leave the home screen
        }
        return super.dispatchKeyEvent(e);
    }

    @Override protected void onResume() {
        super.onResume(); visible = true; hideSystemUi(); web.onResume();
        web.evaluateJavascript("window.__auroraResume&&window.__auroraResume();", null);
    }
    @Override protected void onPause() { visible = false; web.onPause(); super.onPause(); }
    @Override public void onBackPressed() { /* handled in dispatchKeyEvent */ }
}
