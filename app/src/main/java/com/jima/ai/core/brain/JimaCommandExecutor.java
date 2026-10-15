package com.jima.ai.core.brain;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.jima.ai.core.accessibility.JimaAccessibilityService;
import com.jima.ai.core.assistant.JimaStateManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class JimaCommandExecutor {

    private final Context context;
    private final GeminiBrain geminiBrain;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public JimaCommandExecutor(Context context) {
        this.context = context.getApplicationContext();
        this.geminiBrain = new GeminiBrain();
    }

    public void executeVoiceCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.trim().isEmpty()) {
            return;
        }

        String command = rawCommand.trim();
        JimaAccessibilityService accessService = JimaAccessibilityService.getInstance();

        // Fast Offline Instant Reflexes (Zero latency for common actions)
        if (accessService != null && tryFastLocalAction(command, accessService)) {
            return;
        }

        // Otherwise ask Gemini Brain with live Screen Context
        JimaStateManager.getInstance().setState(
                JimaStateManager.State.PROCESSING,
                "Thinking: " + command
        );

        String screenContext = (accessService != null)
                ? accessService.dumpScreenContext()
                : "Accessibility Service not enabled.";

        geminiBrain.planCrowWork(context, command, screenContext, new GeminiBrain.BrainCallback() {
            @Override
            public void onActionsReady(JSONArray stepsArray, String spokenSummary) {
                JimaStateManager.getInstance().setState(
                        JimaStateManager.State.EXECUTING,
                        spokenSummary
                );
                runStepRecursively(stepsArray, 0);
            }

            @Override
            public void onError(String error) {
                JimaStateManager.getInstance().setState(
                        JimaStateManager.State.LISTENING,
                        error
                );
            }
        });
    }

    private boolean tryFastLocalAction(String cmd, JimaAccessibilityService service) {
        String lower = cmd.toLowerCase(Locale.ROOT);

        if (lower.equals("scroll down") || lower.equals("next reel") || lower.equals("next short") || lower.equals("neeche karo")) {
            service.scrollScreen("DOWN");
            JimaStateManager.getInstance().setState(JimaStateManager.State.LISTENING, "Scrolled down");
            return true;
        }
        if (lower.equals("scroll up") || lower.equals("previous reel") || lower.equals("upar karo")) {
            service.scrollScreen("UP");
            JimaStateManager.getInstance().setState(JimaStateManager.State.LISTENING, "Scrolled up");
            return true;
        }
        if (lower.equals("go back") || lower.equals("back") || lower.equals("peeche jao")) {
            service.pressBack();
            JimaStateManager.getInstance().setState(JimaStateManager.State.LISTENING, "Went back");
            return true;
        }
        if (lower.equals("go home") || lower.equals("home screen")) {
            service.pressHome();
            JimaStateManager.getInstance().setState(JimaStateManager.State.LISTENING, "Home screen");
            return true;
        }
        if (lower.startsWith("open ") && !lower.contains(" and ") && !lower.contains(" search ")) {
            String appName = cmd.substring(5).trim();
            if (service.launchAppByNameOrPackage(appName)) {
                JimaStateManager.getInstance().setState(JimaStateManager.State.LISTENING, "Opened " + appName);
                return true;
            }
        }
        return false;
    }

    private void runStepRecursively(final JSONArray steps, final int index) {
        if (!JimaStateManager.getInstance().isAwake() || index >= steps.length()) {
            JimaStateManager.getInstance().setState(
                    JimaStateManager.State.LISTENING,
                    "Jima listening..."
            );
            return;
        }

        mainHandler.post(() -> {
            JSONObject step = steps.optJSONObject(index);
            long delayForNext = 900;

            if (step != null) {
                String action = step.optString("action", "").toUpperCase(Locale.ROOT);
                String target = step.optString("target", "");
                String value = step.optString("value", "");

                JimaAccessibilityService service = JimaAccessibilityService.getInstance();
                if (service != null) {
                    switch (action) {
                        case "OPEN_APP":
                            service.launchAppByNameOrPackage(target);
                            delayForNext = 2200; // Wait for app to load
                            break;
                        case "CLICK":
                            service.clickByTextOrDescription(target);
                            delayForNext = 1000;
                            break;
                        case "TAP_XY":
                            float x = (float) step.optDouble("x", 500);
                            float y = (float) step.optDouble("y", 500);
                            service.clickAtCoordinates(x, y);
                            delayForNext = 900;
                            break;
                        case "TYPE":
                            service.typeIntoField(target, value);
                            delayForNext = 1100;
                            break;
                        case "SCROLL":
                            service.scrollScreen(value.isEmpty() ? target : value);
                            delayForNext = 900;
                            break;
                        case "BACK":
                            service.pressBack();
                            delayForNext = 700;
                            break;
                        case "HOME":
                            service.pressHome();
                            delayForNext = 800;
                            break;
                        case "WAIT":
                            try {
                                delayForNext = Long.parseLong(value);
                            } catch (Exception ignored) {
                                delayForNext = 1500;
                            }
                            break;
                    }
                }
            }

            mainHandler.postDelayed(() -> runStepRecursively(steps, index + 1), delayForNext);
        });
    }
}

