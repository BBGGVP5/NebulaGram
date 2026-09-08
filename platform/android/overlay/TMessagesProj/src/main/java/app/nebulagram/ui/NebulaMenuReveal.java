package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.Matrix;
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

/** One anchored surface transition; resolve window coordinates only after layout. */
public final class NebulaMenuReveal {
    private final ActionBarPopupWindowLayout host;
    private WeakReference<View> anchor;
    private final RectF bounds = new RectF();
    private final Rect contentBounds = new Rect();
    private final Path clip = new Path();
    private final Matrix contentTransform = new Matrix();
    private final Matrix inverseContentTransform = new Matrix();
    private final int[] location = new int[2];
    private float progress = 1, originX, originY;
    private float pullX, pullY, touchX, touchY;
    private boolean touching, began, originResolved;
    private final android.view.ViewTreeObserver.OnPreDrawListener originListener = this::resolveOrigin;
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
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) NebulaHaptics.tick(host);
        if (!NebulaMenuStyle.animated()) return;
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE && !touching) {
            if (action == MotionEvent.ACTION_MOVE) NebulaHaptics.tick(host);
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
        pullX = pullY = 0;
    }
    public void reset() {
        stopTouch();
        removeOriginListener();
        began = false;
        originResolved = false;
        progress = 1;
        host.setScaleX(1); host.setScaleY(1); host.setAlpha(1);
    }
    public void setAnchor(View view) {
        anchor = view == null ? null : new WeakReference<>(view);
        began = false;
    }
    public void begin() {
        stopTouch(); removeOriginListener(); began = true; originResolved = false; progress = 0;
        host.setScaleX(1); host.setScaleY(1); host.setAlpha(0);
        host.getViewTreeObserver().addOnPreDrawListener(originListener);
    }
    private void removeOriginListener() {
        if (host.getViewTreeObserver().isAlive()) host.getViewTreeObserver().removeOnPreDrawListener(originListener);
    }
    private boolean resolveOrigin() {
        if (!host.isAttachedToWindow() || host.getWidth() == 0 || host.getHeight() == 0) return true;
        originX = host.getWidth();
        originY = host.shownFromBottom ? host.getHeight() : 0;
        View view = anchor == null ? null : anchor.get();
        if (view != null && view.isAttachedToWindow()) {
            view.getLocationOnScreen(location);
            float x = location[0] + view.getWidth() / 2f, y = location[1] + view.getHeight() / 2f;
            host.getLocationOnScreen(location);
            originX = NebulaMenuMotion.pivot(x, location[0], host.getWidth());
            originY = NebulaMenuMotion.pivot(y, location[1], host.getHeight());
        }
        host.setPivotX(originX); host.setPivotY(originY);
        originResolved = true; removeOriginListener(); applyMotion();
        return true;
    }
    public float getProgress() { return progress; }
    public void setProgress(float value) { progress = Math.max(0, Math.min(1, value)); applyMotion(); host.invalidate(); }
    private void applyMotion() {
        if (!originResolved) return;
        float scale = NebulaMenuMotion.scale(progress);
        host.setScaleX(scale); host.setScaleY(scale);
        host.setAlpha(Math.min(1f, progress * 4f));
    }
    private float shape(Rect finalBounds) {
        bounds.set(finalBounds);
        float x = pullX, y = pullY;
        bounds.left -= Math.max(0, -x); bounds.right += Math.max(0, x);
        bounds.top -= Math.max(0, -y); bounds.bottom += Math.max(0, y);
        return NebulaMenuStyle.radius();
    }
    public void applyBounds(Rect rect, Drawable material) {
        if (pullX == 0 && pullY == 0) return;
        float radius = shape(rect);
        rect.set(Math.round(bounds.left), Math.round(bounds.top), Math.round(bounds.right), Math.round(bounds.bottom));
        if (material instanceof BlurredBackgroundDrawable) {
            BlurredBackgroundDrawable glass = (BlurredBackgroundDrawable) material;
            glass.setRadius(radius);
            glass.setThickness(AndroidUtilities.dp(5));
            glass.setIntensity(NebulaGlass.refraction());
        }
    }
    public void clip(Canvas canvas) {
        if (pullX == 0 && pullY == 0) return;
        contentBounds.set(0, 0, host.getMeasuredWidth(), host.getMeasuredHeight());
        float radius = shape(contentBounds);
        bounds.inset(AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        clip.rewind(); clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);
        // Map the content's padded edges to the same stretched edges as the glass.
        // Clipping alone left the labels/icons stationary under a moving surface.
        updateContentTransform();
        canvas.concat(contentTransform);
    }
    private void updateContentTransform() {
        float padding = AndroidUtilities.dp(8);
        float width = host.getMeasuredWidth(), height = host.getMeasuredHeight();
        contentTransform.setScale(NebulaMenuMotion.stretchScale(pullX, width, padding),
                NebulaMenuMotion.stretchScale(pullY, height, padding));
        contentTransform.postTranslate(NebulaMenuMotion.stretchOffset(pullX, width, padding),
                NebulaMenuMotion.stretchOffset(pullY, height, padding));
    }
    public MotionEvent contentTouchEvent(MotionEvent event) {
        if (pullX == 0 && pullY == 0) return event;
        updateContentTransform();
        if (!contentTransform.invert(inverseContentTransform)) return event;
        MotionEvent copy = MotionEvent.obtain(event);
        copy.transform(inverseContentTransform);
        return copy;
    }
    public void finish() {
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
