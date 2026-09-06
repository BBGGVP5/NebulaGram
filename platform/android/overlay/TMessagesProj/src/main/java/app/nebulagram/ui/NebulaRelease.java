package app.nebulagram.ui;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URI;
import java.util.Locale;

/** Metadata kept in the CI-generated APK name; the post caption stays human-readable. */
public final class NebulaRelease {
    public static final String CHANNEL = "ngram_releases";
    // The channel supplied by the owner, not just a username that can be reassigned.
    public static final long CHANNEL_ID = 3985386470L;
    private static final Pattern NAME = Pattern.compile("^NebulaGram-([0-9]+\\.[0-9]+\\.[0-9]+)-TG-([0-9]+\\.[0-9]+(?:\\.[0-9]+)?)-b([0-9]{1,10})-(universal|arm64-v8a|armeabi-v7a|x86_64|x86)\\.apk$");
    public final String versionName, telegramVersion, abi;
    public final int versionCode;

    private NebulaRelease(String versionName, String telegramVersion, int code, String abi) {
        this.versionName = versionName; this.telegramVersion = telegramVersion; this.versionCode = code; this.abi = abi;
    }

    public static NebulaRelease parse(String fileName) {
        if (fileName == null || fileName.length() > 180) return null;
        Matcher match = NAME.matcher(fileName);
        if (!match.matches()) return null;
        try {
            int code = Integer.parseInt(match.group(3));
            return code > 0 ? new NebulaRelease(match.group(1), match.group(2), code, match.group(4)) : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    public boolean compatible(String[] deviceAbis) {
        if ("universal".equals(abi)) return true;
        for (String supported : deviceAbis) if (abi.equals(supported)) return true;
        return false;
    }

    private int preference(String[] deviceAbis) {
        if ("universal".equals(abi)) return 1;
        for (int i = 0; i < deviceAbis.length; i++) if (abi.equals(deviceAbis[i])) return i * 2;
        return Integer.MAX_VALUE;
    }

    public boolean preferredTo(NebulaRelease old, String[] deviceAbis) {
        return old == null || versionCode > old.versionCode
                || versionCode == old.versionCode && preference(deviceAbis) < old.preference(deviceAbis);
    }

    public static boolean isUpdateLink(String value) {
        return "tg://update".equalsIgnoreCase(value) || "tg://update/".equalsIgnoreCase(value) || "tg:update".equalsIgnoreCase(value);
    }

    public static int linkedPost(String value) {
        if (value == null) return 0;
        try {
            URI uri = new URI(value.startsWith("t.me/") ? "https://" + value : value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"t.me".equalsIgnoreCase(uri.getHost()) || uri.getUserInfo() != null || uri.getPort() != -1) return 0;
            String prefix = "/" + CHANNEL + "/";
            if (!uri.getPath().startsWith(prefix)) return 0;
            String id = uri.getPath().substring(prefix.length());
            if (!id.matches("[1-9][0-9]{0,9}")) return 0;
            return Integer.parseInt(id);
        } catch (Exception ignored) { return 0; }
    }

    public static boolean isChangelogLabel(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("что нового") || lower.contains("список изменений") || lower.contains("changelog")
                || lower.contains("what's new") || lower.contains("what’s new") || lower.contains("release notes");
    }

    public static int plainChangelogPost(String caption) {
        if (caption == null) return 0;
        for (String line : caption.split("\\n")) {
            int start = line.indexOf("https://t.me/");
            if (start < 0 || !isChangelogLabel(line.substring(0, start))) continue;
            String url = line.substring(start).trim().split("\\s+", 2)[0];
            int post = linkedPost(url);
            if (post != 0) return post;
        }
        return 0;
    }
}
