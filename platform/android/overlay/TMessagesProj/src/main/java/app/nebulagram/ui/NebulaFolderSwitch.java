package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

import java.util.WeakHashMap;

/**
 * Как выглядит смена папок в списке чатов.
 *
 * <p>Пейджер Telegram двигает страницы через {@code setTranslationX} и при
 * свайпе читает это значение обратно, поэтому логический сдвиг остаётся
 * нетронутым. Стиль кладётся поверх: матрица анимации (масштаб и поправка к
 * сдвигу), прозрачность и клип. Матрица анимации есть с Android 10; на более
 * старых системах остаётся обычный сдвиг.
 *
 * <p>Вызывается на каждом кадре перехода для двух страниц и ничего не
 * выделяет, кроме состояния страницы при первом касании.
 */
public final class NebulaFolderSwitch {
    private static final String PREFS = "nebulagram";
    private static final String KEY = "folder_switch_style";

    private static final float[] OUT = new float[5];
    private static final WeakHashMap<View, State> STATES = new WeakHashMap<>();
    private static int cached = -1;

    private static final class State {
        final Matrix matrix = new Matrix();
        final Rect clip = new Rect();
        float corner;
        boolean touched, alpha, outline;
    }

    private static final ViewOutlineProvider ROUNDED = new ViewOutlineProvider() {
        @Override
        public void getOutline(View view, Outline outline) {
            State state = STATES.get(view);
            float radius = state == null ? 0f : state.corner;
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
        }
    };

    private NebulaFolderSwitch() { }

    public static int style() {
        int value = cached;
        if (value < 0) {
            Context context = ApplicationLoader.applicationContext;
            value = context == null ? NebulaMotionShapes.SLIDE
                    : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, NebulaMotionShapes.SLIDE);
            if (value < 0 || value >= NebulaMotionShapes.STYLES) value = NebulaMotionShapes.SLIDE;
            cached = value;
        }
        return value;
    }

    public static void setStyle(int value) {
        cached = value;
        ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt(KEY, value).apply();
    }

    public static CharSequence[] names() {
        return new CharSequence[]{
                NebulaText.text("Сдвиг", "Slide"),
                NebulaText.text("Карточки", "Cards"),
                NebulaText.text("Наплыв", "Fade through"),
                NebulaText.text("Глубина", "Depth"),
                NebulaText.text("Мгновенно", "Instant"),
        };
    }

    /** Применяет стиль к странице после того, как пейджер выставил её логический сдвиг. */
    public static void apply(View page, float translationX) {
        if (Build.VERSION.SDK_INT < 29) return;
        int style = style();
        if (style == NebulaMotionShapes.SLIDE || NebulaGlass.reduced()) {
            // «Уменьшение движения» — обычный сдвиг, без масштаба и прозрачности.
            style = NebulaMotionShapes.SLIDE;
        }
        float width = page.getMeasuredWidth();
        State state = STATES.get(page);
        if (style == NebulaMotionShapes.SLIDE || width <= 0f) {
            if (state != null && state.touched) reset(page, state);
            return;
        }
        NebulaMotionShapes.page(style, translationX / width, width, OUT);
        boolean identity = OUT[0] == 0f && OUT[1] == 1f && OUT[2] == 1f && OUT[3] >= width && OUT[4] == 0f;
        if (identity) {
            if (state != null && state.touched) reset(page, state);
            return;
        }
        if (state == null) {
            state = new State();
            STATES.put(page, state);
        }
        state.touched = true;
        int height = page.getMeasuredHeight();

        state.matrix.reset();
        if (OUT[1] != 1f) state.matrix.postScale(OUT[1], OUT[1], width / 2f, height / 2f);
        if (OUT[0] != 0f) state.matrix.postTranslate(OUT[0], 0f);
        page.setAnimationMatrix(state.matrix);

        if (OUT[2] != 1f || state.alpha) {
            state.alpha = OUT[2] != 1f;
            page.setAlpha(OUT[2]);
        }

        if (OUT[3] < width) {
            state.clip.set(0, 0, (int) Math.ceil(OUT[3]), height);
            page.setClipBounds(state.clip);
        } else if (page.getClipBounds() != null) {
            page.setClipBounds(null);
        }

        if (OUT[4] > 0f) {
            state.corner = AndroidUtilities.dp(28) * OUT[4];
            if (!state.outline) {
                state.outline = true;
                page.setOutlineProvider(ROUNDED);
                page.setClipToOutline(true);
            }
            page.invalidateOutline();
        } else if (state.outline) {
            dropOutline(page, state);
        }
    }

    private static void reset(View page, State state) {
        page.setAnimationMatrix(null);
        if (state.alpha) {
            page.setAlpha(1f);
            state.alpha = false;
        }
        if (page.getClipBounds() != null) page.setClipBounds(null);
        if (state.outline) dropOutline(page, state);
        state.touched = false;
    }

    private static void dropOutline(View page, State state) {
        state.outline = false;
        page.setClipToOutline(false);
        page.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    }
}
