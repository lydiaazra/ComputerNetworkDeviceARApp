package com.example.computernetworkdevicearapp;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class NetworkLabActivity extends AppCompatActivity {

    private static final String TAG       = "NetworkLab";
    private static final int    CHUNK     = 100_000;

    private WebView webView;
    private final Map<String, String> glbMap = new HashMap<>();

    // GLB type → asset filename mapping
    private static final String[][] GLB_FILES = {
            {"router",   "models/router.glb"},
            {"switch",   "models/switch.glb"},
            {"hub",      "models/hub.glb"},
            {"firewall", "models/firewall.glb"},
            {"pc",       "models/pc.glb"},
            {"laptop",   "models/laptop.glb"},
            {"ap",       "models/accesspoint.glb"},
            {"server",   "models/server.glb"},
    };

    @SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_lab);

        // Pre-load all GLBs in background
        preloadGlbs();

        webView = findViewById(R.id.networkLabWebView);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setBuiltInZoomControls(false);

        webView.addJavascriptInterface(new LabBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Tell JS which device types have GLBs available
                StringBuilder js = new StringBuilder();
                for (String[] entry : GLB_FILES) {
                    if (glbMap.containsKey(entry[0])) {
                        js.append("notifyGlbReady('").append(entry[0]).append("');");
                    }
                }
                if (js.length() > 0) {
                    view.evaluateJavascript(
                            "setTimeout(function(){" + js + "},800);", null);
                }
            }
        });

        // Load HTML from assets — relative paths resolve to assets/assets/
        webView.loadUrl("file:///android_asset/assets/network_lab.html");
    }

    // Pre-load all GLBs as base64 in background thread
    private void preloadGlbs() {
        new Thread(() -> {
            for (String[] entry : GLB_FILES) {
                String type = entry[0];
                String path = entry[1];
                try {
                    InputStream is  = getAssets().open(path);
                    byte[]      buf = streamToBytes(is);
                    glbMap.put(type, Base64.encodeToString(buf, Base64.NO_WRAP));
                    Log.d(TAG, "✅ GLB loaded: " + path + " (" + buf.length/1024 + " KB)");
                } catch (IOException e) {
                    Log.w(TAG, "GLB missing: " + path);
                }
            }
        }).start();
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

    private byte[] streamToBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    // ── JavaScript Bridge ──────────────────────────────────────────────────

    private class LabBridge {

        /** Total base64 length for a device type (0 = not available) */
        @JavascriptInterface
        public int getGlbLength(String type) {
            String d = glbMap.get(type);
            return d != null ? d.length() : 0;
        }

        /** Returns up to CHUNK chars of base64 starting at offset */
        @JavascriptInterface
        public String getGlbChunk(String type, int offset) {
            String d = glbMap.get(type);
            if (d == null || offset >= d.length()) return "";
            return d.substring(offset, Math.min(offset + CHUNK, d.length()));
        }

        @JavascriptInterface
        public void goBack() {
            runOnUiThread(() -> finish());
        }

        @JavascriptInterface
        public void log(String msg) {
            Log.d(TAG, "JS: " + msg);
        }
    }
}