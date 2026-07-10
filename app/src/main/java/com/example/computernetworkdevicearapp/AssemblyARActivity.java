package com.example.computernetworkdevicearapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AssemblyARActivity extends AppCompatActivity
        implements TextToSpeech.OnInitListener {

    private static final String TAG = "AssemblyAR";
    private static final int CAMERA_PERMISSION_CODE = 802;
    private static final int CHUNK_SIZE = 100_000;

    private PreviewView cameraPreview;
    private WebView     modelWebView;
    private WebView     avatarWebView;

    // Which topology to load — passed from TopologySelectActivity, defaults to "home".
    private String topologyId = "home";

    // Only ONE device model's data lives in memory at a time — the current step's.
    private String  glbBase64 = null;

    // Avatar assets — same avatar.glb reused from CombinedARActivity, loaded once.
    private String avatarBase64  = null;
    private String threeJs       = null;
    private String gltfLoaderJs  = null;

    // TTS drives both the spoken line and the avatar's lip-sync/gesture animation.
    // No Inworld here — every line is scripted in the JS STEPS array, so an
    // on-device, offline TTS call is all that's needed, with no network dependency.
    private TextToSpeech tts;
    private boolean      ttsReady = false;

    // QR anchor — the whole device diorama is placed at wherever a QR code is
    // detected in the camera feed, using ML Kit (no ARCore needed). Content of the
    // QR code doesn't matter, only its detected position/size.
    // Currently disabled — flip to true to re-enable once there's time to revisit.
    private static final boolean ENABLE_QR_TRACKING = false;
    private final ExecutorService qrExecutor = Executors.newSingleThreadExecutor();
    private long lastQrAnalysisMs = 0;
    private static final long QR_ANALYSIS_INTERVAL_MS = 100; // ~10fps, enough for anchor tracking
    private com.google.mlkit.vision.barcode.BarcodeScanner barcodeScanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assembly_ar);

        cameraPreview = findViewById(R.id.cameraPreview);
        modelWebView  = findViewById(R.id.modelWebView);
        avatarWebView = findViewById(R.id.avatarWebView);

        String extra = getIntent().getStringExtra("topologyId");
        if (extra != null && !extra.isEmpty()) topologyId = extra;

        avatarBase64 = readAssetAsBase64("models/avatar.glb");
        threeJs      = readAssetAsString("assets/three.min.js");
        gltfLoaderJs = readAssetAsString("assets/GLTFLoader.js");

        setupWebView();
        setupAvatarWebView();
        setupTTS();

        if (ENABLE_QR_TRACKING) {
            BarcodeScannerOptions qrOptions = new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build();
            barcodeScanner = BarcodeScanning.getClient(qrOptions);
        }

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
                if (ENABLE_QR_TRACKING) {
                    ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build();
                    imageAnalysis.setAnalyzer(qrExecutor, this::analyzeFrameForQr);
                    provider.bindToLifecycle(
                            this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
                } else {
                    provider.bindToLifecycle(
                            this, CameraSelector.DEFAULT_BACK_CAMERA, preview);
                }
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Camera error: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // ── QR anchor detection ───────────────────────────────────────────────────────
    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeFrameForQr(ImageProxy imageProxy) {
        long now = System.currentTimeMillis();
        if (now - lastQrAnalysisMs < QR_ANALYSIS_INTERVAL_MS) {
            imageProxy.close();
            return;
        }
        lastQrAnalysisMs = now;

        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) { imageProxy.close(); return; }

        InputImage inputImage = InputImage.fromMediaImage(
                mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        barcodeScanner.process(inputImage)
                .addOnSuccessListener(barcodes -> onQrResult(barcodes, inputImage.getWidth(), inputImage.getHeight()))
                .addOnFailureListener(e -> Log.w(TAG, "QR scan failed: " + e.getMessage()))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void onQrResult(List<Barcode> barcodes, int imgW, int imgH) {
        if (barcodes.isEmpty() || barcodes.get(0).getBoundingBox() == null) {
            runOnUiThread(() -> modelWebView.evaluateJavascript(
                    "if(window.onQrUpdate)onQrUpdate(false,0,0,0);", null));
            return;
        }
        Rect box = barcodes.get(0).getBoundingBox();
        float nx = box.centerX() / (float) imgW;
        float ny = box.centerY() / (float) imgH;
        float sizeNorm = Math.max(box.width() / (float) imgW, box.height() / (float) imgH);
        runOnUiThread(() -> modelWebView.evaluateJavascript(
                "if(window.onQrUpdate)onQrUpdate(true," + nx + "," + ny + "," + sizeNorm + ");", null));
    }

    // ── Device model WebView (Three.js scene, one step's GLB at a time) ──────────
    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        modelWebView.setBackgroundColor(Color.TRANSPARENT);
        modelWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        WebSettings s = modelWebView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);

        modelWebView.addJavascriptInterface(new AssemblyBridge(), "AndroidBridge");
        modelWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (!url.startsWith("file:")) return;
                view.evaluateJavascript(
                        "setTimeout(function(){ if(window.initTopology) initTopology('" + topologyId + "'); }, 200);",
                        null);
            }
        });

        modelWebView.loadUrl("file:///android_asset/assets/assembly_ar_demo.html");
    }

    // ── Avatar WebView — identical pattern to CombinedARActivity ─────────────────
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

    // ── TTS ────────────────────────────────────────────────────────────────────
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
    }

    private void doSpeakAvatarLine(String text) {
        if (!ttsReady || text == null || text.isEmpty()) return;
        // Start the lip-sync/gesture animation immediately — same as CombinedARActivity,
        // since waiting for TTS onStart can fire late on short lines.
        avatarWebView.evaluateJavascript("startTalking()", null);
        Bundle params = new Bundle();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "assembly_avatar_speech");
    }

    private byte[] streamToBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
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

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (modelWebView != null) modelWebView.destroy();
        if (avatarWebView != null) avatarWebView.destroy();
        if (barcodeScanner != null) barcodeScanner.close();
        qrExecutor.shutdown();
        super.onDestroy();
    }

    // ── JS Bridge — device/step WebView ───────────────────────────────────────────
    private class AssemblyBridge {

        @JavascriptInterface
        public void requestGlb(String filename) {
            new Thread(() -> {
                try {
                    InputStream is = getAssets().open("models/" + filename);
                    glbBase64 = Base64.encodeToString(streamToBytes(is), Base64.NO_WRAP);
                } catch (Exception e) {
                    Log.e(TAG, "GLB missing for '" + filename + "' — check assets/models/" + filename + " : " + e.getMessage());
                    glbBase64 = "";
                }
                runOnUiThread(() -> modelWebView.evaluateJavascript("onGlbReady();", null));
            }).start();
        }

        @JavascriptInterface
        public int getGlbLength() {
            return glbBase64 != null ? glbBase64.length() : 0;
        }

        @JavascriptInterface
        public String getGlbChunk(int offset) {
            if (glbBase64 == null || offset >= glbBase64.length()) return "";
            return glbBase64.substring(offset, Math.min(offset + CHUNK_SIZE, glbBase64.length()));
        }

        // Called by assembly_ar_demo.html whenever a new avatar line should be spoken —
        // step intro, connection confirmation, or the final completion line.
        @JavascriptInterface
        public void speakAvatarLine(String text) {
            runOnUiThread(() -> doSpeakAvatarLine(text));
        }

        @JavascriptInterface
        public void onAssemblyComplete() { Log.d(TAG, "Assembly complete — all devices connected"); }

        @JavascriptInterface
        public void setAvatarVisible(boolean visible) {
            runOnUiThread(() -> avatarWebView.setVisibility(visible ? View.VISIBLE : View.GONE));
        }

        @JavascriptInterface
        public void goBack() { runOnUiThread(AssemblyARActivity.this::finish); }

        @JavascriptInterface
        public void log(String msg) { Log.d(TAG, "JS: " + msg); }
    }

    // ── Avatar HTML — identical scene/lip-sync logic to CombinedARActivity ───────
    private class AvatarBridge {
        @JavascriptInterface
        public void onAvatarLoaded() { Log.d(TAG, "Avatar ready"); }
        @JavascriptInterface
        public void log(String m) { Log.d(TAG, "Avatar JS: " + m); }
    }

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