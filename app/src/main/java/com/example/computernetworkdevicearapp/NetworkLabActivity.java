package com.example.computernetworkdevicearapp;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

public class NetworkLabActivity extends AppCompatActivity {

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_lab);

        String deviceType = getIntent().getStringExtra("deviceType");
        String htmlFile   = htmlFileFor(deviceType);

        webView = findViewById(R.id.networkLabWebView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);

        webView.addJavascriptInterface(new LabBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/assets/" + htmlFile);
    }

    private String htmlFileFor(String type) {
        if (type == null) return "router_demo.html";
        switch (type) {
            case "switch":   return "switch_demo.html";
            case "hub":      return "hub_demo.html";
            case "firewall": return "firewall_demo.html";
            case "wap":      return "wap_demo.html";
            case "nic":      return "nic_demo.html";
            case "repeater": return "repeater_demo.html";
            case "gateway":  return "gateway_demo.html";
            default:         return "router_demo.html";
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private class LabBridge {
        @android.webkit.JavascriptInterface
        public void goBack() {
            runOnUiThread(() -> finish());
        }
    }
}