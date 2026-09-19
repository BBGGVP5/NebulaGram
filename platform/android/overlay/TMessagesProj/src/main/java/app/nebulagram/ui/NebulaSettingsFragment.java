package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Настройки NebulaGram: входная страница.
 *
 * <p>Раньше это был один длинный список, в котором туннель, цвета, шапка чата
 * и вкладки шли подряд. Разделы решают ту же задачу, что и в самом Telegram:
 * искать нужное глазами по трём строкам быстрее, чем по пятнадцати, а каждый
 * раздел может начинаться с превью — на общем списке для них нет места.
 */
public class NebulaSettingsFragment extends BaseFragment {

    private LinearLayout content;
    private LinearLayout sections;
    private LinearLayout searchResults;
    private android.widget.EditText search;
    private FrameLayout root;
    private int paletteSurface, palettePrimary;

    @Override
    public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.NebulaSettings));
        actionBar.setBackgroundColor(theme.surface());
        actionBar.setTitleColor(theme.onSurface());
        actionBar.setItemsColor(theme.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id >= NebulaSettingsTransfer.EXPORT) { NebulaSettingsTransfer.action(NebulaSettingsFragment.this, id); return; }
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        NebulaSettingsTransfer.menu(this);
        root = new FrameLayout(context);
        paletteSurface = theme.surface();
        palettePrimary = theme.primary();
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

        build(context);
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, root);
    }

    @Override public void onActivityResultFragment(int request, int result, android.content.Intent data) {
        if (!NebulaSettingsTransfer.result(this, request, result, data)) super.onActivityResultFragment(request, result, data);
        else if (root != null) build(root.getContext());
    }
    private void build(Context context) {
        content.removeAllViews();
        NebulaTheme theme = NebulaTheme.of(context);
        search = new android.widget.EditText(context);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setHint(NebulaText.text("Поиск настроек", "Search settings"));
        search.setContentDescription(NebulaText.text("Поиск настроек", "Search settings"));
        search.setTextColor(theme.onSurface());
        search.setHintTextColor(theme.onSurfaceVariant());
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        android.graphics.drawable.GradientDrawable field = new android.graphics.drawable.GradientDrawable();
        field.setCornerRadius(AndroidUtilities.dp(14));
        field.setColor(theme.surfaceContainer());
        search.setBackground(field);
        content.addView(search, new LinearLayout.LayoutParams(-1, -2));
        searchResults = new LinearLayout(context);
        searchResults.setOrientation(LinearLayout.VERTICAL);
        searchResults.setVisibility(View.GONE);
        content.addView(searchResults, new LinearLayout.LayoutParams(-1, -2));
        sections = new LinearLayout(context);
        sections.setOrientation(LinearLayout.VERTICAL);
        content.addView(sections, new LinearLayout.LayoutParams(-1, -2));
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { showSearch(context, s.toString()); }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        buildSections(context);
    }

    private void showSearch(Context context, String query) {
        boolean searching = query.trim().length() >= 2;
        sections.setVisibility(searching ? View.GONE : View.VISIBLE);
        searchResults.setVisibility(searching ? View.VISIBLE : View.GONE);
        searchResults.removeAllViews();
        if (!searching) return;
        java.util.ArrayList<NebulaSettingsSearch.Entry> matches = NebulaSettingsSearch.match(query);
        searchResults.addView(NebulaCard.header(context, matches.isEmpty()
                ? NebulaText.text("Ничего не найдено", "No settings found")
                : NebulaText.text("Результаты поиска", "Search results")));
        NebulaCard results = new NebulaCard(context);
        for (NebulaSettingsSearch.Entry entry : matches) {
            results.add(new NebulaRow(context).icon(entry.icon).title(entry.title)
                    .subtitle(entry.info, false).trailing(NebulaRow.TRAIL_CHEVRON)
                    .withClick(v -> { AndroidUtilities.hideKeyboard(search); entry.open(this); }));
        }
        if (!matches.isEmpty()) searchResults.addView(results, cardParams());
    }

    private void buildSections(Context context) {
        sections.addView(NebulaCard.header(context, NebulaText.text("Подключение", "Connection")));
        NebulaCard tunnel = new NebulaCard(context);
        tunnel.add(new NebulaLinkRow(context).withClick(v -> presentFragment(new NebulaMenuFragment())));
        sections.addView(tunnel, cardParams());

        sections.addView(NebulaCard.header(context, NebulaText.text("Приложение", "Application")));
        NebulaCard app = new NebulaCard(context);
        app.add(section(context, R.drawable.msg_settings, R.string.NebulaSectionGeneral, R.string.NebulaGeneralSub, NebulaSectionFragment.SECTION_GENERAL));
        app.add(new NebulaRow(context).icon(R.drawable.msg_secret).title(NebulaText.text("Конфиденциальность", "Privacy"))
                .subtitle(NebulaText.text("Локальный архив удалённых сообщений", "Local deleted-message archive"), false)
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> presentFragment(new NebulaPrivacyFragment())));
        app.add(section(context, R.drawable.msg_customize, R.string.NebulaAppearanceTitle, R.string.NebulaAppearanceSub, NebulaSectionFragment.SECTION_APPEARANCE));
        sections.addView(app, cardParams());

        sections.addView(NebulaCard.header(context, NebulaText.text("Навигация", "Navigation")));
        NebulaCard navigation = new NebulaCard(context);
        navigation.add(section(context, R.drawable.msg_list, R.string.NebulaSectionPanel, R.string.NebulaPanelSub, NebulaSectionFragment.SECTION_TABS));
        navigation.add(section(context, R.drawable.files_folder, R.string.NebulaSectionFolders, R.string.NebulaFoldersInfo, NebulaSectionFragment.SECTION_FOLDERS));
        sections.addView(navigation, cardParams());

        sections.addView(NebulaCard.header(context, NebulaText.text("Чаты и профиль", "Chats and profile")));
        NebulaCard chats = new NebulaCard(context);
        chats.add(section(context, R.drawable.msg_discussion, R.string.NebulaSectionChats, R.string.NebulaChatsSub, NebulaSectionFragment.SECTION_CHATS));
        chats.add(section(context, R.drawable.menu_reply, R.string.NebulaSectionMessages, R.string.NebulaMessagesInfo, NebulaSectionFragment.SECTION_MESSAGES));
        chats.add(section(context, R.drawable.msg_openprofile, R.string.NebulaSectionProfile, R.string.NebulaProfileInfo, NebulaSectionFragment.SECTION_PROFILE));
        sections.addView(chats, cardParams());

        sections.addView(NebulaCard.header(context, NebulaText.text("Инструменты и приложение", "Tools and app")));
        NebulaCard tools = new NebulaCard(context);
        tools.add(new NebulaRow(context).icon(R.drawable.msg_emoji_smiles).title(NebulaText.text("Искусственный интеллект", "AI assistant"))
                .subtitle("Gemini · Claude · GPT", false).trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> presentFragment(new NebulaAiFragment())));
        tools.add(section(context, R.drawable.msg_info, R.string.NebulaSectionAbout, R.string.NebulaAboutSub, NebulaSectionFragment.SECTION_ABOUT));
        sections.addView(tools, cardParams());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (root == null) return;
        NebulaTheme theme = NebulaTheme.of(root.getContext());
        if (paletteSurface == theme.surface() && palettePrimary == theme.primary()) return;
        paletteSurface = theme.surface();
        palettePrimary = theme.primary();
        root.setBackgroundColor(theme.surface());
        actionBar.setBackgroundColor(theme.surface());
        actionBar.setTitleColor(theme.onSurface());
        actionBar.setItemsColor(theme.onSurface(), false);
        build(root.getContext());
    }

    private NebulaRow section(Context context, int icon, int title, int subtitle, int id) {
        return new NebulaRow(context)
                .icon(icon)
                .title(LocaleController.getString(title))
                .subtitle(LocaleController.getString(subtitle), false)
                .trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> presentFragment(new NebulaSectionFragment(id)));
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(6);
        return params;
    }

    @Override
    public boolean isLightStatusBar() {
        return !NebulaTheme.of(getContext()).isDark();
    }
}
