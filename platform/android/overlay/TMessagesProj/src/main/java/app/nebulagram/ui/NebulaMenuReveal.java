package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import android.view.View;
import java.lang.ref.WeakReference;

/** A growing material boundary; menu labels retain their natural size throughout. */
public final class NebulaMenuReveal {
    private final ActionBarPopupWindowLayout host;
    private WeakReference<View> anchor;
    private final RectF bounds = new RectF();
    private final Path clip = new Path();
    private final int[] location = new int[2];
    private float progress = 1, originX, originY;

    public NebulaMenuReveal(ActionBarPopupWindowLayout host) { this.host = host; }
    public void setAnchor(View view) { anchor = view == null ? null : new WeakReference<>(view); }
    public void begin() {
        float size = initialHalfSize();
        originX = host.getMeasuredWidth() - size;
        originY = host.shownFromBottom ? host.getMeasuredHeight() - size : size;
        View view = anchor == null ? null : anchor.get();
        if (view != null && view.isAttachedToWindow() && host.isAttachedToWindow()) {
            view.getLocationOnScreen(location);
            float x = location[0] + view.getWidth() / 2f, y = location[1] + view.getHeight() / 2f;
            host.getLocationOnScreen(location);
            originX = Math.max(size, Math.min(host.getMeasuredWidth() - size, x - location[0]));
            originY = Math.max(size, Math.min(host.getMeasuredHeight() - size, y - location[1]));
        }
        setProgress(0);
    }
    public void setProgress(float value) { progress = Math.max(0, Math.min(1, value)); host.invalidate(); }
    private float shape(Rect finalBounds) {
        float half = initialHalfSize(), p = progress;
        bounds.set((originX-half)*(1-p)+finalBounds.left*p,
                (originY-half)*(1-p)+finalBounds.top*p,
                (originX+half)*(1-p)+finalBounds.right*p,
                (originY+half)*(1-p)+finalBounds.bottom*p);
        return Math.max(0, half-AndroidUtilities.dp(8))*(1-p)+NebulaMenuStyle.radius()*p;
    }
    private float initialHalfSize() {
        return Math.min(AndroidUtilities.dp(36), Math.min(host.getMeasuredWidth(), host.getMeasuredHeight()) / 2f);
    }
    public void applyBounds(Rect rect, Drawable material) {
        if (progress >= 1) return;
        float radius = shape(rect);
        rect.set(Math.round(bounds.left), Math.round(bounds.top), Math.round(bounds.right), Math.round(bounds.bottom));
        if (material instanceof BlurredBackgroundDrawable) {
            BlurredBackgroundDrawable glass = (BlurredBackgroundDrawable) material;
            glass.setRadius(radius);
            glass.setThickness(AndroidUtilities.dp(5));
            glass.setIntensity(.22f + .20f * (float)Math.sin(progress * Math.PI));
        }
    }
    public void clip(Canvas canvas) {
        if (progress >= 1) return;
        float radius = shape(new Rect(0, 0, host.getMeasuredWidth(), host.getMeasuredHeight()));
        bounds.inset(AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        clip.rewind(); clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);
    }
    public void finish() {
        setProgress(1);
        Drawable material = host.getBackgroundDrawable();
        if (material instanceof BlurredBackgroundDrawable) {
            ((BlurredBackgroundDrawable) material).setRadius(NebulaMenuStyle.radius());
            ((BlurredBackgroundDrawable) material).setIntensity(.22f);
        }
    }
}
