package com.jima.ai.core.brain;

import android.content.Context;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jima.ai.core.assistant.JimaStateManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiBrain {

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;

    public interface BrainCallback {
        void onActionsReady(JSONArray stepsArray, String spokenSummary);
        void onError(String error);
    }

    public GeminiBrain() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    public void planCrowWork(Context context, String userCommand, String screenDump, BrainCallback callback) {
        String apiKey = JimaStateManager.getInstance().getApiKey(context);
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("Missing API Key! Open Jima App & save your Gemini key.");
            return;
        }

        String model = JimaStateManager.getInstance().getModelName(context);
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        try {
            String systemPrompt = "You are Jima, an elite Android UI Automation ('Crow Work') AI. "
                    + "Convert the user's voice command into a JSON object containing a 'summary' string and a 'steps' array.\n"
                    + "Allowed step actions:\n"
                    + "- OPEN_APP: {\"action\":\"OPEN_APP\", \"target\":\"YouTube\"}\n"
                    + "- CLICK: {\"action\":\"CLICK\", \"target\":\"Search\"}\n"
                    + "- TAP_XY: {\"action\":\"TAP_XY\", \"x\":540, \"y\":120}\n"
                    + "- TYPE: {\"action\":\"TYPE\", \"target\":\"Search\", \"value\":\"Arijit Singh songs\"}\n"
                    + "- SCROLL: {\"action\":\"SCROLL\", \"value\":\"DOWN\"} (or UP/LEFT/RIGHT)\n"
                    + "- BACK: {\"action\":\"BACK\"}\n"
                    + "- HOME: {\"action\":\"HOME\"}\n"
                    + "- WAIT: {\"action\":\"WAIT\", \"value\":\"1500\"}\n\n"
                    + "Current Screen Context:\n" + screenDump + "\n\n"
                    + "User Command: \"" + userCommand + "\"\n\n"
                    + "Respond ONLY with valid raw JSON in this exact format (no markdown blocks):\n"
                    + "{\"summary\":\"Opening YouTube and searching\",\"steps\":[{\"action\":\"OPEN_APP\",\"target\":\"YouTube\"}]}";

            JSONObject partObj = new JSONObject();
            partObj.put("text", systemPrompt);

            JSONArray partsArray = new JSONArray();
            partsArray.put(partObj);

            JSONObject contentObj = new JSONObject();
            contentObj.put("parts", partsArray);

            JSONArray contentsArray = new JSONArray();
            contentsArray.put(contentObj);

            JSONObject rootBody = new JSONObject();
            rootBody.put("contents", contentsArray);

            RequestBody body = RequestBody.create(rootBody.toString(), JSON_MEDIA);
            Request request = new Request.Builder().url(url).post(body).build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Network Error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful() || response.body() == null) {
                        callback.onError("Gemini HTTP " + response.code());
                        return;
                    }
                    String rawResp = response.body().string();
                    try {
                        JsonObject root = JsonParser.parseString(rawResp).getAsJsonObject();
                        String aiText = root.getAsJsonArray("candidates")
                                .get(0).getAsJsonObject()
                                .getAsJsonObject("content")
                                .getAsJsonArray("parts")
                                .get(0).getAsJsonObject()
                                .get("text").getAsString().trim();

                        // Strip markdown code fences if present
                        if (aiText.startsWith("```")) {
                            aiText = aiText.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
                        }

                        JSONObject parsedPlan = new JSONObject(aiText);
                        String summary = parsedPlan.optString("summary", "Executing command...");
                        JSONArray steps = parsedPlan.optJSONArray("steps");
                        if (steps == null) {
                            steps = new JSONArray();
                        }
                        callback.onActionsReady(steps, summary);
                    } catch (Exception e) {
                        callback.onError("Plan Parse Error: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("Request Error: " + e.getMessage());
        }
    }
}

