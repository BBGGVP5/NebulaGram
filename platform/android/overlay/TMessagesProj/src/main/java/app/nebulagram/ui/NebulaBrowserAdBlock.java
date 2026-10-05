package app.nebulagram.ui;

import android.content.SharedPreferences;
import android.webkit.*;
import android.util.AtomicFile;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Browser-only filter cache. Request threads perform indexed matching without disk or network IO. */
public final class NebulaBrowserAdBlock {
    private static final String BUNDLED = "||doubleclick.net^\n||googlesyndication.com^\n||adservice.google.com^\n||googleadservices.com^\n||adnxs.com^\n||adsrvr.org^\n||taboola.com^\n||outbrain.com^\n||amazon-adsystem.com^\n||criteo.com^\n||adform.net^\n||rubiconproject.com^\n||pubmatic.com^\n||smartadserver.com^\n##.adsbygoogle\n##[data-ad-slot]\n##.ad-banner\n##.advertisement\n";
    private static volatile NebulaAdBlockRules rules = NebulaAdBlockRules.parse(BUNDLED);
    private static volatile Set<String> exclusions = Collections.emptySet();
    private static boolean prepared, refreshing;
    private static long revision;
    private static final AtomicLong blocked = new AtomicLong();
    private volatile String page = "";
    public NebulaBrowserAdBlock() { prepare(); }
    public static SharedPreferences prefs() { return ApplicationLoader.applicationContext.getSharedPreferences("nebula_browser", 0); }
    public static boolean enabled() { return prefs().getBoolean("adblock", false); }
    public static long blockedCount() { return blocked.get(); }
    public static int ruleCount() { return rules.networkCount + rules.cosmeticCount; }
    public static void exclusions(String text) {
        LinkedHashSet<String> values = new LinkedHashSet<>(); for (String line : text.split("[\\s,]+")) { String domain = NebulaAdBlockRules.domain(line); if (!domain.isEmpty() && values.size() < 100) values.add(domain); }
        exclusions = Collections.unmodifiableSet(values); prefs().edit().putString("exclusions", String.join("\n", values)).apply();
    }
    private static File cache() { return new File(ApplicationLoader.applicationContext.getFilesDir(), "browser-adblock.txt"); }
    public static synchronized void prepare() {
        if (prepared) return; prepared = true; final long generation = revision; exclusions(prefs().getString("exclusions", ""));
        new Thread(() -> { try (InputStream in = new AtomicFile(cache()).openRead()) { NebulaAdBlockRules parsed = NebulaAdBlockRules.parse(BUNDLED + "\n" + read(in)); synchronized(NebulaBrowserAdBlock.class) { if(generation == revision) rules = parsed; } } catch (Exception ignored) { } }, "NebulaBrowserFilters").start();
    }
    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) { if (out.size() + n > 8_000_000) throw new IOException("Filters too large"); out.write(buffer, 0, n); }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
    public static synchronized boolean refreshing() { return refreshing; }
    public static synchronized void refresh(java.util.function.Consumer<Boolean> done) {
        if (refreshing) return; refreshing = true;
        new Thread(() -> {
            boolean success = false; HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("https://easylist.to/easylist/easylist.txt").openConnection(); connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(20000); connection.setReadTimeout(20000);
                if (connection.getResponseCode() != 200) throw new IOException();
                String text; try (InputStream in = connection.getInputStream()) { text = read(in); }
                NebulaAdBlockRules parsed = NebulaAdBlockRules.parse(BUNDLED + "\n" + text); if (parsed.networkCount < 50) throw new IOException("Invalid filter list");
                AtomicFile target = new AtomicFile(cache()); FileOutputStream out = target.startWrite();
                try { out.write(text.getBytes(StandardCharsets.UTF_8)); target.finishWrite(out); } catch (Exception e) { target.failWrite(out); throw e; }
                synchronized(NebulaBrowserAdBlock.class) { revision++; rules = parsed; } prefs().edit().putLong("updated", System.currentTimeMillis()).apply(); success = true;
            } catch (Exception ignored) { } finally { if (connection != null) connection.disconnect(); synchronized (NebulaBrowserAdBlock.class) { refreshing = false; } }
            final boolean result = success; org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> done.accept(result));
        }, "NebulaBrowserFilterRefresh").start();
    }
    public void navigate(String url) { page = url == null ? "" : url; }
    public WebResourceResponse intercept(WebResourceRequest request) { return response(request.getUrl().toString(), request.isForMainFrame()); }
    public WebResourceResponse intercept(String url) {
        // The old overload has no main-frame flag: only definite asset requests are eligible.
        String path; try { path = new URL(url).getPath().toLowerCase(Locale.ROOT); } catch (Exception ignored) { return null; }
        if (!path.matches(".*\\.(js|css|png|jpe?g|gif|webp|svg|avif|woff2?|ttf|otf|mp4|webm|mp3|ogg)$")) return null;
        return response(url, url.equals(page));
    }
    private WebResourceResponse response(String url, boolean main) {
        if (!enabled() || !rules.blocked(url, page, main, false, exclusions)) return null;
        blocked.incrementAndGet();
        return new WebResourceResponse("text/plain", "UTF-8", 204, "No Content", Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }
    public void finished(WebView webView, String url) {
        if (!enabled() || !url.equals(page)) return; String css = rules.css(url, exclusions); if (css.isEmpty()) return;
        webView.evaluateJavascript("(function(){var s=document.getElementById('nebula-adblock-style');if(!s){s=document.createElement('style');s.id='nebula-adblock-style';(document.head||document.documentElement).appendChild(s);}s.textContent=" + JSONObject.quote(css) + ";})()", null);
    }
}
