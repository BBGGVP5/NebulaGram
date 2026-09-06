package app.nebulagram.ui;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Small REST transport. No retries, automatic chat access, logging or persistent conversations. */
public final class NebulaAiClient {
    public static final int OPENAI = 0, CLAUDE = 1, GEMINI = 2, CUSTOM = 3;
    private volatile HttpURLConnection connection;
    private volatile boolean cancelled;
    public void cancel() { cancelled = true; HttpURLConnection c = connection; if (c != null) c.disconnect(); }
    public static String base(int provider, String custom) throws Exception {
        String base = provider == OPENAI ? "https://api.openai.com/v1" : provider == CLAUDE ? "https://api.anthropic.com/v1"
                : provider == GEMINI ? "https://generativelanguage.googleapis.com/v1beta" : custom.trim();
        URL url = new URL(base);
        if (!"https".equals(url.getProtocol()) || url.getHost().isEmpty() || url.getUserInfo() != null || url.getQuery() != null || url.getRef() != null) throw new IOException("HTTPS URL required");
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base;
    }
    public static JSONObject payload(int provider, String model, String prompt, String input) throws JSONException {
        if (provider == GEMINI) {
            JSONObject body = new JSONObject().put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", new JSONArray().put(new JSONObject().put("text", input)))));
            if (!prompt.isEmpty()) body.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", prompt))));
            return body;
        }
        JSONObject body = new JSONObject().put("model", model);
        if (provider == OPENAI) return body.put("input", input).put("instructions", prompt).put("max_output_tokens", 8192).put("store", false);
        JSONArray messages = new JSONArray();
        if (provider == CUSTOM && !prompt.isEmpty()) messages.put(new JSONObject().put("role", "system").put("content", prompt));
        messages.put(new JSONObject().put("role", "user").put("content", input));
        body.put("messages", messages);
        if (provider == CLAUDE) { body.put("max_tokens", 4096); if (!prompt.isEmpty()) body.put("system", prompt); }
        return body;
    }
    public static String output(int provider, JSONObject json) throws JSONException {
        StringBuilder text = new StringBuilder();
        if (provider == CUSTOM) {
            JSONObject message = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
            String content = message.optString("content", "");
            if (!content.equals("null")) text.append(content);
            if (text.length() == 0) text.append(message.optString("refusal", ""));
        } else if (provider == GEMINI) {
            JSONArray candidates = json.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
                if (content != null) append(text, content.optJSONArray("parts"));
            }
        } else if (provider == CLAUDE) append(text, json.optJSONArray("content"));
        else {
            JSONArray items = json.optJSONArray("output");
            for (int i = 0; items != null && i < items.length(); i++) append(text, items.getJSONObject(i).optJSONArray("content"));
        }
        return text.toString().trim();
    }
    private static void append(StringBuilder out, JSONArray parts) throws JSONException {
        for (int i = 0; parts != null && i < parts.length(); i++) {
            JSONObject part = parts.getJSONObject(i);
            if (part.optBoolean("thought")) continue;
            String value = part.optString("text", part.optString("refusal", ""));
            if (!value.isEmpty()) { if (out.length() > 0) out.append('\n'); out.append(value); }
        }
    }
    public String generate(int provider, String custom, String key, String model, String prompt, String input) throws Exception {
        if (key.isEmpty() || model.trim().isEmpty() || input.trim().isEmpty()) throw new IOException("Key, model and text required");
        if (input.length() > 50000 || prompt.length() > 20000) throw new IOException("Text too long");
        String modelName = model.startsWith("models/") ? model.substring(7) : model;
        String endpoint = provider == OPENAI ? "/responses" : provider == CLAUDE ? "/messages" : provider == GEMINI
                ? "/models/" + URLEncoder.encode(modelName, "UTF-8") + ":generateContent" : "/chat/completions";
        return output(provider, request(provider, base(provider, custom) + endpoint, key, payload(provider, model, prompt, input)));
    }
    public ArrayList<String> models(int provider, String custom, String key) throws Exception {
        if (key.isEmpty()) throw new IOException("API key required");
        TreeSet<String> result = new TreeSet<>();
        String next = "";
        for (int page = 0; page < 20; page++) {
            JSONObject json = request(provider, base(provider, custom) + "/models" + next, key, null);
            JSONArray data = json.optJSONArray(provider == GEMINI ? "models" : "data");
            for (int i = 0; data != null && i < data.length(); i++) {
                JSONObject item = data.getJSONObject(i);
                if (provider == GEMINI) {
                    JSONArray methods = item.optJSONArray("supportedGenerationMethods");
                    if (methods == null || !methods.toString().contains("generateContent")) continue;
                }
                String name = item.optString(provider == GEMINI ? "name" : "id");
                if (!name.isEmpty()) result.add(name.startsWith("models/") ? name.substring(7) : name);
            }
            if (provider == GEMINI && !json.optString("nextPageToken").isEmpty()) next = "?pageToken=" + URLEncoder.encode(json.getString("nextPageToken"), "UTF-8");
            else if (provider == CLAUDE && json.optBoolean("has_more") && !json.optString("last_id").isEmpty()) next = "?after_id=" + URLEncoder.encode(json.getString("last_id"), "UTF-8");
            else break;
        }
        return new ArrayList<>(result);
    }
    private JSONObject request(int provider, String url, String key, JSONObject body) throws Exception {
        if (cancelled) throw new InterruptedIOException();
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection(); connection = c;
        try {
            if (cancelled) throw new InterruptedIOException();
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(20000); c.setReadTimeout(120000);
            c.setRequestProperty("Accept", "application/json");
            if (provider == CLAUDE) { c.setRequestProperty("x-api-key", key); c.setRequestProperty("anthropic-version", "2023-06-01"); }
            else if (provider == GEMINI) c.setRequestProperty("x-goog-api-key", key);
            else c.setRequestProperty("Authorization", "Bearer " + key);
            if (body != null) {
                c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8); c.setFixedLengthStreamingMode(bytes.length);
                if (cancelled) throw new InterruptedIOException();
                try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
            }
            if (cancelled) throw new InterruptedIOException();
            int status = c.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("HTTP " + status);
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) != -1) { if (cancelled) throw new InterruptedIOException(); if (out.size() + n > 4000000) throw new IOException("Response too large"); out.write(buf, 0, n); }
                return new JSONObject(new String(out.toByteArray(), StandardCharsets.UTF_8));
            }
        } finally { c.disconnect(); if (connection == c) connection = null; }
    }
}
