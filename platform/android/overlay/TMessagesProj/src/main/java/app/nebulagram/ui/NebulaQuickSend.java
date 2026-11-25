package app.nebulagram.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import java.util.ArrayList;
import java.util.List;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.BackupImageView;

/**
 * Hold the attach button for a row of the latest gallery photos, or the emoji button for recent
 * stickers. Slide onto one and lift the finger to send it; lifting without sliding leaves the row
 * open for a tap. Setting "quick_send_hold", off by default: long presses then do nothing new.
 */
public final class NebulaQuickSend {
    private NebulaQuickSend() { }
    public static final String KEY = "quick_send_hold";
    static final int LIMIT = 8;
    public interface Pick<T> { void send(T item); }

    private static PopupWindow popup;
    private static LinearLayout row;
    private static final ArrayList<Object> items = new ArrayList<>();
    private static Pick<Object> pick;
    private static int selected = -1;
    private static float downX, downY;
    private static boolean moved;

    public static boolean enabled() { return NebulaMessagePreferences.enabled(KEY, false); }

    /** The newest still photos of the gallery Telegram already loaded, at most {@link #LIMIT}. */
    static List<MediaController.PhotoEntry> recentPhotos(List<MediaController.PhotoEntry> all) {
        ArrayList<MediaController.PhotoEntry> out = new ArrayList<>();
        if (all == null) return out;
        for (MediaController.PhotoEntry entry : all) {
            if (entry == null || entry.isVideo || entry.path == null) continue;
            out.add(entry);
            if (out.size() == LIMIT) break;
        }
        return out;
    }

    static List<TLRPC.Document> recentStickers(List<TLRPC.Document> all) {
        ArrayList<TLRPC.Document> out = new ArrayList<>();
        if (all == null) return out;
        for (TLRPC.Document document : all) {
            if (document == null) continue;
            out.add(document);
            if (out.size() == LIMIT) break;
        }
        return out;
    }

