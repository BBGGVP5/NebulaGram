package app.nebulagram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.palette.graphics.Palette;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.util.List;

/**
 * Accent from a picture: the user's own avatar or the current chat wallpaper.
 *
 * <p>This is only the picture side — finding the bitmap, running androidx
 * Palette off the UI thread and noticing when the picture changes. Choosing
 * the colour is {@link NebulaPictureAccent}; handing it to Telegram is
 * {@link NebulaTheme#applyMaterialYou}, the same path Material You uses, so
 * the accent survives restarts and reaches screens NebulaGram does not own.
 */
public final class NebulaPictureTheme {
    private NebulaPictureTheme() { }

    public static final int OFF = 0, AVATAR = 1, WALLPAPER = 2;
    private static final int SAMPLE = 112;

    /** What one extraction found; the preview draws it. */
    public static final class Result {
        public final int source;
        public final Bitmap thumbnail;
        public final int[] swatches;
        public final int seed;
        Result(int source, Bitmap thumbnail, int[] swatches, int seed) {
            this.source = source;
            this.thumbnail = thumbnail;
            this.swatches = swatches;
            this.seed = seed;
        }
    }

    public interface Listener { void onResult(Result result); }

    private static Listener listener;
    private static Result last;
    private static boolean started;
    private static long avatarPhotoId = Long.MIN_VALUE;
    private static boolean avatarMissing;
    private static int generation;

    public static String[] names() {
        return new String[]{NebulaText.text("Выключено", "Off"), NebulaText.text("Моя аватарка", "My avatar"),
                NebulaText.text("Обои чата", "Chat wallpaper")};
    }

    public static int source() {
        return NebulaTheme.pictureSource();
    }

    /** The settings preview listens while it is on screen. */
    public static void setListener(Listener value) {
        listener = value;
    }

    public static void clearListener(Listener value) {
        if (listener == value) listener = null;
    }

    public static void setSource(Context context, int source) {
        SharedPreferences.Editor editor = prefs().edit().putInt(NebulaTheme.KEY_PICTURE_SOURCE, source);
        if (source == OFF) editor.remove(NebulaTheme.KEY_PICTURE_SEED);
        editor.apply();
        if (source == OFF) {
            NebulaTheme.refreshAccentSource(context);
            Theme.reloadAllResources(context);
            notify(new Result(OFF, null, new int[0], 0));
        } else {
            refresh();
        }
    }

    private static final java.util.Set<Integer> watched = new java.util.HashSet<>();

