package app.nebulagram.ui;

import static app.nebulagram.ui.NebulaText.text;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;

public final class NebulaAiServicesFragment extends BaseFragment {
    private LinearLayout content;
    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, text("Сервисы", "Services"));
        content = NebulaFormUi.column(c); rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, content), -12);
    }
    @Override public void onResume() { super.onResume(); if (content != null) rebuild(); }
    private void rebuild() {
        Context c = content.getContext(); content.removeAllViews();
        content.addView(new NebulaSettingsHero(c, "🔑", text("Сервисы", "Services"), text("Выберите подключение для работы с ИИ.", "Choose a connection for AI.")));
        NebulaCard card = new NebulaCard(c);
        for (NebulaAiServices.Service service : NebulaAiServices.list()) {
            boolean nano = service.provider == NebulaAiClient.NANO;
            NebulaRow row = new NebulaRow(c).title(service.name).radio(service.id.equals(NebulaAiServices.selected()))
                    .subtitle(nano ? text("На устройстве · удерживайте для настройки модели", "On device · hold for model settings")
                            : NebulaAiServices.providerName(service.provider) + " · " + (service.model.isEmpty() ? text("выберите модель", "choose a model") : service.model), false)
                    .withClick(v -> { try { NebulaAiServices.select(service.id); rebuild(); } catch (Exception e) { error(c); } });
            row.setOnLongClickListener(v -> {
                if (nano) { presentFragment(new NebulaAiFragment().openNanoConnection()); return true; }
                showDialog(new NebulaDialog.Builder(c).setTitle(service.name).setItems(new CharSequence[]{text("Изменить", "Edit"), text("Удалить", "Delete")}, (d, which) -> {
                    if (which == 0) presentFragment(new NebulaAiServiceEditorFragment(service.id));
                    else showDialog(new NebulaDialog.Builder(c).setTitle(text("Удалить сервис?", "Delete service?"))
                            .setMessage(text("Ключ этого подключения будет удалён с устройства.", "This connection's key will be removed from this device."))
                            .setPositiveButton(text("Удалить", "Delete"), (a, b) -> { try { NebulaAiServices.delete(service.id); rebuild(); } catch (Exception e) { error(c); } }).create());
                }).create()); return true;
            }); card.add(row);
        }
        card.add(new NebulaRow(c).icon(R.drawable.msg_add).title(text("Новый сервис", "New service")).withClick(v -> presentFragment(new NebulaAiServiceEditorFragment(null))));
        NebulaFormUi.group(content, text("Сервисы", "Services"), card);
        content.addView(NebulaFormUi.note(c, text("Удерживайте сервис, чтобы изменить или удалить его. Ключи хранятся отдельно в защищённом хранилище Android.", "Hold a service to edit or delete it. Keys are stored separately in Android's protected storage.")));
    }
    private void error(Context c) { Toast.makeText(c, text("Не удалось прочитать ключ подключения. Введите его заново.", "Could not read this connection's key. Enter it again."), Toast.LENGTH_LONG).show(); }
}
