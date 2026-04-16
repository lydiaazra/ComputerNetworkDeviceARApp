package com.example.computernetworkdevicearapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class QuizLevelActivity extends AppCompatActivity {

    private Button btnEasy, btnIntermediate, btnAdvanced, btnBack;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_level);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() != null) {
            userId = mAuth.getCurrentUser().getUid();
        } else {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnEasy = findViewById(R.id.btnEasy);
        btnIntermediate = findViewById(R.id.btnIntermediate);
        btnAdvanced = findViewById(R.id.btnAdvanced);
        btnBack = findViewById(R.id.btnBack);

        loadProgress();

        btnEasy.setOnClickListener(v -> {
            Toast.makeText(this, "Easy clicked", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(QuizLevelActivity.this, ARQuizActivity.class);
            intent.putExtra("level", "easy");
            startActivity(intent);
        });

        btnIntermediate.setOnClickListener(v -> {
            db.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(document -> {
                        if (Boolean.TRUE.equals(document.getBoolean("easyCompleted"))) {
                            Intent intent = new Intent(QuizLevelActivity.this, ARQuizActivity.class);
                            intent.putExtra("level", "intermediate");
                            startActivity(intent);
                        } else {
                            Toast.makeText(this, "Complete Easy level first!", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        btnAdvanced.setOnClickListener(v -> {
            db.collection("users").document(userId)
                    .get()
                    .addOnSuccessListener(document -> {
                        if (Boolean.TRUE.equals(document.getBoolean("intermediateCompleted"))) {
                            Intent intent = new Intent(QuizLevelActivity.this, ARQuizActivity.class);
                            intent.putExtra("level", "advanced");
                            startActivity(intent);
                        } else {
                            Toast.makeText(this, "Complete Intermediate level first!", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void loadProgress() {
        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        boolean easyDone = Boolean.TRUE.equals(document.getBoolean("easyCompleted"));
                        boolean intermediateDone = Boolean.TRUE.equals(document.getBoolean("intermediateCompleted"));
                        updateButtonStates(easyDone, intermediateDone);
                    }
                });
    }

    private void updateButtonStates(boolean easyDone, boolean intermediateDone) {
        btnEasy.setAlpha(1.0f);

        if (easyDone) {
            btnIntermediate.setAlpha(1.0f);
            btnIntermediate.setText("Intermediate ✓ Unlocked");
        } else {
            btnIntermediate.setAlpha(0.4f);
            btnIntermediate.setText("Intermediate 🔒 Locked");
        }

        if (intermediateDone) {
            btnAdvanced.setAlpha(1.0f);
            btnAdvanced.setText("Advanced ✓ Unlocked");
        } else {
            btnAdvanced.setAlpha(0.4f);
            btnAdvanced.setText("Advanced 🔒 Locked");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProgress();
    }
}