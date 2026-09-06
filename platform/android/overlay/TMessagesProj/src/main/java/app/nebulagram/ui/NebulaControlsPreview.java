package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;

/** Live preference samples. Uses the account identity and the current theme. */
public final class NebulaControlsPreview extends View {
    public static final int ICONS = 0, FOLDERS = 1, MESSAGE = 2, PROFILE = 3;
    private final int kind;
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final ImageReceiver avatar = new ImageReceiver(this);
    private final ImageReceiver cover = new ImageReceiver(this);
    private String name = "NebulaGram";

    public NebulaControlsPreview(Context context, int kind) {
        super(context);
        this.kind = kind;
        TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        if (user != null) {
            name = UserObject.getUserName(user);
            avatar.setForUserOrChat(user, new AvatarDrawable(user));
            if (user.photo != null) cover.setForUserOrChat(user, null);
        }
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); avatar.onAttachedToWindow(); cover.onAttachedToWindow(); }
    @Override protected void onDetachedFromWindow() { cover.onDetachedFromWindow(); avatar.onDetachedFromWindow(); super.onDetachedFromWindow(); }
    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), AndroidUtilities.dp(kind == MESSAGE ? 240 : kind == PROFILE ? 236 : 76));
    }
    private float dp(float v) { return AndroidUtilities.dpf2(v); }
    private void box(Canvas c, float l, float t, float r, float b, int color, float radius) {
        paint.setColor(color); paint.setStyle(Paint.Style.FILL);
        rect.set(l, t, r, b); c.drawRoundRect(rect, dp(radius), dp(radius), paint);
    }
    private void text(Canvas c, String value, float x, float y, float width, float size, int color, boolean bold) {
        paint.setTextSize(dp(size)); paint.setColor(color); paint.setTypeface(bold ? AndroidUtilities.bold() : null);
        paint.setTextAlign(Paint.Align.LEFT);
        c.drawText(TextUtils.ellipsize(value, paint, Math.max(0, width), TextUtils.TruncateAt.END).toString(), x, y, paint);
    }
    private void centered(Canvas c, String text, float w, float y, float size, int color, boolean bold) {
        paint.setTextSize(dp(size)); paint.setTypeface(bold ? AndroidUtilities.bold() : null);
        String value = TextUtils.ellipsize(text, paint, w - dp(48), TextUtils.TruncateAt.END).toString();
        text(c, value, (w - paint.measureText(value)) / 2, y, w - dp(48), size, color, bold);
    }
    private void icon(Canvas c, int id, float x, float y, int color) {
        Drawable d = getContext().getResources().getDrawable(NebulaIcons.resource(id)).mutate();
        d.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        d.setBounds((int)(x - dp(12)), (int)(y - dp(12)), (int)(x + dp(12)), (int)(y + dp(12))); d.draw(c);
    }
    @Override protected void onDraw(Canvas c) {
        NebulaTheme t = NebulaTheme.of(getContext());
        float w = getWidth();
        if (kind == ICONS) {
            int[] icons = {R.drawable.msg_settings, R.drawable.msg_saved, R.drawable.msg_sendfile, R.drawable.msg_search, R.drawable.msg_calls};
            for (int i = 0; i < icons.length; i++) icon(c, icons[i], w * (i + .5f) / icons.length, dp(38), t.onSurface());
        } else if (kind == FOLDERS) {
            box(c, dp(8), dp(16), w - dp(8), dp(60), t.surfaceContainer(), 22);
            float cell = (w - dp(24)) / 3;
            box(c, dp(12), dp(20), dp(12) + cell, dp(56), NebulaTheme.stateLayer(t.primary(), .18f), 18);
            if (NebulaAppearance.folderOutline()) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.5f)); paint.setColor(t.primary());
                c.drawRoundRect(rect, dp(18), dp(18), paint); paint.setStyle(Paint.Style.FILL);
            }
            String[] names = {LocaleController.getString(R.string.FilterAllChats), LocaleController.getString(R.string.Contacts), LocaleController.getString(R.string.Filters)};
            for (int i = 0; i < 3; i++) {
                float x = dp(20) + cell * i;
                if (NebulaAppearance.folderStyle() != 0) icon(c, R.drawable.files_folder, x + dp(14), dp(38), i == 0 ? t.primary() : t.onSurfaceVariant());
                if (NebulaAppearance.folderStyle() != 1) text(c, names[(i + (NebulaAppearance.hideAllChats() ? 1 : 0)) % 3],
                        x + dp(NebulaAppearance.folderStyle() == 2 ? 32 : 0), dp(42), cell - dp(28), 12, t.onSurface(), i == 0);
                if (i == 0 && !NebulaAppearance.hideTabCounters()) text(c, "7", x + cell - dp(24), dp(42), dp(18), 11, t.primary(), true);
            }
        } else if (kind == MESSAGE) {
            float left = dp(42), right = w - dp(12);
            box(c, left, dp(12), right, dp(224), t.surfaceContainer(), 18);
            avatar.setRoundRadius(AndroidUtilities.dp(14)); avatar.setImageCoords(dp(8), dp(192), dp(28), dp(28)); avatar.draw(c);
            int accent = NebulaAppearance.replyColors() ? t.primary() : t.onSurfaceVariant();
            text(c, name, left + dp(12), dp(34), right - left - dp(24), 14, accent, true);
            if (NebulaAppearance.replyBackground()) box(c, left + dp(12), dp(46), right - dp(12), dp(101), NebulaTheme.stateLayer(accent, .12f), 5);
            box(c, left + dp(12), dp(46), left + dp(15), dp(101), accent, 1);
            text(c, name, left + dp(22), dp(66), right - left - dp(40), 12, accent, true);
            text(c, LocaleController.getString(R.string.NebulaPreviewReply), left + dp(22), dp(89), right - left - dp(40), 14, t.onSurface(), false);
            text(c, LocaleController.getString(R.string.NebulaPreviewMessage), left + dp(12), dp(126), right - left - dp(24), 14, t.onSurface(), false);
            box(c, left + dp(12), dp(140), right - dp(12), dp(193), NebulaTheme.stateLayer(accent, .1f), 5);
            text(c, "NebulaGram", left + dp(22), dp(162), right - left - dp(40), 13, accent, true);
            text(c, "github.com/BBGGVP5/NebulaGram", left + dp(22), dp(183), right - left - dp(40), 12, t.onSurface(), false);
            if (NebulaAppearance.replyEmoji()) {
                paint.setColor(NebulaTheme.stateLayer(accent, .15f));
                for (int i = 0; i < 3; i++) c.drawCircle(right - dp(20 + i * 20), dp(149), dp(4), paint);
            }
            text(c, NebulaAppearance.secondsInTime() ? "12:34:56" : "12:34", right - dp(66), dp(212), dp(58), 10, t.onSurfaceVariant(), false);
        } else {
            int surface = NebulaAppearance.profileBackground() ? androidx.core.graphics.ColorUtils.blendARGB(t.surfaceContainer(), t.primary(), .15f) : t.surface();
            box(c, dp(8), dp(8), w - dp(8), dp(226), surface, NebulaAppearance.profileStyle() ? 20 : 0);
            if (NebulaAppearance.profilePhotoBanner() && cover.hasImageSet()) {
                cover.setRoundRadius(AndroidUtilities.dp(20)); cover.setImageCoords(dp(8), dp(8), w - dp(16), dp(82)); cover.draw(c);
                box(c, dp(8), dp(8), w - dp(8), dp(90), 0x66000000, 20);
            }
            if (NebulaAppearance.profileBackground() && NebulaAppearance.profileEmoji()) {
                paint.setColor(NebulaTheme.stateLayer(t.primary(), .18f));
                for (int i = 0; i < 12; i++) c.drawCircle(dp(25) + (w - dp(50)) * (i % 4) / 3, dp(28 + 45 * (i / 4)), dp(3), paint);
            }
            avatar.setRoundRadius(AndroidUtilities.dp(32)); avatar.setImageCoords(w / 2 - dp(32), dp(18), dp(64), dp(64)); avatar.draw(c);
            centered(c, name, w, dp(110), 17, t.onSurface(), true);
            centered(c, LocaleController.getString(R.string.Online), w, dp(131), 13, t.onSurfaceVariant(), false);
            box(c, dp(8), dp(148), w - dp(8), dp(226), t.surfaceContainer(), 16);
            if (NebulaAppearance.profileChannel()) text(c, LocaleController.getString(R.string.NebulaProfileChannel), dp(24), dp(166), w - dp(48), 13, t.primary(), true);
            if (NebulaAppearance.profileBirthday()) text(c, LocaleController.getString(R.string.NebulaProfileBirthday), dp(24), dp(188), w - dp(48), 12, t.onSurfaceVariant(), false);
            if (NebulaAppearance.profileBusiness()) text(c, LocaleController.getString(R.string.NebulaProfileBusiness), dp(24), dp(210), w - dp(48), 12, t.onSurfaceVariant(), false);
        }
    }
}
