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
        Drawable wallpaper = Theme.getCachedWallpaperNonBlocking();
        if (wallpaper == null) {
            canvas.drawColor(Theme.getColor(Theme.key_chat_wallpaper));
            return;
        }
        Rect previous = new Rect(wallpaper.getBounds());
        int w = getWidth(), h = getHeight();
        int naturalW = wallpaper.getIntrinsicWidth(), naturalH = wallpaper.getIntrinsicHeight();
        if (naturalW > 0 && naturalH > 0) {
            float scale = Math.max(w / (float) naturalW, h / (float) naturalH);
            w = Math.round(naturalW * scale);
            h = Math.round(naturalH * scale);
        }
        int left = (getWidth() - w) / 2, top = (getHeight() - h) / 2;
        wallpaper.setBounds(left, top, left + w, top + h);
        wallpaper.draw(canvas);
        wallpaper.setBounds(previous);
    }
}
