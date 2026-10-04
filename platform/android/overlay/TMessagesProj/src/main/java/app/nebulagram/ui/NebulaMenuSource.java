package app.nebulagram.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import java.lang.ref.WeakReference;
import org.telegram.messenger.AndroidUtilities;

/** A small source control travels at native size; message-sized anchors are never hidden. */
public final class NebulaMenuSource {
    private WeakReference<View> source;
    private Bitmap bitmap;
    private float alpha;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    public void capture(View view) {
        clear();
        if (view == null || !view.isAttachedToWindow() || view.getWidth() <= 0 || view.getHeight() <= 0
                || view.getWidth() > AndroidUtilities.dp(72) || view.getHeight() > AndroidUtilities.dp(72)
                || view.getAlpha() <= 0) return;
        Bitmap candidate = null;
        try {
            candidate = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(candidate));
            bitmap = candidate; alpha = view.getAlpha(); source = new WeakReference<>(view);
            view.setAlpha(0);
        } catch (RuntimeException failure) {
            if (candidate != null) candidate.recycle();
            bitmap = null; source = null;
        }
    }
    public void draw(Canvas canvas, NebulaMenuBubble.Frame frame) {
        if (bitmap == null || frame.content >= 1) return;
        paint.setAlpha(Math.round(alpha * (1 - frame.content) * 255));
        canvas.drawBitmap(bitmap, frame.x - bitmap.getWidth() / 2f, frame.y - bitmap.getHeight() / 2f, paint);
    }
    public void clear() {
        View view = source == null ? null : source.get();
        if (view != null) view.setAlpha(alpha);
        source = null;
        // A recorded hardware display list can still reference the old bitmap until its next frame.
        bitmap = null;
    }
}
