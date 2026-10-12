package app.nebulagram.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

/** Explicit paste only: screen creation and refresh never inspect the clipboard. */
public final class NebulaLinkClipboard {
    private NebulaLinkClipboard() { }
    public static String read(Context context) {
        try {
            ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager == null || !manager.hasPrimaryClip()) return "";
            ClipData clip = manager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return "";
            CharSequence value = clip.getItemAt(0).getText();
            if (value == null || value.length() > 512 * 1024) return "";
            return value.toString().trim();
        } catch (SecurityException | IllegalStateException unavailable) { return ""; }
    }
    public static String title() { return NebulaText.text("Из буфера", "From clipboard"); }
    public static String empty() { return NebulaText.text("В буфере нет текста для импорта", "No import text in the clipboard"); }
}
