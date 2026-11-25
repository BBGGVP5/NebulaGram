package app.nebulagram.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.FragmentSearchField;

/**
 * Pill Stack: user-made pills in the home search field. Each pill may ask one address on its own
 * schedule (while the app is open) and shows a line made from the answer. Master switch
 * "pills_enabled", off by default; without it nothing is attached and nothing is requested.
 */
public final class NebulaPills {
    private NebulaPills() { }
    public static final String KEY = "pills_enabled";
    private static final int MAX_BYTES = 256 * 1024, TIMEOUT_MS = 10000, MAX_PILLS = 12;
    private static final ExecutorService network = Executors.newFixedThreadPool(2);
    private static final Map<String, NebulaPillFormat.Response> responses = new HashMap<>();
    private static final Map<String, Long> requested = new HashMap<>();
    private static final ArrayList<WeakReference<Stack>> stacks = new ArrayList<>();
    private static List<NebulaPill> cache;

    static SharedPreferences prefs() { return ApplicationLoader.applicationContext.getSharedPreferences("nebula_pills", 0); }
    public static boolean enabled() { return prefs().getBoolean(KEY, false); }
    public static void setEnabled(boolean value) { prefs().edit().putBoolean(KEY, value).apply(); refreshAll(); }

    public static synchronized List<NebulaPill> all() {
        if (cache == null) {
            cache = new ArrayList<>();
            try {
                JSONArray array = new JSONArray(prefs().getString("pills", "[]"));
                for (int i = 0; i < array.length() && cache.size() < MAX_PILLS; i++) {
                    JSONObject o = array.optJSONObject(i);
                    if (o != null) cache.add(NebulaPill.of(o, true));
                }
            } catch (Exception error) { FileLog.e(error); }
        }
        return new ArrayList<>(cache);
    }

    public static synchronized void save(List<NebulaPill> pills) {
        JSONArray array = new JSONArray();
        try { for (NebulaPill p : pills) array.put(p.json()); } catch (Exception error) { FileLog.e(error); return; }
        cache = new ArrayList<>(pills);
        prefs().edit().putString("pills", array.toString()).apply();
        refreshAll();
    }

    public static void put(NebulaPill pill) {
        List<NebulaPill> pills = all();
        boolean replaced = false;
        for (int i = 0; i < pills.size(); i++) if (pills.get(i).id.equals(pill.id)) { pills.set(i, pill); replaced = true; }
        if (!replaced) {
            if (pills.size() >= MAX_PILLS) return;
            pills.add(pill);
        }
        synchronized (NebulaPills.class) { responses.remove(pill.id); requested.remove(pill.id); }
        save(pills);
    }

    public static void remove(String id) {
        List<NebulaPill> pills = all();
        for (int i = pills.size() - 1; i >= 0; i--) if (pills.get(i).id.equals(id)) pills.remove(i);
        save(pills);
    }

    public static boolean infinite() { return prefs().getBoolean("infinite", true); }
    public static void setInfinite(boolean value) { prefs().edit().putBoolean("infinite", value).apply(); refreshAll(); }

    /** {secret.name} values; they never leave the device inside exported pills. */
    public static Map<String, String> secrets() {
        Map<String, String> out = new HashMap<>();
        try {
            JSONObject o = new JSONObject(prefs().getString("secrets", "{}"));
            for (java.util.Iterator<String> keys = o.keys(); keys.hasNext(); ) { String k = keys.next(); out.put(k, o.optString(k)); }
        } catch (Exception ignored) { }
        return out;
    }
    public static void setSecrets(Map<String, String> values) { prefs().edit().putString("secrets", new JSONObject(values).toString()).apply(); }
    public static String latitude() { return prefs().getString("lat", ""); }
    public static String longitude() { return prefs().getString("lon", ""); }
    public static void setLocation(String lat, String lon) { prefs().edit().putString("lat", lat).putString("lon", lon).apply(); }

    public static synchronized NebulaPillFormat.Response response(String id) { return responses.get(id); }

    /** Fills {lang}, {currency}, {lat}, {lon} and, where allowed, {secret.name}. */
    static String fill(String text, String url) {
        String lang = LocaleController.getInstance().getCurrentLocale().getLanguage();
        String filled = NebulaPillFormat.variables(text, lang, NebulaInlineSettings.resolvedCurrency(), latitude(), longitude());
        return NebulaPillFormat.secrets(filled, secrets(), NebulaPillFormat.secretsAllowed(url));
    }

