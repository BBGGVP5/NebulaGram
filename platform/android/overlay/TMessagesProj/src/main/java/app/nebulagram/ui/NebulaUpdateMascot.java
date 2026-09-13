package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.PowerManager;
import android.view.View;
import org.telegram.messenger.R;

/** Bundled Nova sticker-style emblem. A single entrance, not an idle render loop. */
public final class NebulaUpdateMascot extends View {
    private final Drawable nova;
    private boolean played;
    public NebulaUpdateMascot(Context context) {
        super(context);
        nova = context.getResources().getDrawable(R.drawable.nebula_launcher_nova_monochrome).mutate();
        nova.setTint(NebulaTheme.of(context).primary());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int size = Math.min(getWidth(), getHeight());
        // Compensate for the launcher's adaptive safe padding in this decorative view.
        int extra = size / 3;
        nova.setBounds(-extra, -extra, size + extra, size + extra);
        nova.draw(canvas);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        PowerManager power = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        if (played || NebulaGlass.reduced() || power != null && power.isPowerSaveMode()
                || Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) return;
        played = true;
        setScaleX(.84f); setScaleY(.84f); setRotation(-9f);
        animate().scaleX(1f).scaleY(1f).rotation(0f).setStartDelay(180).setDuration(520)
                .setInterpolator(new android.view.animation.OvershootInterpolator(.7f)).start();
    }
    @Override protected void onDetachedFromWindow() {
        animate().cancel(); setScaleX(1f); setScaleY(1f); setRotation(0f);
        super.onDetachedFromWindow();
    }
}
