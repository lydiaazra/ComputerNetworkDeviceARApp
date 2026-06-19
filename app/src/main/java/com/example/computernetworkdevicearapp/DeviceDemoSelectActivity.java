package com.example.computernetworkdevicearapp;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.android.material.button.MaterialButton;

public class DeviceDemoSelectActivity extends AppCompatActivity {

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_demo_select);

        setupCard(R.id.cardRouter,   "router");
        setupCard(R.id.cardSwitch,   "switch");
        setupCard(R.id.cardHub,      "hub");
        setupCard(R.id.cardFirewall, "firewall");
        setupCard(R.id.cardAccessPoint,      "wap");
        setupCard(R.id.cardNIC,      "nic");
        setupCard(R.id.cardRepeater, "repeater");
        setupCard(R.id.cardGateway,  "gateway");

        MaterialButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupCard(int cardId, String deviceType) {
        CardView card = findViewById(cardId);
        if (card == null) return;
        card.setOnClickListener(v -> {
            Intent intent = new Intent(this, NetworkLabActivity.class);
            intent.putExtra("deviceType", deviceType);
            startActivity(intent);
        });
    }
}