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
        content.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6),
                AndroidUtilities.dp(12), AndroidUtilities.dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        build(context);
        return fragmentView = root;
    }

    @Override public void onActivityResultFragment(int request, int result, android.content.Intent data) {
        if (!NebulaSettingsTransfer.result(this, request, result, data)) super.onActivityResultFragment(request, result, data);
        else if (root != null) build(root.getContext());
    }
    private void build(Context context) {
        content.removeAllViews();

        content.addView(NebulaCard.header(context, NebulaText.text("Приложение", "Application")));
        NebulaCard app = new NebulaCard(context);
        app.add(section(context, R.drawable.msg_settings, R.string.NebulaSectionGeneral, R.string.NebulaGeneralSub, NebulaSectionFragment.SECTION_GENERAL));
        app.add(section(context, R.drawable.msg_customize, R.string.NebulaAppearanceTitle, R.string.NebulaAppearanceSub, NebulaSectionFragment.SECTION_APPEARANCE));
        content.addView(app, cardParams());

        content.addView(NebulaCard.header(context, NebulaText.text("Навигация", "Navigation")));
        NebulaCard navigation = new NebulaCard(context);
        navigation.add(section(context, R.drawable.msg_list, R.string.NebulaSectionPanel, R.string.NebulaPanelSub, NebulaSectionFragment.SECTION_TABS));
        navigation.add(section(context, R.drawable.files_folder, R.string.NebulaSectionFolders, R.string.NebulaFoldersInfo, NebulaSectionFragment.SECTION_FOLDERS));
        content.addView(navigation, cardParams());

        content.addView(NebulaCard.header(context, NebulaText.text("Чаты и профиль", "Chats and profile")));
        NebulaCard chats = new NebulaCard(context);
        chats.add(section(context, R.drawable.msg_discussion, R.string.NebulaSectionChats, R.string.NebulaChatsSub, NebulaSectionFragment.SECTION_CHATS));
        chats.add(section(context, R.drawable.menu_reply, R.string.NebulaSectionMessages, R.string.NebulaMessagesInfo, NebulaSectionFragment.SECTION_MESSAGES));
        chats.add(section(context, R.drawable.msg_openprofile, R.string.NebulaSectionProfile, R.string.NebulaProfileInfo, NebulaSectionFragment.SECTION_PROFILE));
        content.addView(chats, cardParams());

        content.addView(NebulaCard.header(context, NebulaText.text("Инструменты", "Tools")));
        NebulaCard tools = new NebulaCard(context);
        tools.add(new NebulaLinkRow(context).withClick(v -> presentFragment(new NebulaMenuFragment())));
        tools.add(new NebulaRow(context).icon(R.drawable.msg_emoji_smiles).title(NebulaText.text("Искусственный интеллект", "AI assistant"))
                .subtitle("Gemini · Claude · GPT", false).trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> presentFragment(new NebulaAiFragment())));
        content.addView(tools, cardParams());

        NebulaCard about = new NebulaCard(context);
        about.add(section(context, R.drawable.msg_info, R.string.NebulaSectionAbout, R.string.NebulaAboutSub, NebulaSectionFragment.SECTION_ABOUT));
        LinearLayout.LayoutParams bottom = cardParams(); bottom.topMargin = org.telegram.messenger.AndroidUtilities.dp(22);
        content.addView(about, bottom);
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
