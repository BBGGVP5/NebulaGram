package app.nebulagram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow.ActionBarPopupWindowLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProviderBuilder;

/** One material, colour basis and motion for iOS-style menus. */
public final class NebulaMenuStyle {
    private static final java.util.WeakHashMap<View, Integer> rowColors = new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<View, SurfaceBinding> surfaces = new java.util.WeakHashMap<>();
    private static final class SurfaceBinding {
        Drawable drawable;
        int color;
        float opacity;
    }
    private NebulaMenuStyle() { }
    public static boolean enabled() { return NebulaAppearance.liquidAnimations() || NebulaAppearance.iosComposer() || NebulaAppearance.chatHeader(); }
    public static boolean animated() { return enabled() && NebulaAppearance.liquidAnimations(); }
    public static int radius() { return AndroidUtilities.dp(enabled() ? 24 : 12); }
    public static int surface(Theme.ResourcesProvider provider) {
        return NebulaMenuPalette.surface(provider != null ? provider.isDark() : Theme.isCurrentThemeDark());
    }
    public static float opacity() {
        return LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR) ? NebulaMenuPalette.opacity(NebulaGlass.opacity()) : 1f;
    }
    public static int foreground(int original, Theme.ResourcesProvider provider) {
        return NebulaChatColors.foreground(original, NebulaMenuPalette.contrastSurface(surface(provider), opacity()));
    }
    public static void prepare(ActionBarPopupWindowLayout host, Theme.ResourcesProvider provider) {
        if (!enabled()) return;
        Drawable drawable = host.getBackgroundDrawable();
        if (drawable == null) return; // A transparent swipe-back page is not a second surface.
        int color = surface(provider);
        float alpha = opacity();
        SurfaceBinding binding = surfaces.get(host);
        if (binding == null) { binding = new SurfaceBinding(); surfaces.put(host, binding); }
        if (binding.drawable != drawable || binding.color != color || binding.opacity != alpha) {
            if (drawable instanceof org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable) {
                ((org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable) drawable).setColorProvider(provider(provider));
            } else {
                host.setBackgroundDrawable(drawable = fallback(provider));
            }
            binding.drawable = drawable; binding.color = color; binding.opacity = alpha;
        }
    }
    public static BlurredBackgroundProvider provider(Theme.ResourcesProvider provider) {
        return new Material(provider)
                .setBackgroundColor((r, dark) -> Theme.multAlpha(surface(r),
                        opacity()))
                .setStrokeColorTop(0x30ffffff, 0x30ffffff)
                .setStrokeColorBottom(0x12000000, 0x14ffffff)
                .setStrokeWidth(AndroidUtilities.dpf2(.55f), AndroidUtilities.dpf2(.4f))
                .setShadowColor(0x20000000, 0x38000000)
                .setShadowLayer(AndroidUtilities.dpf2(4), 0, AndroidUtilities.dpf2(2)).build();
    }
    public static final class Material extends BlurredBackgroundProviderBuilder {
        Material(Theme.ResourcesProvider provider) { super(provider); }
    }
    public static Drawable fallback(Theme.ResourcesProvider provider) {
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {ColorUtils.blendARGB(surface(provider), Color.WHITE, .06f), surface(provider)});
        fill.setCornerRadius(radius()); fill.setStroke(AndroidUtilities.dp(1), 0x28ffffff);
        return new InsetDrawable(fill, AndroidUtilities.dp(8)) {
            // Native callers tint the old monochrome popup asset. This surface already has its palette.
            @Override public void setColorFilter(android.graphics.ColorFilter filter) { }
        };
    }
    public static int secondary(Theme.ResourcesProvider provider) {
        int background = surface(provider);
        int gray = ColorUtils.calculateLuminance(background) < .35 ? 0xffb2b2b2 : 0xff595959;
        return foreground(gray, provider);
    }
    public static Theme.ResourcesProvider ownerProvider(View view, Theme.ResourcesProvider fallback) {
        android.view.ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof ActionBarPopupWindowLayout) {
                return ((ActionBarPopupWindowLayout) parent).getNebulaResourcesProvider();
            }
            parent = parent.getParent();
        }
        return fallback;
    }
    public static void styleRows(View view, Theme.ResourcesProvider provider) {
        if (!enabled()) return;
        if (view instanceof ActionBarMenuSubItem) {
            ActionBarMenuSubItem row = (ActionBarMenuSubItem) view;
            int color = row.getTextView().getCurrentTextColor();
            // A near-black wallpaper tint is still ordinary text. Test contrast,
            // not saturation; retain semantic colours whenever they are readable.
            color = foreground(color, provider);
            row.setTextColor(color); row.setIconColor(color);
            // ThemeDescription can update TextView directly, bypassing the row's cache.
            if (row.getTextView().getCurrentTextColor() != color) row.getTextView().setTextColor(color);
            if (row.subtextView != null) {
                int subColor = foreground(row.subtextView.getCurrentTextColor(), provider);
                if (row.subtextView.getCurrentTextColor() != subColor) row.subtextView.setTextColor(subColor);
            }
            Integer previous = rowColors.get(row);
            if (previous == null || previous != color) rowColors.put(row, color);
            if ((previous == null || previous != color) && row.checkView != null) {
                final int checkColor = color;
                row.checkView.getCheckBoxBase().setResourcesProvider(new Theme.ResourcesProvider() {
                    @Override public int getColor(int key) {
                        return key == Theme.key_actionBarDefaultSubmenuItem ? checkColor : Theme.getColor(key, provider);
                    }
                });
            }
            if (previous == null || previous != color) row.setSelectorColor(Theme.multAlpha(color, .08f));
        } else if (view instanceof android.widget.TextView) {
            android.widget.TextView text = (android.widget.TextView) view;
            int color = foreground(text.getCurrentTextColor(), provider);
            if (color != text.getCurrentTextColor()) text.setTextColor(color);
        } else if (view instanceof org.telegram.ui.ActionBar.SimpleTextView) {
            org.telegram.ui.ActionBar.SimpleTextView text = (org.telegram.ui.ActionBar.SimpleTextView) view;
            int color = foreground(text.getTextColor(), provider);
            if (color != text.getTextColor()) text.setTextColor(color);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i=0;i<group.getChildCount();i++) styleRows(group.getChildAt(i), provider);
        }
    }
    public static AnimatorSet opening(ActionBarPopupWindowLayout content, float finalScaleY) {
        content.setBackScaleY(finalScaleY); content.setBackAlpha(255);
        for (int i = 0; i < content.getItemsCount(); i++) {
            View child = content.getItemAt(i);
            child.setTranslationY(0); child.setAlpha(child.isEnabled() ? 1f : .5f);
        }
        content.nebulaReveal.begin();
        ValueAnimator frame = ValueAnimator.ofFloat(0f, 1f);
        frame.setInterpolator(new android.view.animation.DecelerateInterpolator(2f));
        frame.addUpdateListener(a -> content.nebulaReveal.setProgress((float) a.getAnimatedValue()));
        AnimatorSet set = new AnimatorSet(); set.playTogether(frame); set.setDuration(260);
        set.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(Animator animation) {
                if (!cancelled) content.nebulaReveal.finish();
            }
        });
        return set;
    }
    public static AnimatorSet closing(ActionBarPopupWindowLayout content) {
        content.nebulaReveal.prepareClose();
        ValueAnimator frame = ValueAnimator.ofFloat(content.nebulaReveal.getProgress(), 0f);
        frame.setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f));
        frame.addUpdateListener(a -> content.nebulaReveal.setProgress((float) a.getAnimatedValue()));
        AnimatorSet set = new AnimatorSet(); set.playTogether(frame); set.setDuration(150);
        return set;
    }
}
