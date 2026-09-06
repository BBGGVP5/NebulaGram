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
    private TextView answer, keyStatus;
    private NebulaCard responseCard;
    private NebulaButton copy, clear;
    private NebulaButton send, load;
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
        content.addView(NebulaCard.header(c, text("Подключение", "Connection")));
        NebulaCard settings = new NebulaCard(c);
        settings.add(new NebulaRow(c).icon(R.drawable.msg_customize).title(text("Провайдер", "Provider")).subtitle(PROVIDERS[provider], false)
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> showDialog(new AlertDialog.Builder(c).setTitle(text("Провайдер", "Provider"))
                .setItems(PROVIDERS, (d, which) -> {
                    if (which == provider || !save()) return;
                    initial = input.getText().toString(); cancel(); provider = which;
                    prefs.edit().putInt("provider", provider).apply(); build(c);
                }).create())));
        endpoint = field(c, settings, text("Адрес API", "API address"), "https://example.com/v1", prefs.getString("endpoint", "https://api.openai.com/v1"), false, 1000);
        ((View) endpoint.getParent()).setVisibility(provider == NebulaAiClient.CUSTOM ? View.VISIBLE : View.GONE);
        key = field(c, settings, text("API-ключ", "API key"), text("Введите ключ провайдера", "Enter a provider key"), "", false, 2048);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setTypeface(android.graphics.Typeface.DEFAULT);
        if (android.os.Build.VERSION.SDK_INT >= 26) key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        keyStatus = label(c, "", 13, NebulaTheme.of(c).onSurfaceVariant());
        keyStatus.setPadding(dp(16), dp(6), dp(16), dp(6)); settings.add(keyStatus);
        clear = button(c, settings, text("Удалить сохранённый ключ", "Remove saved key"), false, v -> {
            try { NebulaAiSecrets.save(provider, ""); key.setText(""); refreshKeyStatus(); toast(text("Ключ удалён", "Key removed")); }
            catch (Exception e) { toast(text("Не удалось удалить ключ", "Unable to remove key")); }
        });
        model = field(c, settings, text("Модель", "Model"), text("Выберите из списка или введите ID", "Choose from the list or enter an ID"), prefs.getString("model_" + provider, ""), false, 256);
        load = button(c, settings, text("Выбрать модель из списка", "Choose an available model"), false, v -> request(true));
        button(c, settings, text("Сохранить подключение", "Save connection"), true, v -> { if (save()) toast(text("Настройки сохранены", "Settings saved")); });
        content.addView(settings);
        refreshKeyStatus();

        content.addView(NebulaCard.header(c, text("Инструкции для ИИ", "AI instructions")));
        NebulaCard instructions = new NebulaCard(c);
        prompt = field(c, instructions, text("Системный промпт · необязательно", "System prompt · optional"),
                text("Например: отвечай кратко и на русском", "For example: give concise answers"), prefs.getString("prompt", ""), true, 20000);
        button(c, instructions, text("Сохранить инструкции", "Save instructions"), false, v -> { if (save()) toast(text("Инструкции сохранены", "Instructions saved")); });
        content.addView(instructions);

        content.addView(NebulaCard.header(c, text("Запрос", "Request")));
        NebulaCard request = new NebulaCard(c);
        input = field(c, request, text("Сообщение для ИИ", "Message for AI"), text("Напишите вопрос или вставьте текст", "Write a question or paste text"), initial, true, 50000);
        TextView info = label(c, text("Провайдер получит этот текст и системный промпт. Ответ появится здесь.", "The provider receives this text and the system prompt. The response appears here."), 13, NebulaTheme.of(c).onSurfaceVariant());
        info.setPadding(dp(16), dp(8), dp(16), dp(8)); request.add(info);
        send = button(c, request, text("Отправить запрос", "Send request"), true, v -> { if (client != null) cancel(); else request(false); });
        content.addView(request);

        responseCard = new NebulaCard(c);
        responseCard.add(NebulaCard.header(c, text("Ответ", "Response")));
        answer = label(c, "", 16, NebulaTheme.of(c).onSurface()); answer.setPadding(dp(16), dp(12), dp(16), dp(12)); answer.setTextIsSelectable(true); responseCard.add(answer);
        copy = button(c, responseCard, text("Скопировать ответ", "Copy response"), false, v -> AndroidUtilities.addToClipboard(answer.getText()));
        responseCard.setVisibility(View.GONE);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(-1, -2); resultParams.topMargin = dp(18);
        content.addView(responseCard, resultParams);
    }
    private TextView label(Context c, String value, int size, int color) {
        TextView v = new TextView(c); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private NebulaButton button(Context c, NebulaCard card, String title, boolean primary, View.OnClickListener listener) {
        LinearLayout container = new LinearLayout(c); container.setPadding(dp(16), dp(8), dp(16), dp(12));
        NebulaButton button = new NebulaButton(c, primary ? NebulaButton.STYLE_FILLED : NebulaButton.STYLE_TEXT);
        button.setText(title); button.setOnClickListener(listener); container.addView(button, new LinearLayout.LayoutParams(-1, -2)); card.add(container); return button;
    }
    private EditText field(Context c, NebulaCard card, String title, String hint, String value, boolean multi, int limit) {
        NebulaTheme t = NebulaTheme.of(c);
        LinearLayout field = new LinearLayout(c); field.setOrientation(LinearLayout.VERTICAL); field.setPadding(dp(16), dp(14), dp(16), dp(12));
        TextView label = label(c, title, 13, t.primary()); label.setTypeface(AndroidUtilities.bold()); field.addView(label);
        EditText edit = new EditText(c); edit.setTextColor(t.onSurface()); edit.setHintTextColor(t.onSurfaceVariant());
        edit.setTextSize(16); edit.setHint(hint); edit.setText(value); edit.setSingleLine(!multi); edit.setMinLines(multi ? 3 : 1);
        edit.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        edit.setMaxLines(multi ? 8 : 1); edit.setFilters(new InputFilter[] {new InputFilter.LengthFilter(limit)}); edit.setPadding(dp(12), dp(12), dp(12), dp(12));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(t.surface()); background.setCornerRadius(dp(12)); background.setStroke(dp(1), NebulaTheme.stateLayer(t.outline(), .35f)); edit.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(8); field.addView(edit, params); card.add(field); return edit;
    }
    private void refreshKeyStatus() {
        if (keyStatus == null) return;
        boolean saved = NebulaAiSecrets.exists(provider);
        keyStatus.setText(saved ? text("Ключ сохранён на устройстве. Введите новый, чтобы заменить его.", "A key is saved on this device. Enter a new one to replace it.")
                : text("Нужен API-ключ выбранного провайдера.", "An API key from the selected provider is required."));
        key.setHint(saved ? "••••••••" : text("Введите ключ провайдера", "Enter a provider key"));
        if (clear != null) ((View) clear.getParent()).setVisibility(saved ? View.VISIBLE : View.GONE);
    }
    private boolean save() {
        try {
            NebulaAiClient.base(provider, endpoint.getText().toString());
            String entered = key.getText().toString().trim();
            if (!entered.isEmpty()) { NebulaAiSecrets.save(provider, entered); key.setText(""); }
            prefs.edit().putInt("provider", provider).putString("model_" + provider, model.getText().toString().trim())
                    .putString("endpoint", endpoint.getText().toString().trim()).putString("prompt", prompt.getText().toString()).apply();
            refreshKeyStatus();
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
        send.setText(text("Отменить запрос", "Cancel request")); load.setEnabled(false);
        if (!list) {
            responseCard.setVisibility(View.VISIBLE); ((View) copy.getParent()).setVisibility(View.GONE);
            answer.setText(text("Ожидание ответа…", "Waiting for response…"));
        } else load.setText(text("Загрузка моделей…", "Loading models…"));
        new Thread(() -> {
            try {
                final ArrayList<String> models = list ? task.models(selected, url, secret) : null;
                final String result = list ? "" : task.generate(selected, url, secret, name, instructions, message);
                AndroidUtilities.runOnUIThread(() -> {
                    if (client != task || getParentActivity() == null) return;
                    finishRequest();
                    if (list) {
                        if (models.isEmpty()) toast(text("Список пуст. Имя модели можно ввести вручную.", "The list is empty. You can enter a model name manually."));
                        else showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text("Модель", "Model"))
                                .setItems(models.toArray(new String[0]), (d, i) -> { model.setText(models.get(i)); save(); }).create());
                    } else {
                        answer.setText(result.isEmpty() ? text("Провайдер не вернул текст. Проверьте модель и запрос.", "The provider returned no text. Check the model and request.") : result);
                        ((View) copy.getParent()).setVisibility(result.isEmpty() ? View.GONE : View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                // Never display/log provider response bodies, headers, URLs with keys or entered text.
                final String status = e.getMessage() != null && e.getMessage().matches("HTTP [0-9]{3}") ? e.getMessage() : "";
                AndroidUtilities.runOnUIThread(() -> {
                    if (client != task || getParentActivity() == null) return;
                    finishRequest(); responseCard.setVisibility(View.VISIBLE); ((View) copy.getParent()).setVisibility(View.GONE); answer.setText(text("Запрос не выполнен. Проверьте подключение, API-ключ, доступ к модели и квоту. ", "Request failed. Check connectivity, API key, model access and quota. ") + status);
                });
            }
        }, "NebulaAI").start();
    }
    private void finishRequest() { client = null; send.setText(text("Отправить запрос", "Send request")); load.setEnabled(true); load.setText(text("Выбрать модель из списка", "Choose an available model")); }
    private void cancel() { if (client != null) { client.cancel(); if (answer != null && responseCard.getVisibility() == View.VISIBLE) answer.setText(text("Запрос отменён", "Request cancelled")); } if (send != null) finishRequest(); }
    private void toast(String message) { if (getParentActivity() != null) Toast.makeText(getParentActivity(), message, Toast.LENGTH_SHORT).show(); }
    @Override public void onFragmentDestroy() { cancel(); if (key != null) key.setText(""); super.onFragmentDestroy(); }
}
