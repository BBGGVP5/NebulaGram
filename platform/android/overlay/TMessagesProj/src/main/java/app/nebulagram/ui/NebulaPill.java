package app.nebulagram.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** One pill: how it looks, where its data comes from, how it shows it and what a tap does. */
public final class NebulaPill {
    public static final int ACTION_DETAILS = 0, ACTION_OPEN = 1, ACTION_COPY = 2, ACTION_REFRESH = 3;
    /** Refresh choices in minutes; 0 means only by hand. */
    public static final int[] INTERVALS = {0, 5, 15, 30, 60, 180, 720, 1440};
    /** Icon keys shown in the editor; the drawable is chosen by NebulaPills. */
    public static final String[] ICONS = {"code", "bitcoin", "dollar", "github", "pulse", "cloud", "wallet", "timer", "star", "chart"};
    public static final int[] COLORS = {0xFFD9D9D9, 0xFFE5484D, 0xFFE8702A, 0xFFF0950C, 0xFFF2C12E, 0xFF35C13E, 0xFF1A7F5A, 0xFF1CB09E,
            0xFF1FB2D8, 0xFF1E96F0, 0xFF3D6EF0, 0xFF6E5BE8, 0xFFB45BE8, 0xFFE8559E, 0xFF9A7055, 0xFF7C8C9C, 0xFF4A5261};

    public static final class Condition {
        public String field = "", op = ">", value = "";
        public int color;
        public String text = "";
        JSONObject json() throws Exception {
            return new JSONObject().put("field", field).put("op", op).put("value", value).put("color", color).put("text", text);
        }
        static Condition of(JSONObject o) {
            Condition c = new Condition();
            c.field = o.optString("field", ""); c.op = o.optString("op", ">"); c.value = o.optString("value", "");
            c.color = o.optInt("color", 0); c.text = o.optString("text", "");
            return c;
        }
    }

    public String id = UUID.randomUUID().toString();
    public String name = "";
    public String icon = "code";
    public int color = COLORS[9];
    public boolean request;
    public String url = "", method = "GET", headers = "", body = "";
    public int interval = 60;
    public String text = "";
    public final List<Condition> conditions = new ArrayList<>();
    public int action = ACTION_DETAILS;
    public String actionUrl = "";

    public JSONObject json() throws Exception {
        JSONArray rules = new JSONArray();
        for (Condition c : conditions) rules.put(c.json());
        return new JSONObject().put("v", 1).put("id", id).put("name", name).put("icon", icon).put("color", color)
                .put("request", request).put("url", url).put("method", method).put("headers", headers).put("body", body)
                .put("interval", interval).put("text", text).put("conditions", rules).put("action", action).put("actionUrl", actionUrl);
    }

    /** Imports keep everything except the id, so the same pill can be imported twice. */
    public static NebulaPill of(JSONObject o, boolean keepId) {
        NebulaPill p = new NebulaPill();
        if (keepId && o.has("id")) p.id = o.optString("id", p.id);
        p.name = limit(o.optString("name", ""), 64);
        p.icon = o.optString("icon", "code");
        p.color = o.optInt("color", COLORS[9]);
        p.request = o.optBoolean("request", false);
        p.url = limit(o.optString("url", ""), 2048);
        p.method = "POST".equalsIgnoreCase(o.optString("method", "GET")) ? "POST" : "GET";
        p.headers = limit(o.optString("headers", ""), 4096);
        p.body = limit(o.optString("body", ""), 8192);
        p.interval = interval(o.optInt("interval", 60));
        p.text = limit(o.optString("text", ""), 256);
        JSONArray rules = o.optJSONArray("conditions");
        if (rules != null) for (int i = 0; i < rules.length() && i < 16; i++) {
            JSONObject rule = rules.optJSONObject(i);
            if (rule != null) p.conditions.add(Condition.of(rule));
        }
        p.action = Math.max(ACTION_DETAILS, Math.min(ACTION_REFRESH, o.optInt("action", ACTION_DETAILS)));
        p.actionUrl = limit(o.optString("actionUrl", ""), 2048);
        return p;
    }

    static int interval(int minutes) {
        for (int value : INTERVALS) if (value == minutes) return value;
        return 60;
    }

    private static String limit(String value, int max) { return value == null ? "" : value.length() <= max ? value : value.substring(0, max); }

    /** The first matching condition, or null. */
    public Condition active(NebulaPillFormat.Response response, long now) {
        for (Condition c : conditions) if (NebulaPillFormat.matches(c.field, c.op, c.value, response, now)) return c;
        return null;
    }

    /** What the pill shows now: the condition's text when it has one, else the pill text. */
    public String shown(NebulaPillFormat.Response response, long now) {
        Condition c = active(response, now);
        String template = c != null && !c.text.isEmpty() ? c.text : text;
        if (request && response == null && template.contains("{") && !template.contains("{countdown:")) return "…";
        return NebulaPillFormat.render(template, response, now);
    }

    public int shownColor(NebulaPillFormat.Response response, long now) {
        Condition c = active(response, now);
        return c != null && c.color != 0 ? c.color : color;
    }

    /** Ready-made pills; each is an ordinary pill the user can change. */
    public static NebulaPill template(int index, boolean russian) {
        NebulaPill p = new NebulaPill();
        switch (index) {
            case 1:
                p.name = "Bitcoin"; p.icon = "bitcoin"; p.color = COLORS[3]; p.request = true; p.interval = 15;
                p.url = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd";
                p.text = "${bitcoin.usd|short}";
                break;
            case 2:
                p.name = russian ? "Доллар США" : "US Dollar"; p.icon = "dollar"; p.color = COLORS[6]; p.request = true; p.interval = 60;
                p.url = "https://open.er-api.com/v6/latest/USD";
                p.text = "{rates.EUR|fixed:3} €";
                break;
            case 3:
                p.name = "GitHub"; p.icon = "github"; p.color = COLORS[15]; p.request = true; p.interval = 180;
                p.url = "https://api.github.com/repos/DrKLO/Telegram";
                p.text = "★ {stargazers_count|short}";
                break;
            case 4:
                p.name = russian ? "Сервер" : "Server"; p.icon = "pulse"; p.color = COLORS[5]; p.request = true; p.interval = 5;
                p.url = "https://example.com";
                p.text = "{code} · {latency} ms";
                Condition down = new Condition();
                down.field = "code"; down.op = ">="; down.value = "400"; down.color = COLORS[1];
                p.conditions.add(down);
                break;
            case 5:
                p.name = russian ? "Погода" : "Weather"; p.icon = "cloud"; p.color = COLORS[15]; p.request = true; p.interval = 30;
                p.url = "https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}&current=temperature_2m";
                p.text = "{current.temperature_2m|round}°";
                break;
            case 6:
                p.name = "TON"; p.icon = "wallet"; p.color = COLORS[10]; p.request = true; p.interval = 60;
                p.url = "https://toncenter.com/api/v2/getAddressBalance?address=ADDRESS";
                p.text = "{result|fixed:0} nTON";
                break;
            case 7:
                p.name = russian ? "Обратный отсчёт" : "Countdown"; p.icon = "timer"; p.color = COLORS[12]; p.request = false; p.interval = 0;
                p.text = "{countdown:" + (java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) + 1) + "-01-01}";
                break;
            default:
                p.name = russian ? "Новая пилюля" : "New pill"; p.icon = "code"; p.text = russian ? "Текст" : "Text";
                break;
        }
        return p;
    }
    public static final int TEMPLATES = 8;
}
