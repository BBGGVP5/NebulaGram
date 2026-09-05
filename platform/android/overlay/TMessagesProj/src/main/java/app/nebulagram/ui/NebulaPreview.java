package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
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
    private final Drawable backArrow;
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
        backArrow = context.getResources().getDrawable(R.drawable.ic_ab_back).mutate();
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
        int height = AndroidUtilities.dp(72);
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawHeader(canvas);
    }

    // --- шапка чата ---------------------------------------------------------

    private void drawHeader(Canvas canvas) {
        boolean styled = NebulaAppearance.chatHeader();
        float margin = AndroidUtilities.dp(8);
        float height = AndroidUtilities.dp(44);
        float top = (getHeight() - height) / 2f;
        float centerY = top + height / 2f;

        if (!styled) {
            paint.setColor(theme.surfaceContainer());
            rect.set(margin, top, getWidth() - margin, top + height);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), paint);
        }

        float arrowX = styled ? margin + height / 2f : margin + AndroidUtilities.dp(18);
        if (styled) drawGlassCircle(canvas, arrowX, centerY, height / 2f);
        int arrowHalf = AndroidUtilities.dp(12);
        backArrow.setColorFilter(theme.onSurface(), PorterDuff.Mode.SRC_IN);
        backArrow.setBounds(Math.round(arrowX) - arrowHalf, Math.round(centerY) - arrowHalf,
                Math.round(arrowX) + arrowHalf, Math.round(centerY) + arrowHalf);
        backArrow.draw(canvas);

        // В Liquid Glass имя по центру и аватар отдельно справа. Поэтому
        // видна ключевая разница, которую даёт переключатель в реальном чате.
        float avatarX = styled ? getWidth() - margin - height / 2f : margin + AndroidUtilities.dp(48);
        if (styled) drawGlassCircle(canvas, avatarX, centerY, height / 2f);
        float avatarRadius = AndroidUtilities.dp(styled ? 20 : 14);
        avatar.setRoundRadius((int) avatarRadius);
        avatar.setImageCoords(avatarX - avatarRadius, centerY - avatarRadius,
                avatarRadius * 2, avatarRadius * 2);
        avatar.draw(canvas);

        float textLeft = avatarX + AndroidUtilities.dp(22);
        paint.setTextSize(AndroidUtilities.dp(13));
        float capsuleWidth = NebulaChatStyle.headerWidth(getWidth(), (int) paint.measureText(accountName));
        float titleWidth = Math.max(0, capsuleWidth - AndroidUtilities.dp(36));
        float capsuleLeft = NebulaChatStyle.headerLeft(getWidth(), (int) capsuleWidth);
        float titleX = styled ? capsuleLeft + AndroidUtilities.dp(18) : textLeft;
        float titleCenter = capsuleLeft + capsuleWidth / 2f;
        if (styled) {
            rect.set(titleX - AndroidUtilities.dp(18), top + AndroidUtilities.dp(2),
                    titleX + titleWidth + AndroidUtilities.dp(18), top + height - AndroidUtilities.dp(2));
            drawGlassSurface(canvas, rect, AndroidUtilities.dp(20));
        }
        paint.setTextSize(AndroidUtilities.dp(13));
        drawLabel(canvas, accountName,
                styled ? titleCenter : textLeft, centerY - AndroidUtilities.dp(2),
                titleWidth + AndroidUtilities.dp(36),
                styled ? Paint.Align.CENTER : Paint.Align.LEFT, true, theme.onSurface());

        paint.setTextSize(AndroidUtilities.dp(10));
        drawLabel(canvas, accountStatus,
                styled ? titleCenter : textLeft, centerY + AndroidUtilities.dp(10),
                titleWidth + AndroidUtilities.dp(36),
                styled ? Paint.Align.CENTER : Paint.Align.LEFT, false,
                NebulaTheme.stateLayer(theme.onSurfaceVariant(), 0.85f));
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
