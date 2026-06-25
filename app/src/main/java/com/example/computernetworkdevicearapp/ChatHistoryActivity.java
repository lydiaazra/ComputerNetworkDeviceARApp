package com.example.computernetworkdevicearapp;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChatHistoryActivity extends AppCompatActivity {

    private static final String TAG = "ChatHistory";

    private LinearLayout llHistoryContainer;
    private TextView      tvEmptyState;
    private ProgressBar   progressLoading;

    private FirebaseFirestore db;
    private FirebaseAuth      mAuth;

    private static class ChatEntry {
        String sender, message, deviceName;
        long   timestampMillis;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_history);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        llHistoryContainer = findViewById(R.id.llHistoryContainer);
        tvEmptyState        = findViewById(R.id.tvEmptyState);
        progressLoading      = findViewById(R.id.progressLoading);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnClearHistory).setOnClickListener(v -> confirmClearHistory());

        loadHistory();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Chat History")
                .setMessage("This will permanently delete all your chat history. This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> clearHistory())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearHistory() {
        if (mAuth.getCurrentUser() == null) return;
        String uid = mAuth.getCurrentUser().getUid();

        progressLoading.setVisibility(View.VISIBLE);
        llHistoryContainer.removeAllViews();
        tvEmptyState.setVisibility(View.GONE);

        db.collection("users").document(uid)
                .collection("chatHistory")
                .get()
                .addOnSuccessListener(snapshot -> {
                    com.google.firebase.firestore.WriteBatch batch = db.batch();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        batch.delete(doc.getReference());
                    }
                    batch.commit()
                            .addOnSuccessListener(unused -> showEmpty())
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "Failed to clear history: " + e.getMessage());
                                showEmpty();
                            });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to fetch history for deletion: " + e.getMessage());
                    showEmpty();
                });
    }

    private void loadHistory() {
        if (mAuth.getCurrentUser() == null) {
            showEmpty();
            return;
        }
        String uid = mAuth.getCurrentUser().getUid();

        db.collection("users").document(uid)
                .collection("chatHistory")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(this::onHistoryLoaded)
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to load chat history: " + e.getMessage());
                    showEmpty();
                });
    }

    private void onHistoryLoaded(com.google.firebase.firestore.QuerySnapshot snapshot) {
        progressLoading.setVisibility(View.GONE);

        if (snapshot.isEmpty()) {
            showEmpty();
            return;
        }

        // Preserve insertion order; group chronologically by device
        Map<String, List<ChatEntry>> grouped = new LinkedHashMap<>();
        Map<String, Long> lastSeenAt = new LinkedHashMap<>();

        for (QueryDocumentSnapshot doc : snapshot) {
            ChatEntry entry = new ChatEntry();
            entry.sender     = doc.getString("sender");
            entry.message    = doc.getString("message");
            entry.deviceName = doc.getString("deviceName");
            com.google.firebase.Timestamp ts = doc.getTimestamp("timestamp");
            entry.timestampMillis = ts != null ? ts.toDate().getTime() : 0L;

            if (entry.deviceName == null) entry.deviceName = "Unknown Device";

            grouped.computeIfAbsent(entry.deviceName, k -> new ArrayList<>()).add(entry);
            lastSeenAt.put(entry.deviceName, entry.timestampMillis);
        }

        // Sort device groups: most recently active device first
        List<String> deviceOrder = new ArrayList<>(grouped.keySet());
        deviceOrder.sort((a, b) -> Long.compare(lastSeenAt.get(b), lastSeenAt.get(a)));

        for (String device : deviceOrder) {
            llHistoryContainer.addView(buildSectionHeader(device));
            for (ChatEntry entry : grouped.get(device)) {
                boolean isUser = "You".equals(entry.sender);
                llHistoryContainer.addView(buildBubble(isUser, entry.sender, entry.message));
            }
        }
    }

    private void showEmpty() {
        progressLoading.setVisibility(View.GONE);
        tvEmptyState.setVisibility(View.VISIBLE);
    }

    private View buildSectionHeader(String deviceName) {
        TextView tv = new TextView(this);
        tv.setText(deviceIcon(deviceName) + "  " + deviceName);
        tv.setTextColor(0xFF7EDCFF);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(18);
        lp.bottomMargin = dp(8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private String deviceIcon(String deviceName) {
        String n = deviceName.toLowerCase();
        if (n.contains("router"))   return "🔀";
        if (n.contains("switch"))   return "🔌";
        if (n.contains("hub"))      return "📡";
        if (n.contains("firewall")) return "🛡️";
        if (n.contains("access"))   return "📶";
        if (n.contains("nic"))      return "💾";
        if (n.contains("repeat"))   return "🔁";
        if (n.contains("gateway"))  return "🌐";
        return "💻";
    }

    /** Same bubble style as the live AR Assistant chat — You right-aligned, AI Avatar left-aligned. */
    private TextView buildBubble(boolean isUser, String label, String message) {
        int labelColor  = isUser ? 0xFF7EDCFF : 0xFF9EE6A8;
        int bubbleColor = isUser ? 0xFF1A3A6B : 0xFF1C2438;

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        String prefix = label + ": ";
        ssb.append(prefix);
        ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, prefix.length(), 0);
        ssb.setSpan(new ForegroundColorSpan(labelColor), 0, prefix.length(), 0);
        ssb.append(message);

        TextView tv = new TextView(this);
        tv.setText(ssb);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(12);
        int padH = dp(12), padV = dp(8);
        tv.setPadding(padH, padV, padH, padV);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bubbleColor);
        bg.setCornerRadius(dp(12));
        tv.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = isUser ? Gravity.END : Gravity.START;
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        tv.setLayoutParams(lp);

        return tv;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}