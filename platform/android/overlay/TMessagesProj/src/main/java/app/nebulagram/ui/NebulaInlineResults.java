package app.nebulagram.ui;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java dispatcher behind the gray result in the input field; Android state arrives through Options. */
public final class NebulaInlineResults {
    private NebulaInlineResults() { }

    public static final class Options {
        public boolean calculator = true, currency = true, units = true, timezones = true;
        public boolean group, degrees = true, imperial, russian;
        public String defaultCurrency = "USD";
        /** Units of a currency per US dollar, or null until they are loaded. */
        public Map<String, Double> rates;
        public long now = System.currentTimeMillis();
        public TimeZone local = TimeZone.getDefault();
        /** Set when a currency expression was recognised but no rates were available. */
        public boolean ratesWanted;
    }

    private static final Pattern CONNECTOR = Pattern.compile("\\s+(?:to|in|into|в|во|на)(?=\\s)|\\s*(?:->|→|=>)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** The result for the last line of a draft, or "" when there is nothing to show. */
    public static String compute(CharSequence text, Options o) {
        if (text == null) return "";
        int length = text.length(), start = length;
        while (start > 0 && text.charAt(start - 1) != '\n' && length - start <= 160) start--;
        String line = text.subSequence(start, length).toString().trim();
        if (line.isEmpty() || line.length() > 120) return "";
        if (o.calculator) {
            String value = NebulaMath.result(line, o.degrees, o.group);
            if (!value.isEmpty()) return value;
        }
        if (!line.endsWith("=")) return "";
        String body = line.substring(0, line.length() - 1).trim();
        if (body.isEmpty()) return "";
        String value = "";
        if (o.units) value = NebulaInlineUnits.convert(body, o, true);
        if (value.isEmpty() && o.currency) value = NebulaInlineCurrency.convert(body, o);
        if (value.isEmpty() && o.timezones) value = NebulaInlineTime.convert(body, o);
        if (value.isEmpty() && o.units) value = NebulaInlineUnits.convert(body, o, false);
        return value;
    }

    /** Every "<left> to <right>" reading of an expression; the converters try them in order. */
    static String[][] splits(String body) {
        java.util.ArrayList<String[]> out = new java.util.ArrayList<>();
        Matcher m = CONNECTOR.matcher(body);
        while (m.find()) {
            String left = body.substring(0, m.start()).trim(), right = body.substring(m.end()).trim();
            if (!left.isEmpty() && !right.isEmpty()) out.add(new String[]{left, right});
        }
        return out.toArray(new String[0][]);
    }

    /** About six significant digits: what a conversion is accurate to. */
    static String approximate(double value, boolean group) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "";
        BigDecimal v = new BigDecimal(value);
        v = v.abs().compareTo(BigDecimal.valueOf(100000)) >= 0 ? v.setScale(0, RoundingMode.HALF_UP) : v.round(new MathContext(6, RoundingMode.HALF_UP));
        return NebulaMath.format(v, group);
    }
}
