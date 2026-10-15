package com.jima.ai.core.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

public class JimaStateManager {

    public enum State {
        SLEEPING,
        LISTENING,
        PROCESSING,
        EXECUTING
    }

    private static final String PREF_NAME = "jima_core_prefs";
    private static final String KEY_GEMINI_API = "gemini_api_key";
    private static final String KEY_MODEL_NAME = "gemini_model_name";

    private static volatile JimaStateManager instance;
    private volatile State currentState = State.SLEEPING;
    private volatile String statusMessage = "Jima is sleeping";
    private volatile String activeTargetPackage = "";
    private volatile String pendingCommand = "";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<StateListener> listeners = new ArrayList<>();

    public interface StateListener {
        void onStateChanged(State newState, String message);
    }

    private JimaStateManager() {}

    public static JimaStateManager getInstance() {
        if (instance == null) {
            synchronized (JimaStateManager.class) {
                if (instance == null) {
                    instance = new JimaStateManager();
                }
            }
        }
        return instance;
    }

    public boolean isAwake() {
        return currentState != State.SLEEPING;
    }

    public State getCurrentState() {
        return currentState;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public synchronized void setState(final State newState, final String message) {
        this.currentState = newState;
        this.statusMessage = message;
        mainHandler.post(() -> {
            for (StateListener listener : new ArrayList<>(listeners)) {
                listener.onStateChanged(newState, message);
            }
        });
    }

    public synchronized void addListener(StateListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public synchronized void removeListener(StateListener listener) {
        listeners.remove(listener);
    }

    public String getActiveTargetPackage() {
        return activeTargetPackage;
    }

    public void setActiveTargetPackage(String packageName) {
        if (packageName != null && !packageName.equals("com.jima.ai")) {
            this.activeTargetPackage = packageName;
        }
    }

    public String getPendingCommand() {
        return pendingCommand;
    }

    public void setPendingCommand(String command) {
        this.pendingCommand = command != null ? command : "";
    }

    public void saveApiKey(Context context, String apiKey) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_GEMINI_API, apiKey != null ? apiKey.trim() : "").apply();
    }

    public String getApiKey(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_GEMINI_API, "");
    }

    public void saveModelName(Context context, String modelName) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_MODEL_NAME, modelName.trim()).apply();
    }

    public String getModelName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_MODEL_NAME, "gemini-2.5-flash");
    }
}
