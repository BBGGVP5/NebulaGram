package app.nebulagram.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EditTextBoldCursor;

/** "Pill Stack": the switch, the list of pills, templates, import, coordinates and secrets. */
public final class NebulaPillsFragment extends BaseFragment {
    private LinearLayout column;

    private static String t(String ru, String en) { return NebulaText.text(ru, en); }

    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, t("Пилюли в поиске", "Pill Stack"));
        column = NebulaFormUi.column(c);
        rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, column));
    }

    @Override public void onResume() {
        super.onResume();
        if (column != null) rebuild();
    }

    private void rebuild() {
        Context c = column.getContext();
        column.removeAllViews();
        NebulaCard main = new NebulaCard(c);
        NebulaRow enabled = new NebulaRow(c).title(t("Показывать пилюли", "Show pills")).trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaPills.enabled());
        enabled.withClick(v -> { NebulaPills.setEnabled(enabled.toggleChecked()); rebuild(); });
        main.add(enabled);
        NebulaRow infinite = new NebulaRow(c).title(t("Бесконечная прокрутка", "Infinite scrolling"))
                .subtitle(t("Все пилюли в ряд, листаются пальцем; без неё видна первая", "All pills in a row you can swipe; without it only the first shows"), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaPills.infinite());
        infinite.withClick(v -> NebulaPills.setInfinite(infinite.toggleChecked()));
        main.add(infinite);
        NebulaFormUi.group(column, t("Настройки", "Settings"), main);
        column.addView(NebulaFormUi.note(c, t(
                "Пилюли стоят справа в поле поиска на главной. Каждая может раз в выбранный интервал запрашивать указанный вами адрес, пока приложение открыто. Адреса и ответы никуда, кроме этого адреса, не отправляются.",
                "Pills sit on the right of the home search field. Each one may ask the address you set once per chosen interval while the app is open. Nothing is sent anywhere except that address.")));

        NebulaCard list = new NebulaCard(c);
        List<NebulaPill> pills = NebulaPills.all();
        for (NebulaPill pill : pills) {
            NebulaPillFormat.Response response = NebulaPills.response(pill.id);
            list.add(new NebulaRow(c).title(pill.name.isEmpty() ? t("Без названия", "Untitled") : pill.name)
                    .subtitle(pill.shown(response, System.currentTimeMillis()), false).badge(" ", pill.color).trailing(NebulaRow.TRAIL_CHEVRON)
                    .withClick(v -> presentFragment(new NebulaPillEditorFragment(copy(pill)))));
        }
        if (pills.size() < 12) list.add(new NebulaRow(c).title(t("Новая пилюля", "New pill")).trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> templates(c)));
        list.add(new NebulaRow(c).title(t("Импорт из буфера обмена", "Import from clipboard")).withClick(v -> importClipboard(c)));
        NebulaFormUi.group(column, t("Пилюли", "Pills"), list);

        NebulaCard extra = new NebulaCard(c);
        extra.add(new NebulaRow(c).title(t("Координаты для {lat} и {lon}", "Coordinates for {lat} and {lon}"))
                .value(NebulaPills.latitude().isEmpty() ? t("Не заданы", "Not set") : NebulaPills.latitude() + ", " + NebulaPills.longitude())
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> location(c)));
        extra.add(new NebulaRow(c).title(t("Секреты", "Secrets")).value(String.valueOf(NebulaPills.secrets().size()))
                .trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> secrets(c)));
        NebulaFormUi.group(column, t("Подстановки", "Substitutions"), extra);
        column.addView(NebulaFormUi.note(c, t(
                "Секреты — например токен API — хранятся только на устройстве и не попадают в скопированные пилюли. В запросе они пишутся как {secret.name}.",
                "Secrets such as an API token stay on this device and are left out of copied pills. In a request they are written as {secret.name}.")));
    }

    private static NebulaPill copy(NebulaPill pill) {
        try { return NebulaPill.of(pill.json(), true); } catch (Exception e) { return pill; }
    }

    private void templates(Context c) {
        boolean ru = "ru".equals(t("ru", "en"));
        String[] names = new String[NebulaPill.TEMPLATES];
        String[] hints = {t("С нуля", "Start from scratch"), "CoinGecko", "open.er-api.com", "api.github.com",
                t("Код ответа и задержка", "Response code and latency"), "Open-Meteo", "toncenter.com", t("Без запросов", "No requests")};
        for (int i = 0; i < names.length; i++) names[i] = i == 0 ? t("Пустая", "Blank") : NebulaPill.template(i, ru).name;
        showDialog(new NebulaDialog.Builder(c).setTitle(t("Шаблоны", "Templates")).setItems(names, (d, which) ->
                presentFragment(new NebulaPillEditorFragment(NebulaPill.template(which, ru)))).setDescriptions(hints).create());
    }

    private void importClipboard(Context c) {
        ClipboardManager clipboard = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        CharSequence text = clip != null && clip.getItemCount() > 0 ? clip.getItemAt(0).coerceToText(c) : null;
        NebulaPill pill = NebulaPillShare.parse(text == null ? null : text.toString());
        if (pill == null) {
            Toast.makeText(c, t("В буфере нет пилюли", "No pill in the clipboard"), Toast.LENGTH_SHORT).show();
            return;
        }
        // An imported pill opens in the editor first, so its address is seen before it is saved.
        presentFragment(new NebulaPillEditorFragment(pill));
    }

    private void location(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(4), AndroidUtilities.dp(20), 0);
        EditTextBoldCursor lat = NebulaFormUi.field(c, t("Широта, например 55.75", "Latitude, e.g. 51.5"), 1, 16);
        lat.setText(NebulaPills.latitude());
        EditTextBoldCursor lon = NebulaFormUi.field(c, t("Долгота, например 37.62", "Longitude, e.g. -0.12"), 1, 16);
        lon.setText(NebulaPills.longitude());
        box.addView(lat, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = AndroidUtilities.dp(8);
        box.addView(lon, params);
        showDialog(new NebulaDialog.Builder(c).setTitle(t("Координаты", "Coordinates")).setView(box)
                .setPositiveButton(t("Готово", "Done"), (d, w) -> {
                    NebulaPills.setLocation(NebulaPillShare.coordinate(lat.getText().toString(), 90), NebulaPillShare.coordinate(lon.getText().toString(), 180));
                    rebuild();
                }).setNegativeButton(t("Отмена", "Cancel"), null).create());
    }

    private void secrets(Context c) {
        EditTextBoldCursor field = NebulaFormUi.field(c, t("имя=значение, по одному в строке", "name=value, one per line"), 4, 4096);
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> e : NebulaPills.secrets().entrySet()) text.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        field.setText(text.toString().trim());
        LinearLayout box = new LinearLayout(c);
        box.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(4), AndroidUtilities.dp(20), 0);
        box.addView(field, new LinearLayout.LayoutParams(-1, -2));
        showDialog(new NebulaDialog.Builder(c).setTitle(t("Секреты", "Secrets")).setView(box)
                .setPositiveButton(t("Готово", "Done"), (d, w) -> { NebulaPills.setSecrets(NebulaPillShare.secrets(field.getText().toString())); rebuild(); })
                .setNegativeButton(t("Отмена", "Cancel"), null).create());
    }
}
