package app.nebulagram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EditTextBoldCursor;

import app.nebulagram.nebulalink.NebulaLink;

/** Optional connection before login; content and actions scroll together under IME. */
public class NebulaConnectFragment extends BaseFragment {
    /** Сколько серверов показывать на первом экране: остальное — в настройках. */
    private static final int VISIBLE_SERVERS = 100;

    private NebulaAuthArt art;
    private EditTextBoldCursor input;
    private NebulaButton connect;
    private NebulaButton another;
    private TextView status;
    private TextView title;
    private TextView subtitle;
    private LinearLayout field;
    private TextView hint;
    private LinearLayout servers;
    private boolean busy;
    private boolean destroyed;
    /** Выбранный сервер и общее их число, пока экран в режиме выбора. */
    private String selected;
    private int total;

    @Override
    public View createView(Context context) {
        destroyed = false;
        NebulaTheme theme = NebulaTheme.of(context);
        actionBar.setAddToContainer(false);
        actionBar.setVisibility(View.GONE);
        NebulaOnboardingLayout root = new NebulaOnboardingLayout(context);

        art = new NebulaAuthArt(NebulaAuthArt.KIND_LINK, theme.primary(), theme.onSurfaceVariant());
        ImageView image = new ImageView(context);
        image.setImageDrawable(art);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Плитки под рисунком нет: в макетах её не было, а здесь она ещё и
        // срезала расходящуюся волну по краям квадрата.
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(248), AndroidUtilities.dp(248));
        badgeParams.gravity = Gravity.CENTER_HORIZONTAL;
        badgeParams.bottomMargin = AndroidUtilities.dp(16);
        root.content.addView(image, badgeParams);

        title = NebulaIntroFragment.text(context, 30, theme.onSurface(), true);
        title.setGravity(Gravity.CENTER);
        title.setText(NebulaIntroFragment.highlight(
                LocaleController.getString(R.string.NebulaAuthLinkTitle), "NebulaLink", theme.primary()));
        root.content.addView(title, NebulaIntroFragment.width());
        subtitle = NebulaIntroFragment.text(context, 15, theme.onSurfaceVariant(), false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setText(LocaleController.getString(R.string.NebulaAuthLinkSubtitle));
        LinearLayout.LayoutParams subtitleParams = NebulaIntroFragment.width();
        subtitleParams.topMargin = AndroidUtilities.dp(12);
        root.content.addView(subtitle, subtitleParams);

        field = new LinearLayout(context);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(16), AndroidUtilities.dp(18), AndroidUtilities.dp(12));
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(theme.surfaceContainer());
        surface.setCornerRadius(AndroidUtilities.dp(22));
        field.setBackground(surface);
        TextView label = NebulaIntroFragment.text(context, 13, theme.primary(), true);
        label.setText(LocaleController.getString(R.string.NebulaAuthLinkField));
        field.addView(label, NebulaIntroFragment.width());

