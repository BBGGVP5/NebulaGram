package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

/**
 * Рисунки над заголовками на экранах входа — по макетам онбординга.
 *
 * <p>Знак приложения там был не к месту: он одинаков на всех шагах и ничего не
 * говорит о том, чего от вас ждут. Здесь картинка объясняет шаг сама: на
 * подключении щит рисует галочку, на номере крутятся барабаны цифр, на коде
 * замок с бегущим по кругу пунктиром.
 *
 * <p>Рисуем сами, а не берём готовую анимацию Telegram: их наборы привязаны к
 * их же оформлению и цветам, а нам нужны наши тона и наша скруглённая форма.
 * Вся геометрия задана на квадрате 100x100, поэтому рисунок одинаково выглядит
 * и в значке на 88 точек, и в любом другом размере.
 */
public class NebulaAuthArt extends Drawable {

    /** Экран ввода номера: барабаны с цифрами за кодом страны. */
    public static final int KIND_PHONE = 0;
    /** Экран ввода кода: замок с точками и бегущим по кругу пунктиром. */
    public static final int KIND_CODE = 1;
    /** Экран подключения NebulaLink: щит, который ставит галочку. */
    public static final int KIND_LINK = 2;

    /** Сколько барабанов крутится справа от кода страны. */
    private static final int REELS = 4;

    /**
     * Сколько полных оборотов делает каждый барабан за цикл. Числа целые и
     * разные: на стыке цикла цифры не прыгают, а барабаны при этом
     * останавливаются вразнобой, как при живом наборе.
     */
    private static final int[] REEL_TURNS = {3, 4, 2, 5};

    /** Длина цикла для каждого вида, мс. */
    private static final int[] DURATION = {12000, 18000, 3200};

    /** Доли цикла подключения: рисование галочки, начало и время растворения. */
    private static final float DRAW_UNTIL = 0.28f;
    private static final float FADE_FROM = 0.76f;
    private static final float FADE_TIME = 0.16f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint digits = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Path segment = new Path();
    private final PathMeasure measure = new PathMeasure();
    private final int kind;
    private final int accent;
    private final int muted;

    private ValueAnimator cycle;
    private float phase;

    public NebulaAuthArt(int kind, int accent, int muted) {
        this.kind = kind;
        this.accent = accent;
        this.muted = muted;
        digits.setTypeface(AndroidUtilities.bold());
        digits.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        float side = Math.min(bounds.width(), bounds.height());
        float unit = side / 100f;
        canvas.save();
        canvas.translate(bounds.centerX() - side / 2f, bounds.centerY() - side / 2f);
        switch (kind) {
            case KIND_CODE:
                drawCode(canvas, unit);
                break;
            case KIND_LINK:
                drawLink(canvas, unit);
                break;
            default:
                drawPhone(canvas, unit);
                break;
        }
        canvas.restore();
    }

    /**
     * Набор номера: код страны стоит на месте, следом крутятся цифры.
     *
     * <p>Цифры сменяются рывком, а не плавной лентой: так читается набор, а не
     * прокрутка списка.
     */
    private void drawPhone(Canvas canvas, float unit) {
        final float height = unit * 44;
        final float reel = unit * 16;
        final float gap = unit * 3.5f;
        final float prefix = unit * 20;
        final float radius = unit * 8;
        final float total = prefix + gap + REELS * reel + (REELS - 1) * gap;
        final float top = unit * 50 - height / 2f;
        float left = (unit * 100 - total) / 2f;

        // Код страны — залитая плашка: она никуда не едет и держит строку.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent);
        paint.setAlpha(56);
        rect.set(left, top, left + prefix, top + height);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setAlpha(255);

        digits.setTextSize(unit * 24);
        digits.setColor(accent);
        final float baseline = rect.centerY() - (digits.ascent() + digits.descent()) / 2f;
        canvas.drawText("+", rect.centerX(), baseline, digits);
        left += prefix + gap;

        for (int i = 0; i < REELS; i++) {
            rect.set(left, top, left + reel, top + height);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(unit * 2.6f);
            paint.setColor(muted);
            canvas.drawRoundRect(rect, radius, radius, paint);

            int digit = (int) (phase * REEL_TURNS[i] * 10) % 10;
            canvas.drawText(String.valueOf(digit), rect.centerX(), baseline, digits);
            left += reel + gap;
        }
        paint.setStyle(Paint.Style.FILL);
    }

