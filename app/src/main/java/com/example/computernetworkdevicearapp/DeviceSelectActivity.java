package com.example.computernetworkdevicearapp;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class DeviceSelectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_select);

        // ✅ Each card opens DeviceVoiceARActivity — avatar-led, no ArFragment
        //    (DeviceDisplay/ArFragment crashes on Android 16 / Galaxy A56).
        //    CameraARActivity (with the text info cards) is still available
        //    separately for Device Demo's detail view if needed.
        setupCard(R.id.cardRouter,      "Router");
        setupCard(R.id.cardSwitch,      "Switch");
        setupCard(R.id.cardHub,         "Hub");
        setupCard(R.id.cardFirewall,    "Firewall");
        setupCard(R.id.cardAccessPoint, "Access Point");
        setupCard(R.id.cardNIC, "NIC");
        setupCard(R.id.cardRepeater, "Repeater");
        setupCard(R.id.cardGateway, "Gateway");
        setupCard(R.id.cardServer, "Server");
        setupCard(R.id.cardModem, "Modem");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void setupCard(int cardId, String deviceName) {
        CardView card = findViewById(cardId);
        if (card == null) return;
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, DeviceVoiceARActivity.class);
            intent.putExtra("deviceName", deviceName);
            startActivity(intent);
        });
    }
}