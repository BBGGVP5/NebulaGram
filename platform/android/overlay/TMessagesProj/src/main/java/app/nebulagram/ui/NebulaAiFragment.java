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
    private EditText key, model, endpoint, prompt;
    private NebulaAiChatView chat;
    private final LinearLayout[] bodies = new LinearLayout[3];
    private TextView keyStatus;
    private NebulaButton clear, load;
    private NebulaCard nanoCard;
    private TextView nanoStatus;
    private ProgressBar nanoProgressBar;
    private boolean nanoPaused;
    private long nanoTotal;
    private final Runnable nanoPoll = () -> { if (!this.destroyed && !nanoPaused) refreshNanoStatus(); };
    private NebulaAiClient client;
    private NebulaButton nanoAction, nanoFallback, saveConnection;
    private NebulaRow nanoRelease, nanoPerformance;
    private Thread nanoWorker;
    private int nanoRequest;
    private boolean nanoDownloading, destroyed;
    public NebulaAiFragment() { }
    public NebulaAiFragment openConnection() { selectedPage = 1; return this; }
    public NebulaAiFragment(String input) { initial = input == null ? "" : input; }
    @Override public View createView(Context c) {
        prefs = c.getSharedPreferences("nebula_ai_settings", 0);
        provider = Math.max(0, Math.min(NebulaAiClient.NANO, prefs.getInt("provider", 0)));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back); actionBar.setTitle(text("Искусственный интеллект", "AI assistant"));
        NebulaTheme t = NebulaTheme.of(c); actionBar.setBackgroundColor(t.opaqueSurface()); actionBar.setTitleColor(t.onSurface()); actionBar.setItemsColor(t.onSurface(), false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() { @Override public void onItemClick(int id) { if (id == -1) finishFragment(); } });
        content = new LinearLayout(c); content.setOrientation(LinearLayout.VERTICAL); content.setBackgroundColor(t.opaqueSurface()); content.setPadding(dp(16), dp(8), dp(16), dp(8));
        build(c);
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, content, -12);
    }
    private int dp(int n) { return AndroidUtilities.dp(n); }
    private void build(Context c) {
        invalidateNanoCheck();
        if (chat != null) chat.dispose();
        content.removeAllViews();
        hero = new NebulaSettingsHero(c, R.drawable.msg_emoji_smiles,
                text("ИИ-помощник", "AI assistant"),
                text("Чат, модели и ваши инструкции.", "Chat, models and your instructions."));
        content.addView(hero);
        LinearLayout navigation = new LinearLayout(c);
        navigation.setBaselineAligned(false);
        navigation.setPadding(dp(4), dp(4), dp(4), dp(4));
        android.graphics.drawable.GradientDrawable track = new android.graphics.drawable.GradientDrawable();
        track.setColor(NebulaTheme.of(c).surfaceContainer()); track.setCornerRadius(dp(18));
        navigation.setBackground(track);
        String[] names = {text("Чат", "Chat"), text("Подключение", "Connection"), text("Инструкции", "Instructions")};
        for (int i = 0; i < tabs.length; i++) {
            final int page = i;
            TextView tab = tabs[i] = label(c, names[i], 13, NebulaTheme.of(c).onSurface());
            tab.setGravity(android.view.Gravity.CENTER); tab.setTypeface(AndroidUtilities.bold());
            tab.setMinHeight(dp(48)); tab.setPadding(dp(4), dp(8), dp(4), dp(8));
            tab.setOnClickListener(v -> selectPage(page));
            navigation.addView(tab, new LinearLayout.LayoutParams(0, -2, 1));
        }
        LinearLayout.LayoutParams navigationParams = new LinearLayout.LayoutParams(-1, -2);
        navigationParams.topMargin = dp(18); navigationParams.bottomMargin = dp(6);
        content.addView(navigation, navigationParams);
        for (int i = 0; i < pages.length; i++) {
            pages[i] = new LinearLayout(c); pages[i].setOrientation(LinearLayout.VERTICAL);
            content.addView(pages[i], new LinearLayout.LayoutParams(-1, 0, 1));
            bodies[i] = new LinearLayout(c); bodies[i].setOrientation(LinearLayout.VERTICAL);
            if (i == 0) pages[i].addView(bodies[i], new LinearLayout.LayoutParams(-1, -1));
            else {
                ScrollView scroll = new ScrollView(c); scroll.setFillViewport(true); scroll.addView(bodies[i]);
                pages[i].addView(scroll, new LinearLayout.LayoutParams(-1, -1));
            }
        }
        bodies[1].addView(NebulaCard.header(c, text("Подключение", "Connection")));
        NebulaCard settings = new NebulaCard(c);
        settings.add(new NebulaRow(c).icon(R.drawable.msg_customize)
                .title(text("Включить ИИ", "Enable AI"))
                .subtitle(text("Пункт в меню сообщений доступен после настройки подключения", "Available in message menus after a connection is configured"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaAiAvailability.enabled())
                .withClick(v -> { NebulaAiAvailability.setEnabled(((NebulaRow) v).toggleChecked()); refreshKeyStatus(); }));
        settings.add(new NebulaRow(c).icon(R.drawable.nebula_ai_spark).title(text("ИИ на главной", "AI on the home screen"))
                .subtitle(text("Открывать чат с ИИ кнопкой вместо камеры", "Open an AI chat from the camera button"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaAiSheet.homeEnabled())
                .withClick(v -> prefs.edit().putBoolean("home_shortcut", ((NebulaRow) v).toggleChecked()).apply()));

        settings.add(new NebulaRow(c).icon(R.drawable.nebula_ai_spark).title(text("ИИ в чате", "AI in chats"))
                .subtitle(text("Кнопка в поле ввода и перевод", "Composer button and translation"), false)
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> presentFragment(new NebulaTranslationFragment(currentAccount, 0))));

        settings.add(new NebulaRow(c).icon(R.drawable.msg_customize).title(text("Провайдер", "Provider")).subtitle(PROVIDERS[provider], false)
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> showDialog(new NebulaDialog.Builder(c).setTitle(text("Провайдер", "Provider"))
                .setSelectedIndex(provider).setDescriptions(new CharSequence[]{
                    text("API-ключ OpenAI", "OpenAI API key"), text("API-ключ Anthropic", "Anthropic API key"),
                    text("API-ключ Google AI", "Google AI API key"), text("Ваш адрес API и ключ", "Your API address and key"),
                    text("Локально · через Android AICore", "On device · via Android AICore")}).setItems(PROVIDERS, (d, which) -> {
                    if (which == provider || !save()) return;
                    initial = chat == null ? initial : chat.draft(); cancel(); provider = which;
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
        load = button(c, settings, text("Выбрать модель из списка", "Choose an available model"), false, v -> loadModels());
        saveConnection = button(c, settings, text("Сохранить подключение", "Save connection"), true,
                v -> { if (save()) toast(text("Настройки сохранены", "Settings saved")); });
        bodies[1].addView(settings);
        nanoCard = new NebulaCard(c);
        LinearLayout.LayoutParams nanoParams = new LinearLayout.LayoutParams(-1, -2);
        nanoParams.topMargin = dp(16);
        bodies[1].addView(nanoCard, nanoParams);
        nanoStatus = label(c, "", 14, NebulaTheme.of(c).onSurfaceVariant());
        nanoStatus.setLineSpacing(dp(3), 1f);
        nanoStatus.setPadding(dp(16), dp(18), dp(16), dp(14));
        nanoStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        nanoCard.add(nanoStatus);
        nanoProgressBar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        nanoProgressBar.setMax(100); nanoProgressBar.setIndeterminate(true);
        nanoProgressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(NebulaTheme.of(c).primary()));
        nanoProgressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(NebulaTheme.of(c).primary()));
        LinearLayout progressWrap = new LinearLayout(c); progressWrap.setPadding(dp(16), 0, dp(16), dp(12));
        progressWrap.addView(nanoProgressBar, new LinearLayout.LayoutParams(-1, dp(8))); nanoCard.add(progressWrap);
        nanoRelease = new NebulaRow(c).title(text("Версия модели", "Model release"))
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> {
                    if (nanoDownloading) return;
                    showDialog(new NebulaDialog.Builder(c).setTitle(text("Версия Gemini Nano", "Gemini Nano release"))
                            .setSelectedIndex(prefs.getBoolean("nano_preview", false) ? 1 : 0)
                            .setDescriptions(new CharSequence[]{text("Стабильная версия для повседневной работы", "Stable release for everyday use"),
                                    text("Ранняя версия · нужен доступ к AICore Preview", "Early release · requires AICore Preview access")})
                            .setItems(new CharSequence[]{"Stable", "Preview"}, (d, which) -> changeNano("nano_preview", which == 1)).create());
                });
        nanoCard.add(nanoRelease);
        nanoPerformance = new NebulaRow(c).title(text("Производительность", "Performance"))
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> {
                    if (nanoDownloading) return;
                    showDialog(new NebulaDialog.Builder(c).setTitle(text("Модель Gemini Nano", "Gemini Nano model"))
                            .setSelectedIndex(prefs.getBoolean("nano_fast", false) ? 1 : 0)
                            .setDescriptions(new CharSequence[]{text("Рекомендуемый вариант · полные возможности", "Recommended · full capabilities"),
                                    text("Быстрее отвечает · доступна не на всех устройствах", "Faster responses · not available on every device")})
                            .setItems(new CharSequence[]{text("Полная", "Full"), text("Быстрая", "Fast")},
                                    (d, which) -> changeNano("nano_fast", which == 1)).create());
                });
        nanoCard.add(nanoPerformance);
        updateNanoLabels();
        nanoAction = button(c, nanoCard, text("Проверить доступность модели", "Check model availability"), true, v -> downloadNano());
        nanoFallback = button(c, nanoCard, text("Попробовать Stable · Полная", "Try Stable · Full"), false, v -> {
            if (nanoDownloading) return;
            prefs.edit().putBoolean("nano_preview", false).putBoolean("nano_fast", false).apply();
            updateNanoLabels(); invalidateNanoCheck(); refreshNanoStatus();
        });
        ((View) nanoFallback.getParent()).setVisibility(View.GONE);
        button(c, nanoCard, text("Обновить Android AICore", "Update Android AICore"), false, v ->
                org.telegram.messenger.browser.Browser.openUrl(c, "https://play.google.com/store/apps/details?id=com.google.android.aicore"));
        nanoCard.add(NebulaFormUi.note(c, text("Моделью управляет Android AICore. Здесь можно проверить доступность и скачать предложенную сервисом модель. Обновления AICore открываются в Google Play.", "Android AICore manages the model. Check availability and download the model offered by the service here. AICore updates open in Google Play.")));
        refreshProviderControls();
        refreshKeyStatus();

        bodies[2].addView(NebulaCard.header(c, text("Инструкции для ИИ", "AI instructions")));
        NebulaCard instructions = new NebulaCard(c);
        prompt = field(c, instructions, text("Системный промпт · необязательно", "System prompt · optional"),
                text("Например: отвечай кратко и на русском", "For example: give concise answers"), prefs.getString("prompt", ""), true, 20000);
        button(c, instructions, text("Сохранить инструкции", "Save instructions"), false, v -> { if (save()) toast(text("Инструкции сохранены", "Instructions saved")); });
        bodies[2].addView(instructions);
        bodies[2].addView(NebulaCard.header(c, text("История", "History")));
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
        bodies[2].addView(history);



        chat = new NebulaAiChatView(c, initial, () -> selectPage(1));
        bodies[0].addView(chat, new LinearLayout.LayoutParams(-1, -1));
        selectPage(selectedPage < 0 ? (!initial.isEmpty() || NebulaAiAvailability.available() ? 0 : 1) : selectedPage);
    }
    private void selectPage(int page) {
        selectedPage = page;
        hero.setVisibility(page == 0 ? View.GONE : View.VISIBLE);
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
        button.setText(title); button.setSingleLine(false); button.setEllipsize(null); button.setPadding(dp(16), dp(12), dp(16), dp(12)); button.setOnClickListener(listener); container.addView(button, new LinearLayout.LayoutParams(-1, -2)); card.add(container); return button;
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
            hero.setStatus(text("Gemini Nano · на устройстве", "Gemini Nano · on device"), false);
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
        if (saveConnection != null) ((View) saveConnection.getParent()).setVisibility(nano ? View.GONE : View.VISIBLE);
    }
    private void updateNanoLabels() {
        nanoRelease.subtitle(prefs.getBoolean("nano_preview", false) ? "Preview" : "Stable", false);
        nanoPerformance.subtitle(prefs.getBoolean("nano_fast", false) ? text("Быстрая модель", "Fast model") : text("Полная модель", "Full model"), false);
    }
    private void changeNano(String key, boolean value) {
        if (nanoDownloading) return;
        prefs.edit().putBoolean(key, value).apply();
        updateNanoLabels();
        invalidateNanoCheck(); refreshNanoStatus();
    }
    private void invalidateNanoCheck() {
        nanoRequest++; nanoTotal = 0;
        AndroidUtilities.cancelRunOnUIThread(nanoPoll);
        if (nanoWorker != null) nanoWorker.interrupt();
        nanoWorker = null; nanoDownloading = false;
    }
    private boolean currentNano(int request) {
        return !destroyed && request == nanoRequest && getParentActivity() != null && provider == NebulaAiClient.NANO;
    }
    private void refreshNanoStatus() { checkNano(false); }
    private void downloadNano() { checkNano(true); }
    private void checkNano(boolean download) {
        if (destroyed || provider != NebulaAiClient.NANO || nanoWorker != null || nanoStatus == null) return;
        if (!NebulaNanoAi.supportedByOs()) {
            ((View) nanoProgressBar.getParent()).setVisibility(View.GONE);
            nanoStatus.setText(text("Gemini Nano требует Android 8 или новее и совместимый AICore.", "Gemini Nano requires Android 8 or later and compatible AICore."));
            nanoAction.setEnabled(false); return;
        }
        final int request = ++nanoRequest;
        final boolean preview = prefs.getBoolean("nano_preview", false), fast = prefs.getBoolean("nano_fast", false);
        AndroidUtilities.cancelRunOnUIThread(nanoPoll);
        nanoDownloading = download || nanoDownloading;
        ((View) nanoProgressBar.getParent()).setVisibility(View.VISIBLE);
        nanoProgressBar.setIndeterminate(true);
        nanoAction.setEnabled(false);
        nanoRelease.setEnabled(!nanoDownloading); nanoPerformance.setEnabled(!nanoDownloading);
        nanoFallback.setEnabled(!nanoDownloading);
        if (!nanoDownloading) nanoStatus.setText(text("Проверяем выбранную модель…", "Checking the selected model…"));
        hero.setStatus(nanoDownloading ? text("Модель загружается…", "Downloading model…") : text("Проверяем модель…", "Checking model…"), false);
        nanoAction.setText(text("Проверяем…", "Checking…"));
        ((View) nanoFallback.getParent()).setVisibility(View.GONE);
        nanoWorker = new Thread(() -> {
            int status = -1;
            String failure = null;
            try (NebulaNanoAi.Session session = new NebulaNanoAi.Session(preview, fast)) {
                status = session.checkStatus();
                if (download && status == com.google.mlkit.genai.common.FeatureStatus.DOWNLOADABLE) {
                    final java.util.concurrent.atomic.AtomicReference<Throwable> downloadError = new java.util.concurrent.atomic.AtomicReference<>();
                    session.download(new com.google.mlkit.genai.common.DownloadCallback() {
                        @Override public void onDownloadStarted(long bytes) { nanoBytes(request, 0, bytes); }
                        @Override public void onDownloadProgress(long bytes) { nanoBytes(request, bytes, -1); }
                        @Override public void onDownloadCompleted() { nanoProgress(request, text("Проверяем загруженную модель…", "Checking the downloaded model…")); }
                        @Override public void onDownloadFailed(com.google.mlkit.genai.common.GenAiException error) { downloadError.set(error); }
                    });
                    if (downloadError.get() != null) failure = NebulaNanoAi.errorText(downloadError.get());
                    else status = session.checkStatus();
                }
            } catch (Exception e) { failure = NebulaNanoAi.errorText(e); }
            final int result = status;
            final String error = failure;
            AndroidUtilities.runOnUIThread(() -> {
                if (!currentNano(request)) return;
                nanoWorker = null;
                nanoAction.setEnabled(true); nanoRelease.setEnabled(true); nanoPerformance.setEnabled(true); nanoFallback.setEnabled(true);
                boolean ready = error == null && result == com.google.mlkit.genai.common.FeatureStatus.AVAILABLE;
                if (ready) NebulaAutoTranslate.modelReady();
                boolean downloadable = error == null && result == com.google.mlkit.genai.common.FeatureStatus.DOWNLOADABLE;
                boolean downloading = error == null && result == com.google.mlkit.genai.common.FeatureStatus.DOWNLOADING;
                nanoDownloading = downloading;
                nanoRelease.setEnabled(!downloading); nanoPerformance.setEnabled(!downloading); nanoFallback.setEnabled(!downloading);
                nanoAction.setEnabled(!downloading);
                ((View) nanoProgressBar.getParent()).setVisibility(downloading ? View.VISIBLE : View.GONE);
                String message = error != null ? error : ready
                        ? text("Модель готова. Запросы обрабатываются на устройстве.", "Model ready. Requests are processed on this device.")
                        : downloadable ? text("Модель доступна. Скачайте её один раз для работы на устройстве.", "Model available. Download it once to use it on this device.")
                        : downloading ? text("Модель скачивается через AICore. Готовность обновится автоматически.", "AICore is downloading the model. Readiness updates automatically.")
                        : (preview || fast)
                        ? text("AICore не предоставил выбранную модель. Попробуйте Stable · Полная или обновите сервис.", "AICore has not provided the selected model. Try Stable · Full or update the service.")
                        : text("AICore пока не предоставил Stable · Полная. Обновите сервис, оставьте устройство в сети и повторите проверку позже.", "AICore has not provided Stable · Full yet. Update the service, keep the device online and check again later.");
                nanoStatus.setText(message);
                hero.setStatus(ready ? text("Готово · на устройстве", "Ready · on device")
                        : downloading ? text("Модель загружается…", "Downloading model…") : text("Gemini Nano · нужна настройка", "Gemini Nano · setup needed"), ready);
                nanoAction.setText(downloading ? text("Загрузка…", "Downloading…") : downloadable ? text("Скачать модель", "Download model") : text("Проверить доступность модели", "Check model availability"));
                ((View) nanoFallback.getParent()).setVisibility(!ready && !downloading && (preview || fast) ? View.VISIBLE : View.GONE);
                if (downloading && !nanoPaused) AndroidUtilities.runOnUIThread(nanoPoll, 3000);
            });
        }, "NebulaNanoCheck");
        nanoWorker.start();
    }
    private void nanoBytes(int request, long loaded, long total) {
        AndroidUtilities.runOnUIThread(() -> {
            if (!currentNano(request)) return;
            if (total >= 0) nanoTotal = total;
            nanoProgressBar.setIndeterminate(nanoTotal <= 0);
            String amount = android.text.format.Formatter.formatShortFileSize(ApplicationLoader.applicationContext, loaded);
            if (nanoTotal > 0) {
                int percent = (int) Math.min(99, loaded * 100.0 / nanoTotal);
                nanoProgressBar.setProgress(percent);
                amount += " / " + android.text.format.Formatter.formatShortFileSize(ApplicationLoader.applicationContext, nanoTotal) + " · " + percent + "%";
            }
            nanoStatus.setText(text("Загрузка модели · ", "Downloading model · ") + amount);
            nanoAction.setText(text("Загрузка…", "Downloading…"));
            hero.setStatus(text("Модель загружается…", "Downloading model…"), false);
        });
    }
    @Override public void onResume() {
        super.onResume(); nanoPaused = false;
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        if (nanoWorker == null && nanoStatus != null && provider == NebulaAiClient.NANO) refreshNanoStatus();
        if (chat != null) chat.refreshStatus();
    }
    @Override public void onPause() {
        nanoPaused = true; AndroidUtilities.cancelRunOnUIThread(nanoPoll); super.onPause();
    }
    private void nanoProgress(int request, String message) {
        AndroidUtilities.runOnUIThread(() -> { if (currentNano(request)) { nanoStatus.setText(message); nanoAction.setText(text("Загрузка…", "Downloading…")); } });
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
            if (chat != null) chat.refreshStatus();
            return true;
        } catch (Exception e) { toast(text("Не удалось сохранить ключ или адрес API", "Unable to save key or API URL")); return false; }
    }
    private void loadModels() {
        if (client != null || !save() || provider == NebulaAiClient.NANO) return;
        final String secret;
        try { secret = NebulaAiSecrets.read(provider); }
        catch (Exception e) { toast(text("Введите API-ключ заново", "Enter your API key again")); return; }
        if (secret.isEmpty()) { toast(text("Введите API-ключ", "Enter an API key")); return; }
        final int selected = provider;
        final String url = endpoint.getText().toString();
        final NebulaAiClient task = client = new NebulaAiClient();
        load.setEnabled(false); load.setText(text("Загрузка моделей…", "Loading models…"));
        new Thread(() -> {
            try {
                final ArrayList<String> models = task.models(selected, url, secret);
                AndroidUtilities.runOnUIThread(() -> {
                    if (client != task || getParentActivity() == null) return;
                    finishRequest();
                    if (models.isEmpty()) toast(text("Список пуст. Введите имя модели вручную.", "The list is empty. Enter a model name manually."));
                    else showDialog(new NebulaDialog.Builder(getParentActivity()).setTitle(text("Модель", "Model"))
                        .setSelectedIndex(models.indexOf(model.getText().toString())).setItems(models.toArray(new String[0]), (d, i) -> { model.setText(models.get(i)); save(); }).create());
                });
            } catch (Exception e) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (client != task || getParentActivity() == null) return;
                    finishRequest(); toast(text("Не удалось загрузить модели. Проверьте подключение и ключ.", "Could not load models. Check the connection and key."));
                });
            }
        }, "NebulaAiModels").start();
    }
    private void finishRequest() { client = null; load.setEnabled(true); load.setText(text("Выбрать модель из списка", "Choose an available model")); }
    private void cancel() { if (client != null) { client.cancel(); finishRequest(); } }
    private void toast(String message) { if (getParentActivity() != null) Toast.makeText(getParentActivity(), message, Toast.LENGTH_SHORT).show(); }
    @Override public void onFragmentDestroy() { destroyed = true; AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid); invalidateNanoCheck(); cancel(); if (chat != null) chat.dispose(); if (key != null) key.setText(""); super.onFragmentDestroy(); }
}
