package com.example.computernetworkdevicearapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class DashboardActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // Hide system scroll indicator
        getWindow().getDecorView().setVerticalScrollBarEnabled(false);

        mAuth = FirebaseAuth.getInstance();
        bindUserGreeting();

        findViewById(R.id.cardARDevices).setOnClickListener(v ->
                startActivity(new Intent(this, DeviceSelectActivity.class)));

        findViewById(R.id.cardARAssistant).setOnClickListener(v ->
                startActivity(new Intent(this, CombinedARActivity.class)));

        findViewById(R.id.cardDeviceDemo).setOnClickListener(v ->
                startActivity(new Intent(this, DeviceDemoSelectActivity.class)));

        findViewById(R.id.cardQuiz).setOnClickListener(v ->
                startActivity(new Intent(this, QuizLevelActivity.class)));

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void bindUserGreeting() {
        TextView tvUserName = findViewById(R.id.tvUserName);
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            tvUserName.setText("Guest");
            return;
        }
        String name = user.getDisplayName();
        if (name == null || name.trim().isEmpty()) {
            String email = user.getEmail();
            name = (email != null && email.contains("@")) ? email.split("@")[0] : "User";
        }
        tvUserName.setText(name + " 👋");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh greeting in case display name changed elsewhere
        bindUserGreeting();
    }
}