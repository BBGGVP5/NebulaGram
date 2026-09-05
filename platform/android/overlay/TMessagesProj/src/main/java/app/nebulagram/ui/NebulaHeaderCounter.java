package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import org.telegram.messenger.AndroidUtilities;

/** Shared geometry and rendering for the preview and the live chat back control. */
public final class NebulaHeaderCounter {
    private static final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF bounds = new RectF();
    private NebulaHeaderCounter() { }

    public static int backWidth() {
        return AndroidUtilities.dp(NebulaAppearance.headerUnread() && NebulaAppearance.iosUnread() ? 76 : 58);
    }

    public static void draw(Canvas canvas, Context context, float left, float centerY, int count) {
        if (!NebulaAppearance.headerUnread() || count <= 0) return;
        NebulaTheme theme = NebulaTheme.of(context);
        boolean ios = NebulaAppearance.iosUnread();
        String text = count > 99 ? "99+" : Integer.toString(count);
        paint.setTypeface(AndroidUtilities.bold());
        paint.setTextSize(AndroidUtilities.dp(ios ? 12 : 10));
        paint.setTextAlign(Paint.Align.CENTER);
        float height = AndroidUtilities.dp(ios ? 22 : 18);
        float width = Math.max(height, paint.measureText(text) + AndroidUtilities.dp(8));
        float x = left + AndroidUtilities.dp(ios ? 52 : 43);
        float y = centerY - AndroidUtilities.dp(ios ? 0 : 17);
        bounds.set(x - width / 2, y - height / 2, x + width / 2, y + height / 2);
        paint.setColor(ios ? theme.onSurface() : theme.primary());
        canvas.drawRoundRect(bounds, height / 2, height / 2, paint);
        paint.setColor(ios ? theme.surface() : theme.onPrimary());
        canvas.drawText(text, x, y - (paint.ascent() + paint.descent()) / 2, paint);
    }
}
