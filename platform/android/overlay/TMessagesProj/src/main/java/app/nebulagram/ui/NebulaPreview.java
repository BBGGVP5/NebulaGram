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
        boolean floating = NebulaAppearance.chatHeader();
        float top = AndroidUtilities.dp(14), height = AndroidUtilities.dp(44);
        float centerY = top + height / 2;
        float backWidth = NebulaHeaderCounter.backWidth();
        rect.set(AndroidUtilities.dp(6), top, backWidth - AndroidUtilities.dp(6), top + height);
        drawGlassSurface(canvas, rect, height / 2);
        int arrowHalf = AndroidUtilities.dp(12);
        int arrowX = AndroidUtilities.dp(29);
        backArrow.setColorFilter(theme.onSurface(), PorterDuff.Mode.SRC_IN);
        backArrow.setBounds(arrowX - arrowHalf, (int) centerY - arrowHalf, arrowX + arrowHalf, (int) centerY + arrowHalf);
        backArrow.draw(canvas);
        NebulaHeaderCounter.draw(canvas, getContext(), 0, centerY, 10);

        paint.setTextSize(AndroidUtilities.dp(17.5f));
        paint.setTypeface(AndroidUtilities.bold());
        float capsuleWidth = NebulaChatStyle.headerWidth(getWidth(), (int) paint.measureText(accountName));
        float capsuleLeft = NebulaChatStyle.headerLeft(getWidth(), (int) capsuleWidth);
        rect.set(capsuleLeft + AndroidUtilities.dp(6), top,
                capsuleLeft + capsuleWidth - AndroidUtilities.dp(6), top + height);
        drawGlassSurface(canvas, rect, height / 2);

        float avatarX = floating ? getWidth() - AndroidUtilities.dp(29) : capsuleLeft + AndroidUtilities.dp(30);
        if (floating) drawGlassCircle(canvas, avatarX, centerY, height / 2);
        float r = AndroidUtilities.dp(20);
        avatar.setRoundRadius((int) r);
        avatar.setImageCoords(avatarX - r, centerY - r, r * 2, r * 2);
        avatar.draw(canvas);
        if (!floating) {
            float menuX = getWidth() - AndroidUtilities.dp(29);
            drawGlassCircle(canvas, menuX, centerY, height / 2);
            paint.setColor(theme.onSurface());
            for (int i = -1; i <= 1; i++) canvas.drawCircle(menuX, centerY + AndroidUtilities.dp(6) * i, AndroidUtilities.dpf2(1.6f), paint);
        }
        float textX = floating ? capsuleLeft + capsuleWidth / 2 : avatarX + AndroidUtilities.dp(30);
        float textWidth = capsuleWidth - AndroidUtilities.dp(floating ? 40 : 68);
        paint.setTextSize(AndroidUtilities.dp(17.5f));
        drawLabel(canvas, accountName, textX, centerY - AndroidUtilities.dp(2), Math.max(0, textWidth),
                floating ? Paint.Align.CENTER : Paint.Align.LEFT, true, theme.onSurface());
        paint.setTextSize(AndroidUtilities.dp(12.5f));
        drawLabel(canvas, accountStatus, textX, centerY + AndroidUtilities.dp(13), Math.max(0, textWidth),
                floating ? Paint.Align.CENTER : Paint.Align.LEFT, false, NebulaTheme.stateLayer(theme.onSurfaceVariant(), .85f));
    }

    private void drawGlassCircle(Canvas canvas, float cx, float cy, float radius) {
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
        drawGlassSurface(canvas, rect, radius);
    }

    private void drawGlassSurface(Canvas canvas, RectF bounds, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorUtils.blendARGB(theme.surfaceContainer(), theme.primary(), .08f));
        canvas.drawRoundRect(bounds, radius, radius, paint);
        if (!NebulaAppearance.glassHighlights()) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dpf2(.75f));
        paint.setColor(NebulaTheme.stateLayer(theme.onSurface(), .18f));
        canvas.drawRoundRect(bounds, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

}
