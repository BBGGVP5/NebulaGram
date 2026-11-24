package app.nebulagram.ui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Time zones by any city of the IANA database ("reykjavik", "ho chi minh") and by country name in
 * English or Russian ("japan", "япония"). Names come from the platform: city names from zone IDs,
 * country names from Locale. A country spanning several zones resolves to its capital's zone.
 */
public final class NebulaWorldZones {
    private NebulaWorldZones() { }

    /** ISO 3166 code and the zone of its capital or main business city. */
    private static final String COUNTRIES =
            "RU Europe/Moscow,UA Europe/Kyiv,BY Europe/Minsk,KZ Asia/Almaty,UZ Asia/Tashkent,KG Asia/Bishkek,TJ Asia/Dushanbe,"
            + "TM Asia/Ashgabat,GE Asia/Tbilisi,AM Asia/Yerevan,AZ Asia/Baku,MD Europe/Chisinau,LV Europe/Riga,LT Europe/Vilnius,"
            + "EE Europe/Tallinn,FI Europe/Helsinki,SE Europe/Stockholm,NO Europe/Oslo,DK Europe/Copenhagen,IS Atlantic/Reykjavik,"
            + "GB Europe/London,IE Europe/Dublin,FR Europe/Paris,DE Europe/Berlin,NL Europe/Amsterdam,BE Europe/Brussels,"
            + "LU Europe/Luxembourg,CH Europe/Zurich,AT Europe/Vienna,IT Europe/Rome,ES Europe/Madrid,PT Europe/Lisbon,"
            + "PL Europe/Warsaw,CZ Europe/Prague,SK Europe/Bratislava,HU Europe/Budapest,RO Europe/Bucharest,BG Europe/Sofia,"
            + "GR Europe/Athens,RS Europe/Belgrade,HR Europe/Zagreb,SI Europe/Ljubljana,BA Europe/Sarajevo,ME Europe/Podgorica,"
            + "MK Europe/Skopje,AL Europe/Tirane,CY Asia/Nicosia,MT Europe/Malta,TR Europe/Istanbul,IL Asia/Jerusalem,"
            + "AE Asia/Dubai,SA Asia/Riyadh,QA Asia/Qatar,KW Asia/Kuwait,BH Asia/Bahrain,OM Asia/Muscat,IR Asia/Tehran,"
            + "IQ Asia/Baghdad,JO Asia/Amman,LB Asia/Beirut,SY Asia/Damascus,EG Africa/Cairo,MA Africa/Casablanca,"
            + "TN Africa/Tunis,DZ Africa/Algiers,NG Africa/Lagos,KE Africa/Nairobi,ET Africa/Addis_Ababa,ZA Africa/Johannesburg,"
            + "IN Asia/Kolkata,PK Asia/Karachi,BD Asia/Dhaka,LK Asia/Colombo,NP Asia/Kathmandu,AF Asia/Kabul,MN Asia/Ulaanbaatar,"
            + "CN Asia/Shanghai,HK Asia/Hong_Kong,TW Asia/Taipei,JP Asia/Tokyo,KR Asia/Seoul,TH Asia/Bangkok,VN Asia/Ho_Chi_Minh,"
            + "KH Asia/Phnom_Penh,LA Asia/Vientiane,MM Asia/Yangon,MY Asia/Kuala_Lumpur,SG Asia/Singapore,ID Asia/Jakarta,"
            + "PH Asia/Manila,AU Australia/Sydney,NZ Pacific/Auckland,US America/New_York,CA America/Toronto,"
            + "MX America/Mexico_City,CU America/Havana,BR America/Sao_Paulo,AR America/Argentina/Buenos_Aires,"
            + "CL America/Santiago,CO America/Bogota,PE America/Lima,VE America/Caracas,EC America/Guayaquil,"
            + "UY America/Montevideo,PY America/Asuncion,BO America/La_Paz";
    private static final String[][] ALIASES = {
            {"usa", "US"}, {"сша", "US"}, {"америка", "US"}, {"uae", "AE"}, {"оаэ", "AE"}, {"эмираты", "AE"},
            {"britain", "GB"}, {"england", "GB"}, {"англия", "GB"}, {"великобритания", "GB"}, {"holland", "NL"},
            {"голландия", "NL"}, {"корея", "KR"}, {"south korea", "KR"}, {"czechia", "CZ"}, {"чехия", "CZ"},
            {"белоруссия", "BY"}, {"киргизия", "KG"}, {"молдова", "MD"}};
    private static volatile Map<String, String> names;

    /** The zone ID for a lower-case city or country name, or null. */
    public static String find(String name) {
        if (name == null || name.length() < 2 || name.length() > 40) return null;
        return names().get(name.trim().toLowerCase(Locale.ROOT).replace('ё', 'е'));
    }

    private static Map<String, String> names() {
        Map<String, String> map = names;
        if (map != null) return map;
        map = new HashMap<>();
        for (String id : TimeZone.getAvailableIDs()) {
            int slash = id.lastIndexOf('/');
            if (slash <= 0 || id.startsWith("Etc/") || id.startsWith("SystemV/")) continue;
            String city = id.substring(slash + 1).replace('_', ' ').toLowerCase(Locale.ROOT);
            if (city.length() >= 3 && !map.containsKey(city)) map.put(city, id);
        }
        Map<String, String> byCode = new HashMap<>();
        Locale[] languages = {Locale.ENGLISH, new Locale("ru")};
        for (String pair : COUNTRIES.split(",")) {
            String code = pair.substring(0, 2), zone = pair.substring(3);
            byCode.put(code, zone);
            Locale country = new Locale("", code);
            for (Locale language : languages) {
                String shown = country.getDisplayCountry(language);
                if (shown != null && !shown.isEmpty() && !shown.equals(code)) map.put(shown.toLowerCase(Locale.ROOT).replace('ё', 'е'), zone);
            }
        }
        for (String[] alias : ALIASES) map.put(alias[0], byCode.get(alias[1]));
        names = map;
        return map;
    }
}
