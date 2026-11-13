package app.nebulagram.ui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Endpoints and result links for reverse image search. Pure, so a check can run it on a JVM. */
public final class NebulaImageSearchLinks {
    public static final int GOOGLE = 0, YANDEX = 1;
    public static final String LENS_UPLOAD = "https://lens.google.com/v3/upload";
    public static final String LENS_HOME = "https://lens.google.com/";
    public static final String YANDEX_UPLOAD = "https://yandex.ru/images-apphost/image-download?cbird=111&images_avatars_size=preview&images_avatars_namespace=images-cbir";
    public static final String YANDEX_HOME = "https://yandex.ru/images/";

    private static final Pattern URL = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern CBIR = Pattern.compile("\"cbir_id\"\\s*:\\s*\"([^\"]+)\"");

    private NebulaImageSearchLinks() {}

    public static String home(int engine) {
        return engine == YANDEX ? YANDEX_HOME : LENS_HOME;
    }

    /** Lens answers the upload with a redirect to its result page; only https Google hosts are accepted. */
    public static String lensResult(String location) {
        if (location == null) return null;
        if (location.startsWith("/")) location = "https://lens.google.com" + location;
        if (!location.startsWith("https://")) return null;
        String host = location.substring(8);
        int slash = host.indexOf('/');
        if (slash >= 0) host = host.substring(0, slash);
        return host.equals("google.com") || host.endsWith(".google.com") ? location : null;
    }

    /** Yandex answers the upload with JSON holding the stored image url and a cbir_id. */
    public static String yandexResult(String json) {
        if (json == null) return null;
        Matcher url = URL.matcher(json), cbir = CBIR.matcher(json);
        if (!url.find() || !cbir.find()) return null;
        String image = url.group(1).replace("\\/", "/");
        return "https://yandex.ru/images/search?rpt=imageview&url=" + encode(image) + "&cbir_id=" + encode(cbir.group(1).replace("\\/", "/"));
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
