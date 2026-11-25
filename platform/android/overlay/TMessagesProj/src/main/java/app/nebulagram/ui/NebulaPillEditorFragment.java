package app.nebulagram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EditTextBoldCursor;

/** "New pill" / "Edit pill": appearance, data source, text with {values}, conditions and the tap action. */
public final class NebulaPillEditorFragment extends BaseFragment {
    private static final int DONE = 1;
    private final NebulaPill pill;
    private LinearLayout column;
    private FrameLayout previewHost;
    private NebulaPillFormat.Response tested;

    public NebulaPillEditorFragment(NebulaPill pill) { this.pill = pill; tested = NebulaPills.response(pill.id); }

    private static String t(String ru, String en) { return NebulaText.text(ru, en); }

    @Override public View createView(Context c) {
        boolean exists = false;
        for (NebulaPill p : NebulaPills.all()) if (p.id.equals(pill.id)) exists = true;
        NebulaFormUi.bar(this, actionBar, c, exists ? t("Пилюля", "Pill") : t("Новая пилюля", "New pill"));
        actionBar.createMenu().addItem(DONE, R.drawable.ic_ab_done).setContentDescription(t("Сохранить", "Save"));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == DONE) { NebulaPills.put(pill); finishFragment(); }
            }
        });
        column = NebulaFormUi.column(c);
        rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, column));
    }

    private EditTextBoldCursor field(Context c, String hint, String value, int lines, int limit, java.util.function.Consumer<String> set) {
        EditTextBoldCursor field = NebulaFormUi.cardField(c, hint, lines, limit);
        field.setText(value);
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable s) { set.accept(s.toString()); preview(); }
        });
        return field;
    }

    private void preview() {
        if (previewHost == null) return;
        previewHost.removeAllViews();
        TextView view = NebulaPills.pillView(previewHost.getContext(), pill, tested);
        previewHost.addView(view, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(30), Gravity.CENTER));
    }

    private void rebuild() {
        Context c = column.getContext();
        column.removeAllViews();
        previewHost = new FrameLayout(c);
        column.addView(previewHost, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(52)));
        preview();

        NebulaCard look = new NebulaCard(c);
        look.add(field(c, t("Название", "Name"), pill.name, 1, 64, v -> pill.name = v));
        LinearLayout colors = new LinearLayout(c);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        colors.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        android.widget.HorizontalScrollView colorScroll = new android.widget.HorizontalScrollView(c);
        colorScroll.setHorizontalScrollBarEnabled(false);
        for (int color : NebulaPill.COLORS) {
            View dot = new View(c);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(color);
            if (color == pill.color) shape.setStroke(AndroidUtilities.dp(3), NebulaTheme.of(c).onSurface());
            dot.setBackground(shape);
            dot.setOnClickListener(v -> { pill.color = color; rebuild(); });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(AndroidUtilities.dp(36), AndroidUtilities.dp(36));
            params.rightMargin = AndroidUtilities.dp(8);
            colors.addView(dot, params);
        }
        colorScroll.addView(colors);
        look.add(colorScroll);
        CharSequence[] icons = NebulaPill.ICONS;
        int iconIndex = 0;
        for (int i = 0; i < icons.length; i++) if (icons[i].equals(pill.icon)) iconIndex = i;
        final int chosenIcon = iconIndex;
        look.add(new NebulaRow(c).title(t("Значок", "Icon")).subtitle(pill.icon, true).trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> showDialog(new NebulaDialog.Builder(c).setTitle(t("Значок", "Icon")).setSelectedIndex(chosenIcon)
                        .setItems(icons, (d, which) -> { pill.icon = NebulaPill.ICONS[which]; rebuild(); }).create())));
        NebulaFormUi.group(column, t("Внешний вид", "Appearance"), look);
        column.addView(NebulaFormUi.note(c, t("Название видно только в настройках пилюль. В поле поиска показывается текст пилюли.",
                "The name is only shown in pill settings. The search field shows the pill text set below.")));

        NebulaCard source = new NebulaCard(c);
        source.add(new NebulaRow(c).title(t("Без запроса", "No request")).radio(!pill.request).withClick(v -> { pill.request = false; rebuild(); }));
        source.add(new NebulaRow(c).title(t("Веб-запрос", "Web request")).radio(pill.request).withClick(v -> { pill.request = true; rebuild(); }));
        if (pill.request) {
            EditTextBoldCursor url = field(c, "https://", pill.url, 1, 2048, v -> pill.url = v);
            url.setTypeface(android.graphics.Typeface.MONOSPACE);
            source.add(url);
            source.add(new NebulaRow(c).title(t("Метод", "Method")).value(pill.method).trailing(NebulaRow.TRAIL_CHEVRON)
                    .withClick(v -> { pill.method = "GET".equals(pill.method) ? "POST" : "GET"; rebuild(); }));
            source.add(field(c, t("Заголовки: Имя: значение, по одному в строке", "Headers: Name: value, one per line"), pill.headers, 2, 4096, v -> pill.headers = v));
            if ("POST".equals(pill.method)) source.add(field(c, t("Тело запроса", "Request body"), pill.body, 3, 8192, v -> pill.body = v));
            String[] intervals = new String[NebulaPill.INTERVALS.length];
            int selected = 0;
            for (int i = 0; i < intervals.length; i++) {
                intervals[i] = interval(NebulaPill.INTERVALS[i]);
                if (NebulaPill.INTERVALS[i] == pill.interval) selected = i;
            }
            final int chosen = selected;
            source.add(new NebulaRow(c).title(t("Частота обновления", "Refresh interval")).value(interval(pill.interval)).trailing(NebulaRow.TRAIL_CHEVRON)
                    .withClick(v -> showDialog(new NebulaDialog.Builder(c).setTitle(t("Частота обновления", "Refresh interval")).setSelectedIndex(chosen)
                            .setItems(intervals, (d, which) -> { pill.interval = NebulaPill.INTERVALS[which]; rebuild(); }).create())));
            source.add(new NebulaRow(c).title(t("Отправить снова", "Send again"))
                    .value(tested == null ? "" : tested.code + " · " + tested.latency + " ms").withClick(v -> test()));
        }
        NebulaFormUi.group(column, t("Источник данных", "Data source"), source);
        if (pill.request) column.addView(NebulaFormUi.note(c, t(
                "В адресе, заголовках и теле можно использовать {lang}, {currency}, {lat}, {lon} и {secret.name}. По обычному HTTP секреты отправляются только на локальные адреса.",
                "You can use {lang}, {currency}, {lat}, {lon} and {secret.name} in the address, headers and body. Over plain HTTP, secrets are sent only to local addresses.")));

        NebulaCard text = new NebulaCard(c);
        text.add(field(c, t("Текст пилюли, например {rates.EUR|fixed:3} €", "Pill text, e.g. {rates.EUR|fixed:3} €"), pill.text, 1, 256, v -> pill.text = v));
        for (String value : NebulaPillFormat.placeholders(pill.text)) {
            String shown = tested == null && pill.request ? "—" : NebulaPillFormat.placeholder(value, tested, System.currentTimeMillis());
            text.add(new NebulaRow(c).title(value).leftToRightText().value(shown));
        }
        text.add(new NebulaRow(c).title(t("+ Код ответа, задержка, время", "+ Response code, latency, time")).withClick(v -> {
            pill.text = (pill.text + " {code} · {latency} ms").trim(); rebuild();
        }));
        NebulaFormUi.group(column, t("Текст пилюли", "Pill text"), text);
        column.addView(NebulaFormUi.note(c, t(
                "Значения в фигурных скобках обновляются с каждым ответом. Путь идёт по JSON: data[0].price. Фильтры через |: fixed:2, round, short, upper, lower, default:x, prefix:x, suffix:x, date, max:20. Без запроса работает {countdown:2026-12-31}.",
                "Values in braces update with every reply. The path walks the JSON: data[0].price. Filters after |: fixed:2, round, short, upper, lower, default:x, prefix:x, suffix:x, date, max:20. Without a request, {countdown:2026-12-31} works.")));

        NebulaCard rules = new NebulaCard(c);
        for (int i = 0; i < pill.conditions.size(); i++) {
            NebulaPill.Condition rule = pill.conditions.get(i);
            final int index = i;
            rules.add(new NebulaRow(c).title(rule.field + " " + rule.op + " " + rule.value).leftToRightText()
                    .subtitle(rule.text.isEmpty() ? t("Меняет цвет", "Changes the colour") : rule.text, false)
                    .badge(" ", rule.color == 0 ? pill.color : rule.color).withClick(v -> editRule(c, rule, index)));
        }
        if (pill.conditions.size() < 16) rules.add(new NebulaRow(c).title(t("Добавить условие", "Add condition")).withClick(v -> editRule(c, new NebulaPill.Condition(), -1)));
        NebulaFormUi.group(column, t("Условия", "Conditions"), rules);
        column.addView(NebulaFormUi.note(c, t("Первое совпавшее условие меняет цвет или текст. Например, пилюля краснеет, когда цена падает.",
                "The first matching condition changes the colour or text. For example, turn the pill red when the price drops.")));

        NebulaCard tap = new NebulaCard(c);
        String[] actions = {t("Показать подробности", "Show details"), t("Открыть ссылку", "Open link"), t("Копировать текст", "Copy text"), t("Обновить", "Refresh")};
        tap.add(new NebulaRow(c).title(t("Действие", "Action")).value(actions[pill.action]).trailing(NebulaRow.TRAIL_CHEVRON)
                .withClick(v -> showDialog(new NebulaDialog.Builder(c).setTitle(t("Действие", "Action")).setSelectedIndex(pill.action)
                        .setItems(actions, (d, which) -> { pill.action = which; rebuild(); }).create())));
        if (pill.action == NebulaPill.ACTION_OPEN) tap.add(field(c, "https://", pill.actionUrl, 1, 2048, v -> pill.actionUrl = v));
        NebulaFormUi.group(column, t("По нажатию", "On tap"), tap);
        column.addView(NebulaFormUi.note(c, t("Удержание пилюли всегда открывает меню: обновить, скопировать или изменить.",
                "Holding the pill always opens a menu to refresh, copy or edit it.")));

        boolean exists = false;
        for (NebulaPill p : NebulaPills.all()) if (p.id.equals(pill.id)) exists = true;
        if (exists) {
            NebulaCard danger = new NebulaCard(c);
            danger.add(new NebulaRow(c).title(t("Скопировать для обмена", "Copy for sharing")).withClick(v -> {
                try {
                    AndroidUtilities.addToClipboard(pill.json().toString());
                    Toast.makeText(c, t("Пилюля скопирована: её можно импортировать на другом устройстве. Секреты не копируются.",
                            "Pill copied: it can be imported elsewhere. Secrets are not included."), Toast.LENGTH_LONG).show();
                } catch (Exception ignored) { }
            }));
            danger.add(new NebulaRow(c).title(t("Удалить пилюлю", "Delete pill")).destructive().withClick(v -> { NebulaPills.remove(pill.id); finishFragment(); }));
            column.addView(danger, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private static String interval(int minutes) {
        if (minutes == 0) return t("Только вручную", "Manual only");
        if (minutes < 60) return minutes + t(" мин", " min");
        if (minutes < 1440) return minutes / 60 + t(" ч", " h");
        return minutes / 1440 + t(" д", " d");
    }

    private void test() {
        NebulaPills.fetch(pill, true, () -> {
            tested = NebulaPills.response(pill.id);
            if (column != null) rebuild();
        });
    }

    private void editRule(Context c, NebulaPill.Condition rule, int index) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(4), AndroidUtilities.dp(20), 0);
        EditTextBoldCursor field = NebulaFormUi.field(c, t("Значение, например rates.EUR или code", "Value, e.g. rates.EUR or code"), 1, 128);
        field.setText(rule.field);
        EditTextBoldCursor op = NebulaFormUi.field(c, "> >= < <= = != contains empty", 1, 8);
        op.setText(rule.op);
        EditTextBoldCursor value = NebulaFormUi.field(c, t("С чем сравнить", "Compare with"), 1, 128);
        value.setText(rule.value);
        EditTextBoldCursor text = NebulaFormUi.field(c, t("Свой текст (необязательно)", "Own text (optional)"), 1, 256);
        text.setText(rule.text);
        for (View v : new View[]{field, op, value, text}) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = AndroidUtilities.dp(8);
            box.addView(v, params);
        }
        NebulaDialog.Builder builder = new NebulaDialog.Builder(c).setTitle(t("Условие", "Condition")).setView(box)
                .setPositiveButton(t("Готово", "Done"), (d, w) -> {
                    rule.field = field.getText().toString().trim();
                    String o = op.getText().toString().trim();
                    rule.op = java.util.Arrays.asList(">", ">=", "<", "<=", "=", "!=", "contains", "empty").contains(o) ? o : ">";
                    rule.value = value.getText().toString().trim();
                    rule.text = text.getText().toString();
                    if (rule.color == 0) rule.color = NebulaPill.COLORS[1];
                    if (index < 0) pill.conditions.add(rule);
                    rebuild();
                })
                .setNeutralButton(t("Цвет", "Colour"), (d, w) -> {
                    int at = 0;
                    for (int i = 0; i < NebulaPill.COLORS.length; i++) if (NebulaPill.COLORS[i] == rule.color) at = i;
                    rule.color = NebulaPill.COLORS[(at + 1) % NebulaPill.COLORS.length];
                    if (index < 0) { rule.field = field.getText().toString().trim(); rule.value = value.getText().toString().trim(); pill.conditions.add(rule); }
                    rebuild();
                });
        if (index >= 0) builder.setNegativeButton(t("Удалить", "Delete"), (d, w) -> { pill.conditions.remove(index); rebuild(); });
        showDialog(builder.create());
    }
}