        input = new EditTextBoldCursor(context);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        input.setTextColor(theme.onSurface());
        input.setHintTextColor(theme.onSurfaceVariant());
        input.setCursorColor(theme.primary());
        input.setHint(LocaleController.getString(R.string.NebulaAuthLinkHint));
        input.setContentDescription(LocaleController.getString(R.string.NebulaAuthLinkField));
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        input.setBackground(null);
        input.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(10));
        input.setMinimumHeight(AndroidUtilities.dp(48));
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        field.addView(input, NebulaIntroFragment.width());
        LinearLayout.LayoutParams fieldParams = NebulaIntroFragment.width();
        fieldParams.topMargin = AndroidUtilities.dp(28);
        root.content.addView(field, fieldParams);

        hint = NebulaIntroFragment.text(context, 12, theme.onSurfaceVariant(), false);
        hint.setText(LocaleController.getString(R.string.NebulaAuthLinkFormats));
        // Значок папки перед перечислением: строка длинная, и без якоря слева
        // она читается как продолжение подписи поля, а не как отдельная справка.
        android.graphics.drawable.Drawable folder =
                androidx.core.content.ContextCompat.getDrawable(context, R.drawable.files_folder);
        if (folder != null) {
            folder.setColorFilter(theme.onSurfaceVariant(), android.graphics.PorterDuff.Mode.SRC_IN);
            int size = AndroidUtilities.dp(16);
            folder.setBounds(0, 0, size, size);
            hint.setCompoundDrawablesRelative(folder, null, null, null);
            hint.setCompoundDrawablePadding(AndroidUtilities.dp(8));
            hint.setGravity(android.view.Gravity.CENTER_VERTICAL);
        }
        LinearLayout.LayoutParams hintParams = NebulaIntroFragment.width();
        hintParams.topMargin = AndroidUtilities.dp(12);
        root.content.addView(hint, hintParams);

        // Список серверов подписки живёт на том же экране, а не на отдельном
        // шаге: индикатор прогресса рассчитан на четыре шага, и вставка пятого
        // сдвинула бы нумерацию во всём входе, включая экраны Telegram.
        servers = new LinearLayout(context);
        servers.setOrientation(LinearLayout.VERTICAL);
        servers.setVisibility(View.GONE);
        LinearLayout.LayoutParams serverParams = NebulaIntroFragment.width();
        serverParams.topMargin = AndroidUtilities.dp(20);
        root.content.addView(servers, serverParams);

        status = NebulaIntroFragment.text(context, 14, theme.onSurfaceVariant(), false);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusParams = NebulaIntroFragment.width();
        statusParams.topMargin = AndroidUtilities.dp(12);
        root.content.addView(status, statusParams);

        connect = new NebulaButton(context, NebulaButton.STYLE_FILLED);
        connect.setText(LocaleController.getString(R.string.NebulaConnect));
        connect.setOnClickListener(v -> submit());
        NebulaOnboardingLayout.action(connect);
        root.actions.addView(connect);
        another = new NebulaButton(context, NebulaButton.STYLE_TEXT);
        another.setText(LocaleController.getString(R.string.NebulaAuthPickAnother));
        another.setVisibility(View.GONE);
        another.setOnClickListener(v -> paste());
        NebulaOnboardingLayout.action(another);
        root.actions.addView(another);
        NebulaButton skip = new NebulaButton(context, NebulaButton.STYLE_TEXT);
        skip.setText(LocaleController.getString(R.string.NebulaAuthDirectAction));
        skip.setOnClickListener(v -> openLogin());
        NebulaOnboardingLayout.action(skip);
        root.actions.addView(skip);

        TextView optional = NebulaIntroFragment.text(context, 12, theme.onSurfaceVariant(), false);
        optional.setGravity(Gravity.CENTER);
        optional.setText(LocaleController.getString(R.string.NebulaAuthLinkOptional));
        root.actions.addView(optional, NebulaIntroFragment.width());
        LinearLayout.LayoutParams connectionParams = NebulaIntroFragment.width();
        connectionParams.topMargin = AndroidUtilities.dp(14);
        root.actions.addView(new NebulaAuthStatus(context, false), connectionParams);
        root.actions.addView(NebulaProgress.build(context, 4, 1));
        fragmentView = root;
        // Серверы могли остаться от прошлого запуска или прошлой попытки: тогда
        // вставлять ссылку заново незачем, сразу показываем выбор.
        NebulaLink.call("servers.list", page(), result -> {
            if (!destroyed && result.ok && result.data != null && result.data.optInt("total") > 0) {
                choose(result.data);
            }
        });
        return root;
    }

    private JSONObject page() {
        JSONObject payload = new JSONObject();
        try {
            payload.put("page", 1);
            payload.put("per_page", VISIBLE_SERVERS);
        } catch (JSONException e) {
            return null;
        }
        return payload;
    }

    /**
     * Главная кнопка. В режиме вставки — импорт подписки, в режиме выбора —
     * подключение выбранным сервером.
     */
    private void submit() {
        if (busy || destroyed) return;
        if (servers.getVisibility() == View.VISIBLE) {
            start();
            return;
        }
        String pasted = input.getText().toString().trim();
        if (pasted.isEmpty()) {
            // Пустое поле — это не согласие пропустить шаг: для пропуска рядом
            // стоит своя кнопка. Раньше «Подключить» молча уводила дальше, и
            // выглядело это так, будто подключение прошло.
            fail(LocaleController.getString(R.string.NebulaAuthLinkEmpty));
            AndroidUtilities.shakeView(input);
            input.requestFocus();
            return;
        }
        JSONObject payload = new JSONObject();
        try {
            payload.put("input", pasted);
        } catch (JSONException e) { return; }
        // onboarding.import, а не onboarding.connect: connect сразу поднимал
        // туннель через первый сервер подписки, и выбрать другой было негде —
        // список серверов на первом экране вообще не показывался.
        begin(LocaleController.getString(R.string.NebulaConnecting));
        NebulaLink.call("onboarding.import", payload, added -> {
            if (destroyed) return;
            if (!added.ok) {
                end();
                fail(added.error);
                return;
            }
            NebulaLink.call("servers.list", page(), listed -> {
                if (destroyed) return;
                end();
                if (!listed.ok) fail(listed.error);
                else if (listed.data == null || listed.data.optInt("total") == 0) {
                    fail(LocaleController.getString(R.string.NebulaAuthPickEmpty));
                } else choose(listed.data);
            });
        });
    }

    /** Поднимает туннель выбранным сервером; он же становится выбранным в ядре. */
    private void start() {
        JSONObject payload = new JSONObject();
        try {
            if (selected != null && !selected.isEmpty()) payload.put("id", selected);
        } catch (JSONException e) { return; }
        begin(LocaleController.getString(R.string.NebulaConnecting));
        NebulaLink.call("tunnel.start", payload, result -> {
            if (destroyed) return;
            end();
            if (result.ok) openLogin();
            else fail(result.error);
        });
    }

    /** Показывает серверы подписки вместо поля ввода. */
    private void choose(JSONObject data) {
        if (destroyed || servers == null) return;
        JSONArray list = data.optJSONArray("servers");
        if (list == null || list.length() == 0) {
            fail(LocaleController.getString(R.string.NebulaAuthPickEmpty));
            return;
        }
        selected = data.optString("selected");
        total = data.optInt("total", list.length());
        title.setText(LocaleController.getString(R.string.NebulaAuthPickTitle));
        subtitle.setText(LocaleController.getString(R.string.NebulaAuthPickSubtitle));
        field.setVisibility(View.GONE);
        hint.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
        another.setVisibility(View.VISIBLE);
        servers.setVisibility(View.VISIBLE);
        AndroidUtilities.hideKeyboard(input);
        render(list);
    }

    /** Возврат к полю: подписка уже сохранена, но добавить можно и вторую. */
    private void paste() {
        if (busy || destroyed) return;
        title.setText(NebulaIntroFragment.highlight(
                LocaleController.getString(R.string.NebulaAuthLinkTitle), "NebulaLink",
                NebulaTheme.of(getContext()).primary()));
        subtitle.setText(LocaleController.getString(R.string.NebulaAuthLinkSubtitle));
        servers.setVisibility(View.GONE);
        another.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
        field.setVisibility(View.VISIBLE);
        hint.setVisibility(View.VISIBLE);
        input.setText("");
        input.requestFocus();
    }

    private void render(JSONArray list) {
        Context context = servers.getContext();
        servers.removeAllViews();
        NebulaCard card = new NebulaCard(context);
        for (int i = 0; i < list.length(); i++) {
            JSONObject server = list.optJSONObject(i);
            if (server != null) card.add(row(context, server, list));
        }
        servers.addView(card, NebulaIntroFragment.width());
        int rest = total - list.length();
        if (rest > 0) {
            TextView more = NebulaIntroFragment.text(context, 12,
                    NebulaTheme.of(context).onSurfaceVariant(), false);
            more.setText(LocaleController.formatString(R.string.NebulaAuthPickMore, rest));
            LinearLayout.LayoutParams params = NebulaIntroFragment.width();
            params.topMargin = AndroidUtilities.dp(10);
            servers.addView(more, params);
        }
    }

    /** Строка сервера: та же, что в настройках NebulaLink, чтобы список узнавался. */
    private View row(Context context, JSONObject server, JSONArray list) {
        String id = server.optString("id");
        NebulaServerLabel label = new NebulaServerLabel(server.optString("name"),
                server.optString("address"), server.optString("flag"));
        NebulaRow row = new NebulaRow(context)
                .icon(R.drawable.msg_language)
                .emojiIcon(label.flag)
                .title(label.title);
        boolean active = !id.isEmpty() && id.equals(selected);
        String protocol = server.optString("protocol").toUpperCase(java.util.Locale.ROOT);
        String line = LocaleController.getString(R.string.NebulaSelected);
        row.subtitle(active ? (protocol.isEmpty() ? line : line + " · " + protocol) : protocol, active);
        row.selection(active);
        NebulaTheme theme = NebulaTheme.of(context);
        int latency = server.optInt("latency_ms");
        row.badge(latency > 0
                        ? latency + " " + LocaleController.getString(R.string.NebulaMs)
                        : LocaleController.getString(latency < 0
                                ? R.string.NebulaNoReply : R.string.NebulaLatencyUnknown),
                latency < 0 ? (theme.isDark() ? 0xFFFFB4AB : 0xFFBA1A1A)
                        : latency > 0 && latency < 300 ? theme.success() : theme.onSurfaceVariant());
        row.withClick(v -> {
            if (busy || id.isEmpty() || id.equals(selected)) return;
            selected = id;
            render(list);
        });
        return row;
    }

    private void begin(String message) {
        busy = true;
        input.setEnabled(false);
        connect.setEnabled(false);
        connect.setText(message);
        show(message);
    }

    private void end() {
        busy = false;
        input.setEnabled(true);
        connect.setEnabled(true);
        connect.setText(LocaleController.getString(R.string.NebulaConnect));
    }

    private void fail(String message) {
        show(message);
        status.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(
                org.telegram.ui.ActionBar.Theme.key_text_RedRegular));
    }

    private void show(String message) {
        status.setVisibility(View.VISIBLE);
        status.setText(message);
        status.setTextColor(NebulaTheme.of(getContext()).onSurfaceVariant());
    }

    private void openLogin() {
        if (destroyed) return;
        AndroidUtilities.hideKeyboard(input);
        // Без удаления предыдущего экрана: с параметром true стек оставался
        // пустым, и с ввода номера некуда было вернуться — ни кнопкой, ни
        // жестом от края. Приветствие остаётся позади, как и ожидается.
        presentFragment(new org.telegram.ui.LoginActivity());
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        if (art != null) art.detach();
        art = null;
        super.onFragmentDestroy();
    }

    @Override
    public boolean isLightStatusBar() { return !NebulaTheme.of(getContext()).isDark(); }
}
