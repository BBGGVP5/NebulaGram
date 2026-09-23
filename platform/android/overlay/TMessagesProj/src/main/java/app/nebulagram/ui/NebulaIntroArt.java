package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

/** Small native illustrations for the welcome pages; no screenshot or remote asset. */
public final class NebulaIntroArt extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final NebulaTheme theme;
    private int page;

    public NebulaIntroArt(Context context) {
        super(context);
        theme = NebulaTheme.of(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public void setPage(int page) {
        this.page = page;
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                AndroidUtilities.dp(250 * NebulaLoginStyle.vertical()));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth() / 320f, getHeight() / 250f);
        canvas.save();
        canvas.translate((getWidth() - 320 * scale) / 2f, (getHeight() - 250 * scale) / 2f);
        canvas.scale(scale, scale);
        orb(canvas, 160, 124, 104, 0x223BCFD8);
        orb(canvas, 252, 43, 27, 0x225968F7);
        switch (page) {
            case 1: drawDesign(canvas); break;
            case 2: drawPrivacy(canvas); break;
            case 3: drawAI(canvas); break;
            case 4: drawLink(canvas); break;
            default: drawWelcome(canvas); break;
        }
        canvas.restore();
    }

    private void drawWelcome(Canvas c) {
        box(c, 52, 16, 268, 225, 26, theme.surfaceContainer());
        box(c, 65, 30, 255, 65, 14, theme.primaryContainer());
        label(c, "NebulaGram", 80, 53, 17, theme.onPrimaryContainer(), true);
        for (int i = 0; i < 3; i++) {
            int y = 82 + i * 42;
            orb(c, 86, y + 12, 14, i == 1 ? 0xFF776AF2 : 0xFF32BED8);
            box(c, 110, y, 233, y + 10, 5, theme.outline());
            box(c, 110, y + 16, 194 + i * 10, y + 23, 4, theme.primaryContainer());
        }
        box(c, 74, 201, 246, 216, 8, theme.primaryContainer());
        for (int i = 0; i < 3; i++) orb(c, 110 + i * 50, 208, 4, theme.primary());
        sparkle(c, 264, 110, 14, 0xFF61DEEB);
        sparkle(c, 43, 172, 8, 0xFF8475FF);
    }

    private void drawDesign(Canvas c) {
        box(c, 42, 38, 228, 211, 23, theme.surfaceContainer());
        box(c, 55, 51, 215, 87, 12, theme.primaryContainer());
        label(c, "Aa", 72, 77, 22, theme.onPrimaryContainer(), true);
        for (int i = 0; i < 3; i++) {
            orb(c, 77 + i * 48, 119, 17,
                    new int[]{0xFF42D5E1, 0xFF6478EF, 0xFF9A77EA}[i]);
        }
        box(c, 55, 157, 215, 190, 16, theme.primaryContainer());
        box(c, 61, 162, 107, 185, 12, theme.primary());
        for (int i = 0; i < 3; i++) orb(c, 83 + i * 52, 174, 4, theme.onPrimaryContainer());
        box(c, 191, 78, 281, 169, 21, 0xFF39475F);
        box(c, 202, 90, 270, 109, 9, 0xFF5E7CF1);
        box(c, 202, 119, 254, 129, 5, 0xFF87DBEA);
        box(c, 202, 138, 264, 148, 5, 0xFFA5B6D3);
        sparkle(c, 276, 52, 12, 0xFF70DBE8);
    }

    private void drawPrivacy(Canvas c) {
        box(c, 43, 38, 277, 207, 27, theme.surfaceContainer());
        box(c, 60, 55, 230, 106, 18, theme.primaryContainer());
        box(c, 73, 69, 193, 79, 5, theme.onPrimaryContainer());
        box(c, 73, 86, 210, 93, 4, theme.outline());
        box(c, 90, 121, 259, 174, 18, 0xFF3A62BE);
        box(c, 108, 136, 230, 146, 5, 0xFFE9F5FF);
        box(c, 108, 154, 190, 161, 4, 0xFFA9D3FF);
        orb(c, 58, 175, 29, 0xFF7181F0);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4);
        paint.setColor(0xFFFFFFFF);
        c.drawRoundRect(46, 171, 70, 187, 5, 5, paint);
        c.drawArc(50, 159, 66, 179, 185, 170, false, paint);
        paint.setStyle(Paint.Style.FILL);
        sparkle(c, 265, 78, 12, 0xFF58D8E6);
    }

    private void drawAI(Canvas c) {
        box(c, 51, 24, 269, 215, 28, theme.surfaceContainer());
        box(c, 67, 39, 252, 86, 17, theme.primaryContainer());
        label(c, "Nebula AI", 83, 69, 18, theme.onPrimaryContainer(), true);
        box(c, 89, 111, 240, 143, 15, theme.primaryContainer());
        box(c, 75, 158, 220, 193, 16, 0xFF3C5FC5);
        box(c, 92, 171, 182, 179, 4, 0xFFE3F5FF);
        orb(c, 54, 114, 27, 0xFF52D6E4);
        sparkle(c, 54, 114, 18, 0xFFFFFFFF);
        sparkle(c, 270, 179, 15, 0xFF867BFF);
    }

    private void drawLink(Canvas c) {
        box(c, 43, 26, 277, 218, 26, theme.surfaceContainer());
        box(c, 58, 40, 262, 89, 16, theme.primaryContainer());
        label(c, "NebulaLink", 75, 71, 18, theme.onPrimaryContainer(), true);
        paint.setColor(theme.primary());
        paint.setStrokeWidth(4);
        paint.setStyle(Paint.Style.STROKE);
        path.reset();
        path.moveTo(92, 143);
        path.cubicTo(132, 111, 174, 194, 228, 143);
        c.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
        orb(c, 91, 143, 17, 0xFF4FD5E3);
        orb(c, 162, 154, 20, 0xFF6A83F0);
        orb(c, 229, 143, 17, 0xFF52D9C3);
        box(c, 79, 191, 241, 204, 7, theme.primaryContainer());
        for (int i = 0; i < 3; i++) orb(c, 105 + i * 55, 197, 3, theme.primary());
        sparkle(c, 267, 108, 11, 0xFF62DDEA);
    }

    private void box(Canvas c, float left, float top, float right, float bottom, float radius, int color) {
        rect.set(left, top, right, bottom);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(color);
        c.drawRoundRect(rect, radius, radius, paint);
    }

    private void orb(Canvas c, float x, float y, float radius, int color) {
        paint.setShader(null);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        c.drawCircle(x, y, radius, paint);
    }

    private void sparkle(Canvas c, float x, float y, float radius, int color) {
        path.reset();
        path.moveTo(x, y - radius);
        path.quadTo(x + radius * .2f, y - radius * .2f, x + radius, y);
        path.quadTo(x + radius * .2f, y + radius * .2f, x, y + radius);
        path.quadTo(x - radius * .2f, y + radius * .2f, x - radius, y);
        path.quadTo(x - radius * .2f, y - radius * .2f, x, y - radius);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        c.drawPath(path, paint);
    }

    private void label(Canvas c, String value, float x, float baseline, float size, int color, boolean bold) {
        paint.setShader(null);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(bold ? AndroidUtilities.bold() : null);
        c.drawText(value, x, baseline, paint);
    }
}
