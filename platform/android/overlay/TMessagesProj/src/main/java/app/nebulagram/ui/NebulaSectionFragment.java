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

    public static final int SECTION_FOLDERS = 5, SECTION_MESSAGES = 6, SECTION_PROFILE = 7,
            SECTION_SWITCHES = 8, SECTION_CHAT_ACTIONS = 9;
    private String focusTitle;
    public NebulaSectionFragment focus(String title) { focusTitle = title; return this; }
    private void focusRow(View view) {
        if (view instanceof android.widget.TextView && focusTitle != null && focusTitle.contentEquals(((android.widget.TextView) view).getText())) {
            android.graphics.Rect rect = new android.graphics.Rect(); view.getDrawingRect(rect);
            content.offsetDescendantRectToMyCoords(view, rect);
            scroll.smoothScrollTo(0, Math.max(0, rect.top - AndroidUtilities.dp(32)));
            View row = (View) view.getParent(); row.setPressed(true); row.postDelayed(() -> row.setPressed(false), 900);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) focusRow(group.getChildAt(i));
        }
    }
    private NebulaFoldersPreview folderPreview;
    private final int section;
    private LinearLayout content;
    private FrameLayout root;
    private ScrollView scroll;
    private int paletteSurface, palettePrimary;
    /** Превью раздела: их перерисовываем после каждого переключателя. */
    private final List<NebulaPreview> previews = new ArrayList<>();
    private final List<NebulaWallpaperPreview> wallpaperPreviews = new ArrayList<>();

    public NebulaSectionFragment(int section) {
        this.section = section;
    }

    @Override
    public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        paletteSurface = theme.surface();
        palettePrimary = theme.primary();

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

        root = new FrameLayout(context);
        root.setBackgroundColor(theme.surface());

        scroll = new ScrollView(context);
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
        if (focusTitle != null) content.post(() -> focusRow(content));
        return fragmentView = root;
    }

    private int titleKey() {
        switch (section) {
            case SECTION_FOLDERS: return R.string.NebulaSectionFolders;
            case SECTION_MESSAGES: return R.string.NebulaSectionMessages;
            case SECTION_PROFILE: return R.string.NebulaSectionProfile;
            case SECTION_SWITCHES: return R.string.NebulaSwitches;
            case SECTION_CHAT_ACTIONS: return R.string.NebulaMenuActions;
            case SECTION_CHATS:
                return R.string.NebulaSectionChats;
            case SECTION_TABS:
                return R.string.NebulaSectionPanel;
            case SECTION_ABOUT:
                return R.string.NebulaSectionAbout;
            case SECTION_GENERAL:
                return R.string.NebulaSectionGeneral;
            default:
                // Заголовок экрана, а не шапка карточки: та написана капсом.
                return R.string.NebulaAppearanceTitle;
        }
    }

    private void build(Context context, NebulaTheme theme) {
        content.removeAllViews();
        previews.clear();
        wallpaperPreviews.clear();
        composerPreview = null;
        switch (section) {
            case SECTION_FOLDERS: buildFolders(context); break;
            case SECTION_MESSAGES: buildMessages(context); break;
            case SECTION_PROFILE: buildProfile(context); break;
            case SECTION_SWITCHES: buildSwitches(context); break;
            case SECTION_CHAT_ACTIONS: buildChatActions(context); break;
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
        NebulaExtras.appearance(this, content);
        NebulaCard card = new NebulaCard(context);
        card.add(link(context, R.drawable.msg_customize, R.string.NebulaSwitches, R.string.NebulaSwitchesInfo, SECTION_SWITCHES));

        // Material You имеет смысл только там, где система отдаёт палитру;
        // на Android 11 и старше строка была бы обманом.
        if (NebulaTheme.supportsDynamic()) {
            card.add(toggle(context, R.drawable.msg_customize,
                    R.string.NebulaMaterialYou, R.string.NebulaMaterialYouSub,
                    NebulaTheme.materialYouEnabled(),
                    value -> {
                        NebulaTheme.setMaterialYouEnabled(value);
                        NebulaTheme.applyMaterialYou(context);
                        refreshPalette();
                    }));
        }
        card.add(toggle(context, R.drawable.msg_edit,
                R.string.NebulaLoginStyleTitle, R.string.NebulaLoginStyleSub,
                NebulaLoginStyle.enabled(), NebulaLoginStyle::setEnabled));
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
        content.addView(list, cardParams());
        content.addView(NebulaMenuFragment.placeholder(context,
                LocaleController.getString(R.string.NebulaRestartHint)));
    }

    private void buildChats(Context context) {
        NebulaCard header = new NebulaCard(context);
        header.add(toggle(context, R.drawable.msg_customize,
                R.string.NebulaFloatingHeader, R.string.NebulaFloatingHeaderInfo,
                NebulaAppearance.chatHeader(), NebulaAppearance::setChatHeader));
        header.add(toggle(context, R.drawable.msg_customize,
                R.string.NebulaCenterHeader, R.string.NebulaCenterHeaderInfo,
                NebulaAppearance.centeredHeader(), NebulaAppearance::setCenteredHeader));
        header.add(toggle(context, R.drawable.msg_photo_settings,
                R.string.NebulaAdaptiveHeader, R.string.NebulaAdaptiveHeaderInfo,
                NebulaAppearance.adaptiveHeader(), NebulaAppearance::setAdaptiveHeader));
        final NebulaRow iosCount = toggle(context, R.drawable.msg_customize,
                R.string.NebulaUnreadIos, R.string.NebulaUnreadIosInfo,
                NebulaAppearance.iosUnread(), NebulaAppearance::setIosUnread);
        header.add(toggle(context, R.drawable.msg_msgbubble,
                R.string.NebulaUnread, R.string.NebulaUnreadInfo,
                NebulaAppearance.headerUnread(), value -> {
                    NebulaAppearance.setHeaderUnread(value);
                    iosCount.setVisibility(value ? View.VISIBLE : View.GONE);
                }));
        iosCount.setVisibility(NebulaAppearance.headerUnread() ? View.VISIBLE : View.GONE);
        header.add(iosCount);
        header.add(toggle(context, R.drawable.msg_customize,
                R.string.NebulaHighlights, R.string.NebulaHighlightsInfo,
                NebulaAppearance.glassHighlights(), value -> {
                    NebulaAppearance.setGlassHighlights(value);
                    org.telegram.ui.ActionBar.Theme.reloadAllResources(context);
                }));
        expandablePreview(context, "header", R.string.NebulaChatHeaderSection, headerPreview(context), header);

        composerPreview = new NebulaComposerPreview(context);
        NebulaCard composer = new NebulaCard(context);
        composer.add(toggle(context, R.drawable.msg_edit,
                R.string.NebulaIosComposer, R.string.NebulaIosComposerInfo,
                NebulaAppearance.iosComposer(), NebulaAppearance::setIosComposer));
        composer.add(toggle(context, R.drawable.msg_photo_settings,
                R.string.NebulaHideCameraTitle, R.string.NebulaHideCameraSub,
                NebulaAppearance.hideAttachCamera(), NebulaAppearance::setHideAttachCamera));
        composer.add(toggle(context, R.drawable.msg_channel,
                R.string.NebulaHideSendAs, R.string.NebulaHideSendAsSub,
                NebulaAppearance.hideSendAs(), NebulaAppearance::setHideSendAs));
        expandablePreview(context, "composer", R.string.NebulaComposerSection, composerPreview, composer);

        content.addView(NebulaCard.header(context,
                LocaleController.getString(R.string.NebulaChatBehaviour)));
        NebulaCard behaviour = new NebulaCard(context);
        behaviour.add(toggle(context, R.drawable.msg_discussion,
                R.string.NebulaNoNextChannel, R.string.NebulaNoNextChannelSub,
                NebulaAppearance.disableNextChannel(), NebulaAppearance::setDisableNextChannel));
        behaviour.add(link(context, R.drawable.msg_list, R.string.NebulaMenuActions, R.string.NebulaMenuActionsInfo, SECTION_CHAT_ACTIONS));
        content.addView(behaviour, cardParams());

        content.addView(NebulaMenuFragment.placeholder(context,
                LocaleController.getString(R.string.NebulaRestartHint)));
    }

    private void buildTabs(Context context) {
        content.addView(NebulaCard.header(context,
                LocaleController.getString(R.string.NebulaTabsSection)));
        NebulaCard card = new NebulaCard(context);
        card.add(toggle(context, R.drawable.msg_customize,
                R.string.NebulaBottomBarTitle, R.string.NebulaBottomBarSub,
                NebulaBottomBar.enabled(), NebulaBottomBar::setEnabled));
        content.addView(card, cardParams());

        content.addView(NebulaCard.header(context,
                LocaleController.getString(R.string.NebulaAppearanceTitle)));
        NebulaTabsEditor editor = new NebulaTabsEditor(context);
        editor.setOnChanged(editor::refresh);
        NebulaCard appearance = new NebulaCard(context);
        appearance.add(editor);
        appearance.add(toggle(context, R.drawable.msg_customize,
                R.string.NebulaCompactTabs, R.string.NebulaCompactTabsInfo,
                NebulaBottomBar.compact(), value -> {
                    NebulaBottomBar.setCompact(value);
                    editor.refresh();
                }));
        appearance.add(toggle(context, R.drawable.msg_photo_settings,
                R.string.NebulaTabLabels, R.string.NebulaTabLabelsSub,
                NebulaBottomBar.tabLabels(), value -> {
                    NebulaBottomBar.setTabLabels(value);
                    editor.refresh();
                }));
        content.addView(appearance, cardParams());
        content.addView(NebulaMenuFragment.placeholder(context,
                LocaleController.getString(R.string.NebulaTabsEditorHint)));

        content.addView(NebulaMenuFragment.placeholder(context,
                LocaleController.getString(R.string.NebulaBottomBarHint)));
    }

    private NebulaRow link(Context context, int icon, int title, int subtitle, int target) {
        return new NebulaRow(context).icon(icon).title(LocaleController.getString(title))
                .subtitle(LocaleController.getString(subtitle), false).trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> presentFragment(new NebulaSectionFragment(target)));
    }

    private void sample(Context context, int kind) {
        NebulaWallpaperPreview wallpaper = new NebulaWallpaperPreview(context);
        wallpaper.addView(new NebulaControlsPreview(context, kind));
        wallpaperPreviews.add(wallpaper);
        content.addView(wallpaper, cardParams());
    }

    private void buildSwitches(Context context) {
        String[] styles = {"Material", "iOS", "One UI", "Android"};
        for (int i = 0; i < styles.length; i++) {
            final int style = i;
            NebulaCard card = new NebulaCard(context);
            NebulaRow row = new NebulaRow(context).icon(R.drawable.msg_customize).title(styles[i])
                    .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaAppearance.switchStyle() == i);
            row.setOnClickListener(v -> { NebulaAppearance.setSwitchStyle(style); refreshPalette(); });
            card.add(row);
            android.widget.LinearLayout samples = new android.widget.LinearLayout(context);
            samples.setGravity(android.view.Gravity.CENTER);
            samples.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(16));
            for (boolean on : new boolean[] {false, true}) {
                NebulaSwitch control = new NebulaSwitch(context);
                control.setPreviewStyle(style);
                control.setChecked(on, false);
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(AndroidUtilities.dp(52), AndroidUtilities.dp(32));
                lp.leftMargin = lp.rightMargin = AndroidUtilities.dp(16);
                samples.addView(control, lp);
            }
            card.add(samples);
            content.addView(card, cardParams());
        }
    }

    private void buildFolders(Context context) {
        folderPreview = new NebulaFoldersPreview(context);
        content.addView(folderPreview, cardParams());
        NebulaCard card = new NebulaCard(context);
        card.add(toggle(context, R.drawable.nebula_cupertino_archive, R.string.NebulaHideAllChats, R.string.NebulaHideAllChatsInfo,
                NebulaAppearance.hideAllChats(), NebulaAppearance::setHideAllChats));
        card.add(toggle(context, R.drawable.nebula_cupertino_bell, R.string.NebulaHideTabCounters, R.string.NebulaHideTabCountersSub,
                NebulaAppearance.hideTabCounters(), NebulaAppearance::setHideTabCounters));
        int[] styles = {R.string.NebulaFolderLabels, R.string.NebulaFolderIcons, R.string.NebulaFolderBoth};
        NebulaRow style = new NebulaRow(context).icon(R.drawable.files_folder).title(LocaleController.getString(R.string.NebulaFolderStyle))
                .subtitle(LocaleController.getString(styles[NebulaAppearance.folderStyle()]), false).trailing(NebulaRow.TRAIL_CHEVRON);
        style.setOnClickListener(v -> new org.telegram.ui.ActionBar.AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.NebulaFolderStyle))
                .setItems(new CharSequence[] {LocaleController.getString(styles[0]), LocaleController.getString(styles[1]), LocaleController.getString(styles[2])},
                    (d, which) -> { NebulaAppearance.setFolderStyle(which); refreshPalette(); }).show());
        card.add(style);
        card.add(toggle(context, R.drawable.nebula_cupertino_edit, R.string.NebulaFolderOutline, R.string.NebulaFolderOutlineInfo,
                NebulaAppearance.folderOutline(), NebulaAppearance::setFolderOutline));
        card.add(toggle(context, R.drawable.nebula_cupertino_list, R.string.NebulaFolderTitle, R.string.NebulaFolderTitleSub,
                NebulaAppearance.folderTitle(), NebulaAppearance::setFolderTitle));
        card.add(new NebulaRow(context).icon(R.drawable.files_folder).title(LocaleController.getString(R.string.Filters))
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> presentFragment(new org.telegram.ui.FiltersSetupActivity())));
        content.addView(card, cardParams());
    }

    private void buildMessages(Context context) {
        NebulaExtras.messages(this, content);
        sample(context, NebulaControlsPreview.MESSAGE);
        NebulaCard card = new NebulaCard(context);
        card.add(toggle(context, R.drawable.msg_recent, R.string.NebulaSeconds, R.string.NebulaSecondsSub,
                NebulaAppearance.secondsInTime(), NebulaAppearance::setSecondsInTime));
        card.add(toggle(context, R.drawable.msg_customize, R.string.NebulaHidePremium, R.string.NebulaHidePremiumInfo,
                NebulaAppearance.hidePremiumStatus(), NebulaAppearance::setHidePremiumStatus));
        card.add(toggle(context, R.drawable.menu_reply, R.string.NebulaReplyBackground, R.string.NebulaReplyBackgroundInfo,
                NebulaAppearance.replyBackground(), NebulaAppearance::setReplyBackground));
        card.add(toggle(context, R.drawable.nebula_cupertino_sliders, R.string.NebulaReplyColors, R.string.NebulaReplyColorsInfo,
                NebulaAppearance.replyColors(), NebulaAppearance::setReplyColors));
        card.add(toggle(context, R.drawable.msg_emoji_smiles, R.string.NebulaReplyEmoji, R.string.NebulaReplyEmojiInfo,
                NebulaAppearance.replyEmoji(), NebulaAppearance::setReplyEmoji));
        content.addView(card, cardParams());
        NebulaCard menu = new NebulaCard(context);
        NebulaRow below = toggle(context, R.drawable.msg_list, R.string.NebulaMenuBelow, R.string.NebulaMenuBelowInfo,
                NebulaAppearance.messageMenuBelow(), NebulaAppearance::setMessageMenuBelow);
        below.setVisibility(NebulaAppearance.messageMenuBlur() ? View.VISIBLE : View.GONE);
        menu.add(toggle(context, R.drawable.msg_customize, R.string.NebulaMenuBlur, R.string.NebulaMenuBlurInfo,
                NebulaAppearance.messageMenuBlur(), value -> {
                    NebulaAppearance.setMessageMenuBlur(value);
                    NebulaVisibility.animate(below, value);
                }));
        menu.add(below);
        content.addView(menu, cardParams());
    }

    private void buildProfile(Context context) {
        sample(context, NebulaControlsPreview.PROFILE);
        NebulaCard card = new NebulaCard(context);
        card.add(toggle(context, R.drawable.msg_openprofile, R.string.NebulaProfileStyle, R.string.NebulaProfileStyleInfo,
                NebulaAppearance.profileStyle(), NebulaAppearance::setProfileStyle));
        card.add(toggle(context, R.drawable.msg_photo_settings, R.string.NebulaProfilePhotoBanner, R.string.NebulaProfilePhotoBannerInfo,
                NebulaAppearance.profilePhotoBanner(), NebulaAppearance::setProfilePhotoBanner));
        card.add(toggle(context, R.drawable.nebula_cupertino_chat, R.string.NebulaProfileChannel, R.string.NebulaProfileChannelInfo,
                NebulaAppearance.profileChannel(), NebulaAppearance::setProfileChannel));
        card.add(toggle(context, R.drawable.msg_calendar, R.string.NebulaProfileBirthday, R.string.NebulaProfileBirthdayInfo,
                NebulaAppearance.profileBirthday(), NebulaAppearance::setProfileBirthday));
        card.add(toggle(context, R.drawable.nebula_cupertino_globe, R.string.NebulaProfileBusiness, R.string.NebulaProfileBusinessInfo,
                NebulaAppearance.profileBusiness(), NebulaAppearance::setProfileBusiness));
        card.add(toggle(context, R.drawable.nebula_cupertino_photo, R.string.NebulaProfileBackground, R.string.NebulaProfileBackgroundInfo,
                NebulaAppearance.profileBackground(), NebulaAppearance::setProfileBackground));
        card.add(toggle(context, R.drawable.msg_emoji_smiles, R.string.NebulaProfileEmoji, R.string.NebulaProfileEmojiInfo,
                NebulaAppearance.profileEmoji(), NebulaAppearance::setProfileEmoji));
        content.addView(card, cardParams());
    }

    private void buildChatActions(Context context) {
        NebulaCard card = new NebulaCard(context);
        card.add(toggle(context, R.drawable.msg_calls, R.string.NebulaMenuCall, R.string.NebulaMenuActionHint,
                NebulaAppearance.menuCall(), NebulaAppearance::setMenuCall));
        card.add(toggle(context, R.drawable.msg_calls, R.string.NebulaMenuVideo, R.string.NebulaMenuActionHint,
                NebulaAppearance.menuVideo(), NebulaAppearance::setMenuVideo));
        card.add(toggle(context, R.drawable.msg_search, R.string.NebulaMenuSearch, R.string.NebulaMenuActionHint,
                NebulaAppearance.menuSearch(), NebulaAppearance::setMenuSearch));
        card.add(toggle(context, R.drawable.msg_notifications, R.string.NebulaMenuMute, R.string.NebulaMenuActionHint,
                NebulaAppearance.menuMute(), NebulaAppearance::setMenuMute));
        content.addView(card, cardParams());
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

    private View headerPreview(Context context) {
        NebulaPreview view = new NebulaPreview(context);
        previews.add(view);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(6);
        params.bottomMargin = AndroidUtilities.dp(2);
        view.setLayoutParams(params);
        return view;
    }

    private void refreshPreviews() {
        if (folderPreview != null) folderPreview.refresh();
        for (NebulaWallpaperPreview view : wallpaperPreviews) {
            view.invalidate();
            for (int i = 0; i < view.getChildCount(); i++) view.getChildAt(i).invalidate();
        }
        if (composerPreview != null) composerPreview.refresh();
        for (NebulaPreview view : previews) {
            view.refresh();
        }
    }

    private NebulaComposerPreview composerPreview;

    private void refreshPalette() {
        if (root == null || content == null) return;
        int y = scroll.getScrollY();
        NebulaTheme theme = NebulaTheme.of(root.getContext());
        paletteSurface = theme.surface();
        palettePrimary = theme.primary();
        root.setBackgroundColor(theme.surface());
        actionBar.setBackgroundColor(theme.surface());
        actionBar.setTitleColor(theme.onSurface());
        actionBar.setItemsColor(theme.onSurface(), false);
        build(root.getContext(), theme);
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    @Override
    public boolean isLightStatusBar() {
        return !NebulaTheme.of(getContext()).isDark();
    }

    private final java.util.Map<String, Boolean> expandedPreviews = new java.util.HashMap<>();

    private void expandablePreview(Context context, String key, int title, View preview, View settings) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        NebulaCard heading = new NebulaCard(context);
        NebulaRow row = new NebulaRow(context).icon(R.drawable.msg_customize)
                .title(LocaleController.getString(title)).trailing(NebulaRow.TRAIL_CHEVRON);
        View chevron = row.getChildAt(row.getChildCount() - 1);
        heading.add(row);
        group.addView(heading);
        LinearLayout details = new LinearLayout(context);
        details.setOrientation(LinearLayout.VERTICAL);
        NebulaWallpaperPreview wallpaper = new NebulaWallpaperPreview(context);
        wallpaperPreviews.add(wallpaper);
        wallpaper.addView(preview, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                AndroidUtilities.dp(72)));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewParams.topMargin = previewParams.bottomMargin = AndroidUtilities.dp(12);
        details.addView(wallpaper, previewParams);
        details.addView(settings);
        boolean expanded = focusTitle != null || (expandedPreviews.containsKey(key) ? expandedPreviews.get(key) : "header".equals(key));
        details.setVisibility(expanded ? View.VISIBLE : View.GONE);
        chevron.setRotation(expanded ? -90 : 90);
        row.setSelected(expanded);
        row.setOnClickListener(v -> {
            boolean open = !row.isSelected();
            expandedPreviews.put(key, open);
            NebulaVisibility.animate(details, open);
            chevron.animate().rotation(open ? -90 : 90).setDuration(220).start();
            row.setSelected(open);
        });
        group.addView(details);
        content.addView(group, cardParams());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (root == null) return;
        NebulaTheme theme = NebulaTheme.of(root.getContext());
        if (paletteSurface != theme.surface() || palettePrimary != theme.primary()) refreshPalette();
        else refreshPreviews();
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(6);
        return params;
    }
}
