package com.example.computernetworkdevicearapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class ARQuizActivity extends AppCompatActivity {

    private static final String TAG        = "ARQuizActivity";
    private static final int    CHUNK_SIZE = 100_000;

    private PreviewView cameraPreview;
    private WebView     modelWebView;
    private TextView    tvQuestion, tvQuestionNumber, tvScore, tvDeviceName;
    private Button      btnOptionA, btnOptionB, btnOptionC, btnOptionD;

    private FirebaseFirestore db;
    private FirebaseAuth      mAuth;

    private final List<Map<String, Object>> questions = new ArrayList<>();
    private int    currentQuestionIndex = 0;
    private int    score                = 0;
    private String level;
    private String currentGlbDevice    = ""; // track which GLB is loaded
    private String glbBase64           = null;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_quiz);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        level = getIntent().getStringExtra("level");

        if (level == null || level.trim().isEmpty()) {
            Toast.makeText(this, "Quiz level not found", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        bindViews();
        setupModelWebView();
        startCamera();
        loadQuestions();
    }

    @Override
    protected void onDestroy() {
        if (modelWebView != null) modelWebView.destroy();
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // Bind views
    // -------------------------------------------------------------------------

    private void bindViews() {
        cameraPreview    = findViewById(R.id.cameraPreview);
        modelWebView     = findViewById(R.id.modelWebView);
        tvQuestion       = findViewById(R.id.tvQuestion);
        tvQuestionNumber = findViewById(R.id.tvQuestionNumber);
        tvScore          = findViewById(R.id.tvScore);
        tvDeviceName     = findViewById(R.id.tvDeviceName);
        btnOptionA       = findViewById(R.id.btnOptionA);
        btnOptionB       = findViewById(R.id.btnOptionB);
        btnOptionC       = findViewById(R.id.btnOptionC);
        btnOptionD       = findViewById(R.id.btnOptionD);

        btnOptionA.setOnClickListener(v -> checkAnswer("A"));
        btnOptionB.setOnClickListener(v -> checkAnswer("B"));
        btnOptionC.setOnClickListener(v -> checkAnswer("C"));
        btnOptionD.setOnClickListener(v -> checkAnswer("D"));
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
    // WebView — Three.js device model
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
                // Will be triggered to load model after questions load
            }
        });

        modelWebView.loadUrl("file:///android_asset/assets/ar_model.html");
    }

    // -------------------------------------------------------------------------
    // Load GLB for current device in background
    // -------------------------------------------------------------------------

    private void loadDeviceModel(String deviceName) {
        if (deviceName == null) return;
        // Skip if same device already loaded
        if (deviceName.equalsIgnoreCase(currentGlbDevice)) {
            injectGlbIntoWebView();
            return;
        }

        String fileName = glbFileForDevice(deviceName);
        currentGlbDevice = deviceName;
        glbBase64 = null;

        new Thread(() -> {
            try {
                InputStream is  = getAssets().open("models/" + fileName);
                byte[]      buf = streamToBytes(is);
                glbBase64 = Base64.encodeToString(buf, Base64.NO_WRAP);
                Log.d(TAG, "✅ Quiz GLB loaded: " + fileName);
                runOnUiThread(this::injectGlbIntoWebView);
            } catch (OutOfMemoryError | IOException e) {
                Log.w(TAG, "GLB not found or OOM: " + fileName);
                glbBase64 = null;
                runOnUiThread(() ->
                        modelWebView.evaluateJavascript(
                                "showFallback('" + deviceName + "')", null));
            }
        }).start();
    }

    private void injectGlbIntoWebView() {
        if (glbBase64 != null) {
            // Use polling approach to wait for bridge to be ready
            new Thread(() -> {
                int waited = 0;
                while (waited < 50) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    waited++;
                }
                runOnUiThread(() ->
                        modelWebView.evaluateJavascript(
                                "setTimeout(function(){loadGlbFromBridge();},300);", null));
            }).start();
        } else {
            modelWebView.evaluateJavascript(
                    "showFallback('" + currentGlbDevice + "')", null);
        }
    }

    private String glbFileForDevice(String name) {
        String n = name.toLowerCase().trim();
        if (n.contains("switch"))   return "switch.glb";
        if (n.contains("hub"))      return "hub.glb";
        if (n.contains("firewall")) return "firewall.glb";
        if (n.contains("access") || n.contains("wap") || n.equals("ap")) return "wap.glb";
        if (n.contains("nic") || n.contains("network interface"))         return "nic.glb";
        if (n.contains("repeat"))   return "repeater.glb";
        if (n.contains("gateway"))  return "gateway.glb";
        return "router.glb";
    }

    private byte[] streamToBytes(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
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
        public void onModelLoaded(String s) { Log.d(TAG, "Model loaded: " + s); }

        @JavascriptInterface
        public void onModelError(String m)  { Log.w(TAG, "Model error: " + m); }

        @JavascriptInterface
        public void log(String m)           { Log.d(TAG, "JS: " + m); }
    }

    // -------------------------------------------------------------------------
    // Load questions from Firestore
    // -------------------------------------------------------------------------

    private void loadQuestions() {
        db.collection("questions")
                .whereEqualTo("level", level.toLowerCase())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    questions.clear();
                    for (QueryDocumentSnapshot doc : querySnapshot)
                        questions.add(doc.getData());

                    if (!questions.isEmpty()) {
                        showQuestion();
                    } else {
                        Toast.makeText(this,
                                "No questions found for level: " + level,
                                Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Firestore Error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    // -------------------------------------------------------------------------
    // Show question
    // -------------------------------------------------------------------------

    private void showQuestion() {
        if (currentQuestionIndex >= questions.size()) {
            finishQuiz();
            return;
        }

        Map<String, Object> question = questions.get(currentQuestionIndex);
        String deviceName = (String) question.get("device");

        tvQuestionNumber.setText("Question " + (currentQuestionIndex + 1) + "/" + questions.size());
        tvScore.setText("Score: " + score);
        tvQuestion.setText(String.valueOf(question.get("question_text")));

        btnOptionA.setText("A. " + question.get("option_a"));
        btnOptionB.setText("B. " + question.get("option_b"));
        btnOptionC.setText("C. " + question.get("option_c"));
        btnOptionD.setText("D. " + question.get("option_d"));

        updateDeviceLabel(deviceName);
        resetButtonColors();
        setButtonsEnabled(true);

        // ✅ Load and display the AR model for this device
        loadDeviceModel(deviceName);
    }

    private void updateDeviceLabel(String deviceName) {
        if (deviceName == null) { tvDeviceName.setText("📡 Network Device"); return; }
        Map<String, String> icons = new HashMap<>();
        icons.put("router",   "🔀 Router");
        icons.put("switch",   "🔌 Switch");
        icons.put("hub",      "📡 Hub");
        icons.put("firewall", "🛡️ Firewall");
        icons.put("nic",      "💾 NIC");
        icons.put("repeater", "📶 Repeater");
        icons.put("gateway",  "🌐 Gateway");
        String label = icons.get(deviceName.toLowerCase());
        tvDeviceName.setText(label != null ? label : "📡 " + deviceName);
    }

    // -------------------------------------------------------------------------
    // Answer handling
    // -------------------------------------------------------------------------

    private void checkAnswer(String selected) {
        if (currentQuestionIndex >= questions.size()) return;
        Map<String, Object> question = questions.get(currentQuestionIndex);
        String correct = (String) question.get("correct_answer");

        setButtonsEnabled(false);

        if (selected.equals(correct)) {
            score++;
            highlightButton(selected, true);
        } else {
            highlightButton(selected, false);
            if (correct != null) highlightButton(correct, true);
        }

        tvScore.setText("Score: " + score);

        new Handler().postDelayed(() -> {
            currentQuestionIndex++;
            showQuestion();
        }, 1500);
    }

    private void highlightButton(String option, boolean correct) {
        int color = correct ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336");
        switch (option) {
            case "A": btnOptionA.setBackgroundColor(color); break;
            case "B": btnOptionB.setBackgroundColor(color); break;
            case "C": btnOptionC.setBackgroundColor(color); break;
            case "D": btnOptionD.setBackgroundColor(color); break;
        }
    }

    private void resetButtonColors() {
        int c = Color.parseColor("#1E2A4A");
        btnOptionA.setBackgroundColor(c);
        btnOptionB.setBackgroundColor(c);
        btnOptionC.setBackgroundColor(c);
        btnOptionD.setBackgroundColor(c);
    }

    private void setButtonsEnabled(boolean e) {
        btnOptionA.setEnabled(e);
        btnOptionB.setEnabled(e);
        btnOptionC.setEnabled(e);
        btnOptionD.setEnabled(e);
    }

    // -------------------------------------------------------------------------
    // Finish quiz
    // -------------------------------------------------------------------------

    private void finishQuiz() {
        if (mAuth.getCurrentUser() == null) { finish(); return; }
        String userId = mAuth.getCurrentUser().getUid();

        Map<String, Object> updates = new HashMap<>();
        updates.put(level + "Score",     score);
        updates.put(level + "Completed", true);
        updates.put(level + "BestScore", score);
        updates.put(level + "Progress",  questions.size());

        db.collection("users").document(userId).update(updates)
                .addOnSuccessListener(v -> {
                    Intent intent = new Intent(this, ResultActivity.class);
                    intent.putExtra("score", score);
                    intent.putExtra("total", questions.size());
                    intent.putExtra("level", level);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to save: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }
}