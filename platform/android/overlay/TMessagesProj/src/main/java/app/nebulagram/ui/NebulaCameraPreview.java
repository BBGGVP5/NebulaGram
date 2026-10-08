package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

/** Preference preview only: never opens hardware or changes the selected camera. */
public final class NebulaCameraPreview extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private ValueAnimator animation;
    private float aspect = targetAspect(), facing;
    private boolean front;

    public NebulaCameraPreview(Context context) {
        super(context);
        setFocusable(true);
        setContentDescription(NebulaText.text("Предпросмотр камеры. Нажмите, чтобы посмотреть смену камеры", "Camera preview. Tap to preview a camera switch"));
        setOnClickListener(v -> { front = !front; animateTo(targetAspect(), front ? 1 : 0); });
    }
    private float dp(float v) { return AndroidUtilities.dpf2(v); }
    private static float targetAspect() {
        int value = NebulaCameraSettings.aspect();
        return value == 2 ? 16f / 9 : value == 3 ? 1f : 4f / 3;
    }
    public void refresh() { animateTo(targetAspect(), front ? 1 : 0); }
    private void animateTo(float nextAspect, float nextFacing) {
        if (animation != null) { animation.cancel(); animation = null; }
        if (!isAttachedToWindow() || NebulaGlass.reduced() || Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) {
            aspect = nextAspect; facing = nextFacing; invalidate(); return;
        }
        final float oldAspect = aspect, oldFacing = facing;
        animation = ValueAnimator.ofFloat(0, 1);
        animation.setDuration(220); animation.setInterpolator(new DecelerateInterpolator(1.5f));
        animation.addUpdateListener(a -> { float p = (float)a.getAnimatedValue(); aspect = oldAspect + (nextAspect - oldAspect) * p; facing = oldFacing + (nextFacing - oldFacing) * p; invalidate(); });
        animation.start();
    }
    @Override protected void onDetachedFromWindow() {
        if (animation != null) { animation.cancel(); animation = null; }
        aspect = targetAspect(); facing = front ? 1 : 0; super.onDetachedFromWindow();
    }
    @Override protected void onMeasure(int width, int height) {
        setMeasuredDimension(MeasureSpec.getSize(width), AndroidUtilities.dp(214));
    }
    private void round(Canvas canvas, float left, float top, float right, float bottom, float radius, int color) {
        paint.setShader(null); paint.setStyle(Paint.Style.FILL); paint.setColor(color);
        rect.set(left, top, right, bottom); canvas.drawRoundRect(rect, dp(radius), dp(radius), paint);
    }
    private void text(Canvas canvas, String value, float left, float baseline, float width, int color, int size, boolean bold) {
        label.setColor(color); label.setTextSize(dp(size)); label.setTypeface(bold ? AndroidUtilities.bold() : null);
        canvas.drawText(TextUtils.ellipsize(value, label, Math.max(0, width), TextUtils.TruncateAt.END).toString(), left, baseline, label);
    }
    private void icon(Canvas canvas, int resource, float x, float y) {
        Drawable drawable = getContext().getResources().getDrawable(NebulaIcons.resource(resource)).mutate();
        drawable.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        int half = AndroidUtilities.dp(9);
        drawable.setBounds((int)x - half, (int)y - half, (int)x + half, (int)y + half); drawable.draw(canvas);
    }
    @Override protected void onDraw(Canvas canvas) {
        NebulaTheme theme = NebulaTheme.of(getContext()); float w = getWidth(), pad = dp(16);
        round(canvas, 0, 0, w, dp(214), 24, theme.surfaceContainer());
        text(canvas, NebulaText.text("Предпросмотр", "Preview"), pad, dp(28), w - pad * 2, theme.onSurface(), 14, true);
        String[] engines = {"Telegram", "Telegram", "Camera2", "CameraX", NebulaText.text("Системная", "System")};
        String quality = NebulaCameraSettings.quality() == 0 ? "Auto" : NebulaCameraSettings.quality() + "p";
        text(canvas, engines[NebulaCameraSettings.backend()] + " · " + quality, pad, dp(49), w - pad * 2, theme.onSurfaceVariant(), 12, false);

        float vh = dp(124), vw = Math.min(w - dp(96), vh * aspect);
        float left = (w - vw) / 2, top = dp(66), right = left + vw, bottom = top + vh;
        int saved = canvas.save();
        path.reset(); path.addRoundRect(left, top, right, bottom, dp(16), dp(16), Path.Direction.CW); canvas.clipPath(path);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(left, top, right, bottom, new int[]{0xFF122B39, 0xFF25536A, 0xFF162C35}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(left, top, right, bottom, paint); paint.setShader(null);
        // A small landscape makes the crop visible without showing a fake live feed.
        paint.setColor(0xFFD6ECE6); canvas.drawCircle(left + vw * (.72f - facing * .4f), top + vh * .25f, dp(10), paint);
        path.reset(); path.moveTo(left - dp(12), bottom); path.lineTo(left + vw * .35f, top + vh * .36f);
        path.lineTo(left + vw * .69f, bottom); path.close(); paint.setColor(0xFF4E8189); canvas.drawPath(path, paint);
        path.reset(); path.moveTo(left + vw * .28f, bottom); path.lineTo(left + vw * .78f, top + vh * .48f);
        path.lineTo(right + dp(24), bottom); path.close(); paint.setColor(0xFF315765); canvas.drawPath(path, paint);
        paint.setStrokeWidth(dp(.6f)); paint.setColor(0x44FFFFFF);
        for (int i = 1; i < 3; i++) {
            canvas.drawLine(left + vw * i / 3, top, left + vw * i / 3, bottom, paint);
            canvas.drawLine(left, top + vh * i / 3, right, top + vh * i / 3, paint);
        }
        round(canvas, left, bottom - dp(35), right, bottom, 0, 0xAA0B1820);
        float cy = bottom - dp(17), cx = w / 2;
        paint.setColor(Color.WHITE); canvas.drawCircle(cx, cy, dp(10), paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.3f)); canvas.drawCircle(cx, cy, dp(13), paint); paint.setStyle(Paint.Style.FILL);
        float gap = NebulaCameraSettings.enabled("center_controls") ? dp(40) : vw / 2 - dp(20);
        icon(canvas, front ? R.drawable.camera_revert1 : R.drawable.camera_revert2, cx + gap, cy);
        icon(canvas, R.drawable.flash_off, cx - gap, cy);
        canvas.restoreToCount(saved);
        int exposure = NebulaCameraSettings.exposure();
        if (exposure != 0) {
            paint.setColor(theme.primary()); paint.setStrokeWidth(dp(2));
            float x = exposure == 2 ? left - dp(12) : right + dp(12);
            if (exposure == 1) { canvas.drawLine(left + dp(24), bottom + dp(10), right - dp(24), bottom + dp(10), paint); canvas.drawCircle(cx, bottom + dp(10), dp(3), paint); }
            else { canvas.drawLine(x, top + dp(22), x, bottom - dp(22), paint); canvas.drawCircle(x, (top + bottom) / 2, dp(3), paint); }
        }
    }
}
