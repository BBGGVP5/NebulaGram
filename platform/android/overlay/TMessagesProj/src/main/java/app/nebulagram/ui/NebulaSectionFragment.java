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

import java.util.ArrayList;
import java.util.List;

import app.nebulagram.nebulalink.NebulaLink;

/**
 * Раздел настроек NebulaGram: внешний вид, чаты, панели, о программе.
 *
 * <p>Один класс на все разделы, а не четыре почти одинаковых фрагмента:
 * различаются они только набором строк, а обвязка — панель заголовка,
 * прокрутка, карточки — у всех одна.
 *
 * <p>Каждый раздел, который что-то меняет во внешнем виде, начинается с
 * превью: переключатель "поле ввода в стиле iOS" сам по себе ничего не
 * показывает, и раньше проверять приходилось выходом в чат и обратно.
 */
public class NebulaSectionFragment extends BaseFragment {

    public static final int SECTION_APPEARANCE = 0;
    public static final int SECTION_CHATS = 1;
    public static final int SECTION_TABS = 2;
    public static final int SECTION_ABOUT = 3;
    public static final int SECTION_GENERAL = 4;

    private final int section;
    private LinearLayout content;
    /** Превью раздела: их перерисовываем после каждого переключателя. */
    private final List<NebulaPreview> previews = new ArrayList<>();

    public NebulaSectionFragment(int section) {
        this.section = section;
    }

