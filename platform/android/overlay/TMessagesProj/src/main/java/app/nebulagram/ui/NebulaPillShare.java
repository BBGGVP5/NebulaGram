package app.nebulagram.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

/** Importing someone's pill and reading the small text settings of Pill Stack. */
public final class NebulaPillShare {
    private NebulaPillShare() { }

    /** A pill from pasted text, with a new id; null when the text is not a pill. */
    public static NebulaPill parse(String text) {
        if (text == null) return null;
        String s = text.trim();
        int start = s.indexOf('{'), end = s.lastIndexOf('}');
        if (start < 0 || end <= start || end - start > 64 * 1024) return null;
        try {
            JSONObject o = new JSONObject(s.substring(start, end + 1));
            if (!o.has("text") || (!o.has("url") && o.optBoolean("request", false))) return null;
            return NebulaPill.of(o, false);
        } catch (Exception error) {
            return null;
        }
    }

    /** A latitude or longitude within ±limit, or "" when it is not a number. */
    public static String coordinate(String text, double limit) {
        if (text == null) return "";
        try {
            double value = Double.parseDouble(text.trim().replace(',', '.'));
            if (Double.isNaN(value) || Math.abs(value) > limit) return "";
            return String.valueOf(Math.round(value * 10000) / 10000d);
        } catch (NumberFormatException error) {
            return "";
        }
    }

    /** "name=value" lines; names are letters, digits, "_" and "-". */
    public static Map<String, String> secrets(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        if (text == null) return out;
        for (String line : text.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String name = line.substring(0, eq).trim();
            if (!name.matches("[A-Za-z0-9_-]{1,32}")) continue;
            out.put(name, line.substring(eq + 1).trim());
        }
        return out;
    }
}
