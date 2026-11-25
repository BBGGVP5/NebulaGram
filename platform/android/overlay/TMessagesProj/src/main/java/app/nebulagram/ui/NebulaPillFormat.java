package app.nebulagram.ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Pill text: "{rates.EUR|fixed:3} €" filled from the last response. Paths walk JSON objects and
 * arrays ("data[0].price"); built-ins are {code}, {latency}, {time} and {body}; filters chain with
 * "|". No code runs: a value is looked up, formatted and pasted, nothing else.
 */
public final class NebulaPillFormat {
    private NebulaPillFormat() { }

    /** What the last request produced. {@code json} is a JSONObject, JSONArray or null for non-JSON. */
    public static final class Response {
        public final int code;
        public final long latency;
        public final long time;
        public final String body;
        public final Object json;
        public Response(int code, long latency, long time, String body) {
            this.code = code; this.latency = latency; this.time = time; this.body = body == null ? "" : body;
            this.json = parse(this.body);
        }
    }

    static Object parse(String body) {
        String s = body == null ? "" : body.trim();
        if (s.isEmpty() || (s.charAt(0) != '{' && s.charAt(0) != '[')) return null;
        try { return new JSONTokener(s).nextValue(); } catch (Exception error) { return null; }
    }

    /** The value at {@code path} ("a.b[2].c"), or null. */
    public static Object lookup(Object root, String path) {
        if (root == null || path == null) return null;
        Object node = root;
        int i = 0, n = path.length();
        while (i < n && node != null) {
            char c = path.charAt(i);
            if (c == '.') { i++; continue; }
            if (c == '[') {
                int close = path.indexOf(']', i);
                if (close < 0) return null;
                String inside = path.substring(i + 1, close).trim();
                i = close + 1;
                if (node instanceof JSONArray) {
                    try {
                        int index = Integer.parseInt(inside);
                        JSONArray array = (JSONArray) node;
                        if (index < 0) index += array.length();
                        node = index >= 0 && index < array.length() ? array.opt(index) : null;
                    } catch (NumberFormatException error) { return null; }
                } else if (node instanceof JSONObject) {
                    String key = inside.length() >= 2 && (inside.startsWith("\"") || inside.startsWith("'")) ? inside.substring(1, inside.length() - 1) : inside;
                    node = ((JSONObject) node).opt(key);
                } else return null;
                continue;
            }
            int end = i;
            while (end < n && path.charAt(end) != '.' && path.charAt(end) != '[') end++;
            String key = path.substring(i, end);
            i = end;
            if (node instanceof JSONObject) node = ((JSONObject) node).opt(key);
            else if (node instanceof JSONArray && key.equals("length")) node = ((JSONArray) node).length();
            else return null;
        }
        return node == JSONObject.NULL ? null : node;
    }

    /** A placeholder's raw value: built-ins first, then the JSON path. */
    static Object value(String name, Response response, long now) {
        if (name.startsWith("countdown:")) return countdown(name.substring(10), now);
        if (response == null) return null;
        switch (name) {
            case "code": return response.code;
            case "latency": return response.latency;
            case "time": return response.time;
            case "body": return response.body.length() > 200 ? response.body.substring(0, 200) : response.body;
            default: return lookup(response.json, name);
        }
    }