    private static boolean galleryAllowed(Context context) {
        String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** Attach button long press. Returns false (nothing shown) when off, not allowed or empty. */
    @SuppressWarnings("unchecked")
    public static boolean photos(View anchor, boolean allowed, Pick<MediaController.PhotoEntry> send) {
        if (!enabled() || !allowed || anchor == null || !galleryAllowed(anchor.getContext())) return false;
        MediaController.AlbumEntry album = MediaController.allPhotosAlbumEntry;
        if (album == null) {
            // First use in this process: Telegram loads the gallery in the background for next time.
            MediaController.loadGalleryPhotosAlbums(0);
            return false;
        }
        List<MediaController.PhotoEntry> photos = recentPhotos(album.photos);
        if (photos.isEmpty()) return false;
        show(anchor, new ArrayList<Object>(photos), (Pick<Object>) (Pick<?>) send, true);
        return true;
    }

    /** Emoji button long press. */
    @SuppressWarnings("unchecked")
    public static boolean stickers(View anchor, int account, boolean allowed, Pick<TLRPC.Document> send) {
        if (!enabled() || !allowed || anchor == null) return false;
        List<TLRPC.Document> stickers = recentStickers(MediaDataController.getInstance(account).getRecentStickersNoCopy(MediaDataController.TYPE_IMAGE));
        if (stickers.isEmpty()) return false;
        show(anchor, new ArrayList<Object>(stickers), (Pick<Object>) (Pick<?>) send, false);
        return true;
    }

    private static void show(View anchor, ArrayList<Object> content, Pick<Object> target, boolean photo) {
        dismiss();
        Context context = anchor.getContext();
        NebulaTheme theme = NebulaTheme.of(context);
        int size = AndroidUtilities.dp(photo ? 56 : 60), gap = AndroidUtilities.dp(6), pad = AndroidUtilities.dp(8);
        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(pad, pad, pad, pad);
        GradientDrawable surface = new GradientDrawable();
        surface.setColor(theme.surface());
        surface.setCornerRadius(AndroidUtilities.dp(22));
        row.setBackground(surface);
        row.setElevation(AndroidUtilities.dp(6));
        items.clear();
        items.addAll(content);
        for (int i = 0; i < content.size(); i++) {
            Object item = content.get(i);
            BackupImageView image = new BackupImageView(context);
            if (item instanceof MediaController.PhotoEntry) {
                MediaController.PhotoEntry entry = (MediaController.PhotoEntry) item;
                image.setRoundRadius(AndroidUtilities.dp(12));
                image.setOrientation(entry.orientation, entry.invert, true);
                image.setImage("thumb://" + entry.imageId + ":" + entry.path, null, (android.graphics.drawable.Drawable) null);
            } else {
                TLRPC.Document sticker = (TLRPC.Document) item;
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
                if (thumb != null) image.setImage(ImageLocation.getForDocument(thumb, sticker), "80_80", (android.graphics.drawable.Drawable) null, sticker);
                else image.setImage(ImageLocation.getForDocument(sticker), "80_80", (android.graphics.drawable.Drawable) null, sticker);
            }
            final int index = i;
            image.setOnClickListener(v -> choose(index));
            FrameLayout cell = new FrameLayout(context);
            cell.addView(image, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size + gap, size + gap);
            row.addView(cell, params);
        }
        pick = target;
        selected = -1;
        moved = false;
        popup = new PopupWindow(row, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(android.R.style.Animation_Dialog);
        popup.setOnDismissListener(() -> { popup = null; row = null; pick = null; items.clear(); selected = -1; });
        row.measure(View.MeasureSpec.makeMeasureSpec(AndroidUtilities.displaySize.x, View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int width = row.getMeasuredWidth(), height = row.getMeasuredHeight();
        int x = position(at[0] + anchor.getWidth() / 2 - width / 2, width, anchor.getRootView().getWidth(), AndroidUtilities.dp(8));
        int y = Math.max(AndroidUtilities.dp(8), at[1] - height - AndroidUtilities.dp(8));
        popup.showAtLocation(anchor.getRootView(), Gravity.NO_GRAVITY, x, y);
        anchor.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    /** Keeps the row inside the window with a margin. */
    static int position(int wanted, int width, int window, int margin) {
        return Math.max(margin, Math.min(wanted, window - width - margin));
    }

    /**
     * The button's touch listener: while the row is up, the same finger slides over it.
     * Returns true when the event belongs to the row.
     */
    public static boolean touch(View button, MotionEvent event) {
        if (popup == null || row == null) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { downX = event.getRawX(); downY = event.getRawY(); }
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE) {
            if (Math.hypot(event.getRawX() - downX, event.getRawY() - downY) > AndroidUtilities.dp(10)) moved = true;
            int index = hit(event.getRawX(), event.getRawY());
            if (index != selected) {
                selected = index;
                highlight();
                if (index >= 0) button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            int index = hit(event.getRawX(), event.getRawY());
            if (index >= 0) choose(index);
            else if (moved) dismiss();
            // Lifted in place: the row stays for a tap. The button still sees the release, so its
            // pressed state clears; the consumed long press keeps it from counting as a click.
            return false;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            if (moved) dismiss();
            return false;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            // A new press on the button while the row waits for a tap closes it.
            dismiss();
            return true;
        }
        return true;
    }

    private static int hit(float rawX, float rawY) {
        if (row == null) return -1;
        int[] at = new int[2];
        for (int i = 0; i < row.getChildCount(); i++) {
            View cell = row.getChildAt(i);
            cell.getLocationOnScreen(at);
            if (rawX >= at[0] && rawX < at[0] + cell.getWidth() && rawY >= at[1] - AndroidUtilities.dp(16) && rawY < at[1] + cell.getHeight() + AndroidUtilities.dp(16)) return i;
        }
        return -1;
    }

    private static void highlight() {
        if (row == null) return;
        for (int i = 0; i < row.getChildCount(); i++) {
            View cell = row.getChildAt(i);
            float scale = i == selected ? 1.18f : 1f;
            cell.animate().scaleX(scale).scaleY(scale).setDuration(140).start();
        }
    }

    private static void choose(int index) {
        Pick<Object> target = pick;
        Object item = index >= 0 && index < items.size() ? items.get(index) : null;
        dismiss();
        if (target != null && item != null) target.send(item);
    }

    public static void dismiss() {
        PopupWindow window = popup;
        popup = null;
        if (window != null) {
            try { window.dismiss(); } catch (Exception ignored) { }
        }
    }
}
