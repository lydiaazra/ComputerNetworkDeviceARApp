package com.example.computernetworkdevicearapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class TopologySelectActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_topology_select);

        findViewById(R.id.cardHome).setOnClickListener(v -> launchAssembly("home"));
        findViewById(R.id.cardOffice).setOnClickListener(v -> launchAssembly("office"));
        findViewById(R.id.cardServerRoom).setOnClickListener(v -> launchAssembly("server_room"));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void launchAssembly(String topologyId) {
        Intent intent = new Intent(this, AssemblyARActivity.class);
        intent.putExtra("topologyId", topologyId);
        startActivity(intent);
    }
}