package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProvider;

/** Lays out Telegram's real controls and draws their separate glass surfaces. */
public final class NebulaComposerStyle {
    private boolean active, controlsMoved;
    private ChatActivityEnterView host;
    private EditText insetEditor, measuredEditor;
    private int baseLeft, baseRight, insetLeft, insetRight;
    private int measuredLeft = -1, measuredRight = -1;
    private View attachment, replyPreview, replyClose, aiButton, expandButton, botMenu;
    private final Rect padded = new Rect();
    private final BlurredBackgroundDrawable[] surfaces = new BlurredBackgroundDrawable[6];

    public boolean isActive() { return active; }

    public void createSurfaces(BlurredBackgroundDrawableViewFactory factory, View drawingParent,
                               BlurredBackgroundColorProvider provider) {
        // Hardware canvases retain RenderNode references until frame playback.
        // Each shape needs its own node, with bounds/alpha left intact after draw().
        for (int i = 0; i < surfaces.length; i++) {
            surfaces[i] = factory.create(drawingParent, provider);
            surfaces[i].setPadding(AndroidUtilities.dp(7));
            surfaces[i].setRadius(AndroidUtilities.dp(22));
        }
    }

    public void setBotMenu(View botMenu) { this.botMenu = botMenu; }

    public void setPreview(View replyPreview, View replyClose) {
        this.replyPreview = replyPreview;
        this.replyClose = replyClose;
    }

