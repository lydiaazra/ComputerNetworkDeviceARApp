package com.example.computernetworkdevicearapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class MainMenuActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        // ✅ Hide system scroll indicator
        getWindow().getDecorView().setVerticalScrollBarEnabled(false);

        mAuth = FirebaseAuth.getInstance();

        findViewById(R.id.btnAR).setOnClickListener(v ->
                startActivity(new Intent(this, DeviceSelectActivity.class)));

        findViewById(R.id.btnDeviceDemo).setOnClickListener(v ->
                startActivity(new Intent(this, DeviceDemoSelectActivity.class)));


        findViewById(R.id.btnQuiz).setOnClickListener(v ->
                startActivity(new Intent(this, QuizLevelActivity.class)));

        findViewById(R.id.btnCombinedAR).setOnClickListener(v ->
                startActivity(new Intent(this, CombinedARActivity.class)));

        findViewById(R.id.btnExit).setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}