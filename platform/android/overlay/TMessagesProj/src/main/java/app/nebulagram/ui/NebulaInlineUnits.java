package app.nebulagram.ui;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Length, weight, temperature, speed and time. Without a target the value moves to the other measurement system. */
public final class NebulaInlineUnits {
    private NebulaInlineUnits() { }
    static final int LENGTH = 0, WEIGHT = 1, TEMPERATURE = 2, SPEED = 3, TIME = 4;
    static final int NEUTRAL = 0, METRIC = 1, IMPERIAL = 2;

    static final class Unit {
        final String id, en, ru; final int kind, system; final double factor;
        Unit(String id, int kind, int system, double factor, String en, String ru) { this.id = id; this.kind = kind; this.system = system; this.factor = factor; this.en = en; this.ru = ru; }
        String label(boolean russian) { return russian ? ru : en; }
    }
    private static final Map<String, Unit> UNITS = new HashMap<>();
    private static final Map<String, Unit> BY_ID = new HashMap<>();
    private static Unit unit(String id, int kind, int system, double factor, String en, String ru, String... names) {
        Unit u = new Unit(id, kind, system, factor, en, ru);
        BY_ID.put(id, u); UNITS.put(id, u); UNITS.put(en.toLowerCase(Locale.ROOT), u); UNITS.put(ru.toLowerCase(Locale.ROOT), u);
        for (String name : names) UNITS.put(name, u);
        return u;
    }
    static {
        unit("mm", LENGTH, METRIC, 0.001, "mm", "мм", "millimeter", "millimeters", "millimetre", "millimetres", "миллиметр", "миллиметра", "миллиметров");
        unit("cm", LENGTH, METRIC, 0.01, "cm", "см", "centimeter", "centimeters", "centimetre", "centimetres", "сантиметр", "сантиметра", "сантиметров");
        unit("m", LENGTH, METRIC, 1, "m", "м", "meter", "meters", "metre", "metres", "метр", "метра", "метров");
        unit("km", LENGTH, METRIC, 1000, "km", "км", "kilometer", "kilometers", "kilometre", "kilometres", "километр", "километра", "километров");
        unit("in", LENGTH, IMPERIAL, 0.0254, "in", "дюйм.", "\"", "inch", "inches", "дюйм", "дюйма", "дюймов");
        unit("ft", LENGTH, IMPERIAL, 0.3048, "ft", "фут.", "'", "foot", "feet", "фут", "фута", "футов");
        unit("yd", LENGTH, IMPERIAL, 0.9144, "yd", "ярд.", "yard", "yards", "ярд", "ярда", "ярдов");
        unit("mi", LENGTH, IMPERIAL, 1609.344, "mi", "миль", "mile", "miles", "миля", "мили");
        unit("nmi", LENGTH, NEUTRAL, 1852, "nmi", "мор. миль", "nautical mile", "nautical miles", "морская миля", "морских миль", "морские мили");
        unit("mg", WEIGHT, METRIC, 1e-6, "mg", "мг", "milligram", "milligrams", "миллиграмм", "миллиграмма", "миллиграммов");
        unit("g", WEIGHT, METRIC, 0.001, "g", "г", "gram", "grams", "gramme", "grammes", "грамм", "грамма", "граммов", "гр");
        unit("kg", WEIGHT, METRIC, 1, "kg", "кг", "kilogram", "kilograms", "kilo", "kilos", "килограмм", "килограмма", "килограммов", "кило");
        unit("t", WEIGHT, METRIC, 1000, "t", "т", "tonne", "tonnes", "тонна", "тонны", "тонн");
        unit("oz", WEIGHT, IMPERIAL, 0.028349523125, "oz", "унц.", "ounce", "ounces", "унция", "унции", "унций");
        unit("lb", WEIGHT, IMPERIAL, 0.45359237, "lb", "фнт", "lbs", "pound", "pounds", "фунт", "фунта", "фунтов");
        unit("st", WEIGHT, IMPERIAL, 6.35029318, "st", "стоун", "stone", "stones", "стоуна", "стоунов");
        unit("c", TEMPERATURE, METRIC, 0, "°C", "°C", "°c", "℃", "celsius", "цельсий", "цельсия", "по цельсию", "градусов цельсия");
        unit("f", TEMPERATURE, IMPERIAL, 0, "°F", "°F", "°f", "℉", "fahrenheit", "фаренгейт", "фаренгейта", "по фаренгейту", "градусов фаренгейта");
        unit("k", TEMPERATURE, NEUTRAL, 0, "K", "K", "kelvin", "kelvins", "кельвин", "кельвина", "кельвинов");
        unit("m/s", SPEED, METRIC, 1, "m/s", "м/с", "mps", "meters per second", "метров в секунду");
        unit("km/h", SPEED, METRIC, 1 / 3.6, "km/h", "км/ч", "kmh", "kph", "kmph", "километров в час");
        unit("mph", SPEED, IMPERIAL, 0.44704, "mph", "миль/ч", "mi/h", "miles per hour", "миль в час");
        unit("kn", SPEED, NEUTRAL, 1852 / 3600.0, "kn", "уз", "knot", "knots", "kt", "узел", "узла", "узлов");
        unit("ft/s", SPEED, IMPERIAL, 0.3048, "ft/s", "фут/с", "fps");
        unit("ms", TIME, NEUTRAL, 0.001, "ms", "мс", "millisecond", "milliseconds", "миллисекунда", "миллисекунды", "миллисекунд");
        unit("s", TIME, NEUTRAL, 1, "s", "с", "sec", "secs", "second", "seconds", "сек", "секунда", "секунды", "секунд", "секунду");
        unit("min", TIME, NEUTRAL, 60, "min", "мин", "mins", "minute", "minutes", "минута", "минуты", "минут", "минуту");
        unit("h", TIME, NEUTRAL, 3600, "h", "ч", "hr", "hrs", "hour", "hours", "час", "часа", "часов");
        unit("d", TIME, NEUTRAL, 86400, "d", "д", "day", "days", "дн", "день", "дня", "дней", "сутки", "суток");
        unit("wk", TIME, NEUTRAL, 604800, "wk", "нед.", "week", "weeks", "нед", "неделя", "недели", "недель", "неделю");
        unit("mo", TIME, NEUTRAL, 2629746, "mo", "мес.", "month", "months", "мес", "месяц", "месяца", "месяцев");
        unit("yr", TIME, NEUTRAL, 31556952, "yr", "г.", "year", "years", "y", "год", "года", "лет");
        UNITS.remove("k"); // "5k" is a thousand; Kelvin is the capital K or the word.
    }

