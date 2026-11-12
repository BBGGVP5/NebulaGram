package app.nebulagram.ui;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
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
import org.telegram.ui.Components.EditTextBoldCursor;

import app.nebulagram.nebulalink.NebulaLink;

/**
 * Маршрутизация NebulaLink: правила «домен или подсеть → напрямую, через
 * прокси или блокировать» и готовые наборы.
 *
 * <p>Экран только редактирует таблицу. Разбор правил и перевод их в
 * конфигурацию Xray живут в ядре (routing.set отказывает с понятной ошибкой),
 * поэтому здесь нет второй, расходящейся с ядром проверки. Трафик самого
 * Telegram ядро держит в туннеле, пока пользователь явно не снимет эту
 * гарантию переключателем ниже.
 */
public class NebulaRoutingFragment extends BaseFragment {

    private static final String[] ACTIONS = {"proxy", "direct", "block"};

    private LinearLayout content;
    private JSONObject view;
    private boolean busy;

    @Override
    public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(NebulaText.text("Маршрутизация", "Routing"));
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
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, root);
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onFragmentDestroy() {
        content = null;
        super.onFragmentDestroy();
    }

    private void load() {
        NebulaLink.call("routing.get", null, result -> {
            if (result.ok && result.data != null) {
                render(result.data);
            } else if (!result.ok) {
                report(result.error);
            }
        });
    }

    private JSONObject routing() {
        JSONObject routing = view == null ? null : view.optJSONObject("routing");
        return routing == null ? new JSONObject() : routing;
    }

    private void render(JSONObject data) {
        if (content == null) return;
        view = data;
        Context context = content.getContext();
        content.removeAllViews();
        JSONObject routing = routing();
        String preset = routing.optString("preset");

        content.addView(NebulaCard.header(context, NebulaText.text("ГОТОВЫЕ НАБОРЫ", "PRESETS")));
        NebulaCard presets = new NebulaCard(context);
        JSONArray list = data.optJSONArray("presets");
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id");
            String title = NebulaText.text(item.optString("title_ru"), item.optString("title_en"));
            presets.add(new NebulaRow(context).icon(R.drawable.msg_list)
                    .title(title)
                    .subtitle(presetHint(id), false)
                    .selection(id.equals(preset))
                    .withClick(v -> confirmPreset(id, title)));
        }
        content.addView(presets, cardParams());

        content.addView(NebulaCard.header(context, NebulaText.text("ОБЩИЕ", "GENERAL")));
        NebulaCard general = new NebulaCard(context);
        String fallback = routing.optString("default", "");
        general.add(new NebulaRow(context).icon(R.drawable.msg_permissions)
                .title(NebulaText.text("Остальной трафик", "Everything else"))
                .subtitle(actionTitle(fallback.isEmpty() ? "proxy" : fallback), true)
                .trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> chooseAction(NebulaText.text("Остальной трафик", "Everything else"),
                        fallback.isEmpty() ? "proxy" : fallback, false, action -> {
                            JSONObject next = copy(routing());
                            put(next, "default", "proxy".equals(action) ? "" : action);
                            put(next, "preset", "");
                            save(next);
                        })));
        boolean telegramProxied = !routing.optBoolean("telegram_follows_rules");
        NebulaRow telegram = new NebulaRow(context).icon(R.drawable.msg_secret)
                .title(NebulaText.text("Telegram всегда через прокси", "Telegram always through the proxy"))
                .subtitle(NebulaText.text("Правила ниже не уводят сам мессенджер из туннеля",
                        "The rules below never take the messenger itself out of the tunnel"), false)
                .trailing(NebulaRow.TRAIL_SWITCH)
                .checked(telegramProxied);
        telegram.withClick(v -> {
            if (telegramProxied) confirmTelegramFollowsRules();
            else setTelegramFollowsRules(false);
        });
        general.add(telegram);
        content.addView(general, cardParams());

        content.addView(NebulaCard.header(context, NebulaText.text("ПРАВИЛА", "RULES")));
        NebulaCard rules = new NebulaCard(context);
        JSONArray items = routing.optJSONArray("rules");
        boolean geoMissing = false;
        for (int i = 0; items != null && i < items.length(); i++) {
            JSONObject rule = items.optJSONObject(i);
            if (rule == null) continue;
            final int index = i;
            String match = rule.optString("match");
            if (!data.optBoolean("geo_assets") && (match.startsWith("geoip:") || match.startsWith("geosite:"))) {
                geoMissing = true;
            }
            rules.add(new NebulaRow(context).icon(R.drawable.msg_language)
                    .title(match)
                    .leftToRightText()
                    .subtitle(actionTitle(rule.optString("action")), true)
                    .trailing(NebulaRow.TRAIL_CHEVRON)
                    .withClick(v -> editRule(index, rule)));
        }
        rules.add(new NebulaRow(context).icon(R.drawable.msg_edit)
                .title(NebulaText.text("Добавить правило", "Add rule"))
                .subtitle(NebulaText.text("Домен, суффикс, IP или подсеть: example.com, *.ru, 10.0.0.0/8",
                        "Domain, suffix, IP or subnet: example.com, *.ru, 10.0.0.0/8"), false)
                .withClick(v -> askMatch(-1, "", "direct")));
        content.addView(rules, cardParams());
        if (geoMissing) {
            content.addView(NebulaMenuFragment.placeholder(context, NebulaText.text(
                    "Правила geoip: и geosite: работают, только когда в папке NebulaLink лежат geoip.dat и geosite.dat. Без них такие правила пропускаются.",
                    "geoip: and geosite: rules only work when geoip.dat and geosite.dat are in the NebulaLink folder. Without them such rules are skipped.")));
        }
        content.addView(NebulaMenuFragment.placeholder(context, NebulaText.text(
                "Правила проверяются сверху вниз, срабатывает первое совпавшее. Изменения применяются к работающему туннелю сразу.",
                "Rules are checked top to bottom and the first match wins. Changes apply to a running tunnel at once.")));
    }

    private static String presetHint(String id) {
        switch (id) {
            case "ru_direct":
                return NebulaText.text(".ru, .рф и .su — напрямую, остальное через прокси",
                        ".ru, .рф and .su direct, everything else through the proxy");
            case "telegram_only":
                return NebulaText.text("Через прокси только Telegram, остальное напрямую",
                        "Only Telegram through the proxy, everything else direct");
            case "all_proxy":
                return NebulaText.text("Без правил: весь трафик через прокси",
                        "No rules: all traffic through the proxy");
            default:
                return "";
        }
    }

    private static String actionTitle(String action) {
        switch (action) {
            case "direct":
                return NebulaText.text("Напрямую", "Direct");
            case "block":
                return NebulaText.text("Блокировать", "Block");
            default:
                return NebulaText.text("Через прокси", "Through the proxy");
        }
    }

    private interface ActionChoice { void chosen(String action); }

    private void chooseAction(String title, String current, boolean withDelete, ActionChoice choice) {
        if (getParentActivity() == null) return;
        int count = ACTIONS.length + (withDelete ? 1 : 0);
        CharSequence[] labels = new CharSequence[count];
        for (int i = 0; i < ACTIONS.length; i++) labels[i] = actionTitle(ACTIONS[i]);
        if (withDelete) labels[ACTIONS.length] = NebulaText.text("Удалить правило", "Delete rule");
        int selected = java.util.Arrays.asList(ACTIONS).indexOf(current);
        showDialog(new NebulaDialog.Builder(getParentActivity())
                .setTitle(title)
                .setSelectedIndex(selected)
                .setItems(labels, (dialog, which) -> choice.chosen(which < ACTIONS.length ? ACTIONS[which] : "delete"))
                .create());
    }

    private void editRule(int index, JSONObject rule) {
        String match = rule.optString("match");
        chooseAction(match, rule.optString("action"), true, action -> {
            JSONObject next = copy(routing());
            JSONArray rules = next.optJSONArray("rules");
            if (rules == null || index >= rules.length()) return;
            if ("delete".equals(action)) {
                rules.remove(index);
            } else {
                JSONObject changed = new JSONObject();
                put(changed, "match", match);
                put(changed, "action", action);
                try {
                    rules.put(index, changed);
                } catch (JSONException e) {
                    return;
                }
            }
            put(next, "preset", "");
            save(next);
        });
    }

    /** Сначала адрес, потом действие: так правило не сохранится наполовину. */
    private void askMatch(int index, String initial, String action) {
        if (getParentActivity() == null) return;
        Context context = getParentActivity();
        NebulaTheme theme = NebulaTheme.of(context);
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(theme.onSurface());
        input.setHintTextColor(theme.onSurfaceVariant());
        input.setBackground(null);
        input.setSingleLine();
        input.setHint("example.com");
        input.setText(initial);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8),
                AndroidUtilities.dp(24), AndroidUtilities.dp(8));
        showDialog(new NebulaDialog.Builder(context)
                .setTitle(NebulaText.text("Домен или подсеть", "Domain or subnet"))
                .setView(input)
                .setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
                    String typed = input.getText().toString().trim();
                    if (typed.isEmpty()) return;
                    AndroidUtilities.runOnUIThread(() -> chooseAction(typed, action, false, chosen -> {
                        JSONObject next = copy(routing());
                        JSONArray rules = next.optJSONArray("rules");
                        if (rules == null) {
                            rules = new JSONArray();
                            put(next, "rules", rules);
                        }
                        JSONObject rule = new JSONObject();
                        put(rule, "match", typed);
                        put(rule, "action", chosen);
                        rules.put(rule);
                        put(next, "preset", "");
                        save(next);
                    }));
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    private void confirmPreset(String id, String title) {
        if (getParentActivity() == null) return;
        showDialog(new NebulaDialog.Builder(getParentActivity())
                .setTitle(title)
                .setMessage(NebulaText.text("Текущие правила будут заменены набором.",
                        "The current rules will be replaced by the preset."))
                .setPositiveButton(NebulaText.text("Применить", "Apply"), (dialog, which) -> {
                    JSONObject payload = new JSONObject();
                    put(payload, "id", id);
                    send("routing.preset", payload);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create());
    }

    /** Снять гарантию для Telegram можно только явно: это и есть «пользователь сам сказал». */
    private void confirmTelegramFollowsRules() {
        if (getParentActivity() == null) return;
        showDialog(new NebulaDialog.Builder(getParentActivity())
                .setTitle(NebulaText.text("Пустить Telegram по правилам?", "Let Telegram follow the rules?"))
                .setMessage(NebulaText.text(
                        "Серверы Telegram пойдут туда, куда их отправят правила, — в том числе напрямую. Там, где Telegram заблокирован, он перестанет подключаться.",
                        "Telegram's servers will go wherever the rules send them, direct included. Where Telegram is blocked it will stop connecting."))
                .setPositiveButton(NebulaText.text("Пустить", "Allow"), (dialog, which) -> setTelegramFollowsRules(true))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> {
                    if (view != null) render(view);
                })
                .create());
    }

    private void setTelegramFollowsRules(boolean follows) {
        JSONObject next = copy(routing());
        put(next, "telegram_follows_rules", follows);
        save(next);
    }

    private void save(JSONObject routing) {
        JSONObject payload = new JSONObject();
        put(payload, "routing", routing);
        send("routing.set", payload);
    }

    private void send(String method, JSONObject payload) {
        if (busy) return;
        busy = true;
        NebulaLink.call(method, payload, result -> {
            busy = false;
            if (result.ok && result.data != null) {
                render(result.data);
            } else {
                // Ошибка ядра здесь — это текст о неверном правиле, его и показываем.
                if (!result.ok) report(result.error);
                if (view != null) render(view);
            }
        });
    }

    private static JSONObject copy(JSONObject source) {
        try {
            return new JSONObject(source.toString());
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static void put(JSONObject target, String key, Object value) {
        try {
            target.put(key, value);
        } catch (JSONException ignored) {
        }
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
