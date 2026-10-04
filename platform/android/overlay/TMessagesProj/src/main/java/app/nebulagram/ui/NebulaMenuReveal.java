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
    private final NebulaMenuBubble.Frame frame = new NebulaMenuBubble.Frame();
    private final NebulaMenuBubble.Frame closeFrame = new NebulaMenuBubble.Frame();
    private float seed;
    private View focusContent;
    private float progress = 1, originX, originY, clockStart;
    private float closeProgress, closeStart;
    private boolean closing;
    private int focusStep = -1;
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
        closing = false;
        progress = 1; clockStart = 0;
        clearFocus();
        host.setScaleX(1); host.setScaleY(1); host.setAlpha(1);
    }
    public void setAnchor(View view) {
        anchor = view == null ? null : new WeakReference<>(view);
        began = false;
    }
    public void begin() {
        stopTouch(); removeOriginListener(); began = true; originResolved = false; progress = 0; clockStart = 0; closing = false;
        clearFocus();
        host.setScaleX(1); host.setScaleY(1); host.setAlpha(0);
        host.getViewTreeObserver().addOnPreDrawListener(originListener);
        resolveOrigin();
    }
    private void removeOriginListener() {
        if (host.getViewTreeObserver().isAlive()) host.getViewTreeObserver().removeOnPreDrawListener(originListener);
    }
    private boolean resolveOrigin() {
        if (!host.isAttachedToWindow() || host.getWidth() == 0 || host.getHeight() == 0) return true;
        originX = host.getWidth();
        originY = host.shownFromBottom ? host.getHeight() : 0;
        View view = anchor == null ? null : anchor.get();
        seed = AndroidUtilities.dp(48);
        if (view != null && view.isAttachedToWindow()) {
            view.getLocationOnScreen(location);
            float x = location[0] + view.getWidth() / 2f, y = location[1] + view.getHeight() / 2f;
            host.getLocationOnScreen(location);
            originX = NebulaMenuMotion.pivot(x, location[0], host.getWidth());
            originY = NebulaMenuMotion.pivot(y, location[1], host.getHeight());
            seed = Math.max(AndroidUtilities.dp(24), Math.min(AndroidUtilities.dp(56),
                    Math.min(view.getWidth(), view.getHeight())));
        }
        host.setPivotX(originX); host.setPivotY(originY);
        clockStart = progress; progress = 0;
        originResolved = true; removeOriginListener(); applyMotion();
        return true;
    }
    public float getProgress() { return progress; }
    public void setProgress(float value) {
        progress = Math.max(0, Math.min(1, originResolved ? (value - clockStart) / Math.max(.01f, 1 - clockStart) : value));
        applyMotion(); host.invalidate();
    }
    private void applyMotion() {
        if (!originResolved) return;
        updateFrame();
        // The glass changes shape in window coordinates. Scaling the host would
        // make both the surface and text grow around the same fixed corner.
        host.setScaleX(1); host.setScaleY(1); host.setAlpha(frame.alpha);
        View content = host.getItemsCount() == 0 ? null : (View) host.getItemAt(0).getParent();
        if (content != focusContent) { clearFocus(); focusContent = content; }
        int step = Math.round(12 * (1 - frame.content));
        if (focusContent != null && step != focusStep) {
            NebulaMenuFocus.apply(focusContent, step); focusStep = step;
        }
    }
    private void clearFocus() {
        if (focusContent != null) NebulaMenuFocus.clear(focusContent);
        focusContent = null; focusStep = -1;
    }
    private void updateFrame() {
        if (closing) NebulaMenuBubble.closing(frame, closeFrame, closeProgress,
                host.getWidth(), host.getHeight(), originX, originY, seed);
        else NebulaMenuBubble.opening(frame, progress, host.getWidth(), host.getHeight(),
                originX, originY, seed, NebulaMenuStyle.radius());
        NebulaMenuBubble.fit(frame, host.getWidth(), host.getHeight(), AndroidUtilities.dp(8));
    }
    private float shape(Rect finalBounds) {
        bounds.set(finalBounds);
        if (progress != 1) {
            updateFrame();
            // Map native split/submenu rectangles into the same expanding surface.
            float sx = frame.width / Math.max(1, host.getWidth());
            float sy = frame.height / Math.max(1, host.getHeight());
            bounds.set(frame.x + (bounds.left - host.getWidth() / 2f) * sx,
                    frame.y + (bounds.top - host.getHeight() / 2f) * sy,
                    frame.x + (bounds.right - host.getWidth() / 2f) * sx,
                    frame.y + (bounds.bottom - host.getHeight() / 2f) * sy);
        }
        float x = pullX, y = pullY;
        bounds.left -= Math.max(0, -x); bounds.right += Math.max(0, x);
        bounds.top -= Math.max(0, -y); bounds.bottom += Math.max(0, y);
        return progress == 1 ? NebulaMenuStyle.radius() : frame.radius;
    }
    public void applyBounds(Rect rect, Drawable material) {
        if (pullX == 0 && pullY == 0 && progress == 1) return;
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
        if (pullX == 0 && pullY == 0 && progress == 1) return;
        contentBounds.set(0, 0, host.getMeasuredWidth(), host.getMeasuredHeight());
        float radius = shape(contentBounds);
        bounds.inset(AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        clip.rewind(); clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        canvas.clipPath(clip);
        if (frame.content < 1) canvas.saveLayerAlpha(0, 0, host.getMeasuredWidth(), host.getMeasuredHeight(),
                Math.round(frame.content * 255));
        // Map the content's padded edges to the same stretched edges as the glass.
        // Clipping alone left the labels/icons stationary under a moving surface.
        updateContentTransform();
        canvas.concat(contentTransform);
    }
    private void updateContentTransform() {
        float padding = AndroidUtilities.dp(8);
        float width = host.getMeasuredWidth(), height = host.getMeasuredHeight();
        float scale = 1, x = width / 2, y = height / 2, surfaceWidth = width, surfaceHeight = height;
        if (progress != 1) {
            updateFrame();
            scale = NebulaMenuBubble.contentScale(frame, width, height, padding);
            x = frame.x; y = frame.y; surfaceWidth = frame.width; surfaceHeight = frame.height;
        }
        contentTransform.setScale(scale, scale);
        contentTransform.postTranslate(x - width / 2 * scale, y - height / 2 * scale);
        // Stretch in the moving bubble's coordinates, then inverse-map touches
        // through this same matrix. Its center follows the glass even mid-reveal.
        float sx = NebulaMenuMotion.stretchScale(pullX, surfaceWidth, padding);
        float sy = NebulaMenuMotion.stretchScale(pullY, surfaceHeight, padding);
        contentTransform.postScale(sx, sy);
        contentTransform.postTranslate(Math.min(0, pullX) + (x - surfaceWidth / 2 + padding) * (1 - sx),
                Math.min(0, pullY) + (y - surfaceHeight / 2 + padding) * (1 - sy));
    }
    public MotionEvent contentTouchEvent(MotionEvent event) {
        if (pullX == 0 && pullY == 0 && progress == 1) return event;
        updateContentTransform();
        if (!contentTransform.invert(inverseContentTransform)) return event;
        MotionEvent copy = MotionEvent.obtain(event);
        copy.transform(inverseContentTransform);
        return copy;
    }
    public void finish() {
        closing = false;
        setProgress(1);
        clearFocus();
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
        if (!originResolved) resolveOrigin();
        closeProgress = 0; closeStart = progress;
        updateFrame(); closeFrame.copy(frame);
        closing = true;
    }
    public void setCloseProgress(float value) {
        closeProgress = Math.max(0, Math.min(1, value));
        progress = closeStart * (1 - closeProgress * closeProgress);
        applyMotion(); host.invalidate();
    }
}
