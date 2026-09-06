package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.content.*;
import android.text.InputType;
import android.text.InputFilter;
import android.view.View;
import android.widget.*;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.*;
import java.util.ArrayList;

/** User chooses the exact request text. Responses are never sent to a Telegram chat automatically. */
public final class NebulaAiFragment extends BaseFragment {
    private static final String[] PROVIDERS = {"OpenAI · GPT", "Anthropic · Claude", "Google · Gemini", "OpenAI-compatible"};
    private int provider;
    private String initial = "";
    private SharedPreferences prefs;
    private LinearLayout content;
    private EditText key, model, endpoint, prompt, input;
    private TextView answer;
    private NebulaRow send, load;
    private NebulaAiClient client;
    public NebulaAiFragment() { }
    public NebulaAiFragment(String input) { initial = input == null ? "" : input; }
    @Override public View createView(Context c) {
        prefs = c.getSharedPreferences("nebula_ai_settings", 0);
        provider = Math.max(0, Math.min(3, prefs.getInt("provider", 0)));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back); actionBar.setTitle(text("Искусственный интеллект", "AI assistant"));
        NebulaTheme t = NebulaTheme.of(c); actionBar.setBackgroundColor(t.surface()); actionBar.setTitleColor(t.onSurface()); actionBar.setItemsColor(t.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() { @Override public void onItemClick(int id) { if (id == -1) finishFragment(); } });
        ScrollView scroll = new ScrollView(c); scroll.setFillViewport(true); scroll.setBackgroundColor(t.surface());
        content = new LinearLayout(c); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(12), dp(8), dp(12), dp(32)); scroll.addView(content);
        build(c);
        return fragmentView = scroll;
    }
    private int dp(int n) { return AndroidUtilities.dp(n); }
    private void build(Context c) {
        content.removeAllViews();
        NebulaCard settings = new NebulaCard(c);
        settings.add(new NebulaRow(c).icon(R.drawable.msg_customize).title(text("Провайдер", "Provider")).subtitle(PROVIDERS[provider], false)
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> showDialog(new AlertDialog.Builder(c).setTitle(text("Провайдер", "Provider"))
                .setItems(PROVIDERS, (d, which) -> { initial = input.getText().toString(); cancel(); provider = which; prefs.edit().putInt("provider", provider).apply(); build(c); }).create())));
        content.addView(settings);
        endpoint = field(c, text("Адрес API (с /v1)", "API base URL (include /v1)"), prefs.getString("endpoint", "https://api.openai.com/v1"), false, 1000);
        endpoint.setVisibility(provider == NebulaAiClient.CUSTOM ? View.VISIBLE : View.GONE);
        key = field(c, text("API-ключ · пустое поле сохраняет прежний", "API key · leave blank to keep saved key"), "", false, 2048);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        if (android.os.Build.VERSION.SDK_INT >= 26) key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        model = field(c, text("Модель", "Model"), prefs.getString("model_" + provider, ""), false, 256);
        load = new NebulaRow(c).icon(R.drawable.msg_download).title(text("Загрузить список моделей", "Load available models"));
        load.setOnClickListener(v -> request(true)); content.addView(load);
        prompt = field(c, text("Системный промпт", "System prompt"), prefs.getString("prompt", ""), true, 20000);
        NebulaRow save = new NebulaRow(c).icon(R.drawable.nebula_cupertino_bookmark).title(text("Сохранить настройки", "Save settings"));
        save.setOnClickListener(v -> { if (save()) toast(text("Сохранено", "Saved")); }); content.addView(save);
        NebulaRow clear = new NebulaRow(c).icon(R.drawable.msg_delete).title(text("Удалить сохранённый ключ", "Remove saved key"));
        clear.setOnClickListener(v -> { try { NebulaAiSecrets.save(provider, ""); key.setText(""); toast(text("Ключ удалён", "Key removed")); } catch (Exception e) { toast(text("Не удалось удалить ключ", "Unable to remove key")); } }); content.addView(clear);
        content.addView(NebulaMenuFragment.placeholder(c, text("По кнопке ниже провайдер получит только введённый текст и промпт. Ответ можно скопировать; в чат он сам не отправляется.", "The button below sends only the entered text and prompt to the provider. You can copy the response; it is not sent to a chat automatically.")));
        input = field(c, text("Текст для запроса", "Request text"), initial, true, 50000);
        send = new NebulaRow(c).icon(R.drawable.msg_send).title(text("Отправить запрос", "Send request")); send.setOnClickListener(v -> { if (client != null) cancel(); else request(false); }); content.addView(send);
        answer = new TextView(c); answer.setTextSize(16); answer.setTextColor(NebulaTheme.of(c).onSurface()); answer.setPadding(dp(16), dp(12), dp(16), dp(12)); answer.setTextIsSelectable(true); content.addView(answer);
        content.addView(new NebulaRow(c).icon(R.drawable.msg_copy).title(text("Скопировать ответ", "Copy response")).withClick(v -> { if (answer.length() > 0) AndroidUtilities.addToClipboard(answer.getText()); }));
    }
    private EditText field(Context c, String hint, String value, boolean multi, int limit) {
        EditText edit = new EditText(c); edit.setTextColor(NebulaTheme.of(c).onSurface()); edit.setHintTextColor(NebulaTheme.of(c).onSurfaceVariant());
        edit.setTextSize(15); edit.setHint(hint); edit.setText(value); edit.setSingleLine(!multi); edit.setMinLines(multi ? 3 : 1);
        edit.setMaxLines(multi ? 8 : 1); edit.setFilters(new InputFilter[] {new InputFilter.LengthFilter(limit)}); edit.setPadding(dp(16), dp(12), dp(16), dp(12));
        content.addView(edit, new LinearLayout.LayoutParams(-1, -2)); return edit;
    }
    private boolean save() {
        try {
            NebulaAiClient.base(provider, endpoint.getText().toString());
            String entered = key.getText().toString().trim();
            if (!entered.isEmpty()) { NebulaAiSecrets.save(provider, entered); key.setText(""); }
            prefs.edit().putInt("provider", provider).putString("model_" + provider, model.getText().toString().trim())
                    .putString("endpoint", endpoint.getText().toString().trim()).putString("prompt", prompt.getText().toString()).apply();
            return true;
        } catch (Exception e) { toast(text("Не удалось сохранить ключ или адрес API", "Unable to save key or API URL")); return false; }
    }
    private void request(boolean list) {
        if (client != null || !save()) return;
        final String secret;
        try { secret = NebulaAiSecrets.read(provider); } catch (Exception e) { toast(text("Введите API-ключ заново", "Enter your API key again")); return; }
        if (secret.isEmpty() || !list && (model.length() == 0 || input.getText().toString().trim().isEmpty())) { toast(text("Укажите API-ключ, модель и текст", "Enter an API key, model and text")); return; }
        final int selected = provider;
        final String url = endpoint.getText().toString(), name = model.getText().toString().trim(), instructions = prompt.getText().toString(), message = input.getText().toString();
        final NebulaAiClient task = client = new NebulaAiClient();
        send.title(text("Отменить запрос", "Cancel request")); load.setEnabled(false);
        answer.setText(text("Ожидание ответа…", "Waiting for response…"));
        new Thread(() -> {
            try {
                final ArrayList<String> models = list ? task.models(selected, url, secret) : null;
                final String result = list ? "" : task.generate(selected, url, secret, name, instructions, message);
                AndroidUtilities.runOnUIThread(() -> {
                    if (client != task || getParentActivity() == null) return;
                    finishRequest();
                    if (list) {
                        answer.setText("");
                        if (models.isEmpty()) toast(text("Список пуст. Имя модели можно ввести вручную.", "The list is empty. You can enter a model name manually."));
                        else showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text("Модель", "Model"))
                                .setItems(models.toArray(new String[0]), (d, i) -> { model.setText(models.get(i)); save(); }).create());
                    } else answer.setText(result.isEmpty() ? text("Провайдер не вернул текст. Проверьте модель и запрос.", "The provider returned no text. Check the model and request.") : result);
                });
            } catch (Exception e) {
                // Never display/log provider response bodies, headers, URLs with keys or entered text.
                final String status = e.getMessage() != null && e.getMessage().matches("HTTP [0-9]{3}") ? e.getMessage() : "";
                AndroidUtilities.runOnUIThread(() -> {
                    if (client != task || getParentActivity() == null) return;
                    finishRequest(); answer.setText(text("Запрос не выполнен. Проверьте подключение, API-ключ, доступ к модели и квоту. ", "Request failed. Check connectivity, API key, model access and quota. ") + status);
                });
            }
        }, "NebulaAI").start();
    }
    private void finishRequest() { client = null; send.title(text("Отправить запрос", "Send request")); load.setEnabled(true); }
    private void cancel() { if (client != null) client.cancel(); if (send != null) finishRequest(); }
    private void toast(String message) { if (getParentActivity() != null) Toast.makeText(getParentActivity(), message, Toast.LENGTH_SHORT).show(); }
    @Override public void onFragmentDestroy() { cancel(); if (key != null) key.setText(""); super.onFragmentDestroy(); }
}