    @Override
    public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(titleKey()));
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

    private int titleKey() {
        switch (section) {
            case SECTION_CHATS:
                return R.string.NebulaSectionChats;
            case SECTION_TABS:
                return R.string.NebulaSectionPanel;
            case SECTION_ABOUT:
                return R.string.NebulaSectionAbout;
            case SECTION_GENERAL:
                return R.string.NebulaSectionGeneral;
            default:
                return R.string.NebulaSectionLook;
        }
    }

    private void build(Context context, NebulaTheme theme) {
        content.removeAllViews();
        previews.clear();
        switch (section) {
            case SECTION_CHATS:
                buildChats(context);
                break;
            case SECTION_TABS:
                buildTabs(context);
                break;
            case SECTION_ABOUT:
                buildAbout(context);
                break;
            case SECTION_GENERAL:
                buildGeneral(context);
                break;
            default:
                buildAppearance(context, theme);
                break;
        }
    }

    // --- разделы ------------------------------------------------------------

    private void buildAppearance(Context context, NebulaTheme theme) {
        NebulaCard card = new NebulaCard(context);

        // Material You имеет смысл только там, где система отдаёт палитру;
        // на Android 11 и старше строка была бы обманом.
        if (theme.isDynamic()) {
            card.add(toggle(context, R.drawable.msg_customize,
                    R.string.NebulaMaterialYou, R.string.NebulaMaterialYouSub,
                    NebulaTheme.materialYouEnabled(),
                    value -> {
                        NebulaTheme.setMaterialYouEnabled(value);
                        NebulaTheme.applyMaterialYou(context);
                    }));
        }
        card.add(toggle(context, R.drawable.msg_edit,
                R.string.NebulaLoginStyleTitle, R.string.NebulaLoginStyleSub,
                NebulaLoginStyle.enabled(), NebulaLoginStyle::setEnabled));
        card.add(toggle(context, R.drawable.msg_openprofile,
                R.string.NebulaProfileStyle, R.string.NebulaProfileStyleInfo,
                NebulaAppearance.profileStyle(), NebulaAppearance::setProfileStyle));
        card.add(toggle(context, R.drawable.msg_list,
                R.string.NebulaHideDividers, R.string.NebulaHideDividersSub,
                NebulaAppearance.hideDividers(), NebulaAppearance::setHideDividers));
        content.addView(card, cardParams());

        content.addView(NebulaCard.header(context,
                LocaleController.getString(R.string.NebulaListSection)));
        NebulaCard list = new NebulaCard(context);
        list.add(toggle(context, R.drawable.msg_search,
                R.string.NebulaHideSearchField, R.string.NebulaHideSearchFieldSub,
                NebulaAppearance.hideSearchField(), NebulaAppearance::setHideSearchField));
        list.add(toggle(context, R.drawable.files_folder,
                R.string.NebulaHideTabCounters, R.string.NebulaHideTabCountersSub,
                NebulaAppearance.hideTabCounters(), NebulaAppearance::setHideTabCounters));
        content.addView(list, cardParams());
        content.addView(NebulaMenuFragment.placeholder(context,
                LocaleController.getString(R.string.NebulaRestartHint)));
    }

    private void buildChats(Context context) {
        content.addView(NebulaCard.header(context,
                LocaleController.getString(R.string.NebulaChatHeaderSection)));
        content.addView(preview(context, NebulaPreview.KIND_HEADER));
        NebulaCard header = new NebulaCard(context);
        header.add(toggle(context, R.drawable.msg_customize,
                R.string.NebulaFloatingHeader, R.string.NebulaFloatingHeaderInfo,
                NebulaAppearance.chatHeader(), NebulaAppearance::setChatHeader));
        content.addView(header, cardParams());

        content.addView(NebulaCard.header(context,
                LocaleController.getString(R.string.NebulaComposerSection)));
        content.addView(preview(context, NebulaPreview.KIND_COMPOSER));
        NebulaCard composer = new NebulaCard(context);
        composer.add(toggle(context, R.drawable.msg_edit,
                R.string.NebulaIosComposer, R.string.NebulaIosComposerInfo,
                NebulaAppearance.iosComposer(), NebulaAppearance::setIosComposer));
        composer.add(toggle(context, R.drawable.msg_photo_settings,
                R.string.NebulaHideCameraTitle, R.string.NebulaHideCameraSub,
                NebulaAppearance.hideAttachCamera(), NebulaAppearance::setHideAttachCamera));
        content.addView(composer, cardParams());

        content.addView(NebulaCard.header(context,
                LocaleController.getString(R.string.NebulaChatBehaviour)));
        NebulaCard behaviour = new NebulaCard(context);
        behaviour.add(toggle(context, R.drawable.msg_channel,
                R.string.NebulaHideSendAs, R.string.NebulaHideSendAsSub,
                NebulaAppearance.hideSendAs(), NebulaAppearance::setHideSendAs));
        behaviour.add(toggle(context, R.drawable.msg_discussion,
                R.string.NebulaNoNextChannel, R.string.NebulaNoNextChannelSub,
                NebulaAppearance.disableNextChannel(), NebulaAppearance::setDisableNextChannel));
        content.addView(behaviour, cardParams());

        content.addView(NebulaMenuFragment.placeholder(context,
                LocaleController.getString(R.string.NebulaRestartHint)));
    }

    private void buildTabs(Context context) {
        content.addView(preview(context, NebulaPreview.KIND_TABS));

        NebulaCard card = new NebulaCard(context);
        card.add(toggle(context, R.drawable.msg_customize,
                R.string.NebulaBottomBarTitle, R.string.NebulaBottomBarSub,
                NebulaBottomBar.enabled(), NebulaBottomBar::setEnabled));
        card.add(tabToggle(context, NebulaBottomBar.TAB_CONTACTS,
                R.drawable.msg_contacts, R.string.NebulaTabContacts));
        card.add(tabToggle(context, NebulaBottomBar.TAB_SETTINGS,
                R.drawable.msg_settings, R.string.NebulaTabSettings));
        card.add(tabToggle(context, NebulaBottomBar.TAB_PROFILE,
                R.drawable.msg_openprofile, R.string.NebulaTabProfile));
        content.addView(card, cardParams());

        NebulaCard side = new NebulaCard(context);
        side.add(toggle(context, R.drawable.msg_list,
                R.string.NebulaSidePanelTitle, R.string.NebulaSidePanelSub,
                NebulaBottomBar.sidebarEnabled(), NebulaBottomBar::setSidebarEnabled));
        content.addView(side, cardParams());
        content.addView(NebulaMenuFragment.placeholder(context,
                LocaleController.getString(R.string.NebulaBottomBarHint)));
    }

    /**
     * Настройки самого Telegram, у которых нет своего экрана. Код ими
     * пользуется, а включить их было негде — поэтому это не наши выдумки, а
     * доступ к тому, что уже написано.
     */
    private void buildGeneral(Context context) {
        content.addView(NebulaCard.header(context,
                LocaleController.getString(R.string.NebulaTextSection)));
        NebulaCard text = new NebulaCard(context);
        text.add(toggle(context, R.drawable.msg_emoji_smiles,
                R.string.NebulaSystemEmoji, R.string.NebulaSystemEmojiSub,
                NebulaAppearance.systemEmoji(), NebulaAppearance::setSystemEmoji));
        text.add(toggle(context, R.drawable.msg_edit,
                R.string.NebulaSystemFont, R.string.NebulaSystemFontSub,
                NebulaAppearance.systemBoldFont(), NebulaAppearance::setSystemBoldFont));
        text.add(toggle(context, R.drawable.msg_emoji_smiles,
                R.string.NebulaBigEmoji, R.string.NebulaBigEmojiSub,
                org.telegram.messenger.SharedConfig.allowBigEmoji,
                value -> org.telegram.messenger.SharedConfig.toggleBigEmoji()));
        content.addView(text, cardParams());

        content.addView(NebulaCard.header(context,
                LocaleController.getString(R.string.NebulaMediaSection)));
        NebulaCard media = new NebulaCard(context);
        media.add(toggle(context, R.drawable.msg_voice_unmuted,
                R.string.NebulaRaiseToSpeak, R.string.NebulaRaiseToSpeakSub,
                org.telegram.messenger.SharedConfig.raiseToSpeak,
                value -> org.telegram.messenger.SharedConfig.toggleRaiseToSpeak()));
        media.add(toggle(context, R.drawable.msg_played,
                R.string.NebulaPauseOnRecord, R.string.NebulaPauseOnRecordSub,
                org.telegram.messenger.SharedConfig.pauseMusicOnRecord,
                value -> org.telegram.messenger.SharedConfig.togglePauseMusicOnRecord()));
        media.add(toggle(context, R.drawable.msg_played,
                R.string.NebulaNextMediaTap, R.string.NebulaNextMediaTapSub,
                org.telegram.messenger.SharedConfig.nextMediaTap,
                value -> org.telegram.messenger.SharedConfig.toggleNextMediaTap()));
        media.add(toggle(context, R.drawable.msg_download,
                R.string.NebulaStreamMedia, R.string.NebulaStreamMediaSub,
                org.telegram.messenger.SharedConfig.streamMedia,
                value -> org.telegram.messenger.SharedConfig.toggleStreamMedia()));
        content.addView(media, cardParams());
        content.addView(NebulaMenuFragment.placeholder(context,
                LocaleController.getString(R.string.NebulaRestartHint)));
    }

    private void buildAbout(Context context) {
        NebulaCard card = new NebulaCard(context);
        card.add(new NebulaRow(context)
                .icon(R.drawable.msg_info)
                .title(LocaleController.getString(R.string.nl_versions))
                .subtitle(versions(), true));
        content.addView(card, cardParams());
    }

    private String versions() {
        try {
            return "NebulaLink " + NebulaLink.version();
        } catch (Throwable e) {
            return "NebulaLink";
        }
    }

    // --- строительные блоки -------------------------------------------------

    /** Действие переключателя: значение уже новое, остаётся его сохранить. */
    private interface Setter {
        void apply(boolean value);
    }

    private NebulaRow toggle(Context context, int icon, int title, int subtitle,
                             boolean checked, Setter setter) {
        NebulaRow row = new NebulaRow(context)
                .icon(icon)
                .title(LocaleController.getString(title))
                .subtitle(LocaleController.getString(subtitle), false)
                .trailing(NebulaRow.TRAIL_SWITCH)
                .checked(checked);
        row.setOnClickListener(v -> {
            setter.apply(row.toggleChecked());
            refreshPreviews();
        });
        return row;
    }

    private NebulaRow tabToggle(Context context, String tab, int icon, int title) {
        NebulaRow row = new NebulaRow(context)
                .icon(icon)
                .title(LocaleController.getString(title))
                .trailing(NebulaRow.TRAIL_SWITCH)
                .checked(NebulaBottomBar.tabEnabled(tab));
        row.setOnClickListener(v -> {
            NebulaBottomBar.setTabEnabled(tab, row.toggleChecked());
            refreshPreviews();
        });
        return row;
    }

    private View preview(Context context, int kind) {
        NebulaPreview view = new NebulaPreview(context, kind);
        previews.add(view);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(6);
        params.bottomMargin = AndroidUtilities.dp(2);
        view.setLayoutParams(params);
        return view;
    }

    private void refreshPreviews() {
        for (NebulaPreview view : previews) {
            view.refresh();
        }
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(6);
        return params;
    }
}
