package com.example.computernetworkdevicearapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.List;

import nl.dionsegijn.konfetti.core.Party;
import nl.dionsegijn.konfetti.core.Position;
import nl.dionsegijn.konfetti.core.models.Shape;
import nl.dionsegijn.konfetti.core.models.Size;
import nl.dionsegijn.konfetti.xml.KonfettiView;

public class ResultActivity extends AppCompatActivity {

    private TextView tvResultScore, tvResultLevel, tvResultMessage;
    private Button btnBackToMenu, btnTryAgain;
    private KonfettiView viewKonfetti;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        tvResultScore = findViewById(R.id.tvResultScore);
        tvResultLevel = findViewById(R.id.tvResultLevel);
        tvResultMessage = findViewById(R.id.tvResultMessage);
        btnBackToMenu = findViewById(R.id.btnBackToMenu);
        btnTryAgain = findViewById(R.id.btnTryAgain);
        viewKonfetti = findViewById(R.id.viewKonfetti);

        int score = getIntent().getIntExtra("score", 0);
        int total = getIntent().getIntExtra("total", 0);
        String level = getIntent().getStringExtra("level");

        tvResultScore.setText(score + "/" + total);

        if (level != null && !level.isEmpty()) {
            tvResultLevel.setText("Level: " +
                    level.substring(0, 1).toUpperCase() + level.substring(1));
        } else {
            tvResultLevel.setText("Level: Unknown");
        }

        double percentage = 0;
        if (total > 0) {
            percentage = (double) score / total * 100;
        }

        if (percentage == 100) {
            tvResultMessage.setText("Perfect Score! 🏆 Outstanding!");
        } else if (percentage >= 80) {
            tvResultMessage.setText("Excellent! 🌟 Keep it up!");
        } else if (percentage >= 60) {
            tvResultMessage.setText("Good job! 👍 Keep practicing!");
        } else if (percentage >= 40) {
            tvResultMessage.setText("Not bad! 📚 Study more!");
        } else {
            tvResultMessage.setText("Keep trying! 💪 You can do it!");
        }

        viewKonfetti.postDelayed(() -> showKonfetti(score, total), 300);

        btnBackToMenu.setOnClickListener(v -> {
            Intent intent = new Intent(ResultActivity.this, MainMenuActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        btnTryAgain.setOnClickListener(v -> {
            Intent intent = new Intent(ResultActivity.this, ARQuizActivity.class);
            intent.putExtra("level", level);
            startActivity(intent);
            finish();
        });
    }

    private void showKonfetti(int score, int total) {
        int confettiAmount = 80;

        if (total > 0) {
            double percentage = (double) score / total * 100;

            if (percentage == 100) {
                confettiAmount = 220;
            } else if (percentage >= 80) {
                confettiAmount = 160;
            } else if (percentage >= 60) {
                confettiAmount = 120;
            } else {
                confettiAmount = 80;
            }
        }

        List<Party> parties = Arrays.asList(
                new Party(
                        0f,
                        30f,
                        0.9f,
                        360,
                        Arrays.asList(
                                Color.YELLOW,
                                Color.GREEN,
                                Color.CYAN,
                                Color.MAGENTA,
                                Color.WHITE,
                                Color.parseColor("#FF6B6B"),
                                Color.parseColor("#4D96FF")
                        ),
                        Arrays.asList(
                                Shape.Circle,
                                Shape.Square
                        ),
                        Arrays.asList(
                                new Size(12, 5f),
                                new Size(16, 6f)
                        ),
                        2500L,
                        confettiAmount,
                        new Position.Relative(0.5, 0.0)
                )
        );

        viewKonfetti.start(parties);
    }
}