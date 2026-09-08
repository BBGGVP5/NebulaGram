package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/** Uses the active Telegram wallpaper without changing the shared drawable's state. */
public final class NebulaWallpaperPreview extends FrameLayout {
    public NebulaWallpaperPreview(Context context) {
        super(context);
        setWillNotDraw(false);
        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Theme.getColor(Theme.key_chat_wallpaper));
        outline.setCornerRadius(AndroidUtilities.dp(20));
        setBackground(outline);
        setClipToOutline(true);
    }

    @Override protected void onDraw(Canvas canvas) {
        drawWallpaper(canvas, getWidth(), getHeight());
    }

    public static void drawWallpaper(Canvas canvas, int width, int height) {
        Drawable wallpaper = Theme.getCachedWallpaperNonBlocking();
        if (wallpaper == null) {
            canvas.drawColor(Theme.getColor(Theme.key_chat_wallpaper));
            return;
        }
        Rect previous = new Rect(wallpaper.getBounds());
        int w = width, h = height;
        int naturalW = wallpaper.getIntrinsicWidth(), naturalH = wallpaper.getIntrinsicHeight();
        if (naturalW > 0 && naturalH > 0) {
            float scale = Math.max(w / (float) naturalW, h / (float) naturalH);
            w = Math.round(naturalW * scale);
            h = Math.round(naturalH * scale);
        }
        int left = (width - w) / 2, top = (height - h) / 2;
        int save = canvas.save();
        canvas.clipRect(0, 0, width, height);
        try {
            wallpaper.setBounds(left, top, left + w, top + h);
            wallpaper.draw(canvas);
        } finally {
            wallpaper.setBounds(previous);
            canvas.restoreToCount(save);
        }
    }
}