    /** Asks the pill's address now (also when forced by hand) and redraws every stack on reply. */
    public static void fetch(NebulaPill pill, boolean force, Runnable done) {
        if (pill == null || !pill.request || TextUtils.isEmpty(pill.url)) { if (done != null) done.run(); return; }
        long now = System.currentTimeMillis();
        synchronized (NebulaPills.class) {
            Long last = requested.get(pill.id);
            long every = Math.max(1, pill.interval) * 60000L;
            if (!force && last != null && (pill.interval == 0 || now - last < every)) { if (done != null) done.run(); return; }
            requested.put(pill.id, now);
        }
        network.execute(() -> {
            NebulaPillFormat.Response response = request(pill);
            synchronized (NebulaPills.class) { responses.put(pill.id, response); }
            AndroidUtilities.runOnUIThread(() -> { refreshAll(); if (done != null) done.run(); });
        });
    }

    static NebulaPillFormat.Response request(NebulaPill pill) {
        long start = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            String address = fill(pill.url, pill.url).trim();
            if (!address.startsWith("https://") && !address.startsWith("http://")) return new NebulaPillFormat.Response(0, 0, start, "");
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod(pill.method);
            connection.setRequestProperty("User-Agent", "NebulaGram");
            connection.setRequestProperty("Accept", "application/json, text/plain, */*");
            for (String line : fill(pill.headers, pill.url).split("\n")) {
                int colon = line.indexOf(':');
                if (colon > 0) connection.setRequestProperty(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
            if ("POST".equals(pill.method)) {
                byte[] body = fill(pill.body, pill.url).getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                try (OutputStream out = connection.getOutputStream()) { out.write(body); }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (stream != null) try (InputStream in = stream) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) > 0 && out.size() < MAX_BYTES) out.write(buffer, 0, n);
            }
            return new NebulaPillFormat.Response(code, System.currentTimeMillis() - start, System.currentTimeMillis(), out.toString("UTF-8"));
        } catch (Exception error) {
            return new NebulaPillFormat.Response(0, System.currentTimeMillis() - start, System.currentTimeMillis(), "");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** DialogsActivity: the stack goes into the search field, after Telegram's own icons. */
    public static void attach(BaseFragment fragment, FragmentSearchField field) {
        if (field == null) return;
        Stack stack = new Stack(field.getContext(), fragment, field);
        field.addAdditionalIcon(stack);
        synchronized (stacks) { stacks.add(new WeakReference<>(stack)); }
        stack.rebuild();
    }

    static void refreshAll() {
        AndroidUtilities.runOnUIThread(() -> {
            synchronized (stacks) {
                for (int i = stacks.size() - 1; i >= 0; i--) {
                    Stack stack = stacks.get(i).get();
                    if (stack == null) stacks.remove(i); else stack.rebuild();
                }
            }
        });
    }

    static int icon(String key) {
        switch (key == null ? "" : key) {
            case "bitcoin": case "dollar": case "wallet": return org.telegram.messenger.R.drawable.msg_gift_premium;
            case "github": case "code": return org.telegram.messenger.R.drawable.msg_bot;
            case "pulse": case "chart": return org.telegram.messenger.R.drawable.msg_stats;
            case "timer": return org.telegram.messenger.R.drawable.msg_stories_timer;
            default: return org.telegram.messenger.R.drawable.msg_link;
        }
    }

    /** White text on dark pills, dark text on light ones. */
    static int textColor(int background) {
        double l = (0.299 * Color.red(background) + 0.587 * Color.green(background) + 0.114 * Color.blue(background)) / 255;
        return l > 0.62 ? 0xFF1B1B1F : Color.WHITE;
    }

    static TextView pillView(Context context, NebulaPill pill, NebulaPillFormat.Response response) {
        long now = System.currentTimeMillis();
        int background = pill.shownColor(response, now);
        TextView view = new TextView(context);
        view.setText(pill.shown(response, now));
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setMaxWidth(AndroidUtilities.dp(160));
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        view.setTypeface(AndroidUtilities.bold());
        view.setTextColor(textColor(background));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setCompoundDrawablePadding(AndroidUtilities.dp(4));
        android.graphics.drawable.Drawable drawable = context.getResources().getDrawable(icon(pill.icon)).mutate();
        drawable.setColorFilter(new android.graphics.PorterDuffColorFilter(textColor(background), android.graphics.PorterDuff.Mode.SRC_IN));
        int size = AndroidUtilities.dp(16);
        drawable.setBounds(0, 0, size, size);
        view.setCompoundDrawables(drawable, null, null, null);
        view.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(10), 0);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(AndroidUtilities.dp(14));
        shape.setColor(background);
        view.setBackground(shape);
        return view;
    }

