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
        public void open(BaseFragment f) { openSection(f, section, title, -1); }
    }
    public static boolean openSection(BaseFragment f, int section, String focus, int row) {
        if (section >= 0 && section <= NebulaSectionFragment.SECTION_GLASS) {
            NebulaSectionFragment target = new NebulaSectionFragment(section);
            f.presentFragment(row >= 0 ? target.focusRowIndex(row) : target.focus(focus));
        } else if (section == -22) f.presentFragment(new NebulaSupportFragment());
        else if (section == -16) f.presentFragment(new NebulaTasksFragment());
        else if (section == -17) f.presentFragment(new NebulaMessageToolsFragment(f.getCurrentAccount(), 0, "", null));
        else if (section == -23) f.presentFragment(new NebulaTranslationFragment(f.getCurrentAccount(), 0));
        else if (section == -18) f.presentFragment(new NebulaSyncFragment());
        else if (section == -19) f.presentFragment(new NebulaLockedChatsFragment());
        else if (section == -20) f.presentFragment(new NebulaIconPickerFragment());
        else if (section == -21) f.presentFragment(new NebulaAiHistoryFragment());
        else if (section == -1) f.presentFragment(new NebulaSettingsFragment());
        else if (section == -10 || section == -11) f.presentFragment(new NebulaDesignFragment(section == -11));
        else if (section == -12) f.presentFragment(new NebulaAiSettingsFragment());
        else if (section == -24) f.presentFragment(new NebulaBrowserSettingsFragment());
        else if (section == -25) f.presentFragment(new NebulaIconPacksFragment());
        else if (section == -26) f.presentFragment(new NebulaCameraFragment());
        else if (section == -27) f.presentFragment(new NebulaMessageFilterFragment());
        else if (section == -28) f.presentFragment(new NebulaMessageMenuFragment());
        else if (section == -29) f.presentFragment(new NebulaAccountsFragment());
        else if (section == -30) f.presentFragment(new NebulaAudioSettingsFragment());
        else if (section == -31) f.presentFragment(new NebulaSettingsHubFragment(NebulaSettingsHubFragment.TOOLS));
        else if (section == -32) f.presentFragment(new NebulaLocalPinsFragment(f.getCurrentAccount()));
        else if (section == -33) f.presentFragment(new NebulaEditHistoryFragment(f.getCurrentAccount(), null));
        else if (section == -34) f.presentFragment(new NebulaFontFragment());
        else if (section == -35) f.presentFragment(new NebulaBotsFragment(f.getCurrentAccount()));
        else if (section == -13) NebulaUpdateSettingsSheet.show(f);
        else if (section == -14) f.presentFragment(new NebulaMenuFragment(NebulaMenuFragment.SCREEN_ADVANCED));
        else if (section == -15) f.presentFragment(row >= 0 ? new NebulaPrivacyFragment().focusRowIndex(row) : new NebulaPrivacyFragment().focus(focus));
        else return false;
        return true;
    }
    public static ArrayList<Entry> all() {
        ArrayList<Entry> result = new ArrayList<>();
        result.add(new Entry(-15, NebulaText.text("Скрыть строки чатов", "Hide chat rows"), NebulaText.text("Имена, фото и превью · показ списка", "Names, photos and previews · list presentation"), R.drawable.msg_secret));
        result.add(new Entry(-32, NebulaText.text("Локальные закрепления", "Local pins"), NebulaText.text("На устройстве · отдельно для папок", "On device · per folder"), R.drawable.msg_pin));
        result.add(new Entry(-33, NebulaText.text("История правок", "Edit history"), NebulaText.text("Предыдущие версии сообщений", "Previous message versions"), R.drawable.msg_edit));
        result.add(new Entry(-29, NebulaText.text("Аккаунты", "Accounts"), NebulaText.text("Поиск, порядок, переключение, добавить аккаунт", "Search, order, switch, add account"), R.drawable.msg_openprofile));
        result.add(new Entry(-30, NebulaText.text("Аудио и голоса", "Audio and voices"), NebulaText.text("Расшифровка голосовых, Aa, озвучивание, на устройстве", "Voice transcription, Aa, speech, on device"), R.drawable.msg_voicechat));
        result.add(new Entry(-31, NebulaText.text("ИИ и аудио", "AI and audio"), NebulaText.text("Сервисы, модели, перевод и распознавание", "Services, models, translation and recognition"), R.drawable.nebula_ai_spark));
        result.add(new Entry(1, NebulaText.text("Панель форматирования", "Formatting toolbar"), NebulaText.text("Клавиатура, жирный, курсив, цитата, спойлер, ссылка", "Keyboard, bold, italic, quote, spoiler, link"), R.drawable.msg_edit));
        result.add(new Entry(-27, NebulaText.text("Фильтр сообщений", "Message filter"), NebulaText.text("Слова, транслитерация, исключения, заблокированные", "Words, transliteration, exceptions, blocked users"), R.drawable.msg_search));
        result.add(new Entry(-28, NebulaText.text("Меню сообщения", "Message menu"), NebulaText.text("Действия, высота, компактный вид и размытие", "Actions, height, compact appearance and blur"), R.drawable.msg_list));
        result.add(new Entry(-26, NebulaText.text("Камера", "Camera"), NebulaText.text("Движок кружков, CameraX, качество, стабилизация, экспозиция и размытие", "Round-video engine, CameraX, quality, stabilization, exposure and blur"), R.drawable.msg_videocall));
        result.add(new Entry(-22, NebulaText.text("Поддержать проект", "Support the project"),
                NebulaText.text("Донат и значок поддержки NebulaGram", "Donation and NebulaGram supporter badge"), R.drawable.nebula_settings_support));
        result.add(new Entry(5, NebulaText.text("Оформление папок", "Folder appearance"),
                NebulaText.text("iOS, жидкое стекло, обычные, минималистичные", "iOS, Liquid Glass, ordinary, minimal"), R.drawable.files_folder));
        result.add(new Entry(-14, NebulaText.text("Кнопка NebulaLink", "NebulaLink button"), NebulaText.text("Щит на главной, подключение, сервер, пинг", "Shield on home, connection, server, ping"), R.drawable.nebula_link_shield));
        result.add(new Entry(12, NebulaText.text("Стекло", "Glass"), NebulaText.text("Прозрачность, размытие, блики", "Transparency, blur, highlights"), R.drawable.msg_customize));
        result.add(new Entry(12, NebulaText.text("Настроить стекло", "Customize glass"), NebulaText.text("Прозрачность, размытие, блики", "Transparency, blur, highlights"), R.drawable.msg_customize));
        result.add(new Entry(12, NebulaText.text("Виброотклик стекла", "Glass haptics"), "NebulaGram", R.drawable.msg_customize));
        result.add(new Entry(-13, NebulaText.text("Обновления NebulaGram", "NebulaGram updates"), NebulaText.text("Версия, APK, скачать и установить", "Version, APK, download and install"), R.drawable.msg_download));
        result.add(new Entry(-24, NebulaText.text("Браузер и блокировка рекламы", "Browser and ad blocking"), "EasyList · " + NebulaText.text("фильтры и исключения", "filters and site exceptions"), R.drawable.msg_language));
        result.add(new Entry(-25, NebulaText.text("Паки иконок · импорт", "Icon packs · import"), ".icons · Remix Outline", R.drawable.msg_customize));
        result.add(new Entry(-12, NebulaText.text("Сервисы и роли ИИ", "AI services and roles"), "Nebula AI · OpenRouter · Perplexity · Gemini Nano", R.drawable.nebula_ai_spark));
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
        result.add(new Entry(0, NebulaText.text("Центрировать заголовки", "Center titles"), "NebulaGram", R.drawable.msg_list));
        result.add(new Entry(0, NebulaText.text("«Чаты» вместо NebulaGram", "Show Chats instead of NebulaGram"), NebulaText.text("Оформление · заголовок главной", "Appearance · home title"), R.drawable.msg_list));
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
        result.add(new Entry(3, NebulaText.text("О программе", "About"), "NebulaGram", R.drawable.msg_info));
        result.add(new Entry(3, NebulaText.text("Релизы", "Releases"), "GitHub NebulaGram", R.drawable.msg_download));
        // Append, never insert before existing entries: their history IDs are positional.
        result.add(new Entry(4, NebulaText.text("История поиска настроек", "Settings search history"), "NebulaGram", R.drawable.msg_recent));
        result.add(new Entry(4, NebulaText.text("Очистить историю поиска настроек", "Clear settings search history"), "NebulaGram", R.drawable.msg_delete));
        result.add(new Entry(4, NebulaText.text("Истории в списке чатов", "Stories in the chat list"), NebulaText.text("Сторисы, главная, архив", "Stories, home, archive"), R.drawable.msg_photo_settings));
        result.add(new Entry(2, R.string.NebulaHideHomeCamera, R.string.NebulaHideHomeCameraInfo, R.drawable.outline_fab_story_24));
        result.add(new Entry(2, R.string.NebulaHideHomeCompose, R.string.NebulaHideHomeComposeInfo, R.drawable.filled_fab_compose_32));
        result.add(new Entry(-15, NebulaText.text("Конфиденциальность", "Privacy"), NebulaText.text("Локальные копии, удалённые сообщения и пересылка", "Local copies, deleted messages and forwarding"), R.drawable.msg_secret));
        result.add(new Entry(-16, NebulaText.text("Список дел", "Tasks"), NebulaText.text("Задачи и напоминания", "Tasks and reminders"), R.drawable.msg_calendar));
        result.add(new Entry(-17, NebulaText.text("Инструменты текста", "Text tools"), NebulaText.text("Перевод, голос, краткое содержание", "Translation, speech, summaries"), R.drawable.msg_customize));
        result.add(new Entry(-18, NebulaText.text("Синхронизация настроек", "Settings sync"), NebulaText.text("Через «Избранное» Telegram", "Via Telegram Saved Messages"), R.drawable.msg_saved));
        result.add(new Entry(-19, NebulaText.text("Пароли чатов", "Chat passwords"), NebulaText.text("Отдельный пароль для каждого чата", "A separate password for each chat"), R.drawable.msg_secret));
        result.add(new Entry(-20, NebulaText.text("Иконка приложения", "App icon"), NebulaText.text("Оригинальные значки NebulaGram", "Original NebulaGram icons"), R.drawable.msg_customize));
        result.add(new Entry(0, NebulaText.text("Анимация переходов", "Transition animation"), NebulaText.text("Стандартная, AOSP или Spring", "Standard, AOSP or Spring"), R.drawable.msg_customize));
        result.add(new Entry(-21, NebulaText.text("История ИИ-чата", "AI chat history"), NebulaText.text("Локальные запросы, включаются отдельно", "Local requests, opt-in"), R.drawable.msg_recent));
        result.add(new Entry(-23, NebulaText.text("Перевод в реальном времени", "Real-time translation"), NebulaText.text("Входящие сообщения, автоперевод чата, ИИ", "Incoming messages, automatic chat translation, AI"), R.drawable.msg_customize));
        result.add(new Entry(-23, NebulaText.text("Перевод своих сообщений", "Translate my sent messages"), NebulaText.text("Отправленные сообщения, исходящие, ИИ", "Sent messages, outgoing, AI"), R.drawable.msg_customize));
        result.add(new Entry(-23, NebulaText.text("Перевод при наборе", "Translate while typing"), NebulaText.text("Мой текст, черновик, язык, задержка", "My text, draft, language, delay"), R.drawable.msg_customize));
        result.add(new Entry(-23, NebulaText.text("Быстрый перевод на устройстве", "Fast on-device translation"), NebulaText.text("Языковые пакеты, живой перевод, движок, Wi-Fi", "Language packs, live translation, engine, Wi-Fi"), R.drawable.msg_customize));
        result.add(new Entry(-23, NebulaText.text("Подставлять перевод сразу", "Insert translation automatically"), NebulaText.text("Оригинал, возврат текста, перевод как в клавиатуре", "Original, restore text, keyboard translation"), R.drawable.msg_customize));
        result.add(new Entry(-23, NebulaText.text("Кнопка ИИ в поле ввода", "AI button in composer"), NebulaText.text("Инструменты, подписи к вложениям, значок", "Tools, attachment captions, shortcut"), R.drawable.msg_customize));
        result.add(new Entry(-23, NebulaText.text("Языки перевода", "Translation languages"), NebulaText.text("Язык входящих и моего текста", "Incoming and outgoing language"), R.drawable.msg_customize));
        result.add(new Entry(12, NebulaText.text("Качество жидкого стекла", "Liquid Glass quality"), NebulaText.text("Оптимизация, размытие, блики, прозрачность", "Performance, blur, highlights, transparency"), R.drawable.msg_customize));
        result.add(new Entry(7, NebulaText.text("Баннер профиля", "Profile banner"), NebulaText.text("Фото, фон, плавный переход", "Photo, background, gradient"), R.drawable.msg_customize));
        result.add(new Entry(-34, NebulaText.text("Свой шрифт", "Custom font"), "TTF · OTF · " + NebulaText.text("шрифт приложения", "app font"), R.drawable.msg_edit));
        result.add(new Entry(-35, NebulaText.text("Мои боты", "My bots"), NebulaText.text("Переводы, меню, права, звёзды и бизнес", "Translations, menu, rights, stars and business"), R.drawable.msg_settings));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, NebulaText.text("Очередь медленного режима", "Slow mode queue"), NebulaText.text("Отложенные текстовые сообщения", "Scheduled text messages"), R.drawable.msg_calendar));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, NebulaText.text("Выделение без лимита 100", "Unlimited selection"), NebulaText.text("Выбрать загруженные сообщения", "Select loaded messages"), R.drawable.msg_select));
        result.add(new Entry(NebulaSectionFragment.SECTION_APPEARANCE, NebulaText.text("Свечение медиа", "Ambient media"), NebulaText.text("Подсветка фото и видео в просмотрщике", "Photo and video glow in viewer"), R.drawable.msg_customize));
        result.add(new Entry(NebulaSectionFragment.SECTION_APPEARANCE, NebulaText.text("Палитра Monet", "Monet palette"), "Monet · Material 3 · Material You", R.drawable.msg_customize));
        result.add(new Entry(NebulaSectionFragment.SECTION_APPEARANCE, NebulaText.text("Шапка Material 3", "Material 3 header"), NebulaText.text("Размытый фон без преломления и скруглённый нижний край", "Blurred background without refraction and rounded bottom corners"), R.drawable.msg_customize));
        result.add(new Entry(NebulaSectionFragment.SECTION_APPEARANCE, NebulaText.text("Стеклянная шапка главной", "Glass home header"), NebulaText.text("Главная и заголовки · жидкое стекло", "Home and titles · Liquid Glass"), R.drawable.nebula_link_shield));
        result.add(new Entry(NebulaSectionFragment.SECTION_APPEARANCE, NebulaText.text("Появление текста", "Text appearance"), NebulaText.text("Плавное затухание при появлении и обновлении текста", "Short fade when message text appears or changes"), R.drawable.msg_edit));
        result.add(new Entry(NebulaSectionFragment.SECTION_APPEARANCE, NebulaText.text("Сжатие сообщений у краёв", "Message edge compression"), NebulaText.text("Плавное уменьшение сообщений у краёв экрана", "Smooth edge compression for messages"), R.drawable.msg_customize));
        result.add(new Entry(NebulaSectionFragment.SECTION_APPEARANCE, NebulaText.text("Анимация", "Animation"), "Expressive · " + NebulaText.text("Стандарт", "Standard"), R.drawable.msg_customize));
        result.add(new Entry(NebulaSectionFragment.SECTION_APPEARANCE, NebulaText.text("Список чатов", "Chat list"), NebulaText.text("Каждый чат своей карточкой", "Each chat on its own card"), R.drawable.msg_list));
        result.add(new Entry(NebulaSectionFragment.SECTION_APPEARANCE, NebulaText.text("Вкладки профиля", "Profile tabs"), NebulaText.text("Отдельные кнопки вместо общей полосы", "Separate buttons instead of one strip"), R.drawable.msg_openprofile));
        result.add(new Entry(NebulaSectionFragment.SECTION_APPEARANCE, NebulaText.text("Сжатие у краёв", "Edge compression"), NebulaText.text("Чаты плавно уходят за верхний край", "Chats slide away past the top edge"), R.drawable.msg_list));
        result.add(new Entry(NebulaSectionFragment.SECTION_APPEARANCE, NebulaText.text("Снежинки", "Snowflakes"), NebulaText.text("В списке чатов и переписках", "In the chat list and conversations"), R.drawable.msg_customize));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, NebulaText.text("Поле ввода", "Composer"), "Telegram · Expressive · " + NebulaText.text("Кнопки", "Buttons"), R.drawable.msg_edit));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, R.string.NebulaWidePosts, R.string.NebulaWidePostsSub, R.drawable.msg_customize));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, NebulaText.text("Фон отдельных чатов", "Per-chat wallpaper"), NebulaText.text("Обои для отдельных переписок", "Wallpaper for individual conversations"), R.drawable.msg_photo_settings));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, NebulaText.text("Цитировать ответы", "Quote replies"), NebulaText.text("Цитировать текст сообщения при ответе", "Quote message text when replying"), R.drawable.menu_reply));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, NebulaText.text("Кнопка «Без авторства»", "Without-author button"), NebulaText.text("Пересылка без автора при выделении сообщений", "Forward without author when selecting messages"), R.drawable.msg_forward));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, NebulaText.text("Отключить вибрацию чатов", "Disable chat haptics"), NebulaText.text("Отклик жестов и действий в переписке", "Gesture and action feedback in conversations"), R.drawable.msg_notifications));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, NebulaText.text("Вычисления при наборе", "Inline arithmetic"), NebulaText.text("Калькулятор и счёт выражений в поле ввода", "Calculator and math expressions in composer"), R.drawable.msg_edit));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, NebulaText.text("Количество пересылок", "Forward count"), NebulaText.text("Рядом с просмотрами постов каналов", "Beside channel post views"), R.drawable.msg_forward));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, NebulaText.text("Счётчики только со звуком", "Unmuted folder counters"), NebulaText.text("Не учитывать чаты без звука в числах папок", "Exclude muted chats from folder counts"), R.drawable.files_folder));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, NebulaText.text("Время у стикеров", "Sticker time"), NebulaText.text("Обычно, сбоку или скрыто", "Default, beside sticker or hidden"), R.drawable.msg_emoji_smiles));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, NebulaText.text("Действия свайпа", "Swipe actions"), NebulaText.text("Ответ, копирование, перевод и инструменты", "Reply, copy, translation and tools"), R.drawable.msg_list));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, NebulaText.text("Эффект удаления", "Deletion effect"), NebulaText.text("Пыль · анимация распыления сообщений", "Dust · Thanos message disintegration"), R.drawable.msg_delete));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, NebulaText.text("Стикеры", "Stickers"), NebulaText.text("Размер стикеров, эмодзи и GIF", "Sticker, emoji and GIF size"), R.drawable.msg_emoji_smiles));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, NebulaText.text("Анимированные эмодзи", "Animated emoji"), NebulaText.text("Размер стикеров, эмодзи и GIF", "Sticker, emoji and GIF size"), R.drawable.msg_emoji_smiles));
        result.add(new Entry(NebulaSectionFragment.SECTION_CHATS, "GIF", NebulaText.text("Размер стикеров, эмодзи и GIF", "Sticker, emoji and GIF size"), R.drawable.msg_gif));
        result.add(new Entry(NebulaSectionFragment.SECTION_GENERAL, NebulaText.text("Формат ID", "ID format"), "Telegram · Bot API · -100", R.drawable.msg_copy));
        result.add(new Entry(NebulaSectionFragment.SECTION_GENERAL, NebulaText.text("Плавное затухание", "Smooth fading"), NebulaText.text("Плавнее менять прозрачность при переходах", "Smooth opacity during navigation transitions"), R.drawable.msg_customize));
        result.add(new Entry(NebulaSectionFragment.SECTION_GENERAL, "Predictive Back", NebulaText.text("Предпросмотр возврата системным жестом", "Preview navigation with the system back gesture"), R.drawable.msg_customize));
        result.add(new Entry(NebulaSectionFragment.SECTION_GENERAL, NebulaText.text("Приглушить не-контакты", "Silence non-contacts"), NebulaText.text("Сообщения незнакомых людей без звука и вибрации", "Messages from unknown people without sound or vibration"), R.drawable.msg_notifications));
        result.add(new Entry(NebulaSectionFragment.SECTION_GENERAL, NebulaText.text("Игнорировать упоминания", "Ignore mentions"), NebulaText.text("Не уведомлять об упоминаниях и скрывать счётчик", "Skip mention notifications and hide counter"), R.drawable.msg_mentions));
        result.add(new Entry(NebulaSectionFragment.SECTION_GENERAL, NebulaText.text("Архивировать новые истории", "Archive new stories"), NebulaText.text("Автоматическая архивация историй пользователей и каналов", "Automatic archiving for user and channel stories"), R.drawable.msg_photo_settings));
        result.add(new Entry(NebulaSectionFragment.SECTION_GENERAL, NebulaText.text("Память приложения", "App memory"), NebulaText.text("Оперативная память, JVM heap и кэш", "RAM, JVM heap and cache status"), R.drawable.msg_settings));
        result.add(new Entry(NebulaSectionFragment.SECTION_FOLDERS, NebulaText.text("Как на iOS", "Like iOS"), NebulaText.text("Папки снизу, жидкое стекло", "Bottom folders, Liquid Glass"), R.drawable.files_folder));
        result.add(new Entry(NebulaSectionFragment.SECTION_FOLDERS, R.string.NebulaMutedInTabCounters, R.string.NebulaMutedInTabCountersInfo, R.drawable.msg_mute));
        result.add(new Entry(NebulaSectionFragment.SECTION_FOLDERS, R.string.NebulaFolderTabsBottom, R.string.NebulaFolderTabsBottomInfo, R.drawable.files_folder));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Камера кружка", "Round video camera"), NebulaText.text("Фронтальная, основная или спрашивать перед записью", "Front, rear or ask before recording"), R.drawable.msg_videocall));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Следующее голосовое автоматически", "Autoplay next voice message"), NebulaText.text("Медиа и сообщения", "Media and messages"), R.drawable.msg_voicechat));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Запускать видео кнопкой громкости", "Play video with a volume key"), NebulaText.text("Медиа и сообщения", "Media and messages"), R.drawable.msg_played));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Пауза видео при сворачивании", "Pause video in the background"), NebulaText.text("Медиа и сообщения", "Media and messages"), R.drawable.msg_played));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Показывать дату оригинала пересылки", "Show forwarded message's original date"), NebulaText.text("Медиа и сообщения", "Media and messages"), R.drawable.msg_recent));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Карандаш вместо «изменено»", "Pencil instead of “edited”"), NebulaText.text("Медиа и сообщения", "Media and messages"), R.drawable.msg_edit));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Кнопка быстрой пересылки", "Quick share button"), NebulaText.text("Медиа и сообщения", "Media and messages"), R.drawable.msg_share));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Эффекты Premium-стикеров", "Premium sticker effects"), NebulaText.text("Медиа и сообщения", "Media and messages"), R.drawable.msg_emoji_smiles));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Анимация реакций", "Reaction animations"), NebulaText.text("Медиа и сообщения", "Media and messages"), R.drawable.msg_reactions));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Открывать Instant View", "Open Instant View"), NebulaText.text("Медиа и сообщения", "Media and messages"), R.drawable.msg_language));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Удалять для всех по умолчанию", "Select delete for everyone by default"), NebulaText.text("Медиа и сообщения", "Media and messages"), R.drawable.msg_delete));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Скрывать клавиатуру при прокрутке", "Hide keyboard while scrolling"), NebulaText.text("Жесты и порог прокрутки чата", "Gestures and chat scroll threshold"), R.drawable.msg_list));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Шаг перемотки видео", "Video seek step"), NebulaText.text("5, 10, 15, 20 или 25 секунд", "5, 10, 15, 20 or 25 seconds"), R.drawable.msg_played));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Расшифровка аудио", "Audio transcription"), "Telegram · Nebula AI", R.drawable.msg_voicechat));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Микрофоны в режиме видеокамеры", "Camcorder microphone processing"), NebulaText.text("Запись звука", "Audio recording"), R.drawable.msg_voice_unmuted));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Стереозвук в кружках", "Stereo audio in round videos"), NebulaText.text("Два канала при записи видеосообщений", "Two-channel audio in video messages"), R.drawable.msg_voice_unmuted));
        result.add(new Entry(NebulaSectionFragment.SECTION_MESSAGES, NebulaText.text("Приглушать другие звуки при записи", "Suppress other audio while recording"), NebulaText.text("Системный аудиофокус при записи голосовых и кружков", "System audio focus while recording voice and round videos"), R.drawable.msg_voice_unmuted));
        result.add(new Entry(NebulaSectionFragment.SECTION_PROFILE, NebulaText.text("Скрывать номера телефонов", "Hide phone numbers"), NebulaText.text("Показывать в профиле", "Show in profile"), R.drawable.msg_calls));
        result.add(new Entry(NebulaSectionFragment.SECTION_PROFILE, NebulaText.text("Дата-центр фотографии", "Profile photo data center"), "DC · " + NebulaText.text("Показывать в профиле", "Show in profile"), R.drawable.msg_openprofile));
        result.add(new Entry(NebulaSectionFragment.SECTION_PROFILE, NebulaText.text("Мой значок", "My badge"), NebulaText.text("Значок профиля NebulaGram", "NebulaGram profile badge"), R.drawable.nebula_badge_star));
        result.add(new Entry(-15, NebulaText.text("Подтверждать удаление", "Authenticate before deletion"), NebulaText.text("Биометрия или пароль перед удалением чата и истории", "Biometrics or passcode before deleting chat or history"), R.drawable.msg_secret));
        result.add(new Entry(-15, NebulaText.text("Предпочитать системный PIN или пароль", "Prefer device PIN or passcode"), NebulaText.text("Защита удаления чатов", "Chat deletion protection"), R.drawable.msg_secret));
        result.add(new Entry(-15, NebulaText.text("Скрыть архив", "Hide archive"), NebulaText.text("Скрывает строку архива в списке чатов", "Hides the archive row in the chat list"), R.drawable.files_folder));
        result.add(new Entry(-15, NebulaText.text("Открыть архив", "Open archive"), NebulaText.text("Перейти к архивированным чатам", "Open archived chats"), R.drawable.files_folder));
        result.add(new Entry(-15, NebulaText.text("Сохранять удалённые сообщения", "Save deleted messages"), NebulaText.text("Локальный архив удалённых сообщений в чате", "Local archive of deleted messages in chat"), R.drawable.msg_delete));
        result.add(new Entry(-15, NebulaText.text("Сохранять в секретных чатах", "Retain in secret chats"), NebulaText.text("Удалённые сообщения в секретных чатах", "Deleted messages in secret chats"), R.drawable.msg_secret));
        result.add(new Entry(-15, NebulaText.text("Сохранять исчезающие сообщения", "Retain expiring messages"), NebulaText.text("Локальная копия сообщений с таймером", "Local copy of disappearing messages"), R.drawable.msg_autodelete));
        result.add(new Entry(-15, NebulaText.text("Значок удалённого сообщения", "Deleted message icon"), NebulaText.text("Символ или эмодзи вместо слова «Удалено»", "Symbol or emoji instead of Deleted"), R.drawable.msg_emoji_smiles));
        result.add(new Entry(-15, NebulaText.text("Как выделять", "How to mark"), NebulaText.text("Приглушением или цветом для удалённых сообщений", "Fading or colour tint for deleted messages"), R.drawable.msg_palette));
        result.add(new Entry(-15, NebulaText.text("Цвет выделения", "Marker colour"), NebulaText.text("Цвет подложки удалённых сообщений", "Highlight colour for deleted messages"), R.drawable.msg_colors));
        result.add(new Entry(-15, NebulaText.text("Редактирование перед пересылкой", "Edit before forwarding"), NebulaText.text("Изменение текста и подписи перед отправкой пересылки", "Change text and captions before sending forwarded messages"), R.drawable.msg_edit));
        result.add(new Entry(-15, NebulaText.text("Разрешить снимки и пересылку", "Allow screenshots and forwarding"), NebulaText.text("Снять ограничения копирования и скриншотов в защищённых чатах", "Lift copy and screenshot restrictions in protected chats"), R.drawable.msg_forward));
        result.add(new Entry(-15, NebulaText.text("Очистить удаленки", "Clear deleted messages"), NebulaText.text("Удалить сохранённые локальные копии удалённых сообщений", "Clear locally retained deleted messages"), R.drawable.msg_clearcache));
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
