
package com.example.computernetworkdevicearapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

public class CableARActivity extends AppCompatActivity {

    private static final String TAG = "CableAR";
    private static final int CAMERA_PERMISSION_CODE = 800;
    private static final int CHUNK_SIZE = 100_000;

    private PreviewView cameraPreview;
    private WebView     modelWebView;

    private String  glbBase64      = null;
    private boolean isPageFinished = false;
    private boolean isGlbReady     = false;

    // Change this to whichever device you want to show (router is default)
    private final String deviceName = "Router";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cable_ar);

        cameraPreview = findViewById(R.id.cameraPreview);
        modelWebView  = findViewById(R.id.modelWebView);

        setupWebView();
        loadGlbInBackground();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req,
                                           @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == CAMERA_PERMISSION_CODE && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                provider.unbindAll();
                provider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, preview);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"})
    private void setupWebView() {
        modelWebView.setBackgroundColor(Color.TRANSPARENT);
        modelWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings s = modelWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);

        modelWebView.addJavascriptInterface(new CableBridge(), "AndroidBridge");

        modelWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (!url.startsWith("file:")) return;
                isPageFinished = true;
                tryLoadModel();
            }
        });

        modelWebView.loadUrl("file:///android_asset/assets/cable_ar_demo.html");
    }

    private void loadGlbInBackground() {
        String file = glbFileName();
        new Thread(() -> {
            try {
                InputStream is = getAssets().open("models/" + file);
                glbBase64 = Base64.encodeToString(streamToBytes(is), Base64.NO_WRAP);
                Log.d(TAG, "GLB ready: " + file);
            } catch (Exception e) {
                Log.e(TAG, "GLB load error: " + e.getMessage());
                glbBase64 = null;
            }
            isGlbReady = true;
            runOnUiThread(this::tryLoadModel);
        }).start();
    }

    private void tryLoadModel() {
        if (!isPageFinished || !isGlbReady) return;
        String fn = glbBase64 != null ? "loadGlbFromBridge();" : "buildPlaceholder();";
        modelWebView.evaluateJavascript("setTimeout(function(){ " + fn + " }, 300);", null);
    }

    private String glbFileName() {
        switch (deviceName.toLowerCase()) {
            case "switch":   return "switch.glb";
            case "hub":      return "hub.glb";
            case "firewall": return "firewall.glb";
            default:         return "router.glb";
        }
    }

    private byte[] streamToBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    @Override
    protected void onDestroy() {
        if (modelWebView != null) modelWebView.destroy();
        super.onDestroy();
    }

    // ── JS Bridge ─────────────────────────────────────────────────────────────
    private class CableBridge {
        @JavascriptInterface
        public int getGlbLength() {
            return glbBase64 != null ? glbBase64.length() : 0;
        }
        @JavascriptInterface
        public String getGlbChunk(int offset) {
            if (glbBase64 == null || offset >= glbBase64.length()) return "";
            return glbBase64.substring(offset,
                    Math.min(offset + CHUNK_SIZE, glbBase64.length()));
        }
        @JavascriptInterface
        public void onModelLoaded(String name) { Log.d(TAG, "Model loaded: " + name); }
        @JavascriptInterface
        public void goBack() { runOnUiThread(() -> finish()); }
        @JavascriptInterface
        public void resetDemo() {
            runOnUiThread(() ->
                    modelWebView.evaluateJavascript("resetCableDemo();", null));
        }
        @JavascriptInterface
        public void log(String msg) { Log.d(TAG, "JS: " + msg); }
    }
}