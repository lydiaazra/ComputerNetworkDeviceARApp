package com.example.computernetworkdevicearapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText etRegEmail, etRegPassword;
    private Button btnRegister;
    private TextView tvBackToLogin;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etRegEmail = findViewById(R.id.etRegEmail);
        etRegPassword = findViewById(R.id.etRegPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvBackToLogin = findViewById(R.id.tvBackToLogin);

        btnRegister.setOnClickListener(v -> registerUser());

        tvBackToLogin.setOnClickListener(v -> finish());
    }

    private void registerUser() {
        String email = etRegEmail.getText().toString().trim();
        String password = etRegPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            etRegEmail.setError("Email is required!");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            etRegPassword.setError("Password is required!");
            return;
        }
        if (password.length() < 6) {
            etRegPassword.setError("Password must be at least 6 characters!");
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        // Save user progress to Firestore
                        String userId = mAuth.getCurrentUser().getUid();
                        Map<String, Object> userProgress = new HashMap<>();
                        userProgress.put("email", email);
                        userProgress.put("easyCompleted", false);
                        userProgress.put("intermediateCompleted", false);
                        userProgress.put("advancedCompleted", false);
                        userProgress.put("easyScore", 0);
                        userProgress.put("intermediateScore", 0);
                        userProgress.put("advancedScore", 0);

                        db.collection("users").document(userId)
                                .set(userProgress)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(RegisterActivity.this,
                                            "Registration Successful!", Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(RegisterActivity.this,
                                            MainMenuActivity.class));
                                    finish();
                                });
                    } else {
                        Toast.makeText(RegisterActivity.this,
                                "Registration Failed! " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}