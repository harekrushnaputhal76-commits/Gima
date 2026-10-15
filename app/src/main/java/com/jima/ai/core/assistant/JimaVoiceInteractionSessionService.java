package com.jima.ai.core.assistant;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

/**
 * Triggered when the user invokes the default assistant gesture.
 * Launches the floating Siri Orb overlay without pausing background apps like YouTube.
 */
public class JimaVoiceInteractionSessionService extends VoiceInteractionSessionService {

    @Override
    public VoiceInteractionSession onNewSession(Bundle args) {
        return new JimaSession(this);
    }

    private static class JimaSession extends VoiceInteractionSession {

        public JimaSession(Context context) {
            super(context);
        }

        @Override
        public void onShow(Bundle args, int showFlags) {
            super.onShow(args, showFlags);

            // Wake up Jima State Manager
            JimaStateManager.getInstance().setState(
                    JimaStateManager.State.LISTENING,
                    "Jima listening..."
            );

            // Start the Floating Siri Orb Overlay Service
            Context ctx = getContext();
            Intent overlayIntent = new Intent();
            overlayIntent.setClassName(ctx.getPackageName(), "com.jima.ai.core.overlay.JimaOverlayService");
            overlayIntent.setAction("ACTION_START_ORB");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(overlayIntent);
            } else {
                ctx.startService(overlayIntent);
            }

            // Immediately hide the system full-screen window so YouTube/Instagram stays touchable
            hide();
        }
    }
}

