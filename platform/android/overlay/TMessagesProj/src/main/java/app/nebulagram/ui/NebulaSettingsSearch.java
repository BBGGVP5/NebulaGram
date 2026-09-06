package app.nebulagram.ui;

import java.util.*;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ProfileActivity;

/** One index powers Telegram settings search and the global chat search. */
public final class NebulaSettingsSearch {
    public static final class Entry {
        public final int section, icon;
        public final String title, info;
        Entry(int section, int title, int info, int icon) {
            this(section, LocaleController.getString(title), LocaleController.getString(info), icon);
        }
        Entry(int section, String title, String info, int icon) { this.section = section; this.title = title; this.info = info; this.icon = icon; }
        public void open(BaseFragment f) {
            if (section == -1) f.presentFragment(new NebulaSettingsFragment());
            else if (section == -10 || section == -11) f.presentFragment(new NebulaDesignFragment(section == -11));
            else if (section == -12) f.presentFragment(new NebulaAiFragment());
            else f.presentFragment(new NebulaSectionFragment(section).focus(title));
        }
    }
    public static ArrayList<Entry> all() {
        ArrayList<Entry> result = new ArrayList<>();
        result.add(new Entry(0, R.string.NebulaMaterialYou, R.string.NebulaMaterialYouSub, R.drawable.msg_customize));
        result.add(new Entry(0, R.string.NebulaLoginStyleTitle, R.string.NebulaLoginStyleSub, R.drawable.msg_edit));
        result.add(new Entry(0, R.string.NebulaHideDividers, R.string.NebulaHideDividersSub, R.drawable.msg_list));
        result.add(new Entry(0, R.string.NebulaHideSearchField, R.string.NebulaHideSearchFieldSub, R.drawable.msg_search));
        result.add(new Entry(1, R.string.NebulaFloatingHeader, R.string.NebulaFloatingHeaderInfo, R.drawable.msg_customize));
        result.add(new Entry(1, R.string.NebulaCenterHeader, R.string.NebulaCenterHeaderInfo, R.drawable.msg_customize));
        result.add(new Entry(1, R.string.NebulaAdaptiveHeader, R.string.NebulaAdaptiveHeaderInfo, R.drawable.msg_photo_settings));
        result.add(new Entry(1, R.string.NebulaUnreadIos, R.string.NebulaUnreadIosInfo, R.drawable.msg_customize));
        result.add(new Entry(1, R.string.NebulaUnread, R.string.NebulaUnreadInfo, R.drawable.msg_msgbubble));
        result.add(new Entry(1, R.string.NebulaHighlights, R.string.NebulaHighlightsInfo, R.drawable.msg_customize));
        result.add(new Entry(1, R.string.NebulaIosComposer, R.string.NebulaIosComposerInfo, R.drawable.msg_edit));
        result.add(new Entry(1, R.string.NebulaHideCameraTitle, R.string.NebulaHideCameraSub, R.drawable.msg_photo_settings));
        result.add(new Entry(1, R.string.NebulaHideSendAs, R.string.NebulaHideSendAsSub, R.drawable.msg_channel));
        result.add(new Entry(1, R.string.NebulaNoNextChannel, R.string.NebulaNoNextChannelSub, R.drawable.msg_discussion));
        result.add(new Entry(2, R.string.NebulaBottomBarTitle, R.string.NebulaBottomBarSub, R.drawable.msg_customize));
        result.add(new Entry(2, R.string.NebulaCompactTabs, R.string.NebulaCompactTabsInfo, R.drawable.msg_customize));
        result.add(new Entry(2, R.string.NebulaTabLabels, R.string.NebulaTabLabelsSub, R.drawable.msg_photo_settings));
        result.add(new Entry(4, R.string.NebulaSystemEmoji, R.string.NebulaSystemEmojiSub, R.drawable.msg_emoji_smiles));
        result.add(new Entry(4, R.string.NebulaSystemFont, R.string.NebulaSystemFontSub, R.drawable.msg_edit));
        result.add(new Entry(4, R.string.NebulaBigEmoji, R.string.NebulaBigEmojiSub, R.drawable.msg_emoji_smiles));
        result.add(new Entry(4, R.string.NebulaRaiseToSpeak, R.string.NebulaRaiseToSpeakSub, R.drawable.msg_voice_unmuted));
        result.add(new Entry(4, R.string.NebulaPauseOnRecord, R.string.NebulaPauseOnRecordSub, R.drawable.msg_played));
        result.add(new Entry(4, R.string.NebulaNextMediaTap, R.string.NebulaNextMediaTapSub, R.drawable.msg_played));
        result.add(new Entry(4, R.string.NebulaStreamMedia, R.string.NebulaStreamMediaSub, R.drawable.msg_download));
        result.add(new Entry(5, R.string.NebulaHideAllChats, R.string.NebulaHideAllChatsInfo, R.drawable.nebula_cupertino_archive));
        result.add(new Entry(5, R.string.NebulaHideTabCounters, R.string.NebulaHideTabCountersSub, R.drawable.nebula_cupertino_bell));
        result.add(new Entry(5, R.string.NebulaFolderOutline, R.string.NebulaFolderOutlineInfo, R.drawable.nebula_cupertino_edit));
        result.add(new Entry(5, R.string.NebulaFolderTitle, R.string.NebulaFolderTitleSub, R.drawable.nebula_cupertino_list));
        result.add(new Entry(6, R.string.NebulaSeconds, R.string.NebulaSecondsSub, R.drawable.msg_recent));
        result.add(new Entry(6, R.string.NebulaHidePremium, R.string.NebulaHidePremiumInfo, R.drawable.msg_customize));
        result.add(new Entry(6, R.string.NebulaReplyBackground, R.string.NebulaReplyBackgroundInfo, R.drawable.menu_reply));
        result.add(new Entry(6, R.string.NebulaReplyColors, R.string.NebulaReplyColorsInfo, R.drawable.nebula_cupertino_sliders));
        result.add(new Entry(6, R.string.NebulaReplyEmoji, R.string.NebulaReplyEmojiInfo, R.drawable.msg_emoji_smiles));
        result.add(new Entry(6, R.string.NebulaMenuBelow, R.string.NebulaMenuBelowInfo, R.drawable.msg_list));
        result.add(new Entry(6, R.string.NebulaMenuBlur, R.string.NebulaMenuBlurInfo, R.drawable.msg_customize));
        result.add(new Entry(7, R.string.NebulaProfileStyle, R.string.NebulaProfileStyleInfo, R.drawable.msg_openprofile));
        result.add(new Entry(7, R.string.NebulaProfilePhotoBanner, R.string.NebulaProfilePhotoBannerInfo, R.drawable.msg_photo_settings));
        result.add(new Entry(7, R.string.NebulaProfileChannel, R.string.NebulaProfileChannelInfo, R.drawable.nebula_cupertino_chat));
        result.add(new Entry(7, R.string.NebulaProfileBirthday, R.string.NebulaProfileBirthdayInfo, R.drawable.msg_calendar));
        result.add(new Entry(7, R.string.NebulaProfileBusiness, R.string.NebulaProfileBusinessInfo, R.drawable.nebula_cupertino_globe));
        result.add(new Entry(7, R.string.NebulaProfileBackground, R.string.NebulaProfileBackgroundInfo, R.drawable.nebula_cupertino_photo));
        result.add(new Entry(7, R.string.NebulaProfileEmoji, R.string.NebulaProfileEmojiInfo, R.drawable.msg_emoji_smiles));
        result.add(new Entry(9, R.string.NebulaMenuCall, R.string.NebulaMenuActionHint, R.drawable.msg_calls));
        result.add(new Entry(9, R.string.NebulaMenuVideo, R.string.NebulaMenuActionHint, R.drawable.msg_calls));
        result.add(new Entry(9, R.string.NebulaMenuSearch, R.string.NebulaMenuActionHint, R.drawable.msg_search));
        result.add(new Entry(9, R.string.NebulaMenuMute, R.string.NebulaMenuActionHint, R.drawable.msg_notifications));
        result.add(new Entry(-10, NebulaText.text("Наборы иконок", "Icon packs"), "NebulaGram · Solar · iOS Outline", R.drawable.msg_customize));
        result.add(new Entry(-11, NebulaText.text("Закругление аватарок", "Avatar corners"), "NebulaGram", R.drawable.msg_openprofile));
        result.add(new Entry(-12, NebulaText.text("Искусственный интеллект", "AI assistant"), "NebulaGram · Gemini · Claude · GPT · API", R.drawable.msg_customize));
        result.add(new Entry(0, NebulaText.text("Центрировать заголовок на главной", "Center the home title"), "NebulaGram", R.drawable.msg_list));
        result.add(new Entry(6, NebulaText.text("Двойной тап по своему сообщению", "Double tap your message"), NebulaText.text("Редактировать, ответить, копировать", "Edit, reply, copy"), R.drawable.msg_edit));
        result.add(new Entry(0, R.string.NebulaAppearanceTitle, R.string.NebulaSettings, R.drawable.msg_customize));
        result.add(new Entry(1, R.string.NebulaSectionChats, R.string.NebulaSettings, R.drawable.msg_discussion));
        result.add(new Entry(2, R.string.NebulaSectionPanel, R.string.NebulaSettings, R.drawable.msg_list));
        result.add(new Entry(4, R.string.NebulaSectionGeneral, R.string.NebulaSettings, R.drawable.msg_settings));
        result.add(new Entry(5, R.string.NebulaFolderStyle, R.string.NebulaSettings, R.drawable.files_folder));
        result.add(new Entry(8, R.string.NebulaSwitches, R.string.NebulaSettings, R.drawable.msg_customize));
        result.add(new Entry(-12, NebulaText.text("API-ключ", "API key"), "NebulaGram", R.drawable.msg_customize));
        result.add(new Entry(-12, NebulaText.text("Модель ИИ", "AI model"), "NebulaGram", R.drawable.msg_customize));
        result.add(new Entry(-12, NebulaText.text("Системный промпт", "System prompt"), "NebulaGram", R.drawable.msg_customize));
        result.add(new Entry(-1, NebulaText.text("Экспорт настроек", "Export settings"), "NebulaGram", R.drawable.msg_customize));
        result.add(new Entry(-1, NebulaText.text("Импорт настроек", "Import settings"), "NebulaGram", R.drawable.msg_customize));
        result.add(new Entry(0, NebulaText.text("Анимации Liquid Glass", "Liquid Glass animations"), "NebulaGram", R.drawable.msg_customize));
        return result;
    }
    private static String normalize(String s) { return s.toLowerCase(Locale.ROOT).replace('ё', 'е').trim(); }
    public static ArrayList<Entry> match(String query) {
        ArrayList<Entry> result = new ArrayList<>();
        if (query == null || query.trim().length() < 2) return result;
        String[] words = normalize(query).split("\\s+");
        for (Entry e : all()) {
            String haystack = normalize(e.title + " " + e.info + " NebulaGram");
            boolean found = true;
            for (String word : words) if (!haystack.contains(word)) { found = false; break; }
            if (found) result.add(e);
            if (result.size() == 12) break;
        }
        return result;
    }
    public static ProfileActivity.SearchAdapter.SearchResult[] append(BaseFragment fragment, ProfileActivity.SearchAdapter.SearchResult[] nativeResults) {
        ArrayList<ProfileActivity.SearchAdapter.SearchResult> results = new ArrayList<>(Arrays.asList(nativeResults));
        int id = 9000;
        for (Entry e : all()) results.add(new ProfileActivity.SearchAdapter.SearchResult(id++, e.title, "NebulaGram", e.icon, () -> e.open(fragment)));
        return results.toArray(new ProfileActivity.SearchAdapter.SearchResult[0]);
    }
}
