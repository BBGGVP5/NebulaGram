package app.nebulagram.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * The settings backup file: what goes in, how it is written, and what an
 * imported file is allowed to change.
 *
 * <p>Plain Java on maps — no SharedPreferences, no org.json — so
 * {@code scripts/check-settings-backup.py} compiles and round-trips it with a
 * bare JDK. {@link NebulaSettingsTransfer} does the Android half: reading the
 * stores, the document picker, the confirmation dialog.
 *
 * <p>Version 2 keeps every preference of the listed stores with its exact
 * type ({@code int} and {@code long} look the same in JSON, and a 5 written
 * back as a long would crash a {@code getInt}). Version 1 files, the old
 * flat allowlist export, still import.
 */
public final class NebulaSettingsBackup {
    private NebulaSettingsBackup() { }

    public static final String FORMAT = "NebulaGram-settings";
    public static final int VERSION = 2;
    public static final String MAIN = "nebulagram";

    /**
     * Stores that hold settings. Left out on purpose: message archives, pins,
     * AI chats, queues and caches (data, not preferences), the badge admin
     * store (a token), cloud sync state, icon packs (they point at files).
     */
    public static final String[] STORES = {
            MAIN, "nebula_ai_settings", "nebula_ai_services", "nebula_privacy",
            "nebula_message_preferences", "nebula_message_menu", "nebula_media_settings",
            "nebula_camera", "nebula_browser", "nebula_audio_settings", "nebulagram_feed",
    };

    static final int MAX_FILE = 2_000_000, MAX_KEYS = 5000, MAX_STRING = 65536, MAX_SET = 512,
            MAX_CONNECTIONS = 64, MAX_URL = 2048;

    /** Fragments that mark a credential, wherever they sit in a key. */
    private static final String[] SECRET_PARTS = {"key", "token", "secret", "password", "passwd", "pass", "auth",
            "credential", "cookie", "session", "bearer", "private"};
    /** Device or session state that is meaningless, or harmful, on another install. The picture
     *  accent seed is recomputed from the picture; NebulaTheme is not referenced, it pulls in Android. */
    private static final String[] STATE_PREFIXES = {"accent_before_material_you", "tunnel_", "proxy_", "font_custom",
            "picture_accent_seed", "pending_", "last_", "prompted", "intro_seen", "initialized",
            "connection_revision", "notifications_account_", "deleted_archive_error_", "settings_search_history"};

    public static boolean isStore(String store) {
        for (String known : STORES) if (known.equals(store)) return true;
        return false;
    }

    /** Whether a preference may leave the device in a backup at all. */
    public static boolean exportable(String store, String key) {
        if (!isStore(store) || key == null || key.isEmpty() || key.length() > 200) return false;
        String lower = key.toLowerCase(java.util.Locale.ROOT);
        for (String prefix : STATE_PREFIXES) if (lower.startsWith(prefix)) return false;
        // Schema keys are presentation settings by contract, even if a word in
        // them looks like a credential ("hide_send_as" is not a password).
        if (MAIN.equals(store) && NebulaSettingsSchema.types.containsKey(key)) return true;
        for (String token : lower.split("[^a-z0-9]+")) {
            for (String part : SECRET_PARTS) if (token.equals(part) || token.equals(part + "s")) return false;
        }
        for (String part : new String[]{"apikey", "api_key", "accesstoken", "access_token"}) {
            if (lower.contains(part)) return false;
        }
        return true;
    }

    /** A NebulaLink subscription: only exported when the user asks for connection keys. */
    public static final class Connection {
        public final String name, url, kind;
        public Connection(String name, String url, String kind) {
            this.name = name == null ? "" : name;
            this.url = url;
            this.kind = kind == null || kind.isEmpty() ? "remnawave" : kind;
        }
        @Override public boolean equals(Object other) {
            return other instanceof Connection && url.equals(((Connection) other).url);
        }
        @Override public int hashCode() { return url.hashCode(); }
    }

