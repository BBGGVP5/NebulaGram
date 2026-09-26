package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

/** Local history viewer; nothing here is uploaded or synchronized. */
public final class NebulaAiHistoryFragment extends BaseFragment {
    private LinearLayout content;
    @Override public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(NebulaText.text("История ИИ-чата", "AI chat history"));
        actionBar.setBackgroundColor(theme.surface()); actionBar.setTitleColor(theme.onSurface()); actionBar.setItemsColor(theme.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() { @Override public void onItemClick(int id) { if (id == -1) finishFragment(); } });
        ScrollView scroll = new ScrollView(context); scroll.setFillViewport(true); scroll.setBackgroundColor(theme.surface());
        content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(28));
        scroll.addView(content); build(context);
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, scroll);
    }
    private void build(Context context) {
        content.removeAllViews();
        content.addView(new NebulaSettingsHero(context, R.drawable.msg_recent,
                NebulaText.text("История запросов", "Request history"),
                NebulaText.text("Сохраняется только на этом устройстве и только после включения в настройках ИИ.", "Stored only on this device, and only when enabled in AI settings.")));
        JSONArray items = NebulaAiHistory.items();
        if (items.length() == 0) {
            content.addView(NebulaMenuFragment.placeholder(context, NebulaText.text("История пока пуста", "No history yet")));
            return;
        }
        NebulaCard card = new NebulaCard(context);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i); if (item == null) continue;
            addEntry(context, card, item.optString("provider"), item.optString("input"), item.optString("output"));
        }
        content.addView(card);
        NebulaCard actions = new NebulaCard(context);
        actions.add(new NebulaRow(context).icon(R.drawable.msg_delete)
                .title(NebulaText.text("Очистить историю", "Clear history"))
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> showDialog(new AlertDialog.Builder(context)
                        .setMessage(NebulaText.text("Удалить сохранённые запросы на этом устройстве?", "Delete saved requests from this device?"))
                        .setNegativeButton(NebulaText.text("Отмена", "Cancel"), null)
                        .setPositiveButton(NebulaText.text("Очистить", "Clear"), (d, which) -> { NebulaAiHistory.clear(); build(context); }).create())));
        content.addView(actions);
    }
    private void addEntry(Context context, NebulaCard card, String provider, String input, String output) {
        LinearLayout column = new LinearLayout(context); column.setOrientation(LinearLayout.VERTICAL); column.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        TextView heading = new TextView(context); heading.setText(provider); heading.setTextSize(13); heading.setTextColor(NebulaTheme.of(context).primary()); heading.setTypeface(AndroidUtilities.bold()); column.addView(heading);
        TextView question = new TextView(context); question.setText(input); question.setTextSize(15); question.setTextColor(NebulaTheme.of(context).onSurface()); question.setMaxLines(5); question.setEllipsize(android.text.TextUtils.TruncateAt.END); LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(-1, -2); qp.topMargin = AndroidUtilities.dp(6); column.addView(question, qp);
        TextView response = new TextView(context); response.setText(output); response.setTextSize(14); response.setTextColor(NebulaTheme.of(context).onSurfaceVariant()); response.setMaxLines(8); response.setEllipsize(android.text.TextUtils.TruncateAt.END); LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2); rp.topMargin = AndroidUtilities.dp(8); column.addView(response, rp);
        card.add(column);
    }
}
