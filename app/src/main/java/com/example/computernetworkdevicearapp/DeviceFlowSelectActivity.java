package com.example.computernetworkdevicearapp;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Arrays;
import java.util.List;

public class DeviceFlowSelectActivity extends AppCompatActivity {

    // Tier 1 = full interactive demo (distinct forwarding/routing logic, like Switch).
    // Tier 2 = simple, device-specific animation (single concept, not source→destination picking).
    // Tier 3 = appears as a participant inside other devices' demos, not a standalone scenario.
    private enum Tier { FULL, SIMPLE, PARTICIPANT }

    private static class DeviceEntry {
        final String id, label, icon, description;
        final Tier tier;
        final boolean active; // whether this device's scenario is built yet

        DeviceEntry(String id, String label, String icon, String description, Tier tier, boolean active) {
            this.id = id; this.label = label; this.icon = icon;
            this.description = description; this.tier = tier; this.active = active;
        }
    }

    private final List<DeviceEntry> devices = Arrays.asList(
            new DeviceEntry("home_network", "Home Network", "🏠", "Build a complete home network — Router, Modem, Switch, WAP and devices", Tier.FULL, true),
            new DeviceEntry("office_network", "Office Network", "🏢", "Build an office network with departments, Firewall and multiple Switches", Tier.FULL, true),
            new DeviceEntry("server_room_network", "Server Room Network", "🖥️", "Build a server room with Gateway, Switch and legacy Hub equipment", Tier.FULL, true),
            new DeviceEntry("full_network", "Full Network", "🌐", "See every device connected together in one real network topology", Tier.FULL, true),
            new DeviceEntry("switch",   "Switch",   "🔀", "Selective forwarding — only the intended device receives the data", Tier.FULL, true),
            new DeviceEntry("hub",      "Hub",      "📡", "Broadcasts the same data to every connected device",                Tier.FULL, true),
            new DeviceEntry("router",   "Router",   "🔌", "Routes data between networks using IP addresses",                   Tier.FULL, true),
            new DeviceEntry("firewall", "Firewall", "🛡️", "Allows or blocks data based on security rules",                     Tier.FULL, true),

            new DeviceEntry("repeater", "Repeater", "🔁", "Regenerates a weak signal so it can travel further",                Tier.SIMPLE, true),
            new DeviceEntry("gateway",  "Gateway",  "🌐", "Translates data between two different network types",              Tier.SIMPLE, true),
            new DeviceEntry("wap",      "WAP",      "📶", "Bridges a wireless signal onto the wired network",                  Tier.SIMPLE, true),
            new DeviceEntry("server",   "Server",   "🗄️", "Responds to requests from other devices on the network",           Tier.SIMPLE, true),

            new DeviceEntry("nic",      "NIC",      "💾", "Appears as the connection point inside PCs in other demos",         Tier.PARTICIPANT, true),
            new DeviceEntry("pc",       "PC",       "💻", "Appears as an endpoint device inside other demos",                  Tier.PARTICIPANT, true)
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_flow_select);

        LinearLayout container = findViewById(R.id.deviceListContainer);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        for (DeviceEntry d : devices) container.addView(buildRow(d));
    }

    private View buildRow(DeviceEntry d) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(Color.parseColor(d.active ? "#0A1E14" : "#11151F"));
        bg.setStroke(dp(1), Color.parseColor(d.active ? "#1E4A2A" : "#242C3A"));
        row.setBackground(bg);
        row.setAlpha(d.active ? 1f : 0.55f);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(10);
        row.setLayoutParams(rowLp);

        TextView icon = new TextView(this);
        icon.setText(d.icon);
        icon.setTextSize(20);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(44), dp(44));
        iconLp.setMarginEnd(dp(12));
        icon.setLayoutParams(iconLp);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setCornerRadius(dp(12));
        iconBg.setColor(Color.parseColor(d.active ? "#0F2A1A" : "#1A2030"));
        icon.setBackground(iconBg);
        row.addView(icon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textCol.setLayoutParams(textLp);

        TextView label = new TextView(this);
        label.setText(d.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(14);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        textCol.addView(label);

        TextView desc = new TextView(this);
        desc.setText(d.active ? d.description : d.description + " — coming soon");
        desc.setTextColor(Color.parseColor("#8FA5C3"));
        desc.setTextSize(10.5f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dp(2);
        desc.setLayoutParams(descLp);
        textCol.addView(desc);

        row.addView(textCol);

        TextView status = new TextView(this);
        status.setText("›");
        status.setTextColor(Color.parseColor(d.active ? "#3EC26E" : "#586071"));
        status.setTextSize(22);
        row.addView(status);

        if (d.active) {
            row.setOnClickListener(v -> {
                if (d.tier == Tier.PARTICIPANT) {
                    Intent intent = new Intent(this, DeviceVoiceARActivity.class);
                    intent.putExtra("deviceName", d.label);
                    startActivity(intent);
                } else {
                    Intent intent = new Intent(this, DeviceFlowDemoActivity.class);
                    intent.putExtra("deviceType", d.id);
                    startActivity(intent);
                }
            });
        } else {
            row.setOnClickListener(v ->
                    Toast.makeText(this, d.label + " demo is coming soon", Toast.LENGTH_SHORT).show());
        }

        return row;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}