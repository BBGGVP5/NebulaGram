package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

/**
 * Рисунок над заголовком на экранах входа: набирающийся номер и приходящий код.
 *
 * <p>Знак приложения там был не к месту: он одинаков на всех шагах и ничего не
 * говорит о том, чего от вас ждут. Здесь картинка объясняет шаг сама — на
 * первом экране цифры набираются одна за другой, на втором заполняются ячейки
 * кода, как будто он пришёл в сообщении.
 *
 * <p>Рисуем сами, а не берём готовую анимацию Telegram: их наборы привязаны к
 * их же оформлению и цветам, а нам нужны наши тона и наша скруглённая форма.
 */
public class NebulaAuthArt extends Drawable {

    /** Экран ввода номера: цифры набираются в поле. */
    public static final int KIND_PHONE = 0;
    /** Экран ввода кода: ячейки заполняются по очереди. */
    public static final int KIND_CODE = 1;

    /** Сколько знаков набирается, прежде чем цикл начнётся заново. */
    private static final int PHONE_DIGITS = 7;
    private static final int CODE_CELLS = 4;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int kind;
    private final int accent;
    private final int muted;

    private ValueAnimator cycle;
    private float phase;

    public NebulaAuthArt(int kind, int accent, int muted) {
        this.kind = kind;
        this.accent = accent;
        this.muted = muted;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        // Всё считается от стороны рисунка, поэтому он одинаково выглядит и в
        // значке на 88 точек, и в любом другом размере.
        float side = Math.min(bounds.width(), bounds.height());
        float unit = side / 100f;
        canvas.save();
        canvas.translate(bounds.centerX() - side / 2f, bounds.centerY() - side / 2f);
        if (kind == KIND_CODE) {
            drawCode(canvas, unit);
        } else {
            drawPhone(canvas, unit);
        }
        canvas.restore();
    }

    /** Поле с номером: цифры появляются слева направо, за ними идёт курсор. */
    private void drawPhone(Canvas canvas, float unit) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(unit * 4);
        paint.setColor(muted);
        rect.set(unit * 12, unit * 30, unit * 88, unit * 70);
        canvas.drawRoundRect(rect, unit * 12, unit * 12, paint);

        paint.setStyle(Paint.Style.FILL);
        // Код страны отделён чертой — так фигура читается как номер, а не как
        // случайный набор полосок.
        paint.setColor(muted);
        rect.set(unit * 20, unit * 46, unit * 30, unit * 54);
        canvas.drawRoundRect(rect, unit * 4, unit * 4, paint);
        paint.setStrokeWidth(unit * 2);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(unit * 35, unit * 42, unit * 35, unit * 58, paint);
        paint.setStyle(Paint.Style.FILL);

        float filled = phase * (PHONE_DIGITS + 1);
        float x = unit * 41;
        for (int i = 0; i < PHONE_DIGITS; i++) {
            float appear = Math.max(0f, Math.min(1f, filled - i));
            if (appear <= 0f) {
                break;
            }
            paint.setColor(accent);
            paint.setAlpha((int) (255 * appear));
            float height = unit * 8 * appear;
            rect.set(x, unit * 50 - height / 2f, x + unit * 5, unit * 50 + height / 2f);
            canvas.drawRoundRect(rect, unit * 2, unit * 2, paint);
            x += unit * 6.6f;
        }
        paint.setAlpha(255);

        // Курсор мигает там, где появится следующая цифра.
        if (filled < PHONE_DIGITS) {
            float blink = (float) (0.5 + 0.5 * Math.cos(phase * 12 * Math.PI));
            paint.setColor(accent);
            paint.setAlpha((int) (255 * blink));
            rect.set(x, unit * 43, x + unit * 2.5f, unit * 57);
            canvas.drawRoundRect(rect, unit, unit, paint);
            paint.setAlpha(255);
        }
    }

    /** Ячейки кода: заполняются по очереди, затем цикл повторяется. */
    private void drawCode(Canvas canvas, float unit) {
        float size = unit * 17;
        float gap = unit * 6;
        float total = CODE_CELLS * size + (CODE_CELLS - 1) * gap;
        float left = (unit * 100 - total) / 2f;
        float top = unit * 41;

        float filled = phase * (CODE_CELLS + 1);
        for (int i = 0; i < CODE_CELLS; i++) {
            float appear = Math.max(0f, Math.min(1f, filled - i));
            rect.set(left, top, left + size, top + size);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(unit * 3);
            // Пустая ячейка приглушена, заполненная берёт цвет акцента: видно,
            // сколько знаков уже введено.
            paint.setColor(appear > 0 ? accent : muted);
            canvas.drawRoundRect(rect, unit * 5, unit * 5, paint);

            if (appear > 0) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(accent);
                paint.setAlpha((int) (255 * appear));
                float dot = size * 0.28f * appear;
                canvas.drawCircle(rect.centerX(), rect.centerY(), dot, paint);
                paint.setAlpha(255);
            }
            left += size + gap;
        }
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        if (cycle == null && !bounds.isEmpty()) {
            start();
        }
    }

    private void start() {
        cycle = ValueAnimator.ofFloat(0f, 1f);
        cycle.setDuration(kind == KIND_CODE ? 3200 : 4200);
        cycle.setRepeatCount(ValueAnimator.INFINITE);
        cycle.setInterpolator(new LinearInterpolator());
        cycle.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidateSelf();
        });
        cycle.start();
    }

    /** Обязательно вызвать, когда экран уходит: иначе цикл крутится в фоне. */
    public void detach() {
        if (cycle != null) {
            cycle.cancel();
            cycle = null;
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
