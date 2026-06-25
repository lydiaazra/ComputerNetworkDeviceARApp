package com.example.computernetworkdevicearapp;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;

import nl.dionsegijn.konfetti.core.Party;
import nl.dionsegijn.konfetti.core.Position;
import nl.dionsegijn.konfetti.core.PartyFactory;
import nl.dionsegijn.konfetti.core.emitter.Emitter;
import nl.dionsegijn.konfetti.core.emitter.EmitterConfig;
import nl.dionsegijn.konfetti.core.models.Shape;
import nl.dionsegijn.konfetti.core.models.Size;
import nl.dionsegijn.konfetti.xml.KonfettiView;

import java.util.concurrent.TimeUnit;

public class ResultActivity extends AppCompatActivity {

    private TextView    tvEmoji, tvResultScore, tvResultLevel, tvResultMessage;
    private TextView    tvBadge, tvCorrect, tvIncorrect, tvAccuracy, tvUnlockText;
    private LinearLayout unlockBanner;
    private Button      btnPrimary, btnBackToMenu;
    private KonfettiView viewKonfetti;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        bindViews();

        int    score = getIntent().getIntExtra("score", 0);
        int    total = getIntent().getIntExtra("total", 10);
        String level = getIntent().getStringExtra("level");
        if (level == null) level = "easy";

        int    incorrect   = total - score;
        double percentage  = total > 0 ? (double) score / total * 100 : 0;

        setupScoreDisplay(score, total, incorrect, percentage);
        setupLevelDisplay(level, percentage);
        setupMessage(percentage);
        setupButtons(level, percentage);
        setupUnlockBanner(level, percentage);

