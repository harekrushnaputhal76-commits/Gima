package com.jima.ai.core.assistant;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.jima.ai.R;
import com.jima.ai.core.overlay.JimaOverlayService;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_RECORD_AUDIO = 1001;
    private TextView tvSystemStatus;
    private EditText etGeminiApiKey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvSystemStatus = findViewById(R.id.tv_system_status);
        etGeminiApiKey = findViewById(R.id.et_gemini_api_key);
        Button btnSaveKey = findViewById(R.id.btn_save_key);
        Button btnPermOverlay = findViewById(R.id.btn_perm_overlay);
        Button btnPermAccessibility = findViewById(R.id.btn_perm_accessibility);
        Button btnPermAssistant = findViewById(R.id.btn_perm_assistant);
        Button btnLaunchOrb = findViewById(R.id.btn_launch_orb);

        etGeminiApiKey.setText(JimaStateManager.getInstance().getApiKey(this));
        checkMicPermission();

        btnSaveKey.setOnClickListener(v -> {
            String key = etGeminiApiKey.getText().toString().trim();
            JimaStateManager.getInstance().saveApiKey(this, key);
            Toast.makeText(this, "API Key Saved", Toast.LENGTH_SHORT).show();
            updateStatusText();
        });

        btnPermOverlay.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
            } else {
                Toast.makeText(this, "Overlay Permission Already Granted", Toast.LENGTH_SHORT).show();
            }
        });

        btnPermAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        btnPermAssistant.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS);
            startActivity(intent);
        });

        btnLaunchOrb.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant Overlay Permission first", Toast.LENGTH_SHORT).show();
                return;
            }
            launchFloatingOrb();
        });

        if (Intent.ACTION_ASSIST.equals(getIntent().getAction())) {
            launchFloatingOrb();
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusText();
    }

    private void checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_RECORD_AUDIO
            );
        }
    }

    private void launchFloatingOrb() {
        JimaStateManager.getInstance().setState(
                JimaStateManager.State.LISTENING,
                "Jima listening..."
        );
        Intent serviceIntent = new Intent(this, JimaOverlayService.class);
        serviceIntent.setAction("ACTION_START_ORB");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        moveTaskToBack(true);
    }

    private void updateStatusText() {
        boolean hasOverlay = Settings.canDrawOverlays(this);
        boolean hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        boolean hasKey = !JimaStateManager.getInstance().getApiKey(this).isEmpty();

        String status = "Overlay: " + (hasOverlay ? "OK" : "Missing")
                + " | Mic: " + (hasMic ? "OK" : "Missing")
                + " | API Key: " + (hasKey ? "Set" : "Empty");
        tvSystemStatus.setText(status);
    }
}

