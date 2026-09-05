package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;

/** Account header preview; composer and navigation use their native controls. */
public class NebulaPreview extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private NebulaTheme theme;

    /**
     * Настоящие аватарка и имя аккаунта. Серые плашки вместо них требовали
     * догадываться, что именно окажется в шапке; со своим лицом и своим
     * именем превью читается как снимок будущего экрана, а не как схема.
     */
    private final org.telegram.messenger.ImageReceiver avatar =
            new org.telegram.messenger.ImageReceiver(this);
    private String accountName = "";
    private String accountStatus = "";

    public NebulaPreview(@NonNull Context context) {
        super(context);
        theme = NebulaTheme.of(context);
        loadAccount();
    }

    private void loadAccount() {
        try {
            int account = org.telegram.messenger.UserConfig.selectedAccount;
            org.telegram.tgnet.TLRPC.User self = org.telegram.messenger.MessagesController
                    .getInstance(account)
                    .getUser(org.telegram.messenger.UserConfig.getInstance(account).clientUserId);
            if (self == null) {
                return;
            }
            accountName = org.telegram.messenger.UserObject.getUserName(self);
            accountStatus = LocaleController.getString(R.string.Online);
            avatar.setForUserOrChat(self,
                    new org.telegram.ui.Components.AvatarDrawable(self));
        } catch (Throwable e) {
            // Превью — украшение: без аватарки оно всё равно показывает форму.
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatar.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        avatar.onDetachedFromWindow();
        super.onDetachedFromWindow();
    }

    /** Рисует текст, обрезая по ширине: длинное имя не должно лезть за край. */
    private void drawLabel(Canvas canvas, String text, float x, float y,
                           float maxWidth, Paint.Align align, boolean bold, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        paint.setTypeface(bold ? AndroidUtilities.bold() : null);
        paint.setTextAlign(align);
        paint.setColor(color);
        String shown = android.text.TextUtils.ellipsize(text, new android.text.TextPaint(paint),
                maxWidth, android.text.TextUtils.TruncateAt.END).toString();
        canvas.drawText(shown, x, y, paint);
        paint.setTypeface(null);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    /** Перерисовать после изменения настройки. */
    public void refresh() {
        theme = NebulaTheme.of(getContext());
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int height = AndroidUtilities.dp(92);
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Превью должно быть тем же фрагментом чата, а не карточкой со
        // случайными геометрическими фигурами. Так сразу видно, что именно
        // включит переключатель поверх реальных обоев.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.surface());
        rect.set(0, 0, getWidth(), getHeight());
        float radius = NebulaTheme.cornerMedium();
        canvas.drawRoundRect(rect, radius, radius, paint);
        drawWallpaper(canvas);
        drawHeader(canvas);
    }

    // --- шапка чата ---------------------------------------------------------

    private void drawHeader(Canvas canvas) {
        boolean styled = NebulaAppearance.chatHeader();
        float margin = AndroidUtilities.dp(14);
        float height = AndroidUtilities.dp(48);
        float top = AndroidUtilities.dp(20);
        float centerY = top + height / 2f;

        if (!styled) {
            paint.setColor(theme.surfaceContainer());
            rect.set(margin, top, getWidth() - margin, top + height);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), paint);
        }

        // Стрелка назад — она есть в обоих вариантах.
        paint.setColor(theme.onSurfaceVariant());
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.8f));
        float arrowX = styled ? margin + height / 2f : margin + AndroidUtilities.dp(18);
        if (styled) drawGlassCircle(canvas, arrowX, centerY, height / 2f);
        paint.setColor(theme.onSurface());
        // Стрелка целиком: остриё плюс древко. Без древка «галочка» читалась
        // как знак «меньше», а не как кнопка возврата.
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        float arrowSize = AndroidUtilities.dp(5);
        path.reset();
        path.moveTo(arrowX + arrowSize * 0.8f, centerY - arrowSize);
        path.lineTo(arrowX - arrowSize * 0.4f, centerY);
        path.lineTo(arrowX + arrowSize * 0.8f, centerY + arrowSize);
        canvas.drawPath(path, paint);
        canvas.drawLine(arrowX - arrowSize * 0.4f, centerY,
                arrowX + arrowSize * 1.7f, centerY, paint);
        paint.setStyle(Paint.Style.FILL);

        // В Liquid Glass имя по центру и аватар отдельно справа. Поэтому
        // видна ключевая разница, которую даёт переключатель в реальном чате.
        float avatarX = styled ? getWidth() - margin - height / 2f : margin + AndroidUtilities.dp(48);
        if (styled) drawGlassCircle(canvas, avatarX, centerY, height / 2f);
        float avatarRadius = AndroidUtilities.dp(styled ? 15 : 14);
        avatar.setRoundRadius((int) avatarRadius);
        avatar.setImageCoords(avatarX - avatarRadius, centerY - avatarRadius,
                avatarRadius * 2, avatarRadius * 2);
        avatar.draw(canvas);

        float textLeft = avatarX + AndroidUtilities.dp(22);
        float titleWidth = AndroidUtilities.dp(84);
        float titleX = styled ? (getWidth() - titleWidth) / 2f : textLeft;
        if (styled) {
            rect.set(titleX - AndroidUtilities.dp(18), top + AndroidUtilities.dp(4),
                    titleX + titleWidth + AndroidUtilities.dp(18), top + height - AndroidUtilities.dp(4));
            drawGlassSurface(canvas, rect, AndroidUtilities.dp(20));
        }
        paint.setTextSize(AndroidUtilities.dp(13));
        drawLabel(canvas, accountName,
                styled ? getWidth() / 2f : textLeft, centerY - AndroidUtilities.dp(2),
                titleWidth + AndroidUtilities.dp(36),
                styled ? Paint.Align.CENTER : Paint.Align.LEFT, true, theme.onSurface());

        paint.setTextSize(AndroidUtilities.dp(10));
        drawLabel(canvas, accountStatus,
                styled ? getWidth() / 2f : textLeft, centerY + AndroidUtilities.dp(10),
                titleWidth + AndroidUtilities.dp(36),
                styled ? Paint.Align.CENTER : Paint.Align.LEFT, false,
                NebulaTheme.stateLayer(theme.onSurfaceVariant(), 0.85f));
    }

    private void drawWallpaper(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1));
        paint.setColor(NebulaTheme.stateLayer(theme.primary(), .16f));
        for (int i = 0; i < 6; i++) {
            float x = AndroidUtilities.dp(18 + i * 54);
            float y = AndroidUtilities.dp(16 + (i % 3) * 25);
            rect.set(x, y, x + AndroidUtilities.dp(26), y + AndroidUtilities.dp(18));
            canvas.drawArc(rect, 20 + i * 23, 210, false, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawGlassCircle(Canvas canvas, float cx, float cy, float radius) {
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        drawGlassSurface(canvas, rect, radius);
    }

    private void drawGlassSurface(Canvas canvas, RectF bounds, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.blendARGB(theme.surfaceContainer(), theme.primary(), .08f));
        canvas.drawRoundRect(bounds, radius, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dpf2(.75f));
        paint.setColor(NebulaTheme.stateLayer(theme.onSurface(), .18f));
        canvas.drawRoundRect(bounds, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

}
