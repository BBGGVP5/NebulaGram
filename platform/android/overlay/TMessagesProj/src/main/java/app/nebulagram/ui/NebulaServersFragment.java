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
        load();
    }

    @Override
    public void onPause() {
        NebulaLink.removeStatusListener(statusListener);
        super.onPause();
    }

    @Override
    public void onFragmentDestroy() {
        NebulaLink.removeStatusListener(statusListener);
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
        content.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6),
                AndroidUtilities.dp(12), AndroidUtilities.dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        load();
        return root;
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

        List<JSONObject> servers = serverList(data.optJSONArray("servers"));

        NebulaCard actions = new NebulaCard(context);
        actions.add(new NebulaRow(context)
                .icon(R.drawable.msg_speed)
                .title(LocaleController.getString(probing
                        ? R.string.NebulaProbing : R.string.NebulaProbe))
                .subtitle(LocaleController.getString(R.string.NebulaProbeSub), false)
                .withClick(v -> probe()));
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
        NebulaTheme theme = NebulaTheme.of(context);
        int latency = server.optInt("latency_ms");
        String latencyLabel = latency > 0
                ? latency + " " + LocaleController.getString(R.string.NebulaMs)
                : LocaleController.getString(latency < 0 ? R.string.NebulaNoReply : R.string.NebulaLatencyUnknown);
        int latencyColor = latency < 0 ? (theme.isDark() ? 0xFFFFB4AB : 0xFFBA1A1A)
                : latency > 0 && latency < 300 ? theme.success() : theme.onSurfaceVariant();
        row.badge(latencyLabel, latencyColor);
        row.withClick(v -> select(id));
        return row;
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

    /**
     * Замер идёт по всем серверам сразу и занимает секунды, поэтому строка
     * меняет подпись: иначе непонятно, нажалось ли.
     */
    private void probe() {
        if (probing) {
            return;
        }
        probing = true;
        load();
        NebulaLink.call("probe.servers", null, result -> {
            probing = false;
            if (!result.ok) {
                report(result.error);
            }
            load();
        });
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
