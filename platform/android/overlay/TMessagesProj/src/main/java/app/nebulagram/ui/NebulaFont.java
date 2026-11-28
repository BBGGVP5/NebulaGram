package app.nebulagram.ui;

import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.util.AtomicFile;
import org.telegram.messenger.ApplicationLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Persistent font choice; imported fonts never replace bundled monospace assets. */
public final class NebulaFont {
    private static Typeface custom;
    private NebulaFont() { }
    private static SharedPreferences preferences() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0);
    }
    private static File file() {
        return new File(ApplicationLoader.applicationContext.getFilesDir(), "nebula-custom-font.ttf");
    }
    public static boolean system() { return preferences().getInt("font_mode", 0) == 1; }
    public static boolean customEnabled() { return preferences().getInt("font_mode", 0) == 2; }
    public static synchronized boolean hasCustom() { return file().isFile(); }
    public static void choose(int mode) {
        if (mode < 0 || mode > 2 || (mode == 2 && !hasCustom())) return;
        preferences().edit().putInt("font_mode", mode).apply();
    }
    public static synchronized void install(InputStream input) throws IOException {
        byte[] data = NebulaFontFile.read(input);
        File candidate = File.createTempFile("nebula-font-", ".ttf", ApplicationLoader.applicationContext.getCacheDir());
        try {
            try (FileOutputStream out = new FileOutputStream(candidate)) { out.write(data); }
            Typeface validated;
            try { validated = Typeface.createFromFile(candidate); }
            catch (RuntimeException error) { throw new IOException("Invalid font", error); }
            AtomicFile destination = new AtomicFile(file());
            FileOutputStream out = null;
            try {
                out = destination.startWrite();
                out.write(data);
                destination.finishWrite(out);
            } catch (IOException error) {
                destination.failWrite(out);
                throw error;
            }
            custom = validated;
        } finally { candidate.delete(); }
    }
    public static synchronized Typeface substitute(String path) {
        if (path == null || !path.startsWith("fonts/r") || path.contains("mono")) return null;
        int style = path.contains("italic") ? Typeface.ITALIC : Typeface.NORMAL;
        if (path.contains("medium") || path.contains("bold")) style |= Typeface.BOLD;
        if (system()) return Typeface.create(Typeface.DEFAULT, style);
        if (!customEnabled()) return null;
        try {
            if (custom == null) {
                AtomicFile stored = new AtomicFile(file());
                try (InputStream input = stored.openRead()) { NebulaFontFile.read(input); }
                custom = Typeface.createFromFile(file());
            }
            return Typeface.create(custom, style);
        } catch (IOException | RuntimeException error) { return null; }
    }
}
