package app.nebulagram.ui;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.util.AtomicFile;
import org.json.*;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SvgHelper;
import java.io.*;
import java.util.*;

/** Imported packs share immutable bitmaps; every icon keeps its own tint, bounds and paint. */
public final class NebulaIconPackStore {
    public static final class Pack {
        public final String id, name, author, version;
        Pack(JSONObject json) { id = json.optString("id"); name = json.optString("name"); author = json.optString("author"); version = json.optString("version"); }
    }
    private static final class Snapshot {
        final String id; final Map<String, Bitmap> icons;
        Snapshot(String id, Map<String, Bitmap> icons) { this.id = id; this.icons = icons; }
    }
    private static volatile Snapshot loaded = new Snapshot("", Collections.emptyMap());
    private static boolean startup;
    private static volatile long generation;
    private NebulaIconPackStore() { }
    private static SharedPreferences prefs() { return ApplicationLoader.applicationContext.getSharedPreferences("nebula_icon_packs", 0); }
    public static String selected() { return prefs().getString("selected", ""); }
    public static ArrayList<Pack> list() {
        ArrayList<Pack> result = new ArrayList<>();
        try { JSONArray json = new JSONArray(prefs().getString("packs", "[]")); for (int i = 0; i < Math.min(json.length(), 40); i++) result.add(new Pack(json.getJSONObject(i))); } catch (Exception ignored) { }
        return result;
    }
    private static File file(String id) throws Exception {
        if (!id.matches("[a-zA-Z0-9._-]{1,80}")) throw new IOException("Invalid pack");
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String hash = android.util.Base64.encodeToString(digest, android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "icon-packs"); if (!dir.exists() && !dir.mkdirs()) throw new IOException("Storage unavailable");
        return new File(dir, hash + ".icons");
    }
    private static Map<String, Bitmap> decode(NebulaIconArchive pack) throws Exception {
        HashMap<String, Bitmap> result = new HashMap<>();
        for (Map.Entry<String, byte[]> icon : pack.icons.entrySet()) {
            byte[] bytes = icon.getValue(); Bitmap bitmap;
            if (bytes.length > 3 && new String(bytes, java.nio.charset.StandardCharsets.UTF_8).contains("<svg")) {
                // Telegram's SVG reader understands literal colors, not CSS currentColor.
                String xml = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).replace("currentColor", "#000000");
                // SvgDrawable is Telegram's animated loading placeholder; render the actual SVG bitmap.
                bitmap = SvgHelper.getBitmap(xml, 96, 96, false);
                if (bitmap == null) throw new IOException("Invalid SVG");
            } else {
                BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true; BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                if (bounds.outWidth < 1 || bounds.outHeight < 1 || bounds.outWidth > 1024 || bounds.outHeight > 1024) throw new IOException("Invalid image dimensions");
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length); if (bitmap == null) throw new IOException("Invalid image");
                if (bitmap.getWidth() > 96 || bitmap.getHeight() > 96) bitmap = Bitmap.createScaledBitmap(bitmap, 96, 96, true);
            }
            result.put(icon.getKey(), bitmap);
        }
        return Collections.unmodifiableMap(result);
    }
    /** Call from the import worker; the prior pack remains intact until all icons decode. */
    public static synchronized Pack install(InputStream input) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int n;
        while ((n = input.read(buffer)) != -1) { if (out.size() + n > 16_000_000) throw new IOException("Pack too large"); out.write(buffer, 0, n); }
        byte[] archive = out.toByteArray(); NebulaIconArchive pack = NebulaIconArchive.read(new ByteArrayInputStream(archive)); Map<String, Bitmap> decoded = decode(pack);
        ArrayList<Pack> packs = list(); if (packs.size() >= 40 && packs.stream().noneMatch(p -> p.id.equals(pack.id))) throw new IOException("Too many packs");
        AtomicFile target = new AtomicFile(file(pack.id)); FileOutputStream stream = target.startWrite();
        try { stream.write(archive); target.finishWrite(stream); } catch (Exception e) { target.failWrite(stream); throw e; }
        JSONArray data = new JSONArray(); for (Pack prior : packs) if (!prior.id.equals(pack.id)) data.put(new JSONObject().put("id", prior.id).put("name", prior.name).put("author", prior.author).put("version", prior.version));
        JSONObject metadata = new JSONObject().put("id", pack.id).put("name", pack.name).put("author", pack.author).put("version", pack.version); data.put(metadata);
        prefs().edit().putString("packs", data.toString()).apply();
        generation++;
        if (selected().equals(pack.id)) { loaded = new Snapshot(pack.id, decoded); }
        return new Pack(metadata);
    }
    public static synchronized void select(String id) throws Exception {
        Map<String, Bitmap> decoded;
        try (InputStream input = new AtomicFile(file(id)).openRead()) { decoded = decode(NebulaIconArchive.read(input)); }
        generation++; loaded = new Snapshot(id, decoded); prefs().edit().putString("selected", id).apply(); NebulaIcons.setPack(3);
    }
    public static synchronized void delete(String id) throws Exception {
        generation++;
        JSONArray data = new JSONArray(); for (Pack p : list()) if (!p.id.equals(id)) data.put(new JSONObject().put("id", p.id).put("name", p.name).put("author", p.author).put("version", p.version));
        if (selected().equals(id)) { NebulaIcons.setPack(0); loaded = new Snapshot("", Collections.emptyMap()); prefs().edit().remove("selected").apply(); }
        new AtomicFile(file(id)).delete(); prefs().edit().putString("packs", data.toString()).apply();
    }
    public static void export(String id, OutputStream out) throws Exception { try (InputStream in = new AtomicFile(file(id)).openRead()) { byte[] buffer = new byte[8192]; int n; while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n); } }
    public static Map<String, Bitmap> preview(String id) throws Exception { try (InputStream in = new AtomicFile(file(id)).openRead()) { return decode(NebulaIconArchive.read(in)); } }
    public static synchronized void prepare() {
        if (startup || ApplicationLoader.applicationContext == null) return; startup = true; final long revision = generation;
        new Thread(() -> {
            try {
                String id = selected(); if (id.isEmpty()) return;
                Map<String, Bitmap> decoded;
                try (InputStream in = new AtomicFile(file(id)).openRead()) { decoded = decode(NebulaIconArchive.read(in)); }
                synchronized (NebulaIconPackStore.class) {
                    if (revision != generation || !id.equals(selected())) return;
                    loaded = new Snapshot(id, decoded);
                }
                org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
                    if (revision == generation && NebulaIcons.pack() == 3 && id.equals(selected()))
                        org.telegram.ui.ActionBar.Theme.reloadAllResources(ApplicationLoader.applicationContext);
                });
            } catch (Exception ignored) { }
        }, "NebulaIconPacks").start();
    }
    public static Drawable drawable(Resources resources, int original) {
        if (NebulaIcons.pack() != 3) return null; prepare();
        Snapshot snapshot = loaded;
        if (!snapshot.id.equals(selected())) return null;
        try {
            Bitmap bitmap = snapshot.icons.get(resources.getResourceEntryName(original)); if (bitmap == null) return null;
            Drawable nativeIcon = NebulaIconResources.originalDrawable(resources, original);
            return new Icon(bitmap, nativeIcon.getIntrinsicWidth(), nativeIcon.getIntrinsicHeight());
        } catch (Resources.NotFoundException ignored) { return null; }
    }
    private static final class Icon extends Drawable {
        private final Bitmap bitmap; private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG); private final int width, height;
        private android.content.res.ColorStateList tint;
        private PorterDuff.Mode tintMode = PorterDuff.Mode.SRC_IN;
        Icon(Bitmap bitmap, int width, int height) { this.bitmap = bitmap; this.width = width; this.height = height; }
        @Override public void draw(Canvas canvas) { canvas.drawBitmap(bitmap, null, getBounds(), paint); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public void setTintList(android.content.res.ColorStateList value) { tint = value; applyTint(); }
        @Override public void setTintMode(PorterDuff.Mode mode) { tintMode = mode; applyTint(); }
        @Override public boolean isStateful() { return tint != null && tint.isStateful(); }
        @Override protected boolean onStateChange(int[] state) {
            if (tint == null) return false;
            applyTint(); return true;
        }
        private void applyTint() { setColorFilter(tint == null ? null : new PorterDuffColorFilter(tint.getColorForState(getState(), tint.getDefaultColor()), tintMode)); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return width; }
        @Override public int getIntrinsicHeight() { return height; }
        @Override public ConstantState getConstantState() { return new ConstantState() { @Override public Drawable newDrawable() { return new Icon(bitmap, width, height); } @Override public int getChangingConfigurations() { return 0; } }; }
    }
}
