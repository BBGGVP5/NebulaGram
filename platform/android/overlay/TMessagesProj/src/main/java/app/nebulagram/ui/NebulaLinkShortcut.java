package app.nebulagram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import app.nebulagram.nebulalink.NebulaLink;

/** Home-only control. Rendering/preview never starts a tunnel or a probe. */
public final class NebulaLinkShortcut extends View {
    private static final String KEY = "home_nebulalink";
    private final BaseFragment owner;
    private final ActionBarMenuItem item;
    private final int preview;
    private final Drawable icon;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private boolean pending, failed, probing;
    private final NebulaLink.StatusListener statusListener = status -> { failed = false; refresh(); };
    private final SharedPreferences.OnSharedPreferenceChangeListener preferencesListener = (p, key) -> {
        if (KEY.equals(key)) refresh();
    };
    private final NotificationCenter.NotificationCenterDelegate proxyListener = (id, account, args) -> refresh();
    private static SharedPreferences prefs() { return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0); }
    public static boolean visible() { return prefs().getBoolean(KEY, true); }
    public static void visible(boolean show) { prefs().edit().putBoolean(KEY, show).apply(); }

    private NebulaLinkShortcut(Context context, BaseFragment owner, ActionBarMenuItem item, int preview) {
        super(context); this.owner = owner; this.item = item; this.preview = preview;
        icon = context.getResources().getDrawable(R.drawable.nebula_link_shield).mutate();
        setImportantForAccessibility(preview >= 0 ? IMPORTANT_FOR_ACCESSIBILITY_YES : IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (preview >= 0) setContentDescription(label(preview));
    }
    public static void install(BaseFragment owner, ActionBarMenu menu) {
        ActionBarMenuItem item = menu.addItemWithWidth(-2401, 0, AndroidUtilities.dp(46));
        item.getIconView().setVisibility(GONE);
        NebulaLinkShortcut control = new NebulaLinkShortcut(menu.getContext(), owner, item, -1);
        item.addView(control, LayoutHelper.createFrame(-1, -1));
        item.setOnClickListener(v -> control.toggle());
        item.setOnLongClickListener(v -> { NebulaHaptics.tick(item); control.showMenu(); return true; });
        control.refresh();
    }
    public static void addSettings(LinearLayout content) {
        Context c = content.getContext();
        NebulaCard card = new NebulaCard(c);
        card.add(NebulaExtras.toggle(c, R.drawable.nebula_link_shield, NebulaText.text("NebulaLink на главной", "NebulaLink on home"),
                NebulaText.text("Нажатие — соединение, удержание — сервер и пинг", "Tap to connect; hold for server and ping"),
                visible(), NebulaLinkShortcut::visible));
        LinearLayout samples = new LinearLayout(c);
        samples.setOrientation(LinearLayout.VERTICAL);
        samples.setPadding(dp(8), dp(4), dp(8), dp(8));
        LinearLayout pair = null;
        for (int state = 0; state < 4; state++) {
            if (state % 2 == 0) {
                pair = new LinearLayout(c);
                samples.addView(pair, new LinearLayout.LayoutParams(-1, -2));
            }
            LinearLayout column = new LinearLayout(c);
            column.setGravity(android.view.Gravity.CENTER_VERTICAL);
            column.setMinimumHeight(dp(52));
            column.addView(new NebulaLinkShortcut(c, null, null, state), new LinearLayout.LayoutParams(dp(46), dp(46)));
            android.widget.TextView text = new android.widget.TextView(c); text.setText(label(state));
            text.setTextSize(13); text.setTextColor(NebulaTheme.of(c).onSurfaceVariant());
            column.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            pair.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
        }
        card.add(samples); content.addView(card);
    }
    private static String label(int state) {
        switch (state) {
            case NebulaLinkShortcutState.CONNECTING: return NebulaText.text("Подключение", "Connecting");
            case NebulaLinkShortcutState.ON: return NebulaText.text("Подключено", "Connected");
            case NebulaLinkShortcutState.ERROR: return NebulaText.text("Ошибка", "Error");
            default: return NebulaText.text("Отключено", "Disconnected");
        }
    }
    private String phase() { JSONObject status = NebulaLink.status(); return status == null ? "disconnected" : status.optString("state"); }
    private int state() { return preview >= 0 ? preview : NebulaLinkShortcutState.resolve(phase(), NebulaLink.isRoutingThroughTunnel(), pending, failed); }
    private void refresh() {
        if (item != null) {
            item.setVisibility(visible() ? VISIBLE : GONE);
            item.setEnabled(!pending);
            item.setContentDescription("NebulaLink · " + label(state()) + NebulaText.text(". Удерживайте для выбора сервера и пинга", ". Hold for server and ping"));
        }
        invalidate();
    }
    private void toggle() {
        if (pending || owner == null) return;
        NebulaHaptics.tick(item); failed = false; pending = true; refresh();
        if (NebulaLinkShortcutState.shouldStop(phase())) { runTunnel("tunnel.stop"); return; }
        NebulaLink.call("settings.get", null, result -> {
            if (!isAttachedToWindow()) { pending = false; return; }
            if (!result.ok) { complete(false, result.error); return; }
            if (result.data == null || result.data.optString("selected_server_id").isEmpty()) {
                pending = false; refresh(); owner.presentFragment(new NebulaServersFragment());
            } else runTunnel("tunnel.start");
        });
    }
    private void runTunnel(String method) { NebulaLink.call(method, null, result -> complete(result.ok, result.error)); }
    private void complete(boolean ok, String error) {
        pending = false; failed = !ok;
        if (!isAttachedToWindow()) return;
        refresh(); if (!ok) report(error);
    }
    private void report(String error) { Toast.makeText(getContext(), error == null ? label(NebulaLinkShortcutState.ERROR) : error, Toast.LENGTH_LONG).show(); }
    private void showMenu() {
        if (owner == null || !isAttachedToWindow()) return;
        JSONObject status = NebulaLink.status(), server = status == null ? null : status.optJSONObject("server");
        ItemOptions menu = ItemOptions.makeOptions(owner, item, true, false, false);
        menu.addText("NebulaLink · " + label(state()), 14);
        if (server != null && NebulaLink.isRoutingThroughTunnel()) menu.addText(NebulaLinkRow.serverLabel(server), 13);
        menu.add(R.drawable.msg_language, NebulaText.text("Выбрать сервер", "Choose server"), () -> owner.presentFragment(new NebulaServersFragment()));
        menu.add(R.drawable.msg_speed, NebulaText.text("Пинг выбранного сервера", "Ping selected server"), this::probe);
        menu.show();
    }
    private void probe() {
        if (probing) return;
        probing = true;
        NebulaLink.call("settings.get", null, settings -> {
            if (!isAttachedToWindow()) { probing = false; return; }
            String id = settings.data == null ? "" : settings.data.optString("selected_server_id");
            if (!settings.ok) { probing = false; report(settings.error); return; }
            if (id.isEmpty()) { probing = false; owner.presentFragment(new NebulaServersFragment()); return; }
            JSONObject payload = new JSONObject();
            try { payload.put("ids", new JSONArray().put(id)); } catch (JSONException e) { probing = false; report(e.getMessage()); return; }
            NebulaLink.call("probe.servers", payload, result -> {
                probing = false;
                if (!isAttachedToWindow()) return;
                if (!result.ok) { report(result.error); return; }
                int ms = result.data == null ? -1 : result.data.optInt(id, -1);
                report(ms > 0 ? "NebulaLink · " + ms + NebulaText.text(" мс", " ms") : NebulaText.text("Сервер недоступен", "Server unreachable"));
            });
        });
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (preview >= 0) return;
        prefs().registerOnSharedPreferenceChangeListener(preferencesListener);
        NebulaLink.addStatusListener(statusListener);
        NotificationCenter.getGlobalInstance().addObserver(proxyListener, NotificationCenter.proxySettingsChanged);
        refresh();
    }
    @Override protected void onDetachedFromWindow() {
        if (preview < 0) {
            prefs().unregisterOnSharedPreferenceChangeListener(preferencesListener);
            NebulaLink.removeStatusListener(statusListener);
            NotificationCenter.getGlobalInstance().removeObserver(proxyListener, NotificationCenter.proxySettingsChanged);
        }
        super.onDetachedFromWindow();
    }
    private static int dp(float value) { return AndroidUtilities.dp(value); }
    @Override protected void onDraw(Canvas canvas) {
        int state = state(); NebulaTheme theme = NebulaTheme.of(getContext());
        int color = state == NebulaLinkShortcutState.ON ? theme.success() : state == NebulaLinkShortcutState.ERROR ? 0xffd95858
                : state == NebulaLinkShortcutState.CONNECTING ? theme.primary() : theme.onSurfaceVariant();
        int x = getWidth()/2, y = getHeight()/2;
        icon.setColorFilter(color, PorterDuff.Mode.SRC_IN); icon.setBounds(x-dp(12),y-dp(12),x+dp(12),y+dp(12)); icon.draw(canvas);
        paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.7f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (state == NebulaLinkShortcutState.ON) {
            canvas.drawLine(x-dp(4), y, x-dp(1), y+dp(3), paint);
            canvas.drawLine(x-dp(1), y+dp(3), x+dp(4), y-dp(3), paint);
        } else if (state == NebulaLinkShortcutState.ERROR) {
            canvas.drawLine(x, y-dp(4), x, y, paint);
            paint.setStyle(Paint.Style.FILL); canvas.drawCircle(x, y+dp(3), dp(.9f), paint);
        }
        if (state == NebulaLinkShortcutState.CONNECTING) {
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.5f));
            arc.set(x-dp(4),y-dp(4),x+dp(4),y+dp(4));
            canvas.drawArc(arc, preview >= 0 ? -90 : android.os.SystemClock.uptimeMillis()%1200*360f/1200, 100, false, paint);
            if (preview < 0 && isShown()) postInvalidateDelayed(32);
        }
    }
}
