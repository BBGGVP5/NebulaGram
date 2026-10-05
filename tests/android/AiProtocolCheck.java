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
        for (int provider : new int[]{5, 6}) {
            responses.add(fixtures[provider == 5 ? 3 : 0]);
            check(new NebulaAiClient().generate(provider, "", "test-key", "model", "prompt", "input").equals("Привет 🌍"), "new provider response");
            Fake request = requests.get(requests.size() - 1);
            check(request.getURL().getHost().equals(provider == 5 ? "openrouter.ai" : "api.perplexity.ai"), "provider host");
            check(request.getURL().getPath().equals(provider == 5 ? "/api/v1/chat/completions" : "/v1/agent"), "provider endpoint");
            check(request.json().has(provider == 5 ? "messages" : "input"), "provider payload");
        }
        String[][] events = {
            {"{\"type\":\"response.output_text.delta\",\"delta\":\"Привет 🌍\"}", "{\"type\":\"response.completed\"}"},
            {"{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"hidden\"}}", "{\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"Привет 🌍\"}}", "{\"type\":\"message_stop\"}"},
            {"{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Привет\"}]}}]}", "{\"candidates\":[{\"content\":{\"parts\":[{\"thought\":true,\"text\":\"hidden\"},{\"text\":\" 🌍\"}]},\"finishReason\":\"STOP\"}]}"},
            {"{\"choices\":[{\"delta\":{\"reasoning\":\"hidden\",\"content\":\"Привет 🌍\"}}]}", "{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}"}
        };
        for (int provider : new int[]{0, 1, 2, 3, 5, 6}) {
            StringBuilder sse = new StringBuilder(": keepalive\r\n\r\n");
            for (String event : events[provider == 5 ? 3 : provider == 6 ? 0 : provider]) sse.append("event: ignored\r\ndata: ").append(event).append("\r\n\r\n");
            byte[] fragmented = sse.toString().getBytes(StandardCharsets.UTF_8);
            InputStream oneByte = new ByteArrayInputStream(fragmented) { @Override public synchronized int read(byte[] b, int off, int len) { return super.read(b, off, Math.min(1, len)); } };
            ArrayList<String> progress = new ArrayList<>();
            check(NebulaAiClient.readStream(provider, oneByte, () -> false, progress::add).equals("Привет 🌍"), "fragmented SSE / thought exclusion / spaces");
            check(!progress.isEmpty() && !progress.toString().contains("hidden"), "real progress");
            responses.add(sse.toString());
            check(new NebulaAiClient().generate(provider, "https://example.test/v1", "test-key", "model", "", "hello", new NebulaAiClient.Options(true, false, .4), progress::add).equals("Привет 🌍"), "SSE transport");
            Fake request = requests.get(requests.size() - 1);
            check("text/event-stream".equals(request.getRequestProperty("Accept")), "stream accept");
            check(provider == 2 ? request.getURL().getQuery().equals("alt=sse") : request.json().getBoolean("stream"), "stream opt-in body");
        }
        for (String event : new String[]{"{\"type\":\"response.failed\"}", "{\"error\":{\"message\":\"private error\"}}", "{\"type\":\"response.output_text.delta\",\"delta\":\"partial\"}"}) {
            try { NebulaAiClient.readStream(0, new ByteArrayInputStream(("data: " + event + "\n\n").getBytes(StandardCharsets.UTF_8)), () -> false, null); throw new AssertionError("unfinished/error stream accepted"); }
            catch (IOException expected) { check(!expected.getMessage().contains("private error"), "safe stream errors"); }
        }
        try { NebulaAiClient.readStream(0, new ByteArrayInputStream("data: {}\n\n".getBytes()), () -> true, null); throw new AssertionError("canceled stream accepted"); } catch (InterruptedIOException expected) { }
        check(!NebulaAiClient.generationOptions(0, "gpt-5-model", new JSONObject(), new NebulaAiClient.Options(false, true, 1)).has("temperature"), "reasoning models omit unsupported temperature");
        for (String url : Arrays.asList("http://example.test", "https://user:password@example.test", "https://example.test?key=secret", "https://example.test#fragment")) {
            try { NebulaAiClient.base(3, url); throw new AssertionError("unsafe base accepted"); } catch (IOException expected) { }
        }
        File audio = File.createTempFile("nebula-audio-test", ".ogg");
        try {
            byte[] bytes = new byte[]{'O','g','g','S',0,1,2,3};
            java.nio.file.Files.write(audio.toPath(), bytes);
            responses.add("{\"outputs\":[{\"type\":\"text\",\"text\":\"Transcript\"}]}");
            check("Transcript".equals(new NebulaAiClient().transcribe(2,"","test-key","models/audio-model",audio,"audio/ogg")), "transcript output");
            Fake upload = requests.get(requests.size()-1);
            check(upload.getURL().getPath().endsWith("/interactions"), "transcription endpoint");
            check(!upload.json().getBoolean("store"), "transcription stored");
            JSONObject part = upload.json().getJSONArray("input").getJSONObject(1);
            check("audio".equals(part.getString("type")), "audio input type");
            check("audio/ogg".equals(part.getString("mime_type")), "audio MIME");
            check(Arrays.equals(bytes, Base64.getDecoder().decode(part.getString("data"))), "upload integrity");
            int count = requests.size();
            NebulaAiClient cancelledAudio = new NebulaAiClient(); cancelledAudio.cancel();
            try { cancelledAudio.transcribe(2,"","test-key","audio-model",audio,"audio/ogg"); throw new AssertionError("cancelled audio upload"); } catch (InterruptedIOException expected) { }
            check(requests.size()==count, "cancelled audio must not connect");
            try { new NebulaAiClient().transcribe(0,"","test-key","audio-model",audio,"audio/ogg"); throw new AssertionError("unsupported audio provider"); } catch (IOException expected) { }
            try(RandomAccessFile oversized=new RandomAccessFile(audio,"rw")){oversized.setLength(14_000_001);}
            try { new NebulaAiClient().transcribe(2,"","test-key","audio-model",audio,"audio/ogg"); throw new AssertionError("oversized audio accepted"); } catch (IOException expected) { }
            check(requests.size()==count, "invalid audio must not connect");
        } finally { audio.delete(); }
        status = 307;
        try { new NebulaAiClient().models(0, "", "test-key"); throw new AssertionError("redirect accepted"); } catch (IOException expected) { check(expected.getMessage().equals("HTTP 307"), "safe error message"); }
        NebulaAiClient cancelled = new NebulaAiClient(); cancelled.cancel();
        int before = requests.size();
        try { cancelled.models(0, "", "test-key"); throw new AssertionError("cancelled request started"); } catch (InterruptedIOException expected) { }
        check(requests.size() == before, "cancel should not connect");
        try { cancelled.generate(NebulaAiClient.NANO, "", "", "", "translate", "hello"); throw new AssertionError("cancelled Nano request started"); } catch (InterruptedIOException expected) { }
        check("hello".equals(new NebulaAiClient().generate(NebulaAiClient.NANO, "", "", "", "translate", "hello")), "Nano path delegates locally");
        check(requests.size() == before, "Nano never falls back to a network provider");
        check(NebulaSettingsSchema.types.containsKey("bottom_bar_settings"), "tab visibility portable");
        for (String key : NebulaSettingsSchema.types.keySet()) check(!key.contains("secret") && !key.contains("proxy") && !key.contains("api") && !key.contains("token"), "secret in export allowlist");
        System.out.println("AI protocol checks passed: 6 remote providers, UTF-8, models, pagination, refusals, cancellation, redirects and export isolation");
    }
}
