package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import org.telegram.messenger.AndroidUtilities;

/** Distinct scan/create glyphs with the same outline weight as the menu icons. */
public final class NebulaQrIcon extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shape = new Path();

    public NebulaQrIcon(boolean scan) {
        paint.setColor(0xffffffff);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.65f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        if (scan) {
            shape.moveTo(7,2);shape.lineTo(2,2);shape.lineTo(2,7);
            shape.moveTo(17,2);shape.lineTo(22,2);shape.lineTo(22,7);
            shape.moveTo(2,17);shape.lineTo(2,22);shape.lineTo(7,22);
            shape.moveTo(17,22);shape.lineTo(22,22);shape.lineTo(22,17);
            square(6,6,4);square(14,6,4);square(6,14,4);
            shape.moveTo(14,14);shape.lineTo(18,14);shape.lineTo(18,18);
        } else {
            square(2,2,7);square(14,2,7);square(2,14,7);
            shape.moveTo(14,18);shape.lineTo(22,18);
            shape.moveTo(18,14);shape.lineTo(18,22);
        }
    }

    private void square(float x, float y, float size) {
        shape.addRoundRect(x,y,x+size,y+size,1,1,Path.Direction.CW);
    }

    @Override public void draw(Canvas canvas) {
        int save = canvas.save();
        canvas.translate(getBounds().left,getBounds().top);
        canvas.scale(getBounds().width()/24f,getBounds().height()/24f);
        canvas.drawPath(shape,paint);
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha);invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter);invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return AndroidUtilities.dp(24); }
    @Override public int getIntrinsicHeight() { return AndroidUtilities.dp(24); }
}
