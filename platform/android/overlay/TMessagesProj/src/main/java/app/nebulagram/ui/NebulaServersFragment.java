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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.nebulagram.nebulalink.NebulaLink;

/**
 * Список серверов: то, чего не хватало, чтобы туннелем можно было пользоваться.
 *
 * <p>Схема меню умеет показывать настройки, но не список из девяноста строк,
 * приходящий из ядра. Поэтому экран свой: он спрашивает у ядра servers.list,
 * рисует строки и отправляет обратно server.select.
 *
 * <p>Сортировка по задержке, а не по имени: когда серверов много, единственный
 * вопрос к списку — какой из них быстрый.
 */
public class NebulaServersFragment extends BaseFragment {

    /** Серверов у подписки бывает под сотню, и все они нужны на одном экране. */
    private static final int PER_PAGE = 500;

    private LinearLayout content;
    private String selectedId = "";
    private boolean probing;

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
        Context context = content.getContext();
        content.removeAllViews();

        List<JSONObject> servers = sorted(data.optJSONArray("servers"));

        NebulaCard actions = new NebulaCard(context);
        actions.add(new NebulaRow(context)
                .icon(R.drawable.msg_speed)
                .title(LocaleController.getString(probing
                        ? R.string.NebulaProbing : R.string.NebulaProbe))
                .subtitle(LocaleController.getString(R.string.NebulaProbeSub), false)
                .withClick(v -> probe()));
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

    /**
     * Быстрые сверху, непроверенные внизу. Ноль в latency_ms означает "не
     * измеряли", и без этого разделения такой сервер выглядел бы мгновенным.
     */
    private List<JSONObject> sorted(JSONArray raw) {
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
        Collections.sort(servers, (a, b) -> Integer.compare(latencyOrLast(a), latencyOrLast(b)));
        return servers;
    }

    private int latencyOrLast(JSONObject server) {
        int latency = server.optInt("latency_ms");
        return latency > 0 ? latency : Integer.MAX_VALUE;
    }

    private View buildRow(Context context, JSONObject server) {
        String id = server.optString("id");
        String flag = server.optString("flag");
        String name = server.optString("name");
        String label = flag.isEmpty() ? name : flag + "  " + name;

        NebulaRow row = new NebulaRow(context)
                .icon(R.drawable.msg_language)
                .title(label.isEmpty() ? server.optString("address") : label);

        boolean selected = !id.isEmpty() && id.equals(selectedId);
        row.subtitle(describe(server, selected), selected);
        row.withClick(v -> select(id));
        return row;
    }

    /** "Выбран · 120 мс · VLESS" — состояние, скорость, протокол, в этом порядке. */
    private String describe(JSONObject server, boolean selected) {
        StringBuilder line = new StringBuilder();
        if (selected) {
            line.append(LocaleController.getString(R.string.NebulaSelected));
        }
        int latency = server.optInt("latency_ms");
        if (latency > 0) {
            if (line.length() > 0) {
                line.append(" · ");
            }
            line.append(latency).append(" ").append(LocaleController.getString(R.string.NebulaMs));
        }
        String protocol = server.optString("protocol");
        if (!protocol.isEmpty()) {
            if (line.length() > 0) {
                line.append(" · ");
            }
            line.append(protocol.toUpperCase());
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
