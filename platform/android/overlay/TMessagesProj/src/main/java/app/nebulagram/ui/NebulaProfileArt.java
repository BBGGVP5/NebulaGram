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
        // A peer can override profile colors. The decoration should follow
        // the app theme instead of inheriting that peer's pink accent.
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
    }
    private static int surface(Theme.ResourcesProvider provider) {
        return Theme.getColor(Theme.key_windowBackgroundWhite);
    }
    private static int ink(Theme.ResourcesProvider provider) {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, provider);
    }

    public static int sectionColor(Context context, Theme.ResourcesProvider provider) {
        NebulaTheme material = NebulaTheme.of(context);
        return ColorUtils.blendARGB(surface(provider), accent(provider), .065f);
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

        private int accentColor() { return accent(provider); }
        private int surfaceColor() { return surface(provider); }

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
        private final Path bannerClip = new Path();
        private LinearGradient gradient, bottomFade;
        private int previousFadeColor;
        private float previousFadeTop, previousFadeBottom;
        private int previousStart, previousEnd, previousWidth;
        private float previousTop, previousBottom;

        private final int[] savedRadii = new int[4];
        private final int[] squareRadii = new int[4];
        private float foregroundAlpha;

        public void draw(Canvas canvas, int width, View avatar, SimpleTextView title,
                         View subtitle, View actions, BackupImageView galleryPhoto, float headerBottom,
                         float progress, float expanded, float media, float opening,
                         Theme.ResourcesProvider provider) {
            foregroundAlpha = 0;
            Actions buttons = actions instanceof Actions ? (Actions) actions : null;
            if (buttons != null) buttons.bannerReady = false;
            final float alpha = clamp((progress - .25f) / .75f)
                    * (1f - clamp(media)) * clamp(opening);
            if (!NebulaAppearance.profileStyle() || avatar == null || title == null || subtitle == null
                    || alpha <= .01f || width < dp(240) || headerBottom <= dp(64)) {
                if (buttons != null) buttons.setBannerActive(false);
                return;
            }
            final float top = Math.max(dp(4), avatar.getY() - dp(14));
            // The native header includes the music row below the actions.
            // Its bounds also follow the search/media transition and expansion.
            final float bottom = headerBottom;
            rect.set(0, 0, width, bottom);
            BackupImageView photo = galleryPhoto != null && expanded > .5f
                    && galleryPhoto.getImageReceiver().hasImageLoaded() ? galleryPhoto : findPhoto(avatar);
            final boolean banner = NebulaAppearance.profilePhotoBanner() && photo != null
                    && photo.getImageReceiver().hasImageLoaded();
            if (buttons != null) buttons.setBannerActive(banner);
            // Keep Telegram's peer colour/emoji decoration when no photo is available.
            if (!banner) return;
            final NebulaTheme material = NebulaTheme.of(avatar.getContext());
            final int accent = accent(provider);
            int base = material.isDynamic() ? material.surfaceContainer() : surface(provider);
            final int titleColor = title.getTextPaint().getColor() | 0xff000000;
            if (ColorUtils.calculateContrast(titleColor, base | 0xff000000) < 4.5) {
                base = ColorUtils.blendARGB(base,
                        ColorUtils.calculateLuminance(titleColor) > .5 ? Color.BLACK : Color.WHITE, .85f);
            }
            final int start = ColorUtils.blendARGB(base, accent, .2f);
            if (gradient == null || previousStart != start || previousEnd != base || previousWidth != width ||
                    previousTop != top || previousBottom != bottom) {
                gradient = new LinearGradient(0, top, width, bottom, start, base, Shader.TileMode.CLAMP);
                previousStart = start; previousEnd = base; previousWidth = width;
                previousTop = top; previousBottom = bottom;
            }
            final int page = Theme.getColor(Theme.key_windowBackgroundGray, provider) | 0xff000000;
            final float fadeTop = Math.max(0, bottom - dp(148));
            if (bottomFade == null || previousFadeColor != page || previousFadeTop != fadeTop
                    || previousFadeBottom != bottom) {
                bottomFade = new LinearGradient(0, fadeTop, 0, bottom,
                        new int[] {ColorUtils.setAlphaComponent(page, 0),
                                ColorUtils.setAlphaComponent(page, 28),
                                ColorUtils.setAlphaComponent(page, 138), page},
                        new float[] {0f, .32f, .72f, 1f}, Shader.TileMode.CLAMP);
                previousFadeColor = page;
                previousFadeTop = fadeTop; previousFadeBottom = bottom;
            }
            foregroundAlpha = alpha;
            // Only the decorative image gives way to Telegram's native gallery.
            // The foreground fade and glass remain continuous through expansion.
            float backdropAlpha = alpha * (1f - clamp(expanded));
            if (backdropAlpha > 0) drawBannerBackdrop(canvas, photo.getImageReceiver(), rect, backdropAlpha);
            if (buttons != null && canvas.isHardwareAccelerated()) {
                buttons.captureBanner(this, photo.getImageReceiver(), rect);
            }
        }

        public void clear(View actions) {
            foregroundAlpha = 0;
            if (actions instanceof Actions) {
                ((Actions) actions).bannerReady = false;
                ((Actions) actions).setBannerActive(false);
            }
        }

        /** After native photo/blur children, before controls, music and title. */
        public void drawForeground(Canvas canvas) {
            if (foregroundAlpha <= 0 || bottomFade == null) return;
            paint.setShader(bottomFade);
            paint.setAlpha(Math.round(255 * foregroundAlpha));
            canvas.drawRect(rect.left, rect.top, rect.right, rect.bottom + 1f, paint);
            paint.setShader(null);
        }

        private void drawBannerBackdrop(Canvas canvas, ImageReceiver receiver, RectF bounds, float alpha) {
            drawPhotoBanner(canvas, receiver, bounds, alpha);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(gradient);
            paint.setAlpha(Math.round(255 * alpha * .20f));
            canvas.drawRect(bounds, paint);
            paint.setShader(null);
        }

        private void drawBannerSurface(Canvas canvas, ImageReceiver receiver, RectF bounds, float alpha) {
            drawBannerBackdrop(canvas, receiver, bounds, alpha);
            paint.setShader(bottomFade);
            paint.setAlpha(Math.round(255 * alpha));
            canvas.drawRect(bounds, paint);
            paint.setShader(null);
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
         * Р¤РѕСЂРјР° С€Р°РїРєРё вЂ” РїСЂСЏРјРѕСѓРіРѕР»СЊРЅРёРє РІРѕ РІСЃСЋ С€РёСЂРёРЅСѓ, Р±РµР· СЃРєСЂСѓРіР»РµРЅРёР№.
         *
         * <p>РЎРєСЂСѓРіР»РµРЅРёРµ СЃРЅРёР·Сѓ РІС‹РіР»СЏРґРµР»Рѕ РєР°Рє СЂР°РјРєР° РІРѕРєСЂСѓРі С„РѕС‚РѕРіСЂР°С„РёРё: РѕРЅР°
         * РѕР±СЂС‹РІР°Р»Р°СЃСЊ, РЅРµ РґРѕС…РѕРґСЏ РґРѕ РєСЂР°С‘РІ, Рё С€Р°РїРєР° С‡РёС‚Р°Р»Р°СЃСЊ РєР°Рє РєР°СЂС‚РѕС‡РєР°,
         * РІСЃС‚Р°РІР»РµРЅРЅР°СЏ РІ СЌРєСЂР°РЅ, Р° РЅРµ РєР°Рє СЃР°Рј РІРµСЂС… РїСЂРѕС„РёР»СЏ.
         */
        private static void heroPath(Path path, RectF bounds) {
            path.rewind();
            path.addRect(bounds, Path.Direction.CW);
        }

        private void drawPhotoBanner(Canvas canvas, ImageReceiver receiver, RectF target, float alpha) {
            final float imageX = receiver.getImageX(), imageY = receiver.getImageY();
            final float imageW = receiver.getImageWidth(), imageH = receiver.getImageHeight();
            final float imageAlpha = receiver.getAlpha();
            System.arraycopy(receiver.getRoundRadius(), 0, savedRadii, 0, 4);
            int save = canvas.save();
            try {
                heroPath(bannerClip, target);
                canvas.clipPath(bannerClip);
                receiver.setImageCoords(target);
                // The array overload preserves the user's base avatar radius.
                receiver.setRoundRadius(squareRadii);
                receiver.setAlpha(.78f * alpha);
                receiver.draw(canvas, null);
                paint.setShader(null);
                paint.setColor(Color.BLACK);
                paint.setAlpha((int) (110 * alpha));
                canvas.drawRect(target, paint);
            } finally {
                canvas.restoreToCount(save);
                receiver.setAlpha(imageAlpha);
                receiver.setRoundRadius(savedRadii);
                receiver.setImageCoords(imageX, imageY, imageW, imageH);
            }
        }
    }

    /** Native hit targets and press animation, with a shared photographic glass source. */
    public static final class Actions extends ProfileActionsView {
        private final Paint actionFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final NebulaProfileEmoji emoji = new NebulaProfileEmoji(this);
        private final Theme.ResourcesProvider provider;
        private NebulaProfileGlass glass;
        private boolean bannerReady, bannerActive, hasNativeColor, nativeHasColorById;
        private int nativeColor;
        public Actions(Context context, int height, Theme.ResourcesProvider provider) {
            super(context, height);
            this.provider = provider;
        }
        @Override public void setActionsColor(int color, boolean hasColorById) {
            nativeColor = color; nativeHasColorById = hasColorById; hasNativeColor = true;
            super.setActionsColor(bannerActive ? 0x66101010 : color, !bannerActive && hasColorById);
        }
        private void setBannerActive(boolean active) {
            if (bannerActive == active) return;
            bannerActive = active;
            if (hasNativeColor) super.setActionsColor(active ? 0x66101010 : nativeColor,
                    !active && nativeHasColorById);
        }
        @Override protected boolean hasCustomActionSurface(Canvas canvas) {
            return NebulaAppearance.profileStyle() && NebulaMenuStyle.enabled();
        }
        @Override public float getRoundRadius() { return dp(18); }
        @Override protected int actionTextColor(int color) {
            return bannerActive?android.graphics.Color.WHITE:Theme.getColor(Theme.key_windowBackgroundWhiteBlackText,provider);
        }
        @Override protected boolean drawActionEmoji(Canvas canvas, android.graphics.Rect bounds, int key, float alpha) {
            return emoji.draw(canvas,bounds,key,alpha);
        }
        @Override protected void onAttachedToWindow() {super.onAttachedToWindow();emoji.attach();}

        private void captureBanner(Hero hero, ImageReceiver receiver, RectF bounds) {
            if (!NebulaProfileGlass.supported()) return;
            if (glass == null) glass = new NebulaProfileGlass(provider);
            Canvas capture = glass.begin((int) Math.ceil(bounds.width()), (int) Math.ceil(bounds.height()));
            try { hero.drawBannerSurface(capture, receiver, bounds, 1f); }
            finally { glass.end(); }
            bannerReady = true;
        }

        @Override protected void drawActionSurface(Canvas canvas, RectF rect, int key, float radius, float alpha) {
            if (!NebulaMenuStyle.enabled()) return;
            if (bannerReady && glass != null && canvas.isHardwareAccelerated() && NebulaProfileGlass.supported()) {
                glass.draw(canvas, rect, key, radius, alpha, getX(), getY());
            } else {
                actionFill.setColor(bannerActive?0x882b3337:Theme.getColor(Theme.key_windowBackgroundGray,provider));
                actionFill.setAlpha(Math.round(android.graphics.Color.alpha(actionFill.getColor())*alpha));
                canvas.drawRoundRect(rect,radius,radius,actionFill);
            }
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            emoji.detach();
            glass = null;
            bannerReady = false; setBannerActive(false);
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
            int accentColor = accent(provider);
            paint.setColor(Theme.multAlpha(accentColor, .12f * alpha / 255f));
            canvas.drawRoundRect(rect, dp(10), dp(10), paint);
        }
        @Override public void setAlpha(int alpha) { this.alpha = alpha; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    public static final class IdentityBackground extends LabelBackground {
        public IdentityBackground(Context context, Theme.ResourcesProvider provider) { super(context, provider); }
        /**
         * Р“РѕС‚РѕРІС‹Р№ РіСЂР°РґРёРµРЅС‚. РЁРµР№РґРµСЂ вЂ” РЅР°С‚РёРІРЅС‹Р№ РѕР±СЉРµРєС‚, Рё РµРіРѕ СЃР±РѕСЂРєР° РЅР° РєР°Р¶РґС‹Р№
         * РєР°РґСЂ Р·Р°СЃС‚Р°РІР»СЏРµС‚ РєСЂР°СЃРєСѓ РїРµСЂРµСЃРѕР±РёСЂР°С‚СЊ РїСЂРѕРіСЂР°РјРјСѓ Р·Р°Р»РёРІРєРё; С€Р°РїРєР° РїСЂРѕС„РёР»СЏ
         * РїСЂРё РїСЂРѕРєСЂСѓС‚РєРµ СЂРёСЃСѓРµС‚СЃСЏ РїРѕСЃС‚РѕСЏРЅРЅРѕ. РЎРѕСЃРµРґРЅРёР№ РіСЂР°РґРёРµРЅС‚ РІС‹С€Рµ РїРѕ С„Р°Р№Р»Сѓ
         * РєСЌС€РёСЂСѓРµС‚СЃСЏ СЂРѕРІРЅРѕ С‚Р°Рє Р¶Рµ.
         */
        private LinearGradient gradient;
        private int previousStart, previousEnd;
        private float previousLeft, previousTop, previousRight, previousBottom;
        @Override public void draw(Canvas canvas) {
            rect.set(getBounds());
            int base = material.isDynamic() ? material.surfaceContainer() : surface(provider);
            int accentColor = accent(provider);
            int start = ColorUtils.blendARGB(base, accentColor, .22f);
            int end = ColorUtils.blendARGB(base, accentColor, .04f);
            if (gradient == null || previousStart != start || previousEnd != end
                    || previousLeft != rect.left || previousTop != rect.top
                    || previousRight != rect.right || previousBottom != rect.bottom) {
                gradient = new LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                        start, end, Shader.TileMode.CLAMP);
                previousStart = start; previousEnd = end;
                previousLeft = rect.left; previousTop = rect.top;
                previousRight = rect.right; previousBottom = rect.bottom;
            }
            paint.setShader(gradient);
            paint.setAlpha(alpha);
            canvas.drawRoundRect(rect, dp(24), dp(24), paint);
            paint.setShader(null);
        }
    }
}
