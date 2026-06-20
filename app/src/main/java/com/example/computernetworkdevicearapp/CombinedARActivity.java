package com.example.computernetworkdevicearapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Html;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.ScrollView;
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
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class CombinedARActivity extends AppCompatActivity
        implements TextToSpeech.OnInitListener {

    private static final String TAG                   = "CombinedAR";
    private static final int    CAMERA_PERMISSION_CODE = 600;
    private static final int    SPEECH_REQUEST_CODE    = 700;
    private static final int    CHUNK_SIZE             = 100_000;
    private static final String BACKEND_URL = "http://172.20.10.7:3000/inworld-chat";

    // All available devices
    private static final String[] DEVICES = {
            "Router", "Switch", "Hub", "Firewall",
            "Access Point", "NIC", "Repeater", "Gateway"
    };

    private PreviewView    cameraPreview;
    private WebView        avatarWebView;
    private WebView        modelWebView;
    private TextView       tvAvatarStatus, tvAssistantReply, tvDeviceName;
    private EditText       etQuestion;
    private MaterialButton btnAsk, btnSpeak, btnBack, btnMic, btnChangeDevice;
    private View           bottomPanel;
    private ScrollView     replyScroll;

    // TTS
    private TextToSpeech tts;
    private boolean      ttsReady = false;

    // Avatar assets
    private String avatarBase64  = null;
    private String threeJs       = null;
    private String gltfLoaderJs  = null;

    // Device model
    private String  deviceName     = "Router";
    private String  glbBase64      = null;
    private boolean isPageFinished = false;
    private boolean isGlbReady     = false;

    // Full conversation log (You / Assistant turns), rendered as HTML in tvAssistantReply
    private final StringBuilder conversationLog = new StringBuilder();
    private static final String DEFAULT_GREETING =
            "Ask me anything about this network device.";

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_combined_ar);

        // Allow content to draw edge-to-edge, then handle keyboard/nav insets manually
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        deviceName = getIntent().getStringExtra("deviceName");
        if (deviceName == null || deviceName.isEmpty()) deviceName = "Router";

        avatarBase64 = readAssetAsBase64("models/avatar.glb");
        threeJs      = readAssetAsString("assets/three.min.js");
        gltfLoaderJs = readAssetAsString("assets/GLTFLoader.js");

        bindViews();
        setupKeyboardInsets();
        setupAvatarWebView();
        setupModelWebView();
        setupButtons();
        setupTTS();
        loadGlbInBackground();
        resetConversation();

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
        if (req == CAMERA_PERMISSION_CODE
                && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SPEECH_REQUEST_CODE
                && resultCode == RESULT_OK
                && data != null) {
            ArrayList<String> results =
                    data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                etQuestion.setText(results.get(0));
                etQuestion.setSelection(etQuestion.getText().length());
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (avatarWebView != null) avatarWebView.destroy();
        if (modelWebView != null) modelWebView.destroy();
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // Bind views
    // -------------------------------------------------------------------------

    private void bindViews() {
        cameraPreview    = findViewById(R.id.cameraPreview);
        avatarWebView    = findViewById(R.id.avatarWebView);
        modelWebView     = findViewById(R.id.modelWebView);
        tvAvatarStatus   = findViewById(R.id.tvAvatarStatus);
        tvAssistantReply = findViewById(R.id.tvAssistantReply);
        tvDeviceName     = findViewById(R.id.tvDeviceName);
        etQuestion       = findViewById(R.id.etQuestion);
        btnAsk           = findViewById(R.id.btnAsk);
        btnSpeak         = findViewById(R.id.btnSpeak);
        btnBack          = findViewById(R.id.btnBack);
        btnMic           = findViewById(R.id.btnMic);
        btnChangeDevice  = findViewById(R.id.btnChangeDevice);
        bottomPanel      = findViewById(R.id.bottomPanel);
        replyScroll      = findViewById(R.id.replyScroll);

        tvDeviceName.setText(deviceName);
    }

    // -------------------------------------------------------------------------
    // Keyboard / nav-bar inset handling
    // -------------------------------------------------------------------------

    private void setupKeyboardInsets() {
        final int baseMarginPx = (int) (48 * getResources().getDisplayMetrics().density);

        ViewCompat.setOnApplyWindowInsetsListener(bottomPanel, (v, insets) -> {
            int imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;

            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.bottomMargin = imeBottom > 0
                    ? imeBottom
                    : Math.max(navBottom, baseMarginPx);
            v.setLayoutParams(params);

            return insets;
        });
    }

    // -------------------------------------------------------------------------
    // Conversation log — full chat history (You / Assistant)
    // -------------------------------------------------------------------------

    private void resetConversation() {
        conversationLog.setLength(0);
        conversationLog.append(DEFAULT_GREETING);
        renderConversation();
    }

    private void appendMessage(String sender, String message) {
        if (conversationLog.length() > 0) conversationLog.append("<br><br>");
        String color = sender.equals("You") ? "#7EDCFF" : "#9EE6A8";
        String safeMessage = message
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\n", "<br>");
        conversationLog.append("<b><font color='").append(color).append("'>")
                .append(sender).append(":</font></b> ").append(safeMessage);
        renderConversation();
    }

    private void renderConversation() {
        runOnUiThread(() -> {
            tvAssistantReply.setText(
                    Html.fromHtml(conversationLog.toString(), Html.FROM_HTML_MODE_LEGACY));
            if (replyScroll != null) {
                replyScroll.post(() -> replyScroll.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    /** Returns the plain text of the last "Assistant" turn, for the Speak button. */
    private String getLastAssistantReply() {
        String html = conversationLog.toString();
        int idx = html.lastIndexOf("Assistant:</font></b> ");
        if (idx == -1) return "";
        String tail = html.substring(idx + "Assistant:</font></b> ".length());
        return Html.fromHtml(tail, Html.FROM_HTML_MODE_LEGACY).toString().trim();
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
                Log.e(TAG, "Camera error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // -------------------------------------------------------------------------
    // Change device — popup menu with all 8 devices
    // -------------------------------------------------------------------------

    private void showDeviceMenu() {
        PopupMenu popup = new PopupMenu(this, btnChangeDevice);
        for (int i = 0; i < DEVICES.length; i++) {
            popup.getMenu().add(0, i, i, DEVICES[i]);
        }
        popup.setOnMenuItemClickListener(item -> {
            String selected = DEVICES[item.getItemId()];
            switchDevice(selected);
            return true;
        });
        popup.show();
    }

    private void switchDevice(String newDevice) {
        deviceName = newDevice;
        tvDeviceName.setText(deviceName);
        tvAvatarStatus.setText("Loading model...");
        resetConversation();

        // Reset flags and reload GLB
        isGlbReady = false;
        glbBase64  = null;
        loadGlbInBackground();
    }

    // -------------------------------------------------------------------------
    // GLB loading — background thread with callback
    // -------------------------------------------------------------------------

    private void loadGlbInBackground() {
        String file = glbFileName();
        new Thread(() -> {
            try {
                InputStream is  = getAssets().open("models/" + file);
                byte[]      buf = streamToBytes(is);
                glbBase64 = Base64.encodeToString(buf, Base64.NO_WRAP);
                Log.d(TAG, "GLB ready: " + file);
            } catch (Exception e) {
                Log.e(TAG, "GLB error: " + e.getMessage());
                glbBase64 = null;
            }
            isGlbReady = true;
            runOnUiThread(() -> {
                tvAvatarStatus.setText("Ready");
                tryLoadModel();
            });
        }).start();
    }

    private void tryLoadModel() {
        if (!isPageFinished || !isGlbReady) return;
        String fn = glbBase64 != null
                ? "loadGlbFromBridge();"
                : "showFallback('" + deviceName + "');";
        modelWebView.evaluateJavascript(
                "setTimeout(function(){ " + fn + " }, 300);", null);
    }

    private String glbFileName() {
        String name = deviceName.toLowerCase().trim();
        if (name.contains("switch"))   return "switch.glb";
        if (name.contains("hub"))      return "hub.glb";
        if (name.contains("firewall")) return "firewall.glb";
        if (name.contains("access") || name.contains("wap")) return "wap.glb";
        if (name.contains("nic"))      return "nic.glb";
        if (name.contains("repeat"))   return "repeater.glb";
        if (name.contains("gateway"))  return "gateway.glb";
        return "router.glb";
    }

    private byte[] streamToBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Avatar WebView
    // -------------------------------------------------------------------------

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupAvatarWebView() {
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

    // -------------------------------------------------------------------------
    // Device Model WebView
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
                isPageFinished = true;
                tryLoadModel();
            }
        });

        modelWebView.loadUrl("file:///android_asset/assets/ar_model.html");
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
            tts.setSpeechRate(0.9f);
            ttsReady = !(r == TextToSpeech.LANG_MISSING_DATA
                    || r == TextToSpeech.LANG_NOT_SUPPORTED);

            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String id) {
                    runOnUiThread(() -> tvAvatarStatus.setText("Speaking..."));
                }
                @Override
                public void onDone(String id) {
                    runOnUiThread(() -> {
                        avatarWebView.evaluateJavascript("stopTalking()", null);
                        tvAvatarStatus.setText("Ready");
                    });
                }
                @Override
                public void onError(String id) {
                    runOnUiThread(() ->
                            avatarWebView.evaluateJavascript("stopTalking()", null));
                }
            });
        }
    }

    private void speakWithAvatar(String text) {
        if (!ttsReady) {
            Toast.makeText(this, "TTS not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        tvAvatarStatus.setText("Speaking...");
        // Start the gesture/lip-sync animation immediately — don't wait for
        // TTS onStart, which can fire late (sometimes near the end of short replies).
        avatarWebView.evaluateJavascript("startTalking()", null);
        android.os.Bundle params = new android.os.Bundle();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "avatar_speech");
    }

    // -------------------------------------------------------------------------
    // Buttons
    // -------------------------------------------------------------------------

    private void setupButtons() {

        // Change device — show popup menu
        btnChangeDevice.setOnClickListener(v -> showDeviceMenu());

        btnAsk.setOnClickListener(v -> {
            String q = etQuestion.getText().toString().trim();
            if (q.isEmpty()) {
                Toast.makeText(this, "Please enter a question",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            appendMessage("You", q);
            etQuestion.setText("");
            tvAvatarStatus.setText("Thinking...");
            askAssistant(q);
        });

        btnSpeak.setOnClickListener(v -> {
            String reply = getLastAssistantReply();
            if (reply.isEmpty()) {
                Toast.makeText(this, "No reply to speak yet",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            speakWithAvatar(reply);
        });

        btnBack.setOnClickListener(v -> finish());

        btnMic.setOnClickListener(v -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask about this device...");
            try {
                startActivityForResult(intent, SPEECH_REQUEST_CODE);
            } catch (Exception e) {
                Toast.makeText(this, "Speech not available",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // -------------------------------------------------------------------------
    // AI assistant
    // -------------------------------------------------------------------------

    private void askAssistant(String question) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(BACKEND_URL);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(20_000);
                conn.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("deviceName",        deviceName);
                body.put("deviceExplanation", "A computer network device used in networking.");
                body.put("question",          question);

                BufferedWriter w = new BufferedWriter(
                        new OutputStreamWriter(conn.getOutputStream()));
                w.write(body.toString()); w.flush(); w.close();

                int code = conn.getResponseCode();
                BufferedReader r = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 300
                                ? conn.getInputStream()
                                : conn.getErrorStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();

                String reply = new JSONObject(sb.toString())
                        .optString("reply", "Sorry, no response.");

                runOnUiThread(() -> {
                    appendMessage("Assistant", reply);
                    tvAvatarStatus.setText("Ready — tap Speak");
                });

            } catch (java.net.ConnectException e) {
                runOnUiThread(() -> {
                    appendMessage("Assistant", "Cannot reach server.");
                    tvAvatarStatus.setText("Offline");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendMessage("Assistant", "Error: " + e.getMessage());
                    tvAvatarStatus.setText("Error");
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Avatar HTML
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
                "#status{position:fixed;bottom:4px;left:0;right:0;text-align:center;" +
                "color:#7EDCFF;font:10px system-ui;pointer-events:none;}" +
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
                "          if(window.AndroidBridge)AndroidBridge.log('LipSync mesh='+node.name+' primary='+foundPrimary.join(',')+' visemes='+foundViseme.join(','));" +
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
                "  if(window.AndroidBridge)AndroidBridge.log('startTalking. morphMeshes='+morphTargets.length+' upperArmL='+(!!leftUpperArmBone)+' upperArmR='+(!!rightUpperArmBone));" +
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

    // -------------------------------------------------------------------------
    // JS Bridges
    // -------------------------------------------------------------------------

    private class AvatarBridge {
        @JavascriptInterface
        public void onAvatarLoaded() {
            runOnUiThread(() -> tvAvatarStatus.setText("Ready"));
        }
        @JavascriptInterface
        public void log(String m) { Log.d(TAG, "Avatar JS: " + m); }
    }

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
        public void onModelLoaded(String s) { Log.d(TAG, "Model loaded"); }
        @JavascriptInterface
        public void onModelError(String m)  { Log.w(TAG, "Model error: " + m); }
        @JavascriptInterface
        public void log(String m)           { Log.d(TAG, "Model JS: " + m); }
    }

    // -------------------------------------------------------------------------
    // Asset helpers
    // -------------------------------------------------------------------------

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
}