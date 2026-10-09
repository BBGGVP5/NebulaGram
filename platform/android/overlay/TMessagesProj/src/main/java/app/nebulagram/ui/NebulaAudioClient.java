package app.nebulagram.ui;

import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** One captured audio connection. No redirects, retries or provider fallback. */
public final class NebulaAudioClient {
    public static final class Configuration {
        public final int provider;
        public final String key, model, voice, style;
        public final double speed;
        public Configuration(int provider, String key, String model, String voice, String style, double speed) throws IOException {
            if (provider != 0 && provider != 2 || key == null || key.isEmpty() || key.length() > 10000 || key.indexOf('\r') >= 0 || key.indexOf('\n') >= 0) throw new IOException("Configure an OpenAI or Gemini audio service");
            this.provider = provider; this.key = key; this.model = NebulaAudioProtocol.model(model); this.voice = voice; this.style = style; this.speed = speed;
        }
        String base() { return provider == 0 ? "https://api.openai.com/v1" : "https://generativelanguage.googleapis.com/v1beta"; }
    }
    private volatile boolean cancelled;
    private volatile HttpURLConnection connection;
    public void cancel() { cancelled = true; HttpURLConnection c = connection; if (c != null) c.disconnect(); }
    private void check() throws InterruptedIOException { if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled"); }
    public String transcribe(Configuration config, File file, String mime) throws Exception {
        check();
        if (file == null || !file.isFile() || file.length() == 0 || file.length() > NebulaAudioProtocol.MAX_INPUT) throw new IOException("Maximum recording size: 14 MB");
        byte[] media;
        try (InputStream in = new FileInputStream(file)) { media = read(in, NebulaAudioProtocol.MAX_INPUT); }
        String boundary = "nebula-" + UUID.randomUUID();
        byte[] body = config.provider == 0 ? NebulaAudioProtocol.multipart(media, mime, config.model, boundary)
            : NebulaAudioProtocol.transcription(media, mime, config.model).toString().getBytes(StandardCharsets.UTF_8);
        byte[] response = request(config, config.provider == 0 ? "/audio/transcriptions" : "/interactions", body,
            config.provider == 0 ? "multipart/form-data; boundary=" + boundary : "application/json", 2_000_000);
        return NebulaAudioProtocol.transcript(config.provider, new JSONObject(new String(response, StandardCharsets.UTF_8)));
    }
    public byte[] speech(Configuration config, String text) throws Exception {
        check();
        byte[] body = NebulaAudioProtocol.speech(config.provider, config.model, text, config.voice, config.style, config.speed).toString().getBytes(StandardCharsets.UTF_8);
        byte[] response = request(config, config.provider == 0 ? "/audio/speech" : "/interactions", body, "application/json", config.provider == 0 ? NebulaAudioProtocol.MAX_AUDIO : NebulaAudioProtocol.MAX_JSON);
        return config.provider == 0 ? NebulaAudioProtocol.wave(response) : NebulaAudioProtocol.generatedAudio(new JSONObject(new String(response, StandardCharsets.UTF_8)));
    }
    private byte[] request(Configuration config, String path, byte[] body, String type, int max) throws Exception {
        check(); HttpURLConnection c = (HttpURLConnection)new URL(config.base() + path).openConnection(); connection = c;
        try {
            check(); c.setInstanceFollowRedirects(false); c.setConnectTimeout(20000); c.setReadTimeout(120000);
            c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type", type);
            c.setRequestProperty(config.provider == 0 ? "Authorization" : "x-goog-api-key", config.provider == 0 ? "Bearer " + config.key : config.key);
            c.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = c.getOutputStream()) { for (int offset=0; offset<body.length; offset+=8192) { check(); out.write(body,offset,Math.min(8192,body.length-offset)); } }
            check(); int status = c.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Audio service: HTTP " + status);
            if (c.getContentLength() > max) throw new IOException("Audio response too large");
            try (InputStream in = c.getInputStream()) { return read(in, max); }
        } finally { connection = null; c.disconnect(); }
    }
    private byte[] read(InputStream in, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        long deadline = System.nanoTime() + 180_000_000_000L;
        while ((n=in.read(buffer)) != -1) { check(); if (System.nanoTime() > deadline || out.size()+n>max) throw new IOException("Audio request limit exceeded"); out.write(buffer,0,n); }
        check(); return out.toByteArray();
    }
}