    /** The pills shown in one search field; hidden while the user types or searches. */
    static final class Stack extends HorizontalScrollView {
        private final LinearLayout row;
        private final BaseFragment fragment;
        private final FragmentSearchField field;

        Stack(Context context, BaseFragment fragment, FragmentSearchField field) {
            super(context);
            this.fragment = fragment;
            this.field = field;
            setHorizontalScrollBarEnabled(false);
            setOverScrollMode(OVER_SCROLL_NEVER);
            row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            addView(row, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
            field.editText.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
                @Override public void afterTextChanged(Editable s) { updateVisibility(); }
            });
            field.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> updateVisibility());
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int limit = infinite() ? AndroidUtilities.dp(200) : AndroidUtilities.dp(170);
            super.onMeasure(MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST), heightMeasureSpec);
        }

        private void updateVisibility() {
            boolean searching = field.editText.isFocused() || field.editText.length() > 0;
            setVisibility(enabled() && row.getChildCount() > 0 && !searching ? VISIBLE : GONE);
        }

        void rebuild() {
            row.removeAllViews();
            if (enabled()) {
                List<NebulaPill> pills = all();
                int shown = 0;
                for (NebulaPill pill : pills) {
                    if (!infinite() && shown >= 1) break;
                    fetch(pill, false, null);
                    TextView view = pillView(getContext(), pill, response(pill.id));
                    view.setOnClickListener(v -> tap(pill));
                    view.setOnLongClickListener(v -> { menu(pill, v); return true; });
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(28));
                    params.leftMargin = AndroidUtilities.dp(4);
                    params.gravity = Gravity.CENTER_VERTICAL;
                    row.addView(view, params);
                    shown++;
                }
            }
            updateVisibility();
        }

        private void tap(NebulaPill pill) {
            switch (pill.action) {
                case NebulaPill.ACTION_OPEN:
                    if (!TextUtils.isEmpty(pill.actionUrl)) org.telegram.messenger.browser.Browser.openUrl(getContext(), pill.actionUrl);
                    break;
                case NebulaPill.ACTION_COPY:
                    copy(pill);
                    break;
                case NebulaPill.ACTION_REFRESH:
                    fetch(pill, true, null);
                    break;
                default:
                    details(pill);
                    break;
            }
        }

        private void copy(NebulaPill pill) {
            AndroidUtilities.addToClipboard(pill.shown(response(pill.id), System.currentTimeMillis()));
            Toast.makeText(getContext(), NebulaText.text("Скопировано", "Copied"), Toast.LENGTH_SHORT).show();
        }

        private void details(NebulaPill pill) {
            NebulaPillFormat.Response response = response(pill.id);
            StringBuilder text = new StringBuilder();
            text.append(pill.shown(response, System.currentTimeMillis()));
            if (pill.request) {
                text.append("\n\n").append(pill.url);
                if (response != null) {
                    text.append("\n").append(NebulaText.text("Ответ: ", "Response: ")).append(response.code).append(" · ").append(response.latency).append(" ms");
                    text.append("\n").append(NebulaText.text("Обновлено: ", "Updated: "))
                            .append(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date(response.time)));
                }
            }
            if (fragment != null) fragment.showDialog(new NebulaDialog.Builder(getContext()).setTitle(pill.name).setMessage(text.toString())
                    .setPositiveButton(NebulaText.text("Закрыть", "Close"), null).create());
        }

        private void menu(NebulaPill pill, View anchor) {
            CharSequence[] items = {NebulaText.text("Обновить", "Refresh"), NebulaText.text("Копировать", "Copy"), NebulaText.text("Изменить", "Edit")};
            if (fragment != null) fragment.showDialog(new NebulaDialog.Builder(getContext()).setTitle(pill.name)
                    .setItems(items, (dialog, which) -> {
                        if (which == 0) fetch(pill, true, null);
                        else if (which == 1) copy(pill);
                        else fragment.presentFragment(new NebulaPillEditorFragment(pill));
                    }).create());
        }
    }
}
