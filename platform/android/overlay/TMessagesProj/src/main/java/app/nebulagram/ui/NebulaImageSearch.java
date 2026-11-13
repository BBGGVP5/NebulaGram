package app.nebulagram.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.widget.Toast;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

/**
 * "Search by image" from the photo viewer. The shown frame is re-encoded as a JPEG,
 * uploaded only after the user picks an engine, and the result page opens in
 * Telegram's in-app browser. If an engine rejects the upload, its own upload page
 * opens instead, so the user can still pick the file there.
 */
public final class NebulaImageSearch {
    private static final int MAX_SIDE = 1280;
    private static final String AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36";

    private NebulaImageSearch() {}

    public static String title() {
        return NebulaText.text("Поиск по картинке", "Search by image");
    }

    /** Asks for an engine, then calls {@code close} (the photo viewer covers the browser) and searches. */
    public static void choose(Activity activity, Bitmap bitmap, Theme.ResourcesProvider resources, Runnable close) {
        if (activity == null || bitmap == null || bitmap.isRecycled()) {
            toast(NebulaText.text("Изображение ещё не загружено", "The image has not loaded yet"));
            return;
        }
        byte[] jpeg = encode(bitmap);
        if (jpeg == null) return;
        new AlertDialog.Builder(activity, resources)
                .setTitle(title())
                .setItems(new CharSequence[]{"Google Lens", NebulaText.text("Яндекс Картинки", "Yandex Images")}, (dialog, which) -> {
                    close.run();
                    search(activity, which == 0 ? NebulaImageSearchLinks.GOOGLE : NebulaImageSearchLinks.YANDEX, jpeg);
                })
                .show();
    }

    private static void search(Activity activity, int engine, byte[] jpeg) {
        toast(NebulaText.text("Загружаем изображение…", "Uploading the image…"));
        Utilities.globalQueue.postRunnable(() -> {
            String result = null;
            try {
                result = engine == NebulaImageSearchLinks.YANDEX ? yandex(jpeg) : lens(jpeg);
            } catch (Exception e) {
                FileLog.e(e);
            }
            String url = result;
            AndroidUtilities.runOnUIThread(() -> {
                if (url == null) toast(NebulaText.text("Сервис не принял файл — откройте загрузку вручную", "The service refused the upload — pick the file on its page"));
                if (!Browser.openInTelegramBrowser(activity, url != null ? url : NebulaImageSearchLinks.home(engine), null)) {
                    Browser.openUrl(activity, url != null ? url : NebulaImageSearchLinks.home(engine));
                }
            });
        });
    }

    private static byte[] encode(Bitmap bitmap) {
        try {
            float scale = Math.min(1f, MAX_SIDE / (float) Math.max(bitmap.getWidth(), bitmap.getHeight()));
            Bitmap source = scale < 1f ? Bitmap.createScaledBitmap(bitmap, Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true) : bitmap;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            source.compress(Bitmap.CompressFormat.JPEG, 90, out);
            if (source != bitmap) source.recycle();
            return out.toByteArray();
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static String lens(byte[] jpeg) throws Exception {
        String boundary = "nebula" + UUID.randomUUID().toString().replace("-", "");
        HttpURLConnection connection = open(NebulaImageSearchLinks.LENS_UPLOAD);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"encoded_image\"; filename=\"image.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes("UTF-8"));
            out.write(jpeg);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes("UTF-8"));
        }
        int code = connection.getResponseCode();
        String location = connection.getHeaderField("Location");
        connection.disconnect();
        return code >= 300 && code < 400 ? NebulaImageSearchLinks.lensResult(location) : null;
    }

    private static String yandex(byte[] jpeg) throws Exception {
        HttpURLConnection connection = open(NebulaImageSearchLinks.YANDEX_UPLOAD);
        connection.setRequestProperty("Content-Type", "image/jpeg");
        try (OutputStream out = connection.getOutputStream()) {
            out.write(jpeg);
        }
        if (connection.getResponseCode() != 200) {
            connection.disconnect();
            return null;
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try (InputStream in = connection.getInputStream()) {
            byte[] buffer = new byte[8192];
            for (int read; (read = in.read(buffer)) > 0 && body.size() < 65536; ) body.write(buffer, 0, read);
        }
        connection.disconnect();
        return NebulaImageSearchLinks.yandexResult(body.toString("UTF-8"));
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("User-Agent", AGENT);
        return connection;
    }

    private static void toast(String text) {
        Toast.makeText(ApplicationLoader.applicationContext, text, Toast.LENGTH_SHORT).show();
    }
}
