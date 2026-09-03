package app.nebulagram.ui;

import android.content.Context;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EditTextBoldCursor;

import app.nebulagram.nebulalink.NebulaLink;

/**
 * Renders a NebulaLink screen from the schema the Go core returns.
 *
 * <p>There is no list of settings in this file. {@code menu.get} describes every
 * screen — its sections, rows, widget kinds, options and commands — and this
 * class turns that description into Material 3 views. A new option is therefore
 * added once, in Go, and appears on Android, iOS and desktop at the same time;
 * the core's own test refuses a row whose command does not exist, so a dead row
 * cannot reach a screen.
 */
public class NebulaMenuFragment extends BaseFragment {

    public static final String SCREEN_HOME = "nebulalink.home";

    private final String screenId;

    private JSONObject screen;
    private JSONObject settings;
    private LinearLayout content;

    public NebulaMenuFragment() {
        this(SCREEN_HOME);
    }

    public NebulaMenuFragment(String screenId) {
        this.screenId = screenId;
    }

    @Override
    public boolean onFragmentCreate() {
        load();
        return super.onFragmentCreate();
    }

    /** Pulls the schema and the current values, then draws. */
    private void load() {
        NebulaLink.call("menu.get", null, menuResult -> {
            if (!menuResult.ok) {
                return;
            }
            screen = findScreen(menuResult.array);
            NebulaLink.call("settings.get", null, settingsResult -> {
                if (settingsResult.ok) {
                    settings = settingsResult.data;
                }
                rebuild();
            });
        });
    }