    /** Код: пунктирное кольцо крутится, точки в замке всплывают по очереди. */
    private void drawCode(Canvas canvas, float unit) {
        final float time = phase * DURATION[KIND_CODE];

        // Кольцо делает за цикл два полных оборота, поэтому на стыке цикла
        // пунктир не дёргается.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(unit * 2.2f);
        paint.setColor(muted);
        paint.setPathEffect(new DashPathEffect(new float[]{unit * 7, unit * 11}, 0));
        canvas.save();
        canvas.rotate(phase * 720f, unit * 50, unit * 50);
        canvas.drawCircle(unit * 50, unit * 50, unit * 48, paint);
        canvas.restore();
        paint.setPathEffect(null);

        rect.set(unit * 16, unit * 26, unit * 84, unit * 74);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent);
        paint.setAlpha(48);
        canvas.drawRoundRect(rect, unit * 15, unit * 15, paint);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(unit * 3);
        canvas.drawRoundRect(rect, unit * 15, unit * 15, paint);

        // Точки всплывают одна за другой со сдвигом — знак приходящего кода.
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 3; i++) {
            float local = ((time - i * 350f) % 2250f) / 2250f;
            if (local < 0) {
                local += 1f;
            }
            float wave = (float) (0.5 - 0.5 * Math.cos(local * 2 * Math.PI));
            paint.setColor(accent);
            paint.setAlpha((int) (128 + 127 * wave));
            canvas.drawCircle(unit * (32 + 18 * i), unit * 50 - unit * 6 * wave, unit * 6.5f, paint);
        }
        paint.setAlpha(255);
    }

    /** Подключение: щит ставит галочку, вокруг расходится волна. */
    private void drawLink(Canvas canvas, float unit) {
        // Волна расходится, когда галочка дорисована: это подтверждение, а не
        // постоянный фон. До того её нет вовсе.
        if (phase > DRAW_UNTIL) {
            final float halo = (phase - DRAW_UNTIL) / (1f - DRAW_UNTIL);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(unit * 2.6f);
            paint.setColor(accent);
            paint.setAlpha((int) (150 * (1f - halo)));
            // Больше волне расти некуда: сорок девять долей из пятидесяти —
            // это уже граница квадрата. Поэтому вместе с ней подрос и сам
            // квадрат, а щит внутри стал компактнее.
            canvas.drawCircle(unit * 50, unit * 50, unit * (32 + 17 * halo), paint);
            paint.setAlpha(255);
        }

        path.reset();
        path.moveTo(unit * 50, unit * 14);
        path.lineTo(unit * 80, unit * 26);
        path.lineTo(unit * 80, unit * 52);
        path.cubicTo(unit * 80, unit * 71, unit * 67, unit * 81, unit * 50, unit * 86);
        path.cubicTo(unit * 33, unit * 81, unit * 20, unit * 71, unit * 20, unit * 52);
        path.lineTo(unit * 20, unit * 26);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accent);
        paint.setAlpha(48);
        canvas.drawPath(path, paint);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(unit * 3);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(path, paint);

        // Галочка рисуется, держится, растворяется целиком — и цикл начинается
        // заново. Раньше она стиралась задом наперёд, будто её отматывают, и
        // на стыке цикла оставался огрызок линии.
        final float draw = Math.min(1f, phase / DRAW_UNTIL);
        final int alpha = phase < FADE_FROM ? 255
                : (int) (255 * Math.max(0f, 1f - (phase - FADE_FROM) / FADE_TIME));
        if (alpha > 0) {
            path.reset();
            path.moveTo(unit * 36, unit * 52);
            path.lineTo(unit * 46, unit * 62);
            path.lineTo(unit * 65, unit * 40);
            measure.setPath(path, false);
            segment.reset();
            measure.getSegment(0, measure.getLength() * draw, segment, true);
            paint.setStrokeWidth(unit * 5.4f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(accent);
            paint.setAlpha(alpha);
            canvas.drawPath(segment, paint);
            paint.setAlpha(255);
            paint.setStrokeCap(Paint.Cap.BUTT);
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
        cycle.setDuration(DURATION[kind >= 0 && kind < DURATION.length ? kind : 0]);
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
        digits.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
