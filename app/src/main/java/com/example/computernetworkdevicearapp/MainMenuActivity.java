package com.example.computernetworkdevicearapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class MainMenuActivity extends AppCompatActivity {

    private Button btnAR, btnQuiz, btnAvatar, btnExit;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        mAuth = FirebaseAuth.getInstance();

        btnAR = findViewById(R.id.btnAR);
        btnQuiz = findViewById(R.id.btnQuiz);
        btnAvatar = findViewById(R.id.btnAvatar);
        btnExit = findViewById(R.id.btnExit);

        // AR Button - goes to Device Display
        btnAR.setOnClickListener(v -> {
            Intent intent = new Intent(MainMenuActivity.this, DeviceDisplay.class);
            intent.putExtra("deviceName", "Router");
            startActivity(intent);
        });

        // Quiz Button - goes to Level Selection
        btnQuiz.setOnClickListener(v -> {
            Intent intent = new Intent(MainMenuActivity.this, QuizLevelActivity.class);
            startActivity(intent);
        });

        // Avatar Button - goes to Avatar Screen
        btnAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(MainMenuActivity.this, AvatarActivity.class);
            startActivity(intent);
        });

        // Exit Button - logout and close app
        btnExit.setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(MainMenuActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}