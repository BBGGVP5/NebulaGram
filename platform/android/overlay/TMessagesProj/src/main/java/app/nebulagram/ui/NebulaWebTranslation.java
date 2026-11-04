package app.nebulagram.ui;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keyless web translators. Each request names its service; only the text being translated is sent. */
public final class NebulaWebTranslation {
    public static final String GOOGLE = "google", MICROSOFT = "microsoft", YANDEX = "yandex";
    public static final String[] PROVIDERS = {GOOGLE, MICROSOFT, YANDEX};
    private static final String YANDEX_ID = UUID.randomUUID().toString().replace("-", "");
    private static String microsoftToken;
    private static long microsoftExpiry;

    private NebulaWebTranslation() { }

    public static final class Request {
        public final String method, url, contentType, body;
        public final Map<String, String> headers = new LinkedHashMap<>();
        Request(String method, String url, String contentType, String body) {
            this.method = method; this.url = url; this.contentType = contentType; this.body = body;
        }
    }

    public static boolean known(String provider) {
        return GOOGLE.equals(provider) || MICROSOFT.equals(provider) || YANDEX.equals(provider);
    }
    public static String name(String provider) {
        if (GOOGLE.equals(provider)) return "Google Translate";
        if (MICROSOFT.equals(provider)) return "Microsoft Translator";
        if (YANDEX.equals(provider)) return NebulaText.text("Яндекс Переводчик", "Yandex Translate");
        return provider;
    }
    public static String host(String provider) {
        if (GOOGLE.equals(provider)) return "translate.googleapis.com";
        if (MICROSOFT.equals(provider)) return "edge.microsoft.com · api-edge.cognitive.microsofttranslator.com";
        return "translate.yandex.net";
    }
    /** Google needs a GET URL; the others take a body and can carry more per request. */
    public static int chunkLimit(String provider) { return GOOGLE.equals(provider) ? 1000 : 3500; }
    /** Services that accept HTML keep one request per message and the context across style runs. */
    public static boolean markup(String provider) { return MICROSOFT.equals(provider) || YANDEX.equals(provider); }

    public static String language(String provider, String code) {
        String value = code == null || code.isEmpty() ? "en" : code.replace('_', '-');
        String lower = value.toLowerCase(Locale.ROOT);
        String base = lower.contains("-") ? lower.substring(0, lower.indexOf('-')) : lower;
        boolean traditional = lower.equals("zh-tw") || lower.equals("zh-hk") || lower.equals("zh-hant");
        if (GOOGLE.equals(provider)) {
            if (base.equals("zh")) return traditional ? "zh-TW" : "zh-CN";
            if (base.equals("nb")) return "no";
            return base;
        }
        if (MICROSOFT.equals(provider)) {
            if (base.equals("zh")) return traditional ? "zh-Hant" : "zh-Hans";
            if (base.equals("no")) return "nb";
            if (base.equals("tl")) return "fil";
            if (base.equals("sr")) return "sr-Cyrl";
            if (lower.equals("pt-pt")) return "pt-pt";
            return base;
        }
        return base;
    }

    private static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    public static Request request(String provider, String text, String target, String token, boolean html) {
        String to = language(provider, target);
        if (GOOGLE.equals(provider)) {
            return new Request("GET", "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=" + encode(to)
                + "&dt=t&ie=UTF-8&oe=UTF-8&q=" + encode(text), null, null);
        }
        if (MICROSOFT.equals(provider)) {
            Request request = new Request("POST", "https://api-edge.cognitive.microsofttranslator.com/translate?api-version=3.0&to=" + encode(to)
                + (html ? "&textType=html" : ""), "application/json; charset=UTF-8", "[{\"Text\":" + quote(text) + "}]");
            request.headers.put("Authorization", "Bearer " + token);
            return request;
        }
        if (YANDEX.equals(provider)) {
            return new Request("POST", "https://translate.yandex.net/api/v1/tr.json/translate?id=" + YANDEX_ID + "-0-0&srv=android",
                "application/x-www-form-urlencoded; charset=UTF-8",
                "lang=" + encode(to) + (html ? "&format=html" : "") + "&text=" + encode(text));
        }
        throw new IllegalArgumentException("Unknown translator " + provider);
    }
    public static Request microsoftAuth() { return new Request("GET", "https://edge.microsoft.com/translate/auth", null, null); }

