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

    private void build(Context context, NebulaTheme theme) {
        content.removeAllViews();

        content.addView(NebulaCard.header(context,
                LocaleController.getString(R.string.NebulaSectionTunnel)));
        NebulaCard tunnel = new NebulaCard(context);
        tunnel.add(new NebulaRow(context)
                .icon(R.drawable.msg_secret)
                .title(LocaleController.getString(R.string.NebulaLinkName))
                .subtitle(LocaleController.getString(R.string.nl_source_sub), false)
                .trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> presentFragment(new NebulaMenuFragment())));
        tunnel.add(new NebulaRow(context)
                .icon(R.drawable.files_folder)
                .title(LocaleController.getString(R.string.NebulaSubscriptions))
                .subtitle(LocaleController.getString(R.string.nl_add_sub_sub), false)
                .trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> presentFragment(new NebulaSubscriptionsFragment())));
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
        content.addView(look, cardParams());

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

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(6);
        return params;
    }
}
