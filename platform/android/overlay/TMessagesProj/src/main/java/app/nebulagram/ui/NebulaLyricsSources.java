package app.nebulagram.ui;

/**
 * Where lyrics come from and in which order. "lrclib.net" (the default) is the old behaviour; the
 * audio file's own lyrics are the USLT frame of an MP3 or the ©lyr atom of an M4A, which Telegram
 * already reads into AudioInfo. Only lrclib.net needs the network.
 */
public final class NebulaLyricsSources {
    private NebulaLyricsSources() { }
    public static final int LRCLIB = 0, FILE_THEN_LRCLIB = 1, LRCLIB_THEN_FILE = 2, FILE_ONLY = 3;

    public static String[] titles() {
        return new String[]{
                "lrclib.net",
                NebulaText.text("Файл, затем lrclib.net", "File, then lrclib.net"),
                NebulaText.text("lrclib.net, затем файл", "lrclib.net, then file"),
                NebulaText.text("Только текст из файла", "Only lyrics from the file")};
    }

    static int clamp(int source) { return source < LRCLIB || source > FILE_ONLY ? LRCLIB : source; }
    public static boolean fileFirst(int source) { source = clamp(source); return source == FILE_THEN_LRCLIB || source == FILE_ONLY; }
    public static boolean usesNetwork(int source) { return clamp(source) != FILE_ONLY; }
    public static boolean fileAfterNetwork(int source) { return clamp(source) == LRCLIB_THEN_FILE; }
    public static boolean usesFile(int source) { return clamp(source) != LRCLIB; }

    /** Lyrics embedded in the file, synced when they carry LRC times, or null. */
    public static NebulaLrc fromFile(String embedded, boolean rich) {
        if (embedded == null || embedded.trim().isEmpty()) return null;
        NebulaLrc parsed = NebulaLrc.parse(embedded, rich);
        if (parsed.isEmpty()) return null;
        if (!parsed.synced) parsed = NebulaLrc.plain(embedded);
        return parsed.isEmpty() ? null : parsed;
    }

    /** Which lyrics to show once lrclib.net answered (null when it found nothing). */
    public static NebulaLrc choose(int source, NebulaLrc network, NebulaLrc file) {
        source = clamp(source);
        if (source == FILE_ONLY) return file;
        if (source == FILE_THEN_LRCLIB && file != null) return file;
        if (network != null) return network;
        return source == LRCLIB_THEN_FILE || source == FILE_THEN_LRCLIB ? file : null;
    }
}
