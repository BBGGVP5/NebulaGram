package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.AlertDialog;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;

import app.nebulagram.nebulalink.NebulaLink;

/**
 * Список серверов: то, чего не хватало, чтобы туннелем можно было пользоваться.
 *
 * <p>Схема меню умеет показывать настройки, но не список из девяноста строк,
 * приходящий из ядра. Поэтому экран свой: он спрашивает у ядра servers.list,
 * рисует строки и отправляет обратно server.select.
 *
 * <p>Порядок подписки сохраняется по умолчанию. Сортировку по задержке
 * пользователь выбирает отдельно; ядро применяет её до разбивки на страницы.
 */
public class NebulaServersFragment extends BaseFragment {

    /** Серверов у подписки бывает под сотню, и все они нужны на одном экране. */
    private static final int PER_PAGE = 500;

    private LinearLayout content;
    private String selectedId = "";
    private boolean probing;
    private boolean cancelling;
    private String probeRequestId;
    private int probeCompleted, probeTotal;
    private NebulaRow probeAction;
    private final HashMap<String, NebulaRow> serverRows = new HashMap<>();
    private final NebulaLink.ProbeListener probeListener = this::onProbeProgress;
    private JSONObject lastData;
    private final NebulaLink.StatusListener statusListener = status -> {
        if (lastData != null) {
            render(lastData);
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        NebulaLink.addStatusListener(statusListener);
        NebulaLink.addProbeListener(probeListener);
        load();
    }

    @Override
    public void onPause() {
        // Уходя с экрана, проверку прекращаем: девяносто серверов продолжали
        // опрашиваться в фоне, хотя смотреть на результат стало некому.
        cancelProbe();
        stopPendingAnimation();
        NebulaLink.removeStatusListener(statusListener);
        NebulaLink.removeProbeListener(probeListener);
        super.onPause();
    }

    @Override
    public void onFragmentDestroy() {
        cancelProbe();
        stopPendingAnimation();
        probeRequestId = null;
        NebulaLink.removeProbeListener(probeListener);
        NebulaLink.removeStatusListener(statusListener);
        serverRows.clear();
        probeAction = null;
        content = null;
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.nl_servers));
        actionBar.setBackgroundColor(theme.surface());
        actionBar.setTitleColor(theme.onSurface());
        actionBar.setItemsColor(theme.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(theme.surface());

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                AndroidUtilities.dp(16), AndroidUtilities.dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        load();
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, root);
    }

    private void load() {
        JSONObject payload = new JSONObject();
        try {
            payload.put("per_page", PER_PAGE);
            payload.put("page", 1);
        } catch (JSONException e) {
            return;
        }
        NebulaLink.call("servers.list", payload, result -> {
            if (result.ok && result.data != null) {
                selectedId = result.data.optString("selected", "");
                render(result.data);
            } else if (!result.ok) {
                report(result.error);
            }
        });
    }

    private void render(JSONObject data) {
        if (content == null) {
            return;
        }
        lastData = data;
        Context context = content.getContext();
        content.removeAllViews();
        serverRows.clear();

        List<JSONObject> servers = serverList(data.optJSONArray("servers"));

        NebulaCard actions = new NebulaCard(context);
        probeAction = new NebulaRow(context).icon(R.drawable.msg_speed)
                .subtitle(LocaleController.getString(R.string.NebulaProbeSub) + "\n"
                        + NebulaText.text("≈ означает оценку: время GET в Nimbo Ping делится на 3,3 и округляется до ближайшей миллисекунды. Другие проверки сохраняют исходный смысл.",
                        "≈ marks an estimate: Nimbo Ping GET time divided by 3.3, rounded to the nearest millisecond. Other checks keep their original meaning."), false)
                .withClick(v -> { if (probing) cancelProbe(); else probe(); });
        updateProbeAction();
        actions.add(probeAction);
        boolean byLatency = "latency".equals(data.optString("sort", "default"));
        actions.add(new NebulaRow(context).icon(R.drawable.msg_customize)
                .title(LocaleController.getString(R.string.NebulaServerSort))
                .subtitle(LocaleController.getString(byLatency
                        ? R.string.NebulaSortLatency : R.string.NebulaSortDefault), true)
                .trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> chooseSort()));
        content.addView(actions, cardParams());

        if (servers.isEmpty()) {
            content.addView(NebulaMenuFragment.placeholder(context,
                    LocaleController.getString(R.string.NebulaNoServers)));
            return;
        }

        int total = data.optInt("total", servers.size());
        content.addView(NebulaCard.header(context, LocaleController.formatString(
                R.string.NebulaShownOf, servers.size(), total)));

        NebulaCard list = new NebulaCard(context);
        for (JSONObject server : servers) {
            list.add(buildRow(context, server));
        }
        content.addView(list, cardParams());
    }

    /** The core has already ordered the complete list before pagination. */
    private List<JSONObject> serverList(JSONArray raw) {
        List<JSONObject> servers = new ArrayList<>();
        if (raw == null) {
            return servers;
        }
        for (int i = 0; i < raw.length(); i++) {
            JSONObject server = raw.optJSONObject(i);
            if (server != null) {
                servers.add(server);
            }
        }
        return servers;
    }

    private void chooseSort() {
        if (getParentActivity() == null) {
            return;
        }
        showDialog(new AlertDialog.Builder(getParentActivity())
                .setTitle(LocaleController.getString(R.string.NebulaServerSort))
                .setItems(new CharSequence[]{
                        LocaleController.getString(R.string.NebulaSortDefault),
                        LocaleController.getString(R.string.NebulaSortLatency)
                }, (dialog, which) -> {
                    JSONObject settings = new JSONObject();
                    try {
                        settings.put("server_sort", which == 1 ? "latency" : "default");
                    } catch (JSONException e) {
                        return;
                    }
                    NebulaLink.call("settings.set", settings, result -> {
                        if (result.ok) {
                            load();
                        } else {
                            report(result.error);
                        }
                    });
                }).create());
    }

    private View buildRow(Context context, JSONObject server) {
        String id = server.optString("id");
        NebulaServerLabel label = new NebulaServerLabel(server.optString("name"),
                server.optString("address"), server.optString("flag"));

        NebulaRow row = new NebulaRow(context)
                .icon(R.drawable.msg_language)
                .emojiIcon(label.flag)
                .title(label.title);

        boolean selected = !id.isEmpty() && id.equals(selectedId);
        JSONObject status = NebulaLink.status();
        JSONObject active = status == null ? null : status.optJSONObject("server");
        boolean connected = active != null && "connected".equals(status.optString("state"))
                && id.equals(active.optString("id")) && NebulaLink.isRoutingThroughTunnel();
        row.subtitle(describe(server, selected, connected), selected);
        row.selection(selected);
        if (connected) {
            row.connected(true);
        }
        updateLatency(row, server);
        serverRows.put(id, row);
        row.withClick(v -> select(id));
        return row;
    }

    /** Идентификаторы серверов, ответа по которым в этом проходе ещё нет. */
    private final java.util.HashSet<String> pending = new java.util.HashSet<>();
    private int pendingFrame;
    private final Runnable pendingTick = new Runnable() {
        @Override public void run() {
            if (pending.isEmpty() || content == null) return;
            pendingFrame = (pendingFrame + 1) % 3;
            NebulaTheme theme = NebulaTheme.of(content.getContext());
            String dots = "···".substring(0, pendingFrame + 1);
            for (String id : pending) {
                NebulaRow row = serverRows.get(id);
                if (row != null) row.badge(dots, theme.onSurfaceVariant());
            }
            AndroidUtilities.runOnUIThread(this, 350);
        }
    };

    private void startPendingAnimation(java.util.Collection<String> ids) {
        pending.clear();
        pending.addAll(ids);
        pendingFrame = -1;
        AndroidUtilities.cancelRunOnUIThread(pendingTick);
        AndroidUtilities.runOnUIThread(pendingTick);
    }

    private void stopPendingAnimation() {
        if (pending.isEmpty()) return;
        java.util.ArrayList<String> stale = new java.util.ArrayList<>(pending);
        pending.clear();
        AndroidUtilities.cancelRunOnUIThread(pendingTick);
        // Вернуть строкам их настоящее значение: анимация подменяла только текст.
        if (lastData != null) {
            for (JSONObject server : serverList(lastData.optJSONArray("servers"))) {
                if (!stale.contains(server.optString("id"))) continue;
                NebulaRow row = serverRows.get(server.optString("id"));
                if (row != null) updateLatency(row, server);
            }
        }
    }

    private void updateLatency(NebulaRow row, JSONObject server) {
        NebulaTheme theme = NebulaTheme.of(row.getContext());
        int latency = server.optInt("latency_ms");
        String method = server.optString("latency_method");
        long checkedAt = server.optLong("checked_at");
        row.badge(NebulaLatency.format(latency, method, checkedAt,
                        LocaleController.getString(R.string.NebulaMs),
                        LocaleController.getString(R.string.NebulaLatencyUnknown),
                        LocaleController.getString(R.string.NebulaNoReply)),
                latency < 0 ? (theme.isDark() ? 0xFFFFB4AB : 0xFFBA1A1A)
                        : NebulaLatency.isMeasured(latency, checkedAt) && NebulaLatency.displayMillis(latency, method) < 300
                        ? theme.success() : theme.onSurfaceVariant());
    }

    /** Latency lives in the trailing badge; the subtitle names protocol and state. */
    private String describe(JSONObject server, boolean selected, boolean connected) {
        StringBuilder line = new StringBuilder();
        if (connected || selected) {
            line.append(LocaleController.getString(connected ? R.string.NebulaConnected : R.string.NebulaSelected));
        }
        String protocol = server.optString("protocol");
        if (!protocol.isEmpty()) {
            if (line.length() > 0) {
                line.append(" · ");
            }
            line.append(protocol.toUpperCase(Locale.ROOT));
        }
        return line.toString();
    }

    private void select(String id) {
        if (id.isEmpty()) {
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("id", id);
        } catch (JSONException e) {
            return;
        }
        NebulaLink.call("server.select", payload, result -> {
            if (result.ok) {
                selectedId = id;
                load();
            } else {
                report(result.error);
            }
        });
    }

    private void updateProbeAction() {
        if (probeAction == null) return;
        probeAction.title(probing
                ? NebulaText.text(cancelling ? "Отмена… " : "Отменить проверку · ",
                        cancelling ? "Cancelling… " : "Cancel check · ") + probeCompleted + "/" + probeTotal
                : LocaleController.getString(R.string.NebulaProbe));
    }

    private void onProbeProgress(JSONObject progress) {
        if (probeRequestId == null || !probeRequestId.equals(progress.optString("request_id"))) return;
        if (progress.optBoolean("cancelled")) cancelling = true;
        probeCompleted = progress.optInt("completed", probeCompleted);
        probeTotal = progress.optInt("total", probeTotal);
        updateProbeAction();
        String id = progress.optString("id");
        if (lastData == null || id.isEmpty() || !progress.has("latency_ms")) return;
        for (JSONObject server : serverList(lastData.optJSONArray("servers"))) {
            if (!id.equals(server.optString("id"))) continue;
            try {
                server.put("latency_ms", progress.optInt("latency_ms"));
                server.put("latency_method", progress.optString("latency_method"));
                server.put("checked_at", progress.optLong("checked_at"));
            } catch (JSONException ignored) { return; }
            pending.remove(id);
            NebulaRow row = serverRows.get(id);
            if (row != null) updateLatency(row, server);
            break;
        }
    }

    private void probe() {
        if (probing || lastData == null) return;
        JSONArray ids = new JSONArray();
        for (JSONObject server : serverList(lastData.optJSONArray("servers"))) {
            String id = server.optString("id");
            if (!id.isEmpty()) ids.put(id);
        }
        // Empty ids means all servers to the backend; never send it for an empty page.
        if (ids.length() == 0) return;
        String requestId = UUID.randomUUID().toString();
        JSONObject payload = new JSONObject();
        try {
            payload.put("ids", ids);
            payload.put("timeout", 5);
            payload.put("request_id", requestId);
        } catch (JSONException ignored) { return; }
        probeRequestId = requestId;
        probing = true; cancelling = false; probeCompleted = 0; probeTotal = ids.length();
        java.util.ArrayList<String> waiting = new java.util.ArrayList<>();
        for (int i = 0; i < ids.length(); i++) waiting.add(ids.optString(i));
        startPendingAnimation(waiting);
        updateProbeAction();
        NebulaLink.call("probe.servers", payload, result -> {
            if (!requestId.equals(probeRequestId)) return;
            boolean wasCancelled = cancelling;
            probeRequestId = null; probing = false; cancelling = false;
            stopPendingAnimation();
            if (!result.ok && !wasCancelled) report(NebulaText.text("Не удалось проверить серверы", "Could not check servers"));
            updateProbeAction();
            if (content != null) load();
        });
    }

    private void cancelProbe() {
        if (probeRequestId == null || cancelling) return;
        JSONObject payload = new JSONObject();
        try { payload.put("request_id", probeRequestId); } catch (JSONException ignored) { return; }
        cancelling = true;
        updateProbeAction();
        NebulaLink.call("probe.cancel", payload, null);
    }

    private void report(String message) {
        if (getParentActivity() != null && message != null) {
            Toast.makeText(getParentActivity(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(6);
        return params;
    }
}
