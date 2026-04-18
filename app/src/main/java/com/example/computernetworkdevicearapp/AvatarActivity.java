package com.example.computernetworkdevicearapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import io.github.sceneview.ar.ArSceneView;
import io.github.sceneview.ar.node.ArModelNode;
import io.github.sceneview.ar.node.PlacementMode;
import dev.romainguy.kotlin.math.Float3;

public class AvatarActivity extends AppCompatActivity {

    private ArSceneView arSceneView;
    private ArModelNode modelNode;
    private Button btnBack, btnDeviceRouter, btnDeviceSwitch, btnDeviceHub,
            btnDeviceAccessPoint, btnDeviceModem, btnPlaceAvatar;
    private TextView tvDeviceTitle, tvExplanation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar);

        arSceneView = findViewById(R.id.arSceneView);
        tvDeviceTitle = findViewById(R.id.tvDeviceTitle);
        tvExplanation = findViewById(R.id.tvExplanation);
        btnBack = findViewById(R.id.btnBack);

        btnDeviceRouter = findViewById(R.id.btnDeviceRouter);
        btnDeviceSwitch = findViewById(R.id.btnDeviceSwitch);
        btnDeviceHub = findViewById(R.id.btnDeviceHub);
        btnDeviceAccessPoint = findViewById(R.id.btnDeviceAccessPoint);
        btnDeviceModem = findViewById(R.id.btnDeviceModem);
        btnPlaceAvatar = findViewById(R.id.btnPlaceAvatar);

        modelNode = new ArModelNode(
                PlacementMode.PLANE_HORIZONTAL,
                new Float3(0f, 0f, 0f),
                true,
                false
        );

        modelNode.loadModelGlbAsync(
                "models/avatar.glb",
                true,
                0.5f,
                null,
                null,
                null
        );

        arSceneView.addChild(modelNode);

        btnPlaceAvatar.setOnClickListener(v -> {
            modelNode.anchor();
            Toast.makeText(this, "Avatar placed!", Toast.LENGTH_SHORT).show();
        });

        btnDeviceRouter.setOnClickListener(v -> showDeviceInfo("Router",
                "A router is a networking device that forwards data packets between computer networks. Routers perform the traffic directing functions on the Internet."));

        btnDeviceSwitch.setOnClickListener(v -> showDeviceInfo("Switch",
                "A network switch is networking hardware that connects devices on a computer network by using packet switching to receive and forward data to the destination device."));

        btnDeviceHub.setOnClickListener(v -> showDeviceInfo("Hub",
                "A network hub is a node that broadcasts data to every computer or Ethernet-based device connected to it. It is less intelligent than a switch."));

        btnDeviceAccessPoint.setOnClickListener(v -> showDeviceInfo("Access Point",
                "A wireless access point (WAP) is a networking hardware device that allows other Wi-Fi devices to connect to a wired network."));

        btnDeviceModem.setOnClickListener(v -> showDeviceInfo("Modem",
                "A modem (modulator-demodulator) is a device that converts signals from one form to another, allowing computers to communicate over telephone lines or cable systems."));

        btnBack.setOnClickListener(v -> finish());
    }

    private void showDeviceInfo(String title, String explanation) {
        tvDeviceTitle.setText(title);
        tvExplanation.setText(explanation);
    }
}