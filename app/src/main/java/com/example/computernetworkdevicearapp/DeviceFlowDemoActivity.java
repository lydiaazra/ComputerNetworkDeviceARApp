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

public class DeviceFlowDemoActivity extends AppCompatActivity
        implements TextToSpeech.OnInitListener {

    private static final String TAG                    = "DeviceFlowDemo";
    private static final int    CAMERA_PERMISSION_CODE = 302;
    private static final int    CHUNK_SIZE             = 100_000;

    private PreviewView    cameraPreview;
    private WebView        modelWebView;
    private WebView        avatarWebView;
    private TextView       tvDeviceTitle, tvStatusIcon, tvStatusTitle, tvStatusSub;
    private MaterialButton btnBack, btnDemoMode;
    private View           statusBar;

    private String glbBase64 = null;

    private String avatarBase64  = null;
    private String threeJs       = null;
    private String gltfLoaderJs  = null;

    private TextToSpeech tts;
    private boolean      ttsReady    = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_flow_ar);

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

    private void bindViews() {
        cameraPreview  = findViewById(R.id.cameraPreview);
        modelWebView   = findViewById(R.id.modelWebView);
        avatarWebView  = findViewById(R.id.avatarWebView);
        tvDeviceTitle  = findViewById(R.id.tvDeviceTitle);
        tvStatusIcon   = findViewById(R.id.tvStatusIcon);
        tvStatusTitle  = findViewById(R.id.tvStatusTitle);
        tvStatusSub    = findViewById(R.id.tvStatusSub);
        btnBack        = findViewById(R.id.btnBack);
        btnDemoMode    = findViewById(R.id.btnDemoMode);
        statusBar      = findViewById(R.id.statusBar);
    }

    private void setupNavBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(statusBar, (v, insets) -> {
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            ViewGroup.MarginLayoutParams params =
                    (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    Math.max(navBottom, dp(24)) + dp(20));
            return insets;
        });
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
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
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview);
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupModelWebView() {
        modelWebView.setBackgroundColor(Color.TRANSPARENT);
        modelWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings s = modelWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        modelWebView.addJavascriptInterface(new FlowBridge(), "AndroidBridge");
        modelWebView.setWebViewClient(new WebViewClient());
        modelWebView.loadUrl("file:///android_asset/assets/device_flow_demo.html");
    }

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

        avatarWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (avatarBase64 != null) {
                    String js = "setTimeout(function(){" +
                            "loadAvatar('data:model/gltf-binary;base64," + avatarBase64 + "');" +
                            "}, 400);";
                    view.evaluateJavascript(js, null);
                }
            }
        });

        avatarWebView.loadDataWithBaseURL("https://cdn.jsdelivr.net/", buildAvatarHtml(),
                "text/html", "UTF-8", null);
    }

    private String readAssetAsBase64(String path) {
        try { return Base64.encodeToString(streamToBytes(getAssets().open(path)), Base64.NO_WRAP); }
        catch (IOException e) { Log.w(TAG, "Not found: " + path); return null; }
    }
    private String readAssetAsString(String path) {
        try { return new String(streamToBytes(getAssets().open(path)), "UTF-8"); }
        catch (IOException e) { Log.w(TAG, "JS not found: " + path); return null; }
    }
    private byte[] streamToBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private void setupTTS() {
        tts = new TextToSpeech(this, this);
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int r = tts.setLanguage(Locale.US);
            tts.setSpeechRate(0.95f);
            ttsReady = !(r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) {
                    runOnUiThread(() -> avatarWebView.evaluateJavascript("stopTalking()", null));
                }
                @Override public void onError(String id) {
                    runOnUiThread(() -> avatarWebView.evaluateJavascript("stopTalking()", null));
                }
            });
        }
    }

    private void setupButtons() {
        btnBack.setOnClickListener(v -> finish());
        btnDemoMode.setOnClickListener(v ->
                modelWebView.evaluateJavascript("onDemoModeToggle();", null));
    }

    private class FlowBridge {
        @JavascriptInterface
        public int getGlbLength() { return glbBase64 != null ? glbBase64.length() : 0; }

        @JavascriptInterface
        public String getGlbChunk(int offset) {
            if (glbBase64 == null || offset >= glbBase64.length()) return "";
            return glbBase64.substring(offset, Math.min(offset + CHUNK_SIZE, glbBase64.length()));
        }

        @JavascriptInterface
        public void requestGlb(String filename) {
            new Thread(() -> {
                try {
                    InputStream is = getAssets().open("models/" + filename);
                    glbBase64 = Base64.encodeToString(streamToBytes(is), Base64.NO_WRAP);
                } catch (Exception e) {
                    Log.e(TAG, "GLB missing for '" + filename + "': " + e.getMessage());
                    glbBase64 = "";
                }
                runOnUiThread(() -> modelWebView.evaluateJavascript("onGlbReady();", null));
            }).start();
        }

        @JavascriptInterface
        public void speakAvatarLine(String text) {
            runOnUiThread(() -> {
                avatarWebView.evaluateJavascript("startTalking()", null);
                if (ttsReady) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "flow_line");
            });
        }

        @JavascriptInterface
        public void setStatus(String icon, String title, String sub) {
            runOnUiThread(() -> {
                tvStatusIcon.setText(icon);
                tvStatusTitle.setText(title);
                tvStatusSub.setText(sub);
            });
        }

        @JavascriptInterface
        public void onSceneReady() {
            Log.d(TAG, "Scene ready — all nodes loaded");
        }

        @JavascriptInterface
        public void log(String m) { Log.d(TAG, "JS: " + m); }
    }

    // Avatar HTML — identical lip-sync/gesture logic reused across the app.
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
                "<style>*{margin:0;padding:0;}html,body{width:100%;height:100%;background:transparent;overflow:hidden;}" +
                "canvas{display:block;background:transparent;}" +
                "#status{position:fixed;bottom:2px;left:0;right:0;text-align:center;color:#7EDCFF;font:9px system-ui;pointer-events:none;}" +
                "</style></head><body><div id='status'>Loading...</div>" +
                threeScript + loaderScript +
                "<script>" +
                "var renderer,scene,camera,clock,mixer=null;" +
                "var avatarModel=null,idleAction=null,talkAction=null;" +
                "var headBone=null,jawBone=null;" +
                "var leftUpperArmBone=null,rightUpperArmBone=null;" +
                "var leftArmBone=null,rightArmBone=null;" +
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
                "  camera.position.set(0,1.2,3.5); camera.lookAt(0,1.0,0);" +
                "  scene.add(new THREE.AmbientLight(0xffffff,1.8));" +
                "  var k=new THREE.DirectionalLight(0xffffff,1.2); k.position.set(1,3,2); scene.add(k);" +
                "  animate(); setStatus('');" +
                "}" +
                "window.loadAvatar=function(url){" +
                "  setStatus('Loading...');" +
                "  new THREE.GLTFLoader().load(url,function(gltf){" +
                "    avatarModel=gltf.scene; scene.add(avatarModel);" +
                "    avatarModel.rotation.y=0.3; avatarModel.position.x=-0.2;" +
                "    avatarModel.traverse(function(node){" +
                "      var n=(node.name||'').toLowerCase();" +
                "      if(!headBone&&n.includes('head'))headBone=node;" +
                "      if(!jawBone&&n.includes('jaw'))jawBone=node;" +
                "      if(!leftArmBone&&(n.includes('leftforearm')||n.includes('forearm_l')))leftArmBone=node;" +
                "      if(!rightArmBone&&(n.includes('rightforearm')||n.includes('forearm_r')))rightArmBone=node;" +
                "      if(!leftUpperArmBone&&n==='leftarm')leftUpperArmBone=node;" +
                "      if(!rightUpperArmBone&&n==='rightarm')rightUpperArmBone=node;" +
                "      if(node.isMesh&&node.morphTargetDictionary){" +
                "        var dict=node.morphTargetDictionary;" +
                "        var primaryNames=['jawOpen','mouthOpen'];" +
                "        var visemeNames=['viseme_aa','viseme_E','viseme_I','viseme_O','viseme_U','viseme_PP','viseme_FF','viseme_TH','viseme_DD','viseme_kk','viseme_CH','viseme_SS','viseme_nn','viseme_RR'];" +
                "        var foundPrimary=primaryNames.filter(function(k){return dict.hasOwnProperty(k);});" +
                "        var foundViseme=visemeNames.filter(function(k){return dict.hasOwnProperty(k);});" +
                "        if((foundPrimary.length+foundViseme.length)>0){" +
                "          var mats=Array.isArray(node.material)?node.material:[node.material];" +
                "          mats.forEach(function(m){if(m){m.morphTargets=true;m.morphNormals=true;m.needsUpdate=true;}});" +
                "          morphTargets.push({mesh:node,primary:foundPrimary.map(function(k){return dict[k];}),viseme:foundViseme.map(function(k){return dict[k];})});" +
                "        }" +
                "      }" +
                "    });" +
                "    var anims=gltf.animations||[];" +
                "    if(anims.length){" +
                "      mixer=new THREE.AnimationMixer(avatarModel);" +
                "      var idleClip=anims.find(function(a){return a.name.toLowerCase().includes('idle');})||anims[0];" +
                "      idleAction=mixer.clipAction(idleClip); idleAction.play();" +
                "      var talkClip=anims.find(function(a){var nm=a.name.toLowerCase();return nm.includes('talk')||nm.includes('speak');});" +
                "      if(talkClip){talkAction=mixer.clipAction(talkClip);talkAction.setLoop(THREE.LoopRepeat,Infinity);}" +
                "    }" +
                "    setStatus('');" +
                "  },function(x){if(x.total)setStatus((x.loaded/x.total*100|0)+'%');},function(e){setStatus('Error');});" +
                "};" +
                "window.startTalking=function(){isTalking=true;talkPhase=0;if(talkAction){idleAction&&idleAction.fadeOut(0.2);talkAction.reset().fadeIn(0.2).play();}};" +
                "function applyTalkPose(dt){" +
                "  talkPhase+=dt; var t=talkPhase*3.0; var target=isTalking?1:0;" +
                "  talkWeight+=(target-talkWeight)*Math.min(1,dt*6);" +
                "  if(morphTargets.length>0){" +
                "    var mainOpen=Math.max(0,0.45*Math.abs(Math.sin(t*1.4))+0.08*Math.random())*talkWeight;" +
                "    morphTargets.forEach(function(mt){" +
                "      mt.primary.forEach(function(idx){mt.mesh.morphTargetInfluences[idx]=mainOpen;});" +
                "      mt.viseme.forEach(function(idx,i){mt.mesh.morphTargetInfluences[idx]=Math.max(0,0.35*Math.abs(Math.sin(t*0.9+i*0.8))*mainOpen);});" +
                "    });" +
                "  }" +
                "  if(jawBone){jawBone.rotation.x+=0.35*Math.abs(Math.sin(t*1.4))*talkWeight;}" +
                "  if(headBone){headBone.rotation.x+=0.05*Math.sin(t*0.4)*talkWeight;headBone.rotation.y+=0.04*Math.sin(t*0.27)*talkWeight;}" +
                "  var lift=Math.max(0,Math.sin(t*1.6))*talkWeight;" +
                "  if(leftUpperArmBone){leftUpperArmBone.rotation.z+=0.85*lift;leftUpperArmBone.rotation.x+=0.1*lift;}" +
                "  if(leftArmBone){leftArmBone.rotation.z+=0.4*lift;}" +
                "}" +
                "window.stopTalking=function(){isTalking=false;if(talkAction){talkAction.fadeOut(0.3);if(idleAction){idleAction.reset().fadeIn(0.3).play();}}};" +
                "function animate(){requestAnimationFrame(animate);var dt=clock?clock.getDelta():0;if(mixer)mixer.update(dt);if(isTalking||talkWeight>0.001)applyTalkPose(dt);if(renderer&&scene&&camera)renderer.render(scene,camera);}" +
                "function setStatus(m){document.getElementById('status').textContent=m;}" +
                "window.addEventListener('load',function(){var t=0;(function wait(){if(typeof THREE!=='undefined'&&THREE.GLTFLoader){initScene();}else if(t++<30){setTimeout(wait,200);}else{setStatus('Failed');}})();});" +
                "window.addEventListener('resize',function(){if(!renderer)return;camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();renderer.setSize(innerWidth,innerHeight);});" +
                "</script></body></html>";
    }
}