    public static String parse(String provider, String body) {
        Object root = new Json(body).value();
        if (GOOGLE.equals(provider)) {
            StringBuilder answer = new StringBuilder();
            Object sentences = index(root, 0);
            if (sentences instanceof List) for (Object sentence : (List<?>)sentences) {
                Object part = index(sentence, 0);
                if (part instanceof String) answer.append((String)part);
            }
            return checked(answer.toString());
        }
        if (MICROSOFT.equals(provider)) {
            Object first = index(root, 0);
            Object translations = first instanceof Map ? ((Map<?, ?>)first).get("translations") : null;
            Object text = index(translations, 0) instanceof Map ? ((Map<?, ?>)index(translations, 0)).get("text") : null;
            if (text instanceof String) return checked((String)text);
            throw new IllegalStateException("TRANSLATOR_RESPONSE " + error(root));
        }
        if (root instanceof Map) {
            Object code = ((Map<?, ?>)root).get("code");
            Object text = ((Map<?, ?>)root).get("text");
            if (code instanceof Number && ((Number)code).intValue() != 200) throw new IllegalStateException("TRANSLATOR_RESPONSE " + code);
            StringBuilder answer = new StringBuilder();
            if (text instanceof List) for (Object part : (List<?>)text) if (part instanceof String) answer.append((String)part);
            return checked(answer.toString());
        }
        throw new IllegalStateException("TRANSLATOR_RESPONSE");
    }
    private static String error(Object root) {
        Object error = root instanceof Map ? ((Map<?, ?>)root).get("error") : null;
        Object message = error instanceof Map ? ((Map<?, ?>)error).get("message") : null;
        return message == null ? "" : message.toString();
    }
    private static String checked(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalStateException("EMPTY_TRANSLATION");
        return value;
    }
    private static Object index(Object value, int index) {
        return value instanceof List && ((List<?>)value).size() > index ? ((List<?>)value).get(index) : null;
    }

