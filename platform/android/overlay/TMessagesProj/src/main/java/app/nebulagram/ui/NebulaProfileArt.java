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
        private int previousStart, previousEnd;
        private float previousTop, previousBottom;

        public void draw(Canvas canvas, int width, View avatar, SimpleTextView title,
                         View subtitle, View actions, float progress, float expanded, float media,
                         float opening, Theme.ResourcesProvider provider) {
            if (actions instanceof Actions) ((Actions) actions).bannerReady = false;
            if (!NebulaAppearance.profileStyle() || avatar == null || title == null || subtitle == null) return;
            // Р‘Р°РЅРЅРµСЂ СѓС…РѕРґРёС‚ СЂРѕРІРЅРѕ Р·Р° С‚Рѕ РІСЂРµРјСЏ, Р·Р° РєРѕС‚РѕСЂРѕРµ СЂР°СЃРєСЂС‹РІР°РµС‚СЃСЏ Р°РІР°С‚Р°СЂРєР°.
            // РўСЂРѕР№РЅРѕР№ РјРЅРѕР¶РёС‚РµР»СЊ РіР°СЃРёР» РµРіРѕ РЅР° РїРµСЂРІРѕР№ С‚СЂРµС‚Рё С…РѕРґР°, Р° СЂРѕРґРЅР°СЏ
            // С„РѕС‚РѕРіСЂР°С„РёСЏ Рє СЌС‚РѕРјСѓ РјРѕРјРµРЅС‚Сѓ РµС‰С‘ РЅРµ Р·Р°РєСЂС‹РІР°Р»Р° С€Р°РїРєСѓ вЂ” РјРµР¶РґСѓ РЅРёРјРё
            // РѕСЃС‚Р°РІР°Р»СЃСЏ РєР°РґСЂ СЃ РіРѕР»С‹Рј С„РѕРЅРѕРј, Рё СЌС‚Рѕ С‡РёС‚Р°Р»РѕСЃСЊ РєР°Рє РјРѕСЂРіР°РЅРёРµ.
            final float alpha = clamp((progress - .25f) / .75f) * (1f - clamp(expanded))
                    * (1f - clamp(media)) * clamp(opening);
            if (alpha <= .01f || width < dp(240)) return;
            final float top = Math.max(dp(4), avatar.getY() - dp(14));
            final float bottom = Math.max(subtitle.getY() + subtitle.getHeight() + dp(14),
                    actions != null && actions.getVisibility() == View.VISIBLE
                            ? actions.getY() + dp(74) : 0);
            if (bottom <= top + dp(64)) return;
            // Р’Рѕ РІСЃСЋ С€РёСЂРёРЅСѓ Рё РґРѕ РІРµСЂС…РЅРµРіРѕ РєСЂР°СЏ: РєР°СЂС‚РѕС‡РєР° СЃ РѕС‚СЃС‚СѓРїР°РјРё С‡РёС‚Р°Р»Р°СЃСЊ
            // РєР°Рє РІРёРґР¶РµС‚ РІРЅСѓС‚СЂРё СЌРєСЂР°РЅР°, Р° РЅРµ РєР°Рє С€Р°РїРєР° РїСЂРѕС„РёР»СЏ.
            rect.set(0, 0, width, bottom);
            final BackupImageView photo = findPhoto(avatar);
            final boolean banner = NebulaAppearance.profilePhotoBanner() && photo != null
                    && photo.getImageReceiver().hasImageLoaded();
            // Telegram's TopView already renders the peer's colour/emoji or the standard header.
            if (!banner) return;
            final NebulaTheme material = NebulaTheme.of(avatar.getContext());
            final int accent = accent(provider);
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
            // End in the exact page colour, including light and custom themes.
            // A long eased fade keeps the photograph behind the identity and actions
            // while removing its rectangular lower edge.
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
                previousFadeTop = fadeTop;
                previousFadeBottom = bottom;
            }
            drawBannerSurface(canvas, photo.getImageReceiver(), rect, alpha);
            if (actions instanceof Actions && canvas.isHardwareAccelerated()) {
                ((Actions) actions).captureBanner(this, photo.getImageReceiver(), rect);
            }
        }

        private void drawBannerSurface(Canvas canvas, ImageReceiver receiver, RectF bounds, float alpha) {
            drawPhotoBanner(canvas, receiver, bounds, alpha);
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(gradient);
            paint.setAlpha(Math.round(255 * alpha * .20f));
            canvas.drawRect(bounds, paint);
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
            final float imageX = receiver.getImageX();
            final float imageY = receiver.getImageY();
            final float imageW = receiver.getImageWidth();
            final float imageH = receiver.getImageHeight();
            final float imageAlpha = receiver.getAlpha();
            final int[] imageRadii = receiver.getRoundRadius().clone();
            int save = canvas.save();
            heroPath(bannerClip, target);
            canvas.clipPath(bannerClip);
            receiver.setImageCoords(target);
            // Decorative banner rendering must not change the avatar's base radius.
            receiver.setRoundRadius(new int[] {0, 0, 0, 0});
            receiver.setAlpha(.78f * alpha);
            // The one-argument draw applies the user's avatar shape; bypass it here.
            receiver.draw(canvas, null);
            paint.setColor(Color.BLACK);
            paint.setAlpha((int) (110 * alpha));
            canvas.drawRect(target, paint);
            canvas.restoreToCount(save);
            receiver.setAlpha(imageAlpha);
            receiver.setRoundRadius(imageRadii);
            receiver.setImageCoords(imageX, imageY, imageW, imageH);
        }
    }

    /** Native hit targets and press animation, with a shared photographic glass source. */
    public static final class Actions extends ProfileActionsView {
        private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Matrix sheenMatrix = new android.graphics.Matrix();
        private final LinearGradient sheenGradient = new LinearGradient(0, 0, 0, 1,
                new int[] {0x28ffffff, 0x08ffffff, 0x02ffffff},
                new float[] {0f, .48f, 1f}, Shader.TileMode.CLAMP);
        private final Theme.ResourcesProvider provider;
        private NebulaProfileGlass glass;
        private boolean bannerReady;
        public Actions(Context context, int height, Theme.ResourcesProvider provider) {
            super(context, height);
            this.provider = provider;
        }
        @Override public void setActionsColor(int color, boolean hasColorById) {
            // White native labels retain contrast over even a bright photograph.
            if (NebulaAppearance.profilePhotoBanner()) super.setActionsColor(0x66101010, false);
            else super.setActionsColor(color, hasColorById);
        }
        @Override public float getRoundRadius() { return dp(20); }

        private void captureBanner(Hero hero, ImageReceiver receiver, RectF bounds) {
            if (!NebulaProfileGlass.supported()) return;
            if (glass == null) glass = new NebulaProfileGlass(provider);
            Canvas capture = glass.begin(Math.round(bounds.width()), Math.round(bounds.height()));
            try { hero.drawBannerSurface(capture, receiver, bounds, 1f); }
            finally { glass.end(); }
            bannerReady = true;
        }

        @Override protected void drawActionSurface(Canvas canvas, RectF rect, int key, float radius, float alpha) {
            if (!NebulaMenuStyle.enabled()) return;
            if (bannerReady && glass != null && NebulaProfileGlass.supported()) {
                glass.draw(canvas, rect, key, radius, alpha, getX(), getY());
            }
            if (!NebulaAppearance.glassHighlights()) return;
            // A broad translucent highlight gives depth without outlining the button.
            sheenMatrix.setScale(1f, Math.max(1f, rect.height()));
            sheenMatrix.postTranslate(0f, rect.top);
            sheenGradient.setLocalMatrix(sheenMatrix);
            sheen.setShader(sheenGradient);
            sheen.setAlpha(Math.round(255 * alpha));
            canvas.drawRoundRect(rect, radius, radius, sheen);
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            glass = null;
            bannerReady = false;
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
