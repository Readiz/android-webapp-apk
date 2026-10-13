package com.readiz.webapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    WebView web;
    private UpdateController updates;
    private FrameLayout root;
    private LinearLayout errorPanel;
    private String retryUrl = NavigationPolicy.HOME;

    @SuppressLint("SetJavaScriptEnabled")
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(244,246,243));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(safe.left,safe.top,safe.right,safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            }
            return insets.consumeSystemWindowInsets();
        });
        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        root.addView(web, new FrameLayout.LayoutParams(-1,-1));
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setUserAgentString(settings.getUserAgentString() + " " + BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_CODE);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return !NavigationPolicy.internal(request.getUrl().toString());
                return navigate(request.getUrl().toString(),request.hasGesture());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url) { return navigate(url,true); }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon) {
                if (!NavigationPolicy.internal(url)) { view.stopLoading(); showError(); return; }
                retryUrl = url.contains("/auth/") ? NavigationPolicy.HOME : url;
                errorPanel.setVisibility(View.GONE);
            }
            @Override public void onPageFinished(WebView view,String url) { CookieManager.getInstance().flush(); if(updates != null) updates.onPageFinished(); }
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error) {
                if(request.isForMainFrame()) showError();
            }
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response) {
                if(request.isForMainFrame() && response.getStatusCode() >= 400) showError();
            }
            @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error) {
                handler.cancel(); showError();
            }
        });
        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setPadding(32,32,32,32);
        errorPanel.setBackgroundColor(Color.rgb(244,246,243));
        TextView message = new TextView(this);
        message.setText("연결하지 못했습니다.\n인터넷 연결을 확인한 뒤 다시 시도해 주세요.");
        message.setGravity(Gravity.CENTER);
        message.setTextSize(17);
        Button retry = new Button(this);retry.setText("다시 시도");
        retry.setOnClickListener(v -> web.loadUrl(NavigationPolicy.internal(retryUrl) ? retryUrl : NavigationPolicy.HOME));
        errorPanel.addView(message);errorPanel.addView(retry);
        root.addView(errorPanel,new FrameLayout.LayoutParams(-1,-1));
        errorPanel.setVisibility(View.GONE);
        if(state == null || web.restoreState(state) == null || !NavigationPolicy.internal(web.getUrl())) web.loadUrl(NavigationPolicy.HOME);
        updates = new UpdateController(this);
        root.requestApplyInsets();
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,this::handleBack);
    }
    private boolean navigate(String url,boolean gesture) {
        if(NavigationPolicy.internal(url)) return false;
        if(gesture && NavigationPolicy.external(url)) {
            try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }
            catch(ActivityNotFoundException ignored) { showError(); }
        }
        return true;
    }
    private void showError() { if(errorPanel != null) errorPanel.setVisibility(View.VISIBLE); }
    @Override protected void onSaveInstanceState(Bundle out) { web.saveState(out);super.onSaveInstanceState(out); }
    @Override protected void onPause() { CookieManager.getInstance().flush();web.onPause();if(updates != null) updates.onPause();super.onPause(); }
    @Override protected void onResume() { super.onResume();if(web != null) web.onResume();if(updates != null) updates.onResume(); }
    @Override public void onBackPressed() { handleBack(); }
    private void handleBack() {
        if(web.canGoBack()) {
            android.webkit.WebBackForwardList history=web.copyBackForwardList();
            String previous=history.getItemAtIndex(history.getCurrentIndex()-1).getUrl();
            if(NavigationPolicy.internal(previous) && !previous.contains("/login") && !previous.contains("/auth/")) { web.goBack();return; }
        }
        new AlertDialog.Builder(this).setMessage(BuildConfig.APP_NAME + "을 종료할까요?")
            .setNegativeButton("계속 보기",null).setPositiveButton("종료",(dialog,which)->finish()).show();
    }
    @Override protected void onDestroy() { if(updates != null) updates.close();root.removeView(web);web.destroy();super.onDestroy(); }
}
