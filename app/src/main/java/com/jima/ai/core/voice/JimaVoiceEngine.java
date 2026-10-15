package com.jima.ai.core.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import com.jima.ai.core.assistant.JimaStateManager;

import java.util.ArrayList;
import java.util.Locale;

public class JimaVoiceEngine {

    public interface VoiceCallback {
        void onPartialSpeech(String partialText);
        void onFinalCommand(String commandText);
        void onStopRequested();
    }

    private final Context context;
    private final VoiceCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isContinuousLoopActive = false;

    public JimaVoiceEngine(Context context, VoiceCallback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
        initRecognizer();
    }

    private void initRecognizer() {
        mainHandler.post(() -> {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            );
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    JimaStateManager.getInstance().setState(
                            JimaStateManager.State.LISTENING,
                            "Jima listening..."
                    );
                }

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {}

                @Override
                public void onError(int error) {
                    if (isContinuousLoopActive && JimaStateManager.getInstance().isAwake()) {
                        mainHandler.postDelayed(() -> startListeningCycle(), 350);
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                    );
                    if (matches != null && !matches.isEmpty()) {
                        String spoken = matches.get(0).trim();
                        if (isStopWord(spoken)) {
                            stopListening();
                            if (callback != null) {
                                callback.onStopRequested();
                            }
                            return;
                        }
                        if (callback != null && !spoken.isEmpty()) {
                            callback.onFinalCommand(spoken);
                        }
                    }
                    if (isContinuousLoopActive && JimaStateManager.getInstance().isAwake()) {
                        mainHandler.postDelayed(() -> startListeningCycle(), 400);
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    ArrayList<String> partial = partialResults.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                    );
                    if (partial != null && !partial.isEmpty() && callback != null) {
                        callback.onPartialSpeech(partial.get(0));
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        });
    }

    private boolean isStopWord(String text) {
        String lower = text.toLowerCase(Locale.ROOT).trim();
        return lower.equals("stop")
                || lower.equals("jima stop")
                || lower.equals("cancel")
                || lower.equals("band karo")
                || lower.equals("ruk jao");
    }

    public void startContinuousListening() {
        isContinuousLoopActive = true;
        startListeningCycle();
    }

    private void startListeningCycle() {
        mainHandler.post(() -> {
            if (!isContinuousLoopActive || !JimaStateManager.getInstance().isAwake()) {
                return;
            }
            try {
                speechRecognizer.startListening(recognizerIntent);
            } catch (Exception e) {
                initRecognizer();
            }
        });
    }

    public void stopListening() {
        isContinuousLoopActive = false;
        mainHandler.post(() -> {
            try {
                if (speechRecognizer != null) {
                    speechRecognizer.stopListening();
                    speechRecognizer.cancel();
                }
            } catch (Exception ignored) {}
        });
    }

    public void destroy() {
        stopListening();
        mainHandler.post(() -> {
            try {
                if (speechRecognizer != null) {
                    speechRecognizer.destroy();
                    speechRecognizer = null;
                }
            } catch (Exception ignored) {}
        });
    }
}

