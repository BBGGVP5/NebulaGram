package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

/**
 * Переключатель по Material 3.
 *
 * <p>Системный {@code android.widget.Switch} рисуется темой платформы: тонкая
 * дорожка, маленький кружок, цвета из AppCompat. Рядом с нашими карточками он
 * выглядел чужим — а Material 3 переключатель устроен иначе: дорожка выше
 * кружка, кружок вырастает при включении, у выключенного состояния есть
 * обводка. Своя отрисовка — единственный способ получить это без Material
 * Components, тянуть которые в форк Telegram означало бы конфликт тем.
 */
public class NebulaSwitch extends View {

    // Размеры из спецификации M3: дорожка 52x32, кружок 24 включённый и
    // 16 выключенный. Уменьшать нельзя — палец промахивается.
    private static final int TRACK_WIDTH = 52;
    private static final int TRACK_HEIGHT = 32;
    private static final int THUMB_ON = 24;
    private static final int THUMB_OFF = 16;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final NebulaTheme theme;

    private boolean checked;
    /** 0 — выключен, 1 — включён; дробное значение во время анимации. */
    private float progress;
    private ValueAnimator animator;

    public NebulaSwitch(@NonNull Context context) {
        super(context);
        theme = NebulaTheme.of(context);
        setClickable(false);
        setFocusable(false);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(AndroidUtilities.dp(TRACK_WIDTH), AndroidUtilities.dp(TRACK_HEIGHT));
    }

    public boolean isChecked() {
        return checked;
    }

    /** Ставит состояние с анимацией: без неё переключение читается как подмена. */
    public void setChecked(boolean value, boolean animated) {
        if (checked == value) {
            return;
        }
        checked = value;
        if (animator != null) {
            animator.cancel();
        }
        if (!animated) {
            progress = value ? 1f : 0f;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(progress, value ? 1f : 0f);
        animator.setDuration(180);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void setChecked(boolean value) {
        setChecked(value, true);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float height = AndroidUtilities.dp(TRACK_HEIGHT);
        float width = AndroidUtilities.dp(TRACK_WIDTH);
        float radius = height / 2f;

        // Дорожка: у выключенного — приглушённая поверхность с обводкой, у
        // включённого — акцент. Промежуточные кадры смешиваем, иначе на
        // середине анимации цвет прыгает.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(blend(NebulaTheme.stateLayer(theme.onSurfaceVariant(), 0.20f),
                theme.primary(), progress));
        track.set(0, 0, width, height);
        canvas.drawRoundRect(track, radius, radius, paint);

        if (progress < 1f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1, AndroidUtilities.dp(2)));
            paint.setColor(withAlpha(theme.outline(), 1f - progress));
            float inset = paint.getStrokeWidth() / 2f;
            track.set(inset, inset, width - inset, height - inset);
            canvas.drawRoundRect(track, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        float thumb = AndroidUtilities.dp(THUMB_OFF)
                + (AndroidUtilities.dp(THUMB_ON) - AndroidUtilities.dp(THUMB_OFF)) * progress;
        float margin = AndroidUtilities.dp(4);
        float left = margin + thumb / 2f;
        float right = width - margin - thumb / 2f;
        float centerX = left + (right - left) * progress;

        paint.setColor(blend(theme.outline(), theme.onPrimary(), progress));
        canvas.drawCircle(centerX, height / 2f, thumb / 2f, paint);
    }

    /** Линейное смешивание по каналам: цвета берутся из палитры, не из ресурсов. */
    private static int blend(int from, int to, float ratio) {
        int a = (int) (((from >>> 24) & 0xFF) + ((((to >>> 24) & 0xFF)) - ((from >>> 24) & 0xFF)) * ratio);
        int r = (int) (((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF)) - ((from >> 16) & 0xFF)) * ratio);
        int g = (int) (((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF)) - ((from >> 8) & 0xFF)) * ratio);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * ratio);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (((int) (255 * Math.max(0, Math.min(1, alpha)))) << 24);
    }
}
