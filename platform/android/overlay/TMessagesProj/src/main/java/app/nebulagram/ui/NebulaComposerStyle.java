package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.ChatActivityEnterView;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;

/**
 * Repositions Telegram's original attachment and emoji controls, then renders
 * their three surfaces using its existing blur source. No replacement inputs,
 * gesture listeners, screenshots, or background blur passes are introduced.
 */
public final class NebulaComposerStyle {
    private boolean active;
    private boolean controlsMoved;
    private EditText insetEditor;
    private EditText measuredEditor;
    private int measuredLeft = -1, measuredRight = -1;
    private int baseLeft, baseRight, insetLeft, insetRight;
    private final Rect original = new Rect();
    private final Rect padded = new Rect();
    private final Rect working = new Rect();
    private ChatActivityEnterView host;

    public void restoreInsets() {
        if (insetEditor == null) return;
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) insetEditor.getLayoutParams();
        // Native code may change these when a bot/send-as control appears.
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
            params.rightMargin = insetRight = baseRight + AndroidUtilities.dp(10);
        }
        if (measuredEditor != editor || measuredLeft != params.leftMargin || measuredRight != params.rightMargin) {
            // A parent-only requestLayout can leave unchanged nested measure
            // specs cached. Dirty only this chain, only when final insets change.
            for (View child = editor; child != host; ) {
                child.forceLayout();
                if (!(child.getParent() instanceof View)) break;
                child = (View) child.getParent();
            }
            measuredEditor = editor;
            measuredLeft = params.leftMargin;
            measuredRight = params.rightMargin;
        }
    }

    public void layout(View emoji, View attachment) {
        if (emoji == null || attachment == null || !(emoji.getParent() instanceof ViewGroup)) return;
        if (active) {
            ViewGroup parent = (ViewGroup) emoji.getParent();
            // Derive from stable parent geometry, never from our previous layout.
            move(emoji, parent.getWidth() - parent.getPaddingRight()
                    - emoji.getMeasuredWidth() - AndroidUtilities.dp(6));
            move(attachment, parent.getPaddingLeft());
            controlsMoved = true;
        } else if (controlsMoved) {
            restoreControl(emoji);
            restoreControl(attachment);
            controlsMoved = false;
        }
    }

    private static void move(View view, int left) {
        view.layout(left, view.getTop(), left + view.getMeasuredWidth(), view.getBottom());
    }

    private static void restoreControl(View view) {
        ViewGroup parent = (ViewGroup) view.getParent();
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        int gravity = Gravity.getAbsoluteGravity(params.gravity, parent.getLayoutDirection())
                & Gravity.HORIZONTAL_GRAVITY_MASK;
        int left = parent.getPaddingLeft() + params.leftMargin;
        if (gravity == Gravity.RIGHT) {
            left = parent.getWidth() - parent.getPaddingRight() - params.rightMargin - view.getMeasuredWidth();
        }
        move(view, left);
    }

    public boolean draw(Canvas canvas, BlurredBackgroundDrawable background) {
        if (!active || host == null || host.isRecordingAudioVideo() || host.isEditingMessage()) return false;
        original.set(background.getBounds());
        padded.set(background.getPaddedBounds());
        int diameter = AndroidUtilities.dp(44);
        // Шесть точек между кружком и полем на тёмных обоях сливались в одну
        // фигуру: у всех трёх поверхностей одинаковый фон, и узкая щель между
        // ними просто не читается. Десять — минимум, при котором видно, что
        // кнопок три, а не одна широкая панель.
        int gap = AndroidUtilities.dp(10);
        int padding = padded.left - original.left;
        if (padded.width() < diameter * 2 + gap * 2 + AndroidUtilities.dp(100)
                || padded.height() < diameter - AndroidUtilities.dp(2)) return false;

        // Preserve the exact upstream insets and alpha, including IME animation.
        drawSurface(canvas, background, padded.left, padded.bottom - diameter,
                padded.left + diameter, padded.bottom, padding);
        drawSurface(canvas, background, padded.left + diameter + gap, padded.top,
                padded.right - diameter - gap, padded.bottom, padding);
        drawSurface(canvas, background, padded.right - diameter, padded.bottom - diameter,
                padded.right, padded.bottom, padding);
        background.setBounds(original);
        return true;
    }

    private void drawSurface(Canvas canvas, BlurredBackgroundDrawable background,
                             int left, int top, int right, int bottom, int padding) {
        working.set(left - padding, top - padding, right + padding, bottom + padding);
        background.setBounds(working);
        background.draw(canvas);
    }
}
