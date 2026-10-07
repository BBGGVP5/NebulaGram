package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

/** Grouped AI preferences with a scrolling native animated emoji header. */
public final class NebulaAiSettingsFragment extends BaseFragment {
    private LinearLayout content;
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, "Nebula AI");
        content = NebulaFormUi.column(c); rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, content), -12);
    }
    @Override public void onResume() { super.onResume(); if (content != null) rebuild(); }
    private NebulaRow link(int icon, String title, String value, Runnable action) {
        return new NebulaRow(content.getContext()).icon(icon).title(title).subtitle(value, true)
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> action.run());
    }
    private NebulaRow toggle(String key, String title, String hint, boolean defaultValue) {
        SharedPreferences p = NebulaAiOptions.prefs();
        return new NebulaRow(content.getContext()).title(title).subtitle(hint, false).trailing(NebulaRow.TRAIL_SWITCH)
                .checked("replace_editor".equals(key)?NebulaAiReplacements.editor():p.getBoolean(key, defaultValue)).withClick(v -> p.edit().putBoolean(key, ((NebulaRow) v).toggleChecked()).apply());
    }
    private void rebuild() {
        Context c = content.getContext(); content.removeAllViews();
        content.addView(new NebulaSettingsHero(c, "🤖", "Nebula AI", text("Работайте с сообщениями или создавайте текст перед отправкой.", "Work with messages or create text before sending.")));
        content.addView(NebulaFormUi.primary(c, text("Открыть чат с ИИ", "Open AI chat"), v -> presentFragment(new NebulaAiFragment())));
        NebulaCard main = new NebulaCard(c);
        NebulaAiServices.list();
        NebulaAiServices.Service service = NebulaAiServices.find(NebulaAiServices.selected());
        main.add(link(R.drawable.msg_language, text("Сервисы", "Services"), service == null ? text("Настроить", "Configure") : service.name, () -> presentFragment(new NebulaAiServicesFragment())));
        NebulaAiRoles.Role role = NebulaAiRoles.current();
        main.add(link(R.drawable.msg_openprofile, text("Роли", "Roles"), NebulaAiRoles.displayName(role), () -> presentFragment(new NebulaAiRolesFragment())));
        main.add(link(R.drawable.msg_recent, text("История сообщений", "Message history"), NebulaAiHistory.enabled() ? text("Включена", "Enabled") : text("Выключена", "Disabled"), () -> presentFragment(new NebulaAiHistorySettingsFragment())));
        main.add(link(R.drawable.msg_translate, text("Переводчик", "Translator"), text("Живой перевод и мой текст", "Live translation and my text"), () -> presentFragment(new NebulaTranslationFragment(currentAccount, 0))));
        NebulaFormUi.group(content, text("Основные", "General"), main);
        NebulaCard replacements = new NebulaCard(c);
        replacements.add(toggle("replace_editor", text("Редактор Nebula AI", "Nebula AI editor"), text("Заменяет кнопку ИИ Telegram · при выключении возвращается Telegram", "Replaces Telegram AI · turn off to use Telegram"), false));
        replacements.add(toggle("replace_summaries", text("Краткие сводки", "Summaries"), text("Сокращать длинные сообщения через Nebula AI", "Summarize long messages with Nebula AI"), false));
        NebulaFormUi.group(content, text("ИИ Telegram", "Telegram AI"), replacements);
        NebulaCard generation = new NebulaCard(c);
        generation.add(toggle("stream_response", text("Потоковая передача ответа", "Stream response"), text("Показывать ответ облачного сервиса по мере получения", "Show cloud responses as they arrive"), true));
        generation.add(toggle("response_only", text("Вставлять только ответ", "Insert only the answer"), text("Отключите, чтобы сохранить исходный текст рядом", "Disable to keep the original text alongside it"), true));
        generation.add(toggle("quote_answer", text("Вставлять ответ как цитату", "Insert the answer as a quote"), "", false));
        generation.add(toggle("reasoning", text("Рассуждения", "Reasoning"), text("Для поддерживаемых моделей · может увеличить время ответа", "For supported models · may increase response time"), false));
        NebulaFormUi.group(content, text("Генерация", "Generation"), generation);
        NebulaCard temperature = new NebulaCard(c);
        TextView value = NebulaFormUi.note(c, "");
        SeekBar slider = new SeekBar(c); slider.setMax(200); slider.setProgress(Math.round(NebulaAiOptions.prefs().getFloat("temperature", 1f) * 100));
        slider.setPadding(NebulaFormUi.dp(16), NebulaFormUi.dp(10), NebulaFormUi.dp(16), NebulaFormUi.dp(20));
        value.setText(text("Температура · ", "Temperature · ") + slider.getProgress() / 100f);
        slider.setContentDescription(text("Температура ответа, от 0 до 2", "Response temperature, from 0 to 2"));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) { value.setText(text("Температура · ", "Temperature · ") + progress / 100f); }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { NebulaAiOptions.prefs().edit().putFloat("temperature", s.getProgress() / 100f).apply(); }
        });
        temperature.add(value); temperature.add(slider); NebulaFormUi.group(content, text("Температура", "Temperature"), temperature);
        content.addView(NebulaFormUi.note(c, text("Низкое значение делает ответ точнее, высокое — разнообразнее. Доступность температуры и рассуждений зависит от модели. Gemini Nano управляет генерацией на устройстве.", "Lower values make responses more focused; higher values add variety. Temperature and reasoning depend on the model. Gemini Nano manages generation on device.")));
    }
}
