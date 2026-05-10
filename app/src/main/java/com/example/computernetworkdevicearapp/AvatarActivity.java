package com.example.computernetworkdevicearapp;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

public class AvatarActivity extends AppCompatActivity {

    private static final String TAG = "AvatarActivity";

    // AR
    private ArFragment arFragment;
    private AnchorNode anchorNode;
    private boolean    modelPlaced = false;

    // UI
    private Button   btnBack, btnDeviceRouter, btnDeviceSwitch, btnPlaceAvatar;
    private TextView tvDeviceTitle, tvExplanation;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar);

        bindViews();
        setupArFragment();   // ← session config lives here
        setupButtons();
    }

    // -------------------------------------------------------------------------
    // View binding
    // -------------------------------------------------------------------------

    private void bindViews() {
        tvDeviceTitle   = findViewById(R.id.tvDeviceTitle);
        tvExplanation   = findViewById(R.id.tvExplanation);
        btnBack         = findViewById(R.id.btnBack);
        btnDeviceRouter = findViewById(R.id.btnDeviceRouter);
        btnDeviceSwitch = findViewById(R.id.btnDeviceSwitch);
        btnPlaceAvatar  = findViewById(R.id.btnPlaceAvatar);
    }

    // -------------------------------------------------------------------------
    // AR Fragment setup — includes session config fix for Samsung Android 16
    // -------------------------------------------------------------------------

    private void setupArFragment() {
        arFragment = (ArFragment) getSupportFragmentManager()
                .findFragmentById(R.id.arFragment);

        if (arFragment == null) {
            Toast.makeText(this, "AR Fragment not found in layout",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // ✅ FIX: setOnSessionConfigurationListener fires just before ARCore
        //         creates its session — the safest place to apply Config changes.
        //         Disabling depth + using AMBIENT_INTENSITY prevents the black
        //         screen / FatalException on Samsung Galaxy devices with Android 16.
        arFragment.setOnSessionConfigurationListener((session, config) -> {
            config.setDepthMode(Config.DepthMode.DISABLED);
            config.setLightEstimationMode(Config.LightEstimationMode.AMBIENT_INTENSITY);
            config.setFocusMode(Config.FocusMode.AUTO);
            config.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
        });

        // Tap a detected plane → place avatar there
        arFragment.setOnTapArPlaneListener((hitResult, plane, motionEvent) -> {
            if (modelPlaced) return;
            placeAvatarModel(hitResult.createAnchor());
        });
    }

    // -------------------------------------------------------------------------
    // Place avatar model at the tapped AR anchor
    // -------------------------------------------------------------------------

    private void placeAvatarModel(Anchor anchor) {
        ModelRenderable.builder()
                .setSource(this, Uri.parse("models/avatar.glb"))
                .setIsFilamentGltf(true)
                .setAsyncLoadEnabled(true)
                .build()
                .thenAccept(renderable -> {
                    addToScene(anchor, renderable);
                    modelPlaced = true;
                    runOnUiThread(() ->
                            Toast.makeText(this, "Avatar placed!",
                                    Toast.LENGTH_SHORT).show());
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Avatar load failed", throwable);
                    runOnUiThread(() ->
                            Toast.makeText(this,
                                    "Error loading avatar: " + throwable.getMessage(),
                                    Toast.LENGTH_LONG).show());
                    return null;
                });
    }

    private void addToScene(Anchor anchor, ModelRenderable renderable) {
        ArSceneView sceneView = arFragment.getArSceneView();

        if (anchorNode != null) anchorNode.setParent(null);

        anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(sceneView.getScene());

        TransformableNode node = new TransformableNode(
                arFragment.getTransformationSystem());
        node.setParent(anchorNode);
        node.setRenderable(renderable);
        node.select();
    }

    // -------------------------------------------------------------------------
    // Buttons
    // -------------------------------------------------------------------------

    private void setupButtons() {

        btnPlaceAvatar.setOnClickListener(v -> {
            if (!modelPlaced) {
                Toast.makeText(this,
                        "Tap a flat surface on screen to place the avatar.",
                        Toast.LENGTH_SHORT).show();
            } else {
                // Allow repositioning
                modelPlaced = false;
                Toast.makeText(this,
                        "Tap a surface to reposition the avatar.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        btnDeviceRouter.setOnClickListener(v ->
                showDeviceInfo("Router",
                        "A router forwards data packets between networks "
                                + "using IP addresses. It operates at Layer 3 of the OSI model."));

        btnDeviceSwitch.setOnClickListener(v ->
                showDeviceInfo("Switch",
                        "A switch connects LAN devices using MAC addresses. "
                                + "It operates at Layer 2 of the OSI model."));

        btnBack.setOnClickListener(v -> finish());
    }

    // -------------------------------------------------------------------------
    // Device info panel
    // -------------------------------------------------------------------------

    private void showDeviceInfo(String title, String explanation) {
        tvDeviceTitle.setText(title);
        tvExplanation.setText(explanation);
    }
}