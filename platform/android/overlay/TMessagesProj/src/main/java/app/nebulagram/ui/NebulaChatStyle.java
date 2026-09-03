package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;

/** Chat titles float above the wallpaper; search and selection retain a surface. */
public final class NebulaChatStyle {
    private NebulaChatStyle() {}

    public static void header(ActionBar bar, boolean normalChat) {
        if (bar != null) bar.setNebulaFloatingChatHeader(normalChat && NebulaAppearance.chatHeader());
    }

    /** A quiet title capsule retains contrast even over a bright photo wallpaper. */
    public static final class TitleSurface {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();

        public void draw(Canvas canvas, Rect area, int titleColor, int backgroundColor) {
            boolean lightText = ColorUtils.calculateLuminance(titleColor) > 0.4;
            int base = lightText ? 0xFF14171D : Color.WHITE;
            int tint = ColorUtils.blendARGB(base, backgroundColor, 0.16f);
            bounds.set(area);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ColorUtils.setAlphaComponent(tint, 244));
            canvas.drawRoundRect(bounds, AndroidUtilities.dp(20), AndroidUtilities.dp(20), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, AndroidUtilities.dpf2(0.5f)));
            paint.setColor(lightText ? 0x18FFFFFF : 0x12000000);
            canvas.drawRoundRect(bounds, AndroidUtilities.dp(20), AndroidUtilities.dp(20), paint);
        }
    }
}