    // NebulaTranslationFormat's numbered delimiters become HTML spans that the
    // service keeps; anything it reorders or drops fails the format parser,
    // which then falls back to one request per style run.
    private static final Pattern DELIMITER = Pattern.compile("(\\[\\[\\[N+)(\\d+)\\]\\]\\]");
    private static final Pattern SPAN = Pattern.compile("<span\\s+id\\s*=\\s*\"?n(\\d+)\"?\\s*>(.*?)</span>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    public static String toMarkup(String payload) {
        Matcher matcher = DELIMITER.matcher(payload);
        StringBuilder html = new StringBuilder();
        int last = -1, previous = 0;
        while (matcher.find()) {
            if (last >= 0) html.append("<span id=\"n").append(last).append("\">").append(escape(payload.substring(previous, matcher.start()))).append("</span>");
            last = Integer.parseInt(matcher.group(2)); previous = matcher.end();
        }
        if (last < 0 || previous != payload.length()) return null;
        return html.toString();
    }
    public static String fromMarkup(String payload, String html) {
        Matcher delimiter = DELIMITER.matcher(payload);
        if (!delimiter.find()) return null;
        String prefix = delimiter.group(1);
        int count = 0;
        do count++; while (delimiter.find());
        Matcher span = SPAN.matcher(html);
        StringBuilder answer = new StringBuilder();
        int expected = 0;
        while (span.find()) {
            int id = Integer.parseInt(span.group(1));
            if (id != expected) return null;
            answer.append(prefix).append(id).append("]]]").append(unescape(span.group(2)));
            expected++;
        }
        if (expected != count - 1) return null;
        return answer.append(prefix).append(expected).append("]]]").toString();
    }
    static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("\n", "<br>");
    }
    static String unescape(String value) {
        return value.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "")
            .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", "\u00A0").replace("&amp;", "&");
    }

    public static String translate(String provider, String text, String target, boolean structured, BooleanSupplier cancelled) throws Exception {
        boolean html = structured && markup(provider);
        String input = html ? toMarkup(text) : text;
        // An empty answer fails NebulaTranslationFormat's parser, which retries run by run.
        if (input == null) return "";
        String token = MICROSOFT.equals(provider) ? microsoftToken(cancelled) : null;
        String answer = parse(provider, execute(request(provider, input, target, token, html), cancelled));
        if (!html) return answer;
        String mapped = fromMarkup(text, answer);
        return mapped == null ? "" : mapped;
    }
    private static synchronized String microsoftToken(BooleanSupplier cancelled) throws Exception {
        if (microsoftToken != null && System.currentTimeMillis() < microsoftExpiry) return microsoftToken;
        String token = execute(microsoftAuth(), cancelled).trim();
        if (token.isEmpty() || token.startsWith("{")) throw new IllegalStateException("TRANSLATOR_AUTH");
        microsoftToken = token; microsoftExpiry = System.currentTimeMillis() + 8 * 60 * 1000;
        return token;
    }
    private static String execute(Request request, BooleanSupplier cancelled) throws Exception {
        if (cancelled.getAsBoolean()) throw new InterruptedIOException();
        HttpURLConnection connection = (HttpURLConnection)new URL(request.url).openConnection();
        try {
            connection.setConnectTimeout(10000); connection.setReadTimeout(20000);
            connection.setRequestMethod(request.method);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
            for (Map.Entry<String, String> header : request.headers.entrySet()) connection.setRequestProperty(header.getKey(), header.getValue());
            if (request.body != null) {
                byte[] body = request.body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", request.contentType);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
            }
            int code = connection.getResponseCode();
            if (cancelled.getAsBoolean()) throw new InterruptedIOException();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = read(stream);
            if (code == 401 && request.headers.containsKey("Authorization")) synchronized (NebulaWebTranslation.class) { microsoftToken = null; }
            if (code >= 400) throw new java.io.IOException("HTTP " + code);
            return body;
        } finally {
            connection.disconnect();
        }
    }
    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (InputStream input = stream) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) > 0;) {
                if (output.size() > 2 * 1024 * 1024) throw new java.io.IOException("Response too large");
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format(Locale.ROOT, "\\u%04x", (int)c)); else out.append(c);
            }
        }
        return out.append('"').toString();
    }

    /** Small JSON reader: the service replies are untrusted, so failures are exceptions, never guesses. */
    static final class Json {
        private final String s; private int i;
        Json(String s) { this.s = s == null ? "" : s; }
        Object value() {
            Object value = read(); skip();
            if (i != s.length()) throw new IllegalStateException("TRANSLATOR_RESPONSE");
            return value;
        }
        private void skip() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        private Object read() {
            skip();
            if (i >= s.length()) throw new IllegalStateException("TRANSLATOR_RESPONSE");
            char c = s.charAt(i);
            if (c == '[') {
                i++; ArrayList<Object> list = new ArrayList<>(); skip();
                if (peek(']')) { i++; return list; }
                while (true) { list.add(read()); skip(); if (peek(',')) { i++; continue; } expect(']'); return list; }
            }
            if (c == '{') {
                i++; LinkedHashMap<String, Object> map = new LinkedHashMap<>(); skip();
                if (peek('}')) { i++; return map; }
                while (true) {
                    skip(); Object key = read();
                    if (!(key instanceof String)) throw new IllegalStateException("TRANSLATOR_RESPONSE");
                    skip(); expect(':'); map.put((String)key, read()); skip();
                    if (peek(',')) { i++; continue; } expect('}'); return map;
                }
            }
            if (c == '"') return string();
            if (s.startsWith("null", i)) { i += 4; return null; }
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw new IllegalStateException("TRANSLATOR_RESPONSE");
            return Double.parseDouble(s.substring(start, i));
        }
        private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }
        private void expect(char c) { if (!peek(c)) throw new IllegalStateException("TRANSLATOR_RESPONSE"); i++; }
        private String string() {
            expect('"'); StringBuilder out = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (i >= s.length()) break;
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw new IllegalStateException("TRANSLATOR_RESPONSE");
                        out.append((char)Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: out.append(e);
                }
            }
            throw new IllegalStateException("TRANSLATOR_RESPONSE");
        }
    }
}