    public void restoreInsets() {
        if (insetEditor == null) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) insetEditor.getLayoutParams();
        if (params.leftMargin == insetLeft) params.leftMargin = baseLeft;
        if (params.rightMargin == insetRight) params.rightMargin = baseRight;
        insetEditor = null;
    }

    public void prepare(ChatActivityEnterView host, EditText editor, int width, boolean supported) {
        this.host = host;
        active = supported && editor != null && NebulaAppearance.iosComposer()
                && width >= AndroidUtilities.dp(260);
        if (editor == null) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) editor.getLayoutParams();
        if (active) {
            insetEditor = editor;
            baseLeft = params.leftMargin;
            baseRight = params.rightMargin;
            params.leftMargin = insetLeft = baseLeft + AndroidUtilities.dp(10);
            // Emoji remains inside the input even when native code hides the paperclip.
            params.rightMargin = insetRight = Math.max(baseRight, AndroidUtilities.dp(50)) + AndroidUtilities.dp(6);
        }
        if (measuredEditor != editor || measuredLeft != params.leftMargin || measuredRight != params.rightMargin) {
            forceMeasure(editor);
            measuredEditor = editor;
            measuredLeft = params.leftMargin;
            measuredRight = params.rightMargin;
        }
        if (replyPreview != null) {
            ViewGroup.MarginLayoutParams preview = (ViewGroup.MarginLayoutParams) replyPreview.getLayoutParams();
            int left = active ? AndroidUtilities.dp(50) : 0;
            int right = AndroidUtilities.dp(active ? 50 : 52);
            if (preview.leftMargin != left || preview.rightMargin != right) {
                preview.leftMargin = left;
                preview.rightMargin = right;
                forceMeasure(replyPreview);
            }
        }
        if (replyClose != null) {
            ViewGroup.MarginLayoutParams close = (ViewGroup.MarginLayoutParams) replyClose.getLayoutParams();
            int closeWidth = AndroidUtilities.dp(active ? 44 : 52);
            if (close.width != closeWidth) {
                close.width = closeWidth;
                forceMeasure(replyClose);
            }
        }
    }

    private void forceMeasure(View view) {
        for (View child = view; child != host; ) {
            child.forceLayout();
            if (!(child.getParent() instanceof View)) break;
            child = (View) child.getParent();
        }
    }

    public void layout(View emoji, View attachment, View senderSelect, View aiButton, View expandButton) {
        this.attachment = attachment;
        this.aiButton = aiButton;
        this.expandButton = expandButton;
        if (emoji == null || attachment == null || !(emoji.getParent() instanceof ViewGroup)) return;
        if (attachment instanceof NebulaAttachmentButton) ((NebulaAttachmentButton) attachment).refreshStyle();
        if (active) {
            ViewGroup parent = (ViewGroup) emoji.getParent();
            // Move first. Drawing reads the resulting positions in its own coordinate space.
            move(attachment, parent.getPaddingLeft());
            move(emoji, parent.getWidth() - parent.getPaddingRight() - AndroidUtilities.dp(6) - emoji.getMeasuredWidth());
            if (senderSelect != null && senderSelect.getVisibility() == View.VISIBLE && senderSelect.getParent() == parent) {
                move(senderSelect, AndroidUtilities.dp(54));
            }
            if (botMenu != null && botMenu.getParent() == parent) move(botMenu, AndroidUtilities.dp(54));
            controlsMoved = true;
        } else if (controlsMoved) {
            restoreControl(emoji);
            restoreControl(attachment);
            if (senderSelect != null && senderSelect.getParent() == emoji.getParent()) restoreControl(senderSelect);
            if (botMenu != null && botMenu.getParent() == emoji.getParent()) restoreControl(botMenu);
            controlsMoved = false;
        }
    }

    private static void move(View view, int left) {
        view.layout(left, view.getTop(), left + view.getMeasuredWidth(), view.getBottom());
    }

    private static void restoreControl(View view) {
        ViewGroup parent = (ViewGroup) view.getParent();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        int gravity = Gravity.getAbsoluteGravity(params.gravity, parent.getLayoutDirection()) & Gravity.HORIZONTAL_GRAVITY_MASK;
        int left = parent.getPaddingLeft() + params.leftMargin;
        if (gravity == Gravity.RIGHT) left = parent.getWidth() - parent.getPaddingRight() - params.rightMargin - view.getMeasuredWidth();
        move(view, left);
    }

    public boolean draw(Canvas canvas, BlurredBackgroundDrawable background, View drawingParent) {
        if (!active || host == null || host.getVisibility() != View.VISIBLE || surfaces[0] == null) return false;
        padded.set(background.getPaddedBounds());
        if (padded.isEmpty()) return true;
        int padding = AndroidUtilities.dp(7);
        int diameter = Math.min(AndroidUtilities.dp(44), padded.height());
        int gap = AndroidUtilities.dp(6);
        int left = Math.max(padded.left, Math.round(position(host, drawingParent, true)));
        int right = Math.min(padded.right, Math.round(position(host, drawingParent, true)) + host.getWidth());
        int editorLeft = left + diameter + gap;
        int editorRight = right - diameter - gap;
        if (editorRight <= editorLeft) return true;
        for (BlurredBackgroundDrawable drawable : surfaces) drawable.setAlpha(background.getAlpha());
        if (attachment != null && attachment.getVisibility() == View.VISIBLE) {
            surface(canvas, surfaces[0], left, padded.bottom - diameter, left + diameter, padded.bottom, padding);
        }
        surface(canvas, surfaces[1], editorLeft, padded.top, editorRight, padded.bottom, padding);
        surface(canvas, surfaces[2], right - diameter, padded.bottom - diameter, right, padded.bottom, padding);
        smallButtonSurface(canvas, surfaces[3], drawingParent, replyClose, padding);
        smallButtonSurface(canvas, surfaces[4], drawingParent, aiButton, padding);
        smallButtonSurface(canvas, surfaces[5], drawingParent, expandButton, padding);
        return true;
    }

    /** Follow the native buttons' visibility, fade, scale and multiline translation. */
    private void smallButtonSurface(Canvas canvas, BlurredBackgroundDrawable background, View drawingParent, View button, int padding) {
        if (button == null) return;
        float alpha = 1f;
        View ancestor = button;
        while (ancestor != drawingParent) {
            if (ancestor.getVisibility() != View.VISIBLE) return;
            alpha *= ancestor.getAlpha();
            if (!(ancestor.getParent() instanceof View)) return;
            ancestor = (View) ancestor.getParent();
        }
        if (alpha <= 0f) return;
        int size = Math.round(AndroidUtilities.dp(32) * Math.min(button.getScaleX(), button.getScaleY()));
        if (size <= 0) return;
        int left = Math.round(position(button, drawingParent, true) + (button.getWidth() - size) / 2f);
        int top = Math.round(position(button, drawingParent, false) + (button.getHeight() - size) / 2f);
        background.setAlpha(Math.round(background.getAlpha() * alpha));
        surface(canvas, background, left, top, left + size, top + size, padding);
    }

    /** Includes the input island's IME translation and its 7dp host margin. */
    private static float position(View view, View ancestor, boolean horizontal) {
        float value = 0;
        while (view != ancestor) {
            value += horizontal ? view.getX() : view.getY();
            if (!(view.getParent() instanceof View)) break;
            View parent = (View) view.getParent();
            value -= horizontal ? parent.getScrollX() : parent.getScrollY();
            view = parent;
        }
        return value;
    }

    private void surface(Canvas canvas, BlurredBackgroundDrawable background, int left, int top, int right, int bottom, int padding) {
        background.setBounds(left - padding, top - padding, right + padding, bottom + padding);
        background.draw(canvas);
    }
}
