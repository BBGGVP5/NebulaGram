package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;

/** Short category pages keep the settings entry screen easy to scan. */
public final class NebulaSettingsHubFragment extends BaseFragment {
    public static final int GENERAL = 0;
    public static final int APPEARANCE = 1;
    public static final int CHATS = 2;
    public static final int NAVIGATION = 3;
    public static final int PRIVACY = 4;
    public static final int TOOLS = 5;

    private final int category;

    public NebulaSettingsHubFragment(int category) {
        this.category = category;
    }

    @Override public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(title());
        actionBar.setBackgroundColor(theme.surface());
        actionBar.setTitleColor(theme.onSurface());
        actionBar.setItemsColor(theme.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(theme.surface());
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                AndroidUtilities.dp(16), AndroidUtilities.dp(28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String[] emoji = {"🧰", "🎨", "💬", "🧭", "🔐", "🤖"};
        String[] details = {
                NebulaText.text("Подключение и поведение приложения.", "Connection and app behavior."),
                NebulaText.text("Цвета, стекло и значки мессенджера.", "Colors, glass and messenger icons."),
                NebulaText.text("Ввод, сообщения, камера и задачи.", "Composer, messages, camera and tasks."),
                NebulaText.text("Нижняя панель и папки чатов.", "The bottom bar and chat folders."),
                NebulaText.text("Локальная защита и сохранение сообщений.", "Local protection and message retention."),
                NebulaText.text("Ассистент, перевод, расшифровка и голоса.", "Assistant, translation, transcription and voices.")};
        int intro = Math.max(0, Math.min(emoji.length - 1, category));
        content.addView(new NebulaSettingsHero(context, emoji[intro], title().toString(), details[intro]));
        NebulaCard card = new NebulaCard(context);
        switch (category) {
            case GENERAL:
                card.add(section(context, R.drawable.msg_settings, "Основные", "General",
                        NebulaSectionFragment.SECTION_GENERAL));
                card.add(row(context, R.drawable.msg_saved, "Синхронизация", "Settings sync", "Копия настроек", "Settings backup",
                        () -> presentFragment(new NebulaSyncFragment())));
                break;
            case APPEARANCE:
                card.add(section(context, R.drawable.msg_customize, "Оформление", "Appearance",
                        NebulaSectionFragment.SECTION_APPEARANCE));
                card.add(row(context, R.drawable.msg_customize, "Форма", "Shape", "Скругления и фото профиля", "Corners and profile photos",
                        () -> presentFragment(new NebulaShapeFragment())));
                card.add(row(context, R.drawable.nebula_settings_app_icon, "Иконка приложения", "App icon", "Значки NebulaGram", "NebulaGram icons",
                        () -> presentFragment(new NebulaIconPickerFragment())));
                break;
            case CHATS:
                card.add(row(context, R.drawable.msg_videocall, "Камера", "Camera", "Съёмка и кружки", "Capture and round videos",
                        () -> presentFragment(new NebulaCameraFragment())));
                card.add(section(context, R.drawable.msg_discussion, "Оформление чата", "Chat appearance",
                        NebulaSectionFragment.SECTION_CHATS));
                card.add(section(context, R.drawable.menu_reply, "Сообщения", "Messages",
                        NebulaSectionFragment.SECTION_MESSAGES));
                card.add(section(context, R.drawable.msg_openprofile, "Профиль", "Profile",
                        NebulaSectionFragment.SECTION_PROFILE));
                break;
            case NAVIGATION:
                card.add(section(context, R.drawable.msg_list, "Нижняя панель", "Bottom bar",
                        NebulaSectionFragment.SECTION_TABS));
                card.add(section(context, R.drawable.files_folder, "Папки", "Folders",
                        NebulaSectionFragment.SECTION_FOLDERS));
                card.add(row(context, R.drawable.msg_pin, "Локальные закрепления", "Local pins", "Только на этом устройстве", "Only on this device",
                        () -> presentFragment(new NebulaLocalPinsFragment(currentAccount))));
                break;
            case PRIVACY:
                card.add(row(context, R.drawable.msg_secret, "Удалённые сообщения", "Deleted messages", "Локальный архив", "Local archive",
                        () -> presentFragment(new NebulaPrivacyFragment())));
                card.add(row(context, R.drawable.nebula_settings_chat_lock, "Пароли чатов", "Chat passwords", "Локальная защита", "Local protection",
                        () -> presentFragment(new NebulaLockedChatsFragment())));
                break;
            case TOOLS:
                break;
            default:
                break;
        }
        if (!card.isEmpty()) NebulaFormUi.group(content, category == CHATS ? NebulaText.text("Оформление переписки", "Conversation appearance") : title().toString(), card);
        if (category == TOOLS) {
            NebulaCard ai = new NebulaCard(context);
            ai.add(row(context, R.drawable.nebula_ai_spark, "Nebula AI", "Nebula AI", "Чат, модель и инструкции", "Chat, model and instructions", () -> presentFragment(new NebulaAiSettingsFragment())));
            ai.add(row(context, R.drawable.msg_settings, "Сервисы ИИ", "AI services", "Подключения и ключи", "Connections and keys", () -> presentFragment(new NebulaAiServicesFragment())));
            ai.add(row(context, R.drawable.msg_translate, "Переводчик", "Translator", "На устройстве или через выбранный сервис", "On device or through your selected service", () -> presentFragment(new NebulaTranslationFragment(currentAccount, 0))));
            ai.add(row(context, R.drawable.msg_voicechat, "Аудио и голоса", "Audio and voices", "Расшифровка, перевод голосовых и озвучивание", "Transcription, voice translation and speech", () -> presentFragment(new NebulaAudioSettingsFragment())));
            NebulaFormUi.group(content, NebulaText.text("ИИ и аудио", "AI and audio"), ai);
        }
        if (category == CHATS) {
            NebulaCard tools = new NebulaCard(context);
            tools.add(row(context, R.drawable.nebula_settings_text_tools, "Инструменты текста", "Text tools", "Перевод, сокращение, озвучивание и задачи", "Translation, summaries, speech and tasks", () -> presentFragment(new NebulaMessageToolsFragment(null))));
            tools.add(row(context, R.drawable.menu_reply, "Быстрые ответы", "Quick replies", "Личные шаблоны для текущего аккаунта", "Personal templates for this account", () -> presentFragment(new NebulaQuickRepliesFragment(currentAccount, null, null))));
            tools.add(row(context, R.drawable.msg_calendar, "Список дел", "Tasks", "Задачи и напоминания", "Tasks and reminders", () -> presentFragment(new NebulaTasksFragment())));
            tools.add(row(context, R.drawable.msg_saved, "Метки в Избранном", "Saved Messages labels", "Ваши метки и сохранённые сообщения", "Your labels and saved messages", () -> presentFragment(new NebulaSavedTagsFragment(currentAccount, 0, null))));
            NebulaFormUi.group(content, NebulaText.text("Инструменты сообщений", "Message tools"), tools);
        }
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, scroll, -1);
    }

    private CharSequence title() {
        switch (category) {
            case GENERAL: return NebulaText.text("Основные", "General");
            case APPEARANCE: return NebulaText.text("Внешний вид", "Appearance");
            case CHATS: return NebulaText.text("Чаты и сообщения", "Chats and messages");
            case NAVIGATION: return NebulaText.text("Навигация", "Navigation");
            case PRIVACY: return NebulaText.text("Конфиденциальность", "Privacy");
            default: return NebulaText.text("ИИ и аудио", "AI and audio");
        }
    }

    private NebulaRow row(Context context, int icon, String titleRu, String titleEn,
                          String detailRu, String detailEn, Runnable open) {
        return new NebulaRow(context).icon(icon).title(NebulaText.text(titleRu, titleEn))
                .subtitle(NebulaText.text(detailRu, detailEn), false)
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> open.run());
    }

    private NebulaRow section(Context context, int icon, String titleRu, String titleEn, int id) {
        return new NebulaRow(context).icon(icon).title(NebulaText.text(titleRu, titleEn))
                .trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> presentFragment(new NebulaSectionFragment(id)));
    }
}
