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

import app.nebulagram.nebulalink.NebulaLink;

/**
 * Раздел «NebulaGram» — всё, что форк добавляет к Telegram, в одном месте.
 *
 * <p>Собственный экран, а не строки, вкраплённые в настройки Telegram: так
 * добавление новой опции не требует правки чужого файла, а наш след в апстриме
 * остаётся одной строкой перехода сюда.
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

        build(context, theme);
        return root;
    }

    private void rebuild() {
        build(content.getContext(), NebulaTheme.of(content.getContext()));
    }

    private void build(Context context, NebulaTheme theme) {
        content.removeAllViews();

        // Подписки живут внутри NebulaLink, на экране "Источник и фильтры":
        // отдельная строка рядом дублировала тот же экран и заставляла гадать,
        // чем одно отличается от другого.
        content.addView(NebulaCard.header(context,
                LocaleController.getString(R.string.NebulaSectionTunnel)));
        NebulaCard tunnel = new NebulaCard(context);
        tunnel.add(new NebulaLinkRow(context)
                .withClick(v -> presentFragment(new NebulaMenuFragment())));
        content.addView(tunnel, cardParams());

        content.addView(NebulaCard.header(context,
                LocaleController.getString(R.string.NebulaSectionLook)));
        NebulaCard look = new NebulaCard(context);

        // Material You имеет смысл только там, где система вообще отдаёт
        // палитру; на Android 11 и старше строка была бы обманом.
        if (theme.isDynamic()) {
            NebulaRow dynamic = new NebulaRow(context)
                    .icon(R.drawable.msg_customize)
                    .title(LocaleController.getString(R.string.NebulaMaterialYou))
                    .subtitle(LocaleController.getString(R.string.NebulaMaterialYouSub), false)
                    .trailing(NebulaRow.TRAIL_SWITCH)
                    .checked(NebulaTheme.materialYouEnabled());
            dynamic.setOnClickListener(v -> {
                boolean enabled = dynamic.toggleChecked();
                NebulaTheme.setMaterialYouEnabled(enabled);
                NebulaTheme.applyMaterialYou(context);
            });
            look.add(dynamic);
        }

        NebulaRow login = new NebulaRow(context)
                .icon(R.drawable.msg_edit)
                .title(LocaleController.getString(R.string.NebulaLoginStyleTitle))
                .subtitle(LocaleController.getString(R.string.NebulaLoginStyleSub), false)
                .trailing(NebulaRow.TRAIL_SWITCH)
                .checked(NebulaLoginStyle.enabled());
        login.setOnClickListener(v -> NebulaLoginStyle.setEnabled(login.toggleChecked()));
        look.add(login);
        NebulaRow composer = new NebulaRow(context)
                .icon(R.drawable.msg_edit)
                .title(LocaleController.getString(R.string.NebulaIosComposer))
                .subtitle(LocaleController.getString(R.string.NebulaIosComposerInfo), false)
                .trailing(NebulaRow.TRAIL_SWITCH)
                .checked(NebulaAppearance.iosComposer());
        composer.setOnClickListener(v -> NebulaAppearance.setIosComposer(composer.toggleChecked()));
        look.add(composer);

        NebulaRow header = new NebulaRow(context)
                .icon(R.drawable.msg_customize)
                .title(LocaleController.getString(R.string.NebulaFloatingHeader))
                .subtitle(LocaleController.getString(R.string.NebulaFloatingHeaderInfo), false)
                .trailing(NebulaRow.TRAIL_SWITCH)
                .checked(NebulaAppearance.chatHeader());
        header.setOnClickListener(v -> NebulaAppearance.setChatHeader(header.toggleChecked()));
        look.add(header);

        NebulaRow information = new NebulaRow(context)
                .icon(R.drawable.msg_info)
                .title(LocaleController.getString(R.string.NebulaProfileStyle))
                .subtitle(LocaleController.getString(R.string.NebulaProfileStyleInfo), false)
                .trailing(NebulaRow.TRAIL_SWITCH)
                .checked(NebulaAppearance.profileStyle());
        information.setOnClickListener(v -> NebulaAppearance.setProfileStyle(information.toggleChecked()));
        look.add(information);
        content.addView(look, cardParams());

        // Настройки штатных вкладок и отдельной боковой панели.
        content.addView(NebulaCard.header(context,
                LocaleController.getString(R.string.NebulaSectionPanel)));
        NebulaCard panel = new NebulaCard(context);

        NebulaRow bar = new NebulaRow(context)
                .icon(R.drawable.msg_customize)
                .title(LocaleController.getString(R.string.NebulaBottomBarTitle))
                .subtitle(LocaleController.getString(R.string.NebulaBottomBarSub), false)
                .trailing(NebulaRow.TRAIL_SWITCH)
                .checked(NebulaBottomBar.enabled());
        bar.setOnClickListener(v -> {
            NebulaBottomBar.setEnabled(bar.toggleChecked());
            rebuild();
        });
        panel.add(bar);

        NebulaRow sidebar = new NebulaRow(context)
                .icon(R.drawable.msg_customize)
                .title(LocaleController.getString(R.string.NebulaSidePanelTitle))
                .subtitle(LocaleController.getString(R.string.NebulaSidePanelSub), false)
                .trailing(NebulaRow.TRAIL_SWITCH)
                .checked(NebulaBottomBar.sidebarEnabled());
        sidebar.setOnClickListener(v -> {
            NebulaBottomBar.setSidebarEnabled(sidebar.toggleChecked());
            rebuild();
        });
        panel.add(sidebar);

        panel.add(tabRow(context, NebulaBottomBar.TAB_CONTACTS,
                R.drawable.msg_contacts, R.string.NebulaTabContacts));
        panel.add(tabRow(context, NebulaBottomBar.TAB_SETTINGS,
                R.drawable.msg_settings, R.string.NebulaTabSettings));
        panel.add(tabRow(context, NebulaBottomBar.TAB_PROFILE,
                R.drawable.msg_openprofile, R.string.NebulaTabProfile));
        content.addView(panel, cardParams());
        content.addView(NebulaMenuFragment.placeholder(context,
                LocaleController.getString(R.string.NebulaBottomBarHint)));

        NebulaCard about = new NebulaCard(context);
        NebulaRow versions = new NebulaRow(context)
                .icon(R.drawable.msg_info)
                .title(LocaleController.getString(R.string.nl_versions));
        about.add(versions);
        content.addView(about, cardParams());

        // Версии спрашиваем у ядра: так строка не врёт после обновления Xray.
        NebulaLink.call("core.versions", null, result -> {
            if (result.ok && result.data != null) {
                versions.subtitle(describe(result.data), true);
            }
        });
    }

    private String describe(org.json.JSONObject versions) {
        StringBuilder text = new StringBuilder();
        java.util.Iterator<String> keys = versions.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (text.length() > 0) {
                text.append(" · ");
            }
            text.append(key).append(' ').append(versions.optString(key));
        }
        return text.toString();
    }

    /** Переключатель одной вкладки: три строки отличаются только значком и словом. */
    private NebulaRow tabRow(android.content.Context context, String tab, int icon, int title) {
        NebulaRow row = new NebulaRow(context)
                .icon(icon)
                .title(LocaleController.getString(title))
                .trailing(NebulaRow.TRAIL_SWITCH)
                .checked(NebulaBottomBar.tabEnabled(tab));
        row.setOnClickListener(v -> {
            NebulaBottomBar.setTabEnabled(tab, row.toggleChecked());
            rebuild();
        });
        return row;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(6);
        return params;
    }
}
