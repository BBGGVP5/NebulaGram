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
    /** Ширина настоящей кнопки вложений — она же диаметр стеклянных кружков. */
    private int buttonSize;
    /** Видна ли кнопка вложений: под скрытой стеклянный кружок не рисуем. */
    private boolean attachmentVisible = true;
    /** Левый край кнопки вложений в координатах панели, -1 пока не измерен. */
    private int attachmentLeft = -1;
    /**
     * Правый край группы отправки в координатах панели, -1 пока не измерен.
     *
     * <p>Раньше правый кружок считался зеркально от левого. Пока справа стоял
     * микрофон, зеркало совпадало со кнопкой, но при вводе текста на его место
     * встаёт кнопка отправки другой ширины — и кружок уезжал с неё. Считаем по
     * настоящему контейнеру: он не двигается, что бы в нём ни лежало.
     */
    private int sendRight = -1;

    /**
     * Рисуется ли сейчас наша панель. Нужно снаружи: пока она активна,
     * Telegram не должен подкладывать под микрофон акцентный кружок — в нашем
     * оформлении все три поверхности однотонные, без цветных пятен.
     */
    public boolean isActive() {
        return active;
    }

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

    public void layout(View emoji, View attachment, View senderSelect, View sendContainer) {
        if (emoji == null || attachment == null || !(emoji.getParent() instanceof ViewGroup)) return;
        // Размер кружков берём у настоящей кнопки, а не из константы: при
        // зашитых 44dp поле начиналось под кнопкой, если та оказывалась шире,
        // и стеклянные поверхности наезжали друг на друга.
        if (attachment.getMeasuredWidth() > 0) {
            buttonSize = attachment.getMeasuredWidth();
        }
        // При пересылке и ответе Telegram прячет кнопку вложений; кружок под
        // ней оставался пустым стеклянным кругом.
        attachmentVisible = attachment.getVisibility() == View.VISIBLE
                && attachment.getAlpha() > 0.5f;
        // Кружок должен лечь ровно на кнопку. Границы фона размытия для этого
        // не годятся: они шире панели, и круг уезжал влево от скрепки.
        // Считаем положение кнопки в координатах самой панели.
        int offset = 0;
        for (View view = attachment; view != null && view != host; ) {
            offset += view.getLeft();
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        attachmentLeft = offset;
        sendRight = -1;
        if (sendContainer != null && sendContainer.getMeasuredWidth() > 0) {
            int right = sendContainer.getMeasuredWidth();
            for (View view = sendContainer; view != null && view != host; ) {
                right += view.getLeft();
                view = view.getParent() instanceof View ? (View) view.getParent() : null;
            }
            sendRight = right;
        }
        if (active) {
            ViewGroup parent = (ViewGroup) emoji.getParent();
            // Значок наклеек живёт внутри пилюли, у её правого края. Раньше он
            // вставал по краю своего контейнера, а тот шире пилюли на кружок
            // микрофона — значок оказывался снаружи, на стекле.
            int parentOffset = 0;
            for (View view = parent; view != null && view != host; ) {
                parentOffset += view.getLeft();
                view = view.getParent() instanceof View ? (View) view.getParent() : null;
            }
            final int pill = pillRight();
            move(emoji, pill >= 0
                    ? pill - parentOffset - AndroidUtilities.dp(6) - emoji.getMeasuredWidth()
                    : parent.getWidth() - parent.getPaddingRight()
                            - emoji.getMeasuredWidth() - AndroidUtilities.dp(6));
            move(attachment, parent.getPaddingLeft());
            // In channels Telegram places “send as” over the attachment
            // button. Keep both actions: the paperclip owns the left circle
            // and the sender avatar becomes the leading control of the input
            // pill, before the text it already reserves space for.
            if (senderSelect != null && senderSelect.getVisibility() == View.VISIBLE
                    && senderSelect.getParent() == parent) {
                move(senderSelect, parent.getPaddingLeft() + attachment.getMeasuredWidth()
                        + AndroidUtilities.dp(12));
            }
            controlsMoved = true;
        } else if (controlsMoved) {
            restoreControl(emoji);
            restoreControl(attachment);
            if (senderSelect != null && senderSelect.getParent() == emoji.getParent()) {
                restoreControl(senderSelect);
            }
            controlsMoved = false;
        }
    }

    /** Диаметр стеклянных кружков: берём у настоящей кнопки вложений. */
    private int diameter() {
        return buttonSize > 0 ? buttonSize : AndroidUtilities.dp(44);
    }

    /** Правый край пилюли ввода в координатах панели, -1 пока не измерен. */
    private int pillRight() {
        if (sendRight < 0) {
            return -1;
        }
        // Границы стекла известны с первой отрисовки: до неё считаем по краю
        // группы отправки, потом — по видимому краю панели, как и сама пилюля.
        final int right = padded.right > 0 ? Math.min(padded.right, sendRight) : sendRight;
        return right - diameter() - AndroidUtilities.dp(10);
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
        // `prepare` already excludes every transitional state.  Do not repeat
        // a dimension-based fallback here: Telegram's bubble can be shorter
        // than the 48dp touch target, and falling back to its one wide
        // drawable is precisely what joins the editor and microphone again.
        if (!active || host == null) return false;
        // В каналах и там, где писать нельзя, поле ввода скрыто, а внизу стоит
        // своя панель Telegram. Наши три поверхности рисовались и там — отсюда
        // пустые кружки вокруг надписи «Убрать звук».
        if (host.getVisibility() != View.VISIBLE || host.getAlpha() < 0.9f) return false;
        original.set(background.getBounds());
        padded.set(background.getPaddedBounds());
        if (padded.width() <= 0 || padded.height() <= 0) return false;

        // Touch targets stay at Telegram's original 48dp.  The visible glass
        // circle is allowed to be slightly smaller so it always fits the
        // actual blur path during IME and inset animations.
        int diameter = Math.min(diameter(), padded.height());
        if (diameter <= 0) return false;
        int padding = padded.left - original.left;
        // The gap is in visible coordinates. `drawSurface` expands each
        // bounds by drawable padding, then the drawable removes it again, so
        // the three paths never meet even while the keyboard is animating.
        int minimumEditorWidth = AndroidUtilities.dp(88);
        int gap = AndroidUtilities.dp(10);
        int availableForGaps = padded.width() - diameter * 2 - minimumEditorWidth;
        if (availableForGaps < gap * 2) {
            gap = Math.max(AndroidUtilities.dp(3), Math.max(0, availableForGaps / 2));
        }
        // При пересылке и ответе Telegram убирает кнопку вложений: кружок под
        // ней оставался пустым стеклянным кругом. Поле в этом случае начинается
        // от самого края и занимает освободившееся место.
        // Край панели берём по кнопке, а не по фону: между ними была разница,
        // из-за которой кружки стояли не на своих местах.
        final int edge = attachmentLeft >= 0 ? Math.max(padded.left, attachmentLeft) : padded.left;
        // Правый край — по кнопке отправки. Зеркало от левого края держалось,
        // только пока справа стоял микрофон; при вводе текста кружок съезжал.
        final int mirrored = sendRight >= 0
                ? Math.min(padded.right, sendRight)
                : padded.right - (edge - padded.left);
        int editorLeft = attachmentVisible ? edge + diameter + gap : padded.left;
        int editorRight = mirrored - diameter - gap;
        if (editorRight <= editorLeft) return false;

        // Preserve the exact upstream insets and alpha, including IME animation.
        if (attachmentVisible) {
            drawSurface(canvas, background, edge, padded.bottom - diameter,
                    edge + diameter, padded.bottom, padding);
        }
        drawSurface(canvas, background, editorLeft, padded.top, editorRight, padded.bottom, padding);
        drawSurface(canvas, background, mirrored - diameter, padded.bottom - diameter,
                mirrored, padded.bottom, padding);
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
