package app.nebulagram.ui;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** What "Export data" saves and how; plain Java so every rule can be checked without a device. */
public final class NebulaExportOptions {
    // Account
    public static final int INFO = 0, CONTACTS = 1, SESSIONS = 2;
    // Chats
    public static final int PERSONAL = 10, BOTS = 11, SAVED = 12, PRIVATE_GROUPS = 13, PUBLIC_GROUPS = 14, CHANNELS = 15;
    // Media
    public static final int PHOTOS = 20, VIDEOS = 21, VOICE = 22, ROUND = 23, STICKERS = 24, GIFS = 25, FILES = 26;
    public static final int[][] SECTIONS = {{INFO, CONTACTS, SESSIONS}, {PERSONAL, BOTS, SAVED, PRIVATE_GROUPS, PUBLIC_GROUPS, CHANNELS},
            {PHOTOS, VIDEOS, VOICE, ROUND, STICKERS, GIFS, FILES}};
    public static final int FORMAT_HTML = 0, FORMAT_JSON = 1, FORMAT_BOTH = 2;
    public static final int PERIOD_ALL = 0, PERIOD_YEAR = 1, PERIOD_MONTH = 2, PERIOD_WEEK = 3;
    public static final long MIN_LIMIT = 1L << 20, MAX_LIMIT = 4L << 30;

    private final boolean[] chosen = new boolean[32];
    public long sizeLimit = 8L << 20;
    public int format = FORMAT_HTML;
    public int period = PERIOD_ALL;

    public NebulaExportOptions() {
        // As in Telegram Desktop: the account card, personal chats and photos by default.
        chosen[INFO] = true; chosen[CONTACTS] = true; chosen[PERSONAL] = true; chosen[PHOTOS] = true;
    }

    public boolean has(int item) { return item >= 0 && item < chosen.length && chosen[item]; }
    public void set(int item, boolean value) { if (item >= 0 && item < chosen.length) chosen[item] = value; }
    public int selected(int section) { int n = 0; for (int item : SECTIONS[section]) if (chosen[item]) n++; return n; }
    public boolean any(int section) { return selected(section) > 0; }
    public void setAll(int section, boolean value) { for (int item : SECTIONS[section]) chosen[item] = value; }
    /** Something to export at all: account data or at least one kind of chat. */
    public boolean valid() { return any(0) || any(1); }

    /** Slider 0..1 on a log scale between 1 MB and 4 GB. */
    public static long limit(float position) {
        double p = Math.max(0, Math.min(1, position));
        return Math.round(Math.exp(Math.log(MIN_LIMIT) + p * (Math.log(MAX_LIMIT) - Math.log(MIN_LIMIT))));
    }
    public static float position(long limit) {
        long v = Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, limit));
        return (float) ((Math.log(v) - Math.log(MIN_LIMIT)) / (Math.log(MAX_LIMIT) - Math.log(MIN_LIMIT)));
    }
    /** Round slider values to 1, 2, 5 steps so the label reads well. */
    public static long rounded(long bytes) {
        double mb = bytes / 1048576.0;
        double base = Math.pow(10, Math.floor(Math.log10(Math.max(1, mb))));
        double[] steps = {1, 2, 3, 5, 8, 10};
        double best = base;
        for (double s : steps) if (Math.abs(s * base - mb) < Math.abs(best - mb)) best = s * base;
        return Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, Math.round(best * 1048576)));
    }
    public static String size(long bytes) {
        if (bytes >= 1L << 30) return trim(bytes / 1073741824.0) + " GB";
        if (bytes >= 1L << 20) return trim(bytes / 1048576.0) + " MB";
        if (bytes >= 1L << 10) return trim(bytes / 1024.0) + " KB";
        return bytes + " B";
    }
    private static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Oldest message date (unix seconds) to keep, or 0 for everything. */
    public int since(long nowMillis) {
        long day = 86400L;
        long now = nowMillis / 1000;
        switch (period) {
            case PERIOD_YEAR: return (int) (now - 365 * day);
            case PERIOD_MONTH: return (int) (now - 30 * day);
            case PERIOD_WEEK: return (int) (now - 7 * day);
            default: return 0;
        }
    }

    /** A file name safe on every file system, keeping the extension. */
    public static String safeName(String name, String fallback) {
        String s = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        while (s.startsWith(".")) s = s.substring(1);
        if (s.isEmpty()) s = fallback;
        if (s.length() > 80) {
            int dot = s.lastIndexOf('.');
            String ext = dot > 0 && s.length() - dot <= 10 ? s.substring(dot) : "";
            s = s.substring(0, 80 - ext.length()) + ext;
        }
        return s;
    }

    public static String folderName(long nowMillis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT);
        return "NebulaGram Export " + format.format(new Date(nowMillis));
    }

    /** "2026-07-30T03:20:35", the date format of Telegram Desktop's result.json (local time). */
    public static String isoDate(int unix, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
        format.setTimeZone(zone);
        return format.format(new Date(unix * 1000L));
    }

    public static String json(String value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }

    public static String html(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '<': out.append("&lt;"); break;
                case '>': out.append("&gt;"); break;
                case '&': out.append("&amp;"); break;
                case '"': out.append("&quot;"); break;
                case '\'': out.append("&#39;"); break;
                case '\n': out.append("<br>"); break;
                default: out.append(c);
            }
        }
        return out.toString();
    }

    /** A relative link inside the export, each path segment percent-encoded where needed. */
    public static String href(String path) {
        StringBuilder out = new StringBuilder();
        for (char c : path.toCharArray()) {
            if (c < 128 && (Character.isLetterOrDigit(c) || "/._-".indexOf(c) >= 0)) out.append(c);
            else for (byte b : String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8)) out.append(String.format(Locale.ROOT, "%%%02X", b & 0xFF));
        }
        return out.toString();
    }

    /** Progress 0..100 from the finished share of steps and the share of the current one. */
    public static int percent(int done, int total, float current) {
        if (total <= 0) return 0;
        float value = (done + Math.max(0, Math.min(1, current))) / total;
        return Math.max(0, Math.min(100, (int) Math.floor(value * 100)));
    }
}
