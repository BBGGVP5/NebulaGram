package app.nebulagram.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import java.util.function.IntConsumer;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/** A modal card above the actual composer. Native window dimming blocks the chat. */
public final class NebulaRoundCameraPanelHost extends Dialog {
    private final View anchor;
    private final FrameLayout root;
    private final ScrollView card;
    private final int[] inputPosition = new int[2], rootPosition = new int[2];
    private final ViewTreeObserver.OnPreDrawListener position = this::positionCard;
    private boolean completed;

    public NebulaRoundCameraPanelHost(Context context, View anchor, Theme.ResourcesProvider provider,
                                      boolean front, IntConsumer selected) {
        super(context);
        this.anchor = anchor;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        root = new FrameLayout(context); root.setOnClickListener(v -> dismiss());
        card = NebulaRoundCameraSheet.content(context, provider, front, index -> {
            if (completed) return;
            completed = true;
            dismiss(); selected.accept(index);
        }, this::dismiss);
        card.setOnClickListener(v -> { });
        FrameLayout.LayoutParams bounds = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bounds.leftMargin = bounds.rightMargin = dp(12);
        root.addView(card, bounds); setContentView(root);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = .55f; attributes.gravity = Gravity.TOP;
            window.setAttributes(attributes);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        }
    }
    @Override protected void onStart() {
        super.onStart();
        if (getWindow() != null) getWindow().setLayout(-1, -1);
        root.getViewTreeObserver().addOnPreDrawListener(position);
        // Changes in the underlying chat do not invalidate this modal window.
        // Observe that window too so keyboard/composer motion repositions the card.
        anchor.getViewTreeObserver().addOnPreDrawListener(position);
        positionCard();
        if (!NebulaGlass.reduced()) {
            card.setAlpha(0); card.setTranslationY(dp(8));
            card.animate().alpha(1).translationY(0).setDuration(180).start();
        }
    }
    private boolean positionCard() {
        if (anchor.getWindowToken() == null || !anchor.isShown()) { dismiss(); return true; }
        if (root.getHeight() == 0) return true;
        anchor.getLocationOnScreen(inputPosition); root.getLocationOnScreen(rootPosition);
        int top = Math.max(dp(12), Math.min(root.getHeight(), inputPosition[1] - rootPosition[1] - dp(8)));
        int available = Math.max(0, top - dp(12));
        card.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, root.getWidth() - dp(24)), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST));
        FrameLayout.LayoutParams bounds = (FrameLayout.LayoutParams) card.getLayoutParams();
        int bottom = root.getHeight() - top;
        if (bounds.bottomMargin != bottom || bounds.height != card.getMeasuredHeight()) {
            bounds.bottomMargin = bottom; bounds.height = card.getMeasuredHeight(); card.setLayoutParams(bounds);
        }
        return true;
    }
    @Override protected void onStop() {
        if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnPreDrawListener(position);
        if (anchor.getViewTreeObserver().isAlive()) anchor.getViewTreeObserver().removeOnPreDrawListener(position);
        card.animate().cancel(); super.onStop();
    }
    private static int dp(float value) { return AndroidUtilities.dp(value); }
}
