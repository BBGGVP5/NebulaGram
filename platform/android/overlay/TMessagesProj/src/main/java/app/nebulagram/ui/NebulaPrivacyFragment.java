package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;

/**
 * Конфиденциальность: защита чатов, удалённые и изменённые сообщения,
 * ограничения отправителя. Порядок — от того, что видно посторонним, к
 * локальным копиям.
 *
 * <p>Экран собран из тех же карточек и строк, что и остальные разделы
 * NebulaGram, а не из голого списка: разделы отделены шапками, у каждой строки
 * есть значок, пояснения стоят под карточкой мелким кеглем.
 */
public final class NebulaPrivacyFragment extends BaseFragment {
    private LinearLayout content;
    private long owner;
    private int focusIndex = -1;
    private String focusTitle;

    public NebulaPrivacyFragment focusRowIndex(int index) { focusIndex = Math.max(0, index); return this; }
    public NebulaPrivacyFragment focus(String title) { focusTitle = title; return this; }

    private String text(String ru, String en) { return NebulaText.text(ru, en); }

    @Override public View createView(Context context) {
        NebulaTheme theme = NebulaTheme.of(context);
        owner = NebulaDeletedArchive.owner(currentAccount);
        actionBar.setTitle(text("Конфиденциальность", "Privacy"));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setBackgroundColor(theme.surface());
        actionBar.setTitleColor(theme.onSurface());
        actionBar.setItemsColor(theme.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                AndroidUtilities.dp(16), AndroidUtilities.dp(28));
        scroll.setBackgroundColor(theme.surface());
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fragmentView = scroll;
        rebuild();
        if (focusIndex >= 0) scroll.post(() -> focusRowIndex(content, new int[]{0}, focusIndex));
        else if (focusTitle != null) scroll.post(() -> focusTitle(content));
        return fragmentView = NebulaSettingsLayout.wrap(context, actionBar, scroll, -15);
    }

    @Override public boolean isLightStatusBar() {
        return !NebulaTheme.of(getContext()).isDark();
    }

    private NebulaRow row(int icon, String title) {
        return new NebulaRow(content.getContext()).icon(icon).title(title);
    }

    private void header(String title) {
        content.addView(NebulaCard.header(content.getContext(), title));
    }

    private void card(View... rows) {
        NebulaCard card = new NebulaCard(content.getContext());
        for (View row : rows) card.add(row);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(6);
        content.addView(card, params);
    }

