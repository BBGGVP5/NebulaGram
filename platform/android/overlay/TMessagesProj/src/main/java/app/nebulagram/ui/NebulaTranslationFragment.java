package app.nebulagram.ui;

import android.content.Context;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.BaseFragment;

/** One place for the optional shortcut and independent incoming/draft translation. */
public final class NebulaTranslationFragment extends BaseFragment {
    private final long dialog;
    public NebulaTranslationFragment(int account, long dialog) { currentAccount = account; this.dialog = dialog; }
    private String t(String ru, String en) { return NebulaText.text(ru, en); }
    @Override public android.view.View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, t("ИИ в чате", "AI in chats"));
        LinearLayout column = NebulaFormUi.column(c);
        NebulaCard tools = new NebulaCard(c);
        NebulaRow engine = new NebulaRow(c).title(t("Движок живого перевода", "Live translation engine"))
            .subtitle(NebulaTranslationSettings.local() ? t("Быстрый · на устройстве", "Fast · on device") : t("Выбранный провайдер ИИ", "Selected AI provider"), true)
            .trailing(NebulaRow.TRAIL_CHEVRON);
        engine.withClick(v -> showDialog(new NebulaDialog.Builder(c, getResourceProvider()).setTitle(t("Движок живого перевода", "Live translation engine"))
            .setSelectedIndex(NebulaTranslationSettings.local() ? 0 : 1)
            .setItems(new CharSequence[]{t("Быстрый · на устройстве", "Fast · on device"), t("Выбранный провайдер ИИ", "Selected AI provider")}, (d, i) -> {
                NebulaTranslationSettings.global().edit().putBoolean("live_translation_local", i == 0).apply();
                engine.subtitle(i == 0 ? t("Быстрый · на устройстве", "Fast · on device") : t("Выбранный провайдер ИИ", "Selected AI provider"), true);
                if (dialog != 0) NebulaAutoTranslate.retry(currentAccount, dialog);
            }).setNegativeButton(t("Отмена", "Cancel"), null).create()));
        tools.add(engine);
        tools.add(new NebulaRow(c).title(t("Редактор Nebula AI", "Nebula AI editor"))
            .subtitle(t("Вместо кнопки Telegram · удержание открывает инструменты", "Replaces Telegram's button · hold for tools"), false)
            .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaAiReplacements.editor())
            .withClick(v -> NebulaAiOptions.prefs().edit().putBoolean("replace_editor", ((NebulaRow)v).toggleChecked()).apply()));
        tools.add(new NebulaRow(c).title(t("Инструменты в подписях", "Tools in captions"))
            .subtitle(t("Перевод и обработка подписи при отправке медиа", "Translate and process captions when sending media"), false)
            .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaTranslationSettings.shortcut())
            .withClick(v -> {
                // Snapshot the legacy opt-in before this key becomes caption-only.
                NebulaAiOptions.prefs().edit().putBoolean("replace_editor", NebulaAiReplacements.editor()).apply();
                NebulaTranslationSettings.global().edit().putBoolean("composer_shortcut", ((NebulaRow)v).toggleChecked()).apply();
            }));
        tools.add(new NebulaRow(c).title(t("Провайдер и модель", "Provider and model")).trailing(NebulaRow.TRAIL_CHEVRON)
            .withClick(v -> presentFragment(new NebulaAiServicesFragment())));
        column.addView(tools);
        if (NebulaTranslationSettings.global().getInt("provider", 0) == NebulaAiClient.NANO) {
            tools.add(new NebulaRow(c).title(t("Gemini Nano · модель и обновления", "Gemini Nano · model and updates"))
                .subtitle(t("Проверка, загрузка модели и обновление AICore", "Check, download model and update AICore"), false)
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> presentFragment(new NebulaAiFragment().forChat(currentAccount, dialog).openNanoConnection())));
        }
        if (dialog != 0 && !DialogObject.isEncryptedDialog(dialog)) {
            column.addView(NebulaCard.header(c, t("Перевод в этом чате", "Translation in this chat")));
            NebulaCard incoming = new NebulaCard(c);
            incoming.add(new NebulaRow(c).title(t("Входящие сообщения", "Incoming messages"))
                .subtitle(t("Переводить видимые сообщения выбранным движком", "Translate visible messages with the selected engine"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaTranslationSettings.prefs(currentAccount).getBoolean("on_" + dialog, false))
                .withClick(v -> {
                    NebulaRow row = (NebulaRow)v; boolean on = row.toggleChecked();
                    if (on && !ready(c)) { row.checked(false); return; }
                    if (!on) NebulaAutoTranslate.disable(currentAccount, dialog);
                    else {
                        NebulaTranslationSettings.prefs(currentAccount).edit().putBoolean("on_" + dialog, true).apply();
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogTranslate, dialog, true);
                    }
                }));
            incoming.add(new NebulaRow(c).title(t("Мои отправленные сообщения", "My sent messages"))
                .subtitle(t("Перевод в этом чате · оригинал сохраняется", "Translation in this chat · original is preserved"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaTranslationSettings.outgoing(currentAccount, dialog))
                .withClick(v -> {
                    NebulaRow row = (NebulaRow)v; boolean on = row.toggleChecked();
                    if (on && !ready(c)) { row.checked(false); return; }
                    NebulaAutoTranslate.setOutgoing(currentAccount, dialog, on);
                }));
            incoming.add(language(c, t("Язык сообщений", "Message language"), false));
            incoming.add(new NebulaRow(c).title(t("Повторить перевод", "Retry translation"))
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> NebulaAutoTranslate.retry(currentAccount, dialog))); column.addView(incoming);
            NebulaCard draft = new NebulaCard(c);
            draft.add(new NebulaRow(c).title(t("Перевод при наборе", "Translate while typing"))
                .subtitle(t("Автоматическая подстановка · оригинал сохраняется", "Automatic insertion · original is preserved"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaTranslationSettings.draft(currentAccount, dialog))
                .withClick(v -> {
                    NebulaRow row = (NebulaRow)v; boolean on = row.toggleChecked();
                    if (on && !ready(c)) { row.checked(false); return; }
                    NebulaTranslationSettings.prefs(currentAccount).edit().putBoolean("draft_" + dialog, on).apply();
                }));
            draft.add(language(c, t("Язык моего текста", "My text language"), true));
            draft.add(new NebulaRow(c).title(t("Подставлять перевод сразу", "Insert translation automatically"))
                .subtitle(t("Вернуть исходный текст кнопкой «Оригинал»", "Restore your text with Original"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaTranslationSettings.automatic(currentAccount, dialog))
                .withClick(v -> NebulaTranslationSettings.prefs(currentAccount).edit().putBoolean("draft_automatic_" + dialog, ((NebulaRow)v).toggleChecked()).apply()));
            NebulaRow delay = new NebulaRow(c).title(t("Пауза после ввода", "Pause after typing"))
                .subtitle(NebulaTranslationSettings.delay(currentAccount, dialog) + " ms", true).trailing(NebulaRow.TRAIL_CHEVRON);
            delay.withClick(v -> showDialog(new NebulaDialog.Builder(c, getResourceProvider()).setTitle(delayTitle())
                .setItems(new CharSequence[]{"0.15 s", "0.3 s", "0.5 s", "1 s", "2 s"}, (d, i) -> {
                    int value = new int[]{150, 300, 500, 1000, 2000}[i];
                    NebulaTranslationSettings.prefs(currentAccount).edit().putInt("delay_" + dialog, value).apply();
                    delay.subtitle(value + " ms", true);
                }).setNegativeButton(t("Отмена", "Cancel"), null).create()));
            draft.add(delay);
            LinearLayout.LayoutParams draftParams = new LinearLayout.LayoutParams(-1, -2);
            draftParams.topMargin = NebulaFormUi.dp(12); column.addView(draft, draftParams);
            column.addView(NebulaFormUi.note(c, t("Быстрый режим переводит на устройстве после подготовки языковых пакетов по Wi-Fi. Режим ИИ использует вашего провайдера. Автоподстановка сохраняет оригинал; её можно выключить и применять перевод вручную.",
                "Fast mode translates on device after language packs are prepared over Wi-Fi. AI mode uses your provider. Automatic insertion preserves the original; disable it to apply translations manually.")));
        } else {
            NebulaCard chats = new NebulaCard(c);
            chats.add(new NebulaRow(c).title(t("Перевод в реальном времени", "Real-time translation"))
                .subtitle(t("Выбрать чат · входящие и мой текст", "Choose chat · incoming and my text"), false)
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> NebulaPeerSelections.translation(this)));
            LinearLayout.LayoutParams chatParams = new LinearLayout.LayoutParams(-1, -2);
            chatParams.topMargin = NebulaFormUi.dp(12); column.addView(chats, chatParams);
        }
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, column));
    }
    private String delayTitle() { return t("Пауза после ввода", "Pause after typing"); }
    private boolean ready(Context c) {
        if (NebulaTranslationSettings.translationAvailable()) return true;
        Toast.makeText(c, t("Включите ИИ и настройте провайдера", "Enable AI and configure a provider"), Toast.LENGTH_LONG).show(); return false;
    }
    private NebulaRow language(Context c, String title, boolean draft) {
        String code = draft ? NebulaTranslationSettings.draftLanguage(currentAccount, dialog) : NebulaAutoTranslate.language(currentAccount, dialog);
        NebulaRow row = new NebulaRow(c).title(title).subtitle(NebulaTranslationSettings.label(code), true).trailing(NebulaRow.TRAIL_CHEVRON);
        row.withClick(v -> NebulaTranslationSettings.choose(this,
            draft ? NebulaTranslationSettings.draftLanguage(currentAccount, dialog) : NebulaAutoTranslate.language(currentAccount, dialog), value -> {
                if (draft) NebulaTranslationSettings.prefs(currentAccount).edit().putString("draft_language_" + dialog, value).apply();
                else NebulaAutoTranslate.setLanguage(currentAccount, dialog, value);
                row.subtitle(NebulaTranslationSettings.label(value), true);
            }));
        return row;
    }
}
