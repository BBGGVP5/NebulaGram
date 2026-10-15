package app.nebulagram.ui;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.LinearLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.LaunchActivity;
import java.util.ArrayList;
import java.util.List;

/** Searchable account switcher; selection always carries the persistent ID. */
public final class NebulaAccountsFragment extends BaseFragment {
    private LinearLayout results;
    private EditTextBoldCursor search;
    private NebulaRow ordering;
    private Runnable pendingSearch;
    private int order;
    private String t(String ru, String en) { return NebulaText.text(ru, en); }

    @Override public View createView(Context context) {
        NebulaFormUi.bar(this, actionBar, context, t("Аккаунты", "Accounts"));
        LinearLayout column = NebulaFormUi.column(context);
        column.addView(new NebulaSettingsHero(context, "👥", t("Все ваши аккаунты", "All your accounts"),
                t("Переключайтесь, ищите по имени и выбирайте порядок.", "Switch accounts, search by name and choose their order.")));
        search = NebulaFormUi.field(context, t("Имя или @username", "Name or @username"), 1, 120);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        column.addView(search);
        order = Math.max(0, Math.min(2, ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0).getInt("account_list_order", 0)));
        ordering = new NebulaRow(context).title(t("Порядок аккаунтов", "Account order"))
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> chooseOrder());
        NebulaCard controls = new NebulaCard(context);
        controls.add(ordering);
        controls.add(new NebulaRow(context).icon(R.drawable.msg_add).title(t("Добавить аккаунт", "Add account"))
                .withClick(v -> { AndroidUtilities.hideKeyboard(search); NebulaNavigation.addAccount(this); }));
        NebulaFormUi.group(column, t("Управление", "Manage"), controls);
        results = new LinearLayout(context); results.setOrientation(LinearLayout.VERTICAL); column.addView(results);
        column.addView(NebulaFormUi.note(context, t(
                "Лимита числа аккаунтов в NebulaGram нет. Все активные аккаунты продолжают получать уведомления. Большее число подключений использует больше памяти и заряда.",
                "NebulaGram has no account-count limit. All active accounts keep receiving notifications. More connections use more memory and battery.")));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                cancelSearch(); pendingSearch = () -> { pendingSearch = null; rebuild(); };
                AndroidUtilities.runOnUIThread(pendingSearch, 120);
            }
            public void afterTextChanged(Editable s) {}
        });
        rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, NebulaFormUi.scroll(context, column));
    }
    private String[] orders() { return new String[]{t("Сначала новые", "Newest first"), t("По имени", "By name"), t("По порядку добавления", "By creation order")}; }
    private void chooseOrder() {
        showDialog(new NebulaDialog.Builder(results.getContext()).setTitle(t("Порядок аккаунтов", "Account order"))
                .setSelectedIndex(order).setItems(orders(), (dialog, which) -> {
                    order = which;
                    ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0).edit().putInt("account_list_order", order).apply();
                    rebuild();
                }).create());
    }
    private void rebuild() {
        if (results == null) return;
        ordering.subtitle(orders()[order], false);
        ArrayList<NebulaAccountList.Entry> entries = new ArrayList<>();
        for (int id : UserConfig.getAccountIds()) {
            UserConfig config = UserConfig.getInstance(id);
            TLRPC.User user = config.getCurrentUser();
            if (user != null) entries.add(new NebulaAccountList.Entry(id, UserObject.getUserName(user), UserObject.getPublicUsername(user), config.loginTime));
        }
        List<NebulaAccountList.Entry> visible = NebulaAccountList.visible(entries, search.getText().toString(), order);
        Context context = results.getContext(); results.removeAllViews();
        results.addView(NebulaCard.header(context, t("Аккаунты · ", "Accounts · ") + visible.size()));
        NebulaCard card = new NebulaCard(context);
        for (NebulaAccountList.Entry entry : visible) {
            String subtitle = entry.id == UserConfig.selectedAccount ? t("Текущий аккаунт", "Current account") : "";
            if (!entry.username.isEmpty()) subtitle += (subtitle.isEmpty() ? "" : " · ") + "@" + entry.username;
            NebulaRow row = new NebulaRow(context).title(entry.name).subtitle(subtitle, false);
            if (entry.id == UserConfig.selectedAccount) row.badge("✓", NebulaTheme.of(context).primary());
            row.withClick(v -> {
                if (entry.id == UserConfig.selectedAccount || !UserConfig.isValidAccount(entry.id)) return;
                AndroidUtilities.hideKeyboard(search);
                if (LaunchActivity.instance != null) LaunchActivity.instance.switchToAccount(entry.id, true);
            });
            card.add(row);
        }
        if (visible.isEmpty()) results.addView(NebulaFormUi.note(context, t("Аккаунты не найдены", "No accounts found")));
        else results.addView(card);
    }
    private void cancelSearch() { if (pendingSearch != null) AndroidUtilities.cancelRunOnUIThread(pendingSearch); pendingSearch = null; }
    @Override public void onResume() { super.onResume(); cancelSearch(); rebuild(); }
    @Override public void onFragmentDestroy() { cancelSearch(); super.onFragmentDestroy(); }
}