    private void focusRowIndex(View view, int[] index, int target) {
        if (view instanceof NebulaRow) {
            if (index[0]++ == target) focusView(view);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount() && index[0] <= target; i++) {
                focusRowIndex(group.getChildAt(i), index, target);
            }
        }
    }

    private void focusTitle(View view) {
        if (view instanceof android.widget.TextView && focusTitle.contentEquals(((android.widget.TextView) view).getText())) {
            focusView(view);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) focusTitle(group.getChildAt(i));
        }
    }

    private void focusView(View view) {
        android.graphics.Rect rect = new android.graphics.Rect();
        view.getDrawingRect(rect);
        content.offsetDescendantRectToMyCoords(view, rect);
        View row = view;
        while (!(row instanceof NebulaRow) && row.getParent() instanceof View) row = (View) row.getParent();
        if (row instanceof NebulaRow) ((NebulaRow) row).highlight();
        if (content.getParent() instanceof ScrollView) {
            ((ScrollView) content.getParent()).smoothScrollTo(0, Math.max(0, rect.top - AndroidUtilities.dp(32)));
        }
    }

    /** Пояснение под карточкой: мельче и тише текста строк, как в Material 3. */
    private void note(String text) {
        Context context = content.getContext();
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(13);
        label.setLineSpacing(AndroidUtilities.dp(2), 1f);
        label.setTextColor(NebulaTheme.of(context).onSurfaceVariant());
        label.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10),
                AndroidUtilities.dp(16), AndroidUtilities.dp(4));
        content.addView(label);
    }

    private void rebuild() {
        if (content == null) return;
        content.removeAllViews();
        final boolean retention = NebulaDeletedArchive.enabled(owner);
        NebulaSettingsHero hero = new NebulaSettingsHero(content.getContext(), R.drawable.msg_secret,
                text("Конфиденциальность", "Privacy"),
                text("Защита чатов от посторонних глаз и локальные копии сообщений. Всё хранится только на этом устройстве.",
                        "Keep chats away from prying eyes and keep local copies of messages. Everything stays on this device."));
        hero.setStatus(retention ? text("Удалённые сохраняются", "Deleted messages are kept")
                : text("Удалённые не сохраняются", "Deleted messages are not kept"), retention);
        content.addView(hero);

        // 1. Who can see what is on the screen.
        header(text("Защита чатов", "Chat protection"));
        card(row(R.drawable.nebula_settings_chat_lock, text("Пароли чатов", "Chat passwords"))
                .subtitle(text("Отдельный пароль или биометрия для выбранных чатов", "A separate passcode or biometrics for chosen chats"), false)
                .trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> presentFragment(new NebulaLockedChatsFragment())));
        NebulaFeatureControls.privacy(this, content);

        // 2. Copies kept on this device.
        header(text("Удалённые сообщения", "Deleted messages"));
        NebulaRow master = row(R.drawable.msg_delete, text("Сохранять удалённые сообщения", "Save deleted messages"))
                .subtitle(text("Остаются на своём месте в чате с отметкой", "They stay in place in the chat with a marker"), false)
                .trailing(NebulaRow.TRAIL_SWITCH)
                .checked(retention)
                .withClick(v -> {
                    if (NebulaDeletedArchive.enabled(owner)) { NebulaDeletedArchive.setEnabled(owner, false); rebuild(); return; }
                    showDialog(new NebulaDialog.Builder(getParentActivity())
                            .setTitle(text("Включить локальный архив?", "Enable local archive?"))
                            .setMessage(text("Полученные сообщения останутся на своём месте в чате с отметкой удаления. Их содержимое и вложения остаются в обычном локальном кэше Telegram; на сервер ничего не отправляется. Секретные и исчезающие сообщения требуют отдельных переключателей. Защита от копирования сохраняется.", "Received messages stay in place with a deletion marker. Content and attachments remain in Telegram’s normal local cache; nothing is sent to the server. Secret and expiring messages require separate switches. Copy protection is respected."))
                            .setNegativeButton(text("Отмена", "Cancel"), null)
                            .setPositiveButton(text("Включить", "Enable"), (d, w) -> { NebulaDeletedArchive.setEnabled(owner, true); rebuild(); })
                            .create());
                });
        if (retention) {
            card(master, extraToggle(true), extraToggle(false));
            note(text("Без лимита сообщений и срока хранения. Выключение не очищает прежние копии.",
                    "No message or time limit. Turning this off keeps existing copies."));

            header(text("Где сохранять", "Where to save"));
            card(scopeToggle(NebulaDeletedStyle.PRIVATE), scopeToggle(NebulaDeletedStyle.GROUPS),
                    scopeToggle(NebulaDeletedStyle.CHANNELS), scopeToggle(NebulaDeletedStyle.BOTS),
                    scopeToggle(NebulaDeletedStyle.SAVED));
            note(NebulaDeletedStyle.scopeCount() == 0
                    ? text("Не выбрано ничего — сохранять будет нечего.",
                            "Nothing is selected, so nothing will be kept.")
                    : text("В «Избранном» сохраняются сообщения, удалённые на другом устройстве, если они уже загружены сюда. Удаление в этом приложении работает как обычно.",
                            "Saved Messages deleted on another device are kept if already loaded here. Deleting in this app works as usual."));

            header(text("Как выглядят в чате", "How they look in the chat"));
            card(row(R.drawable.msg_emoji_smiles, text("Значок удалённого сообщения", "Deleted message icon"))
                            .subtitle(NebulaDeletedArchive.icon(), true)
                            .trailing(NebulaRow.TRAIL_CHEVRON)
                            .withClick(v -> icons()),
                    row(R.drawable.msg_palette, text("Как выделять", "How to mark"))
                            .subtitle(NebulaDeletedStyle.markTitle(), true)
                            .trailing(NebulaRow.TRAIL_CHEVRON)
                            .withClick(v -> marks()),
                    colourRow());
            note(text("Значок показывается в чате вместо слова «Удалено».",
                    "The icon replaces the word Deleted in the chat."));
        } else {
            card(master);
            note(text("Включите, чтобы сообщение, которое удалил собеседник, оставалось в чате с отметкой. Настройки появятся после включения.",
                    "Turn on to keep a message the other person deleted in the chat with a marker. Its options appear once it is on."));
        }
        card(row(R.drawable.msg_info, text("Как работает сохранение", "How retention works"))
                        .subtitle(text("Медиа, фон и ограничения", "Media, background and limitations"), false)
                        .trailing(NebulaRow.TRAIL_CHEVRON)
                        .withClick(v -> showDialog(new NebulaDialog.Builder(getParentActivity())
                                .setTitle(text("Как работает сохранение", "How retention works"))
                                .setMessage(text("Архив хранится столько, сколько нужно вам: ни по числу сообщений, ни по сроку он не ограничен и очищается только вручную. Текст и уже полученные вложения. Медиа может удаляться стандартной очисткой кэша; недоступные файлы восстановить нельзя. Фоновое сохранение возможно лишь при получении приложением события удаления. Выключение не удаляет существующий архив. Клиент собеседника не определяется.",
                        "The archive is kept for as long as you want it: no message-count or time limit, and it is cleared only by hand. Text and received attachments. Standard cache eviction can remove media; unavailable files cannot be recovered. Background capture requires the app to receive the deletion update. Disabling does not erase existing entries. Peer clients are not detected."))
                                .setPositiveButton(text("Понятно", "Got it"), null).create())),
                row(R.drawable.msg_clearcache, text("Очистить сохранённые копии", "Clear retained copies"))
                        .destructive()
                        .withClick(v -> confirmClear(this, currentAccount, 0, this::rebuild)));
        if (NebulaDeletedArchive.hasError(owner)) {
            note(text("Часть сообщений не удалось сохранить. Архив не сброшен.",
                    "Some messages could not be saved. The archive was not reset."));
        }

        // 3. Edited messages: the earlier versions kept locally.
        header(text("Изменённые сообщения", "Edited messages"));
        card(row(R.drawable.msg_edit, text("История правок", "Edit history"))
                .subtitle(text("Предыдущие версии сообщений на устройстве", "Previous message versions on device"), false)
                .trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> presentFragment(new NebulaEditHistoryFragment(currentAccount, null))));

        // 4. What this client lets you do with protected content.
        header(text("Ограничения отправителя", "Sender restrictions"));
        card(row(R.drawable.msg_forward, text("Разрешить снимки и пересылку", "Allow screenshots and forwarding"))
                .subtitle(text("В чатах, где включена защита содержимого", "In chats with content protection on"), false)
                .trailing(NebulaRow.TRAIL_SWITCH)
                .checked(NebulaContentProtection.enabled())
                .withClick(v -> {
                    if (NebulaContentProtection.enabled()) { NebulaContentProtection.setEnabled(false); rebuild(); return; }
                    showDialog(new NebulaDialog.Builder(getParentActivity())
                            .setTitle(text("Снять ограничения отправителя?", "Lift sender restrictions?"))
                            .setMessage(text("Снимки экрана, копирование и сохранение доступны для уже полученного содержимого. Из защищённых чатов отправляются обычные копии: с текстовой подписью источника либо без неё, без серверной пометки «Переслано от». Для фото и файлов нужна полная загрузка на устройство. Секретные и исчезающие вложения не поддерживаются. Уважайте приватность собеседника.", "Screenshots, copying and saving apply to content already received. Protected chats send ordinary copies, with an optional source label, not Telegram forwarded headers. Photos and files must be downloaded completely. Secret and disappearing attachments are not supported. Respect the sender's privacy."))
                            .setNegativeButton(text("Отмена", "Cancel"), null)
                            .setPositiveButton(text("Снять", "Lift"), (d, w) -> { NebulaContentProtection.setEnabled(true); rebuild(); })
                            .create());
                }));
        note(text("Выключено по умолчанию. Настройка меняет только этот клиент и только на этом устройстве.",
                "Off by default. The setting changes this client on this device and nothing else."));
    }

    /** Один вид собеседника: сохранять из него удалённое или нет. */
    private NebulaRow scopeToggle(int kind) {
        final int icon;
        switch (kind) {
            case NebulaDeletedStyle.SAVED: icon = R.drawable.msg_saved; break;
            case NebulaDeletedStyle.GROUPS: icon = R.drawable.msg_groups; break;
            case NebulaDeletedStyle.CHANNELS: icon = R.drawable.msg_channel; break;
            case NebulaDeletedStyle.BOTS: icon = R.drawable.msg_bot; break;
            default: icon = R.drawable.msg_contacts; break;
        }
        return row(icon, NebulaDeletedStyle.scopeTitle(kind))
                .trailing(NebulaRow.TRAIL_SWITCH)
                .checked(NebulaDeletedStyle.scope(kind))
                .withClick(v -> {
                    NebulaDeletedStyle.setScope(kind, !NebulaDeletedStyle.scope(kind));
                    rebuild();
                });
    }

    private void marks() {
        if (getParentActivity() == null) return;
        String[] titles = {
                text("Не выделять", "Do not mark"),
                text("Приглушением", "By fading"),
                text("Цветом", "With colour"),
        };
        showDialog(new NebulaDialog.Builder(getParentActivity())
                .setTitle(text("Как выделять удалённые", "How to mark deleted messages"))
                .setSelectedIndex(NebulaDeletedStyle.mark()).setItems(titles, (d, which) -> { NebulaDeletedStyle.setMark(which); rebuild(); })
                .create());
    }

    /** Цвет подложки нужен только тогда, когда подложка вообще рисуется. */
    private NebulaRow colourRow() {
        NebulaRow row = row(R.drawable.msg_colors, text("Цвет выделения", "Marker colour"))
                .subtitle(colourName(NebulaDeletedStyle.colourIndex()), true)
                .trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> colours());
        if (NebulaDeletedStyle.mark() != NebulaDeletedStyle.MARK_TINT) {
            row.setAlpha(.45f);
            row.setEnabled(false);
        }
        return row;
    }

    private String colourName(int index) {
        String[] names = {
                text("Как в теме", "Theme accent"), text("Красный", "Red"), text("Оранжевый", "Orange"),
                text("Жёлтый", "Yellow"), text("Зелёный", "Green"), text("Синий", "Blue"),
                text("Фиолетовый", "Purple"), text("Розовый", "Pink"),
        };
        return index >= 0 && index < names.length ? names[index] : names[0];
    }

    private void colours() {
        if (getParentActivity() == null) return;
        String[] titles = new String[NebulaDeletedStyle.PALETTE.length];
        for (int i = 0; i < titles.length; i++) titles[i] = colourName(i);
        showDialog(new NebulaDialog.Builder(getParentActivity())
                .setTitle(text("Цвет выделения", "Marker colour"))
                .setSelectedIndex(NebulaDeletedStyle.colourIndex()).setItems(titles, (d, which) -> { NebulaDeletedStyle.setColourIndex(which); rebuild(); })
                .create());
    }

    private NebulaRow extraToggle(boolean secret) {
        boolean selected = secret ? NebulaDeletedArchive.saveSecret(owner) : NebulaDeletedArchive.saveExpiring(owner);
        return row(secret ? R.drawable.msg_secret : R.drawable.msg_autodelete,
                secret ? text("Сохранять в секретных чатах", "Retain in secret chats")
                       : text("Сохранять исчезающие сообщения", "Retain expiring messages"))
                .trailing(NebulaRow.TRAIL_SWITCH).checked(selected).withClick(v -> {
            if (selected) { NebulaDeletedArchive.setExtra(owner, secret, false); rebuild(); return; }
            showDialog(new NebulaDialog.Builder(getParentActivity())
                    .setTitle(text("Оставлять локальную копию?", "Keep a local copy?"))
                    .setMessage(text("При включённом сохранении копия останется после удаления или таймера. Это меняет ожидаемое поведение секретных и исчезающих сообщений. Работает только с уже полученным содержимым на этом устройстве.", "When retention is enabled, a local copy remains after deletion or expiry. This changes the expected behavior of secret and expiring messages. Only content already received on this device can be kept."))
                    .setNegativeButton(text("Отмена", "Cancel"), null)
                    .setPositiveButton(text("Включить", "Enable"), (d, w) -> { NebulaDeletedArchive.setEnabled(owner, true); NebulaDeletedArchive.setExtra(owner, secret, true); rebuild(); })
                    .create());
        });
    }

    public static void confirmClear(BaseFragment fragment, int account, long peer, Runnable done) {
        if (fragment.getParentActivity() == null) return;
        fragment.showDialog(new NebulaDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider())
            .setTitle(NebulaText.text(peer == 0 ? "Очистить все сохранённые удалённые сообщения?" : "Очистить удалённые сообщения этого чата?", peer == 0 ? "Clear all retained messages?" : "Clear retained messages in this chat?"))
            .setMessage(NebulaText.text("Только локальные сохранённые копии. Обычная переписка и общий медиакэш не удаляются.", "Only locally retained copies. Ordinary history and shared media cache are not deleted."))
            .setNegativeButton(NebulaText.text("Отмена", "Cancel"), null)
            .setPositiveButton(NebulaText.text("Очистить", "Clear"), (d, w) -> NebulaDeletedArchive.clearAsync(account, peer, () -> { if (done != null) done.run(); }, () -> {
                if (fragment.getParentActivity() != null) fragment.showDialog(new NebulaDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider()).setMessage(NebulaText.text("Не удалось очистить сохранённые сообщения.", "Could not clear retained messages.")).setPositiveButton("OK", null).create());
            })).create());
    }

    private void icons() {
        int selectedIcon = java.util.Arrays.asList("🗑", "✕", "◌").indexOf(NebulaDeletedArchive.icon());
        showDialog(new NebulaDialog.Builder(getParentActivity()).setTitle(text("Значок удалённого сообщения", "Deleted message icon"))
            .setSelectedIndex(selectedIcon < 0 ? 3 : selectedIcon)
            .setItems(new CharSequence[]{"🗑", "✕", "◌", text("Свой символ / эмодзи", "Custom symbol / emoji")}, (dialog, which) -> {
                if (which < 3) { NebulaDeletedArchive.setIcon(new String[]{"🗑", "✕", "◌"}[which]); rebuild(); return; }
                EditText input = new EditText(getParentActivity());
                input.setSingleLine(true);
                input.setText(NebulaDeletedArchive.icon());
                input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});
                showDialog(new NebulaDialog.Builder(getParentActivity()).setTitle(text("Свой значок", "Custom icon"))
                    .setMessage(text("До четырёх символов или эмодзи. Значок показывается вместо слова «Удалено».", "Up to four symbols or emoji. The icon replaces the word Deleted."))
                    .setView(input)
                    .setNegativeButton(text("Отмена", "Cancel"), null)
                    .setPositiveButton(text("Сохранить", "Save"), (d, w) -> { NebulaDeletedArchive.setIcon(input.getText().toString()); rebuild(); }).create());
            }).create());
    }

    @Override public void onFragmentDestroy() { NebulaDeleteAuthentication.cancel(this); content = null; super.onFragmentDestroy(); }
}
