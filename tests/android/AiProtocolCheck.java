import app.nebulagram.ui.NebulaAiClient;
import app.nebulagram.ui.NebulaSettingsSchema;
import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AiProtocolCheck {
    static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    static final ArrayDeque<String> responses = new ArrayDeque<>();
    static final ArrayList<Fake> requests = new ArrayList<>();
    static int status = 200;
    static class Fake extends HttpURLConnection {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        Fake(URL u) { super(u); requests.add(this); }
        public void connect() { }
        public void disconnect() { }
        public boolean usingProxy() { return false; }
        public OutputStream getOutputStream() { return body; }
        public InputStream getInputStream() { return new ByteArrayInputStream(responses.remove().getBytes(StandardCharsets.UTF_8)); }
        public int getResponseCode() { return status; }
        JSONObject json() { return new JSONObject(body.toString(StandardCharsets.UTF_8)); }
    }
    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> !protocol.equals("https") ? null : new URLStreamHandler() {
            protected URLConnection openConnection(URL url) { return new Fake(url); }
        });
        String[] fixtures = {
            "{\"output\":[{\"type\":\"reasoning\",\"summary\":[]},{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Привет 🌍\"}]}]}",
            "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"private\"},{\"type\":\"text\",\"text\":\"Привет 🌍\"}]}",
            "{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"private\"},{\"text\":\"Привет 🌍\"}]}}]}",
            "{\"choices\":[{\"message\":{\"content\":\"Привет 🌍\"}}]}"
        };
        for (int provider = 0; provider < 4; provider++) {
            responses.add(fixtures[provider]);
            String output = new NebulaAiClient().generate(provider, "https://example.test/v1/", "test-key", "model", "Промпт", "Сообщение 🐈");
            check(output.equals("Привет 🌍"), "UTF-8 response or reasoning exclusion");
            Fake request = requests.get(requests.size()-1);
            check(!request.getInstanceFollowRedirects(), "credentials must not follow redirects");
            check(request.body.toString(StandardCharsets.UTF_8).contains("Сообщение 🐈"), "UTF-8 body");
            check(!request.getURL().toString().contains("test-key"), "key in URL");
            if (provider == 0) { check(!request.json().getBoolean("store"), "stored request"); check(request.getURL().getPath().endsWith("/responses"), "OpenAI endpoint"); }
            if (provider == 1) { check(request.json().getInt("max_tokens") > 0, "Claude max_tokens"); check("2023-06-01".equals(request.getRequestProperty("anthropic-version")), "Claude version"); }
            if (provider == 2) { check(request.json().has("systemInstruction"), "Gemini system prompt"); check("test-key".equals(request.getRequestProperty("x-goog-api-key")), "Gemini key header"); }
            if (provider == 3) check(request.json().getJSONArray("messages").getJSONObject(0).getString("role").equals("system"), "custom system prompt");
        }
        responses.add("{\"models\":[{\"name\":\"models/embed\",\"supportedGenerationMethods\":[\"embedContent\"]},{\"name\":\"models/text-a\",\"supportedGenerationMethods\":[\"generateContent\"]}],\"nextPageToken\":\"token +\"}");
        responses.add("{\"models\":[{\"name\":\"models/text-b\",\"supportedGenerationMethods\":[\"generateContent\"]}]}");
        check(new NebulaAiClient().models(2, "", "test-key").equals(Arrays.asList("text-a", "text-b")), "pagination/filtering");
        check(requests.get(requests.size()-1).getURL().getQuery().contains("token+%2B"), "encoded page token");
        check(NebulaAiClient.output(0, new JSONObject("{\"output\":[{\"content\":[{\"refusal\":\"Declined\"}]}]}")).equals("Declined"), "refusal handling");
        for (String url : Arrays.asList("http://example.test", "https://user:password@example.test", "https://example.test?key=secret", "https://example.test#fragment")) {
            try { NebulaAiClient.base(3, url); throw new AssertionError("unsafe base accepted"); } catch (IOException expected) { }
        }
        status = 307;
        try { new NebulaAiClient().models(0, "", "test-key"); throw new AssertionError("redirect accepted"); } catch (IOException expected) { check(expected.getMessage().equals("HTTP 307"), "safe error message"); }
        NebulaAiClient cancelled = new NebulaAiClient(); cancelled.cancel();
        int before = requests.size();
        try { cancelled.models(0, "", "test-key"); throw new AssertionError("cancelled request started"); } catch (InterruptedIOException expected) { }
        check(requests.size() == before, "cancel should not connect");
        check(NebulaSettingsSchema.types.containsKey("bottom_bar_settings"), "tab visibility portable");
        for (String key : NebulaSettingsSchema.types.keySet()) check(!key.contains("secret") && !key.contains("proxy") && !key.contains("api") && !key.contains("token"), "secret in export allowlist");
        System.out.println("AI protocol checks passed: 4 providers, UTF-8, models, pagination, refusals, cancellation, redirects and export isolation");
    }
}
