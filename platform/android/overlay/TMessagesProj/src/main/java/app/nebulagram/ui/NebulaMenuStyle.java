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
    private NebulaMenuStyle() { }
    public static boolean enabled() { return NebulaAppearance.iosComposer() || NebulaAppearance.chatHeader(); }
    public static boolean animated() { return enabled() && NebulaAppearance.liquidAnimations(); }
    public static int radius() { return AndroidUtilities.dp(enabled() ? 24 : 12); }
    public static int surface(Theme.ResourcesProvider provider) {
        if (NebulaTheme.materialYouEnabled()) {
            NebulaTheme theme = NebulaTheme.of(org.telegram.messenger.ApplicationLoader.applicationContext);
            return ColorUtils.blendARGB(theme.surfaceContainer(), theme.primary(), .035f);
        }
        int base = Theme.getColor(Theme.key_windowBackgroundWhite, provider) | 0xff000000;
        int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, provider);
        return ColorUtils.blendARGB(base, accent, .09f);
    }
    public static BlurredBackgroundProvider provider(Theme.ResourcesProvider provider) {
        return new Material(provider)
                .setBackgroundColor((r, dark) -> Theme.multAlpha(surface(r),
                        LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR) ? .72f : 1f))
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
        return NebulaChatColors.foreground(gray, background);
    }
    public static void styleRows(View view, Theme.ResourcesProvider provider) {
        if (!enabled()) return;
        if (view instanceof ActionBarMenuSubItem) {
            ActionBarMenuSubItem row = (ActionBarMenuSubItem) view;
            int color = row.getTextView().getCurrentTextColor();
            // A near-black wallpaper tint is still ordinary text. Test contrast,
            // not saturation; retain semantic colours whenever they are readable.
            color = NebulaChatColors.foreground(color, surface(provider));
            row.setTextColor(color); row.setIconColor(color);
            // ThemeDescription can update TextView directly, bypassing the row's cache.
            row.getTextView().setTextColor(color);
            if (row.checkView != null) {
                final int checkColor = color;
                row.checkView.getCheckBoxBase().setResourcesProvider(new Theme.ResourcesProvider() {
                    @Override public int getColor(int key) {
                        return key == Theme.key_actionBarDefaultSubmenuItem ? checkColor : Theme.getColor(key, provider);
                    }
                });
            }
            row.setSelectorColor(Theme.multAlpha(color, .08f));
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i=0;i<group.getChildCount();i++) styleRows(group.getChildAt(i), provider);
        }
    }
    public static AnimatorSet opening(ActionBarPopupWindowLayout content, float finalScaleY) {
        content.setBackScaleY(finalScaleY); content.setBackAlpha(255);
        content.setScaleX(1); content.setScaleY(1); content.setAlpha(0);
        content.nebulaReveal.begin();
        ValueAnimator frame = ValueAnimator.ofFloat(0f, 1f);
        frame.setInterpolator(new android.view.animation.LinearInterpolator());
        frame.addUpdateListener(a -> {
            float t=(float)a.getAnimatedValue();
            float p=1-(float)Math.pow(1-t, 4);
            content.nebulaReveal.setProgress(p);
            content.setAlpha(Math.min(1,t*9));
            for(int i=0;i<content.getItemsCount();i++) {
                View child=content.getItemAt(i);
                child.setAlpha(Math.max(0,Math.min(1, (t-.12f)*3))*(child.isEnabled()?1f:.5f));
                child.setTranslationY(AndroidUtilities.dp(content.shownFromBottom ? 6 : -6)*(1-p));
            }
        });
        AnimatorSet set=new AnimatorSet(); set.playTogether(frame); set.setDuration(240);
        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                content.setScaleX(1); content.setScaleY(1); content.setAlpha(1);
                content.nebulaReveal.finish();
                for(int i=0;i<content.getItemsCount();i++) {
                    View child=content.getItemAt(i); child.setTranslationY(0); child.setAlpha(child.isEnabled()?1f:.5f);
                }
            }
        });
        return set;
    }
    public static AnimatorSet closing(ActionBarPopupWindowLayout content) {
        ValueAnimator frame = ValueAnimator.ofFloat(1f, 0f);
        frame.setInterpolator(new android.view.animation.AccelerateInterpolator());
        frame.addUpdateListener(a -> {
            float p=(float)a.getAnimatedValue();
            content.nebulaReveal.setProgress(p);
            content.setAlpha(p);
        });
        AnimatorSet set = new AnimatorSet(); set.playTogether(frame); set.setDuration(160);
        return set;
    }
}
