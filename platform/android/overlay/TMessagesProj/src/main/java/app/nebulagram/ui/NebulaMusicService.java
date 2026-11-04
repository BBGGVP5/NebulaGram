package app.nebulagram.ui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

/** Opt-in lyrics (lrclib.net) and cover (iTunes Search) lookups with a small disk cache. */
public final class NebulaMusicService {
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final long MISS_TTL = 3L * 24 * 60 * 60 * 1000;
    private static final Map<String, String> memory = new LinkedHashMap<String, String>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) { return size() > 64; }
    };
    private NebulaMusicService() { }

    public static String key(String artist, String title, int duration) {
        return NebulaMusicLookup.normalize(artist) + "|" + NebulaMusicLookup.normalize(title) + "|" + duration;
    }

    /** Delivers lyrics, or null when none exist or the request failed, on the UI thread. */
    public static void lyrics(String artist, String title, int duration, Consumer<NebulaLrc> result) {
        if (title == null || title.trim().isEmpty()) { result.accept(null); return; }
        String key = "lyrics:" + key(artist, title, duration);
        worker.execute(() -> {
            String cached = cached(key);
            String value = cached;
            if (value == null) {
                try {
                    NebulaMusicLookup.Lyrics lyrics = null;
                    String body = get(NebulaMusicLookup.lrclibGet(artist, title, duration), "application/json");
                    if (body != null) lyrics = NebulaMusicLookup.lyrics(body, duration);
                    if (lyrics == null || lyrics.synced == null) {
                        String search = get(NebulaMusicLookup.lrclibSearch(artist, title), "application/json");
                        NebulaMusicLookup.Lyrics found = search == null ? null : NebulaMusicLookup.lyrics(search, duration);
                        if (found != null && (lyrics == null || found.synced != null)) lyrics = found;
                    }
                    value = lyrics == null || lyrics.instrumental ? "" : lyrics.synced != null ? "S" + lyrics.synced : lyrics.plain != null ? "P" + lyrics.plain : "";
                    store(key, value);
                } catch (Exception error) {
                    FileLog.e(error, false);
                    value = null;
                }
            }
            NebulaLrc lrc = value == null || value.isEmpty() ? null : value.charAt(0) == 'S' ? NebulaLrc.parse(value.substring(1)) : NebulaLrc.plain(value.substring(1));
            NebulaLrc answer = lrc == null || lrc.isEmpty() ? null : lrc;
            AndroidUtilities.runOnUIThread(() -> result.accept(answer));
        });
    }

    /** Delivers a high-resolution iTunes cover URL, or null, on the UI thread. */
    public static void cover(String artist, String title, int duration, Consumer<String> result) {
        if (title == null || title.trim().isEmpty()) { result.accept(null); return; }
        String key = "cover:" + key(artist, title, duration);
        worker.execute(() -> {
            String value = cached(key);
            if (value == null) {
                try {
                    String body = get(NebulaMusicLookup.itunesSearch(artist, title, null), "application/json");
                    String url = body == null ? null : NebulaMusicLookup.artwork(body, artist, title, duration, 1200);
                    value = url == null ? "" : url;
                    store(key, value);
                } catch (Exception error) {
                    FileLog.e(error, false);
                }
            }
            String answer = value == null || value.isEmpty() ? null : value;
            AndroidUtilities.runOnUIThread(() -> result.accept(answer));
        });
    }

    private static File file(String key) {
        File folder = new File(ApplicationLoader.applicationContext.getCacheDir(), "nebula_music");
        folder.mkdirs();
        return new File(folder, Utilities.MD5(key) + ".txt");
    }
    private static String cached(String key) {
        synchronized (memory) { if (memory.containsKey(key)) return memory.get(key); }
        File file = file(key);
        if (!file.exists()) return null;
        try {
            String value = new String(read(new java.io.FileInputStream(file)), StandardCharsets.UTF_8);
            // A remembered miss is retried after a few days; found lyrics are kept.
            if (value.isEmpty() && System.currentTimeMillis() - file.lastModified() > MISS_TTL) return null;
            synchronized (memory) { memory.put(key, value); }
            return value;
        } catch (Exception error) {
            return null;
        }
    }
    private static void store(String key, String value) {
        synchronized (memory) { memory.put(key, value); }
        try (FileOutputStream output = new FileOutputStream(file(key))) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            FileLog.e(error, false);
        }
    }
    public static void clearCache() {
        synchronized (memory) { memory.clear(); }
        worker.execute(() -> {
            File[] files = new File(ApplicationLoader.applicationContext.getCacheDir(), "nebula_music").listFiles();
            if (files != null) for (File file : files) file.delete();
        });
    }

    /** Body of a 200 reply, null for 404, an exception otherwise. */
    private static String get(String url, String accept) throws Exception {
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
        try {
            connection.setConnectTimeout(8000); connection.setReadTimeout(12000);
            connection.setRequestProperty("User-Agent", NebulaMusicLookup.USER_AGENT);
            connection.setRequestProperty("Accept", accept);
            int code = connection.getResponseCode();
            if (code == 404) return null;
            if (code != 200) throw new java.io.IOException("HTTP " + code);
            return new String(read(connection.getInputStream()), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }
    private static byte[] read(InputStream stream) throws Exception {
        try (InputStream input = stream) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) > 0;) {
                if (output.size() > 1024 * 1024) throw new java.io.IOException("Response too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }
}