        // Confetti for good scores
        if (percentage >= 60) {
            final String finalLevel = level;
            viewKonfetti.postDelayed(() -> showKonfetti(percentage), 300);
        }
    }

    private void bindViews() {
        tvEmoji         = findViewById(R.id.tvEmoji);
        tvResultScore   = findViewById(R.id.tvResultScore);
        tvResultLevel   = findViewById(R.id.tvResultLevel);
        tvResultMessage = findViewById(R.id.tvResultMessage);
        tvBadge         = findViewById(R.id.tvBadge);
        tvCorrect       = findViewById(R.id.tvCorrect);
        tvIncorrect     = findViewById(R.id.tvIncorrect);
        tvAccuracy      = findViewById(R.id.tvAccuracy);
        tvUnlockText    = findViewById(R.id.tvUnlockText);
        unlockBanner    = findViewById(R.id.unlockBanner);
        btnPrimary      = findViewById(R.id.btnPrimary);
        btnBackToMenu   = findViewById(R.id.btnBackToMenu);
        viewKonfetti    = findViewById(R.id.viewKonfetti);
    }

    private void setupScoreDisplay(int score, int total, int incorrect, double percentage) {
        tvResultScore.setText(score + "/" + total);
        tvCorrect.setText(String.valueOf(score));
        tvIncorrect.setText(String.valueOf(incorrect));
        tvAccuracy.setText((int) percentage + "%");

        if (percentage >= 80) {
            // Green — Passed
            tvEmoji.setText("🏆");
            tvResultScore.setTextColor(Color.parseColor("#00C853"));
            tvBadge.setText("✅  Passed");
            tvBadge.setTextColor(Color.parseColor("#00C853"));
            tvBadge.setBackgroundResource(R.drawable.bg_badge_green);

        } else if (percentage >= 60) {
            // Yellow — Keep Improving
            tvEmoji.setText("🥈");
            tvResultScore.setTextColor(Color.parseColor("#FFB300"));
            tvBadge.setText("😐  Keep Improving");
            tvBadge.setTextColor(Color.parseColor("#FFB300"));
            tvBadge.setBackgroundResource(R.drawable.bg_badge_yellow);

        } else {
            // Red — Needs More Practice
            tvEmoji.setText("❌");
            tvResultScore.setTextColor(Color.parseColor("#F44336"));
            tvBadge.setText("😟  Needs More Practice");
            tvBadge.setTextColor(Color.parseColor("#F44336"));
            tvBadge.setBackgroundResource(R.drawable.bg_badge_red);
        }
    }

    private void setupLevelDisplay(String level, double percentage) {
        String levelName = level.substring(0, 1).toUpperCase() + level.substring(1);
        int levelColor;
        switch (level.toLowerCase()) {
            case "intermediate": levelColor = Color.parseColor("#FFB300"); break;
            case "advanced":     levelColor = Color.parseColor("#F44336"); break;
            default:             levelColor = Color.parseColor("#00C853"); break;
        }
        tvResultLevel.setText("Level: " + levelName);
        tvResultLevel.setTextColor(levelColor);
    }

    private void setupMessage(double percentage) {
        String msg;
        if (percentage == 100) {
            msg = "Perfect Score! Outstanding achievement! 🎉";
        } else if (percentage >= 80) {
            msg = "Great job! Keep learning and improving.";
        } else if (percentage >= 60) {
            msg = "Good effort! Review the topics and try again to do even better.";
        } else if (percentage >= 40) {
            msg = "Don't give up! Practice more and you'll get there.";
        } else {
            msg = "Keep trying! Review the material and give it another shot. 💪";
        }
        tvResultMessage.setText(msg);
    }

    private void setupButtons(String level, double percentage) {
        boolean passed = percentage >= 60;

        if (passed) {
            // Determine next level
            String nextLevel;
            String nextLevelName;
            switch (level.toLowerCase()) {
                case "easy":
                    nextLevel = "intermediate";
                    nextLevelName = "Intermediate Level";
                    break;
                case "intermediate":
                    nextLevel = "advanced";
                    nextLevelName = "Advanced Level";
                    break;
                default:
                    nextLevel = null;
                    nextLevelName = null;
                    break;
            }

            if (nextLevel != null) {
                btnPrimary.setText("Next Level");
                final String nl = nextLevel;
                btnPrimary.setOnClickListener(v -> {
                    Intent intent = new Intent(this, ARQuizActivity.class);
                    intent.putExtra("level", nl);
                    startActivity(intent);
                    finish();
                });
            } else {
                // Advanced completed — no next level
                btnPrimary.setText("Back to Quiz");
                btnPrimary.setOnClickListener(v -> {
                    startActivity(new Intent(this, QuizLevelActivity.class));
                    finish();
                });
            }
        } else {
            btnPrimary.setText("Try Again");
            final String currentLevel = level;
            btnPrimary.setOnClickListener(v -> {
                Intent intent = new Intent(this, ARQuizActivity.class);
                intent.putExtra("level", currentLevel);
                startActivity(intent);
                finish();
            });
        }

        btnBackToMenu.setOnClickListener(v -> {
            Intent intent = new Intent(this, DashboardActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    private void setupUnlockBanner(String level, double percentage) {
        if (percentage < 60) {
            unlockBanner.setVisibility(View.GONE);
            return;
        }

        String nextLevelName;
        switch (level.toLowerCase()) {
            case "easy":         nextLevelName = "Intermediate"; break;
            case "intermediate": nextLevelName = "Advanced";     break;
            default:             nextLevelName = null;           break;
        }

        if (nextLevelName != null) {
            unlockBanner.setVisibility(View.VISIBLE);
            tvUnlockText.setText(nextLevelName + " Level is now available.");
        } else {
            unlockBanner.setVisibility(View.GONE);
        }
    }

    private void showKonfetti(double percentage) {
        long amount = percentage >= 80 ? 150L : 80L;
        EmitterConfig config = new Emitter(3L, TimeUnit.SECONDS).perSecond((int) amount);

        Party party = new PartyFactory(config)
                .angle(270)
                .spread(120)
                .setSpeedBetween(3f, 15f)
                .timeToLive(2500L)
                .shapes(Shape.Circle.INSTANCE, Shape.Square.INSTANCE)
                .sizes(new Size(10, 5f, 0.2f), new Size(14, 6f, 0.2f))
                .position(new Position.Relative(0.5, 0.0))
                .colors(Arrays.asList(
                        Color.YELLOW, Color.GREEN, Color.CYAN,
                        Color.MAGENTA, Color.WHITE,
                        Color.parseColor("#FF6B6B"),
                        Color.parseColor("#4D96FF")))
                .build();

        viewKonfetti.start(party);
    }
}