package com.example.computernetworkdevicearapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
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

public class AvatarActivity extends AppCompatActivity
        implements TextToSpeech.OnInitListener {

    private static final String TAG                   = "AvatarActivity";
    private static final int    CAMERA_PERMISSION_CODE = 400;
    private static final int    SPEECH_REQUEST_CODE    = 500;
    private static final String BACKEND_URL = "http://10.204.96.101:3000/inworld-chat";

    private PreviewView    cameraPreview;
    private WebView        avatarWebView;
    private TextView       tvAssistantStatus, tvAssistantReply;
    private EditText       etQuestion;
    private MaterialButton btnAsk, btnSpeak, btnBack, btnMic;

    // Android TTS
    private TextToSpeech tts;
    private boolean      ttsReady = false;

    private String avatarBase64  = null;
    private String threeJs       = null;
    private String gltfLoaderJs  = null;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar);

        avatarBase64 = readAssetAsBase64("models/avatar.glb");
        threeJs      = readAssetAsString("assets/three.min.js");
        gltfLoaderJs = readAssetAsString("assets/GLTFLoader.js");

        bindViews();
        setupAvatarWebView();
        setupButtons();
        setupTTS();
        handleKeyboard();

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

    // Receive speech-to-text result
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
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // View binding
    // -------------------------------------------------------------------------

    private void bindViews() {
        cameraPreview     = findViewById(R.id.cameraPreview);
        avatarWebView     = findViewById(R.id.avatarWebView);
        tvAssistantStatus = findViewById(R.id.tvAssistantStatus);
        tvAssistantReply  = findViewById(R.id.tvAssistantReply);
        etQuestion        = findViewById(R.id.etAssistantQuestion);
        btnAsk            = findViewById(R.id.btnAskAssistant);
        btnSpeak          = findViewById(R.id.btnSpeakReply);
        btnBack           = findViewById(R.id.btnBack);
        btnMic            = findViewById(R.id.btnSpeakQuestion);
    }

    // -------------------------------------------------------------------------
    // Keyboard handler
    // -------------------------------------------------------------------------

    private void handleKeyboard() {
        View rootView = findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;
            LinearLayout bottomPanel = findViewById(R.id.bottomPanel);
            if (keypadHeight > screenHeight * 0.15) {
                bottomPanel.setTranslationY(-keypadHeight);
            } else {
                bottomPanel.setTranslationY(0);
            }
        });
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
    // Build avatar HTML — Three.js rendering + procedural lip sync animation
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
                "color:#7EDCFF;font:11px system-ui;pointer-events:none;}" +
                "</style></head><body>" +
                "<div id='status'>Loading avatar...</div>" +
                threeScript + loaderScript +
                "<script>" +

                "var renderer,scene,camera,clock,mixer=null;" +
                "var avatarModel=null;" +
                "var morphMeshes=[];" +
                "var idleAction=null,talkAction=null;" +
                "var headBone=null,jawBone=null;" +
                "var leftArmBone=null,rightArmBone=null;" +
                "var leftHandBone=null,rightHandBone=null;" +
                "var mouthMorphIndices=[];" +
                "var mouthMesh=null;" +
                "var isTalking=false,talkInterval=null;" +

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
                "  var f=new THREE.DirectionalLight(0x7EDCFF,0.4);" +
                "  f.position.set(-2,0,-1);scene.add(f);" +
                "  animate();" +
                "  setStatus('');" +
                "}" +

                "window.loadAvatar=function(url){" +
                "  setStatus('Loading avatar...');" +
                "  new THREE.GLTFLoader().load(url," +
                "    function(gltf){" +
                "      avatarModel=gltf.scene;" +
                "      scene.add(avatarModel);" +
                "      avatarModel.traverse(function(node){" +
                "        var n=(node.name||'').toLowerCase();" +
                "        if(!headBone&&n.includes('head'))headBone=node;" +
                "        if(!jawBone&&n.includes('jaw'))jawBone=node;" +
                "        if(!leftHandBone&&(n.includes('lefthand')||n.includes('hand_l')||n.includes('l_hand')))leftHandBone=node;" +
                "        if(!rightHandBone&&(n.includes('righthand')||n.includes('hand_r')||n.includes('r_hand')))rightHandBone=node;" +
                "        if(!leftArmBone&&(n.includes('leftforearm')||n.includes('forearm_l')||n.includes('lowerarm_l')))leftArmBone=node;" +
                "        if(!rightArmBone&&(n.includes('rightforearm')||n.includes('forearm_r')||n.includes('lowerarm_r')))rightArmBone=node;" +
                "        if(node.isMesh&&node.morphTargetDictionary&&Object.keys(node.morphTargetDictionary).length>0){" +
                "          morphMeshes.push(node);" +
                "          var dict=node.morphTargetDictionary;" +
                "          var keys=Object.keys(dict);" +
                "          var mouthKeys=keys.filter(function(k){" +
                "            var kl=k.toLowerCase();" +
                "            return kl.includes('jaw')||kl.includes('mouth')||" +
                "                   kl.includes('lip')||kl.includes('viseme')||" +
                "                   kl.includes('open')||kl.includes('aa')||" +
                "                   kl.includes('ee')||kl.includes('oh')||" +
                "                   kl.includes('smile')||kl.includes('talk');" +
                "          });" +
                "          if(mouthKeys.length>0&&!mouthMesh){" +
                "            mouthMesh=node;" +
                "            mouthMorphIndices=mouthKeys.map(function(k){return dict[k];});" +
                "            if(window.AndroidBridge)AndroidBridge.log('Mouth morphs: '+mouthKeys.join(','));" +
                "          }" +
                "        }" +
                "      });" +
                "      var anims=gltf.animations||[];" +
                "      if(anims.length){" +
                "        mixer=new THREE.AnimationMixer(avatarModel);" +
                "        var idleClip=anims.find(function(a){return a.name.toLowerCase().includes('idle');})||anims[0];" +
                "        idleAction=mixer.clipAction(idleClip);" +
                "        idleAction.play();" +
                "        var talkClip=anims.find(function(a){" +
                "          var nm=a.name.toLowerCase();" +
                "          return nm.includes('talk')||nm.includes('speak');" +
                "        });" +
                "        if(talkClip){talkAction=mixer.clipAction(talkClip);talkAction.setLoop(THREE.LoopRepeat,Infinity);}" +
                "      }" +
                "      setStatus('');" +
                "      if(window.AndroidBridge)AndroidBridge.onAvatarLoaded();" +
                "    }," +
                "    function(x){if(x.total)setStatus('Loading '+(x.loaded/x.total*100|0)+'%');}," +
                "    function(e){setStatus('Avatar load error: '+e.message);}" +
                "  );" +
                "};" +

                // Procedural lip sync — called from Android TTS callbacks
                "window.startTalking=function(){" +
                "  isTalking=true;" +
                "  if(talkAction&&idleAction){idleAction.fadeOut(0.2);talkAction.reset().fadeIn(0.2).play();}" +
                "  var phase=0;" +
                "  talkInterval=setInterval(function(){" +
                "    phase++;" +
                "    var t=phase*0.12;" +
                "    if(mouthMesh&&mouthMorphIndices.length>0){" +
                "      var jawVal=Math.max(0,0.5*Math.abs(Math.sin(t*3.0))+0.2*Math.random());" +
                "      mouthMorphIndices.forEach(function(idx,i){" +
                "        var offset=i*0.7;" +
                "        mouthMesh.morphTargetInfluences[idx]=Math.max(0,0.6*Math.abs(Math.sin(t*2.5+offset))*jawVal);" +
                "      });" +
                "    }" +
                "    if(jawBone){jawBone.rotation.x=0.15*Math.abs(Math.sin(t*3.0));}" +
                "    if(headBone){headBone.rotation.x=0.06*Math.sin(t*1.2);headBone.rotation.y=0.05*Math.sin(t*0.8);}" +
                "    if(leftHandBone){leftHandBone.rotation.z=0.1*Math.sin(t*1.5);leftHandBone.rotation.x=0.08*Math.sin(t*1.1+1.0);}" +
                "    if(rightHandBone){rightHandBone.rotation.z=-0.1*Math.sin(t*1.5+0.5);rightHandBone.rotation.x=0.08*Math.sin(t*1.1);}" +
                "    if(leftArmBone){leftArmBone.rotation.z=0.05*Math.sin(t*0.9+0.3);}" +
                "    if(rightArmBone){rightArmBone.rotation.z=-0.05*Math.sin(t*0.9);}" +
                "    if(avatarModel){avatarModel.rotation.y=0.025*Math.sin(t*0.5);avatarModel.position.y=0.008*Math.sin(t*0.9);}" +
                "  },80);" +
                "};" +

                "window.stopTalking=function(){" +
                "  isTalking=false;" +
                "  if(talkInterval){clearInterval(talkInterval);talkInterval=null;}" +
                "  if(talkAction&&idleAction){talkAction.fadeOut(0.3);idleAction.reset().fadeIn(0.3).play();}" +
                "  if(mouthMesh&&mouthMorphIndices.length>0){mouthMorphIndices.forEach(function(idx){mouthMesh.morphTargetInfluences[idx]=0;});}" +
                "  if(jawBone){jawBone.rotation.x=0;}" +
                "  if(headBone){headBone.rotation.x=0;headBone.rotation.y=0;}" +
                "  if(leftHandBone){leftHandBone.rotation.z=0;leftHandBone.rotation.x=0;}" +
                "  if(rightHandBone){rightHandBone.rotation.z=0;rightHandBone.rotation.x=0;}" +
                "  if(leftArmBone){leftArmBone.rotation.z=0;}" +
                "  if(rightArmBone){rightArmBone.rotation.z=0;}" +
                "  if(avatarModel){avatarModel.rotation.y=0;avatarModel.position.y=0;}" +
                "};" +

                "function animate(){" +
                "  requestAnimationFrame(animate);" +
                "  var dt=clock?clock.getDelta():0;" +
                "  if(mixer)mixer.update(dt);" +
                "  if(renderer&&scene&&camera)renderer.render(scene,camera);" +
                "}" +

                "function setStatus(m){document.getElementById('status').textContent=m;}" +

                "window.addEventListener('load',function(){" +
                "  var t=0;" +
                "  (function wait(){" +
                "    if(typeof THREE!=='undefined'&&THREE.GLTFLoader){initScene();}" +
                "    else if(t++<30){setTimeout(wait,200);}" +
                "    else{setStatus('Three.js failed to load');}" +
                "  })();" +
                "});" +

                "window.addEventListener('resize',function(){" +
                "  if(!renderer)return;" +
                "  camera.aspect=innerWidth/innerHeight;" +
                "  camera.updateProjectionMatrix();" +
                "  renderer.setSize(innerWidth,innerHeight);" +
                "});" +
                "</script></body></html>";
    }

    // -------------------------------------------------------------------------
    // TTS setup — Android TTS with lip sync callbacks
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
                public void onStart(String utteranceId) {
                    // TTS started — trigger avatar lip sync animation
                    runOnUiThread(() -> {
                        tvAssistantStatus.setText("Speaking...");
                        avatarWebView.evaluateJavascript("startTalking()", null);
                    });
                }

                @Override
                public void onDone(String utteranceId) {
                    // TTS done — stop avatar lip sync animation
                    runOnUiThread(() -> {
                        avatarWebView.evaluateJavascript("stopTalking()", null);
                        tvAssistantStatus.setText("Ready");
                    });
                }

                @Override
                public void onError(String utteranceId) {
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
        tvAssistantStatus.setText("Speaking...");
        android.os.Bundle params = new android.os.Bundle();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "avatar_speech");
    }

    // -------------------------------------------------------------------------
    // Buttons
    // -------------------------------------------------------------------------

    private void setupButtons() {

        btnAsk.setOnClickListener(v -> {
            String q = etQuestion.getText().toString().trim();
            if (q.isEmpty()) {
                Toast.makeText(this, "Please enter a question",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            tvAssistantStatus.setText("Thinking...");
            tvAssistantReply.setText("Please wait...");
            askAssistant(q);
        });

        // Speak — Android TTS speaks the reply and triggers lip sync
        btnSpeak.setOnClickListener(v -> {
            String reply = tvAssistantReply.getText().toString().trim();
            if (reply.isEmpty() || reply.equals("Please wait...")) {
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
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your question...");
            try {
                startActivityForResult(intent, SPEECH_REQUEST_CODE);
            } catch (Exception e) {
                Toast.makeText(this, "Speech recognition not available",
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
                body.put("deviceName",        "Network Device");
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
                    tvAssistantReply.setText(reply);
                    tvAssistantStatus.setText("Ready — tap Speak");
                    etQuestion.setText("");
                });

            } catch (java.net.ConnectException e) {
                runOnUiThread(() -> {
                    tvAssistantReply.setText("Cannot reach server.");
                    tvAssistantStatus.setText("Offline");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvAssistantReply.setText("Error: " + e.getMessage());
                    tvAssistantStatus.setText("Error");
                });
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // JavaScript bridge
    // -------------------------------------------------------------------------

    private class AvatarBridge {
        @JavascriptInterface
        public void onAvatarLoaded() {
            runOnUiThread(() -> tvAssistantStatus.setText("Ready"));
        }
        @JavascriptInterface
        public void log(String m) { Log.d(TAG, "JS: " + m); }
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

    private byte[] streamToBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }
}