package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

/**
 * Живое превью элемента интерфейса прямо над его настройками.
 *
 * <p>Переключатель "панель в стиле iOS" сам по себе ничего не сообщает: чтобы
 * увидеть разницу, приходилось выходить в чат, смотреть, возвращаться. Превью
 * показывает результат на месте и перерисовывается по нажатию.
 *
 * <p>Рисуем фигурами, а не настоящими виджетами: настоящий {@code
 * ChatActivityEnterView} тянет за собой пол-Telegram, живёт только внутри чата
 * и в списке настроек вёл бы себя непредсказуемо. Здесь нужен образ, а не
 * работающая копия.
 */
public class NebulaPreview extends View {

    /** Нижняя панель вкладок. */
    public static final int KIND_TABS = 0;
    /** Шапка чата. */
    public static final int KIND_HEADER = 1;
    /** Поле ввода сообщения. */
    public static final int KIND_COMPOSER = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final int kind;
    private NebulaTheme theme;

    public NebulaPreview(@NonNull Context context, int kind) {
        super(context);
        this.kind = kind;
        theme = NebulaTheme.of(context);
    }

    /** Перерисовать после изменения настройки. */
    public void refresh() {
        theme = NebulaTheme.of(getContext());
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int height = AndroidUtilities.dp(kind == KIND_TABS ? 96 : 84);
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Подложка изображает экран приложения, иначе панель висела бы в
        // пустоте и её края нельзя было бы оценить.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(NebulaTheme.stateLayer(theme.onSurface(), 0.05f));
        rect.set(0, 0, getWidth(), getHeight());
        float radius = NebulaTheme.cornerMedium();
        canvas.drawRoundRect(rect, radius, radius, paint);

        switch (kind) {
            case KIND_TABS:
                drawTabs(canvas);
                break;
            case KIND_HEADER:
                drawHeader(canvas);
                break;
            case KIND_COMPOSER:
                drawComposer(canvas);
                break;
            default:
                break;
        }
    }

    // --- нижняя панель ------------------------------------------------------

