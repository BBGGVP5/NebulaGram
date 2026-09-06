package app.nebulagram.ui;

import android.graphics.*;

/** Opaque initials while a notification's photo is absent or undecodable. */
public final class NebulaNotificationAvatar {
    private NebulaNotificationAvatar() { }
    public static Bitmap fallback(long id, CharSequence name) {
        Bitmap bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int[] colors = {0xff4679bc, 0xff8a5caa, 0xff428b72, 0xffb66848, 0xff458f9d};
        paint.setColor(colors[(int) ((id % colors.length + colors.length) % colors.length)]); canvas.drawCircle(48, 48, 48, paint);
        String title = name == null ? "" : name.toString().trim();
        String initial = title.isEmpty() ? "?" : title.substring(0, title.offsetByCodePoints(0, 1)).toUpperCase(java.util.Locale.ROOT);
        paint.setColor(Color.WHITE); paint.setTextSize(40); paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(initial, 48, 48 - (paint.ascent() + paint.descent()) / 2, paint);
        return bitmap;
    }
}
