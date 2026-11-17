package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;

/**
 * Подарки профиля в стеклянных пузырях, которые плавают по орбите вокруг
 * аватарки и пульсируют под песню из профиля, пока она играет.
 *
 * Логика движения и огибающей пульсации не зависит от Android и проверяется
 * отдельно; рисование держит все шейдеры единичного размера и двигает их
 * матрицей, чтобы кадр не создавал объектов.
 */
public final class NebulaGiftOrbit {
    private NebulaGiftOrbit() {}

    public static final String KEY = "profile_gift_orbit";

    public static boolean enabled() {
        try {
            return NebulaMessagePreferences.enabled(KEY, true);
        } catch (Throwable e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- звук

    private static final Object lock = new Object();
    private static int listeners;
    private static final float[] bands = new float[6];
    private static float amplitude;
    private static long updatedAt = Long.MIN_VALUE / 2;

    /** Профиль с подарками на экране: анализатор плеера должен считать спектр. */
    public static void attach() {
        synchronized (lock) {
            listeners++;
        }
    }

    public static void detach() {
        synchronized (lock) {
            listeners = Math.max(0, listeners - 1);
        }
    }

    public static boolean listening() {
        synchronized (lock) {
            return listeners > 0;
        }
    }

    /** Вызывается из потока анализатора VideoPlayer с теми же значениями, что получает волна чата. */
    public static void onVisualizer(boolean playing, float[] values) {
        onVisualizer(playing, values, SystemClock.uptimeMillis());
    }

    static void onVisualizer(boolean playing, float[] values, long now) {
        synchronized (lock) {
            if (!playing || values == null || values.length < 7) {
                amplitude = 0;
                for (int i = 0; i < bands.length; i++) bands[i] = 0;
            } else {
                amplitude = clamp01(values[6]);
                for (int i = 0; i < bands.length; i++) bands[i] = clamp01(values[i]);
            }
            updatedAt = now;
        }
    }

    /** Сила толчка 0..1 для пузыря {@code slot}; без свежих данных — покой. */
    public static float target(int slot, long now) {
        synchronized (lock) {
            if (now - updatedAt > 400) return 0;
            // Анализатор отдаёт ~0.4 даже на тихих местах: всё ниже считается тишиной.
            float loud = clamp01((amplitude - .38f) / .42f);
            float band = bands[Math.floorMod(slot, bands.length)];
            return clamp01(loud * .72f + band * .45f);
        }
    }

    /** Быстрая атака и мягкий спад, независимые от частоты кадров. */
    public static float follow(float current, float target, float dtMs) {
        if (dtMs <= 0) return current;
        float tau = target > current ? 45f : 240f;
        float k = 1f - (float) Math.exp(-dtMs / tau);
        return current + (target - current) * k;
    }

    // ---------------------------------------------------------------- орбита

    /** Полный оборот орбиты, секунд. */
    public static final double PERIOD = 28.0;

    /**
     * Положение подарка {@code index} из {@code count}: out[0]=x, out[1]=y,
     * out[2]=глубина 0 (дальний край орбиты) .. 1 (ближний).
     */
    public static void orbit(int index, int count, double seconds, float cx, float cy, float rx, float ry, float bob, float[] out) {
        int n = Math.max(1, count);
        double base = 2 * Math.PI * index / n - Math.PI / 2;
        // Пузыри не идут строем: у каждого своё покачивание вдоль орбиты и по радиусу.
        double angle = base + seconds * (2 * Math.PI / PERIOD) + .28 * Math.sin(seconds * .55 + index * 1.7);
        double radius = 1 + .07 * Math.sin(seconds * .9 + index * 2.3);
        out[0] = cx + (float) (Math.cos(angle) * rx * radius);
        out[1] = cy + (float) (Math.sin(angle) * ry * radius + Math.sin(seconds * 1.25 + index * 1.1) * bob);
        out[2] = (float) ((Math.sin(angle) + 1) / 2);
    }

    static float clamp01(float v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    // ---------------------------------------------------------------- пузырь

    /** Состояние и краски одного пузыря; цвет — фон подарка. */
    public static final class Bubble {
        private static final int POINTS = 28;

        private final int color;
        private final int seed;
        private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint caustic = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Shader glowShader, bodyShader, rimShader, shineShader;
        private final Matrix matrix = new Matrix();
        private final Path path = new Path();
        private float pulse;
        private long lastFrame;

        public Bubble(int color, int seed) {
            this.color = color | 0xFF000000;
            this.seed = seed;
            int light = mix(this.color, Color.WHITE, .45f);
            int deep = mix(this.color, Color.BLACK, .25f);
            glowShader = new RadialGradient(0, 0, 1,
                    new int[]{alpha(this.color, .62f), alpha(this.color, .22f), alpha(this.color, 0)},
                    new float[]{0, .5f, 1}, Shader.TileMode.CLAMP);
            glow.setShader(glowShader);
            bodyShader = new RadialGradient(-.28f, -.34f, 1.45f,
                    new int[]{alpha(light, .34f), alpha(this.color, .40f), alpha(deep, .86f)},
                    new float[]{0, .58f, 1}, Shader.TileMode.CLAMP);
            body.setShader(bodyShader);
            rimShader = new LinearGradient(-1, -1, 1, 1,
                    new int[]{Color.argb(220, 255, 255, 255), Color.argb(40, 255, 255, 255), alpha(light, .75f)},
                    new float[]{0, .55f, 1}, Shader.TileMode.CLAMP);
            rim.setShader(rimShader);
            rim.setStyle(Paint.Style.STROKE);
            shineShader = new RadialGradient(0, 0, 1,
                    new int[]{Color.argb(210, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                    null, Shader.TileMode.CLAMP);
            shine.setShader(shineShader);
            caustic.setStyle(Paint.Style.STROKE);
            caustic.setStrokeCap(Paint.Cap.ROUND);
            caustic.setColor(light);
        }

        /** Обновляет пульсацию и возвращает её, 0..1. */
        public float step(int slot) {
            long now = SystemClock.uptimeMillis();
            float dt = lastFrame == 0 ? 16 : Math.min(100, now - lastFrame);
            lastFrame = now;
            pulse = follow(pulse, target(slot, now), dt);
            return pulse;
        }

        public float pulse() {
            return pulse;
        }

        /** Пузырь радиуса {@code r} с центром (cx, cy); seconds — общее время анимации. */
        public void draw(Canvas canvas, float cx, float cy, float r, double seconds, float alpha) {
            if (alpha <= 0 || r <= 0) return;
            int a = Math.round(255 * clamp01(alpha));
            float p = pulse;

            // Свечение вокруг: сильнее на ударах.
            float glowR = r * (1.8f + .55f * p);
            setUnit(glowShader, cx, cy, glowR);
            glow.setAlpha(Math.round(a * (.62f + .38f * p)));
            canvas.drawCircle(cx, cy, glowR, glow);

            // «Жидкий» контур: три гармоники с разной скоростью, на ударе дрожит сильнее.
            buildPath(cx, cy, r, seconds, p);
            setUnit(bodyShader, cx, cy, r);
            body.setAlpha(a);
            canvas.drawPath(path, body);

            caustic.setStrokeWidth(r * .09f);
            caustic.setAlpha(Math.round(a * .55f));
            float arc = r * .72f;
            canvas.drawArc(cx - arc, cy - arc, cx + arc, cy + arc, 25, 55, false, caustic);

            rim.setStrokeWidth(Math.max(1f, r * .075f));
            setUnit(rimShader, cx, cy, r);
            rim.setAlpha(a);
            canvas.drawPath(path, rim);

            // Блик: вытянутое пятно сверху слева.
            canvas.save();
            canvas.translate(cx - r * .36f, cy - r * .44f);
            canvas.rotate(-35);
            canvas.scale(r * .46f, r * .24f);
            matrix.reset();
            shineShader.setLocalMatrix(matrix);
            shine.setAlpha(Math.round(a * (.8f + .2f * p)));
            canvas.drawCircle(0, 0, 1, shine);
            canvas.restore();
        }

        private void buildPath(float cx, float cy, float r, double t, float p) {
            path.rewind();
            double wobble = .028 + .05 * p;
            // Сглаживание по серединам отрезков: вершины служат контрольными точками,
            // поэтому контур везде гладкий, включая стык.
            float px = 0, py = 0, fx = 0, fy = 0, sx = 0, sy = 0;
            for (int i = 0; i <= POINTS; i++) {
                double th = 2 * Math.PI * (i % POINTS) / POINTS;
                double k = 1 + wobble * (Math.sin(3 * th + t * 1.9 + seed)
                        + .6 * Math.sin(2 * th - t * 1.3 + seed * .7)
                        + .35 * Math.sin(5 * th + t * 2.7 + seed * 1.3));
                float x = cx + (float) (Math.cos(th) * r * k);
                float y = cy + (float) (Math.sin(th) * r * k);
                if (i == 0) {
                    fx = x;
                    fy = y;
                } else if (i == 1) {
                    sx = (fx + x) / 2;
                    sy = (fy + y) / 2;
                    path.moveTo(sx, sy);
                } else {
                    path.quadTo(px, py, (px + x) / 2, (py + y) / 2);
                }
                px = x;
                py = y;
            }
            // Последняя вершина совпадает с первой: через неё возвращаемся к началу.
            path.quadTo(fx, fy, sx, sy);
            path.close();
        }

        private void setUnit(Shader shader, float cx, float cy, float scale) {
            matrix.setScale(scale, scale);
            matrix.postTranslate(cx, cy);
            shader.setLocalMatrix(matrix);
        }
    }

    static int alpha(int color, float a) {
        return (color & 0x00FFFFFF) | (Math.round(255 * clamp01(a)) << 24);
    }

    static int mix(int a, int b, float t) {
        float u = 1 - t;
        return Color.argb(255,
                Math.round(Color.red(a) * u + Color.red(b) * t),
                Math.round(Color.green(a) * u + Color.green(b) * t),
                Math.round(Color.blue(a) * u + Color.blue(b) * t));
    }
}
