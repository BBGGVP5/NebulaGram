package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;

/** A real chat-glass drawable over sample messages. Only the capsule moves. */
public final class NebulaGlassPreview extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path clip = new Path();
    private final RectF bounds = new RectF(), capsule = new RectF();
    private Hardware hardware;
    private Bitmap fallback;
    private long renderedRevision = -1;
    private float position = .5f;
    private Object wallpaper;
    private int sceneColor;
    private boolean dirty = true;
    private Runnable statusChanged;

    public NebulaGlassPreview(Context context) {
        super(context);
        setContentDescription(NebulaText.text("Стекло поверх сообщений. Проведите вверх или вниз для сравнения.",
                "Glass over messages. Drag up or down to compare."));
        setClickable(true);
        setFocusable(true);
    }
    public void setStatusChanged(Runnable callback) { statusChanged = callback; }
    private static int dp(float value) { return AndroidUtilities.dp(value); }

    private void drawScene(Canvas canvas) {
        NebulaTheme theme = NebulaTheme.of(getContext());
        NebulaWallpaperPreview.drawWallpaper(canvas, getWidth(), getHeight());
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(dp(15));
        String[] lines = {NebulaText.text("Сообщения под стеклом", "Messages behind glass"),
                NebulaText.text("Текст остаётся на месте", "Text stays in place"),
                "NebulaGram · 0123456789", NebulaText.text("Цвет, прозрачность, размытие", "Color, transparency, blur")};
        for (int i = 0; i < lines.length; i++) {
            float y = dp(38 + i * 43);
            paint.setColor(theme.surfaceContainer()); paint.setAlpha(225);
            canvas.drawRoundRect(dp(14), y - dp(23), getWidth() - dp(14), y + dp(10), dp(12), dp(12), paint);
            paint.setColor(i % 2 == 0 ? theme.primary() : theme.onSurface());
            canvas.drawText(lines[i], dp(26), y, paint);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        if (getWidth() <= dp(40) || getHeight() <= dp(80)) return;
        long revision = NebulaGlass.revision();
        Object currentWallpaper = Theme.getCachedWallpaperNonBlocking();
        int color = NebulaTheme.of(getContext()).surfaceContainer();
        if (renderedRevision != revision || wallpaper != currentWallpaper || sceneColor != color) {
            dirty = true; renderedRevision = revision; wallpaper = currentWallpaper; sceneColor = color;
            if (statusChanged != null) post(statusChanged);
        }
        bounds.set(0, 0, getWidth(), getHeight());
        clip.rewind(); clip.addRoundRect(bounds, dp(20), dp(20), Path.Direction.CW);
        int save = canvas.save(); canvas.clipPath(clip);
        drawScene(canvas);
        float top = dp(8) + position * (getHeight() - dp(80));
        capsule.set(dp(34), top, getWidth() - dp(34), top + dp(64));
        boolean blur = LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR);
        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated() && blur) {
            if (hardware == null) { hardware = new Hardware(this); dirty = true; }
            hardware.draw(canvas, dirty);
        } else {
            drawFallback(canvas, blur);
        }
        dirty = false;
        paint.setColor(NebulaChatColors.foreground(NebulaTheme.of(getContext()).onSurface(), NebulaMenuStyle.surface(null)));
        paint.setAlpha(255); paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(AndroidUtilities.bold()); paint.setTextSize(dp(17));
        canvas.drawText("NebulaGram", capsule.centerX(), capsule.centerY() - (paint.ascent() + paint.descent()) / 2f, paint);
        canvas.restoreToCount(save);
    }

    @android.annotation.TargetApi(31)
    private static final class Hardware {
        final NebulaGlassPreview owner;
        final BlurredBackgroundSourceRenderNode source;
        final BlurredBackgroundDrawable glass;
        Hardware(NebulaGlassPreview owner) {
            this.owner = owner;
            BlurredBackgroundSourceColor fallback = new BlurredBackgroundSourceColor();
            fallback.setColor(NebulaMenuStyle.surface(null));
            source = new BlurredBackgroundSourceRenderNode(fallback);
            BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(source);
            factory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
            glass = factory.create(owner, new BlurredBackgroundProviderBuilder(null)
                    .setBackgroundColor((r, dark) -> Theme.multAlpha(NebulaMenuStyle.surface(null), NebulaGlass.opacity()))
                    .setStrokeColorTop(0x60ffffff, 0x60ffffff).setStrokeColorBottom(0x18000000, 0x18ffffff)
                    .setStrokeWidth(AndroidUtilities.dpf2(.65f), AndroidUtilities.dpf2(.4f)).build());
            glass.setRadius(dp(32));
        }
        void draw(Canvas canvas, boolean dirty) {
            source.setBlur(AndroidUtilities.dpf2(NebulaGlass.blur()));
            if (dirty) {
                Canvas capture = source.beginRecording(owner.getWidth(), owner.getHeight());
                try { owner.drawScene(capture); } finally { source.endRecording(); }
                source.invalidateDisplayListForDrawables(); glass.updateColors();
            }
            RectF r = owner.capsule;
            glass.setBounds(Math.round(r.left), Math.round(r.top), Math.round(r.right), Math.round(r.bottom));
            glass.draw(canvas);
        }
    }

    private void drawFallback(Canvas canvas, boolean blur) {
        if (blur && (dirty || fallback == null)) {
            if (fallback != null) fallback.recycle();
            fallback = Bitmap.createBitmap(Math.max(1, getWidth() / 2), Math.max(1, getHeight() / 2), Bitmap.Config.ARGB_8888);
            Canvas capture = new Canvas(fallback);
            capture.scale(fallback.getWidth() / (float) getWidth(), fallback.getHeight() / (float) getHeight());
            drawScene(capture);
            int radius = Math.round(dp(NebulaGlass.blur()) / 2f);
            if (radius > 0) Utilities.stackBlurBitmap(fallback, radius);
        }
        clip.rewind(); clip.addRoundRect(capsule, dp(32), dp(32), Path.Direction.CW);
        int save = canvas.save(); canvas.clipPath(clip);
        paint.setColor(0xffffffff);
        if (blur && fallback != null) canvas.drawBitmap(fallback, null, bounds, paint);
        paint.setColor(NebulaMenuStyle.surface(null));
        paint.setAlpha(blur ? Math.round(NebulaGlass.opacity() * 255) : 255);
        canvas.drawRoundRect(capsule, dp(32), dp(32), paint);
        canvas.restoreToCount(save); paint.setAlpha(255);
        if (NebulaGlass.highlights()) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(AndroidUtilities.dpf2(.65f));
            paint.setColor(0x60ffffff);
            canvas.drawRoundRect(capsule, dp(32), dp(32), paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            position = Math.max(0f, Math.min(1f, (event.getY() - dp(40)) / Math.max(1, getHeight() - dp(80))));
            invalidate(); return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false);
            if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) { dirty = true; }
    @Override protected void onDetachedFromWindow() {
        if (statusChanged != null) removeCallbacks(statusChanged);
        if (fallback != null) { fallback.recycle(); fallback = null; }
        hardware = null; dirty = true;
        super.onDetachedFromWindow();
    }
}
