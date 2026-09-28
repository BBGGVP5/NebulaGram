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
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, root, -1);
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
        search.setTextSize(14);
        search.setHint(NebulaText.text("Поиск настроек", "Search settings"));
        search.setContentDescription(NebulaText.text("Поиск настроек", "Search settings"));
        search.setTextColor(theme.onSurface());
        search.setHintTextColor(theme.onSurfaceVariant());
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setCompoundDrawablesWithIntrinsicBounds(R.drawable.msg_search, 0, 0, 0);
        search.setCompoundDrawablePadding(AndroidUtilities.dp(10));
        search.getCompoundDrawables()[0].mutate().setColorFilter(theme.onSurfaceVariant(), android.graphics.PorterDuff.Mode.SRC_IN);
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
        sections.addView(NebulaCard.header(context, NebulaText.text("Настройки", "Settings")));
        NebulaCard categories = new NebulaCard(context);
        categories.add(hub(context, R.drawable.msg_settings, "Основные", "General",
                "Подключение и поведение", "Connection and behavior", NebulaSettingsHubFragment.GENERAL));
        categories.add(hub(context, R.drawable.msg_customize, "Внешний вид", "Appearance",
                "Цвета, стекло, значки", "Colors, glass, icons", NebulaSettingsHubFragment.APPEARANCE));
        categories.add(hub(context, R.drawable.msg_discussion, "Чаты", "Chats",
                "Список, сообщения, профиль", "List, messages, profile", NebulaSettingsHubFragment.CHATS));
        categories.add(hub(context, R.drawable.msg_list, "Навигация", "Navigation",
                "Панель и папки", "Bar and folders", NebulaSettingsHubFragment.NAVIGATION));
        categories.add(hub(context, R.drawable.msg_secret, "Конфиденциальность", "Privacy",
                "Архив и защита", "Archive and protection", NebulaSettingsHubFragment.PRIVACY));
        categories.add(hub(context, R.drawable.msg_emoji_smiles, "Инструменты", "Tools",
                "ИИ, заметки, текст", "AI, tasks, text", NebulaSettingsHubFragment.TOOLS));
        categories.add(section(context, R.drawable.msg_info, R.string.NebulaSectionAbout,
                R.string.NebulaAboutSub, NebulaSectionFragment.SECTION_ABOUT));
        sections.addView(categories, cardParams());
        sections.addView(NebulaCard.header(context, NebulaText.text("Подключение", "Connection")));
        NebulaCard connection = new NebulaCard(context);
        connection.add(new NebulaRow(context).icon(R.drawable.nebula_link_shield)
                .title("NebulaLink")
                .subtitle(NebulaText.text("Серверы и подписка", "Servers and subscription"), false)
                .trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> presentFragment(new NebulaMenuFragment())));
        sections.addView(connection, cardParams());
    }

    private NebulaRow hub(Context context, int icon, String titleRu, String titleEn,
                          String detailRu, String detailEn, int category) {
        return new NebulaRow(context).icon(icon).title(NebulaText.text(titleRu, titleEn))
                .subtitle(NebulaText.text(detailRu, detailEn), false)
                .trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> presentFragment(new NebulaSettingsHubFragment(category)));
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
        if (fragmentView != null) fragmentView.setBackgroundColor(theme.surface());
        actionBar.setBackgroundColor(theme.surface());
        actionBar.setTitleColor(theme.onSurface());
        actionBar.setItemsColor(theme.onSurface(), false);
        build(root.getContext());
    }

    private NebulaRow section(Context context, int icon, int title, int subtitle, int id) {
        return new NebulaRow(context)
                .icon(icon)
                .title(LocaleController.getString(title))
                .subtitle(sectionSubtitle(id, subtitle), false)
                .trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> presentFragment(new NebulaSectionFragment(id)));
    }

    private CharSequence sectionSubtitle(int id, int fallback) {
        switch (id) {
            case NebulaSectionFragment.SECTION_GENERAL:
                return NebulaText.text("Поведение приложения", "App behavior");
            case NebulaSectionFragment.SECTION_APPEARANCE:
                return NebulaText.text("Цвета, стекло и анимации", "Colors, glass and motion");
            case NebulaSectionFragment.SECTION_TABS:
                return NebulaText.text("Нижняя панель", "Bottom bar");
            case NebulaSectionFragment.SECTION_FOLDERS:
                return NebulaText.text("Вкладки и названия", "Tabs and titles");
            case NebulaSectionFragment.SECTION_CHATS:
                return NebulaText.text("Список и шапка", "List and header");
            case NebulaSectionFragment.SECTION_MESSAGES:
                return NebulaText.text("Меню и ответы", "Menus and replies");
            case NebulaSectionFragment.SECTION_PROFILE:
                return NebulaText.text("Обложка и действия", "Cover and actions");
            case NebulaSectionFragment.SECTION_ABOUT:
                return NebulaText.text("Версия и исходный код", "Version and source");
            default:
                return LocaleController.getString(fallback);
        }
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
