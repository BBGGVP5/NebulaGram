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

        build(context);
        return root;
    }

    private void build(Context context) {
        content.removeAllViews();

        // Туннель отдельной карточкой: это не настройка внешнего вида, а
        // состояние — строка показывает, подключены мы сейчас или нет.
        content.addView(NebulaCard.header(context,
                LocaleController.getString(R.string.NebulaSectionTunnel)));
        NebulaCard tunnel = new NebulaCard(context);
        tunnel.add(new NebulaLinkRow(context)
                .withClick(v -> presentFragment(new NebulaMenuFragment())));
        content.addView(tunnel, cardParams());

        content.addView(NebulaCard.header(context,
                LocaleController.getString(R.string.NebulaSectionSetup)));
        NebulaCard sections = new NebulaCard(context);
        sections.add(section(context, R.drawable.msg_settings,
                R.string.NebulaSectionGeneral, R.string.NebulaGeneralSub,
                NebulaSectionFragment.SECTION_GENERAL));
        sections.add(section(context, R.drawable.msg_customize,
                R.string.NebulaSectionLook, R.string.NebulaAppearanceSub,
                NebulaSectionFragment.SECTION_APPEARANCE));
        sections.add(section(context, R.drawable.msg_discussion,
                R.string.NebulaSectionChats, R.string.NebulaChatsSub,
                NebulaSectionFragment.SECTION_CHATS));
        sections.add(section(context, R.drawable.msg_list,
                R.string.NebulaSectionPanel, R.string.NebulaPanelSub,
                NebulaSectionFragment.SECTION_TABS));
        sections.add(section(context, R.drawable.msg_info,
                R.string.NebulaSectionAbout, R.string.NebulaAboutSub,
                NebulaSectionFragment.SECTION_ABOUT));
        content.addView(sections, cardParams());
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
