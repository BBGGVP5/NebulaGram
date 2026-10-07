package app.nebulagram.ui;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Scalable silver/red shield fallback; it never enlarges a small emoji-sheet bitmap. */
public final class NebulaShieldDrawable extends Drawable {
    private final Path outer = new Path(), face = new Path(), red = new Path();
    private final Paint rim = new Paint(3), plate = new Paint(3), accent = new Paint(3), edge = new Paint(3);
    public NebulaShieldDrawable() {
        outer.moveTo(50, 3); outer.cubicTo(66, 8, 83, 11, 94, 20); outer.cubicTo(96, 62, 76, 95, 50, 110);
        outer.cubicTo(24, 95, 4, 62, 6, 20); outer.cubicTo(17, 11, 34, 8, 50, 3); outer.close();
        face.moveTo(50, 12); face.cubicTo(64, 17, 77, 18, 86, 25); face.cubicTo(86, 59, 72, 85, 50, 100);
        face.cubicTo(28, 85, 14, 59, 14, 25); face.cubicTo(25, 18, 37, 17, 50, 12); face.close();
        red.moveTo(50, 12); red.cubicTo(37, 17, 25, 18, 14, 25); red.cubicTo(14, 59, 28, 85, 50, 100);
        red.lineTo(50, 86); red.lineTo(65, 75); red.lineTo(46, 63); red.lineTo(65, 51); red.lineTo(46, 39); red.lineTo(65, 27); red.close();
        rim.setShader(new LinearGradient(0, 0, 100, 110, new int[]{0xFF526171,0xFFE5EDF5,0xFF7B8998,0xFFDCE5EF}, new float[]{0,.32f,.65f,1}, Shader.TileMode.CLAMP));
        plate.setShader(new LinearGradient(15, 10, 90, 110, 0xFFF1F5FC, 0xFF8E9DAF, Shader.TileMode.CLAMP));
        accent.setShader(new LinearGradient(15, 15, 60, 105, 0xFFFC7868, 0xFFB5232B, Shader.TileMode.CLAMP));
        edge.setStyle(Paint.Style.STROKE); edge.setStrokeWidth(.8f); edge.setColor(0xBBF3F7FC);
    }
    @Override public void draw(Canvas c) {
        Rect b = getBounds(); float scale = Math.min(b.width()/100f, b.height()/113f);
        int save = c.save(); c.translate(b.centerX()-50*scale, b.centerY()-56.5f*scale); c.scale(scale,scale);
        c.drawPath(outer,rim); c.drawPath(face,plate); c.drawPath(red,accent); c.drawPath(outer,edge); c.restoreToCount(save);
    }
    @Override public void setAlpha(int a) { rim.setAlpha(a); plate.setAlpha(a); accent.setAlpha(a); edge.setAlpha(a); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter f) { rim.setColorFilter(f); plate.setColorFilter(f); accent.setColorFilter(f); edge.setColorFilter(f); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
