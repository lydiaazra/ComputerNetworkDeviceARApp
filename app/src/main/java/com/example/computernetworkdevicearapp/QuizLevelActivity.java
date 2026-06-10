package com.example.computernetworkdevicearapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class QuizLevelActivity extends AppCompatActivity {

    private MaterialButton btnEasy, btnIntermediate, btnAdvanced, btnBack;
    private ProgressBar progressEasy;
    private TextView tvEasyProgress, tvEasyBestScore;
    private TextView tvIntermediateBestScore, tvAdvancedBestScore;
    private TextView tvIntermediateIcon, tvAdvancedIcon;
    private LinearLayout lockRequirementIntermediate, lockRequirementAdvanced;

    private FirebaseFirestore db;
    private FirebaseAuth      mAuth;
    private String            userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz_level);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        if (mAuth.getCurrentUser() != null) {
            userId = mAuth.getCurrentUser().getUid();
        } else {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        setupButtons();
        loadProgress();
    }

    private void bindViews() {
        btnEasy          = findViewById(R.id.btnEasy);
        btnIntermediate  = findViewById(R.id.btnIntermediate);
        btnAdvanced      = findViewById(R.id.btnAdvanced);
        btnBack          = findViewById(R.id.btnBack);
        progressEasy     = findViewById(R.id.progressEasy);
        tvEasyProgress   = findViewById(R.id.tvEasyProgress);
        tvEasyBestScore  = findViewById(R.id.tvEasyBestScore);
        tvIntermediateBestScore = findViewById(R.id.tvIntermediateBestScore);
        tvAdvancedBestScore     = findViewById(R.id.tvAdvancedBestScore);
        tvIntermediateIcon      = findViewById(R.id.tvIntermediateIcon);
        tvAdvancedIcon          = findViewById(R.id.tvAdvancedIcon);
        lockRequirementIntermediate = findViewById(R.id.lockRequirementIntermediate);
        lockRequirementAdvanced     = findViewById(R.id.lockRequirementAdvanced);
    }

    private void setupButtons() {

        btnEasy.setOnClickListener(v -> {
            Intent intent = new Intent(this, ARQuizActivity.class);
            intent.putExtra("level", "easy");
            startActivity(intent);
        });

        btnIntermediate.setOnClickListener(v -> {
            db.collection("users").document(userId).get()
                    .addOnSuccessListener(doc -> {
                        if (Boolean.TRUE.equals(doc.getBoolean("easyCompleted"))) {
                            Intent intent = new Intent(this, ARQuizActivity.class);
                            intent.putExtra("level", "intermediate");
                            startActivity(intent);
                        } else {
                            Toast.makeText(this,
                                    "Complete Easy level first!", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        btnAdvanced.setOnClickListener(v -> {
            db.collection("users").document(userId).get()
                    .addOnSuccessListener(doc -> {
                        if (Boolean.TRUE.equals(doc.getBoolean("intermediateCompleted"))) {
                            Intent intent = new Intent(this, ARQuizActivity.class);
                            intent.putExtra("level", "advanced");
                            startActivity(intent);
                        } else {
                            Toast.makeText(this,
                                    "Complete Intermediate level first!", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void loadProgress() {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    boolean easyDone         = Boolean.TRUE.equals(doc.getBoolean("easyCompleted"));
                    boolean intermediateDone = Boolean.TRUE.equals(doc.getBoolean("intermediateCompleted"));

                    // Scores
                    long easyScore         = doc.getLong("easyBestScore")         != null ? doc.getLong("easyBestScore")         : 0;
                    long intermediateScore = doc.getLong("intermediateBestScore") != null ? doc.getLong("intermediateBestScore") : 0;
                    long advancedScore     = doc.getLong("advancedBestScore")     != null ? doc.getLong("advancedBestScore")     : 0;

                    // Progress counts
                    long easyProgress = doc.getLong("easyProgress") != null ? doc.getLong("easyProgress") : 0;

                    updateUI(easyDone, intermediateDone,
                            easyScore, intermediateScore, advancedScore,
                            easyProgress);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to load progress", Toast.LENGTH_SHORT).show());
    }

    private void updateUI(boolean easyDone, boolean intermediateDone,
                          long easyScore, long intermediateScore, long advancedScore,
                          long easyProgress) {

        // ── Easy card ────────────────────────────────────────────────────────
        tvEasyBestScore.setText(easyScore + " / 10");
        tvEasyProgress.setText(easyProgress + " / 10");
        progressEasy.setProgress((int) easyProgress);
        btnEasy.setText(easyDone ? "Continue" : "Start");

        // ── Intermediate card ─────────────────────────────────────────────
        tvIntermediateBestScore.setText(intermediateScore + " / 10");
        if (easyDone) {
            lockRequirementIntermediate.setVisibility(View.GONE);
            tvIntermediateIcon.setText("⚡");
            btnIntermediate.setText(intermediateDone ? "Continue" : "Start");
            btnIntermediate.setTextColor(0xFFFFFFFF);
            btnIntermediate.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFFFB300));
        } else {
            lockRequirementIntermediate.setVisibility(View.VISIBLE);
            tvIntermediateIcon.setText("🔒");
            btnIntermediate.setText("🔒  Locked");
            btnIntermediate.setTextColor(0xFF8FA5C3);
            btnIntermediate.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF1A2030));
        }

        // ── Advanced card ─────────────────────────────────────────────────
        tvAdvancedBestScore.setText(advancedScore + " / 10");
        if (intermediateDone) {
            lockRequirementAdvanced.setVisibility(View.GONE);
            tvAdvancedIcon.setText("🔥");
            btnAdvanced.setText("Start");
            btnAdvanced.setTextColor(0xFFFFFFFF);
            btnAdvanced.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFF44336));
        } else {
            lockRequirementAdvanced.setVisibility(View.VISIBLE);
            tvAdvancedIcon.setText("🔒");
            btnAdvanced.setText("🔒  Locked");
            btnAdvanced.setTextColor(0xFF8FA5C3);
            btnAdvanced.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF1A2030));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (userId != null) loadProgress();
    }
}