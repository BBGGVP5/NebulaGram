package app.nebulagram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

/**
 * Индикатор загрузки и скачивания в духе Material 3 Expressive.
 *
 * <p>Вместо дуги Telegram рисуется волнистая фигура: её контур
 * прорисовывается по проценту, а сама фигура медленно перетекает из круга
 * в «цветок», клевер, овал и обратно. Слабая полная копия контура служит
 * дорожкой — по ней видно, что файл ждёт очереди, даже когда процент ещё 0.
 *
 * <p>Вызывается из {@code MediaActionDrawable.draw} на каждом кадре
 * прогресса, поэтому ничего не выделяет: путь, прямоугольник и кисти
 * общие. Всё рисование идёт на главном потоке.
 */
public final class NebulaShapeProgress {
    private static final String PREFS = "nebulagram";
    private static final String KEY = "media_progress_expressive";

    private static final Path PATH = new Path();
    private static final Path CHECK = new Path();
    private static final RectF PILL = new RectF();
    private static final float[] SWEEP = new float[2];
    private static Paint stroke;
    private static Paint pillFill;
    private static Paint pillText;
    private static Boolean cached;

    private NebulaShapeProgress() { }

    public static boolean enabled() {
        Boolean value = cached;
        if (value == null) {
            Context context = ApplicationLoader.applicationContext;
            value = context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true);
            cached = value;
        }
        return value;
    }

    public static void setEnabled(boolean value) {
        cached = value;
        prefs().edit().putBoolean(KEY, value).apply();
    }

    /** Дольше обычного держит переход «отмена → готово», чтобы успела нарисоваться галочка. */
    public static float finishDuration(float telegram) {
        return enabled() && !NebulaGlass.reduced() ? Math.max(telegram, 560f) : telegram;
    }

    /**
     * Рисует фигуру вместо дуги прогресса.
     *
     * @param rect     круг, в который Telegram вписывал дугу
     * @param progress анимированная доля загрузки 0..1
     * @param rotation угол начала дуги Telegram, градусы — он уже крутится сам
     * @param paint    кисть дуги: цвет, толщина, прозрачность и градиент пузыря
     * @param exit     0..1 — идёт переход в «готово», иначе 0
     * @param check    рисовать ли галочку на выходе (фото и видео без своей)
     * @return false — пусть Telegram рисует свою дугу
     */
    public static boolean draw(Canvas canvas, RectF rect, float progress, float rotation, Paint paint,
                               boolean mini, float exit, boolean check) {
        if (!enabled() || rect.width() <= 0) return false;
        boolean calm = NebulaGlass.reduced();
        long now = SystemClock.uptimeMillis();
        int shape = calm ? NebulaMotionShapes.CALM : NebulaMotionShapes.shapeAt(now);
        float morph = calm ? 0f : NebulaMotionShapes.morphAt(now);
        float settle = calm ? 0f : NebulaMotionShapes.clamp(exit * 1.6f);
        float scale = calm ? 1f : NebulaMotionShapes.pulse(exit);
        double phase = Math.toRadians(rotation);
        float cx = rect.centerX(), cy = rect.centerY();
        float outer = rect.width() / 2f * scale;
        int points = NebulaMotionShapes.points(mini);
        int alpha = paint.getAlpha();

        // Дорожка: вся фигура, едва заметно.
        trace(cx, cy, outer, shape, morph, settle, phase, phase, 1f, points);
        paint.setAlpha((int) (alpha * NebulaMotionShapes.trackAlpha(progress, exit)));
        canvas.drawPath(PATH, paint);
        paint.setAlpha(alpha);

        NebulaMotionShapes.sweep(progress, exit, now, calm, SWEEP);
        float done = SWEEP[1];
        double start = phase + Math.PI * 2 * SWEEP[0];
        trace(cx, cy, outer, shape, morph, settle, phase, start, done, Math.max(4, (int) (points * done)));
        canvas.drawPath(PATH, paint);

        if (check && exit > 0f && !mini) drawCheck(canvas, cx, cy, rect.width(), paint, exit);
        return true;
    }

    /** Процент под кругом — только для фото и видео, у файлов размер уже подписан. */
    public static void drawPercent(Canvas canvas, android.graphics.Rect bounds, float progress, float alpha) {
        if (!enabled() || alpha <= 0f || progress <= 0f || progress >= 1f) return;
        if (bounds.width() < AndroidUtilities.dp(36)) return;
        ensurePaints();
        String text = (int) (progress * 100) + "%";
        float width = pillText.measureText(text);
        float h = AndroidUtilities.dp(18), pad = AndroidUtilities.dp(7);
        float top = bounds.bottom + AndroidUtilities.dp(6);
        PILL.set(bounds.centerX() - width / 2f - pad, top, bounds.centerX() + width / 2f + pad, top + h);
        pillFill.setAlpha((int) (110 * alpha));
        pillText.setAlpha((int) (255 * alpha));
        canvas.drawRoundRect(PILL, h / 2f, h / 2f, pillFill);
        canvas.drawText(text, PILL.centerX() - width / 2f, PILL.centerY() + AndroidUtilities.dp(4), pillText);
    }

    private static void trace(float cx, float cy, float outer, int shape, float morph, float settle,
                              double phase, double start, float share, int count) {
        PATH.rewind();
        double sweep = Math.PI * 2 * share;
        for (int i = 0; i <= count; i++) {
            double angle = start + sweep * i / count;
            float r = outer * NebulaMotionShapes.radius(shape, morph, angle, phase, settle);
            float x = cx + (float) Math.cos(angle) * r;
            float y = cy + (float) Math.sin(angle) * r;
            if (i == 0) PATH.moveTo(x, y); else PATH.lineTo(x, y);
        }
        if (share >= 1f) PATH.close();
    }

    private static void drawCheck(Canvas canvas, float cx, float cy, float size, Paint paint, float exit) {
        ensurePaints();
        float trim = NebulaMotionShapes.checkTrim(exit);
        float u = size / 48f;
        float ax = cx - 9 * u, ay = cy + 1 * u, bx = cx - 3 * u, by = cy + 7 * u, ex = cx + 9 * u, ey = cy - 6 * u;
        float first = 0.35f;
        CHECK.rewind();
        CHECK.moveTo(ax, ay);
        if (trim <= first) {
            float t = trim / first;
            CHECK.lineTo(ax + (bx - ax) * t, ay + (by - ay) * t);
        } else {
            float t = (trim - first) / (1f - first);
            CHECK.lineTo(bx, by);
            CHECK.lineTo(bx + (ex - bx) * t, by + (ey - by) * t);
        }
        stroke.setColor(paint.getColor());
        stroke.setShader(paint.getShader());
        stroke.setStrokeWidth(paint.getStrokeWidth());
        stroke.setAlpha((int) (255 * NebulaMotionShapes.checkAlpha(exit)));
        canvas.drawPath(CHECK, stroke);
    }

    private static void ensurePaints() {
        if (stroke != null) return;
        stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        pillFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        pillFill.setColor(0xff000000);
        pillText = new Paint(Paint.ANTI_ALIAS_FLAG);
        pillText.setColor(0xffffffff);
        pillText.setTextSize(AndroidUtilities.dp(11));
        pillText.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