    private JSONObject findScreen(JSONArray screens) {
        if (screens == null) {
            return null;
        }
        for (int i = 0; i < screens.length(); i++) {
            JSONObject candidate = screens.optJSONObject(i);
            if (candidate != null && screenId.equals(candidate.optString("id"))) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.NebulaLinkName));
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
        content.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4),
                AndroidUtilities.dp(12), AndroidUtilities.dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rebuild();
        return root;
    }

    /** Draws the screen from scratch; cheap enough to call after every change. */
    private void rebuild() {
        if (content == null || screen == null) {
            return;
        }
        Context context = content.getContext();
        content.removeAllViews();
        actionBar.setTitle(localized(screen.optString("title_key"), screen.optString("title")));

        JSONArray sections = screen.optJSONArray("sections");
        if (sections == null) {
            return;
        }
        for (int s = 0; s < sections.length(); s++) {
            JSONObject section = sections.optJSONObject(s);
            if (section == null) {
                continue;
            }
            String header = localized(section.optString("title_key"), section.optString("title"));
            if (header != null && header.length() > 0) {
                content.addView(NebulaCard.header(context, header));
            }

            NebulaCard card = new NebulaCard(context);
            JSONArray rows = section.optJSONArray("rows");
            for (int r = 0; rows != null && r < rows.length(); r++) {
                View view = buildRow(context, rows.optJSONObject(r));
                if (view != null) {
                    card.add(view);
                }
            }
            if (!card.isEmpty()) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.topMargin = AndroidUtilities.dp(6);
                content.addView(card, params);
            }
        }
    }

    private View buildRow(Context context, JSONObject row) {
        if (row == null) {
            return null;
        }
        String type = row.optString("type");
        String key = row.optString("key");
        String title = localized(row.optString("title_key"), row.optString("title"));
        String hint = localized(row.optString("subtitle_key"), row.optString("subtitle"));

        // A capability the platform does not have hides its row: VPN mode is
        // the current example, since proxy mode is what a sideloaded build gets.
        String requires = row.optString("visible_if");
        if ("vpn_supported".equals(requires)) {
            return null;
        }

        NebulaRow view = new NebulaRow(context).icon(iconFor(row.optString("icon"))).title(title);

        switch (type) {
            case "nav":
                view.subtitle(hint, false).trailing(NebulaRow.TRAIL_CHEVRON);
                String target = row.optString("screen");
                view.setOnClickListener(v -> presentFragment(new NebulaMenuFragment(target)));
                break;

            case "switch":
                view.subtitle(hint, false).trailing(NebulaRow.TRAIL_SWITCH)
                        .checked(settings != null && settings.optBoolean(key));
                view.setOnClickListener(v -> {
                    boolean enabled = view.toggleChecked();
                    updateSetting(key, enabled);
                });
                break;

            case "select":
                view.subtitle(optionTitle(row, currentValue(key)), true)
                        .trailing(NebulaRow.TRAIL_CHEVRON);
                view.setOnClickListener(v -> showOptions(context, row, key, view));
                break;

            case "text":
            case "number":
                view.subtitle(displayValue(key, hint), true).trailing(NebulaRow.TRAIL_CHEVRON);
                view.setOnClickListener(v -> showEditor(context, row, key, "number".equals(type), view));
                break;

            case "action":
                view.subtitle(hint, false);
                if (row.optBoolean("destructive")) {
                    view.destructive();
                }
                String command = row.optString("command");
                view.setOnClickListener(v -> {
                    // Часть команд без данных бессмысленна: подписку и ключ
                    // сначала надо куда-то вставить. Раньше строка молча
                    // отправляла пустой запрос, и было непонятно, куда вводить.
                    if ("subscription.add".equals(command)) {
                        askAndRun(context, title, command, "url");
                    } else if ("server.addLink".equals(command)) {
                        askAndRun(context, title, command, "link");
                    } else {
                        runCommand(command, title);
                    }
                });
                break;

            case "card":
                return buildCard(context, key, title);

            case "info":
            default:
                view.subtitle(hint, false);
                break;
        }
        return view;
    }

    /**
     * The two big cards on the home screen. They are rows in the schema like
     * any other, but the state they show — a live tunnel — deserves more than
     * a line of text.
     */
    private View buildCard(Context context, String key, String title) {
        if ("connection".equals(key)) {
            return new NebulaConnectionCard(context, this);
        }
        NebulaRow row = new NebulaRow(context)
                .icon(iconFor("globe"))
                .title(title)
                .trailing(NebulaRow.TRAIL_CHEVRON);
        if ("selected_server".equals(key) || "server_list".equals(key)) {
            row.setOnClickListener(v -> presentFragment(new NebulaMenuFragment("nebulalink.servers")));
        }
        return row;
    }

    // --- values -------------------------------------------------------------

    private String currentValue(String key) {
        return settings == null ? "" : settings.optString(key, "");
    }

    private String displayValue(String key, String fallback) {
        String value = currentValue(key);
        return value.isEmpty() ? fallback : value;
    }

    /** Resolves the label of the option a select row currently holds. */
    private String optionTitle(JSONObject row, String value) {
        JSONArray options = row.optJSONArray("options");
        for (int i = 0; options != null && i < options.length(); i++) {
            JSONObject option = options.optJSONObject(i);
            if (option != null && value.equals(option.optString("value"))) {
                return localized(option.optString("title_key"), option.optString("title"));
            }
        }
        return value;
    }

    private void showOptions(Context context, JSONObject row, String key, NebulaRow view) {
        JSONArray options = row.optJSONArray("options");
        if (options == null || options.length() == 0) {
            return;
        }
        CharSequence[] labels = new CharSequence[options.length()];
        String[] values = new String[options.length()];
        for (int i = 0; i < options.length(); i++) {
            JSONObject option = options.optJSONObject(i);
            labels[i] = localized(option.optString("title_key"), option.optString("title"));
            values[i] = option.optString("value");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(localized(row.optString("title_key"), row.optString("title")));
        builder.setItems(labels, (dialog, which) -> {
            updateSetting(key, values[which]);
            view.subtitle(labels[which], true);
        });
        showDialog(builder.create());
    }

    private void showEditor(Context context, JSONObject row, String key, boolean numeric, NebulaRow view) {
        NebulaTheme theme = NebulaTheme.of(context);

        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(theme.onSurface());
        input.setHintTextColor(theme.onSurfaceVariant());
        input.setBackground(null);
        input.setSingleLine();
        input.setText(currentValue(key));
        input.setInputType(numeric
                ? InputType.TYPE_CLASS_NUMBER
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8),
                AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(localized(row.optString("title_key"), row.optString("title")));
        builder.setView(input);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            String typed = input.getText().toString().trim();
            if (numeric) {
                try {
                    updateSetting(key, Integer.parseInt(typed));
                } catch (NumberFormatException ignored) {
                    return;
                }
            } else {
                updateSetting(key, typed);
            }
            view.subtitle(typed, true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    // --- core calls ---------------------------------------------------------

    private void updateSetting(String key, Object value) {
        JSONObject payload = new JSONObject();
        try {
            payload.put(key, value);
        } catch (JSONException e) {
            return;
        }
        // settings.set takes a partial object, so only the field that changed
        // travels; an older client can never blank a field it does not know.
        NebulaLink.call("settings.set", payload, result -> {
            if (result.ok) {
                settings = result.data;
            } else {
                report(result.error);
            }
        });
    }

    /**
     * Спрашивает строку и отправляет её команде. Одно поле на оба случая:
     * ядро само разбирает, подписка это, ключ или целый конфиг.
     */
    private void askAndRun(Context context, String title, String command, String field) {
        NebulaTheme theme = NebulaTheme.of(context);

        EditTextBoldCursor field_ = new EditTextBoldCursor(context);
        field_.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        field_.setTextColor(theme.onSurface());
        field_.setHintTextColor(theme.onSurfaceVariant());
        field_.setHint(LocaleController.getString(R.string.NebulaConnectHint));
        field_.setBackground(null);
        field_.setSingleLine();
        field_.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field_.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8),
                AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setView(field_);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            String typed = field_.getText().toString().trim();
            if (typed.isEmpty()) {
                return;
            }
            JSONObject payload = new JSONObject();
            try {
                payload.put(field, typed);
            } catch (JSONException e) {
                return;
            }
            NebulaLink.call(command, payload, result -> {
                report(result.ok ? title : result.error);
                if (result.ok) {
                    load();
                }
            });
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void runCommand(String command, String title) {
        if (command == null || command.isEmpty()) {
            return;
        }
        NebulaLink.call(command, null, result -> {
            if (result.ok) {
                report(title);
                load();
            } else {
                report(result.error);
            }
        });
    }

    private void report(String message) {
        if (getParentActivity() == null || message == null) {
            return;
        }
        Toast.makeText(getParentActivity(), message, Toast.LENGTH_SHORT).show();
    }

    // --- presentation helpers ----------------------------------------------

    /**
     * Looks up the translation for a schema key, falling back to the English
     * text the core supplies. A row therefore renders even before its string
     * has been translated, which keeps adding an option to one Go file.
     */
    private String localized(String key, String fallback) {
        if (key == null || key.isEmpty() || getParentActivity() == null) {
            return fallback;
        }
        int id = getParentActivity().getResources().getIdentifier(
                key, "string", getParentActivity().getPackageName());
        return id == 0 ? fallback : LocaleController.getString(id);
    }

    /** Maps the schema's platform-neutral icon names onto Telegram's assets. */
    private int iconFor(String name) {
        if (name == null) {
            return 0;
        }
        switch (name) {
            case "shield":
            case "lock":
                return R.drawable.msg_secret;
            case "globe":
                return R.drawable.msg_language;
            case "folder":
                return R.drawable.files_folder;
            case "settings":
                return R.drawable.msg_settings;
            case "gauge":
                return R.drawable.msg_speed;
            case "refresh":
                return R.drawable.msg_retry;
            case "download":
                return R.drawable.msg_download;
            case "edit":
                return R.drawable.msg_edit;
            case "trash":
                return R.drawable.delete;
            case "chart":
                return R.drawable.msg_stats;
            case "list":
                return R.drawable.msg_list;
            case "search":
                return R.drawable.msg_search;
            case "filter":
                return R.drawable.msg_customize;
            case "route":
                return R.drawable.msg_permissions;
            case "key":
                return R.drawable.msg_reset;
            case "info":
            default:
                return R.drawable.msg_info;
        }
    }

    /** A plain line of text, used where a card has nothing to show yet. */
    static TextView placeholder(Context context, CharSequence text) {
        NebulaTheme theme = NebulaTheme.of(context);
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        view.setTextColor(theme.onSurfaceVariant());
        view.setGravity(Gravity.CENTER);
        view.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16));
        return view;
    }
}
