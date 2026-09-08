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
import android.view.MotionEvent;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/** A growing material boundary; menu labels retain their natural size throughout. */
public final class NebulaMenuReveal {
    private final ActionBarPopupWindowLayout host;
    private WeakReference<View> anchor;
    private final RectF bounds = new RectF();
    private final Rect contentBounds = new Rect();
    private final Path clip = new Path();
    private final int[] location = new int[2];
    private float progress = 1, originX, originY;
    private float pullX, pullY, openingBounce, touchX, touchY;
    private boolean touching, began;
    private final SpringAnimation springX, springY;

    public NebulaMenuReveal(ActionBarPopupWindowLayout host) {
        this.host = host;
        springX = spring(true); springY = spring(false);
    }
    private SpringAnimation spring(boolean horizontal) {
        return new SpringAnimation(this, new FloatPropertyCompat<NebulaMenuReveal>(horizontal ? "pullX" : "pullY") {
            @Override public float getValue(NebulaMenuReveal value) { return horizontal ? pullX : pullY; }
            @Override public void setValue(NebulaMenuReveal value, float position) {
                if (horizontal) pullX = position; else pullY = position;
                host.invalidate();
            }
        }).setSpring(new SpringForce(0).setStiffness(420).setDampingRatio(.66f));
    }
    public void onTouch(MotionEvent event) {
        if (!NebulaMenuStyle.animated()) return;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE && !touching) {
            NebulaHaptics.tick(host);
            touching = true; touchX = event.getRawX(); touchY = event.getRawY();
            springX.animateToFinalPosition(AndroidUtilities.dp(1));
            springY.animateToFinalPosition(AndroidUtilities.dp(1));
        }
        if (action == MotionEvent.ACTION_MOVE && touching) {
            springX.animateToFinalPosition(rubber(event.getRawX()-touchX));
            springY.animateToFinalPosition(rubber(event.getRawY()-touchY));
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touching = false; springX.animateToFinalPosition(0); springY.animateToFinalPosition(0);
        }
    }
    private float rubber(float delta) {
        float limit = AndroidUtilities.dp(9);
        return limit * delta / (AndroidUtilities.dp(45) + Math.abs(delta));
    }
    public void stopTouch() {
        springX.cancel(); springY.cancel(); touching = false;
        pullX = pullY = openingBounce = 0;
    }
    public void reset() {
        stopTouch();
        began = false;
        progress = 1;
    }
    public void setOpeningBounce(float t) {
        openingBounce = AndroidUtilities.dp(2.5f) * (float)(Math.sin(t*Math.PI)*Math.sin(t*Math.PI*3));
        host.invalidate();
    }
    public void setAnchor(View view) {
        anchor = view == null ? null : new WeakReference<>(view);
        began = false;
    }
    public void begin() {
        stopTouch(); began = true;
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
        float x = pullX + openingBounce, y = pullY - openingBounce * .35f;
        bounds.left -= Math.max(0, -x); bounds.right += Math.max(0, x);
        bounds.top -= Math.max(0, -y); bounds.bottom += Math.max(0, y);
        return Math.max(0, half-AndroidUtilities.dp(8))*(1-p)+NebulaMenuStyle.radius()*p;
    }
    private float initialHalfSize() {
        return Math.min(AndroidUtilities.dp(36), Math.min(host.getMeasuredWidth(), host.getMeasuredHeight()) / 2f);
    }
    public void applyBounds(Rect rect, Drawable material) {
        if (progress >= 1 && pullX == 0 && pullY == 0 && openingBounce == 0) return;
        float radius = shape(rect);
        rect.set(Math.round(bounds.left), Math.round(bounds.top), Math.round(bounds.right), Math.round(bounds.bottom));
        if (material instanceof BlurredBackgroundDrawable) {
            BlurredBackgroundDrawable glass = (BlurredBackgroundDrawable) material;
            glass.setRadius(radius);
            glass.setThickness(AndroidUtilities.dp(5));
            glass.setIntensity(NebulaGlass.refraction() + .20f * (float)Math.sin(progress * Math.PI));
        }
    }
    public void clip(Canvas canvas) {
        if (progress >= 1 && pullX == 0 && pullY == 0 && openingBounce == 0) return;
        contentBounds.set(0, 0, host.getMeasuredWidth(), host.getMeasuredHeight());
        float radius = shape(contentBounds);
        bounds.inset(AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        clip.rewind(); clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);
    }
    public void finish() {
        openingBounce = 0;
        setProgress(1);
        Drawable material = host.getBackgroundDrawable();
        if (material instanceof BlurredBackgroundDrawable) {
            ((BlurredBackgroundDrawable) material).setRadius(NebulaMenuStyle.radius());
            ((BlurredBackgroundDrawable) material).setIntensity(NebulaGlass.refraction());
        }
    }
    public void prepareClose() {
        // Some native callers show a popup without an entry animation.
        // Resolve its anchor once instead of collapsing toward the default (0, 0).
        if (!began) { begin(); finish(); }
        stopTouch();
    }
}
