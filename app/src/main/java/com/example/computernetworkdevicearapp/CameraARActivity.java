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
    private static final int    CHUNK_SIZE             = 100_000;

    private PreviewView    cameraPreview;
    private WebView        modelWebView;
    private TextView       tvDeviceTitle, tvDeviceSubtitle, tvHint;
    private TextView       tvWhatItDoes, tvHowItWorks, tvWhyItMatters;
    private MaterialButton btnPlace, btnBack;

    private String deviceName;
    private String whatItDoes;
    private String howItWorks;
    private String whyItMatters;
    private String glbBase64 = null;

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

        // ✅ Load GLB in background thread — prevents OOM crash on large files
        loadGlbInBackground();
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
    // GLB filename — uses contains() for flexible matching
    // -------------------------------------------------------------------------

    private String glbFileName() {
        String name = deviceName.toLowerCase().trim();
        Log.d(TAG, "Device name received: '" + name + "'");

        if (name.contains("switch"))                                return "switch.glb";
        if (name.contains("hub"))                                   return "hub.glb";
        if (name.contains("firewall"))                              return "firewall.glb";
        if (name.contains("access") || name.contains("wap")
                || name.equals("ap"))                               return "wap.glb";
        if (name.contains("nic") || name.contains("network interface")) return "nic.glb";
        if (name.contains("repeat"))                                return "repeater.glb";
        if (name.contains("gateway"))                               return "gateway.glb";
        if (name.contains("modem"))                                 return "modem.glb";
        if (name.contains("router"))                                return "router.glb";

        Log.w(TAG, "No GLB match for: '" + name + "' — using router.glb");
        return "router.glb";
    }

    // -------------------------------------------------------------------------
    // Load GLB in background thread — prevents crash for large files
    // -------------------------------------------------------------------------

    private void loadGlbInBackground() {
        new Thread(() -> {
            String file = glbFileName();
            try {
                Log.d(TAG, "Loading GLB in background: models/" + file);
                InputStream is  = getAssets().open("models/" + file);
                byte[]      buf = streamToBytes(is);
                glbBase64 = Base64.encodeToString(buf, Base64.NO_WRAP);
                Log.d(TAG, "✅ GLB ready: " + file + " (" + buf.length / 1024 + " KB)");
            } catch (OutOfMemoryError e) {
                Log.e(TAG, "❌ OOM — GLB too large: " + file);
                glbBase64 = null;
            } catch (IOException e) {
                Log.w(TAG, "❌ GLB not found: models/" + file);
                glbBase64 = null;
            } catch (Exception e) {
                Log.e(TAG, "❌ GLB error: " + e.getMessage());
                glbBase64 = null;
            }
        }).start();
    }

    private byte[] streamToBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Device data — uses contains() for flexible name matching
    // -------------------------------------------------------------------------

    private void setupDeviceData() {
        String name = deviceName.toLowerCase().trim();

        if (name.contains("switch")) {
            deviceName   = "Layer 2 Switch";
            whatItDoes   = "Connects multiple devices in a LAN using MAC addresses to forward Ethernet frames to the correct destination port.";
            howItWorks   = "• Learns the MAC address of each connected device.\n• Reads destination MAC on each frame.\n• Forwards only to the correct port.\n• Floods if destination is unknown.";
            whyItMatters = "Reduces unnecessary traffic by delivering data only to the intended device, improving overall LAN performance.";

        } else if (name.contains("hub")) {
            deviceName   = "Layer 1 Hub";
            whatItDoes   = "Connects multiple Ethernet devices and broadcasts all incoming data to every connected port.";
            howItWorks   = "• Receives data on one port.\n• Broadcasts to ALL other ports.\n• No filtering — every device sees all traffic.\n• Creates a shared collision domain.";
            whyItMatters = "Legacy device — understanding hubs explains why switches replaced them to eliminate collisions.";

        } else if (name.contains("firewall")) {
            deviceName   = "Firewall";
            whatItDoes   = "Monitors and controls incoming and outgoing traffic based on predefined security rules.";
            howItWorks   = "• Inspects each packet against a ruleset.\n• Blocks unauthorised traffic.\n• Performs NAT to hide internal IPs.\n• Operates from Layer 3 up to Layer 7.";
            whyItMatters = "First line of defence — without a firewall, internal devices are exposed directly to internet threats.";

        } else if (name.contains("access") || name.contains("wap") || name.equals("ap")) {
            deviceName   = "Wireless Access Point";
            whatItDoes   = "Allows wireless devices to connect to a wired LAN using Wi-Fi, acting as a bridge between wireless clients and the network.";
            howItWorks   = "• Broadcasts an SSID for devices to detect.\n• Authenticates connecting devices.\n• Bridges wireless frames to wired Ethernet.\n• Operates at OSI Layer 2.";
            whyItMatters = "Enables mobility — users stay connected to the network without physical cables as they move around.";

        } else if (name.contains("nic") || name.contains("network interface")) {
            deviceName   = "Network Interface Card";
            whatItDoes   = "Hardware component that connects a computer to a network, providing the physical interface for communication.";
            howItWorks   = "• Converts data to electrical or optical signals.\n• Has a unique MAC address burned in at manufacturing.\n• Handles framing at OSI Layer 2.\n• Can be wired (Ethernet) or wireless (Wi-Fi).";
            whyItMatters = "Every networked device has a NIC — it is the physical gateway between a device and the network medium.";

        } else if (name.contains("repeater")) {
            deviceName   = "Repeater";
            whatItDoes   = "Amplifies or regenerates a network signal so it can travel longer distances without degrading.";
            howItWorks   = "• Receives a weakened signal on one port.\n• Regenerates and amplifies it.\n• Retransmits out the other port.\n• Does not filter or process data.";
            whyItMatters = "Extends network reach beyond the 100m cable limit without losing signal quality.";

        } else if (name.contains("gateway")) {
            deviceName   = "Gateway";
            whatItDoes   = "Connects two networks that use different protocols, translating between them so they can communicate.";
            howItWorks   = "• Receives data from one network.\n• Translates the protocol or format.\n• Forwards to the destination network.\n• Can operate at any OSI layer.";
            whyItMatters = "Essential for connecting networks that speak different protocols — e.g. connecting a LAN to the internet.";

        } else if (name.contains("modem")) {
            deviceName   = "Modem";
            whatItDoes   = "Modulates and demodulates signals to enable data transmission over telephone, cable, or fibre connections.";
            howItWorks   = "• Converts digital data to analogue signals.\n• Converts incoming analogue signals back to digital.\n• Connects your local network to the ISP.\n• Often combined with a router in home devices.";
            whyItMatters = "The link between your local network and the internet — without it, no external connectivity is possible.";

        } else {
            deviceName   = "Router";
            whatItDoes   = "Connects multiple networks and forwards data packets between them using IP addresses.";
            howItWorks   = "• Reads the destination IP address in each packet.\n• Looks up the best route in its routing table.\n• Forwards the packet toward the destination.\n• Operates at OSI Layer 3 (Network layer).";
            whyItMatters = "The backbone of the internet — every website request passes through multiple routers to reach its destination.";
        }
    }

    // -------------------------------------------------------------------------
    // Bind views
    // -------------------------------------------------------------------------

    private void bindViews() {
        cameraPreview    = findViewById(R.id.cameraPreview);
        modelWebView     = findViewById(R.id.modelWebView);
        tvHint           = findViewById(R.id.tvHint);
        tvDeviceTitle    = findViewById(R.id.tvDeviceTitle);
        tvDeviceSubtitle = findViewById(R.id.tvDeviceSubtitle);
        tvWhatItDoes     = findViewById(R.id.tvWhatItDoes);
        tvHowItWorks     = findViewById(R.id.tvHowItWorks);
        tvWhyItMatters   = findViewById(R.id.tvWhyItMatters);
        btnPlace         = findViewById(R.id.btnPlace);
        btnBack          = findViewById(R.id.btnBack);

        tvDeviceTitle.setText(deviceName);
        tvDeviceSubtitle.setText("Detailed explanation of this device function");
        tvWhatItDoes.setText(whatItDoes);
        tvHowItWorks.setText(howItWorks);
        tvWhyItMatters.setText(whyItMatters);
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
                // Wait for background GLB loading to finish before injecting
                new Thread(() -> {
                    // Poll until glbBase64 is ready (max 10 seconds)
                    int waited = 0;
                    while (glbBase64 == null && waited < 100) {
                        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                        waited++;
                    }
                    String fn = glbBase64 != null
                            ? "loadGlbFromBridge();"
                            : "showFallback('" + deviceName + "');";
                    runOnUiThread(() ->
                            view.evaluateJavascript(
                                    "setTimeout(function(){ " + fn + " }, 300);", null));
                }).start();
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
    // JS Bridge
    // -------------------------------------------------------------------------

    private class ModelBridge {

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
        public void onModelLoaded(String s) {
            runOnUiThread(() ->
                    tvHint.setText("Drag to rotate  •  Pinch to zoom"));
        }

        @JavascriptInterface
        public void onModelError(String m) {
            runOnUiThread(() -> tvHint.setText("Showing 3D placeholder"));
        }

        @JavascriptInterface
        public void log(String m) { Log.d(TAG, "JS: " + m); }
    }
}