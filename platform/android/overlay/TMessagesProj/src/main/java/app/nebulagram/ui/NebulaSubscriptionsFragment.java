package app.nebulagram.ui;

import android.content.Context;
import android.text.InputType;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

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
 * Сохранённые подписки: сколько серверов дала каждая, когда обновлялась и что
 * с ней не так.
 *
 * <p>Экран нужен потому, что подписок бывает несколько — рабочая, запасная,
 * чужая, — и обновлять их скопом неудобно: упавшая панель сообщает об ошибке
 * для источника, который никто не трогал.
 */
public class NebulaSubscriptionsFragment extends BaseFragment {

    private LinearLayout content;

    @Override
    public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.nl_add_sub));
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
        NebulaLink.call("subscription.list", null, result -> {
            if (result.ok) {
                render(result.array);
            }
        });
    }

    private void render(JSONArray subscriptions) {
        if (content == null) {
            return;
        }
        Context context = content.getContext();
        content.removeAllViews();

        NebulaCard actions = new NebulaCard(context);
        actions.add(new NebulaRow(context)
                .icon(R.drawable.msg_download)
                .title(LocaleController.getString(R.string.nl_add_sub))
                .subtitle(LocaleController.getString(R.string.nl_add_sub_sub), false)
                .withClick(v -> add(context)));
        actions.add(new NebulaRow(context)
                .icon(R.drawable.msg_retry)
                .title(LocaleController.getString(R.string.nl_refresh))
                .withClick(v -> NebulaLink.call("subscription.refreshAll", null, result -> {
                    report(result.ok ? LocaleController.getString(R.string.nl_refresh) : result.error);
                    load();
                })));
        content.addView(actions, cardParams());

        int count = subscriptions == null ? 0 : subscriptions.length();
        if (count == 0) {
            content.addView(NebulaMenuFragment.placeholder(context,
                    LocaleController.getString(R.string.NebulaNoSubscriptions)));
            return;
        }

        content.addView(NebulaCard.header(context, LocaleController.getString(R.string.nl_sec_source)));
        NebulaCard list = new NebulaCard(context);
        for (int i = 0; i < count; i++) {
            JSONObject subscription = subscriptions.optJSONObject(i);
            if (subscription != null) {
                list.add(buildRow(context, subscription));
            }
        }
        content.addView(list, cardParams());
    }

    private View buildRow(Context context, JSONObject subscription) {
        String id = subscription.optString("id");
        String name = subscription.optString("name");
        String error = subscription.optString("last_error");

        NebulaRow row = new NebulaRow(context)
                .icon(R.drawable.files_folder)
                .title(name.isEmpty() ? subscription.optString("url") : name)
                .trailing(NebulaRow.TRAIL_CHEVRON);

        if (!error.isEmpty()) {
            row.subtitle(error, false);
        } else {
            row.subtitle(describe(subscription), true);
        }
        row.setOnClickListener(v -> showActions(context, id, name));
        return row;
    }

    /** «12 серверов · обновлено 5 минут назад» — то, что важно видеть сразу. */
    private String describe(JSONObject subscription) {
        int servers = subscription.optInt("server_count");
        long updated = subscription.optLong("updated_at");
        String amount = servers + " " + LocaleController.getString(R.string.NebulaServersShort);
        if (updated <= 0) {
            return amount;
        }
        CharSequence when = DateUtils.getRelativeTimeSpanString(
                updated * 1000L, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        return amount + " · " + when;
    }

    private void showActions(Context context, String id, String name) {
        CharSequence[] items = {
                LocaleController.getString(R.string.nl_refresh),
                LocaleController.getString(R.string.NebulaRemove),
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(name);
        builder.setItems(items, (dialog, which) -> {
            JSONObject payload = new JSONObject();
            try {
                payload.put("id", id);
            } catch (JSONException e) {
                return;
            }
            String command = which == 0 ? "subscription.refresh" : "subscription.remove";
            NebulaLink.call(command, payload, result -> {
                report(result.ok ? name : result.error);
                load();
            });
        });
        showDialog(builder.create());
    }

    private void add(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);

        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(theme.onSurface());
        input.setHintTextColor(theme.onSurfaceVariant());
        input.setHint(LocaleController.getString(R.string.NebulaConnectHint));
        input.setBackground(null);
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8),
                AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.nl_add_sub));
        builder.setView(input);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            String typed = input.getText().toString().trim();
            if (typed.isEmpty()) {
                return;
            }
            JSONObject payload = new JSONObject();
            try {
                payload.put("url", typed);
            } catch (JSONException e) {
                return;
            }
            NebulaLink.call("subscription.add", payload, result -> {
                report(result.ok ? LocaleController.getString(R.string.nl_add_sub) : result.error);
                load();
            });
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(6);
        return params;
    }

    private void report(String message) {
        if (getParentActivity() != null && message != null) {
            android.widget.Toast.makeText(getParentActivity(), message,
                    android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
