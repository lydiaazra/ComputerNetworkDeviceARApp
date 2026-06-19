package com.example.computernetworkdevicearapp;

import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class DeviceDisplay extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String TAG         = "DeviceDisplay";
    private static final String BACKEND_URL = "http://172.20.10.7:3000/inworld-chat";

    // AR
    private ArFragment arFragment;
    private AnchorNode anchorNode;
    private boolean    devicePlaced = false;

    // UI
    private TextView     tvDeviceTitle, tvExplanation, tvAssistantReply, tvAssistantStatus;
    private EditText     etAssistantQuestion;
    private MaterialButton btnPlaceDevice, btnAskAssistant, btnSpeakReply, btnBack;

    // Device data
    private String deviceName;
    private String modelPath;
    private String explanation;

    // TTS
    private TextToSpeech textToSpeech;
    private boolean      isTtsReady = false;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_display);

        bindViews();
        setupArFragment();   // ← session config lives here
        setupDeviceData();
        setupButtons();

        textToSpeech = new TextToSpeech(this, this);
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    // -------------------------------------------------------------------------
    // View binding
    // -------------------------------------------------------------------------

    private void bindViews() {
        tvDeviceTitle       = findViewById(R.id.tvDeviceTitle);
        tvExplanation       = findViewById(R.id.tvExplanation);
        tvAssistantReply    = findViewById(R.id.tvAssistantReply);
        tvAssistantStatus   = findViewById(R.id.tvAssistantStatus);
        etAssistantQuestion = findViewById(R.id.etAssistantQuestion);
        btnPlaceDevice      = findViewById(R.id.btnPlaceDevice);
        btnAskAssistant     = findViewById(R.id.btnAskAssistant);
        btnSpeakReply       = findViewById(R.id.btnSpeakReply);
        btnBack             = findViewById(R.id.btnBack);
    }

    // -------------------------------------------------------------------------
    // AR Fragment setup — includes session config fix for Samsung Android 16
    // -------------------------------------------------------------------------

    private void setupArFragment() {
        arFragment = (ArFragment) getSupportFragmentManager()
                .findFragmentById(R.id.arFragment);

        if (arFragment == null) {
            Toast.makeText(this, "AR Fragment not found in layout",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // ✅ FIX: setOnSessionConfigurationListener fires just before ARCore
        //         creates its session — the safest place to apply Config changes.
        //         Disabling depth + using AMBIENT_INTENSITY prevents the black
        //         screen / FatalException on Samsung Galaxy devices with Android 16.
        arFragment.setOnSessionConfigurationListener((session, config) -> {
            config.setDepthMode(Config.DepthMode.DISABLED);
            config.setLightEstimationMode(Config.LightEstimationMode.AMBIENT_INTENSITY);
            config.setFocusMode(Config.FocusMode.AUTO);
            config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
        });

        // Tap on a detected plane → place the device model there
        arFragment.setOnTapArPlaneListener((hitResult, plane, motionEvent) -> {
            if (devicePlaced) return;
            placeDeviceModel(hitResult.createAnchor());
        });
    }

    // -------------------------------------------------------------------------
    // Device data
    // -------------------------------------------------------------------------

    private void setupDeviceData() {
        deviceName = getIntent().getStringExtra("deviceName");
        if (deviceName == null || deviceName.trim().isEmpty()) deviceName = "Router";

        switch (deviceName.toLowerCase()) {
            case "switch":
                deviceName  = "Switch";
                modelPath   = "models/switch.glb";
                explanation = "A switch connects devices in a LAN using MAC addresses. "
                        + "It operates at Layer 2 of the OSI model.";
                break;
            case "hub":
                deviceName  = "Hub";
                modelPath   = "models/hub.glb";
                explanation = "A hub broadcasts data to all connected devices. "
                        + "It operates at Layer 1 of the OSI model.";
                break;
            case "firewall":
                deviceName  = "Firewall";
                modelPath   = "models/firewall.glb";
                explanation = "A firewall monitors and filters network traffic "
                        + "based on security rules.";
                break;
            default:
                deviceName  = "Router";
                modelPath   = "models/router.glb";
                explanation = "A router connects multiple networks and forwards "
                        + "packets using IP addresses. It operates at Layer 3 of the OSI model.";
                break;
        }

        tvDeviceTitle.setText("📡 " + deviceName);
        tvExplanation.setText(explanation);
        tvAssistantReply.setText("Hello! I can explain the "
                + deviceName + " and answer your questions.");
        tvAssistantStatus.setText("Ready");
    }

    // -------------------------------------------------------------------------
    // Place 3D device model at the tapped AR anchor
    // -------------------------------------------------------------------------

    private void placeDeviceModel(Anchor anchor) {
        ModelRenderable.builder()
                .setSource(this, Uri.parse(modelPath))
                .setIsFilamentGltf(true)
                .setAsyncLoadEnabled(true)
                .build()
                .thenAccept(renderable -> {
                    addToScene(anchor, renderable);
                    devicePlaced = true;
                    runOnUiThread(() ->
                            Toast.makeText(this,
                                    deviceName + " placed!", Toast.LENGTH_SHORT).show());
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Model load failed", throwable);
                    runOnUiThread(() ->
                            Toast.makeText(this,
                                    "Error loading model: " + throwable.getMessage(),
                                    Toast.LENGTH_LONG).show());
                    return null;
                });
    }

    private void addToScene(Anchor anchor, ModelRenderable renderable) {
        ArSceneView sceneView = arFragment.getArSceneView();

        if (anchorNode != null) anchorNode.setParent(null);

        anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(sceneView.getScene());

        TransformableNode node = new TransformableNode(
                arFragment.getTransformationSystem());
        node.setParent(anchorNode);
        node.setRenderable(renderable);
        node.select();
    }

    // -------------------------------------------------------------------------
    // Buttons
    // -------------------------------------------------------------------------

    private void setupButtons() {

        btnPlaceDevice.setOnClickListener(v -> {
            if (!devicePlaced) {
                Toast.makeText(this,
                        "Tap a flat surface to place the " + deviceName,
                        Toast.LENGTH_SHORT).show();
            } else {
                // Allow repositioning
                devicePlaced = false;
                Toast.makeText(this,
                        "Tap a surface to reposition the " + deviceName,
                        Toast.LENGTH_SHORT).show();
            }
        });

        btnAskAssistant.setOnClickListener(v -> {
            String question = etAssistantQuestion.getText().toString().trim();
            if (question.isEmpty()) {
                Toast.makeText(this, "Please enter a question",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            tvAssistantStatus.setText("Thinking...");
            tvAssistantReply.setText("Please wait...");
            askAssistant(question);
        });

        btnSpeakReply.setOnClickListener(v -> {
            String reply = tvAssistantReply.getText().toString().trim();
            if (reply.isEmpty()) {
                Toast.makeText(this, "No reply to speak yet",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isTtsReady) {
                Toast.makeText(this, "Text-to-Speech not ready",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            tvAssistantStatus.setText("Speaking...");
            textToSpeech.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "reply");
        });

        btnBack.setOnClickListener(v -> finish());
    }

    // -------------------------------------------------------------------------
    // AI assistant — HTTP POST to Node.js backend
    // -------------------------------------------------------------------------

    private void askAssistant(String question) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(BACKEND_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(20_000);
                connection.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("deviceName",        deviceName);
                body.put("deviceExplanation", explanation);
                body.put("question",          question);

                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(connection.getOutputStream()));
                writer.write(body.toString());
                writer.flush();
                writer.close();

                int code = connection.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 300
                                ? connection.getInputStream()
                                : connection.getErrorStream()));

                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String reply = new JSONObject(sb.toString())
                        .optString("reply", "Sorry, no response.");

                runOnUiThread(() -> {
                    tvAssistantReply.setText(reply);
                    tvAssistantStatus.setText("Ready");
                    etAssistantQuestion.setText("");
                });

            } catch (java.net.ConnectException e) {
                runOnUiThread(() -> {
                    tvAssistantReply.setText("Cannot reach backend server.");
                    tvAssistantStatus.setText("Offline");
                });
            } catch (java.net.SocketTimeoutException e) {
                runOnUiThread(() -> {
                    tvAssistantReply.setText("Server took too long to respond.");
                    tvAssistantStatus.setText("Timeout");
                });
            } catch (Exception e) {
                Log.e(TAG, "Assistant error: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    tvAssistantReply.setText("Error: " + e.getMessage());
                    tvAssistantStatus.setText("Error");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // TextToSpeech callback
    // -------------------------------------------------------------------------

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.US);
            textToSpeech.setSpeechRate(0.95f);
            isTtsReady = !(result == TextToSpeech.LANG_MISSING_DATA
                    || result == TextToSpeech.LANG_NOT_SUPPORTED);
        } else {
            isTtsReady = false;
            Log.e(TAG, "TTS init failed");
        }
    }
}