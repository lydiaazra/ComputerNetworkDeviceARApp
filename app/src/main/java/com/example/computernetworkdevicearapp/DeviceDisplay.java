package com.example.computernetworkdevicearapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import dev.romainguy.kotlin.math.Float3;
import io.github.sceneview.ar.ArSceneView;
import io.github.sceneview.ar.node.ArModelNode;
import io.github.sceneview.ar.node.PlacementMode;

public class DeviceDisplay extends AppCompatActivity {

    private ArSceneView arSceneView;
    private ArModelNode modelNode;

    private TextView tvDeviceTitle, tvExplanation;
    private Button btnPlaceDevice, btnBack;

    private String deviceName;
    private String modelPath;
    private String explanation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_display);

        arSceneView = findViewById(R.id.arSceneView);
        tvDeviceTitle = findViewById(R.id.tvDeviceTitle);
        tvExplanation = findViewById(R.id.tvExplanation);
        btnPlaceDevice = findViewById(R.id.btnPlaceDevice);
        btnBack = findViewById(R.id.btnBack);

        deviceName = getIntent().getStringExtra("deviceName");

        if (deviceName == null || deviceName.trim().isEmpty()) {
            deviceName = "Router";
        }

        setupDeviceData(deviceName);
        setupARModel();

        btnPlaceDevice.setOnClickListener(v -> {
            if (modelNode != null) {
                modelNode.anchor();
                Toast.makeText(this, deviceName + " placed!", Toast.LENGTH_SHORT).show();
            }
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
}