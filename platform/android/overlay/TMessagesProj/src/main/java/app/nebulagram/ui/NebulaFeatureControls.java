package app.nebulagram.ui;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.DialogsActivity;

/** Operational controls are appended to existing pages, without an extra category screen. */
public final class NebulaFeatureControls {
    private NebulaFeatureControls() { }
    private static String text(String ru, String en) { return NebulaText.text(ru, en); }
    private static NebulaRow toggle(LinearLayout parent, String key, String ru, String en, String infoRu, String infoEn) {
        NebulaRow row = new NebulaRow(parent.getContext()).title(text(ru, en))
                .subtitle(text(infoRu, infoEn), false).trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaFeatureSettings.enabled(key));
        row.withClick(v -> NebulaFeatureSettings.set(key, row.toggleChecked()));
        return row;
    }
    private static void add(LinearLayout parent, NebulaCard card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(12);
        parent.addView(card, params);
    }
    public static void general(BaseFragment host, LinearLayout parent) {
        NebulaCard card = new NebulaCard(parent.getContext());
        card.add(toggle(parent, "smooth_fade", "Плавное затухание", "Smooth fading", "Плавнее менять прозрачность при переходах", "Smooth opacity during navigation transitions"));
        if (android.os.Build.VERSION.SDK_INT >= 34) card.add(toggle(parent, "predictive_back", "Predictive Back", "Predictive Back", "Предпросмотр возврата системным жестом", "Preview navigation with the system back gesture"));
        add(parent, card);
        parent.addView(NebulaCard.header(parent.getContext(), text("Уведомления", "Notifications")));
        card = new NebulaCard(parent.getContext());
        card.add(toggle(parent, "mute_non_contacts", "Приглушить не-контакты", "Silence non-contacts", "Сообщения незнакомых людей приходят без звука и вибрации", "Messages from unknown people arrive without sound or vibration"));
        card.add(toggle(parent, "ignore_mentions", "Игнорировать упоминания", "Ignore mentions", "Не уведомлять об упоминаниях и скрывать их счётчик", "Skip mention notifications and hide their counter"));
        NebulaPeerSelections.mentionRow(host, card);
        add(parent, card);
        parent.addView(NebulaCard.header(parent.getContext(), text("Архивация историй", "Story archiving")));
        card = new NebulaCard(parent.getContext());
        card.add(toggle(parent, "auto_archive_stories", "Архивировать новые истории", "Archive new stories", "Истории перемещаются в архив. Изменения синхронизируются через Telegram", "Moves stories to the archive. Changes sync through Telegram"));
        card.add(toggle(parent, "archive_user_stories", "Истории пользователей", "User stories", "Применять автоматическую архивацию к людям", "Automatically archive people's stories"));
        card.add(toggle(parent, "archive_channel_stories", "Истории каналов", "Channel stories", "Применять автоматическую архивацию к каналам", "Automatically archive channel stories"));
        add(parent, card);
        card = new NebulaCard(parent.getContext());
        card.add(new NebulaRow(parent.getContext()).icon(R.drawable.msg_settings).title(text("Память приложения", "App memory"))
                .subtitle(text("Экспериментальный экран состояния памяти", "Experimental memory status"), false)
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> host.presentFragment(new NebulaMemoryFragment())));
        add(parent, card);
    }
    public static void appearance(LinearLayout parent) {
        NebulaCard card = new NebulaCard(parent.getContext());
        card.add(toggle(parent, "snowflakes", "Снежинки", "Snowflakes", "В списке чатов и переписках; отключаются при экономии энергии", "In the chat list and conversations; pauses in power-saving mode"));
        add(parent, card);
    }
    public static void chats(BaseFragment host, LinearLayout parent) {
        NebulaCard card = new NebulaCard(parent.getContext());
        card.add(toggle(parent, "custom_chat_wallpaper", "Фон отдельных чатов", "Per-chat wallpaper", "Показывать обои, установленные для отдельных переписок", "Show wallpaper set for individual conversations"));
        card.add(toggle(parent, "quote_full_reply", "Цитировать ответы", "Quote replies", "Цитировать текст сообщения в пределах лимита Telegram. Не работает в топиках", "Quote the message text up to Telegram's limit. Unavailable in topics"));
        card.add(toggle(parent, "disable_chat_vibration", "Отключить вибрацию чатов", "Disable chat haptics", "Отключает отклик жестов и действий в переписке", "Turns off gesture and action feedback in conversations"));
        NebulaPeerSelections.savedRow(host, card);
        add(parent, card);
    }
    public static void privacy(BaseFragment host, LinearLayout parent) {
        NebulaCard card = new NebulaCard(parent.getContext());
        if (android.os.Build.VERSION.SDK_INT >= 29) card.add(toggle(parent, "biometric_delete", "Подтверждать удаление", "Authenticate before deletion", "Системная биометрия или пароль перед удалением чата и очисткой истории", "System biometrics or passcode before deleting a chat or clearing history"));
        card.add(toggle(parent, "hide_archive", "Скрыть архив", "Hide archive", "Скрывает строку архива, сохраняя все чаты внутри", "Hides the archive row while keeping its chats"));
        card.add(new NebulaRow(parent.getContext()).icon(R.drawable.files_folder).title(text("Открыть архив", "Open archive"))
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> {
                    Bundle args = new Bundle(); args.putInt("folderId", 1);
                    DialogsActivity archive = new DialogsActivity(args); archive.setCurrentAccount(host.getCurrentAccount()); host.presentFragment(archive);
                }));
        add(parent, card);
    }
}