    /** A validated file, ready to apply. */
    public static final class Backup {
        public final int version;
        public final Map<String, Map<String, Object>> stores;
        public final List<Connection> connections;
        /** Entries present in the file but refused: unknown stores, secrets, device state. */
        public final int ignored;
        Backup(int version, Map<String, Map<String, Object>> stores, List<Connection> connections, int ignored) {
            this.version = version;
            this.stores = stores;
            this.connections = connections;
            this.ignored = ignored;
        }
    }

    /** One preference an import would change. */
    public static final class Change {
        public final String store, key;
        public final Object before, after;
        Change(String store, String key, Object before, Object after) {
            this.store = store;
            this.key = key;
            this.before = before;
            this.after = after;
        }
    }

    // --- export ---------------------------------------------------------------

    /** Keeps only exportable entries of the known stores, with supported types. */
    public static Map<String, Map<String, Object>> filter(Map<String, ? extends Map<String, ?>> raw) {
        Map<String, Map<String, Object>> result = new TreeMap<>();
        for (String store : STORES) {
            Map<String, ?> values = raw.get(store);
            if (values == null) continue;
            Map<String, Object> kept = new TreeMap<>();
            for (Map.Entry<String, ?> entry : values.entrySet()) {
                Object value = entry.getValue();
                if (!exportable(store, entry.getKey()) || typeName(value) == null) continue;
                if (value instanceof String && ((String) value).length() > MAX_STRING) continue;
                kept.put(entry.getKey(), value);
            }
            if (!kept.isEmpty()) result.put(store, kept);
        }
        return result;
    }

