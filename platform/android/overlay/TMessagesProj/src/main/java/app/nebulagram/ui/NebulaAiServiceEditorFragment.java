package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import java.util.ArrayList;

public final class NebulaAiServiceEditorFragment extends BaseFragment {
    private static final int[] PROVIDERS = {0, 2, 1, 5, 6, 3};
    private final String id;
    private int provider = NebulaAiClient.OPENAI;
    private EditText name, endpoint, model, key;
    private NebulaAiServices.Service original;
    private NebulaAiClient loading;
    private boolean destroyed;
    public NebulaAiServiceEditorFragment(String id) { this.id = id; }
    @Override public View createView(Context c) {
        original = id == null ? null : NebulaAiServices.find(id);
        if (original != null) provider = original.provider;
        NebulaFormUi.bar(this, actionBar, c, original == null ? text("Новый сервис", "New service") : text("Изменить сервис", "Edit service"));
        LinearLayout content = NebulaFormUi.column(c);
        NebulaCard providers = new NebulaCard(c); ArrayList<NebulaRow> rows = new ArrayList<>();
        for (int choice : PROVIDERS) {
            NebulaRow row = new NebulaRow(c).title(NebulaAiServices.providerName(choice)).radio(choice == provider).withClick(v -> {
                provider = choice; for (int i = 0; i < rows.size(); i++) rows.get(i).radio(PROVIDERS[i] == choice);
                endpoint.setVisibility(choice == NebulaAiClient.CUSTOM ? View.VISIBLE : View.GONE);
                if (original == null && name.getText().toString().trim().isEmpty()) name.setText(NebulaAiServices.providerName(choice));
                if (loading != null) { loading.cancel(); loading = null; }
            }); rows.add(row); providers.add(row);
        }
        NebulaFormUi.group(content, text("Провайдер", "Provider"), providers);
        NebulaCard fields = new NebulaCard(c);
        name = field(c, fields, text("Название сервиса", "Service name"), original == null ? "" : original.name, 64);
        endpoint = field(c, fields, text("Адрес API · https://…/v1", "API address · https://…/v1"), original == null ? "https://example.com/v1" : original.endpoint, 1000);
        endpoint.setVisibility(provider == NebulaAiClient.CUSTOM ? View.VISIBLE : View.GONE);
        model = field(c, fields, text("ID модели", "Model ID"), original == null ? "" : original.model, 256);
        key = field(c, fields, original == null ? text("API-ключ", "API key") : text("Новый API-ключ · пусто = оставить прежний", "New API key · blank keeps the saved key"), "", 10000);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); key.setTypeface(android.graphics.Typeface.DEFAULT);
        if (android.os.Build.VERSION.SDK_INT >= 26) key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        NebulaFormUi.group(content, text("Данные сервиса", "Connection"), fields);
        NebulaButton availableModels = new NebulaButton(c, NebulaButton.STYLE_TEXT);
        availableModels.setText(text("Выбрать модель из списка", "Choose an available model")); availableModels.setOnClickListener(v -> models(c)); content.addView(availableModels);
        content.addView(NebulaFormUi.primary(c, text("Сохранить", "Save"), v -> {
            try {
                String secret = secret();
                if (name.getText().toString().trim().isEmpty() || model.getText().toString().trim().isEmpty() || secret.isEmpty()) { toast(c, text("Укажите название, модель и API-ключ.", "Enter a name, model and API key.")); return; }
                NebulaAiServices.save(id, name.getText().toString(), provider, endpoint.getText().toString(), model.getText().toString(), secret); finishFragment();
            } catch (Exception e) { toast(c, text("Проверьте адрес HTTPS и ключ сервиса.", "Check the HTTPS address and service key.")); }
        }));
        content.addView(NebulaFormUi.note(c, text("После сохранения выберите сервис в списке. Запросы отправляются выбранному провайдеру с вашим ключом.", "After saving, select the service from the list. Requests use your key and the selected provider.")));
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, content), -12);
    }
    private EditText field(Context c, NebulaCard card, String hint, String value, int limit) {
        EditText result = NebulaFormUi.cardField(c, hint, 1, limit); result.setText(value); card.add(result); return result;
    }
    private String secret() throws Exception {
        String value = key.getText().toString().trim();
        return value.isEmpty() && original != null && original.provider == provider ? NebulaAiServices.key(original.id) : value;
    }
    private void models(Context c) {
        if (loading != null) return;
        final String secret, address = endpoint.getText().toString(); final int capturedProvider = provider;
        try { secret = secret(); if (secret.isEmpty()) { toast(c, text("Сначала введите API-ключ.", "Enter an API key first.")); return; } NebulaAiClient.base(provider, address); }
        catch (Exception e) { toast(c, text("Проверьте подключение.", "Check the connection.")); return; }
        final NebulaAiClient task = loading = new NebulaAiClient();
        new Thread(() -> {
            try {
                ArrayList<String> available = task.models(capturedProvider, address, secret);
                AndroidUtilities.runOnUIThread(() -> {
                    if (destroyed || loading != task || provider != capturedProvider) return; loading = null;
                    if (available.isEmpty()) { toast(c, text("Список пуст. Введите ID модели вручную.", "The list is empty. Enter a model ID manually.")); return; }
                    showDialog(new NebulaDialog.Builder(c).setTitle(text("Модель", "Model")).setSelectedIndex(available.indexOf(model.getText().toString().trim())).setItems(available.toArray(new String[0]), (d, which) -> model.setText(available.get(which))).create());
                });
            } catch (Exception e) { AndroidUtilities.runOnUIThread(() -> { if (!destroyed && loading == task) { loading = null; toast(c, text("Не удалось загрузить модели. Проверьте ключ или введите ID вручную.", "Could not load models. Check your key or enter the ID manually.")); } }); }
        }, "NebulaServiceModels").start();
    }
    private void toast(Context c, String value) { Toast.makeText(c, value, Toast.LENGTH_LONG).show(); }
    @Override public void onFragmentDestroy() { destroyed = true; if (loading != null) loading.cancel(); super.onFragmentDestroy(); }
}
