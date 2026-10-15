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
    private android.widget.TextView ordering, count;
    private Runnable pendingSearch;
    private boolean lastMasked;
    private org.telegram.messenger.AccountDirectory.Subscription accountSubscription;
    private final org.telegram.messenger.NotificationCenter.NotificationCenterDelegate maskChanged = (id, account, args) -> {
        if (id == org.telegram.messenger.NotificationCenter.dialogsNeedReload && lastMasked == NebulaPresentationPrivacy.enabled()) return;
        cancelSearch(); rebuild();
    };
    private int order;
    private String t(String ru, String en) { return NebulaText.text(ru, en); }
    @Override public boolean onFragmentCreate() {
        super.onFragmentCreate();
        org.telegram.messenger.NotificationCenter.getInstance(currentAccount).addObserver(maskChanged, org.telegram.messenger.NotificationCenter.dialogsNeedReload);
        accountSubscription = org.telegram.messenger.AccountDirectory.observe(
                id -> org.telegram.messenger.NotificationCenter.getInstance(id).addObserver(maskChanged, org.telegram.messenger.NotificationCenter.mainUserInfoChanged),
                id -> org.telegram.messenger.NotificationCenter.getInstance(id).removeObserver(maskChanged, org.telegram.messenger.NotificationCenter.mainUserInfoChanged));
        return true;
    }

    @Override public View createView(Context context) {
        NebulaFormUi.bar(this, actionBar, context, t("Аккаунты", "Accounts"));
        LinearLayout column = NebulaFormUi.column(context);
        column.setLayoutDirection(org.telegram.messenger.LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        actionBar.createMenu().addItem(1, R.drawable.msg_add).setContentDescription(t("Добавить аккаунт", "Add account"));
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == 1) { AndroidUtilities.hideKeyboard(search); NebulaNavigation.addAccount(NebulaAccountsFragment.this); }
            }
        });
        android.widget.FrameLayout searchBox = new android.widget.FrameLayout(context);
        searchBox.setBackground(org.telegram.ui.ActionBar.Theme.createRoundRectDrawable(AndroidUtilities.dp(16), NebulaTheme.of(context).surfaceContainer()));
        search = NebulaFormUi.cardField(context, t("Найти аккаунт", "Find account"), 1, 120);
        search.setPaddingRelative(AndroidUtilities.dp(48), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchBox.addView(search, new android.widget.FrameLayout.LayoutParams(-1,-2));
        android.widget.ImageView searchIcon = new android.widget.ImageView(context); searchIcon.setImageResource(R.drawable.msg_search);
        searchIcon.setColorFilter(NebulaTheme.of(context).onSurfaceVariant());searchIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        android.widget.FrameLayout.LayoutParams iconParams = new android.widget.FrameLayout.LayoutParams(AndroidUtilities.dp(22),AndroidUtilities.dp(22),android.view.Gravity.START|android.view.Gravity.CENTER_VERTICAL);
        iconParams.setMarginStart(AndroidUtilities.dp(16));searchBox.addView(searchIcon,iconParams);column.addView(searchBox);
        order = Math.max(0, Math.min(2, ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0).getInt("account_list_order", 0)));
        LinearLayout tools = new LinearLayout(context);tools.setGravity(android.view.Gravity.CENTER_VERTICAL);
        tools.setPadding(AndroidUtilities.dp(4),AndroidUtilities.dp(12),0,AndroidUtilities.dp(8));
        count = new android.widget.TextView(context);count.setTextSize(13);count.setTextColor(NebulaTheme.of(context).onSurfaceVariant());
        tools.addView(count,new LinearLayout.LayoutParams(0,-2,1));
        ordering = new android.widget.TextView(context);ordering.setTextSize(13);ordering.setTextColor(NebulaTheme.of(context).primary());
        ordering.setPadding(AndroidUtilities.dp(10),AndroidUtilities.dp(12),AndroidUtilities.dp(4),AndroidUtilities.dp(12));
        ordering.setOnClickListener(v -> chooseOrder());tools.addView(ordering,new LinearLayout.LayoutParams(-2,-2));column.addView(tools);
        results = new LinearLayout(context); results.setOrientation(LinearLayout.VERTICAL); column.addView(results);
        column.addView(NebulaFormUi.note(context, t(
                "Уведомления работают для всех аккаунтов. Добавить новый можно кнопкой + вверху. Больше подключений — больше расход памяти и заряда.",
                "All accounts receive notifications. Add another with + above. More connections use more memory and battery.")));
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
        ordering.setText(orders()[order]);
        ordering.setContentDescription(t("Порядок аккаунтов: ", "Account order: ") + orders()[order]);
        ArrayList<NebulaAccountList.Entry> entries = new ArrayList<>();
        for (int id : UserConfig.getAccountIds()) {
            UserConfig config = UserConfig.getInstance(id);
            TLRPC.User user = config.getCurrentUser();
            if (user != null) entries.add(new NebulaAccountList.Entry(id, UserObject.getUserName(user), UserObject.getPublicUsername(user), config.loginTime));
        }
        boolean masked = NebulaPresentationPrivacy.enabled();
        lastMasked = masked;
        search.setEnabled(!masked);
        if (masked && search.length() != 0) { search.setText(""); AndroidUtilities.hideKeyboard(search); }
        List<NebulaAccountList.Entry> visible = NebulaAccountList.visible(entries, masked ? "" : search.getText().toString(), order);
        Context context = results.getContext(); results.removeAllViews();
        count.setText(t("Аккаунты · ", "Accounts · ") + visible.size());
        NebulaCard card = new NebulaCard(context);
        int aliasPosition = 0;
        for (NebulaAccountList.Entry entry : visible) {
            String subtitle = entry.id == UserConfig.selectedAccount ? t("Текущий аккаунт", "Current account") : "";
            if (!masked && !entry.username.isEmpty()) subtitle += (subtitle.isEmpty() ? "" : " · ") + "@" + entry.username;
            NebulaAccountRow row = new NebulaAccountRow(context,entry.id,UserConfig.getInstance(entry.id).getCurrentUser(),
                    masked ? t("Аккаунт ", "Account ") + (++aliasPosition) : entry.name,subtitle,entry.id == UserConfig.selectedAccount,masked);
            row.setOnClickListener(v -> {
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
    @Override public void onFragmentDestroy() {
        org.telegram.messenger.NotificationCenter.getInstance(currentAccount).removeObserver(maskChanged, org.telegram.messenger.NotificationCenter.dialogsNeedReload);
        if (accountSubscription != null) { accountSubscription.close(); accountSubscription = null; }
        cancelSearch(); super.onFragmentDestroy();
    }
}
