package app.nebulagram.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

/** Routes PDF documents from chats to the built-in viewer; the external app path stays one tap away. */
public final class NebulaPdfViewer {
    private NebulaPdfViewer() { }

    public static boolean isPdf(String fileName, String mimeType) {
        return "application/pdf".equalsIgnoreCase(mimeType)
            || fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    /** Opens {@code file} in the viewer and returns true, or returns false to keep Telegram's behaviour. */
    public static boolean open(File file, String fileName, String mimeType, Activity activity, boolean restricted) {
        // The three Telegram entry points already call this method; SVG drawings share them.
        if (NebulaSvgViewer.open(file, fileName, mimeType, activity, restricted)) return true;
        if (!NebulaMediaPreferences.pdfViewer() || file == null || !file.exists() || !isPdf(fileName, mimeType) || !signature(file)) return false;
        BaseFragment host = LaunchActivity.getSafeLastFragment();
        if (host == null || activity == null) return false;
        String title = fileName == null || fileName.isEmpty() ? file.getName() : fileName;
        return host.presentFragment(new NebulaPdfViewerFragment(file, title, restricted));
    }

    private static boolean signature(File file) {
        byte[] head = new byte[5];
        try (FileInputStream input = new FileInputStream(file)) {
            // Telegram stores some downloads without an extension; trust the content, not the name.
            return input.read(head) == 5 && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F' && head[4] == '-';
        } catch (Exception error) {
            return false;
        }
    }

    static Uri uri(Activity activity, File file) {
        return FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file);
    }

    static void openExternally(Activity activity, File file) { openExternally(activity, file, "application/pdf"); }
    static void openExternally(Activity activity, File file, String mime) {
        if (activity == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri(activity, file), mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, NebulaText.text("Открыть в приложении", "Open with")));
        } catch (Exception error) {
            FileLog.e(error);
            Toast.makeText(activity, "application/pdf".equals(mime) ? NebulaText.text("Нет приложения для PDF", "No app can open PDF files") : NebulaText.text("Нет приложения для этого файла", "No app can open this file"), Toast.LENGTH_SHORT).show();
        }
    }

    static void share(Activity activity, File file, String title) { share(activity, file, title, "application/pdf"); }
    static void share(Activity activity, File file, String title, String mime) {
        if (activity == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(mime);
            intent.putExtra(Intent.EXTRA_STREAM, uri(activity, file));
            intent.putExtra(Intent.EXTRA_TITLE, title);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, NebulaText.text("Поделиться", "Share")));
        } catch (Exception error) {
            FileLog.e(error);
        }
    }
}
