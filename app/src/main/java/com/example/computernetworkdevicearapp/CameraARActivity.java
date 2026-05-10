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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

public class CameraARActivity extends AppCompatActivity {

    private static final String TAG                   = "CameraAR";
    private static final int    CAMERA_PERMISSION_CODE = 300;
    // Each chunk returned to JS via @JavascriptInterface must be well under the 1 MB Binder limit
    private static final int    CHUNK_SIZE             = 100_000; // chars (~75 KB of binary)

    private PreviewView    cameraPreview;
    private WebView        modelWebView;
    private TextView       tvDeviceTitle, tvExplanation, tvHint;
    private MaterialButton btnPlace, btnBack;

    private String deviceName;
    private String explanation;
    private String glbBase64 = null; // full base64; served to JS in chunks

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_ar);

        deviceName = getIntent().getStringExtra("deviceName");
        if (deviceName == null || deviceName.isEmpty()) deviceName = "Router";
        setupDeviceData();

        // Read GLB into memory as base64; served to JS in ~75 KB chunks to avoid Binder limit
        loadGlbToMemory();

        bindViews();
        setupModelWebView();
        setupButtons();

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
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    @Override
    protected void onDestroy() {
        if (modelWebView != null) modelWebView.destroy();
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // Load GLB bytes → base64 string (stays in Java heap, not sent via Binder)
    // -------------------------------------------------------------------------

    private void loadGlbToMemory() {
        String file = glbFileName();
        try {
            InputStream is  = getAssets().open("models/" + file);
            byte[]      buf = streamToBytes(is);
            glbBase64 = Base64.encodeToString(buf, Base64.NO_WRAP);
            Log.d(TAG, "✅ GLB loaded: models/" + file + " (" + buf.length / 1024 + " KB)");
        } catch (IOException e) {
            Log.w(TAG, "GLB not found: models/" + file + " → " + e.getMessage());
            glbBase64 = null;
        }
    }

    private String glbFileName() {
        switch (deviceName.toLowerCase()) {
            case "switch":        return "switch.glb";
            case "hub":           return "hub.glb";
            case "firewall":      return "firewall.glb";
            case "access point":
            case "accesspoint":   return "accesspoint.glb";
            default:              return "router.glb";
        }
    }

    private byte[] streamToBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Bind views
    // -------------------------------------------------------------------------

    private void bindViews() {
        cameraPreview = findViewById(R.id.cameraPreview);
        modelWebView  = findViewById(R.id.modelWebView);
        tvDeviceTitle = findViewById(R.id.tvDeviceTitle);
        tvExplanation = findViewById(R.id.tvExplanation);
        tvHint        = findViewById(R.id.tvHint);
        btnPlace      = findViewById(R.id.btnPlace);
        btnBack       = findViewById(R.id.btnBack);

        tvDeviceTitle.setText("📡 " + deviceName);
        tvExplanation.setText(explanation);
        tvHint.setText("Loading 3D model...");
    }

    // -------------------------------------------------------------------------
    // CameraX
    // -------------------------------------------------------------------------

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
                Toast.makeText(this, "Camera error: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // -------------------------------------------------------------------------
    // WebView
    // -------------------------------------------------------------------------

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupModelWebView() {
        modelWebView.setBackgroundColor(Color.TRANSPARENT);
        modelWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings s = modelWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);

        modelWebView.addJavascriptInterface(new ModelBridge(), "AndroidBridge");

        modelWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (!url.startsWith("file:")) return;
                // JS pulls data via getGlbChunk(); no large string passed through Binder here
                String fn = glbBase64 != null ? "loadGlbFromBridge();" : "showFallback('" + deviceName + "');";
                view.evaluateJavascript("setTimeout(function(){ " + fn + " }, 800);", null);
            }
        });

        modelWebView.loadUrl("file:///android_asset/assets/ar_model.html");
    }

    // -------------------------------------------------------------------------
    // Buttons
    // -------------------------------------------------------------------------

    private void setupButtons() {
        btnPlace.setOnClickListener(v ->
                modelWebView.evaluateJavascript("resetCamera()", null));
        btnBack.setOnClickListener(v -> finish());
    }

    // -------------------------------------------------------------------------
    // JS Bridge — GLB data served in small chunks to stay under Binder 1 MB limit
    // -------------------------------------------------------------------------

    private class ModelBridge {

        /** Total length of the base64 string (0 if no GLB). */
        @JavascriptInterface
        public int getGlbLength() {
            return glbBase64 != null ? glbBase64.length() : 0;
        }

        /**
         * Returns up to CHUNK_SIZE base64 characters starting at {@code offset}.
         * Returns "" when offset >= total length.
         */
        @JavascriptInterface
        public String getGlbChunk(int offset) {
            if (glbBase64 == null || offset >= glbBase64.length()) return "";
            int end = Math.min(offset + CHUNK_SIZE, glbBase64.length());
            return glbBase64.substring(offset, end);
        }

        @JavascriptInterface
        public void onModelLoaded(String s) {
            runOnUiThread(() -> tvHint.setText("Drag to rotate  •  Pinch to zoom"));
        }

        @JavascriptInterface
        public void onModelError(String m) {
            runOnUiThread(() -> tvHint.setText("Showing 3D placeholder"));
        }

        @JavascriptInterface
        public void log(String m) { Log.d(TAG, "JS: " + m); }
    }

    // -------------------------------------------------------------------------
    // Device data
    // -------------------------------------------------------------------------

    private void setupDeviceData() {
        switch (deviceName.toLowerCase()) {
            case "switch":
                deviceName  = "Switch";
                explanation = "Connects LAN devices via MAC addresses. OSI Layer 2.";
                break;
            case "hub":
                deviceName  = "Hub";
                explanation = "Broadcasts to all connected ports. OSI Layer 1.";
                break;
            case "firewall":
                deviceName  = "Firewall";
                explanation = "Filters traffic by security rules.";
                break;
            case "access point": case "accesspoint":
                deviceName  = "Access Point";
                explanation = "Extends Wi-Fi wirelessly. OSI Layer 2.";
                break;
            default:
                deviceName  = "Router";
                explanation = "Connects networks via IP addresses. OSI Layer 3.";
                break;
        }
    }
}