    /** "12 d 4 h" until a date ("2026-12-31" or "2026-12-31T18:00"), "0" once it passed. */
    static String countdown(String date, long now) {
        long target = -1;
        for (String pattern : new String[]{"yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd"}) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
                format.setLenient(false);
                format.setTimeZone(TimeZone.getDefault());
                Date parsed = format.parse(date.trim());
                if (parsed != null) { target = parsed.getTime(); break; }
            } catch (Exception ignored) { }
        }
        if (target < 0) return null;
        long left = Math.max(0, target - now) / 60000;
        long days = left / 1440, hours = left % 1440 / 60, minutes = left % 60;
        boolean ru = "ru".equals(NebulaText.text("ru", "en"));
        if (days > 0) return days + (ru ? " д " : " d ") + hours + (ru ? " ч" : " h");
        if (hours > 0) return hours + (ru ? " ч " : " h ") + minutes + (ru ? " мин" : " min");
        return minutes + (ru ? " мин" : " min");
    }

    static BigDecimal number(Object value) {
        if (value instanceof Number) {
            try { return new BigDecimal(value.toString()); } catch (NumberFormatException error) { return null; }
        }
        if (value instanceof String) {
            try { return new BigDecimal(((String) value).trim()); } catch (NumberFormatException error) { return null; }
        }
        return null;
    }

    /** Applies one filter: fixed:n, round, int, upper, lower, short (1.2K), default:x, prefix:x, suffix:x, date, max:n. */
    static Object filter(Object value, String filter) {
        int colon = filter.indexOf(':');
        String name = (colon < 0 ? filter : filter.substring(0, colon)).trim().toLowerCase(Locale.ROOT);
        String arg = colon < 0 ? "" : filter.substring(colon + 1);
        switch (name) {
            case "default": return value == null || value.toString().isEmpty() ? arg : value;
            case "fixed": {
                BigDecimal n = number(value);
                if (n == null) return value;
                int places;
                try { places = Math.max(0, Math.min(10, Integer.parseInt(arg.trim()))); } catch (NumberFormatException e) { places = 2; }
                return n.setScale(places, RoundingMode.HALF_UP).toPlainString();
            }
            case "round": case "int": {
                BigDecimal n = number(value);
                return n == null ? value : n.setScale(0, RoundingMode.HALF_UP).toPlainString();
            }
            case "short": {
                BigDecimal n = number(value);
                if (n == null) return value;
                double d = n.doubleValue(), a = Math.abs(d);
                String[] units = {"", "K", "M", "B", "T"};
                int u = 0;
                while (a >= 1000 && u < units.length - 1) { a /= 1000; d /= 1000; u++; }
                String text = u == 0 ? n.stripTrailingZeros().toPlainString() : new BigDecimal(d).setScale(a >= 100 ? 0 : 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
                return text + units[u];
            }
            case "upper": return value == null ? null : value.toString().toUpperCase(Locale.ROOT);
            case "lower": return value == null ? null : value.toString().toLowerCase(Locale.ROOT);
            case "prefix": return value == null ? null : arg + value;
            case "suffix": return value == null ? null : value + arg;
            case "max": {
                if (value == null) return null;
                int max;
                try { max = Math.max(1, Integer.parseInt(arg.trim())); } catch (NumberFormatException e) { return value; }
                String s = value.toString();
                return s.length() <= max ? s : s.substring(0, max) + "…";
            }
            case "date": {
                BigDecimal n = number(value);
                if (n == null) return value;
                long millis = n.longValue();
                if (millis < 100000000000L) millis *= 1000;
                return new SimpleDateFormat(arg.isEmpty() ? "HH:mm" : arg, Locale.getDefault()).format(new Date(millis));
            }
            default: return value;
        }
    }

    /** Fills every "{…}" of the template. A missing value shows as "—". Braces can be escaped as "{{" and "}}". */
    public static String render(String template, Response response, long now) {
        if (template == null) return "";
        StringBuilder out = new StringBuilder();
        int i = 0, n = template.length();
        while (i < n) {
            char c = template.charAt(i);
            if (c == '{' && i + 1 < n && template.charAt(i + 1) == '{') { out.append('{'); i += 2; continue; }
            if (c == '}' && i + 1 < n && template.charAt(i + 1) == '}') { out.append('}'); i += 2; continue; }
            if (c != '{') { out.append(c); i++; continue; }
            int close = template.indexOf('}', i + 1);
            if (close < 0) { out.append(template, i, n); break; }
            out.append(placeholder(template.substring(i + 1, close), response, now));
            i = close + 1;
        }
        return out.length() > 120 ? out.substring(0, 120) : out.toString();
    }

    static String placeholder(String inside, Response response, long now) {
        String[] parts = split(inside);
        Object value = value(parts[0].trim(), response, now);
        for (int p = 1; p < parts.length; p++) value = filter(value, parts[p]);
        if (value == null) return "—";
        if (value instanceof JSONObject || value instanceof JSONArray) return "…";
        return value.toString();
    }

    private static String[] split(String inside) {
        List<String> parts = new ArrayList<>();
        int from = 0;
        for (int i = 0; i < inside.length(); i++) if (inside.charAt(i) == '|') { parts.add(inside.substring(from, i)); from = i + 1; }
        parts.add(inside.substring(from));
        return parts.toArray(new String[0]);
    }

    /** Placeholders a template uses, for the editor's list of values. */
    public static List<String> placeholders(String template) {
        List<String> out = new ArrayList<>();
        if (template == null) return out;
        int i = 0;
        while ((i = template.indexOf('{', i)) >= 0) {
            if (i + 1 < template.length() && template.charAt(i + 1) == '{') { i += 2; continue; }
            int close = template.indexOf('}', i + 1);
            if (close < 0) break;
            out.add(template.substring(i + 1, close));
            i = close + 1;
        }
        return out;
    }

    /** One condition: "field op value". Ops: > >= < <= = != contains. Numbers compare as numbers. */
    public static boolean matches(String field, String op, String expected, Response response, long now) {
        Object raw = value(field == null ? "" : field.trim(), response, now);
        if (raw == null) return "empty".equals(op);
        String text = raw.toString();
        BigDecimal left = number(raw), right = number(expected);
        switch (op == null ? "" : op) {
            case "empty": return text.isEmpty();
            case "contains": return expected != null && text.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            case "=": return left != null && right != null ? left.compareTo(right) == 0 : text.equals(expected);
            case "!=": return left != null && right != null ? left.compareTo(right) != 0 : !text.equals(expected);
            case ">": return left != null && right != null && left.compareTo(right) > 0;
            case ">=": return left != null && right != null && left.compareTo(right) >= 0;
            case "<": return left != null && right != null && left.compareTo(right) < 0;
            case "<=": return left != null && right != null && left.compareTo(right) <= 0;
            default: return false;
        }
    }

    /** {secret.name} placeholders in an address, header or body; filled only when sending. */
    public static String secrets(String text, java.util.Map<String, String> values, boolean allowed) {
        if (text == null || text.indexOf("{secret.") < 0) return text;
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (true) {
            int start = text.indexOf("{secret.", i);
            if (start < 0) { out.append(text.substring(i)); break; }
            int close = text.indexOf('}', start);
            if (close < 0) { out.append(text.substring(i)); break; }
            out.append(text, i, start);
            String value = values == null ? null : values.get(text.substring(start + 8, close));
            out.append(allowed && value != null ? value : "");
            i = close + 1;
        }
        return out.toString();
    }

    /** Over plain HTTP a secret goes only to this device or the local network. */
    public static boolean secretsAllowed(String url) {
        if (url == null) return false;
        String lower = url.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://")) return true;
        if (!lower.startsWith("http://")) return false;
        String host = lower.substring(7);
        int end = 0;
        while (end < host.length() && "/:?#".indexOf(host.charAt(end)) < 0) end++;
        if (host.startsWith("[")) { int close = host.indexOf(']'); host = close > 0 ? host.substring(1, close) : host; }
        else host = host.substring(0, end);
        if (host.equals("localhost") || host.endsWith(".local") || host.equals("::1")) return true;
        String[] parts = host.split("\\.");
        if (parts.length != 4) return false;
        try {
            int a = Integer.parseInt(parts[0]), b = Integer.parseInt(parts[1]);
            return a == 10 || a == 127 || a == 192 && b == 168 || a == 172 && b >= 16 && b <= 31;
        } catch (NumberFormatException error) {
            return false;
        }
    }

    /** {lang}, {currency}, {lat}, {lon} in the address, headers and body. */
    public static String variables(String text, String lang, String currency, String lat, String lon) {
        if (text == null) return null;
        return text.replace("{lang}", lang == null ? "" : lang).replace("{currency}", currency == null ? "" : currency)
                .replace("{lat}", lat == null ? "" : lat).replace("{lon}", lon == null ? "" : lon);
    }
}
