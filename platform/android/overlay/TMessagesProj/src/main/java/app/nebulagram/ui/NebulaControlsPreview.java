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
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.ActionBar.Theme;

/** Live preference samples. Uses the account identity and the current theme. */
public final class NebulaControlsPreview extends View {
    public static final int ICONS = 0, FOLDERS = 1, MESSAGE = 2, PROFILE = 3;
    private final int kind;
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final ImageReceiver avatar = new ImageReceiver(this);
    private final ImageReceiver cover = new ImageReceiver(this);
    private String name = "NebulaGram";
    private final int account = UserConfig.selectedAccount;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable profileEmoji =
            new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, AndroidUtilities.dp(20));

    public NebulaControlsPreview(Context context, int kind) {
        super(context);
        this.kind = kind;
        profileEmoji.setCurrentAccount(account);
        TLRPC.User user = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        if (user != null) {
            name = UserObject.getUserName(user);
            avatar.setForUserOrChat(user, new AvatarDrawable(user));
            if (user.photo != null) cover.setForUserOrChat(user, null);
        }
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); avatar.onAttachedToWindow(); cover.onAttachedToWindow(); profileEmoji.attach(); }
    @Override protected void onDetachedFromWindow() { profileEmoji.detach(); cover.onDetachedFromWindow(); avatar.onDetachedFromWindow(); super.onDetachedFromWindow(); }
    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), AndroidUtilities.dp(kind == MESSAGE ? 240 : kind == PROFILE ? profileHeight() : 76));
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
            drawProfile(c, t, w);
        }
    }

    /** Строки карточки «Информация» — в том же наборе, что и настоящий профиль. */
    private int infoRows() {
        return 1 + 1
                + (NebulaAppearance.profileBirthday() ? 1 : 0) + (NebulaAppearance.profileBusiness() ? 1 : 0);
    }

    /** Высота живого профиля: шапка, канал и карточка сведений. В dp. */
    private int profileHeight() {
        return 206 + (NebulaAppearance.profileChannel() ? 82 : 0) + 46 + 50 * infoRows() + 12;
    }

    private MessagesController.PeerColor peerColor(TLRPC.User user) {
        if (!NebulaAppearance.profileBackground() || user == null) return null;
        MessagesController.PeerColor peer = MessagesController.PeerColor.fromCollectible(user.emoji_status);
        MessagesController.PeerColors colors = MessagesController.getInstance(account).profilePeerColors;
        int profileColorId = UserObject.getProfileColorId(user);
        if (peer == null && colors != null && profileColorId >= 0) peer = colors.getColor(profileColorId);
        return peer;
    }

    /**
     * Профиль целиком, как его покажет приложение: шапка в цвете профиля или с
     * фотобаннером, а ниже — тот же цвет на всю страницу и полупрозрачные
     * карточки, если включён новый дизайн. Без него — обычный фон и карточки.
     */
    private void drawProfile(Canvas c, NebulaTheme t, float w) {
        final boolean dark = Theme.isCurrentThemeDark();
        final float h = getHeight(), header = dp(196);
        TLRPC.User user = UserConfig.getInstance(account).getCurrentUser();
        MessagesController.PeerColor peer = peerColor(user);
        final boolean styled = NebulaAppearance.profileStyle();
        final boolean photo = NebulaAppearance.profilePhotoBanner() && cover.hasImageSet();
        int save = c.save();
        android.graphics.Path clip = new android.graphics.Path();
        clip.addRoundRect(0, 0, w, h, dp(20), dp(20), android.graphics.Path.Direction.CW);
        c.clipPath(clip);

        // Страница под шапкой.
        int base = Theme.getColor(Theme.key_windowBackgroundGray) | 0xff000000;
        int page = styled && peer != null ? peer.getBgColor1(dark) | 0xff000000 : base;
        if (styled && peer != null) {
            paint.setShader(new android.graphics.LinearGradient(0, header, 0, h, page,
                    androidx.core.graphics.ColorUtils.blendARGB(page, base, dark ? .3f : .45f),
                    android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, header, w, h, paint); paint.setShader(null);
        } else {
            paint.setColor(base); c.drawRect(0, header, w, h, paint);
        }

        // Шапка.
        if (photo) {
            cover.setRoundRadius(0); cover.setImageCoords(0, 0, w, header); cover.draw(c);
            int fade = styled && peer != null ? page : base;
            paint.setShader(new android.graphics.LinearGradient(0, 0, 0, header,
                    new int[] {0x66000000, 0x88000000, fade}, new float[] {0f, .6f, 1f},
                    android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, header, paint); paint.setShader(null);
        } else if (peer != null) {
            paint.setShader(new android.graphics.RadialGradient(w / 2, dp(70), Math.max(w / 2, header),
                    peer.getBgColor2(dark), peer.getBgColor1(dark), android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, header, paint); paint.setShader(null);
        } else {
            paint.setColor(Theme.getColor(Theme.key_actionBarDefault)); c.drawRect(0, 0, w, header, paint);
        }
        long emojiId = !photo && user != null && NebulaAppearance.profileBackground() && NebulaAppearance.profileEmoji()
                ? UserObject.getOnlyProfileEmojiId(user) : 0;
        profileEmoji.set(emojiId, false);
        if (emojiId != 0) {
            profileEmoji.setColor(peer != null && peer.patternColor != 0 ? peer.patternColor : 0xffffffff);
            profileEmoji.setAlpha(90);
            // Кольцо узора вокруг аватарки, как у Telegram.
            for (int i = 0; i < 10; i++) {
                double angle = Math.PI * 2 * i / 10 + .3;
                float radius = dp(i % 2 == 0 ? 74 : 104);
                float x = (float) (w / 2 + Math.cos(angle) * radius * 1.25f), y = (float) (dp(70) + Math.sin(angle) * radius * .78f);
                float size = dp(i % 2 == 0 ? 11 : 9);
                profileEmoji.setBounds((int) (x - size), (int) (y - size), (int) (x + size), (int) (y + size));
                profileEmoji.draw(c);
            }
        }
        final boolean light = photo || peer != null || Theme.getColor(Theme.key_actionBarDefaultTitle) == 0xffffffff;
        paint.setColor(0x55ffffff); c.drawCircle(w / 2, dp(70), dp(41), paint);
        avatar.setRoundRadius(AndroidUtilities.dp(38)); avatar.setImageCoords(w / 2 - dp(38), dp(32), dp(76), dp(76)); avatar.draw(c);
        int title = light ? 0xffffffff : Theme.getColor(Theme.key_actionBarDefaultTitle);
        centered(c, name, w, dp(146), 20, title, true);
        centered(c, LocaleController.getString(R.string.Online), w, dp(170), 14, light ? 0xd9ffffff : Theme.getColor(Theme.key_actionBarDefaultSubtitle), false);

        // Карточки.
        final int card = styled && peer != null ? (dark ? 0x42000000 : 0xebffffff) : Theme.getColor(Theme.key_windowBackgroundWhite);
        final int ink = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        final int muted = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2);
        final int accent = styled && peer != null && dark ? androidx.core.graphics.ColorUtils.blendARGB(peer.getBgColor2(true), 0xffffffff, .55f) : t.primary();
        final float left = dp(12), right = w - dp(12), radius = styled ? 20 : 14;
        float y = dp(206);
        if (NebulaAppearance.profileChannel()) {
            box(c, left, y, right, y + dp(72), card, radius);
            text(c, NebulaText.text("Канал", "Channel"), left + dp(16), y + dp(24), dp(120), 13, accent, true);
            paint.setColor(t.primary()); c.drawCircle(left + dp(36), y + dp(48), dp(17), paint);
            icon(c, R.drawable.msg_channel, left + dp(36), y + dp(48), 0xffffffff);
            text(c, "NebulaGram", left + dp(64), y + dp(44), right - left - dp(80), 14, ink, true);
            text(c, NebulaText.text("Новости и обновления", "News and updates"), left + dp(64), y + dp(62), right - left - dp(80), 12, muted, false);
            y += dp(82);
        }
        float bottom = y + dp(46) + dp(50) * infoRows();
        box(c, left, y, right, bottom, card, radius);
        text(c, NebulaText.text("Информация", "Info"), left + dp(16), y + dp(28), dp(160), 13, accent, true);
        y += dp(46);
        // A hidden number stays in place, blurred, as in the real profile.
        row(c, NebulaMessagePreferences.enabled("hide_profile_phone", false) ? "•••  •••  ••  ••" : "+7 900 ••• •• ••",
                NebulaText.text("Мобильный", "Mobile"), left, right, y, ink, muted, 0, 0); y += dp(50);
        int dc = user != null && user.photo != null && NebulaMessagePreferences.enabled("profile_dc", true) ? user.photo.dc_id : 0;
        row(c, NebulaProfileStyle.idTitle(user != null ? NebulaIds.user(user.id) : "1 694 811 956"),
                NebulaProfileStyle.idSubtitle(NebulaText.text("ID пользователя", "User ID"), dc),
                left, right, y, ink, muted, R.drawable.msg_calendar2, accent);
        y += dp(50);
        if (NebulaAppearance.profileBirthday()) {
            row(c, NebulaText.text("22 июля", "July 22"), NebulaText.text("День рождения", "Birthday"), left, right, y, ink, muted, 0, 0); y += dp(50);
        }
        if (NebulaAppearance.profileBusiness()) {
            row(c, NebulaText.text("Открыто", "Open"), NebulaText.text("Часы работы", "Business hours"), left, right, y, 0xff4fbf62, muted, 0, 0);
        }
        c.restoreToCount(save);
    }

    private void row(Canvas c, String value, String label, float left, float right, float y,
                     int ink, int muted, int icon, int iconColor) {
        float width = right - left - dp(icon != 0 ? 72 : 32);
        text(c, value, left + dp(16), y + dp(18), width, 15, ink, false);
        text(c, label, left + dp(16), y + dp(38), width, 12, muted, false);
        if (icon != 0) icon(c, icon, right - dp(30), y + dp(24), iconColor);
    }
}
