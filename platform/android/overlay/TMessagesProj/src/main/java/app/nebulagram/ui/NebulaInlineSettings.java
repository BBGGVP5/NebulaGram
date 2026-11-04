package app.nebulagram.ui;

import android.content.SharedPreferences;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

/** "Inline results" preferences and the day-long cache of fiat rates. The master switch stays inline_math. */
public final class NebulaInlineSettings {
    private NebulaInlineSettings() { }
    public static final String RATES_URL = "https://open.er-api.com/v6/latest/USD";
    private static final long DAY = 24L * 60 * 60 * 1000, RETRY = 10L * 60 * 1000;
    private static volatile SharedPreferences preferences;
    private static volatile boolean calculator, currency, units, timezones, group;
    private static volatile int angle, measurement;
    private static volatile String defaultCurrency = "";
    private static volatile Map<String, Double> rates;
    private static volatile long ratesTime, attempt;
    private static volatile boolean loading;
    private static final SharedPreferences.OnSharedPreferenceChangeListener listener = (p, k) -> refresh();
    private static synchronized SharedPreferences prefs() {
        if (preferences == null) {
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0);
            preferences.registerOnSharedPreferenceChangeListener(listener); refresh();
        }
        return preferences;
    }
    private static void refresh() {
        SharedPreferences p = preferences; if (p == null) return;
        calculator = p.getBoolean("inline_calculator", true);
        currency = p.getBoolean("inline_currency", true);
        units = p.getBoolean("inline_units", true);
        timezones = p.getBoolean("inline_timezones", true);
        group = p.getBoolean("inline_group_digits", true);
        angle = Math.max(0, Math.min(1, p.getInt("inline_angle_unit", 0)));
        measurement = Math.max(0, Math.min(2, p.getInt("inline_measurement", 0)));
        String code = p.getString("inline_default_currency", "");
        defaultCurrency = code == null || !code.matches("[A-Z]{3}") ? "" : code;
    }
    public static boolean enabled() { return NebulaChatPreferences.math(); }
    public static boolean feature(String key) {
        if (preferences == null) prefs();
        switch (key) { case "inline_calculator": return calculator; case "inline_currency": return currency; case "inline_units": return units; case "inline_timezones": return timezones; default: return group; }
    }
    public static int angle() { if (preferences == null) prefs(); return angle; }
    public static int measurement() { if (preferences == null) prefs(); return measurement; }
    /** "" is Auto. */
    public static String defaultCurrency() { if (preferences == null) prefs(); return defaultCurrency; }
    public static void set(String key, boolean value) { prefs().edit().putBoolean(key, value).apply(); refresh(); }
    public static void angle(int value) { prefs().edit().putInt("inline_angle_unit", Math.max(0, Math.min(1, value))).apply(); refresh(); }
    public static void measurement(int value) { prefs().edit().putInt("inline_measurement", Math.max(0, Math.min(2, value))).apply(); refresh(); }
    public static void defaultCurrency(String code) { prefs().edit().putString("inline_default_currency", code == null || !code.matches("[A-Z]{3}") ? "" : code).apply(); refresh(); }

    /** Auto follows the account's phone number, then the device locale. */
    public static String resolvedCurrency() {
        String chosen = defaultCurrency();
        if (!chosen.isEmpty()) return chosen;
        try {
            org.telegram.tgnet.TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
            String code = user == null ? null : NebulaInlineCurrency.byPhone(user.phone);
            if (code != null) return code;
        } catch (RuntimeException ignored) { }
        try { return java.util.Currency.getInstance(Locale.getDefault()).getCurrencyCode(); } catch (RuntimeException ignored) { return "USD"; }
    }
    public static boolean imperial() {
        int system = measurement();
        if (system != 0) return system == 2;
        String country = Locale.getDefault().getCountry();
        return "US".equals(country) || "LR".equals(country) || "MM".equals(country);
    }

    public static NebulaInlineResults.Options options() {
        if (preferences == null) prefs();
        NebulaInlineResults.Options o = new NebulaInlineResults.Options();
        o.calculator = calculator; o.currency = currency; o.units = units; o.timezones = timezones;
        o.group = group; o.degrees = angle == 0; o.imperial = imperial();
        o.russian = "ru".equals(NebulaText.text("ru", "en"));
        o.defaultCurrency = resolvedCurrency();
        o.rates = rates();
        return o;
    }

    private static SharedPreferences cache() { return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram_rates", 0); }
    /** Cached rates even when stale; a refresh starts in the background after a day. */
    public static Map<String, Double> rates() {
        if (rates == null) {
            try { SharedPreferences c = cache(); Map<String, Double> parsed = parse(c.getString("usd", null)); if (parsed != null) { rates = parsed; ratesTime = c.getLong("time", 0); } }
            catch (Exception ignored) { }
        }
        if (System.currentTimeMillis() - ratesTime > DAY) load(null);
        return rates;
    }
    public static long ratesTime() { return ratesTime; }
    public static void load(Runnable done) {
        long now = System.currentTimeMillis();
        if (loading || now - attempt < RETRY) return;
        loading = true; attempt = now;
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(RATES_URL).openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(10000);
                if (c.getResponseCode() == 200) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192];
                    try (InputStream in = c.getInputStream()) { int n; while ((n = in.read(buffer)) > 0 && out.size() < 512 * 1024) out.write(buffer, 0, n); }
                    String body = out.toString("UTF-8");
                    Map<String, Double> parsed = parse(body);
                    if (parsed != null) {
                        rates = parsed; ratesTime = System.currentTimeMillis();
                        cache().edit().putString("usd", body).putLong("time", ratesTime).apply();
                        if (done != null) org.telegram.messenger.AndroidUtilities.runOnUIThread(done);
                    }
                }
                c.disconnect();
            } catch (Exception ignored) {
            } finally { loading = false; }
        }, "NebulaRates").start();
    }
    static Map<String, Double> parse(String body) throws org.json.JSONException {
        if (body == null) return null;
        JSONObject json = new JSONObject(body);
        if (!"success".equals(json.optString("result"))) return null;
        JSONObject values = json.getJSONObject("rates");
        Map<String, Double> out = new HashMap<>();
        for (Iterator<String> keys = values.keys(); keys.hasNext(); ) {
            String key = keys.next(); double value = values.optDouble(key, 0);
            if (key.matches("[A-Z]{3}") && value > 0) out.put(key, value);
        }
        return out.containsKey("USD") ? Collections.unmodifiableMap(out) : null;
    }
}
