package com.jima.ai.core.overlay;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import com.jima.ai.R;
import com.jima.ai.core.assistant.JimaStateManager;
import com.jima.ai.core.brain.JimaCommandExecutor;
import com.jima.ai.core.voice.JimaVoiceEngine;

public class JimaOverlayService extends Service implements JimaStateManager.StateListener {

    private static final String CHANNEL_ID = "jima_orb_channel";
    private static final int NOTIF_ID = 101;

    private WindowManager windowManager;
    private View overlayView;
    private View siriOrbView;
    private TextView orbStatusText;
    private ObjectAnimator pulseAnimator;
    private JimaVoiceEngine voiceEngine;
    private JimaCommandExecutor commandExecutor;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        commandExecutor = new JimaCommandExecutor(this);
        JimaStateManager.getInstance().addListener(this);

        voiceEngine = new JimaVoiceEngine(this, new JimaVoiceEngine.VoiceCallback() {
            @Override
            public void onPartialSpeech(String partialText) {
                if (orbStatusText != null && !partialText.isEmpty()) {
                    orbStatusText.setText(partialText);
                }
            }

            @Override
            public void onFinalCommand(String commandText) {
                JimaStateManager.getInstance().setPendingCommand(commandText);
                commandExecutor.executeVoiceCommand(commandText);
            }

            @Override
            public void onStopRequested() {
                dismissOrbSmoothly();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showFloatingOrb();
        voiceEngine.startContinuousListening();
        return START_NOT_STICKY;
    }

    private void showFloatingOrb() {
        if (overlayView != null) {
            return;
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.layout_floating_orb, null);
        siriOrbView = overlayView.findViewById(R.id.siri_orb_view);
        orbStatusText = overlayView.findViewById(R.id.orb_status_text);

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = 48;

        overlayView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                dismissOrbSmoothly();
                return true;
            }
            return false;
        });

        siriOrbView.setOnClickListener(v -> dismissOrbSmoothly());

        windowManager.addView(overlayView, params);
        startSiriPulseAnimation();
    }

    private void startSiriPulseAnimation() {
        if (siriOrbView == null) return;
        PropertyValuesHolder scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.22f);
        PropertyValuesHolder scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.22f);
        PropertyValuesHolder alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 0.85f, 1.0f);

        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(siriOrbView, scaleX, scaleY, alpha);
        pulseAnimator.setDuration(850);
        pulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ObjectAnimator.REVERSE);
        pulseAnimator.start();
    }

    private void dismissOrbSmoothly() {
        voiceEngine.stopListening();
        JimaStateManager.getInstance().setState(
                JimaStateManager.State.SLEEPING,
                "Jima is sleeping"
        );

        if (overlayView != null) {
            overlayView.animate()
                    .translationY(300f)
                    .alpha(0f)
                    .setDuration(250)
                    .withEndAction(() -> {
                        removeOverlayView();
                        stopSelf();
                    })
                    .start();
        } else {
            stopSelf();
        }
    }

    private void removeOverlayView() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
        }
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {}
            overlayView = null;
        }
    }

    @Override
    public void onStateChanged(JimaStateManager.State newState, String message) {
        if (orbStatusText != null) {
            orbStatusText.setText(message);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Jima Active Orb",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Jima AI Active")
                .setContentText("Listening for voice commands...")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    @Override
    public void onDestroy() {
        JimaStateManager.getInstance().removeListener(this);
        if (voiceEngine != null) {
            voiceEngine.destroy();
        }
        removeOverlayView();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
