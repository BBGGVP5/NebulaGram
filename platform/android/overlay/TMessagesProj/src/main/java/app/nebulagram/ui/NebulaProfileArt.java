package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ProfileActionsView;

/** Decorative rendering only; Telegram owns the content and interaction. */
public final class NebulaProfileArt {
    private NebulaProfileArt() { }

    private static int dp(float value) { return AndroidUtilities.dp(value); }
    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static int accent(Theme.ResourcesProvider provider) {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, provider);
    }
    private static int surface(Theme.ResourcesProvider provider) {
        return Theme.getColor(Theme.key_windowBackgroundWhite, provider);
    }
    private static int ink(Theme.ResourcesProvider provider) {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, provider);
    }

    /** Tinted surfaces remain in the native section drawing/blur capture path. */
    public static final class Surface {
        private final Theme.ResourcesProvider provider;
        private final NebulaTheme material;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float[] radii = new float[8];
        public Surface(Context context, Theme.ResourcesProvider provider) {
            this.provider = provider;
            material = NebulaTheme.of(context);
        }

        private int accentColor() { return material.isDynamic() ? material.primary() : accent(provider); }
        private int surfaceColor() { return material.isDynamic() ? material.surfaceContainer() : surface(provider); }

        public void draw(Canvas canvas, RectF rect, float top, float bottom, float alpha) {
            radii[0] = radii[1] = radii[2] = radii[3] = top;
            radii[4] = radii[5] = radii[6] = radii[7] = bottom;
            path.rewind();
            path.addRoundRect(rect, radii, Path.Direction.CW);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Theme.multAlpha(ColorUtils.blendARGB(surfaceColor(), accentColor(), .065f), alpha));
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Theme.multAlpha(accentColor(), alpha * .12f));
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    /** Follows native avatar/title coordinates and disappears into native collapse. */
    public static final class Hero {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF ornament = new RectF();
        private final Path clip = new Path();
        private final Path bannerClip = new Path();
        private LinearGradient gradient;
        private int previousStart, previousEnd;
        private float previousTop, previousBottom;

        public void draw(Canvas canvas, int width, View avatar, SimpleTextView title,
                         View subtitle, float progress, float expanded, float media,
                         float opening, Theme.ResourcesProvider provider) {
            if (!NebulaAppearance.profileStyle() || avatar == null || title == null || subtitle == null) return;
            final float alpha = clamp((progress - .25f) / .75f) * (1f - clamp(expanded * 3f))
                    * (1f - clamp(media)) * clamp(opening);
            if (alpha <= .01f || width < dp(240)) return;
            final float top = Math.max(dp(4), avatar.getY() - dp(14));
            final float bottom = subtitle.getY() + subtitle.getHeight() + dp(14);
            if (bottom <= top + dp(64)) return;
            // Во всю ширину и до верхнего края: карточка с отступами читалась
            // как виджет внутри экрана, а не как шапка профиля.
            rect.set(0, 0, width, bottom);
            final BackupImageView photo = findPhoto(avatar);
            final boolean banner = NebulaAppearance.profilePhotoBanner() && photo != null
                    && photo.getImageReceiver().hasImageLoaded();
            if (banner) drawPhotoBanner(canvas, photo.getImageReceiver(), rect, alpha);
            final NebulaTheme material = NebulaTheme.of(avatar.getContext());
            final int accent = material.isDynamic() ? material.primary() : accent(provider);
            int base = material.isDynamic() ? material.surfaceContainer() : surface(provider);
            final int titleColor = title.getTextPaint().getColor() | 0xff000000;
            // Peer-selected profile colours can make the native title white
            // in a light theme. Keep its chosen contrast instead of recolouring it.
            if (ColorUtils.calculateContrast(titleColor, base | 0xff000000) < 4.5) {
                base = ColorUtils.blendARGB(base,
                        ColorUtils.calculateLuminance(titleColor) > .5 ? Color.BLACK : Color.WHITE, .85f);
            }
            final int start = ColorUtils.blendARGB(base, accent, .2f);
            if (gradient == null || previousStart != start || previousEnd != base ||
                    previousTop != top || previousBottom != bottom) {
                gradient = new LinearGradient(0, top, width, bottom, start, base, Shader.TileMode.CLAMP);
                previousStart = start;
                previousEnd = base;
                previousTop = top;
                previousBottom = bottom;
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(gradient);
            // A photo becomes the hero surface. Keep only a light colour veil
            // above it, so its darkened forms remain recognisable.
            paint.setAlpha((int) (255 * alpha * (banner ? .20f : 1f)));
            heroPath(clip, rect);
            canvas.drawPath(clip, paint);
            paint.setShader(null);

            // The arcs sit at the outside corner, clear of the title and avatar.
            int save = canvas.save();
            heroPath(clip, rect);
            canvas.clipPath(clip);
            final float x = LocaleController.isRTL ? rect.left : rect.right;
            ornament.set(x - dp(54), top - dp(40), x + dp(54), top + dp(68));
            paint.setColor(Theme.multAlpha(accent, .1f * alpha));
            paint.setStrokeWidth(dp(12));
            canvas.drawOval(ornament, paint);
            ornament.inset(dp(20), dp(20));
            paint.setStrokeWidth(dp(1));
            paint.setColor(Theme.multAlpha(accent, .22f * alpha));
            canvas.drawOval(ornament, paint);
            canvas.restoreToCount(save);
            paint.setStyle(Paint.Style.FILL);
        }

        private BackupImageView findPhoto(View root) {
            if (root instanceof BackupImageView) return (BackupImageView) root;
            if (!(root instanceof ViewGroup)) return null;
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                BackupImageView photo = findPhoto(group.getChildAt(i));
                if (photo != null) return photo;
            }
            return null;
        }

        /**
         * Форма шапки: скругление только снизу. Сверху она упирается в край
         * экрана, и круглые углы там повисли бы в воздухе.
         */
        private static void heroPath(Path path, RectF bounds) {
            final float r = dp(28);
            HERO_RADII[4] = HERO_RADII[5] = HERO_RADII[6] = HERO_RADII[7] = r;
            path.rewind();
            path.addRoundRect(bounds, HERO_RADII, Path.Direction.CW);
        }

        private static final float[] HERO_RADII = new float[8];

        private void drawPhotoBanner(Canvas canvas, ImageReceiver receiver, RectF target, float alpha) {
            final float imageX = receiver.getImageX();
            final float imageY = receiver.getImageY();
            final float imageW = receiver.getImageWidth();
            final float imageH = receiver.getImageHeight();
            final float imageAlpha = receiver.getAlpha();
            int save = canvas.save();
            heroPath(bannerClip, target);
            canvas.clipPath(bannerClip);
            receiver.setImageCoords(target);
            receiver.setAlpha(.78f * alpha);
            receiver.draw(canvas);
            paint.setColor(0xD8000000);
            paint.setAlpha((int) (190 * alpha));
            canvas.drawRect(target, paint);
            canvas.restoreToCount(save);
            receiver.setAlpha(imageAlpha);
            receiver.setImageCoords(imageX, imageY, imageW, imageH);
        }
    }

    /** Native actions with accent badges and comfortable label separation. */
    public static final class Actions extends ProfileActionsView {
        private final Theme.ResourcesProvider provider;
        private final NebulaTheme material;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF badge = new RectF();
        private float expanded;
        public Actions(Context context, int height, Theme.ResourcesProvider provider) {
            super(context, height);
            this.provider = provider;
            material = NebulaTheme.of(context);
        }
        private int accentColor() { return material.isDynamic() ? material.primary() : accent(provider); }
        private int surfaceColor() { return material.isDynamic() ? material.surfaceContainer() : surface(provider); }
        private int inkColor() { return material.isDynamic() ? material.onSurface() : ink(provider); }
        @Override public void setParentExpanded(float value) {
            expanded = clamp(value);
            super.setParentExpanded(value);
        }
        @Override public float getRoundRadius() { return dp(20 - 4 * expanded); }
        @Override protected void drawActionSurface(Canvas canvas, RectF rect, int key, float radius, float alpha) {
            alpha *= 1f - expanded;
            if (alpha <= 0) return;
            final int color = accentColor();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Theme.multAlpha(ColorUtils.blendARGB(surfaceColor(), color,
                    key == KEY_MESSAGE || key == KEY_JOIN ? .22f : .09f), alpha));
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Theme.multAlpha(color, alpha * .17f));
            canvas.drawRoundRect(rect, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override protected int actionTextColor(int original) {
            return ColorUtils.blendARGB(inkColor(), original, expanded);
        }
        @Override protected int actionIconColor(int original, int key) {
            return ColorUtils.blendARGB(accentColor(), original, expanded);
        }
        @Override protected float actionTextY(float original, int lines) {
            return original + (lines <= 2 ? dp(5) * (1f - expanded) : 0f);
        }
        @Override protected void drawActionIconBackground(Canvas canvas, Rect bounds, int key, int lines, float alpha) {
            if (lines > 2 || expanded >= 1f) return;
            badge.set(bounds.left - dp(4), bounds.top - dp(4), bounds.right + dp(4), bounds.bottom + dp(4));
            paint.setColor(Theme.multAlpha(accentColor(), .14f * alpha * (1f - expanded)));
            canvas.drawRoundRect(badge, dp(10), dp(10), paint);
        }
    }

    public static class LabelBackground extends Drawable {
        final Theme.ResourcesProvider provider;
        final NebulaTheme material;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF rect = new RectF();
        int alpha = 255;
        public LabelBackground(Context context, Theme.ResourcesProvider provider) {
            this.provider = provider;
            material = NebulaTheme.of(context);
        }
        @Override public void draw(Canvas canvas) {
            rect.set(getBounds());
            int accentColor = material.isDynamic() ? material.primary() : accent(provider);
            paint.setColor(Theme.multAlpha(accentColor, .12f * alpha / 255f));
            canvas.drawRoundRect(rect, dp(10), dp(10), paint);
        }
        @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    public static final class IdentityBackground extends LabelBackground {
        public IdentityBackground(Context context, Theme.ResourcesProvider provider) { super(context, provider); }
        @Override public void draw(Canvas canvas) {
            rect.set(getBounds());
            int base = material.isDynamic() ? material.surfaceContainer() : surface(provider);
            int accentColor = material.isDynamic() ? material.primary() : accent(provider);
            paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                    ColorUtils.blendARGB(base, accentColor, .22f),
                    ColorUtils.blendARGB(base, accentColor, .04f), Shader.TileMode.CLAMP));
            paint.setAlpha(alpha);
            canvas.drawRoundRect(rect, dp(24), dp(24), paint);
            paint.setShader(null);
        }
    }
}
