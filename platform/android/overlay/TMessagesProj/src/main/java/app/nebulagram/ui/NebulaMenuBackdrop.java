package app.nebulagram.ui;

import android.graphics.Canvas;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;

/** Captures the originating window, including its wallpaper, for a separate popup window. */
public final class NebulaMenuBackdrop {
    private NebulaMenuBackdrop() { }
    public static void attach(View popup, View anchor, Theme.ResourcesProvider provider) {
        attach(popup, anchor, provider, true);
    }

    /**
     * То же стекло, но без собственных отступов.
     *
     * <p>{@code View.setBackground} спрашивает у фона его отступы и применяет их
     * к самому виду. Обычным меню это на руку — их содержимое и должно отступить
     * от края. А панель выделения текста считает свою ширину и высоту сама,
     * заранее, и такой отступ она не учитывает: строка получалась узкой и
     * обрезанной, а всплывающий список — с пустотой вместо пунктов.
     */
    public static void attachFlat(View popup, View anchor, Theme.ResourcesProvider provider) {
        attach(popup, anchor, provider, false);
    }

    /** A readable sheet surface sampled from the separate originating chat window. */
    public static void attachSheet(View sheet, View anchor, Theme.ResourcesProvider provider) {
        attachEditorSheet(sheet, anchor, provider, null);
    }

    static void attachEditorSheet(View sheet, View anchor, Theme.ResourcesProvider provider, NebulaEditorTabs tabs) {
        if (anchor == null || Build.VERSION.SDK_INT < 31 || !LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)) {
            return;
        }
        attachHardware(sheet, anchor.getRootView(), provider, false, true, tabs);
    }

    private static void attach(View popup, View anchor, Theme.ResourcesProvider provider, boolean padded) {
        if (Build.VERSION.SDK_INT < 31 || !LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)) {
            popup.setBackground(NebulaMenuStyle.fallback(provider));
            return;
        }
        attachHardware(popup, anchor.getRootView(), provider, padded);
    }

    @android.annotation.TargetApi(31)
    private static void attachHardware(View popup, View root, Theme.ResourcesProvider provider, boolean padded) {
        attachHardware(popup, root, provider, padded, false);
    }

    @android.annotation.TargetApi(31)
    private static void attachHardware(View popup, View root, Theme.ResourcesProvider provider, boolean padded, boolean sheet) {
        attachHardware(popup, root, provider, padded, sheet, null);
    }

    @android.annotation.TargetApi(31)
    private static void attachHardware(View popup, View root, Theme.ResourcesProvider provider, boolean padded, boolean sheet, NebulaEditorTabs tabs) {
        BlurredBackgroundSourceColor fallback = new BlurredBackgroundSourceColor();
        fallback.setColor(NebulaMenuStyle.surface(provider));
        BlurredBackgroundSourceRenderNode source = new BlurredBackgroundSourceRenderNode(fallback);
        source.setBlur(AndroidUtilities.dpf2(NebulaGlass.blur()));
        BlurredBackgroundDrawableViewFactory factory = new BlurredBackgroundDrawableViewFactory(source);
        factory.setLiquidGlassEffectAllowed(NebulaMenuStyle.animated());
        BlurredBackgroundDrawable material = factory.create(popup, true)
                .setColorProvider(tabs != null ? NebulaMenuStyle.editorSheetProvider(provider)
                        : sheet ? NebulaMenuStyle.sheetProvider(provider) : NebulaMenuStyle.provider(provider)).setRadius(NebulaMenuStyle.radius())
                .setPadding(padded ? AndroidUtilities.dp(8) : 0).setHasPadding(padded);
        material.setThickness(AndroidUtilities.dp(5)); material.setIntensity(NebulaGlass.refraction());
        popup.setBackground(material);
        BlurredBackgroundDrawable selection = tabs == null ? null : factory.create(tabs, true)
                .setColorProvider(NebulaFolderGlass.provider(provider)).setRadius(AndroidUtilities.dp(24))
                .setPadding(0).setHasPadding(false);
        if (selection != null) {
            selection.setThickness(AndroidUtilities.dp(7)); selection.setIntensity(NebulaGlass.refraction());
            tabs.setGlass(selection);
        }
        int[] origin = new int[2], position = new int[2], tabPosition = new int[2];
        ViewTreeObserver.OnPreDrawListener capture = () -> {
            if (root.isAttachedToWindow() && root.getRootView() != popup.getRootView()
                    && root.getWidth() > 0 && root.getHeight() > 0 && !source.inRecording()) {
                Canvas canvas = source.beginRecording(root.getWidth(), root.getHeight());
                try { root.draw(canvas); } finally { source.endRecording(); }
                root.getLocationOnScreen(origin);
                popup.getLocationOnScreen(position);
                material.setSourceOffset(position[0] - origin[0], position[1] - origin[1]);
                if (selection != null) {
                    tabs.getLocationOnScreen(tabPosition);
                    selection.setSourceOffset(tabPosition[0] - origin[0], tabPosition[1] - origin[1]);
                }
            }
            return true;
        };
        popup.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                v.getViewTreeObserver().addOnPreDrawListener(capture);
            }
            @Override public void onViewDetachedFromWindow(View v) {
                if (v.getViewTreeObserver().isAlive()) v.getViewTreeObserver().removeOnPreDrawListener(capture);
            }
        });
    }
}
