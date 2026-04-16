package com.example.computernetworkdevicearapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class AvatarActivity extends AppCompatActivity {

    private Button btnDeviceRouter, btnDeviceSwitch, btnDeviceHub,
            btnDeviceAccessPoint, btnDeviceModem, btnBack;
    private TextView tvDeviceTitle, tvExplanation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar);

        btnDeviceRouter = findViewById(R.id.btnDeviceRouter);
        btnDeviceSwitch = findViewById(R.id.btnDeviceSwitch);
        btnDeviceHub = findViewById(R.id.btnDeviceHub);
        btnDeviceAccessPoint = findViewById(R.id.btnDeviceAccessPoint);
        btnDeviceModem = findViewById(R.id.btnDeviceModem);
        tvDeviceTitle = findViewById(R.id.tvDeviceTitle);
        tvExplanation = findViewById(R.id.tvExplanation);
        btnBack = findViewById(R.id.btnBack);

        btnDeviceRouter.setOnClickListener(v ->
                showDeviceInfo("Router"));
        btnDeviceSwitch.setOnClickListener(v ->
                showDeviceInfo("Switch"));
        btnDeviceHub.setOnClickListener(v ->
                showDeviceInfo("Hub"));
        btnDeviceAccessPoint.setOnClickListener(v ->
                showDeviceInfo("Access Point"));
        btnDeviceModem.setOnClickListener(v ->
                showDeviceInfo("Modem"));

        btnBack.setOnClickListener(v -> finish());
    }

    private void showDeviceInfo(String device) {
        tvDeviceTitle.setText("📡 " + device);
        tvExplanation.setText(getDeviceExplanation(device));
    }

    private String getDeviceExplanation(String device) {
        switch (device) {
            case "Router":
                return "🔴 ROUTER\n\n" +
                        "A router is a networking device that connects multiple " +
                        "networks together, such as linking a home or office " +
                        "network to the internet.\n\n" +
                        "📌 Key Functions:\n" +
                        "• Directs data packets between networks\n" +
                        "• Assigns IP addresses using DHCP\n" +
                        "• Operates at Layer 3 (Network Layer) of OSI model\n" +
                        "• Provides firewall and security features\n" +
                        "• Supports both wired and wireless connections\n\n" +
                        "💡 Example: The WiFi device at your home that " +
                        "connects all your devices to the internet is a router!";

            case "Switch":
                return "🔵 SWITCH\n\n" +
                        "A switch is a networking device that connects multiple " +
                        "devices within a Local Area Network (LAN).\n\n" +
                        "📌 Key Functions:\n" +
                        "• Uses MAC addresses to forward data\n" +
                        "• Operates at Layer 2 (Data Link Layer) of OSI model\n" +
                        "• Sends data only to the intended device\n" +
                        "• Supports VLAN configuration\n" +
                        "• More efficient than a hub\n\n" +
                        "💡 Example: In a computer lab, a switch connects " +
                        "all computers together so they can communicate!";

            case "Hub":
                return "🟡 HUB\n\n" +
                        "A hub is a basic networking device that connects " +
                        "multiple devices in a network. It is now largely " +
                        "outdated and replaced by switches.\n\n" +
                        "📌 Key Functions:\n" +
                        "• Broadcasts data to ALL connected devices\n" +
                        "• Operates at Layer 1 (Physical Layer) of OSI model\n" +
                        "• Does not filter or direct traffic\n" +
                        "• Can cause data collisions\n" +
                        "• Simple and cheap but inefficient\n\n" +
                        "💡 Example: An old office network might use a hub " +
                        "to connect computers, but this causes slowdowns!";

            case "Access Point":
                return "📶 ACCESS POINT\n\n" +
                        "A wireless access point (WAP) is a device that " +
                        "creates a wireless local area network (WLAN).\n\n" +
                        "📌 Key Functions:\n" +
                        "• Provides WiFi connectivity to devices\n" +
                        "• Extends wireless network coverage\n" +
                        "• Connects wireless devices to wired network\n" +
                        "• Supports multiple simultaneous connections\n" +
                        "• Can be managed centrally\n\n" +
                        "💡 Example: The WiFi hotspot in a university " +
                        "library is provided by multiple access points!";

            case "Modem":
                return "🌐 MODEM\n\n" +
                        "A modem (Modulator-Demodulator) is a device that " +
                        "converts digital signals to analog and vice versa " +
                        "for internet connectivity.\n\n" +
                        "📌 Key Functions:\n" +
                        "• Converts digital data to analog signals\n" +
                        "• Connects your network to ISP\n" +
                        "• Enables internet access\n" +
                        "• Works with DSL, cable, or fiber connections\n" +
                        "• Often combined with router in home devices\n\n" +
                        "💡 Example: The device provided by your internet " +
                        "service provider (ISP) is typically a modem!";

            default:
                return "Select a device to learn about it!";
        }
    }
}