    private void drawTabs(Canvas canvas) {
        float margin = AndroidUtilities.dp(14);
        float height = AndroidUtilities.dp(56);
        float top = getHeight() - height - AndroidUtilities.dp(12);

        if (!NebulaBottomBar.enabled()) {
            drawCentredNote(canvas);
            return;
        }

        paint.setColor(theme.surfaceContainer());
        rect.set(margin, top, getWidth() - margin, top + height);
        canvas.drawRoundRect(rect, height / 2f, height / 2f, paint);

        boolean[] shown = {
                true,
                NebulaBottomBar.tabEnabled(NebulaBottomBar.TAB_CONTACTS),
                NebulaBottomBar.tabEnabled(NebulaBottomBar.TAB_SETTINGS),
                NebulaBottomBar.tabEnabled(NebulaBottomBar.TAB_PROFILE),
        };
        int count = 0;
        for (boolean visible : shown) {
            if (visible) {
                count++;
            }
        }
        float step = (rect.width()) / count;
        float centerY = top + height / 2f;
        int index = 0;
        for (int i = 0; i < shown.length; i++) {
            if (!shown[i]) {
                continue;
            }
            float centerX = rect.left + step * index + step / 2f;
            // Первая вкладка выбрана: так видно, чем выбранная отличается.
            boolean active = index == 0;
            paint.setColor(active ? theme.primary() : theme.onSurfaceVariant());
            drawTabGlyph(canvas, i, centerX, centerY - AndroidUtilities.dp(7));
            paint.setColor(active
                    ? theme.primary() : NebulaTheme.stateLayer(theme.onSurfaceVariant(), 0.55f));
            rect.set(centerX - AndroidUtilities.dp(11), centerY + AndroidUtilities.dp(7),
                    centerX + AndroidUtilities.dp(11), centerY + AndroidUtilities.dp(11));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), paint);
            index++;
        }
    }

    /** Значки вкладок: узнаваемая форма важнее точного повторения иконки. */
    private void drawTabGlyph(Canvas canvas, int tab, float cx, float cy) {
        float size = AndroidUtilities.dp(9);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.6f));
        switch (tab) {
            case 0: // чаты — облачко
                rect.set(cx - size, cy - size * 0.8f, cx + size, cy + size * 0.5f);
                canvas.drawRoundRect(rect, size * 0.5f, size * 0.5f, paint);
                path.reset();
                path.moveTo(cx - size * 0.4f, cy + size * 0.5f);
                path.lineTo(cx - size * 0.15f, cy + size);
                path.lineTo(cx + size * 0.15f, cy + size * 0.5f);
                canvas.drawPath(path, paint);
                break;
            case 1: // контакты — голова и плечи
                canvas.drawCircle(cx, cy - size * 0.35f, size * 0.42f, paint);
                rect.set(cx - size * 0.75f, cy + size * 0.1f, cx + size * 0.75f, cy + size * 1.2f);
                canvas.drawArc(rect, 200, 140, false, paint);
                break;
            case 2: // настройки — шестерёнка, показанная кольцом с зубцами
                canvas.drawCircle(cx, cy, size * 0.45f, paint);
                for (int i = 0; i < 6; i++) {
                    double angle = Math.PI * i / 3;
                    canvas.drawLine(
                            cx + (float) Math.cos(angle) * size * 0.6f,
                            cy + (float) Math.sin(angle) * size * 0.6f,
                            cx + (float) Math.cos(angle) * size * 0.95f,
                            cy + (float) Math.sin(angle) * size * 0.95f, paint);
                }
                break;
            default: // профиль — сплошной кружок вместо аватарки
                paint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, size * 0.85f, paint);
                break;
        }
        paint.setStyle(Paint.Style.FILL);
    }

    // --- шапка чата ---------------------------------------------------------

    private void drawHeader(Canvas canvas) {
        boolean styled = NebulaAppearance.chatHeader();
        float margin = AndroidUtilities.dp(14);
        float height = AndroidUtilities.dp(52);
        float top = (getHeight() - height) / 2f;

        paint.setColor(theme.surfaceContainer());
        rect.set(margin, top, getWidth() - margin, top + height);
        float radius = styled ? height / 2f : AndroidUtilities.dp(4);
        canvas.drawRoundRect(rect, radius, radius, paint);

        float centerY = top + height / 2f;

        // Стрелка назад — она есть в обоих вариантах.
        paint.setColor(theme.onSurfaceVariant());
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.8f));
        float arrowX = margin + AndroidUtilities.dp(18);
        path.reset();
        path.moveTo(arrowX + AndroidUtilities.dp(4), centerY - AndroidUtilities.dp(5));
        path.lineTo(arrowX - AndroidUtilities.dp(2), centerY);
        path.lineTo(arrowX + AndroidUtilities.dp(4), centerY + AndroidUtilities.dp(5));
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);

        // В нашем стиле имя по центру, аватарка справа; в стоковом — слева
        // подряд, как у Telegram. Разница видна сразу, без подписи.
        float avatarX = styled ? getWidth() - margin - AndroidUtilities.dp(20)
                : margin + AndroidUtilities.dp(48);
        paint.setColor(NebulaTheme.stateLayer(theme.primary(), 0.35f));
        canvas.drawCircle(avatarX, centerY, AndroidUtilities.dp(14), paint);

        float textLeft = styled ? 0 : avatarX + AndroidUtilities.dp(22);
        float titleWidth = AndroidUtilities.dp(84);
        float titleX = styled ? (getWidth() - titleWidth) / 2f : textLeft;
        paint.setColor(theme.onSurface());
        rect.set(titleX, centerY - AndroidUtilities.dp(9), titleX + titleWidth, centerY - AndroidUtilities.dp(2));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), paint);

        float subWidth = AndroidUtilities.dp(46);
        float subX = styled ? (getWidth() - subWidth) / 2f : textLeft;
        paint.setColor(NebulaTheme.stateLayer(theme.onSurfaceVariant(), 0.6f));
        rect.set(subX, centerY + AndroidUtilities.dp(2), subX + subWidth, centerY + AndroidUtilities.dp(8));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), paint);
    }

    // --- поле ввода ---------------------------------------------------------

    private void drawComposer(Canvas canvas) {
        boolean ios = NebulaAppearance.iosComposer();
        float margin = AndroidUtilities.dp(14);
        float height = AndroidUtilities.dp(44);
        float centerY = getHeight() / 2f;
        float top = centerY - height / 2f;
        float circle = height / 2f;

        float fieldLeft, fieldRight;
        if (ios) {
            // Кнопки отдельными кружками, поле — самостоятельная пилюля.
            paint.setColor(theme.surfaceContainer());
            canvas.drawCircle(margin + circle, centerY, circle, paint);
            paint.setColor(theme.primary());
            canvas.drawCircle(getWidth() - margin - circle, centerY, circle, paint);

            fieldLeft = margin + height + AndroidUtilities.dp(8);
            fieldRight = getWidth() - margin - height - AndroidUtilities.dp(8);
            paint.setColor(theme.surfaceContainer());
            rect.set(fieldLeft, top, fieldRight, top + height);
            canvas.drawRoundRect(rect, circle, circle, paint);

            paint.setColor(theme.onSurfaceVariant());
            drawClip(canvas, margin + circle, centerY);
            paint.setColor(theme.onPrimary());
            drawMic(canvas, getWidth() - margin - circle, centerY);
        } else {
            // Стоковый вид: всё в одной полосе во всю ширину, углы почти прямые.
            paint.setColor(theme.surfaceContainer());
            rect.set(margin, top, getWidth() - margin, top + height);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), paint);
            fieldLeft = margin;
            fieldRight = getWidth() - margin;

            paint.setColor(theme.onSurfaceVariant());
            drawClip(canvas, fieldLeft + AndroidUtilities.dp(22), centerY);
            drawMic(canvas, fieldRight - AndroidUtilities.dp(22), centerY);
        }

        // Смайл у правого края поля — он есть в обоих вариантах и помогает
        // узнать в фигуре именно строку сообщения.
        paint.setColor(theme.onSurfaceVariant());
        drawSmile(canvas, fieldRight - AndroidUtilities.dp(ios ? 20 : 48), centerY);

        // Строка подсказки "Сообщение".
        float textLeft = fieldLeft + AndroidUtilities.dp(ios ? 18 : 44);
        paint.setColor(NebulaTheme.stateLayer(theme.onSurfaceVariant(), 0.5f));
        rect.set(textLeft, centerY - AndroidUtilities.dp(3),
                textLeft + AndroidUtilities.dp(58), centerY + AndroidUtilities.dp(3));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(3), AndroidUtilities.dp(3), paint);
    }

    /** Скрепка: вытянутая петля под наклоном — форма узнаётся и в 14dp. */
    private void drawClip(Canvas canvas, float cx, float cy) {
        float h = AndroidUtilities.dp(7);
        float w = AndroidUtilities.dp(3.4f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.save();
        canvas.rotate(35, cx, cy);
        path.reset();
        path.moveTo(cx + w, cy - h);
        path.lineTo(cx + w, cy + h * 0.45f);
        path.addArc(cx - w, cy + h * 0.05f, cx + w, cy + h * 0.85f, 0, 180);
        path.moveTo(cx - w, cy + h * 0.45f);
        path.lineTo(cx - w, cy - h * 0.55f);
        path.addArc(cx - w, cy - h * 1.1f, cx + w * 0.2f, cy - h * 0.25f, 180, 180);
        canvas.drawPath(path, paint);
        canvas.restore();
        paint.setStyle(Paint.Style.FILL);
    }

    /** Микрофон: капсула, дужка и ножка — без ножки читался как силуэт человека. */
    private void drawMic(Canvas canvas, float cx, float cy) {
        float size = AndroidUtilities.dp(4.6f);
        rect.set(cx - size * 0.62f, cy - size * 1.7f, cx + size * 0.62f, cy + size * 0.25f);
        canvas.drawRoundRect(rect, size * 0.62f, size * 0.62f, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.4f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        rect.set(cx - size, cy - size * 0.55f, cx + size, cy + size * 0.9f);
        canvas.drawArc(rect, 15, 150, false, paint);
        canvas.drawLine(cx, cy + size * 0.9f, cx, cy + size * 1.7f, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** Смайл: кружок с точками и дугой. */
    private void drawSmile(Canvas canvas, float cx, float cy) {
        float r = AndroidUtilities.dp(6.5f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.4f));
        canvas.drawCircle(cx, cy, r, paint);
        rect.set(cx - r * 0.55f, cy - r * 0.25f, cx + r * 0.55f, cy + r * 0.55f);
        canvas.drawArc(rect, 25, 130, false, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx - r * 0.35f, cy - r * 0.28f, AndroidUtilities.dp(0.9f), paint);
        canvas.drawCircle(cx + r * 0.35f, cy - r * 0.28f, AndroidUtilities.dp(0.9f), paint);
    }

    /** Когда панель выключена, показываем это словами формы, а не пустотой. */
    private void drawCentredNote(Canvas canvas) {
        paint.setColor(NebulaTheme.stateLayer(theme.onSurfaceVariant(), 0.35f));
        float width = AndroidUtilities.dp(120);
        rect.set((getWidth() - width) / 2f, getHeight() / 2f - AndroidUtilities.dp(4),
                (getWidth() + width) / 2f, getHeight() / 2f + AndroidUtilities.dp(4));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), paint);
    }
}
