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
    private static final String[] PROVIDERS = {"OpenAI · GPT", "Anthropic · Claude", "Google · Gemini", "OpenAI-compatible", NebulaText.text("Gemini Nano · на устройстве", "Gemini Nano · on device")};
    private int provider;
    private String initial = "";
    private SharedPreferences prefs;
    private LinearLayout content;
    private NebulaSettingsHero hero;
    private final LinearLayout[] pages = new LinearLayout[3];
    private final TextView[] tabs = new TextView[3];
    private int selectedPage = -1;
    private EditText key, model, endpoint, prompt, input;
    private TextView answer, keyStatus;
    private NebulaCard responseCard;
    private NebulaButton copy, clear;
    private NebulaButton send, load;
    private NebulaCard nanoCard;
    private TextView nanoStatus;
    private NebulaAiClient client;
    public NebulaAiFragment() { }
    public NebulaAiFragment(String input) { initial = input == null ? "" : input; }
    @Override public View createView(Context c) {
        prefs = c.getSharedPreferences("nebula_ai_settings", 0);
        provider = Math.max(0, Math.min(NebulaAiClient.NANO, prefs.getInt("provider", 0)));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back); actionBar.setTitle(text("Искусственный интеллект", "AI assistant"));
        NebulaTheme t = NebulaTheme.of(c); actionBar.setBackgroundColor(t.surface()); actionBar.setTitleColor(t.onSurface()); actionBar.setItemsColor(t.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() { @Override public void onItemClick(int id) { if (id == -1) finishFragment(); } });
        ScrollView scroll = new ScrollView(c); scroll.setFillViewport(true); scroll.setBackgroundColor(t.surface());
        content = new LinearLayout(c); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(16), dp(12), dp(16), dp(28)); scroll.addView(content);
        build(c);
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, scroll, -12);
    }
    private int dp(int n) { return AndroidUtilities.dp(n); }
    private void build(Context c) {
        content.removeAllViews();
        hero = new NebulaSettingsHero(c, R.drawable.msg_emoji_smiles,
                text("ИИ-помощник", "AI assistant"),
                text("Ваш провайдер. Ваши инструкции. Только тот текст, который выберете вы.",
                        "Your provider. Your instructions. Only the text you choose."));
        content.addView(hero);
        LinearLayout navigation = new LinearLayout(c);
        navigation.setPadding(dp(4), dp(4), dp(4), dp(4));
        android.graphics.drawable.GradientDrawable track = new android.graphics.drawable.GradientDrawable();
        track.setColor(NebulaTheme.of(c).surfaceContainer()); track.setCornerRadius(dp(18));
        navigation.setBackground(track);
        String[] names = {text("Запрос", "Request"), text("Подключение", "Connection"), text("Инструкции", "Instructions")};
        for (int i = 0; i < tabs.length; i++) {
            final int page = i;
            TextView tab = tabs[i] = label(c, names[i], 13, NebulaTheme.of(c).onSurface());
            tab.setGravity(android.view.Gravity.CENTER); tab.setTypeface(AndroidUtilities.bold());
            tab.setMinHeight(dp(48)); tab.setPadding(dp(4), dp(8), dp(4), dp(8));
            tab.setOnClickListener(v -> selectPage(page));
            navigation.addView(tab, new LinearLayout.LayoutParams(0, -1, 1));
        }
        LinearLayout.LayoutParams navigationParams = new LinearLayout.LayoutParams(-1, -2);
        navigationParams.topMargin = dp(18); navigationParams.bottomMargin = dp(6);
        content.addView(navigation, navigationParams);
        for (int i = 0; i < pages.length; i++) {
            pages[i] = new LinearLayout(c); pages[i].setOrientation(LinearLayout.VERTICAL);
            content.addView(pages[i], new LinearLayout.LayoutParams(-1, -2));
        }
        pages[1].addView(NebulaCard.header(c, text("Подключение", "Connection")));
        NebulaCard settings = new NebulaCard(c);
        settings.add(new NebulaRow(c).icon(R.drawable.msg_customize)
                .title(text("Включить ИИ", "Enable AI"))
                .subtitle(text("Пункт в меню сообщений доступен после настройки подключения", "Available in message menus after a connection is configured"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaAiAvailability.enabled())
                .withClick(v -> { NebulaAiAvailability.setEnabled(((NebulaRow) v).toggleChecked()); refreshKeyStatus(); }));
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
        nanoCard = new NebulaCard(c);
        nanoCard.add(NebulaCard.header(c, text("Gemini Nano на устройстве", "On-device Gemini Nano")));
        nanoStatus = label(c, text("Проверяем доступность модели…", "Checking model availability…"), 14, NebulaTheme.of(c).onSurfaceVariant());
        nanoStatus.setPadding(dp(16), dp(8), dp(16), dp(8));
        nanoCard.add(nanoStatus);
        nanoCard.add(new NebulaRow(c).icon(R.drawable.msg_customize)
                .title(text("Версия модели", "Model release"))
                .subtitle(prefs.getBoolean("nano_preview", false) ? "Preview" : "Stable", false)
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> showDialog(new AlertDialog.Builder(c)
                        .setTitle(text("Версия Gemini Nano", "Gemini Nano release"))
                        .setSingleChoiceItems(new String[]{"Stable", "Preview"}, prefs.getBoolean("nano_preview", false) ? 1 : 0, (d, which) -> {
                            prefs.edit().putBoolean("nano_preview", which == 1).apply(); d.dismiss(); build(c);
                        }).setNegativeButton(text("Отмена", "Cancel"), null).create())));
        nanoCard.add(new NebulaRow(c).icon(R.drawable.msg_list)
                .title(text("Производительность", "Performance"))
                .subtitle(prefs.getBoolean("nano_fast", true) ? text("Быстрая модель", "Fast model") : text("Полная модель", "Full model"), false)
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> showDialog(new AlertDialog.Builder(c)
                        .setTitle(text("Производительность Gemini Nano", "Gemini Nano performance"))
                        .setSingleChoiceItems(new String[]{text("Быстрая", "Fast"), text("Полная", "Full")}, prefs.getBoolean("nano_fast", true) ? 0 : 1, (d, which) -> {
                            prefs.edit().putBoolean("nano_fast", which == 0).apply(); d.dismiss(); build(c);
                        }).setNegativeButton(text("Отмена", "Cancel"), null).create())));
        button(c, nanoCard, text("Проверить или скачать модель", "Check or download model"), true, v -> downloadNano());
        settings.add(nanoCard);
        refreshProviderControls();
        button(c, settings, text("Сохранить подключение", "Save connection"), true, v -> { if (save()) toast(text("Настройки сохранены", "Settings saved")); });
        pages[1].addView(settings);
        refreshKeyStatus();

        pages[2].addView(NebulaCard.header(c, text("Инструкции для ИИ", "AI instructions")));
        NebulaCard instructions = new NebulaCard(c);
        prompt = field(c, instructions, text("Системный промпт · необязательно", "System prompt · optional"),
                text("Например: отвечай кратко и на русском", "For example: give concise answers"), prefs.getString("prompt", ""), true, 20000);
        button(c, instructions, text("Сохранить инструкции", "Save instructions"), false, v -> { if (save()) toast(text("Инструкции сохранены", "Instructions saved")); });
        pages[2].addView(instructions);
        pages[2].addView(NebulaCard.header(c, text("История", "History")));
        NebulaCard history = new NebulaCard(c);
        history.add(new NebulaRow(c).icon(R.drawable.msg_recent)
                .title(text("Сохранять историю ИИ", "Save AI history"))
                .subtitle(text("Только на этом устройстве", "Only on this device"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaAiHistory.enabled())
                .withClick(v -> NebulaAiHistory.setEnabled(((NebulaRow) v).toggleChecked())));
        history.add(new NebulaRow(c).icon(R.drawable.msg_recent)
                .title(text("История запросов", "Request history"))
                .subtitle(text("Просмотр и очистка сохранённых запросов", "View and clear saved requests"), false)
                .trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> presentFragment(new NebulaAiHistoryFragment())));
        pages[2].addView(history);

        pages[0].addView(NebulaCard.header(c, text("Запрос", "Request")));
        NebulaCard request = new NebulaCard(c);
        input = field(c, request, text("Сообщение для ИИ", "Message for AI"), text("Напишите вопрос или вставьте текст", "Write a question or paste text"), initial, true, 50000);
        TextView info = label(c, text("Провайдер получит этот текст и системный промпт. Ответ появится здесь.", "The provider receives this text and the system prompt. The response appears here."), 13, NebulaTheme.of(c).onSurfaceVariant());
        info.setPadding(dp(16), dp(8), dp(16), dp(8)); request.add(info);
        send = button(c, request, text("Отправить запрос", "Send request"), true, v -> { if (client != null) cancel(); else request(false); });
        pages[0].addView(request);

        responseCard = new NebulaCard(c);
        responseCard.add(NebulaCard.header(c, text("Ответ", "Response")));
        answer = label(c, "", 16, NebulaTheme.of(c).onSurface()); answer.setPadding(dp(16), dp(12), dp(16), dp(12)); answer.setTextIsSelectable(true); responseCard.add(answer);
        copy = button(c, responseCard, text("Скопировать ответ", "Copy response"), false, v -> AndroidUtilities.addToClipboard(answer.getText()));
        responseCard.setVisibility(View.GONE);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(-1, -2); resultParams.topMargin = dp(18);
        pages[0].addView(responseCard, resultParams);
        selectPage(selectedPage < 0 ? (!initial.isEmpty() || NebulaAiAvailability.available() ? 0 : 1) : selectedPage);
    }
    private void selectPage(int page) {
        selectedPage = page;
        NebulaTheme theme = NebulaTheme.of(content.getContext());
        for (int i = 0; i < pages.length; i++) {
            boolean active = page == i;
            pages[i].setVisibility(active ? View.VISIBLE : View.GONE);
            tabs[i].setSelected(active);
            tabs[i].setTextColor(active ? theme.onPrimaryContainer() : theme.onSurfaceVariant());
            android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
            background.setCornerRadius(dp(14));
            background.setColor(active ? theme.primaryContainer() : android.graphics.Color.TRANSPARENT);
            tabs[i].setBackground(background);
        }
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
        refreshProviderControls();
        if (provider == NebulaAiClient.NANO) {
            hero.setStatus(text("Обработка на устройстве", "On-device processing"), NebulaNanoAi.supportedByOs());
            keyStatus.setText(text("Запросы Gemini Nano обрабатываются на устройстве и не отправляются вашему API-провайдеру.",
                    "Gemini Nano requests are processed on this device and are not sent to your API provider."));
            refreshNanoStatus();
            return;
        }
        boolean saved = NebulaAiSecrets.exists(provider);
        hero.setStatus(!NebulaAiAvailability.enabled() ? text("ИИ выключен", "AI is off")
                : NebulaAiAvailability.available() ? text("Подключение настроено · ", "Configured · ") + PROVIDERS[provider]
                : text("Нужна настройка", "Setup needed"), NebulaAiAvailability.available());
        keyStatus.setText(saved ? text("Ключ сохранён на устройстве. Введите новый, чтобы заменить его.", "A key is saved on this device. Enter a new one to replace it.")
                : text("Нужен API-ключ выбранного провайдера.", "An API key from the selected provider is required."));
        key.setHint(saved ? "••••••••" : text("Введите ключ провайдера", "Enter a provider key"));
        if (clear != null) ((View) clear.getParent()).setVisibility(saved ? View.VISIBLE : View.GONE);
    }
    private void refreshProviderControls() {
        boolean nano = provider == NebulaAiClient.NANO;
        if (endpoint != null) ((View) endpoint.getParent()).setVisibility(!nano && provider == NebulaAiClient.CUSTOM ? View.VISIBLE : View.GONE);
        if (key != null) ((View) key.getParent()).setVisibility(nano ? View.GONE : View.VISIBLE);
        if (keyStatus != null) keyStatus.setVisibility(nano ? View.GONE : View.VISIBLE);
        if (clear != null) ((View) clear.getParent()).setVisibility(!nano && NebulaAiSecrets.exists(provider) ? View.VISIBLE : View.GONE);
        if (model != null) ((View) model.getParent()).setVisibility(nano ? View.GONE : View.VISIBLE);
        if (load != null) ((View) load.getParent()).setVisibility(nano ? View.GONE : View.VISIBLE);
        if (nanoCard != null) nanoCard.setVisibility(nano ? View.VISIBLE : View.GONE);
    }
    private void refreshNanoStatus() {
        if (nanoStatus == null) return;
        nanoStatus.setText(NebulaText.text("Проверка доступности Gemini Nano…", "Checking Gemini Nano availability…"));
        new Thread(() -> {
            int status;
            try { status = NebulaNanoAi.checkStatus(); }
            catch (Exception e) { status = com.google.mlkit.genai.common.FeatureStatus.UNAVAILABLE; }
            final int result = status;
            AndroidUtilities.runOnUIThread(() -> {
                if (nanoStatus == null || getParentActivity() == null || provider != NebulaAiClient.NANO) return;
                String message = result == com.google.mlkit.genai.common.FeatureStatus.AVAILABLE
                        ? text("Готово · работает локально", "Ready · runs on device")
                        : result == com.google.mlkit.genai.common.FeatureStatus.DOWNLOADABLE
                        ? text("Модель можно скачать один раз", "Model can be downloaded once")
                        : result == com.google.mlkit.genai.common.FeatureStatus.DOWNLOADING
                        ? text("Модель загружается в AICore", "Model is downloading in AICore")
                        : text("На этом устройстве модель недоступна", "Model is unavailable on this device");
                nanoStatus.setText(message);
            });
        }, "NebulaNanoStatus").start();
    }
    private void downloadNano() {
        if (provider != NebulaAiClient.NANO || client != null) return;
        nanoStatus.setText(text("Подготовка модели…", "Preparing model…"));
        new Thread(() -> {
            try {
                int status = NebulaNanoAi.checkStatus();
                if (status == com.google.mlkit.genai.common.FeatureStatus.DOWNLOADABLE) {
                    NebulaNanoAi.download(new com.google.mlkit.genai.common.DownloadCallback() {
                        @Override public void onDownloadStarted(long bytes) { nanoProgress(text("Загрузка модели…", "Downloading model…")); }
                        @Override public void onDownloadProgress(long bytes) { nanoProgress(text("Загружено ", "Downloaded ") + android.text.format.Formatter.formatShortFileSize(ApplicationLoader.applicationContext, bytes)); }
                        @Override public void onDownloadCompleted() { nanoProgress(text("Модель готова", "Model ready")); }
                        @Override public void onDownloadFailed(com.google.mlkit.genai.common.GenAiException error) { nanoProgress(text("Не удалось скачать модель", "Model download failed")); }
                    });
                }
                AndroidUtilities.runOnUIThread(this::refreshNanoStatus);
            } catch (Exception e) {
                nanoProgress(text("Не удалось скачать модель. Проверьте AICore и повторите попытку.", "Could not download the model. Check AICore and try again."));
            }
        }, "NebulaNanoDownload").start();
    }
    private void nanoProgress(String message) {
        AndroidUtilities.runOnUIThread(() -> { if (nanoStatus != null && getParentActivity() != null) nanoStatus.setText(message); });
    }
    private boolean save() {
        try {
            if (provider != NebulaAiClient.NANO) {
                NebulaAiClient.base(provider, endpoint.getText().toString());
                String entered = key.getText().toString().trim();
                if (!entered.isEmpty()) { NebulaAiSecrets.save(provider, entered); key.setText(""); }
            }
            SharedPreferences.Editor editor = prefs.edit().putInt("provider", provider)
                    .putString("prompt", prompt.getText().toString());
            if (provider != NebulaAiClient.NANO) editor.putString("model_" + provider, model.getText().toString().trim())
                    .putString("endpoint", endpoint.getText().toString().trim());
            editor.apply();
            refreshKeyStatus();
            return true;
        } catch (Exception e) { toast(text("Не удалось сохранить ключ или адрес API", "Unable to save key or API URL")); return false; }
    }
    private void request(boolean list) {
        if (client != null || !save()) return;
        final String secret;
        if (provider == NebulaAiClient.NANO && list) { downloadNano(); return; }
        if (provider == NebulaAiClient.NANO) secret = "";
        else try { secret = NebulaAiSecrets.read(provider); } catch (Exception e) { toast(text("Введите API-ключ заново", "Enter your API key again")); return; }
        if (provider != NebulaAiClient.NANO && (secret.isEmpty() || !list && model.length() == 0)
                || !list && input.getText().toString().trim().isEmpty()) { toast(text("Укажите API-ключ, модель и текст", "Enter an API key, model and text")); return; }
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
                        if (!result.isEmpty()) NebulaAiHistory.add(PROVIDERS[selected], message, result);
                    }
                });
            } catch (Exception e) {
                // Never display/log provider response bodies, headers, URLs with keys or entered text.
                final String status = e.getMessage() != null && e.getMessage().matches("HTTP [0-9]{3}") ? e.getMessage() : "";
                AndroidUtilities.runOnUIThread(() -> {
                    if (client != task || getParentActivity() == null) return;
                    finishRequest(); selectPage(0); responseCard.setVisibility(View.VISIBLE); ((View) copy.getParent()).setVisibility(View.GONE);
                    String failure = e.getMessage() == null ? "" : e.getMessage();
                    if (failure.startsWith("GEMINI_NANO_DOWNLOAD_REQUIRED")) answer.setText(text("Сначала скачайте Gemini Nano на вкладке «Подключение».", "Download Gemini Nano first from the Connection tab."));
                    else if (failure.startsWith("GEMINI_NANO_DOWNLOADING")) answer.setText(text("Gemini Nano ещё загружается. Попробуйте позже.", "Gemini Nano is still downloading. Try again shortly."));
                    else if (failure.startsWith("GEMINI_NANO_UNAVAILABLE")) answer.setText(text("Эта версия Gemini Nano недоступна на устройстве. Выберите Stable/Fast или облачный сервис.", "This Gemini Nano option is unavailable on this device. Choose Stable/Fast or a cloud service."));
                    else answer.setText(text("Запрос не выполнен. Проверьте подключение, API-ключ, доступ к модели и квоту. ", "Request failed. Check connectivity, API key, model access and quota. ") + status);
                });
            }
        }, "NebulaAI").start();
    }
    private void finishRequest() { client = null; send.setText(text("Отправить запрос", "Send request")); load.setEnabled(true); load.setText(text("Выбрать модель из списка", "Choose an available model")); }
    private void cancel() { if (client != null) { client.cancel(); if (answer != null && responseCard.getVisibility() == View.VISIBLE) answer.setText(text("Запрос отменён", "Request cancelled")); } if (send != null) finishRequest(); }
    private void toast(String message) { if (getParentActivity() != null) Toast.makeText(getParentActivity(), message, Toast.LENGTH_SHORT).show(); }
    @Override public void onFragmentDestroy() { cancel(); if (key != null) key.setText(""); super.onFragmentDestroy(); }
}
