package com.example.computernetworkdevicearapp;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import dev.romainguy.kotlin.math.Float3;
import io.github.sceneview.SceneView;
import io.github.sceneview.ar.ArSceneView;
import io.github.sceneview.ar.node.ArModelNode;
import io.github.sceneview.ar.node.PlacementMode;
import io.github.sceneview.node.ModelNode;

public class DeviceDisplay extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private ArSceneView arSceneView;
    private ArModelNode modelNode;

    private SceneView avatarSceneView;
    private ModelNode avatarNode;

    private TextView tvDeviceTitle, tvExplanation, tvAssistantReply, tvAssistantStatus;
    private EditText etAssistantQuestion;
    private Button btnPlaceDevice, btnBack, btnAskAssistant, btnSpeakReply;

    private String deviceName;
    private String modelPath;
    private String explanation;

    private TextToSpeech textToSpeech;
    private boolean isTtsReady = false;

    private static final String BACKEND_URL = "https://inworld-backend.onrender.com/inworld-chat";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_display);

        arSceneView = findViewById(R.id.arSceneView);
        avatarSceneView = findViewById(R.id.avatarSceneView);

        tvDeviceTitle = findViewById(R.id.tvDeviceTitle);
        tvExplanation = findViewById(R.id.tvExplanation);
        tvAssistantReply = findViewById(R.id.tvAssistantReply);
        tvAssistantStatus = findViewById(R.id.tvAssistantStatus);
        etAssistantQuestion = findViewById(R.id.etAssistantQuestion);

        btnPlaceDevice = findViewById(R.id.btnPlaceDevice);
        btnBack = findViewById(R.id.btnBack);
        btnAskAssistant = findViewById(R.id.btnAskAssistant);
        btnSpeakReply = findViewById(R.id.btnSpeakReply);

        textToSpeech = new TextToSpeech(this, this);

        deviceName = getIntent().getStringExtra("deviceName");
        if (deviceName == null || deviceName.trim().isEmpty()) {
            deviceName = "Router";
        }

        setupDeviceData(deviceName);
        setupARModel();
        setupAvatarModel();

        btnPlaceDevice.setOnClickListener(v -> {
            if (modelNode != null) {
                modelNode.anchor();
                Toast.makeText(this, deviceName + " placed!", Toast.LENGTH_SHORT).show();
            }
        });

        btnAskAssistant.setOnClickListener(v -> {
            String userQuestion = etAssistantQuestion.getText().toString().trim();

            if (userQuestion.isEmpty()) {
                Toast.makeText(this, "Please enter a question", Toast.LENGTH_SHORT).show();
                return;
            }

            askInworldAssistant(userQuestion);
        });

        btnSpeakReply.setOnClickListener(v -> {
            String reply = tvAssistantReply.getText().toString().trim();

            if (reply.isEmpty()) {
                Toast.makeText(this, "No reply to speak yet", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isTtsReady) {
                Toast.makeText(this, "Text-to-Speech is not ready", Toast.LENGTH_SHORT).show();
                return;
            }

            tvAssistantStatus.setText("Speaking...");
            animateAvatarTalking();
            textToSpeech.speak(reply, TextToSpeech.QUEUE_FLUSH, null, "assistant_reply");
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void setupDeviceData(String device) {
        switch (device.toLowerCase()) {
            case "router":
                deviceName = "Router";
                modelPath = "models/router.glb";
                explanation = "A router is a networking device that connects multiple networks and forwards data packets between them. It usually operates at Layer 3 of the OSI model.";
                break;

            case "switch":
                deviceName = "Switch";
                modelPath = "models/switch.glb";
                explanation = "A switch connects multiple devices within a local area network and forwards data using MAC addresses. It usually operates at Layer 2 of the OSI model.";
                break;

            case "hub":
                deviceName = "Hub";
                modelPath = "models/hub.glb";
                explanation = "A hub is a simple networking device that broadcasts incoming data to all connected devices. It operates at Layer 1 of the OSI model.";
                break;

            case "access point":
                deviceName = "Access Point";
                modelPath = "models/accesspoint.glb";
                explanation = "An access point provides wireless connectivity and allows Wi-Fi devices to connect to a wired network.";
                break;

            case "modem":
                deviceName = "Modem";
                modelPath = "models/modem.glb";
                explanation = "A modem converts digital and analog signals so that a network can connect to an internet service provider.";
                break;

            default:
                deviceName = "Router";
                modelPath = "models/router.glb";
                explanation = "A router is a networking device that connects multiple networks and forwards data packets between them.";
                break;
        }

        tvDeviceTitle.setText("📡 " + deviceName);
        tvExplanation.setText(explanation);
        tvAssistantReply.setText("Hello, I can explain the " + deviceName + " and answer your questions.");
        tvAssistantStatus.setText("Ready");
    }

    private void setupARModel() {
        modelNode = new ArModelNode(
                PlacementMode.PLANE_HORIZONTAL,
                new Float3(0f, 0f, 0f),
                true,
                false
        );

        modelNode.loadModelGlbAsync(
                modelPath,
                true,
                0.5f,
                null,
                null,
                null
        );

        arSceneView.addChild(modelNode);
    }

    private void setupAvatarModel() {
        avatarNode = new ModelNode();

        avatarNode.loadModelGlbAsync(
                "models/avatar.glb",
                true,
                0.75f,
                null,
                null,
                null
        );

        avatarSceneView.addChild(avatarNode);
    }

    private void animateAvatarTalking() {
        avatarSceneView.animate()
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(180)
                .withEndAction(() ->
                        avatarSceneView.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(180)
                );
    }

    private void askInworldAssistant(String userQuestion) {
        tvAssistantStatus.setText("Thinking...");
        tvAssistantReply.setText("Please wait...");

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                URL url = new URL(BACKEND_URL);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setDoOutput(true);

                JSONObject body = new JSONObject();
                body.put("deviceName", deviceName);
                body.put("deviceExplanation", explanation);
                body.put("question", userQuestion);

                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(connection.getOutputStream())
                );
                writer.write(body.toString());
                writer.flush();
                writer.close();

                int responseCode = connection.getResponseCode();

                BufferedReader reader;
                if (responseCode >= 200 && responseCode < 300) {
                    reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream())
                    );
                } else {
                    reader = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream())
                    );
                }

                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                reader.close();

                JSONObject responseJson = new JSONObject(result.toString());
                String reply = responseJson.optString(
                        "reply",
                        "Sorry, I could not get a response."
                );

                runOnUiThread(() -> {
                    tvAssistantReply.setText(reply);
                    tvAssistantStatus.setText("Ready");
                    etAssistantQuestion.setText("");
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvAssistantReply.setText("Error: " + e.getMessage());
                    tvAssistantStatus.setText("Offline");
                });
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(Locale.US);
            textToSpeech.setSpeechRate(0.95f);

            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = false;
                Toast.makeText(this, "TTS language not supported", Toast.LENGTH_SHORT).show();
            } else {
                isTtsReady = true;
            }
        } else {
            isTtsReady = false;
            Toast.makeText(this, "TTS initialization failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}