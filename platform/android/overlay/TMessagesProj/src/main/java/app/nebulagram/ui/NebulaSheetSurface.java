package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
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

/** One backdrop per dialog, sampled only from the separate originating window. */
@android.annotation.TargetApi(31)
public final class NebulaSheetSurface implements View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener {
    private final View host, root;
    private final BlurredBackgroundSourceRenderNode source;
    private final BlurredBackgroundDrawable[] materials = new BlurredBackgroundDrawable[3];
    private final BlurredBackgroundDrawableViewFactory factory;
    private final Theme.ResourcesProvider provider;
    private static final class Sections {
        final java.util.ArrayList<BlurredBackgroundDrawable> drawables = new java.util.ArrayList<>();
        int used;
        boolean ready;
        int x, y;
    }
    private final java.util.WeakHashMap<View, Sections> sections = new java.util.WeakHashMap<>();
    private final int[] sectionPosition = new int[2];
    private final int[] origin = new int[2], position = new int[2];
    private boolean ready;

    public static NebulaSheetSurface create(View host, View anchor, Theme.ResourcesProvider provider) {
        if (anchor == null || !NebulaMenuStyle.enabled() || Build.VERSION.SDK_INT < 31
                || !LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR)) return null;
        return new NebulaSheetSurface(host, anchor.getRootView(), provider);
    }

    private NebulaSheetSurface(View host, View root, Theme.ResourcesProvider provider) {
        this.host = host;
        this.root = root;
        this.provider = provider;
        BlurredBackgroundSourceColor fallback = new BlurredBackgroundSourceColor();
        fallback.setColor(NebulaMenuStyle.surface(provider));
        source = new BlurredBackgroundSourceRenderNode(fallback);
        source.setBlur(AndroidUtilities.dpf2(NebulaGlass.blur()));
        factory = new BlurredBackgroundDrawableViewFactory(source);
        factory.setLiquidGlassEffectAllowed(NebulaMenuStyle.animated());
        for (int i = 0; i < materials.length; i++) {
            materials[i] = factory.create(host).setColorProvider(NebulaMenuStyle.provider(provider));
            materials[i].setRadius(AndroidUtilities.dp(24));
            materials[i].setThickness(AndroidUtilities.dp(4));
            materials[i].setIntensity(NebulaGlass.refraction());
        }
        host.addOnAttachStateChangeListener(this);
        if (host.isAttachedToWindow()) onViewAttachedToWindow(host);
    }

    public boolean isReady() { return ready && NebulaMenuStyle.enabled(); }

    @Override public boolean onPreDraw() {
        final boolean wasReady = ready;
        ready = false;
        for (Sections bucket : sections.values()) bucket.used = 0;
        // Never sample the sheet itself, including when hosted in a same-window bubble.
        if (!root.isAttachedToWindow() || host.getRootView() == root || root.getWidth() <= 0
                || root.getHeight() <= 0 || source.inRecording()) {
            updateSectionViews(wasReady);
            return true;
        }
        Canvas capture = source.beginRecording(root.getWidth(), root.getHeight());
        try { root.draw(capture); } finally { source.endRecording(); }
        root.getLocationOnScreen(origin);
        host.getLocationOnScreen(position);
        for (BlurredBackgroundDrawable material : materials)
            material.setSourceOffset(position[0] - origin[0], position[1] - origin[1]);
        ready = true;
        updateSectionViews(wasReady);
        return true;
    }

    /** Track fallback draws too: a cached opaque section must redraw when capture becomes ready. */
    public void trackSection(View view) {
        if (!sections.containsKey(view)) sections.put(view, new Sections());
    }

    private void updateSectionViews(boolean wasReady) {
        if (wasReady != ready) host.invalidate();
        for (java.util.Map.Entry<View, Sections> entry : sections.entrySet()) {
            View view = entry.getKey();
            if (view == null || !view.isAttachedToWindow()) continue;
            Sections bucket = entry.getValue();
            view.getLocationOnScreen(sectionPosition);
            int x = sectionPosition[0] - origin[0], y = sectionPosition[1] - origin[1];
            boolean changed = bucket.ready != ready || ready && (bucket.x != x || bucket.y != y);
            bucket.ready = ready;
            bucket.x = x;
            bucket.y = y;
            // No unconditional invalidation: idle sheets must not schedule a redraw loop.
            if (changed) view.invalidate();
        }
    }

    public boolean draw(Canvas canvas, Rect bounds, int insetX, int insetTop, int alpha, int slot) {
        if (!isReady() || !canvas.isHardwareAccelerated()) return false;
        BlurredBackgroundDrawable material = materials[slot];
        material.setBounds(bounds.left + insetX, bounds.top + insetTop, bounds.right - insetX, bounds.bottom);
        material.setAlpha(alpha);
        material.draw(canvas);
        return true;
    }

    public boolean drawSection(View view, Canvas canvas, RectF bounds, float topRadius, float bottomRadius, float alpha) {
        trackSection(view);
        if (!isReady() || !canvas.isHardwareAccelerated() || bounds.isEmpty()) return false;
        Sections bucket = sections.get(view);
        // Draw slots are reset once per window frame, not between blur and onscreen passes.
        if (bucket.used == bucket.drawables.size()) {
            BlurredBackgroundDrawable drawable = factory.create().setColorProvider(NebulaMenuStyle.provider(provider));
            drawable.setThickness(AndroidUtilities.dp(4));
            drawable.setIntensity(NebulaGlass.refraction());
            bucket.drawables.add(drawable);
        }
        BlurredBackgroundDrawable drawable = bucket.drawables.get(bucket.used++);
        view.getLocationOnScreen(sectionPosition);
        drawable.setSourceOffset(sectionPosition[0] - origin[0], sectionPosition[1] - origin[1]);
        drawable.setRadius(topRadius, topRadius, bottomRadius, bottomRadius);
        drawable.setBounds(Math.round(bounds.left), Math.round(bounds.top), Math.round(bounds.right), Math.round(bounds.bottom));
        drawable.setAlpha(Math.round(255 * Math.max(0f, Math.min(1f, alpha))));
        drawable.draw(canvas);
        return true;
    }

    @Override public void onViewAttachedToWindow(View view) {
        view.getViewTreeObserver().removeOnPreDrawListener(this);
        view.getViewTreeObserver().addOnPreDrawListener(this);
    }
    @Override public void onViewDetachedFromWindow(View view) {
        ready = false;
        sections.clear();
        if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(this);
    }
}
