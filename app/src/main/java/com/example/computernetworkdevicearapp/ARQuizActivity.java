package com.example.computernetworkdevicearapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ARQuizActivity extends AppCompatActivity {

    private FrameLayout arSceneView;
    private TextView tvQuestion, tvQuestionNumber, tvScore, tvDeviceName;
    private Button btnOptionA, btnOptionB, btnOptionC, btnOptionD;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private final List<Map<String, Object>> questions = new ArrayList<>();
    private int currentQuestionIndex = 0;
    private int score = 0;
    private String level;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar_quiz);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        level = getIntent().getStringExtra("level");

        Toast.makeText(this, "ARQuizActivity opened", Toast.LENGTH_SHORT).show();

        if (level == null || level.trim().isEmpty()) {
            Toast.makeText(this, "Quiz level not found", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        arSceneView = findViewById(R.id.arSceneView);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvQuestionNumber = findViewById(R.id.tvQuestionNumber);
        tvScore = findViewById(R.id.tvScore);
        tvDeviceName = findViewById(R.id.tvDeviceName);
        btnOptionA = findViewById(R.id.btnOptionA);
        btnOptionB = findViewById(R.id.btnOptionB);
        btnOptionC = findViewById(R.id.btnOptionC);
        btnOptionD = findViewById(R.id.btnOptionD);

        if (arSceneView == null || tvQuestion == null || tvQuestionNumber == null ||
                tvScore == null || tvDeviceName == null ||
                btnOptionA == null || btnOptionB == null ||
                btnOptionC == null || btnOptionD == null) {
            Toast.makeText(this, "Missing views in activity_ar_quiz.xml", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnOptionA.setOnClickListener(v -> checkAnswer("A"));
        btnOptionB.setOnClickListener(v -> checkAnswer("B"));
        btnOptionC.setOnClickListener(v -> checkAnswer("C"));
        btnOptionD.setOnClickListener(v -> checkAnswer("D"));

        loadQuestions();
    }

    private void loadQuestions() {
        Toast.makeText(this, "Selected level: " + level, Toast.LENGTH_SHORT).show();

        db.collection("questions")
                .whereEqualTo("level", level.toLowerCase())
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    questions.clear();

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        questions.add(doc.getData());
                    }

                    Toast.makeText(this, "Questions loaded: " + questions.size(), Toast.LENGTH_LONG).show();

                    if (!questions.isEmpty()) {
                        showQuestion();
                    } else {
                        Toast.makeText(this,
                                "No questions found for level: " + level,
                                Toast.LENGTH_LONG).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Firestore Error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
    }

    private void showQuestion() {
        if (currentQuestionIndex >= questions.size()) {
            finishQuiz();
            return;
        }

        Map<String, Object> question = questions.get(currentQuestionIndex);
        String deviceName = (String) question.get("device");

        tvQuestionNumber.setText("Question " + (currentQuestionIndex + 1) + "/" + questions.size());
        tvScore.setText("Score: " + score);
        tvQuestion.setText(String.valueOf(question.get("question_text")));

        btnOptionA.setText("A. " + String.valueOf(question.get("option_a")));
        btnOptionB.setText("B. " + String.valueOf(question.get("option_b")));
        btnOptionC.setText("C. " + String.valueOf(question.get("option_c")));
        btnOptionD.setText("D. " + String.valueOf(question.get("option_d")));

        updateDeviceLabel(deviceName);
        resetButtonColors();
        setButtonsEnabled(true);
    }

    private void updateDeviceLabel(String deviceName) {
        if (deviceName == null) {
            tvDeviceName.setText("📡 Network Device");
            return;
        }

        switch (deviceName.toLowerCase()) {
            case "router":
                tvDeviceName.setText("📡 Router");
                break;
            case "switch":
                tvDeviceName.setText("🔌 Switch");
                break;
            case "hub":
                tvDeviceName.setText("🔄 Hub");
                break;
            default:
                tvDeviceName.setText("📡 " + deviceName);
                break;
        }
    }

    private void checkAnswer(String selected) {
        if (currentQuestionIndex >= questions.size()) return;

        Map<String, Object> question = questions.get(currentQuestionIndex);
        String correct = (String) question.get("correct_answer");

        setButtonsEnabled(false);

        if (selected.equals(correct)) {
            score++;
            highlightButton(selected, true);
            Toast.makeText(this, "✅ Correct!", Toast.LENGTH_SHORT).show();
        } else {
            highlightButton(selected, false);
            if (correct != null) {
                highlightButton(correct, true);
            }
            Toast.makeText(this, "❌ Wrong! Correct: " + correct, Toast.LENGTH_SHORT).show();
        }

        new Handler().postDelayed(() -> {
            currentQuestionIndex++;
            showQuestion();
        }, 1500);
    }

    private void highlightButton(String option, boolean correct) {
        int color = correct ? Color.parseColor("#4CAF50") : Color.parseColor("#F44336");

        switch (option) {
            case "A":
                btnOptionA.setBackgroundColor(color);
                break;
            case "B":
                btnOptionB.setBackgroundColor(color);
                break;
            case "C":
                btnOptionC.setBackgroundColor(color);
                break;
            case "D":
                btnOptionD.setBackgroundColor(color);
                break;
        }
    }

    private void resetButtonColors() {
        int defaultColor = Color.parseColor("#1E2A4A");
        btnOptionA.setBackgroundColor(defaultColor);
        btnOptionB.setBackgroundColor(defaultColor);
        btnOptionC.setBackgroundColor(defaultColor);
        btnOptionD.setBackgroundColor(defaultColor);
    }

    private void setButtonsEnabled(boolean enabled) {
        btnOptionA.setEnabled(enabled);
        btnOptionB.setEnabled(enabled);
        btnOptionC.setEnabled(enabled);
        btnOptionD.setEnabled(enabled);
    }

    private void finishQuiz() {
        if (mAuth.getCurrentUser() == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        Map<String, Object> updates = new HashMap<>();
        updates.put(level + "Score", score);
        updates.put(level + "Completed", true);

        db.collection("users").document(userId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Intent intent = new Intent(ARQuizActivity.this, ResultActivity.class);
                    intent.putExtra("score", score);
                    intent.putExtra("total", questions.size());
                    intent.putExtra("level", level);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to save score: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
    }
}