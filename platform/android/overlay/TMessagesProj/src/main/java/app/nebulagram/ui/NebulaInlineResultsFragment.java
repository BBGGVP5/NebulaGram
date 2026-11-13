package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BaseFragment;

/** "Inline results": the switches, conversion settings and tap-to-copy examples of the gray result in the input field. */
public final class NebulaInlineResultsFragment extends BaseFragment {
    private LinearLayout content;
    private final boolean[] expanded = new boolean[4];
    private static String text(String ru, String en) { return NebulaText.text(ru, en); }

    @Override public View createView(Context c) {
        NebulaFormUi.bar(this, actionBar, c, text("Результаты в поле ввода", "Inline results"));
        content = NebulaFormUi.column(c); rebuild();
        return fragmentView = NebulaSettingsLayout.wrap(c, actionBar, NebulaFormUi.scroll(c, content), 3);
    }

    private NebulaRow toggle(Context c, String key, String ru, String en, String infoRu, String infoEn) {
        NebulaRow row = new NebulaRow(c).title(text(ru, en)).subtitle(text(infoRu, infoEn), false)
                .trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaInlineSettings.feature(key));
        row.withClick(v -> { NebulaInlineSettings.set(key, row.toggleChecked()); rebuild(); });
        return row;
    }

    private void rebuild() {
        Context c = content.getContext(); content.removeAllViews();
        content.addView(NebulaFormUi.note(c, text("Наберите выражение и «=» — результат появится серым. Пробел вставит его в текст, Backspace сразу после вставки вернёт выражение.",
                "Type an expression and \"=\" to see the result in gray. Space inserts it into the text; Backspace right after the insertion brings the expression back.")));
        NebulaCard card = new NebulaCard(c);
        NebulaRow master = new NebulaRow(c).title(text("Показывать результаты", "Show results")).trailing(NebulaRow.TRAIL_SWITCH).checked(NebulaInlineSettings.enabled());
        master.withClick(v -> { NebulaChatPreferences.set("inline_math", master.toggleChecked()); rebuild(); });
        card.add(master);
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        if (!NebulaInlineSettings.enabled()) return;

        card = new NebulaCard(c);
        card.add(toggle(c, "inline_calculator", "Калькулятор", "Calculator", "Скобки, степени, функции, проценты и системы счисления", "Parentheses, powers, functions, percentages and number bases"));
        card.add(toggle(c, "inline_currency", "Валюты", "Currencies", "Курсы open.er-api.com, обновляются раз в день", "Rates from open.er-api.com, refreshed daily"));
        card.add(toggle(c, "inline_units", "Единицы измерения", "Units of measure", "Длина, вес, температура, скорость и время", "Length, weight, temperature, speed and time"));
        card.add(toggle(c, "inline_timezones", "Часовые пояса", "Time zones", "Города, сокращения и смещения UTC", "Cities, abbreviations and UTC offsets"));
        NebulaFormUi.group(content, text("Функции", "Features"), card);

        card = new NebulaCard(c);
        card.add(toggle(c, "inline_group_digits", "Разделять разряды", "Group digits", "1" + NebulaMath.GROUP + "234" + NebulaMath.GROUP + "567 вместо 1234567", "1" + NebulaMath.GROUP + "234" + NebulaMath.GROUP + "567 instead of 1234567"));
        String[] angles = {text("Градусы", "Degrees"), text("Радианы", "Radians")};
        NebulaRow angle = new NebulaRow(c).title(text("Углы", "Angles")).subtitle(angles[NebulaInlineSettings.angle()], true).trailing(NebulaRow.TRAIL_CHEVRON);
        angle.withClick(v -> showDialog(new NebulaDialog.Builder(c).setTitle(text("Углы", "Angles")).setSelectedIndex(NebulaInlineSettings.angle())
                .setItems(angles, (d, which) -> { NebulaInlineSettings.angle(which); rebuild(); }).create()));
        card.add(angle);
        String[] codes = NebulaInlineCurrency.COMMON;
        CharSequence[] currencies = new CharSequence[codes.length + 1];
        currencies[0] = text("Авто — по номеру телефона", "Auto — by phone number");
        int selected = 0;
        for (int i = 0; i < codes.length; i++) {
            currencies[i + 1] = codes[i] + " · " + NebulaInlineCurrency.label(codes[i]);
            if (codes[i].equals(NebulaInlineSettings.defaultCurrency())) selected = i + 1;
        }
        String current = NebulaInlineSettings.defaultCurrency().isEmpty() ? text("Авто", "Auto") + " · " + NebulaInlineSettings.resolvedCurrency() : NebulaInlineSettings.defaultCurrency();
        NebulaRow currency = new NebulaRow(c).title(text("Валюта по умолчанию", "Default currency")).subtitle(current, true).trailing(NebulaRow.TRAIL_CHEVRON);
        final int chosen = selected;
        currency.withClick(v -> showDialog(new NebulaDialog.Builder(c).setTitle(text("Валюта по умолчанию", "Default currency")).setSelectedIndex(chosen)
                .setItems(currencies, (d, which) -> { NebulaInlineSettings.defaultCurrency(which == 0 ? "" : codes[which - 1]); rebuild(); }).create()));
        card.add(currency);
        String[] systems = {text("Авто", "Auto"), text("Метрическая", "Metric"), text("Имперская", "Imperial")};
        NebulaRow system = new NebulaRow(c).title(text("Система мер", "Measurement system"))
                .subtitle(systems[NebulaInlineSettings.measurement()] + (NebulaInlineSettings.measurement() == 0 ? " · " + systems[NebulaInlineSettings.imperial() ? 2 : 1] : ""), true).trailing(NebulaRow.TRAIL_CHEVRON);
        system.withClick(v -> showDialog(new NebulaDialog.Builder(c).setTitle(text("Система мер", "Measurement system")).setSelectedIndex(NebulaInlineSettings.measurement())
                .setItems(systems, (d, which) -> { NebulaInlineSettings.measurement(which); rebuild(); }).create()));
        card.add(system);
        NebulaFormUi.group(content, text("Настройки", "Settings"), card);

        content.addView(NebulaCard.header(c, text("Возможности", "Capabilities")));
        capability(c, 0, text("Калькулятор", "Calculator"), "2^10 =", "(15 + 5) * 3 =", "sqrt(144) + 2 =", "sin(30) =", "round(pi, 4) =", "max(4, 9, 2) =",
                "200 + 15% =", "15% of 80 =", "5k * 3 =", "2m / 4 =", "hex(255) =", "bin(10) =", "dec(ff, 16) =");
        capability(c, 1, text("Валюты", "Currencies"), "100€ =", "100 dollars to euros =", "5k ₽ to $ =", "$250 in uah =", "1000 рублей в тенге =");
        capability(c, 2, text("Единицы измерения", "Units of measure"), "5 km =", "6 ft to cm =", "70 kg in lb =", "100°F =", "120 km/h to mph =", "90 min =", "3 h to min =");
        capability(c, 3, text("Часовые пояса", "Time zones"), "15:00 msk in tokyo =", "3pm est to utc+3 =", "now in london =", "18:30 в нью-йорке в мск =", "токио =");
        content.addView(NebulaFormUi.note(c, text("Нажмите на пример, чтобы скопировать его.", "Tap an example to copy it.")));
    }

    private void capability(Context c, int index, String title, String... examples) {
        NebulaCard card = new NebulaCard(c);
        NebulaRow head = new NebulaRow(c).title(title).subtitle(expanded[index] ? text("Скрыть примеры", "Hide examples") : text("Примеры: ", "Examples: ") + examples.length, false).trailing(NebulaRow.TRAIL_CHEVRON);
        head.withClick(v -> { expanded[index] = !expanded[index]; rebuild(); });
        card.add(head);
        if (expanded[index]) {
            NebulaInlineResults.Options options = NebulaInlineSettings.options();
            for (String example : examples) {
                String result = NebulaInlineResults.compute(example, options);
                if (options.ratesWanted && options.rates == null) NebulaInlineSettings.load(() -> { if (content != null && fragmentView != null) rebuild(); });
                NebulaRow row = new NebulaRow(c).title(example).leftToRightText()
                        .subtitle(result.isEmpty() ? text("Нет данных", "Not available") : result, !result.isEmpty());
                row.withClick(v -> {
                    AndroidUtilities.addToClipboard(example);
                    Toast.makeText(c, text("Скопировано", "Copied"), Toast.LENGTH_SHORT).show();
                });
                card.add(row);
            }
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = NebulaShape.gap(8);
        content.addView(card, params);
    }
}
