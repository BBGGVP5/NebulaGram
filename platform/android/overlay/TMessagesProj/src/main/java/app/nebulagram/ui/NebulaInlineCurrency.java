package app.nebulagram.ui;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** "100€", "100 dollars to euros", "5k ₽ to $". Rates are fiat per US dollar; crypto and Stars are not offered. */
public final class NebulaInlineCurrency {
    private NebulaInlineCurrency() { }
    private static final Map<String, String> NAMES = new HashMap<>();
    private static final Map<String, String> SYMBOLS = new HashMap<>();
    /** Choices of the default-currency setting, after "Auto". */
    public static final String[] COMMON = {"USD", "EUR", "RUB", "UAH", "BYN", "KZT", "UZS", "GBP", "TRY", "CNY", "JPY", "INR", "GEL", "AMD", "AZN", "KGS", "PLN", "CHF", "AED", "ILS"};
    private static void names(String code, String symbol, String... names) {
        NAMES.put(code.toLowerCase(Locale.ROOT), code);
        if (symbol != null) { SYMBOLS.put(code, symbol); NAMES.put(symbol.toLowerCase(Locale.ROOT), code); }
        for (String name : names) NAMES.put(name, code);
    }
    static {
        names("USD", "$", "us$", "dollar", "dollars", "доллар", "доллара", "долларов", "доллары", "долларах", "бакс", "бакса", "баксов", "баксы");
        names("EUR", "€", "euro", "euros", "евро");
        names("RUB", "₽", "rur", "ruble", "rubles", "rouble", "roubles", "руб", "руб.", "рубль", "рубля", "рублей", "рубли", "р", "р.");
        names("GBP", "£", "pound", "pounds", "pound sterling", "фунт", "фунта", "фунтов", "фунты", "фунт стерлингов", "фунтов стерлингов");
        names("JPY", "¥", "yen", "иена", "иены", "иен", "йена", "йены", "йен");
        names("CNY", null, "rmb", "yuan", "юань", "юаня", "юаней", "юани", "元");
        names("UAH", "₴", "hryvnia", "hryvnias", "гривна", "гривны", "гривен", "гривня", "грн");
        names("KZT", "₸", "tenge", "тенге", "тг");
        names("BYN", null, "br", "belarusian ruble", "belarusian rubles", "белорусский рубль", "белорусских рублей", "белорусских рубля", "бел. руб");
        names("TRY", "₺", "lira", "liras", "лира", "лиры", "лир");
        names("INR", "₹", "rupee", "rupees", "рупия", "рупии", "рупий");
        names("KRW", "₩", "won", "вона", "вон");
        names("ILS", "₪", "shekel", "shekels", "шекель", "шекеля", "шекелей");
        names("GEL", "₾", "lari", "лари");
        names("AZN", "₼", "manat", "манат", "маната", "манатов");
        names("AMD", "֏", "dram", "драм", "драма", "драмов");
        names("UZS", null, "sum", "сум", "сума", "сумов");
        names("KGS", null, "som", "сом", "сома", "сомов");
        names("PLN", null, "zł", "zloty", "złoty", "злотый", "злотых", "злотого");
        names("CHF", null, "franc", "francs", "swiss franc", "франк", "франка", "франков");
        names("AED", null, "dirham", "dirhams", "дирхам", "дирхама", "дирхамов");
        names("THB", "฿", "baht", "бат", "бата", "батов");
        names("VND", "₫", "dong", "донг", "донга", "донгов");
        names("BRL", null, "r$", "real", "reais", "реал", "реала", "реалов");
        names("CAD", null, "c$", "canadian dollar", "canadian dollars", "канадский доллар", "канадских долларов");
        names("AUD", null, "a$", "australian dollar", "australian dollars", "австралийский доллар", "австралийских долларов");
        names("MDL", null, "lei", "лей", "лея", "леев");
        names("NGN", "₦", "naira", "найра");
        names("PHP", "₱", "peso", "pesos", "песо");
        names("HUF", null, "forint", "forints", "форинт", "форинта", "форинтов");
        names("CZK", null, "kč", "koruna", "крона", "кроны", "крон");
    }

    /** An ISO code for a symbol, code or name, or null. Any three-letter code with a known rate also counts. */
    public static String code(String token, Map<String, Double> rates) {
        if (token == null) return null;
        String t = token.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (t.isEmpty() || t.length() > 32) return null;
        String code = NAMES.get(t);
        if (code != null) return code;
        if (t.matches("[a-z]{3}") && rates != null && rates.containsKey(t.toUpperCase(Locale.ROOT))) return t.toUpperCase(Locale.ROOT);
        return null;
    }
    public static String label(String code) { String symbol = SYMBOLS.get(code); return symbol == null ? code : symbol; }