    static Unit find(String token) {
        String t = token.trim().replaceAll("\\s+", " ");
        if (t.equals("K")) return BY_ID.get("k");
        if (t.equals("M")) return null; // million, not metres
        return UNITS.get(t.toLowerCase(Locale.ROOT));
    }

    static String convert(String body, NebulaInlineResults.Options o, boolean explicit) {
        if (explicit) {
            for (String[] split : NebulaInlineResults.splits(body)) {
                Unit target = find(split[1]);
                Object[] source = target == null ? null : amount(split[0], o);
                if (source != null && ((Unit) source[1]).kind == target.kind && source[1] != target) return show((BigDecimal) source[0], (Unit) source[1], target, o);
            }
            return "";
        }
        Object[] source = amount(body, o);
        if (source == null) return "";
        Unit from = (Unit) source[1]; BigDecimal value = (BigDecimal) source[0];
        if (from.kind == TIME) return duration(value.doubleValue() * from.factor, from, o.russian);
        int system = from.system == NEUTRAL ? (o.imperial ? IMPERIAL : METRIC) : from.system == METRIC ? IMPERIAL : METRIC;
        if (from.kind == SPEED && from.system == NEUTRAL) system = o.imperial ? IMPERIAL : METRIC;
        return show(value, from, best(from.kind, system, value.doubleValue() * from.factor), o);
    }
    private static Unit best(int kind, int system, double base) {
        double a = Math.abs(base);
        switch (kind) {
            case LENGTH: return BY_ID.get(system == METRIC ? (a >= 1000 ? "km" : a >= 1 ? "m" : a >= 0.01 ? "cm" : "mm") : (a >= 1609.344 ? "mi" : a >= 0.3048 ? "ft" : "in"));
            case WEIGHT: return BY_ID.get(system == METRIC ? (a >= 1000 ? "t" : a >= 1 ? "kg" : a >= 0.001 ? "g" : "mg") : (a >= 0.45359237 ? "lb" : "oz"));
            case TEMPERATURE: return BY_ID.get(system == METRIC ? "c" : "f");
            default: return BY_ID.get(system == METRIC ? "km/h" : "mph");
        }
    }
    private static String show(BigDecimal value, Unit from, Unit to, NebulaInlineResults.Options o) {
        double v = value.doubleValue(), result;
        if (from.kind == TEMPERATURE) {
            double kelvin = from.id.equals("c") ? v + 273.15 : from.id.equals("f") ? (v - 32) * 5 / 9 + 273.15 : v;
            if (kelvin < 0) return "";
            result = to.id.equals("c") ? kelvin - 273.15 : to.id.equals("f") ? (kelvin - 273.15) * 9 / 5 + 32 : kelvin;
        } else result = v * from.factor / to.factor;
        String number = NebulaInlineResults.approximate(result, o.group);
        return number.isEmpty() ? "" : number + (to.kind == TEMPERATURE && !to.id.equals("k") ? "" : " ") + to.label(o.russian);
    }
    /** "90 min" → "1 h 30 min": the two largest parts, rounded to the smaller one. */
    private static String duration(double seconds, Unit from, boolean russian) {
        String[] ids = {"yr", "d", "h", "min", "s"};
        long total = Math.round(seconds);
        if (total < 60 || seconds > 1e12) return "";
        for (int i = 0; i < ids.length; i++) {
            Unit big = BY_ID.get(ids[i]);
            long count = total / (long) big.factor;
            if (count == 0) continue;
            StringBuilder out = new StringBuilder().append(count).append(' ').append(big.label(russian));
            if (i + 1 < ids.length) {
                Unit small = BY_ID.get(ids[i + 1]);
                long rest = Math.round((total - count * (long) big.factor) / small.factor);
                if (rest * small.factor >= big.factor) { count++; rest = 0; out.setLength(0); out.append(count).append(' ').append(big.label(russian)); }
                if (rest > 0) out.append(' ').append(rest).append(' ').append(small.label(russian));
            }
            if (big == from && out.indexOf(" ", out.indexOf(" ") + 1) < 0) return "";
            return out.toString();
        }
        return "";
    }
    /** {amount, unit}: "5 km", "5km", "100°F", "3,5 кг". */
    private static Object[] amount(String text, NebulaInlineResults.Options o) {
        String s = text.trim();
        for (int i = Math.max(1, s.length() - 32); i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c) || Character.isWhitespace(c) || c == '.' || c == ',') continue;
            if (Character.isLetter(c) && Character.isLetter(s.charAt(i - 1))) continue;
            Unit unit = find(s.substring(i));
            if (unit == null) continue;
            BigDecimal value = NebulaMath.value(s.substring(0, i), o.degrees);
            if (value != null) return new Object[]{value, unit};
        }
        return null;
    }
}
