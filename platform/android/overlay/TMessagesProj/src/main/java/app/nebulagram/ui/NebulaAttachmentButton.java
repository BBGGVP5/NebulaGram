package app.nebulagram.ui;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.ImageView;

/** Keeps the original attachment action in its own circle while typing. */
public final class NebulaAttachmentButton extends ImageView {
    private final NebulaComposerStyle style;
    private float nativeAlpha = 1f, nativeScaleX = 1f, nativeScaleY = 1f, nativeTranslationX;

    public NebulaAttachmentButton(Context context, NebulaComposerStyle style) {
        super(context);
        this.style = style;
    }

    public void refreshStyle() {
        boolean separate = style != null && style.isActive();
        super.setAlpha(separate ? 1f : nativeAlpha);
        super.setScaleX(separate ? 1f : nativeScaleX);
        super.setScaleY(separate ? 1f : nativeScaleY);
        super.setTranslationX(separate ? 0f : nativeTranslationX);
    }

    @Override public void setAlpha(float value) { nativeAlpha = value; refreshStyle(); }
    @Override public void setScaleX(float value) { nativeScaleX = value; refreshStyle(); }
    @Override public void setScaleY(float value) { nativeScaleY = value; refreshStyle(); }
    @Override public void setTranslationX(float value) { nativeTranslationX = value; refreshStyle(); }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return getAlpha() >= 0.5f && super.dispatchTouchEvent(event);
    }
}