    public static String encode(Map<String, ? extends Map<String, ?>> raw, List<Connection> connections, String app, long created) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("format", FORMAT);
        root.put("version", VERSION);
        root.put("app", app == null ? "" : app);
        root.put("created", created);
        Map<String, Object> stores = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> store : filter(raw).entrySet()) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : store.getValue().entrySet()) {
                Map<String, Object> typed = new LinkedHashMap<>();
                typed.put("type", typeName(entry.getValue()));
                Object value = entry.getValue();
                if (value instanceof Set) {
                    List<Object> sorted = new ArrayList<>(new java.util.TreeSet<>((Set<?>) value));
                    value = sorted;
                }
                typed.put("value", value);
                values.put(entry.getKey(), typed);
            }
            stores.put(store.getKey(), values);
        }
        root.put("stores", stores);
        if (connections != null && !connections.isEmpty()) {
            List<Object> list = new ArrayList<>();
            for (Connection connection : connections) {
                if (!validUrl(connection.url)) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", connection.name);
                row.put("url", connection.url);
                row.put("kind", connection.kind);
                list.add(row);
            }
            if (!list.isEmpty()) root.put("connections", list);
        }
        StringBuilder out = new StringBuilder();
        Json.write(out, root, 0);
        return out.append('\n').toString();
    }

    static String typeName(Object value) {
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Integer) return "int";
        if (value instanceof Long) return "long";
        if (value instanceof Float) return Float.isFinite((Float) value) ? "float" : null;
        if (value instanceof String) return "string";
        if (value instanceof Set) {
            Set<?> set = (Set<?>) value;
            if (set.size() > MAX_SET) return null;
            for (Object item : set) if (!(item instanceof String) || ((String) item).length() > MAX_STRING) return null;
            return "string_set";
        }
        return null;
    }

    // --- import ---------------------------------------------------------------

    /** Parses and validates a backup; any malformed entry rejects the whole file. */
    public static Backup decode(String text) {
        if (text == null || text.length() > MAX_FILE) throw new IllegalArgumentException("size");
        Object parsed = Json.parse(text);
        if (!(parsed instanceof Map)) throw new IllegalArgumentException("root");
        Map<?, ?> root = (Map<?, ?>) parsed;
        if (!FORMAT.equals(root.get("format"))) throw new IllegalArgumentException("format");
        Object version = root.get("version");
        if (Objects.equals(version, 1L)) return decodeV1(root);
        if (!Objects.equals(version, (long) VERSION)) throw new IllegalArgumentException("version");
        Object stores = root.get("stores");
        if (!(stores instanceof Map)) throw new IllegalArgumentException("stores");
        Map<String, Map<String, Object>> result = new TreeMap<>();
        int ignored = 0, total = 0;
        for (Map.Entry<?, ?> store : ((Map<?, ?>) stores).entrySet()) {
            if (!(store.getValue() instanceof Map)) throw new IllegalArgumentException("store " + store.getKey());
            String name = String.valueOf(store.getKey());
            Map<?, ?> values = (Map<?, ?>) store.getValue();
            if (!isStore(name)) { ignored += values.size(); continue; }
            Map<String, Object> kept = new TreeMap<>();
            for (Map.Entry<?, ?> entry : values.entrySet()) {
                if (++total > MAX_KEYS) throw new IllegalArgumentException("too many keys");
                String key = String.valueOf(entry.getKey());
                if (!exportable(name, key)) { ignored++; continue; }
                if (!(entry.getValue() instanceof Map)) throw new IllegalArgumentException(key);
                Map<?, ?> typed = (Map<?, ?>) entry.getValue();
                Object value = typed(typed.get("type"), typed.get("value"), key);
                if (MAIN.equals(name)) {
                    Class<?> type = NebulaSettingsSchema.types.get(key);
                    if (type != null && !type.isInstance(value)) throw new IllegalArgumentException("schema " + key);
                }
                kept.put(key, value);
            }
            if (!kept.isEmpty()) result.put(name, kept);
        }
        List<Connection> connections = new ArrayList<>();
        Object list = root.get("connections");
        if (list != null) {
            if (!(list instanceof List) || ((List<?>) list).size() > MAX_CONNECTIONS) throw new IllegalArgumentException("connections");
            for (Object row : (List<?>) list) {
                if (!(row instanceof Map)) throw new IllegalArgumentException("connection");
                Map<?, ?> map = (Map<?, ?>) row;
                Object url = map.get("url"), name = map.get("name"), kind = map.get("kind");
                if (!(url instanceof String) || !validUrl((String) url)) throw new IllegalArgumentException("connection url");
                if (name != null && (!(name instanceof String) || ((String) name).length() > 200)) throw new IllegalArgumentException("connection name");
                if (kind != null && (!(kind instanceof String) || !((String) kind).matches("[a-z0-9_-]{1,32}"))) throw new IllegalArgumentException("connection kind");
                Connection connection = new Connection((String) name, (String) url, (String) kind);
                if (!connections.contains(connection)) connections.add(connection);
            }
        }
        return new Backup(VERSION, result, connections, ignored);
    }

    private static Backup decodeV1(Map<?, ?> root) {
        Object settings = root.get("settings");
        if (!(settings instanceof Map)) throw new IllegalArgumentException("settings");
        Map<String, Object> kept = new TreeMap<>();
        int ignored = 0;
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) settings).entrySet()) {
            String key = String.valueOf(entry.getKey());
            Class<?> type = NebulaSettingsSchema.types.get(key);
            if (type == null) { ignored++; continue; }
            Object value = entry.getValue();
            if (type == Integer.class && value instanceof Long) value = integer((Long) value, key);
            if (!type.isInstance(value) || value instanceof String && ((String) value).length() > 1024) {
                throw new IllegalArgumentException("v1 " + key);
            }
            kept.put(key, value);
        }
        Map<String, Map<String, Object>> stores = new TreeMap<>();
        if (!kept.isEmpty()) stores.put(MAIN, kept);
        return new Backup(1, stores, new ArrayList<>(), ignored);
    }

    private static Object typed(Object type, Object value, String key) {
        if ("boolean".equals(type) && value instanceof Boolean) return value;
        if ("int".equals(type) && value instanceof Long) return integer((Long) value, key);
        if ("long".equals(type) && value instanceof Long) return value;
        if ("float".equals(type) && (value instanceof Long || value instanceof Double)) {
            float result = ((Number) value).floatValue();
            if (!Float.isFinite(result)) throw new IllegalArgumentException(key);
            return result;
        }
        if ("string".equals(type) && value instanceof String && ((String) value).length() <= MAX_STRING) return value;
        if ("string_set".equals(type) && value instanceof List && ((List<?>) value).size() <= MAX_SET) {
            Set<String> set = new LinkedHashSet<>();
            for (Object item : (List<?>) value) {
                if (!(item instanceof String) || ((String) item).length() > MAX_STRING) throw new IllegalArgumentException(key);
                set.add((String) item);
            }
            return set;
        }
        throw new IllegalArgumentException("type " + key);
    }

    private static Integer integer(long value, String key) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IllegalArgumentException(key);
        return (int) value;
    }

    static boolean validUrl(String url) {
        return url != null && url.length() <= MAX_URL && (url.startsWith("https://") || url.startsWith("http://"))
                && url.length() > 8 && url.indexOf(' ') < 0 && url.indexOf('\n') < 0;
    }

    /** What applying the backup over the current values would change; merges, never deletes. */
    public static List<Change> diff(Map<String, ? extends Map<String, ?>> current, Backup backup) {
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> store : backup.stores.entrySet()) {
            Map<String, ?> now = current.get(store.getKey());
            for (Map.Entry<String, Object> entry : store.getValue().entrySet()) {
                Object before = now == null ? null : now.get(entry.getKey());
                if (!same(before, entry.getValue())) changes.add(new Change(store.getKey(), entry.getKey(), before, entry.getValue()));
            }
        }
        return changes;
    }

    /** Subscriptions the device does not have yet, compared by address. */
    public static List<Connection> newConnections(Collection<String> existingUrls, Backup backup) {
        List<Connection> result = new ArrayList<>();
        for (Connection connection : backup.connections) {
            if (existingUrls == null || !existingUrls.contains(connection.url)) result.add(connection);
        }
        return result;
    }

    private static boolean same(Object a, Object b) {
        if (a instanceof Set && b instanceof Set) return new java.util.HashSet<>((Set<?>) a).equals(new java.util.HashSet<>((Set<?>) b));
        return Objects.equals(a, b);
    }

    /** A short human description of a value for the confirmation dialog. */
    public static String describe(Object value) {
        if (value == null) return "—";
        if (value instanceof Set) return "[" + ((Set<?>) value).size() + "]";
        String text = String.valueOf(value);
        return text.length() > 40 ? text.substring(0, 39) + "…" : text;
    }

    // --- JSON -----------------------------------------------------------------

    /** Just enough JSON for this file: objects, arrays, strings, numbers, booleans, null. */
    static final class Json {
        private final String text;
        private int at;
        private int depth;

        private Json(String text) { this.text = text; }

        static Object parse(String text) {
            Json json = new Json(text);
            json.space();
            Object value = json.value();
            json.space();
            if (json.at != text.length()) throw new IllegalArgumentException("trailing data");
            return value;
        }

        private Object value() {
            if (at >= text.length()) throw new IllegalArgumentException("end of file");
            char c = text.charAt(at);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (text.startsWith("true", at)) { at += 4; return Boolean.TRUE; }
            if (text.startsWith("false", at)) { at += 5; return Boolean.FALSE; }
            if (text.startsWith("null", at)) { at += 4; return null; }
            return number();
        }

        private Map<String, Object> object() {
            if (++depth > 16) throw new IllegalArgumentException("nesting");
            Map<String, Object> map = new LinkedHashMap<>();
            at++;
            space();
            if (peek() == '}') { at++; depth--; return map; }
            while (true) {
                space();
                if (peek() != '"') throw new IllegalArgumentException("key at " + at);
                String key = string();
                space();
                expect(':');
                space();
                if (map.containsKey(key)) throw new IllegalArgumentException("duplicate " + key);
                map.put(key, value());
                space();
                char c = next();
                if (c == '}') break;
                if (c != ',') throw new IllegalArgumentException("object at " + at);
            }
            depth--;
            return map;
        }

        private List<Object> array() {
            if (++depth > 16) throw new IllegalArgumentException("nesting");
            List<Object> list = new ArrayList<>();
            at++;
            space();
            if (peek() == ']') { at++; depth--; return list; }
            while (true) {
                space();
                list.add(value());
                space();
                char c = next();
                if (c == ']') break;
                if (c != ',') throw new IllegalArgumentException("array at " + at);
            }
            depth--;
            return list;
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return out.toString();
                if (c < 0x20) throw new IllegalArgumentException("control character");
                if (c != '\\') { out.append(c); continue; }
                char e = next();
                switch (e) {
                    case '"': case '\\': case '/': out.append(e); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u':
                        if (at + 4 > text.length()) throw new IllegalArgumentException("escape");
                        out.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                        at += 4;
                        break;
                    default: throw new IllegalArgumentException("escape");
                }
            }
        }

        private Object number() {
            int start = at;
            if (peek() == '-') at++;
            while (at < text.length() && "0123456789.eE+-".indexOf(text.charAt(at)) >= 0) at++;
            String token = text.substring(start, at);
            if (token.isEmpty() || token.equals("-")) throw new IllegalArgumentException("value at " + start);
            try {
                if (token.indexOf('.') < 0 && token.indexOf('e') < 0 && token.indexOf('E') < 0) return Long.parseLong(token);
                double value = Double.parseDouble(token);
                if (!Double.isFinite(value)) throw new IllegalArgumentException("number");
                return value;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("number at " + start);
            }
        }

        private void space() {
            while (at < text.length() && Character.isWhitespace(text.charAt(at))) at++;
        }

        private char peek() {
            if (at >= text.length()) throw new IllegalArgumentException("end of file");
            return text.charAt(at);
        }

        private char next() {
            char c = peek();
            at++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) throw new IllegalArgumentException("expected " + c + " at " + (at - 1));
        }

        static void write(StringBuilder out, Object value, int indent) {
            if (value == null) out.append("null");
            else if (value instanceof String) quote(out, (String) value);
            else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) out.append(value);
            else if (value instanceof Float || value instanceof Double) {
                double number = ((Number) value).doubleValue();
                // Float.toString keeps the shortest form that reads back as the same float.
                out.append(value instanceof Float ? Float.toString((Float) value) : Double.toString(number));
            } else if (value instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) value;
                if (map.isEmpty()) { out.append("{}"); return; }
                out.append("{\n");
                Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<?, ?> entry = it.next();
                    pad(out, indent + 1);
                    quote(out, String.valueOf(entry.getKey()));
                    out.append(": ");
                    write(out, entry.getValue(), indent + 1);
                    if (it.hasNext()) out.append(',');
                    out.append('\n');
                }
                pad(out, indent);
                out.append('}');
            } else if (value instanceof Collection) {
                Collection<?> list = (Collection<?>) value;
                out.append('[');
                boolean first = true;
                for (Object item : list) {
                    if (!first) out.append(", ");
                    write(out, item, indent + 1);
                    first = false;
                }
                out.append(']');
            } else throw new IllegalArgumentException("unsupported " + value.getClass());
        }

        private static void pad(StringBuilder out, int indent) {
            for (int i = 0; i < indent; i++) out.append("  ");
        }

        private static void quote(StringBuilder out, String value) {
            out.append('"');
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"': out.append("\\\""); break;
                    case '\\': out.append("\\\\"); break;
                    case '\n': out.append("\\n"); break;
                    case '\r': out.append("\\r"); break;
                    case '\t': out.append("\\t"); break;
                    default:
                        if (c < 0x20 || c == 0x2028 || c == 0x2029) out.append(String.format("\\u%04x", (int) c));
                        else out.append(c);
                }
            }
            out.append('"');
        }
    }
}
