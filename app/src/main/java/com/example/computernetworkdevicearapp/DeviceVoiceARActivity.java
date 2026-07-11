package com.example.computernetworkdevicearapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * Minimal "avatar + device only" AR screen — no text info cards. The avatar is
 * the sole narrator, speaking the full device explanation aloud via voice.
 * Reuses the exact same GLB-loading (ar_model.html) and avatar+TTS pattern
 * already proven in CameraARActivity / AssemblyARActivity.
 *
 * Launch contract: same as CameraARActivity — pass deviceName as an Intent extra.
 */
public class DeviceVoiceARActivity extends AppCompatActivity
        implements TextToSpeech.OnInitListener {

    private static final String TAG                    = "DeviceVoiceAR";
    private static final int    CAMERA_PERMISSION_CODE = 301;
    private static final int    CHUNK_SIZE             = 100_000;

    private PreviewView    cameraPreview;
    private WebView        modelWebView;
    private WebView        avatarWebView;
    private TextView       tvDeviceTitle, tvHint;
    private TextView       tvInfoTitle, tvInfoWhatItDoes, tvInfoHowItWorks, tvInfoWhyItMatters;
    private View           infoPanel;
    private MaterialButton btnSpeak, btnBack, btnInfoToggle, btnInfoClose;

    private String  deviceName;
    private String  whatItDoes;
    private String  howItWorks;
    private String  whyItMatters;
    private String  glbBase64   = null;

    private boolean isPageReady = false;
    private boolean isGlbReady  = false;

    private String avatarBase64  = null;
    private String threeJs       = null;
    private String gltfLoaderJs  = null;

    private TextToSpeech tts;
    private boolean      ttsReady    = false;
    private boolean      avatarReady = false;
    private boolean      hasSpoken   = false;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_voice_ar);

        deviceName = getIntent().getStringExtra("deviceName");
        if (deviceName == null || deviceName.isEmpty()) deviceName = "Router";

        loadGlbInBackground();
        setupDeviceData();
        bindViews();
        setupNavBarInsets();
        setupModelWebView();
        setupAvatarWebView();
        setupTTS();
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
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (modelWebView != null) modelWebView.destroy();
        if (avatarWebView != null) avatarWebView.destroy();
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // GLB filename
    // -------------------------------------------------------------------------

    private String glbFileName() {
        String name = deviceName.toLowerCase().trim();
        if (name.contains("switch"))                                               return "switch.glb";
        if (name.contains("hub"))                                                  return "hub.glb";
        if (name.contains("firewall"))                                             return "firewall.glb";
        if (name.contains("access") || name.contains("wap") || name.equals("ap")) return "wap.glb";
        if (name.contains("nic") || name.contains("network interface"))            return "nic.glb";
        if (name.contains("repeat"))                                               return "repeater.glb";
        if (name.contains("gateway"))                                              return "gateway.glb";
        if (name.contains("modem"))                                                return "modem.glb";
        if (name.contains("server"))                                               return "server.glb";
        if (name.contains("router"))                                               return "router.glb";
        return "router.glb";
    }

    private void loadGlbInBackground() {
        String file = glbFileName();
        String[] paths = {
                "assets/models/" + file,
                "models/" + file,
                "assets/" + file,
                file
        };

        new Thread(() -> {
            for (String path : paths) {
                try {
                    InputStream is  = getAssets().open(path);
                    byte[]      buf = streamToBytes(is);
                    glbBase64 = Base64.encodeToString(buf, Base64.NO_WRAP);
                    Log.d(TAG, "GLB loaded: " + path);
                    break;
                } catch (OutOfMemoryError e) {
                    Log.e(TAG, "OOM at path: " + path);
                    glbBase64 = null;
                    break;
                } catch (IOException e) {
                    Log.w(TAG, "Not found: " + path);
                } catch (Exception e) {
                    Log.e(TAG, "Error at " + path + ": " + e.getMessage());
                }
            }
            isGlbReady = true;
            String result = glbBase64 != null
                    ? "✅ loaded " + file + " (" + (glbBase64.length()/1024) + "KB)"
                    : "❌ FAILED to find " + file + " in any path — using placeholder";
            runOnUiThread(() -> {
                Toast.makeText(this, "DEBUG: " + result, Toast.LENGTH_LONG).show();
                tryLoadModel();
            });
        }).start();
    }

    private byte[] streamToBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private void tryLoadModel() {
        if (!isPageReady || !isGlbReady) return;
        String multiplier = glbFileName().equals("router.glb") ? "0.62" : "1.0";
        String fn = glbBase64 != null
                ? "window.MODEL_SCALE_MULTIPLIER=" + multiplier + "; loadGlbFromBridge();"
                : "showFallback('" + deviceName + "');";
        modelWebView.evaluateJavascript(fn, null);
    }

    // -------------------------------------------------------------------------
    // Device data — same content as CameraARActivity
    // -------------------------------------------------------------------------

    private void setupDeviceData() {
        String name = deviceName.toLowerCase().trim();

        if (name.contains("switch")) {
            deviceName   = "Layer 2 Switch";
            whatItDoes   = "Connects multiple devices in a LAN using MAC addresses to forward Ethernet frames to the correct destination port.";
            howItWorks   = "Learns the MAC address of each connected device. Reads the destination MAC on each frame. Forwards only to the correct port. Floods if the destination is unknown.";
            whyItMatters = "Reduces unnecessary traffic by delivering data only to the intended device, improving overall LAN performance.";

        } else if (name.contains("hub")) {
            deviceName   = "Layer 1 Hub";
            whatItDoes   = "Connects multiple Ethernet devices and broadcasts all incoming data to every connected port.";
            howItWorks   = "Receives data on one port. Broadcasts to all other ports. No filtering — every device sees all traffic. Creates a shared collision domain.";
            whyItMatters = "Legacy device — understanding hubs explains why switches replaced them to eliminate collisions.";

        } else if (name.contains("firewall")) {
            deviceName   = "Firewall";
            whatItDoes   = "Monitors and controls incoming and outgoing traffic based on predefined security rules.";
            howItWorks   = "Inspects each packet against a ruleset. Blocks unauthorised traffic. Performs network address translation to hide internal IPs. Operates from Layer 3 up to Layer 7.";
            whyItMatters = "First line of defence — without a firewall, internal devices are exposed directly to internet threats.";

        } else if (name.contains("access") || name.contains("wap") || name.equals("ap")) {
            deviceName   = "Wireless Access Point";
            whatItDoes   = "Allows wireless devices to connect to a wired LAN using Wi-Fi, acting as a bridge between wireless clients and the network.";
            howItWorks   = "Broadcasts an SSID for devices to detect. Authenticates connecting devices. Bridges wireless frames to wired Ethernet. Operates at OSI Layer 2.";
            whyItMatters = "Enables mobility — users stay connected to the network without physical cables as they move around.";

        } else if (name.contains("nic") || name.contains("network interface")) {
            deviceName   = "Network Interface Card";
            whatItDoes   = "Hardware component that connects a computer to a network, providing the physical interface for communication.";
            howItWorks   = "Converts data to electrical or optical signals. Has a unique MAC address burned in at manufacturing. Handles framing at OSI Layer 2. Can be wired or wireless.";
            whyItMatters = "Every networked device has a NIC — it is the physical gateway between a device and the network medium.";

        } else if (name.contains("repeat")) {
            deviceName   = "Repeater";
            whatItDoes   = "Amplifies or regenerates a network signal so it can travel longer distances without degrading.";
            howItWorks   = "Receives a weakened signal on one port. Regenerates and amplifies it. Retransmits out the other port. Does not filter or process data.";
            whyItMatters = "Extends network reach beyond the 100 metre cable limit without losing signal quality.";

        } else if (name.contains("gateway")) {
            deviceName   = "Gateway";
            whatItDoes   = "Connects two networks that use different protocols, translating between them so they can communicate.";
            howItWorks   = "Receives data from one network. Translates the protocol or format. Forwards it to the destination network. Can operate at any OSI layer.";
            whyItMatters = "Essential for connecting networks that speak different protocols — for example, connecting a LAN to the internet.";

        } else if (name.contains("modem")) {
            deviceName   = "Modem";
            whatItDoes   = "Modulates and demodulates signals to enable data transmission over telephone, cable, or fibre connections.";
            howItWorks   = "Converts digital data to analogue signals. Converts incoming analogue signals back to digital. Connects your local network to the ISP. Often combined with a router in home devices.";
            whyItMatters = "The link between your local network and the internet — without it, no external connectivity is possible.";

        } else if (name.contains("server")) {
            deviceName   = "Server";
            whatItDoes   = "A powerful computer that stores data and responds to requests from other devices on the network.";
            howItWorks   = "Waits for incoming requests from client devices. Processes each request — for example, looking up a webpage or file. Sends the response back to the requesting device. Can serve many clients at once.";
            whyItMatters = "Servers power almost everything you use online — every website, app, and file you access is stored and delivered by a server somewhere.";

        } else {
            deviceName   = "Router";
            whatItDoes   = "Connects multiple networks and forwards data packets between them using IP addresses.";
            howItWorks   = "Reads the destination IP address in each packet. Looks up the best route in its routing table. Forwards the packet toward the destination. Operates at OSI Layer 3.";
            whyItMatters = "The backbone of the internet — every website request passes through multiple routers to reach its destination.";
        }
    }

    /** Full narration — this screen has no text cards, so the avatar covers everything. */
    private String spokenExplanation() {
        return "This is the " + deviceName + ". " + whatItDoes + " " + howItWorks + " " + whyItMatters;
    }

    // -------------------------------------------------------------------------
    // Nav bar clearance — dynamically pushes the bottom-anchored UI (hint, and
    // by constraint chain, the avatar above it) clear of the system navigation
    // bar, whether the device uses 3-button or gesture navigation.
    // -------------------------------------------------------------------------

    private void setupNavBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(tvHint, (v, insets) -> {
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.bottomMargin = Math.max(navBottom, dp(24)) + dp(55);
            v.setLayoutParams(params);
            return insets;
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    // -------------------------------------------------------------------------
    // Bind views
    // -------------------------------------------------------------------------

    private void bindViews() {
        cameraPreview = findViewById(R.id.cameraPreview);
        modelWebView  = findViewById(R.id.modelWebView);
        avatarWebView = findViewById(R.id.avatarWebView);
        tvHint        = findViewById(R.id.tvHint);
        tvDeviceTitle = findViewById(R.id.tvDeviceTitle);
        btnSpeak      = findViewById(R.id.btnSpeak);
        btnBack       = findViewById(R.id.btnBack);
        btnInfoToggle = findViewById(R.id.btnInfoToggle);
        btnInfoClose  = findViewById(R.id.btnInfoClose);
        infoPanel     = findViewById(R.id.infoPanel);
        tvInfoTitle        = findViewById(R.id.tvInfoTitle);
        tvInfoWhatItDoes   = findViewById(R.id.tvInfoWhatItDoes);
        tvInfoHowItWorks   = findViewById(R.id.tvInfoHowItWorks);
        tvInfoWhyItMatters = findViewById(R.id.tvInfoWhyItMatters);

        tvDeviceTitle.setText(deviceName);
        tvHint.setText("Loading 3D model...");

        tvInfoTitle.setText(deviceName);
        tvInfoWhatItDoes.setText(whatItDoes);
        tvInfoHowItWorks.setText(howItWorks);
        tvInfoWhyItMatters.setText(whyItMatters);
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
    // Device Model WebView — reuses the existing ar_model.html
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
        modelWebView.setWebViewClient(new WebViewClient());
        modelWebView.loadUrl("file:///android_asset/assets/ar_model.html");
    }

    // -------------------------------------------------------------------------
    // Avatar WebView
    // -------------------------------------------------------------------------

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupAvatarWebView() {
        avatarBase64 = readAssetAsBase64("models/avatar.glb");
        threeJs      = readAssetAsString("assets/three.min.js");
        gltfLoaderJs = readAssetAsString("assets/GLTFLoader.js");

        avatarWebView.setBackgroundColor(Color.TRANSPARENT);
        avatarWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings s = avatarWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        avatarWebView.addJavascriptInterface(new AvatarBridge(), "AndroidBridge");

        avatarWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (avatarBase64 != null) {
                    String js = "setTimeout(function(){" +
                            "loadAvatar('data:model/gltf-binary;base64,"
                            + avatarBase64 + "');" +
                            "}, 500);";
                    view.evaluateJavascript(js, null);
                }
            }
        });

        avatarWebView.loadDataWithBaseURL(
                "https://cdn.jsdelivr.net/",
                buildAvatarHtml(),
                "text/html", "UTF-8", null);
    }

    private String readAssetAsBase64(String path) {
        try {
            byte[] b = streamToBytes(getAssets().open(path));
            return Base64.encodeToString(b, Base64.NO_WRAP);
        } catch (IOException e) {
            Log.w(TAG, "Not found: " + path); return null;
        }
    }

    private String readAssetAsString(String path) {
        try {
            return new String(streamToBytes(getAssets().open(path)), "UTF-8");
        } catch (IOException e) {
            Log.w(TAG, "JS not found: " + path); return null;
        }
    }

    // -------------------------------------------------------------------------
    // TTS
    // -------------------------------------------------------------------------

    private void setupTTS() {
        tts = new TextToSpeech(this, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(Locale.US);
            tts.setSpeechRate(0.95f);
            ttsReady = !(r == TextToSpeech.LANG_MISSING_DATA
                    || r == TextToSpeech.LANG_NOT_SUPPORTED);

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String id) { }
                @Override
                public void onDone(String id) {
                    runOnUiThread(() -> avatarWebView.evaluateJavascript("stopTalking()", null));
                }
                @Override
                public void onError(String id) {
                    runOnUiThread(() -> avatarWebView.evaluateJavascript("stopTalking()", null));
                }
            });
        }
        maybeAutoSpeak();
    }

    private void maybeAutoSpeak() {
        if (hasSpoken || !ttsReady || !avatarReady) return;
        hasSpoken = true;
        speakExplanation();
    }

    private void speakExplanation() {
        if (!ttsReady) {
            Toast.makeText(this, "Text-to-Speech not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        avatarWebView.evaluateJavascript("startTalking()", null);
        Bundle params = new Bundle();
        tts.speak(spokenExplanation(), TextToSpeech.QUEUE_FLUSH, params, "device_explanation");
    }

    // -------------------------------------------------------------------------
    // Buttons
    // -------------------------------------------------------------------------

    private void setupButtons() {
        btnSpeak.setOnClickListener(v -> speakExplanation());
        btnBack.setOnClickListener(v -> finish());
        btnInfoToggle.setOnClickListener(v -> showInfoPanel(true));
        btnInfoClose.setOnClickListener(v -> showInfoPanel(false));
    }

    private void showInfoPanel(boolean show) {
        infoPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        btnInfoClose.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // -------------------------------------------------------------------------
    // JS Bridges
    // -------------------------------------------------------------------------

    private class ModelBridge {
        @JavascriptInterface
        public int getGlbLength() {
            return glbBase64 != null ? glbBase64.length() : 0;
        }
        @JavascriptInterface
        public String getGlbChunk(int offset) {
            if (glbBase64 == null || offset >= glbBase64.length()) return "";
            return glbBase64.substring(offset, Math.min(offset + CHUNK_SIZE, glbBase64.length()));
        }
        @JavascriptInterface
        public void onModelLoaded(String s) {
            runOnUiThread(() -> tvHint.setText("Drag to rotate  •  Pinch to zoom"));
        }
        @JavascriptInterface
        public void onModelError(String m) {
            Log.e(TAG, "Model error: " + m);
            runOnUiThread(() -> tvHint.setText("Showing 3D placeholder"));
        }
        @JavascriptInterface
        public void log(String m) {
            if ("page_ready".equals(m)) {
                isPageReady = true;
                runOnUiThread(() -> tryLoadModel());
            }
        }
    }

    private class AvatarBridge {
        @JavascriptInterface
        public void onAvatarLoaded() {
            avatarReady = true;
            runOnUiThread(() -> maybeAutoSpeak());
        }
        @JavascriptInterface
        public void log(String m) { Log.d(TAG, "Avatar JS: " + m); }
    }

    // -------------------------------------------------------------------------
    // Avatar HTML — identical scene/lip-sync logic used across the app
    // -------------------------------------------------------------------------

    private String buildAvatarHtml() {
        String threeScript = threeJs != null
                ? "<script>" + threeJs + "</script>"
                : "<script src='https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.min.js'></script>";
        String loaderScript = gltfLoaderJs != null
                ? "<script>" + gltfLoaderJs + "</script>"
                : "<script src='https://cdn.jsdelivr.net/npm/three@0.160.0/examples/js/loaders/GLTFLoader.js'></script>";

        return "<!DOCTYPE html><html><head>" +
                "<meta charset='UTF-8'>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>" +
                "*{margin:0;padding:0;}" +
                "html,body{width:100%;height:100%;background:transparent;overflow:hidden;}" +
                "canvas{display:block;background:transparent;}" +
                "#status{position:fixed;bottom:2px;left:0;right:0;text-align:center;" +
                "color:#7EDCFF;font:9px system-ui;pointer-events:none;}" +
                "</style></head><body>" +
                "<div id='status'>Loading...</div>" +
                threeScript + loaderScript +
                "<script>" +
                "var renderer,scene,camera,clock,mixer=null;" +
                "var avatarModel=null,idleAction=null,talkAction=null;" +
                "var headBone=null,jawBone=null;" +
                "var leftUpperArmBone=null,rightUpperArmBone=null;" +
                "var leftArmBone=null,rightArmBone=null;" +
                "var leftHandBone=null,rightHandBone=null;" +
                "var morphTargets=[];" +
                "var isTalking=false,talkPhase=0,talkWeight=0;" +

                "function initScene(){" +
                "  clock=new THREE.Clock();" +
                "  renderer=new THREE.WebGLRenderer({antialias:true,alpha:true});" +
                "  renderer.setPixelRatio(Math.min(devicePixelRatio,2));" +
                "  renderer.setSize(innerWidth,innerHeight);" +
                "  renderer.setClearColor(0x000000,0);" +
                "  document.body.appendChild(renderer.domElement);" +
                "  scene=new THREE.Scene();" +
                "  camera=new THREE.PerspectiveCamera(35,innerWidth/innerHeight,0.01,10);" +
                "  camera.position.set(0,1.2,3.5);" +
                "  camera.lookAt(0,1.0,0);" +
                "  scene.add(new THREE.AmbientLight(0xffffff,1.8));" +
                "  var k=new THREE.DirectionalLight(0xffffff,1.2);" +
                "  k.position.set(1,3,2);scene.add(k);" +
                "  animate();setStatus('');" +
                "}" +

                "window.loadAvatar=function(url){" +
                "  setStatus('Loading...');" +
                "  new THREE.GLTFLoader().load(url,function(gltf){" +
                "    avatarModel=gltf.scene;scene.add(avatarModel);" +
                "    avatarModel.rotation.y=0.3;" +
                "    avatarModel.position.x=-0.35;" +
                "    avatarModel.traverse(function(node){" +
                "      var n=(node.name||'').toLowerCase();" +
                "      if(!headBone&&n.includes('head'))headBone=node;" +
                "      if(!jawBone&&n.includes('jaw'))jawBone=node;" +
                "      if(!leftHandBone&&(n.includes('lefthand')||n.includes('hand_l')))leftHandBone=node;" +
                "      if(!rightHandBone&&(n.includes('righthand')||n.includes('hand_r')))rightHandBone=node;" +
                "      if(!leftArmBone&&(n.includes('leftforearm')||n.includes('forearm_l')))leftArmBone=node;" +
                "      if(!rightArmBone&&(n.includes('rightforearm')||n.includes('forearm_r')))rightArmBone=node;" +
                "      if(!leftUpperArmBone&&n==='leftarm')leftUpperArmBone=node;" +
                "      if(!rightUpperArmBone&&n==='rightarm')rightUpperArmBone=node;" +
                "      if(node.isMesh&&node.morphTargetDictionary){" +
                "        var dict=node.morphTargetDictionary;" +
                "        var primaryNames=['jawOpen','mouthOpen'];" +
                "        var visemeNames=['viseme_aa','viseme_E','viseme_I','viseme_O','viseme_U'," +
                "          'viseme_PP','viseme_FF','viseme_TH','viseme_DD'," +
                "          'viseme_kk','viseme_CH','viseme_SS','viseme_nn','viseme_RR'];" +
                "        var foundPrimary=primaryNames.filter(function(k){return dict.hasOwnProperty(k);});" +
                "        var foundViseme=visemeNames.filter(function(k){return dict.hasOwnProperty(k);});" +
                "        if((foundPrimary.length+foundViseme.length)>0){" +
                "          var mats=Array.isArray(node.material)?node.material:[node.material];" +
                "          mats.forEach(function(m){if(m){m.morphTargets=true;m.morphNormals=true;m.needsUpdate=true;}});" +
                "          morphTargets.push({" +
                "            mesh:node," +
                "            primary:foundPrimary.map(function(k){return dict[k];})," +
                "            viseme:foundViseme.map(function(k){return dict[k];})" +
                "          });" +
                "        }" +
                "      }" +
                "    });" +
                "    var anims=gltf.animations||[];" +
                "    if(anims.length){" +
                "      mixer=new THREE.AnimationMixer(avatarModel);" +
                "      var idleClip=anims.find(function(a){return a.name.toLowerCase().includes('idle');})||anims[0];" +
                "      idleAction=mixer.clipAction(idleClip);idleAction.play();" +
                "      var talkClip=anims.find(function(a){var nm=a.name.toLowerCase();return nm.includes('talk')||nm.includes('speak');});" +
                "      if(talkClip){talkAction=mixer.clipAction(talkClip);talkAction.setLoop(THREE.LoopRepeat,Infinity);}" +
                "    }" +
                "    setStatus('');" +
                "    if(window.AndroidBridge)AndroidBridge.onAvatarLoaded();" +
                "  },function(x){if(x.total)setStatus((x.loaded/x.total*100|0)+'%');},function(e){setStatus('Error');});" +
                "};" +

                "window.startTalking=function(){" +
                "  isTalking=true;talkPhase=0;" +
                "  if(talkAction){idleAction&&idleAction.fadeOut(0.2);talkAction.reset().fadeIn(0.2).play();}" +
                "};" +

                "function applyTalkPose(dt){" +
                "  talkPhase+=dt;var t=talkPhase*3.0;" +
                "  var target=isTalking?1:0;" +
                "  talkWeight+=(target-talkWeight)*Math.min(1,dt*6);" +

                "  if(morphTargets.length>0){" +
                "    var mainOpen=Math.max(0,0.45*Math.abs(Math.sin(t*1.4))+0.08*Math.random())*talkWeight;" +
                "    morphTargets.forEach(function(mt){" +
                "      mt.primary.forEach(function(idx){mt.mesh.morphTargetInfluences[idx]=mainOpen;});" +
                "      mt.viseme.forEach(function(idx,i){" +
                "        mt.mesh.morphTargetInfluences[idx]=Math.max(0,0.35*Math.abs(Math.sin(t*0.9+i*0.8))*mainOpen);});" +
                "    });" +
                "  }" +
                "  if(jawBone){jawBone.rotation.x+=0.35*Math.abs(Math.sin(t*1.4))*talkWeight;}" +
                "  if(headBone){headBone.rotation.x+=0.05*Math.sin(t*0.4)*talkWeight;headBone.rotation.y+=0.04*Math.sin(t*0.27)*talkWeight;}" +

                "  var lift=Math.max(0,Math.sin(t*1.6))*talkWeight;" +
                "  if(leftUpperArmBone){" +
                "    leftUpperArmBone.rotation.z+=0.85*lift;" +
                "    leftUpperArmBone.rotation.x+=0.1*lift;}" +
                "  if(leftArmBone){leftArmBone.rotation.z+=0.4*lift;}" +
                "}" +

                "window.stopTalking=function(){" +
                "  isTalking=false;" +
                "  if(talkAction){" +
                "    talkAction.fadeOut(0.3);" +
                "    if(idleAction){idleAction.reset().fadeIn(0.3).play();}" +
                "  }" +
                "};" +

                "function animate(){" +
                "  requestAnimationFrame(animate);" +
                "  var dt=clock?clock.getDelta():0;" +
                "  if(mixer)mixer.update(dt);" +
                "  if(isTalking||talkWeight>0.001)applyTalkPose(dt);" +
                "  if(renderer&&scene&&camera)renderer.render(scene,camera);" +
                "}" +
                "function setStatus(m){document.getElementById('status').textContent=m;}" +
                "window.addEventListener('load',function(){var t=0;(function wait(){if(typeof THREE!=='undefined'&&THREE.GLTFLoader){initScene();}else if(t++<30){setTimeout(wait,200);}else{setStatus('Failed');}})();});" +
                "window.addEventListener('resize',function(){if(!renderer)return;camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();renderer.setSize(innerWidth,innerHeight);});" +
                "</script></body></html>";
    }
}