package app.nebulagram.ui;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** "15:00 msk in tokyo", "3pm est to utc+3", "now in london", "токио". Cities, abbreviations and UTC offsets. */
public final class NebulaInlineTime {
    private NebulaInlineTime() { }
    private static final Map<String, String> ZONES = new HashMap<>();
    private static final Pattern TIME = Pattern.compile("^(?:(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a\\.m\\.|p\\.m\\.)|(\\d{1,2}):(\\d{2})|now|сейчас|time|время)(?:\\s+|$)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern OFFSET = Pattern.compile("^(?:utc|gmt)?\\s*([+-])\\s*(\\d{1,2})(?::?(\\d{2}))?$");
    private static void zone(String id, String... names) { for (String name : names) ZONES.put(name, id); }
    static {
        zone("UTC", "utc", "gmt", "zulu");
        zone("Europe/Moscow", "msk", "мск", "moscow", "москва", "москве", "москвы", "по москве", "питер", "петербург", "петербурге", "saint petersburg", "st petersburg");
        zone("Europe/Kaliningrad", "kaliningrad", "калининград", "калининграде");
        zone("Europe/Samara", "samara", "самара", "самаре");
        zone("Asia/Yekaterinburg", "yekt", "yekaterinburg", "ekaterinburg", "екатеринбург", "екатеринбурге");
        zone("Asia/Omsk", "omsk", "омск", "омске");
        zone("Asia/Novosibirsk", "novosibirsk", "новосибирск", "новосибирске");
        zone("Asia/Krasnoyarsk", "krasnoyarsk", "красноярск", "красноярске");
        zone("Asia/Irkutsk", "irkutsk", "иркутск", "иркутске");
        zone("Asia/Yakutsk", "yakutsk", "якутск", "якутске");
        zone("Asia/Vladivostok", "vladivostok", "владивосток", "владивостоке");
        zone("Asia/Kamchatka", "kamchatka", "камчатка", "камчатке", "петропавловск-камчатский");
        zone("Europe/Kyiv", "kyiv", "kiev", "киев", "киеве", "київ", "kharkiv", "харьков", "odesa", "odessa", "одесса", "одессе");
        zone("Europe/Minsk", "minsk", "минск", "минске");
        zone("Asia/Almaty", "almaty", "алматы", "astana", "астана", "астане");
        zone("Asia/Tashkent", "tashkent", "ташкент", "ташкенте");
        zone("Asia/Bishkek", "bishkek", "бишкек", "бишкеке");
        zone("Asia/Tbilisi", "tbilisi", "тбилиси");
        zone("Asia/Yerevan", "yerevan", "ереван", "ереване");
        zone("Asia/Baku", "baku", "баку");
        zone("Europe/Istanbul", "istanbul", "стамбул", "стамбуле", "ankara", "анкара", "анкаре", "trt");
        zone("Asia/Dubai", "dubai", "дубай", "дубае", "abu dhabi", "абу-даби", "gst");
        zone("Europe/London", "london", "лондон", "лондоне", "bst", "uk");
        zone("Europe/Paris", "paris", "париж", "париже", "cet", "cest");
        zone("Europe/Berlin", "berlin", "берлин", "берлине", "munich", "мюнхен", "мюнхене");
        zone("Europe/Rome", "rome", "рим", "риме", "milan", "милан", "милане");
        zone("Europe/Madrid", "madrid", "мадрид", "мадриде", "barcelona", "барселона", "барселоне");
        zone("Europe/Amsterdam", "amsterdam", "амстердам", "амстердаме");
        zone("Europe/Warsaw", "warsaw", "варшава", "варшаве");
        zone("Europe/Prague", "prague", "прага", "праге");
        zone("Europe/Vienna", "vienna", "вена", "вене");
        zone("Europe/Helsinki", "helsinki", "хельсинки", "eet", "eest");
        zone("Europe/Riga", "riga", "рига", "риге");
        zone("Europe/Vilnius", "vilnius", "вильнюс", "вильнюсе");
        zone("Europe/Tallinn", "tallinn", "таллин", "таллине", "таллинн");
        zone("Europe/Chisinau", "chisinau", "кишинёв", "кишинев", "кишинёве", "кишиневе");
        zone("Europe/Athens", "athens", "афины", "афинах");
        zone("Europe/Lisbon", "lisbon", "лиссабон", "лиссабоне");
        zone("Asia/Jerusalem", "jerusalem", "иерусалим", "иерусалиме", "tel aviv", "тель-авив", "тель-авиве", "israel", "израиль", "израиле");
        zone("Asia/Kolkata", "ist", "india", "delhi", "new delhi", "дели", "нью-дели", "mumbai", "мумбаи", "kolkata");
        zone("Asia/Bangkok", "bangkok", "бангкок", "бангкоке", "ict");
        zone("Asia/Ho_Chi_Minh", "hanoi", "ханой", "ханое", "ho chi minh", "хошимин", "хошимине");
        zone("Asia/Jakarta", "jakarta", "джакарта", "джакарте", "bali", "бали");
        zone("Asia/Singapore", "singapore", "сингапур", "сингапуре", "sgt");
        zone("Asia/Hong_Kong", "hong kong", "гонконг", "гонконге", "hkt");
        zone("Asia/Shanghai", "beijing", "пекин", "пекине", "shanghai", "шанхай", "шанхае", "china", "китай", "китае");
        zone("Asia/Tokyo", "jst", "tokyo", "токио", "japan", "япония", "японии", "osaka", "осака");
        zone("Asia/Seoul", "kst", "seoul", "сеул", "сеуле");
        zone("Australia/Sydney", "aest", "aedt", "sydney", "сидней", "сиднее", "melbourne", "мельбурн", "мельбурне");
        zone("Pacific/Auckland", "nzst", "nzdt", "auckland", "окленд", "окленде");
        zone("America/New_York", "est", "edt", "et", "new york", "nyc", "нью-йорк", "нью-йорке", "washington", "вашингтон", "вашингтоне", "miami", "майами", "toronto", "торонто", "boston", "бостон", "бостоне");
        zone("America/Chicago", "cst", "cdt", "ct", "chicago", "чикаго", "dallas", "даллас", "далласе", "houston", "хьюстон", "хьюстоне");
        zone("America/Denver", "mst", "mdt", "denver", "денвер", "денвере");
        zone("America/Los_Angeles", "pst", "pdt", "pt", "los angeles", "la", "лос-анджелес", "лос-анджелесе", "san francisco", "сан-франциско", "seattle", "сиэтл", "сиэтле", "vancouver", "ванкувер", "ванкувере");
        zone("America/Sao_Paulo", "sao paulo", "são paulo", "сан-паулу", "rio", "рио", "brazil", "бразилия", "бразилии");
        zone("America/Mexico_City", "mexico city", "мехико");
        zone("America/Argentina/Buenos_Aires", "buenos aires", "буэнос-айрес", "буэнос-айресе");
        zone("Africa/Cairo", "cairo", "каир", "каире", "egypt", "египет", "египте");
        zone("Africa/Johannesburg", "johannesburg", "йоханнесбург", "йоханнесбурге");
    }

    static TimeZone find(String token) {
        String t = token.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (t.isEmpty()) return null;
        String id = ZONES.get(t);
        if (id != null) return TimeZone.getTimeZone(id);
        Matcher m = OFFSET.matcher(t);
        if (!m.matches()) return null;
        int hours = Integer.parseInt(m.group(2)), minutes = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        if (hours > 14 || minutes > 59) return null;
        return TimeZone.getTimeZone(String.format(Locale.ROOT, "GMT%s%02d:%02d", m.group(1), hours, minutes));
    }

    static String convert(String body, NebulaInlineResults.Options o) {
        String[][] splits = NebulaInlineResults.splits(body);
        for (String[] split : splits) {
            TimeZone target = find(split[1]);
            if (target == null) continue;
            String result = convert(split[0], target, o, true);
            if (result != null) return result;
        }
        // "15:00 msk" converts to the phone's zone; a bare city shows its current time.
        String result = convert(body, o.local, o, false);
        if (result != null) return result;
        TimeZone city = find(body);
        return city == null ? "" : show(o.now, city, o.local, o);
    }
    private static String convert(String source, TimeZone target, NebulaInlineResults.Options o, boolean explicit) {
        Matcher m = TIME.matcher(source.trim());
        int hour = -1, minute = 0; boolean time = m.find(); String rest = source.trim();
        if (time) {
            if (m.group(1) != null) {
                hour = Integer.parseInt(m.group(1)); minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
                boolean pm = m.group(3).toLowerCase(Locale.ROOT).startsWith("p");
                if (hour < 1 || hour > 12) return null;
                hour = hour % 12 + (pm ? 12 : 0);
            } else if (m.group(4) != null) { hour = Integer.parseInt(m.group(4)); minute = Integer.parseInt(m.group(5)); }
            if (hour > 23 || minute > 59) return null;
            rest = rest.substring(m.end()).trim().replaceFirst("(?iu)^(?:in|at|в|во)\\s+", "");
        } else if (!explicit) return null;
        // "15:00 in tokyo" starts from the phone's zone; "15:00 =" alone is not a question.
        if (rest.isEmpty() && (!explicit || !time)) return null;
        TimeZone zone = rest.isEmpty() ? o.local : find(rest);
        if (zone == null) return null;
        if (hour < 0) return show(o.now, target, zone, o);
        if (!explicit && zone.getOffset(o.now) == target.getOffset(o.now)) return null;
        Calendar c = Calendar.getInstance(zone, Locale.ROOT);
        c.setTimeInMillis(o.now);
        c.set(Calendar.HOUR_OF_DAY, hour); c.set(Calendar.MINUTE, minute); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return show(c.getTimeInMillis(), target, zone, o);
    }
    /** "21:00", or "03:00 (+1)" when the day differs from the source zone's. */
    private static String show(long instant, TimeZone target, TimeZone source, NebulaInlineResults.Options o) {
        Calendar there = Calendar.getInstance(target, Locale.ROOT), here = Calendar.getInstance(source, Locale.ROOT);
        there.setTimeInMillis(instant); here.setTimeInMillis(instant);
        int shift = day(there) - day(here);
        String time = String.format(Locale.ROOT, "%02d:%02d", there.get(Calendar.HOUR_OF_DAY), there.get(Calendar.MINUTE));
        return shift == 0 ? time : time + " (" + (shift > 0 ? "+" : "−") + Math.abs(shift) + ")";
    }
    private static int day(Calendar c) { return c.get(Calendar.YEAR) * 400 + c.get(Calendar.DAY_OF_YEAR); }
}