    /** Default currency by the account's phone number; null when the prefix is not known. */
    public static String byPhone(String phone) {
        if (phone == null) return null;
        String p = phone.replaceAll("[^0-9]", "");
        String[][] table = {
            {"76", "KZT"}, {"77", "KZT"}, {"7", "RUB"}, {"380", "UAH"}, {"375", "BYN"}, {"998", "UZS"}, {"996", "KGS"}, {"992", "TJS"},
            {"994", "AZN"}, {"995", "GEL"}, {"374", "AMD"}, {"373", "MDL"}, {"993", "TMT"}, {"1", "USD"}, {"44", "GBP"}, {"90", "TRY"},
            {"91", "INR"}, {"86", "CNY"}, {"81", "JPY"}, {"82", "KRW"}, {"972", "ILS"}, {"971", "AED"}, {"966", "SAR"}, {"48", "PLN"},
            {"420", "CZK"}, {"36", "HUF"}, {"46", "SEK"}, {"47", "NOK"}, {"45", "DKK"}, {"41", "CHF"}, {"55", "BRL"}, {"52", "MXN"},
            {"61", "AUD"}, {"64", "NZD"}, {"65", "SGD"}, {"66", "THB"}, {"84", "VND"}, {"62", "IDR"}, {"60", "MYR"}, {"63", "PHP"},
            {"20", "EGP"}, {"27", "ZAR"}, {"234", "NGN"}, {"54", "ARS"}, {"57", "COP"}, {"56", "CLP"}, {"92", "PKR"}, {"880", "BDT"},
            {"381", "RSD"}, {"40", "RON"}, {"359", "BGN"}, {"354", "ISK"},
            {"30", "EUR"}, {"31", "EUR"}, {"32", "EUR"}, {"33", "EUR"}, {"34", "EUR"}, {"351", "EUR"}, {"353", "EUR"}, {"356", "EUR"},
            {"357", "EUR"}, {"358", "EUR"}, {"370", "EUR"}, {"371", "EUR"}, {"372", "EUR"}, {"385", "EUR"}, {"386", "EUR"}, {"39", "EUR"},
            {"421", "EUR"}, {"43", "EUR"}, {"49", "EUR"}, {"352", "EUR"}
        };
        String best = null; int length = 0;
        for (String[] row : table) if (p.startsWith(row[0]) && row[0].length() > length) { best = row[1]; length = row[0].length(); }
        return best;
    }

    static String convert(String body, NebulaInlineResults.Options o) {
        for (String[] split : NebulaInlineResults.splits(body)) {
            String target = code(split[1], o.rates);
            if (target == null) continue;
            String result = convert(split[0], target, o);
            if (result != null) return result;
        }
        String source = sourceOf(body, o);
        if (source == null) return "";
        String target = o.defaultCurrency;
        if (target == null || target.equals(source)) target = "USD".equals(source) ? "EUR" : "USD";
        String result = convert(body, target, o);
        return result == null ? "" : result;
    }
    private static String sourceOf(String amount, NebulaInlineResults.Options o) {
        Object[] parsed = amount(amount, o);
        return parsed == null ? null : (String) parsed[1];
    }
    private static String convert(String amount, String target, NebulaInlineResults.Options o) {
        Object[] parsed = amount(amount, o);
        if (parsed == null || parsed[1].equals(target)) return null;
        if (o.rates == null) { o.ratesWanted = true; return null; }
        Double from = o.rates.get((String) parsed[1]), to = o.rates.get(target);
        if (from == null || to == null || from <= 0) return null;
        BigDecimal value = ((BigDecimal) parsed[0]).multiply(BigDecimal.valueOf(to)).divide(BigDecimal.valueOf(from), MathContext.DECIMAL64);
        BigDecimal shown = value.abs().compareTo(BigDecimal.ONE) >= 0 ? value.setScale(2, RoundingMode.HALF_UP) : value.round(new MathContext(3, RoundingMode.HALF_UP));
        return NebulaMath.format(shown, o.group) + " " + label(target);
    }
    /** {amount, code}: the currency may follow the amount ("100€", "5k ₽") or lead it ("$100"). */
    private static Object[] amount(String text, NebulaInlineResults.Options o) {
        String s = text.trim();
        for (int i = Math.max(1, s.length() - 32); i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i)) || Character.isWhitespace(s.charAt(i))) continue;
            if (Character.isLetter(s.charAt(i)) && Character.isLetter(s.charAt(i - 1))) continue;
            String code = code(s.substring(i), o.rates);
            if (code == null) continue;
            BigDecimal value = NebulaMath.value(s.substring(0, i), o.degrees);
            if (value != null) return new Object[]{value, code};
        }
        for (int i = Math.min(s.length() - 1, 32); i > 0; i--) {
            if (Character.isLetter(s.charAt(i)) && Character.isLetter(s.charAt(i - 1))) continue;
            String code = code(s.substring(0, i), o.rates);
            if (code == null) continue;
            BigDecimal value = NebulaMath.value(s.substring(i), o.degrees);
            if (value != null) return new Object[]{value, code};
        }
        return null;
    }
}
