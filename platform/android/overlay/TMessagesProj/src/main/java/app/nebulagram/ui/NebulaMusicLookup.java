package app.nebulagram.ui;

import java.net.URLEncoder;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Request building and reply matching for lrclib.net lyrics and iTunes Search covers. */
public final class NebulaMusicLookup {
    public static final String USER_AGENT = "NebulaGram (https://github.com/BBGGVP5/NebulaGram)";
    private NebulaMusicLookup() { }

    public static final class Lyrics {
        public final String synced, plain;
        public final boolean instrumental;
        Lyrics(String synced, String plain, boolean instrumental) { this.synced = synced; this.plain = plain; this.instrumental = instrumental; }
        public NebulaLrc lrc() {
            if (synced != null && !synced.trim().isEmpty()) {
                NebulaLrc parsed = NebulaLrc.parse(synced);
                if (!parsed.isEmpty()) return parsed;
            }
            return NebulaLrc.plain(plain);
        }
    }

    private static String encode(String value) {
        try { return URLEncoder.encode(value == null ? "" : value.trim(), "UTF-8").replace("+", "%20"); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    public static String lrclibGet(String artist, String title, int durationSeconds) {
        return "https://lrclib.net/api/get?artist_name=" + encode(artist) + "&track_name=" + encode(title)
            + (durationSeconds > 0 ? "&duration=" + durationSeconds : "");
    }
    public static String lrclibSearch(String artist, String title) {
        return "https://lrclib.net/api/search?track_name=" + encode(title) + (artist == null || artist.trim().isEmpty() ? "" : "&artist_name=" + encode(artist));
    }
    public static String itunesSearch(String artist, String title, String country) {
        return "https://itunes.apple.com/search?term=" + encode((artist == null ? "" : artist + " ") + title)
            + "&media=music&entity=song&limit=10" + (country == null || country.isEmpty() ? "" : "&country=" + encode(country));
    }

    /** One lrclib record, from /api/get, or the closest-duration hit from /api/search. */
    public static Lyrics lyrics(String body, int durationSeconds) {
        Object root = new NebulaWebTranslation.Json(body).value();
        Map<?, ?> best = null;
        if (root instanceof Map) best = (Map<?, ?>)root;
        else if (root instanceof List) {
            double distance = Double.MAX_VALUE;
            for (Object item : (List<?>)root) {
                if (!(item instanceof Map)) continue;
                Map<?, ?> record = (Map<?, ?>)item;
                if (string(record, "syncedLyrics") == null && string(record, "plainLyrics") == null && !Boolean.TRUE.equals(record.get("instrumental"))) continue;
                double d = durationSeconds > 0 && record.get("duration") instanceof Number ? Math.abs(((Number)record.get("duration")).doubleValue() - durationSeconds) : 0;
                // Synced lyrics win over plain ones a few seconds closer.
                if (string(record, "syncedLyrics") == null) d += 2.5;
                if (d < distance) { distance = d; best = record; }
            }
            if (best != null && durationSeconds > 0 && distance > 7.5) best = null;
        }
        if (best == null) return null;
        return new Lyrics(string(best, "syncedLyrics"), string(best, "plainLyrics"), Boolean.TRUE.equals(best.get("instrumental")));
    }
    private static String string(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof String && !((String)value).trim().isEmpty() ? (String)value : null;
    }

    static String normalize(String value) {
        if (value == null) return "";
        String text = Normalizer.normalize(value, Normalizer.Form.NFKD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        text = text.replaceAll("\\s*[(\\[].*?[)\\]]", " ").replaceAll("\\b(feat|ft)\\.?\\b.*", " ");
        return text.replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
    private static boolean similar(String a, String b) {
        String x = normalize(a), y = normalize(b);
        return !x.isEmpty() && !y.isEmpty() && (x.equals(y) || x.contains(y) || y.contains(x));
    }

    /** High-resolution artwork of the iTunes hit matching title, artist and duration, or null. */
    public static String artwork(String body, String artist, String title, int durationSeconds, int size) {
        Object root = new NebulaWebTranslation.Json(body).value();
        Object results = root instanceof Map ? ((Map<?, ?>)root).get("results") : null;
        if (!(results instanceof List)) return null;
        String best = null; int bestScore = 0;
        for (Object item : (List<?>)results) {
            if (!(item instanceof Map)) continue;
            Map<?, ?> record = (Map<?, ?>)item;
            String url = string(record, "artworkUrl100");
            if (url == null || !similar(string(record, "trackName"), title)) continue;
            int score = 1;
            if (artist != null && !artist.trim().isEmpty()) {
                if (similar(string(record, "artistName"), artist)) score += 2; else continue;
            }
            Object millis = record.get("trackTimeMillis");
            if (durationSeconds > 0 && millis instanceof Number) {
                double distance = Math.abs(((Number)millis).doubleValue() / 1000.0 - durationSeconds);
                if (distance > 10) continue;
                if (distance <= 3) score += 2;
            }
            if (score > bestScore) { bestScore = score; best = url; }
        }
        return best == null ? null : resize(best, size);
    }
    public static String resize(String artworkUrl, int size) {
        return artworkUrl.replaceFirst("/\\d+x\\d+(bb|cc)?\\.(jpg|png|webp)$", "/" + size + "x" + size + "bb.jpg");
    }
}