    private static final NotificationCenter.NotificationCenterDelegate observer = (id, account, args) -> {
        int source = source();
        if (id == NotificationCenter.didSetNewWallpapper) {
            if (source == WALLPAPER) schedule();
        } else if (id == NotificationCenter.activeAccountChanged) {
            watch(UserConfig.selectedAccount);
            if (source == AVATAR) schedule(); // another account, another avatar
        } else if (source == AVATAR && account == UserConfig.selectedAccount) {
            if (id == NotificationCenter.updateInterfaces) {
                int mask = args.length > 0 && args[0] instanceof Integer ? (Integer) args[0] : 0;
                if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 && avatarPhotoId != currentPhotoId()) schedule();
            } else if (id == NotificationCenter.mainUserInfoChanged) {
                if (avatarPhotoId != currentPhotoId()) schedule();
            } else if (id == NotificationCenter.fileLoaded) {
                if (avatarMissing) schedule();
            }
        }
    };

    /** Called once from LaunchActivity, next to applyMaterialYou. */
    public static void start() {
        if (started) return;
        started = true;
        try {
            NotificationCenter global = NotificationCenter.getGlobalInstance();
            global.addObserver(observer, NotificationCenter.didSetNewWallpapper);
            global.addObserver(observer, NotificationCenter.activeAccountChanged);
            // NebulaGram has account slots instead of a fixed four; accounts
            // added later are picked up when they become active.
            for (int account : UserConfig.getAccountIds()) watch(account);
            watch(UserConfig.selectedAccount);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        // The avatar may have changed on another device while the app was closed.
        if (source() != OFF) AndroidUtilities.runOnUIThread(NebulaPictureTheme::refresh, 1500);
    }

    private static void watch(int account) {
        if (!watched.add(account)) return;
        try {
            NotificationCenter center = NotificationCenter.getInstance(account);
            center.addObserver(observer, NotificationCenter.updateInterfaces);
            center.addObserver(observer, NotificationCenter.mainUserInfoChanged);
            center.addObserver(observer, NotificationCenter.fileLoaded);
        } catch (Throwable e) {
            watched.remove(account);
            FileLog.e(e);
        }
    }

    private static final Runnable scheduled = NebulaPictureTheme::refresh;

    private static void schedule() {
        AndroidUtilities.cancelRunOnUIThread(scheduled);
        AndroidUtilities.runOnUIThread(scheduled, 400);
    }

    /** Extracts again from the chosen picture and applies the accent if it moved. */
    public static void refresh() {
        int source = source();
        if (source == OFF) return;
        int ticket = ++generation;
        if (source == WALLPAPER) {
            Bitmap sample = wallpaperSample();
            Utilities.globalQueue.postRunnable(() -> finish(ticket, source, sample));
        } else {
            File file = avatarFile();
            Utilities.globalQueue.postRunnable(() -> finish(ticket, source, decode(file)));
        }
    }

    private static void finish(int ticket, int source, Bitmap sample) {
        int[] rgb = new int[0], population = new int[0];
        if (sample != null) {
            try {
                List<Palette.Swatch> swatches = Palette.from(sample).maximumColorCount(16).generate().getSwatches();
                rgb = new int[swatches.size()];
                population = new int[swatches.size()];
                for (int i = 0; i < swatches.size(); i++) {
                    rgb[i] = swatches.get(i).getRgb();
                    population[i] = swatches.get(i).getPopulation();
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        int seed = NebulaPictureAccent.seed(rgb, population);
        int[] order = NebulaPictureAccent.order(rgb, population);
        int[] chips = new int[Math.min(6, order.length)];
        for (int i = 0; i < chips.length; i++) chips[i] = rgb[order[i]] | 0xFF000000;
        Result result = new Result(source, sample, chips, seed);
        AndroidUtilities.runOnUIThread(() -> {
            if (ticket != generation || source() != source) return; // a newer pick won
            Context context = ApplicationLoader.applicationContext;
            int previous = prefs().getInt(NebulaTheme.KEY_PICTURE_SEED, 0);
            // A grey picture keeps the previous colour rather than dropping to none.
            if (seed != 0 && seed != previous) {
                prefs().edit().putInt(NebulaTheme.KEY_PICTURE_SEED, seed).apply();
                NebulaTheme.refreshAccentSource(context);
                Theme.reloadAllResources(context);
            } else if (previous == 0) {
                NebulaTheme.refreshAccentSource(context);
            }
            notify(result);
        });
    }

    /** The latest extraction for the current source, or null before the first one. */
    public static Result last() {
        Result result = last;
        return result != null && result.source == source() ? result : null;
    }

    private static void notify(Result result) {
        last = result;
        Listener current = listener;
        if (current != null) current.onResult(result);
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebulagram", 0);
    }

    private static TLRPC.User user() {
        try {
            return UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        } catch (Throwable e) {
            return null;
        }
    }

    private static long currentPhotoId() {
        TLRPC.User user = user();
        return user == null || user.photo == null ? 0 : user.photo.photo_id;
    }

    /** The small avatar file; asks Telegram to fetch it when it is not cached yet. */
    private static File avatarFile() {
        TLRPC.User user = user();
        avatarPhotoId = currentPhotoId();
        avatarMissing = false;
        if (user == null || user.photo == null || user.photo.photo_small == null) return null;
        try {
            FileLoader loader = FileLoader.getInstance(UserConfig.selectedAccount);
            File file = loader.getPathToAttach(user.photo.photo_small, true);
            if (file != null && file.exists()) return file;
            avatarMissing = true;
            loader.loadFile(ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL), user, null, FileLoader.PRIORITY_NORMAL, 1);
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return null;
    }

    private static Bitmap decode(File file) {
        if (file == null) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            int side = Math.max(bounds.outWidth, bounds.outHeight);
            options.inSampleSize = 1;
            while (side / (options.inSampleSize * 2) >= SAMPLE) options.inSampleSize *= 2;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    /**
     * The wallpaper as Telegram draws it, gradient and pattern included,
     * scaled down. Drawn on the UI thread because animated backgrounds are
     * not safe to draw from elsewhere; at this size it costs well under a frame.
     */
    private static Bitmap wallpaperSample() {
        try {
            Drawable drawable = Theme.getCachedWallpaperNonBlocking();
            if (drawable == null) return null;
            if (drawable instanceof BitmapDrawable && ((BitmapDrawable) drawable).getBitmap() != null) {
                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                float scale = SAMPLE / (float) Math.max(1, Math.max(bitmap.getWidth(), bitmap.getHeight()));
                return Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * scale)),
                        Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
            }
            Bitmap bitmap = Bitmap.createBitmap(SAMPLE, Math.round(SAMPLE * 1.6f), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            android.graphics.Rect bounds = drawable.copyBounds();
            drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            drawable.draw(canvas);
            drawable.setBounds(bounds);
            return bitmap;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }
}
