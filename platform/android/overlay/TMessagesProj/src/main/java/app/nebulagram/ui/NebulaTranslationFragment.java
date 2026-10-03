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
        tools.add(new NebulaRow(c).title(t("Кнопка ИИ в поле ввода", "AI button in composer"))
            .subtitle(t("Инструменты справа · удержание открывает настройки", "Tools on the right · hold for settings"), false)
            .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaTranslationSettings.shortcut())
            .withClick(v -> NebulaTranslationSettings.global().edit().putBoolean("composer_shortcut", ((NebulaRow)v).toggleChecked()).apply()));
        tools.add(new NebulaRow(c).title(t("Провайдер и модель", "Provider and model")).trailing(NebulaRow.TRAIL_CHEVRON)
            .withClick(v -> presentFragment(new NebulaAiFragment().forChat(currentAccount, dialog).openConnection())));
        column.addView(tools);
        if (NebulaTranslationSettings.global().getInt("provider", 0) == NebulaAiClient.NANO) {
            tools.add(new NebulaRow(c).title(t("Gemini Nano · модель и обновления", "Gemini Nano · model and updates"))
                .subtitle(t("Проверка, загрузка модели и обновление AICore", "Check, download model and update AICore"), false)
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> presentFragment(new NebulaAiFragment().forChat(currentAccount, dialog).openConnection())));
        }
        if (dialog != 0 && !DialogObject.isEncryptedDialog(dialog)) {
            column.addView(NebulaCard.header(c, t("Перевод в этом чате", "Translation in this chat")));
            NebulaCard incoming = new NebulaCard(c);
            incoming.add(new NebulaRow(c).title(t("Входящие сообщения", "Incoming messages"))
                .subtitle(t("Переводить видимые сообщения через ИИ", "Translate visible messages using AI"), false)
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
            incoming.add(language(c, t("Язык входящих", "Incoming language"), false));
            incoming.add(new NebulaRow(c).title(t("Повторить перевод", "Retry translation"))
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> NebulaAutoTranslate.retry(currentAccount, dialog))); column.addView(incoming);
            NebulaCard draft = new NebulaCard(c);
            draft.add(new NebulaRow(c).title(t("Перевод при наборе", "Translate while typing"))
                .subtitle(t("Предпросмотр с кнопкой применения", "Preview with an Apply button"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaTranslationSettings.draft(currentAccount, dialog))
                .withClick(v -> {
                    NebulaRow row = (NebulaRow)v; boolean on = row.toggleChecked();
                    if (on && !ready(c)) { row.checked(false); return; }
                    NebulaTranslationSettings.prefs(currentAccount).edit().putBoolean("draft_" + dialog, on).apply();
                }));
            draft.add(language(c, t("Язык моего текста", "My text language"), true));
            NebulaRow delay = new NebulaRow(c).title(t("Пауза после ввода", "Pause after typing"))
                .subtitle(NebulaTranslationSettings.delay(currentAccount, dialog) + " ms", true).trailing(NebulaRow.TRAIL_CHEVRON);
            delay.withClick(v -> showDialog(new NebulaDialog.Builder(c, getResourceProvider()).setTitle(delayTitle())
                .setItems(new CharSequence[]{"0.5 s", "1 s", "2 s"}, (d, i) -> {
                    int value = new int[]{500, 1000, 2000}[i];
                    NebulaTranslationSettings.prefs(currentAccount).edit().putInt("delay_" + dialog, value).apply();
                    delay.subtitle(value + " ms", true);
                }).setNegativeButton(t("Отмена", "Cancel"), null).create()));
            draft.add(delay);
            LinearLayout.LayoutParams draftParams = new LinearLayout.LayoutParams(-1, -2);
            draftParams.topMargin = NebulaFormUi.dp(12); column.addView(draft, draftParams);
            column.addView(NebulaFormUi.note(c, t("При включении текст автоматически обрабатывает выбранный провайдер ИИ. Ваш черновик заменяется только по нажатию «Применить».",
                "When enabled, text is automatically processed by your selected AI provider. Your draft changes only when you tap Apply.")));
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
        if (NebulaAiAvailability.available()) return true;
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
