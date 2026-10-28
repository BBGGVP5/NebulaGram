package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

/**
 * Что получится: строка чата, поле поиска и две кнопки — в текущей форме.
 *
 * <p>Превью рисует то же, что настройка задаёт приложению, а не похожую
 * картинку: радиусы берутся из {@link NebulaShape} теми же вызовами, которыми
 * ими пользуются карточки и фото профиля. Иначе ползунок показывал бы одно, а
 * приложение после выхода с экрана — другое.
 */
public final class NebulaShapePreview extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final float[] radii = new float[8];

    public NebulaShapePreview(Context context) {
        super(context);
    }

    private static int dp(float value) { return AndroidUtilities.dp(value); }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), dp(176));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        NebulaTheme theme = NebulaTheme.of(getContext());
        final int left = dp(16), right = getWidth() - dp(16);

        // Карточка-подложка: её радиус — тот же, что у карточек настроек.
        fill.setColor(NebulaTheme.stateLayer(theme.onSurface(), .05f));
        rect.set(left, dp(12), right, getHeight() - dp(12));
        float card = NebulaShape.radius(20);
        canvas.drawRoundRect(rect, card, card, fill);

        final int inner = left + dp(14);
        final int side = dp(44);
        drawAvatar(canvas, inner, dp(28), side, theme);

        fill.setColor(NebulaTheme.stateLayer(theme.onSurface(), .18f));
        bar(canvas, inner + side + dp(12), dp(36), right - dp(54), dp(8));
        fill.setColor(NebulaTheme.stateLayer(theme.onSurface(), .10f));
        bar(canvas, inner + side + dp(12), dp(52), right - dp(86), dp(8));

        fill.setColor(NebulaTheme.stateLayer(theme.onSurface(), .14f));
        canvas.drawCircle(right - dp(24), dp(50), dp(2.5f), fill);
        canvas.drawCircle(right - dp(24), dp(43), dp(2.5f), fill);
        canvas.drawCircle(right - dp(24), dp(57), dp(2.5f), fill);

        // Поле поиска — капсула, пока скругление не уводят в прямые углы.
        fill.setColor(NebulaTheme.stateLayer(theme.onSurface(), .08f));
        rect.set(inner, dp(86), right - dp(14), dp(116));
        float search = NebulaShape.radius(15);
        canvas.drawRoundRect(rect, search, search, fill);
        fill.setColor(NebulaTheme.stateLayer(theme.onSurface(), .20f));
        canvas.drawCircle(inner + dp(16), dp(101), dp(5), fill);

        float button = NebulaShape.radius(16);
        fill.setColor(theme.primary());
        rect.set(inner, dp(128), (left + right) / 2f - dp(5), dp(160));
        canvas.drawRoundRect(rect, button, button, fill);
        fill.setColor(NebulaTheme.stateLayer(theme.onSurface(), .12f));
        rect.set((left + right) / 2f + dp(5), dp(128), right - dp(14), dp(160));
        canvas.drawRoundRect(rect, button, button, fill);
    }

    private void bar(Canvas canvas, float left, float top, float right, float height) {
        rect.set(left, top, right, top + height);
        canvas.drawRoundRect(rect, height / 2f, height / 2f, fill);
    }

    /** Фото профиля рисуется теми же четырьмя углами, что уходят в ImageReceiver. */
    private void drawAvatar(Canvas canvas, int left, int top, int side, NebulaTheme theme) {
        int[] corners = NebulaShape.avatarRadii(side);
        for (int i = 0; i < 4; i++) {
            radii[i * 2] = corners[i];
            radii[i * 2 + 1] = corners[i];
        }
        rect.set(left, top, left + side, top + side);
        path.reset();
        path.addRoundRect(rect, radii, Path.Direction.CW);
        fill.setColor(theme.primary());
        canvas.drawPath(path, fill);
    }
}
