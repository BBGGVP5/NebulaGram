package app.nebulagram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Голосовое → текст «как в iOS»: пока идёт распознавание, над полем мерцает
 * строка с уже услышанными словами, а готовый текст проявляется в самом поле
 * ввода слово за словом — из размытого акцентного цвета в обычный.
 */
public final class NebulaVoiceReveal {
    private NebulaVoiceReveal() {}

    /** Слово i из n при общем прогрессе t: каждое проявляется за {@link #FADE} «слотов». */
    static final float FADE = 3f;

    static float wordProgress(float t, int index, int count) {
        float x = t * (count + FADE) - index;
        return x <= 0 ? 0 : x >= FADE ? 1 : x / FADE;
    }

    static long duration(int words) {
        return Math.max(600, Math.min(2200, 350 + 55L * words));
    }

    /** Границы слов: пары [начало, конец) по непробельным отрезкам. */
    static List<int[]> words(CharSequence text) {
        List<int[]> out = new ArrayList<>();
        int i = 0, n = text.length();
        while (i < n) {
            while (i < n && Character.isWhitespace(text.charAt(i))) i++;
            int start = i;
            while (i < n && !Character.isWhitespace(text.charAt(i))) i++;
            if (i > start) out.add(new int[]{start, i});
        }
        return out;
    }

    /** Проигрывает проявление, если в поле стоит ровно {@code expected}. */
    public static void play(EditText field, String expected, int accent) {
        if (field == null || expected == null || LiteMode.isPowerSaverApplied()) return;
        Editable text = field.getText();
        if (text == null || !TextUtils.equals(text.toString(), expected)) return;
        List<int[]> ranges = words(text);
        if (ranges.isEmpty()) return;
        final WordSpan[] spans = new WordSpan[ranges.size()];
        for (int i = 0; i < spans.length; i++) {
            spans[i] = new WordSpan(accent);
            text.setSpan(spans[i], ranges.get(i)[0], ranges.get(i)[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        final ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        final TextWatcher[] watcher = new TextWatcher[1];
        final Runnable clear = () -> {
            Editable current = field.getText();
            if (current != null) for (WordSpan span : spans) current.removeSpan(span);
            if (watcher[0] != null) field.removeTextChangedListener(watcher[0]);
            watcher[0] = null;
            field.invalidate();
        };
        // Пользователь начал править — анимация не должна мешать набору.
        watcher[0] = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { animator.cancel(); }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {}
        };
        field.addTextChangedListener(watcher[0]);
        animator.setDuration(duration(spans.length));
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            for (int i = 0; i < spans.length; i++) spans[i].progress = wordProgress(t, i, spans.length);
            field.invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean done;
            private void finish() {
                if (done) return;
                done = true;
                clear.run();
            }
            @Override public void onAnimationEnd(Animator animation) { finish(); }
            @Override public void onAnimationCancel(Animator animation) { finish(); }
        });
        animator.start();
    }

    static final class WordSpan extends CharacterStyle implements UpdateAppearance {
        private static final BlurMaskFilter[] blurs = new BlurMaskFilter[8];
        final int accent;
        float progress;

        WordSpan(int accent) {
            this.accent = accent;
        }

        @Override
        public void updateDrawState(TextPaint paint) {
            float p = progress;
            if (p >= 1) return;
            int base = paint.getColor();
            float ease = p * p * (3 - 2 * p);
            int color = blend(accent, base, ease);
            int alpha = Math.round(Color.alpha(color) * Math.min(1f, p * 1.7f));
            paint.setColor((color & 0x00FFFFFF) | (alpha << 24));
            // Размытие текста аппаратным рендером поддерживается с Android 9.
            if (Build.VERSION.SDK_INT >= 28) {
                int level = Math.round((1 - ease) * (blurs.length - 1));
                if (level > 0) paint.setMaskFilter(blur(level));
            }
        }

        private static BlurMaskFilter blur(int level) {
            if (blurs[level] == null) {
                blurs[level] = new BlurMaskFilter(AndroidUtilities.dp(.75f * level), BlurMaskFilter.Blur.NORMAL);
            }
            return blurs[level];
        }
    }

    static int blend(int from, int to, float t) {
        float u = 1 - t;
        return Color.argb(
                Math.round(Color.alpha(from) * u + Color.alpha(to) * t),
                Math.round(Color.red(from) * u + Color.red(to) * t),
                Math.round(Color.green(from) * u + Color.green(to) * t),
                Math.round(Color.blue(from) * u + Color.blue(to) * t));
    }

    /** Мерцающая строка над записью: заглушка, пока нет слов, потом сами слова. */
    public static final class Pill extends View {
        private final TextPaint paint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final String placeholder;
        private final int base, highlight;
        private CharSequence text = "";
        private StaticLayout layout;
        private LinearGradient shader;
        private int shaderWidth;
        private final long start = SystemClock.uptimeMillis();

        public Pill(Context context, int base, int highlight) {
            super(context);
            this.base = base;
            this.highlight = highlight;
            placeholder = NebulaText.text("Распознаём речь…", "Transcribing…");
            paint.setTextSize(AndroidUtilities.dp(15));
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
            setContentDescription(placeholder);
        }

        public void setText(CharSequence value) {
            CharSequence next = value == null ? "" : value;
            if (TextUtils.equals(text, next)) return;
            text = next;
            setContentDescription(next.length() == 0 ? placeholder : next);
            layout = null;
            requestLayout();
            invalidate();
        }

        private int pad() {
            return AndroidUtilities.dp(16);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            int inner = Math.max(1, width - pad() * 2);
            CharSequence shown = text.length() == 0 ? placeholder : text;
            // Показываем хвост длинной расшифровки: новые слова всегда видны.
            layout = StaticLayout.Builder.obtain(shown, 0, shown.length(), paint, inner)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build();
            int lines = Math.min(3, layout.getLineCount());
            int textHeight = layout.getLineBottom(lines - 1) - layout.getLineTop(0);
            int height = Math.max(AndroidUtilities.dp(48), textHeight + AndroidUtilities.dp(26));
            setMeasuredDimension(width, resolveSize(height, heightSpec));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (layout == null) return;
            int w = getWidth();
            int band = Math.max(1, Math.round(w * .45f));
            if (shader == null || shaderWidth != band) {
                shaderWidth = band;
                shader = new LinearGradient(0, 0, band, 0, new int[]{base, highlight, base}, new float[]{0, .5f, 1}, Shader.TileMode.CLAMP);
            }
            float cycle = ((SystemClock.uptimeMillis() - start) % 1500) / 1500f;
            matrix.setTranslate(-band + (w + band) * cycle, 0);
            shader.setLocalMatrix(matrix);
            paint.setShader(shader);
            int lines = layout.getLineCount();
            int first = Math.max(0, lines - 3);
            int top = layout.getLineTop(first);
            int visible = layout.getLineBottom(lines - 1) - top;
            canvas.save();
            canvas.translate(pad(), (getHeight() - visible) / 2f - top);
            canvas.clipRect(0, top, layout.getWidth(), top + visible);
            layout.draw(canvas);
            canvas.restore();
            paint.setShader(null);
            if (!LiteMode.isPowerSaverApplied()) postInvalidateOnAnimation();
        }
    }
}
