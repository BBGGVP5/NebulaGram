package app.nebulagram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
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
        params.topMargin = NebulaShape.gap(12);
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
        for (int account : org.telegram.messenger.UserConfig.getAccountIds()) {
            if (!org.telegram.messenger.UserConfig.getInstance(account).isClientActivated()) continue;
            final int selectedAccount = account;
            org.telegram.tgnet.TLRPC.User user = org.telegram.messenger.UserConfig.getInstance(account).getCurrentUser();
            String name = user == null ? Integer.toString(account + 1) : org.telegram.messenger.UserObject.getUserName(user);
            card.add(new NebulaRow(parent.getContext()).title(text("Уведомления · ", "Notifications · ") + name)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaChatPreferences.notifications(account)).withClick(v -> {
                    boolean value = ((NebulaRow)v).toggleChecked(); NebulaChatPreferences.notifications(selectedAccount, value);
                    if (value) org.telegram.messenger.NotificationsController.getInstance(selectedAccount).showNotifications();
                    else org.telegram.messenger.NotificationsController.getInstance(selectedAccount).hideNotifications();
                }));
        }
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
        Context context = parent.getContext();
        NebulaCard motion = new NebulaCard(context);
        NebulaRow style = new NebulaRow(context).title(text("Анимация", "Animation"))
                .subtitle(NebulaMotion.title(), true).trailing(NebulaRow.TRAIL_CHEVRON);
        String[] names = {text("Стандарт", "Standard"), "Expressive"};
        style.withClick(v -> new NebulaDialog.Builder(context).setTitle(text("Анимация", "Animation"))
                .setSelectedIndex(NebulaMotion.expressive() ? 1 : 0)
                .setItems(names, (dialog, which) -> {
                    NebulaMotion.setExpressive(which == 1);
                    style.subtitle(NebulaMotion.title(), true);
                }).show());
        motion.add(style);
        add(parent, motion);
        parent.addView(NebulaFormUi.note(context, text(
                "Expressive — лёгкий перелёт и возврат, как сейчас. Стандарт — то же время и те же границы, но затухание без перелёта.",
                "Expressive overshoots slightly and settles back, as it does today. Standard keeps the same duration and bounds, but eases out without the overshoot.")));
        NebulaCard list = new NebulaCard(context);
        NebulaRow cards = new NebulaRow(context).title(text("Список чатов", "Chat list"))
                .subtitle(text("Каждый чат своей карточкой", "Each chat on its own card"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaChatCards.enabled());
        cards.withClick(v -> {
            NebulaChatCards.setEnabled(cards.toggleChecked());
            org.telegram.messenger.NotificationCenter.getGlobalInstance()
                    .postNotificationName(org.telegram.messenger.NotificationCenter.mainUserInfoChanged);
        });
        list.add(cards);
        NebulaRow tabs = new NebulaRow(context).title(text("Вкладки профиля", "Profile tabs"))
                .subtitle(text("Отдельные кнопки вместо общей полосы", "Separate buttons instead of one strip"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaTabButtons.enabled());
        tabs.withClick(v -> NebulaTabButtons.setEnabled(tabs.toggleChecked()));
        list.add(tabs);
        add(parent, list);
        NebulaCard card = new NebulaCard(context);
        card.add(toggle(parent, "snowflakes", "Снежинки", "Snowflakes", "В списке чатов и переписках; отключаются при экономии энергии", "In the chat list and conversations; pauses in power-saving mode"));
        add(parent, card);
    }
    public static void chats(BaseFragment host, LinearLayout parent) {
        NebulaCard card = new NebulaCard(parent.getContext());
        card.add(toggle(parent, "custom_chat_wallpaper", "Фон отдельных чатов", "Per-chat wallpaper", "Показывать обои, установленные для отдельных переписок", "Show wallpaper set for individual conversations"));
        card.add(toggle(parent, "quote_full_reply", "Цитировать ответы", "Quote replies", "Цитировать текст сообщения в пределах лимита Telegram. Не работает в топиках", "Quote the message text up to Telegram's limit. Unavailable in topics"));
        card.add(toggle(parent, "selection_without_author", "Кнопка «Без авторства»", "Without-author button", "Вместо «Ответить» при выделении сообщений. По умолчанию — стандартные кнопки Telegram", "Replace Reply when selecting messages. Standard Telegram buttons by default"));
        card.add(toggle(parent, "disable_chat_vibration", "Отключить вибрацию чатов", "Disable chat haptics", "Отключает отклик жестов и действий в переписке", "Turns off gesture and action feedback in conversations"));
        card.add(new NebulaRow(parent.getContext()).title(text("Вычисления при наборе", "Inline arithmetic"))
            .subtitle(text("Показывать результат рядом с выражением, сохраняя текст", "Show the result beside an expression, keeping your text"), false)
            .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaChatPreferences.math()).withClick(v -> NebulaChatPreferences.set("inline_math", ((NebulaRow)v).toggleChecked())));
        card.add(new NebulaRow(parent.getContext()).title(text("Количество пересылок", "Forward count"))
            .subtitle(text("Рядом с просмотрами постов каналов", "Beside channel post views"), false)
            .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaChatPreferences.forwardCount()).withClick(v -> NebulaChatPreferences.set("channel_forward_count", ((NebulaRow)v).toggleChecked())));
        card.add(new NebulaRow(parent.getContext()).title(text("Счётчики только со звуком", "Unmuted folder counters"))
            .subtitle(text("Не учитывать чаты без звука в числах папок; состав папок сохраняется", "Exclude muted chats from folder counts, keeping folder membership"), false)
            .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaChatPreferences.unmuted()).withClick(v -> {
                NebulaChatPreferences.set("folder_unmuted_only", ((NebulaRow)v).toggleChecked());
                for (int a : org.telegram.messenger.UserConfig.getAccountIds())
                    if (org.telegram.messenger.UserConfig.getInstance(a).isClientActivated()) org.telegram.messenger.MessagesStorage.getInstance(a).nebulaRecountFilters();
            }));
        String[] styles = {text("Обычно", "Default"), text("Сбоку", "Beside sticker"), text("Скрыто", "Hidden")};
        NebulaRow time = new NebulaRow(parent.getContext()).title(text("Время у стикеров", "Sticker time")).subtitle(styles[NebulaChatPreferences.stickerTime()], false).trailing(NebulaRow.TRAIL_CHEVRON);
        time.withClick(v -> host.showDialog(new NebulaDialog.Builder(parent.getContext()).setTitle(text("Время у стикеров", "Sticker time"))
            .setSelectedIndex(NebulaChatPreferences.stickerTime()).setItems(styles, (d, which) -> { NebulaChatPreferences.stickerTime(which); time.subtitle(styles[which], false); }).create())); card.add(time);
        card.add(new NebulaRow(parent.getContext()).title(text("Действия свайпа", "Swipe actions"))
            .subtitle(text("Ответ, копирование, перевод и инструменты; настройте порядок", "Reply, copy, translation and tools; set their order"), false)
            .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> host.presentFragment(new NebulaSwipeSettingsFragment())));
        NebulaPeerSelections.savedRow(host, card);
        add(parent, card);
        stickerSize(parent);
    }

    /** Размеры медиа — с превью: по одному проценту трудно представить итог. */
    private static void stickerSize(LinearLayout parent) {
        Context context = parent.getContext();
        parent.addView(NebulaCard.header(context, text("Размер стикеров, эмодзи и GIF", "Sticker, emoji and GIF size")));
        NebulaCard card = new NebulaCard(context);
        NebulaMediaSizePreview preview = new NebulaMediaSizePreview(context);
        card.add(preview);
        card.add(sizeSlider(context, preview, text("Стикеры", "Stickers"),
                NebulaMediaSize.stickerPercent(), NebulaMediaSize::setStickerPercent));
        card.add(sizeSlider(context, preview, text("Анимированные эмодзи", "Animated emoji"),
                NebulaMediaSize.emojiPercent(), NebulaMediaSize::setEmojiPercent));
        card.add(sizeSlider(context, preview, "GIF",
                NebulaMediaSize.gifPercent(), NebulaMediaSize::setGifPercent));
        add(parent, card);
        parent.addView(NebulaFormUi.note(context, text(
                "Множится доля экрана, а не пиксели, поэтому размер одинаков на телефоне и планшете. У GIF свой ползунок, но Telegram не даёт им стать меньше его собственного предела: иначе поверх гифки не помещаются время и проценты загрузки.",
                "This scales the share of the screen rather than pixels, so sizes match on a phone and a tablet. GIFs have their own slider, but Telegram keeps a floor under them: below it the time and loading percentage no longer fit on top.")));
    }

    private interface Percent { void set(int value); }

    private static View sizeSlider(Context context, View preview, String title, int value, Percent apply) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        android.widget.TextView label = new android.widget.TextView(context);
        label.setText(title);
        label.setTextSize(15);
        label.setTextColor(NebulaTheme.of(context).onSurface());
        label.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), 0);
        column.addView(label);
        android.widget.SeekBar slider = new android.widget.SeekBar(context);
        slider.setMax(NebulaMediaSize.maximum() - NebulaMediaSize.minimum());
        slider.setProgress(value - NebulaMediaSize.minimum());
        slider.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(4),
                AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser) return;
                apply.set(NebulaMediaSize.minimum() + progress);
                preview.invalidate();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar bar) { }
            @Override public void onStopTrackingTouch(android.widget.SeekBar bar) { }
        });
        column.addView(slider);
        return column;
    }
    public static void privacy(BaseFragment host, LinearLayout parent) {
        NebulaCard card = new NebulaCard(parent.getContext());
        NebulaRow mask = new NebulaRow(parent.getContext()).title(text("Скрыть строки чатов", "Hide chat rows"))
                .subtitle(text("Имена, фото и превью в списке чатов", "Names, photos and previews in chat rows"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaPresentationPrivacy.enabled());
        mask.withClick(v -> NebulaPresentationPrivacy.setEnabled(mask.toggleChecked()));
        card.add(mask);
        add(parent, card);
        parent.addView(NebulaFormUi.note(parent.getContext(), text("Скрывает строки главного списка, архива и папок, истории в шапке и имена в экране «Аккаунты» NebulaGram. Открытые переписки, уведомления, другие профили и системные экраны не скрываются. Это оформление, а не блокировка доступа.", "Masks the main list, archive and folder rows, header stories and names in NebulaGram's Accounts screen. Open conversations, notifications, other profiles and system screens are not masked. This changes presentation, not access permissions.")));
        card = new NebulaCard(parent.getContext());
        if (android.os.Build.VERSION.SDK_INT >= 29) card.add(toggle(parent, "biometric_delete", "Подтверждать удаление", "Authenticate before deletion", "Системная биометрия или пароль перед удалением чата и очисткой истории", "System biometrics or passcode before deleting a chat or clearing history"));
        if (android.os.Build.VERSION.SDK_INT >= 30) card.add(NebulaMediaControls.toggle(parent.getContext(), "prefer_device_pin", false, "Предпочитать системный PIN или пароль", "Prefer device PIN or passcode"));
        if (android.os.Build.VERSION.SDK_INT >= 29) card.add(new NebulaRow(parent.getContext()).title(text("Проверить защиту", "Test authentication"))
                .subtitle(text("Проверка без удаления данных", "Test without deleting data"), false).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> NebulaDeleteAuthentication.test(host)));
        card.add(toggle(parent, "hide_archive", "Скрыть архив", "Hide archive", "Скрывает строку архива, сохраняя все чаты внутри", "Hides the archive row while keeping its chats"));
        card.add(new NebulaRow(parent.getContext()).icon(R.drawable.files_folder).title(text("Открыть архив", "Open archive"))
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> {
                    Bundle args = new Bundle(); args.putInt("folderId", 1);
                    DialogsActivity archive = new DialogsActivity(args); archive.setCurrentAccount(host.getCurrentAccount()); host.presentFragment(archive);
                }));
        add(parent, card);
    }
}
