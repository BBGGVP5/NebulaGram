package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;

/** Compact bottom-tab organizer: tap to toggle, hold and drag to reorder. */
public class NebulaTabsEditor extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final org.telegram.messenger.ImageReceiver avatar =
            new org.telegram.messenger.ImageReceiver(this);
    private final int touchSlop;
    private NebulaTheme theme;
    private Runnable onChanged;
    private String[] order = NebulaBottomBar.tabOrder();
    private int pressed = -1;
    private int dragIndex = -1;
    private boolean dragging;
    private boolean tapCancelled;
    private float downX;
    private float downY;

    private final Runnable startDrag = () -> {
        if (pressed < 0) {
            return;
        }
        dragging = true;
        dragIndex = pressed;
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        invalidate();
    };

    public NebulaTabsEditor(@NonNull Context context) {
        super(context);
        theme = NebulaTheme.of(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
        loadAvatar();
    }

    public void setOnChanged(Runnable listener) {
        onChanged = listener;
    }

    public void refresh() {
        theme = NebulaTheme.of(getContext());
        order = NebulaBottomBar.tabOrder();
        invalidate();
    }

    private void loadAvatar() {
        try {
            int account = UserConfig.selectedAccount;
            TLRPC.User self = MessagesController.getInstance(account)
                    .getUser(UserConfig.getInstance(account).clientUserId);
            if (self != null) {
                avatar.setForUserOrChat(self, new AvatarDrawable(self));
            }
        } catch (Throwable ignore) {
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatar.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(startDrag);
        avatar.onDetachedFromWindow();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), AndroidUtilities.dp(88));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean labels = NebulaBottomBar.tabLabels();
        float left = AndroidUtilities.dp(14);
        float right = getWidth() - left;
        float height = AndroidUtilities.dp(labels ? 62 : 54);
        float top = (getHeight() - height) / 2f;
        rect.set(left, top, right, top + height);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(NebulaTheme.stateLayer(theme.onSurface(), 0.035f));
        canvas.drawRoundRect(rect, height / 2f, height / 2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1));
        paint.setColor(NebulaTheme.stateLayer(theme.onSurfaceVariant(), 0.22f));
        canvas.drawRoundRect(rect, height / 2f, height / 2f, paint);
        paint.setStyle(Paint.Style.FILL);

        float step = (right - left) / order.length;
        float iconY = labels ? top + AndroidUtilities.dp(23) : top + height / 2f;
        for (int i = 0; i < order.length; i++) {
            String tab = order[i];
            boolean on = NebulaBottomBar.TAB_CHATS.equals(tab) || NebulaBottomBar.tabEnabled(tab);
            float centerX = left + step * i + step / 2f;

            if (i == pressed || i == dragIndex) {
                paint.setColor(NebulaTheme.stateLayer(theme.primary(), dragging ? 0.18f : 0.10f));
                canvas.drawCircle(centerX, top + height / 2f,
                        height / 2f - AndroidUtilities.dp(4), paint);
            }

            int color = on ? theme.primary()
                    : NebulaTheme.stateLayer(theme.onSurfaceVariant(), 0.42f);
            paint.setColor(color);
            if (NebulaBottomBar.TAB_PROFILE.equals(tab)) {
                drawProfile(canvas, centerX, iconY, on);
            } else {
                glyph(canvas, tab, centerX, iconY);
            }

            if (labels) {
                paint.setTextSize(AndroidUtilities.dp(9.5f));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(on ? AndroidUtilities.bold() : null);
                paint.setColor(color);
                canvas.drawText(label(tab), centerX,
                        top + height - AndroidUtilities.dp(9), paint);
                paint.setTypeface(null);
                paint.setTextAlign(Paint.Align.LEFT);
            }
        }
    }

    private String label(String tab) {
        if (NebulaBottomBar.TAB_CONTACTS.equals(tab)) {
            return LocaleController.getString(R.string.NebulaTabContacts);
        } else if (NebulaBottomBar.TAB_SETTINGS.equals(tab)) {
            return LocaleController.getString(R.string.NebulaTabSettings);
        } else if (NebulaBottomBar.TAB_PROFILE.equals(tab)) {
            return LocaleController.getString(R.string.NebulaTabProfile);
        }
        return LocaleController.getString(R.string.NebulaTabChats);
    }

    private void drawProfile(Canvas canvas, float cx, float cy, boolean on) {
        float r = AndroidUtilities.dp(11);
        avatar.setRoundRadius((int) r);
        avatar.setImageCoords(cx - r, cy - r, r * 2, r * 2);
        avatar.setAlpha(on ? 1f : 0.42f);
        avatar.draw(canvas);
    }

    private void glyph(Canvas canvas, String tab, float cx, float cy) {
        float size = AndroidUtilities.dp(10);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.7f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        if (NebulaBottomBar.TAB_CONTACTS.equals(tab)) {
            canvas.drawCircle(cx, cy - size * 0.35f, size * 0.4f, paint);
            rect.set(cx - size * 0.72f, cy + size * 0.05f,
                    cx + size * 0.72f, cy + size * 1.15f);
            canvas.drawArc(rect, 200, 140, false, paint);
        } else if (NebulaBottomBar.TAB_SETTINGS.equals(tab)) {
            canvas.drawCircle(cx, cy, size * 0.42f, paint);
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI * i / 3;
                canvas.drawLine(
                        cx + (float) Math.cos(angle) * size * 0.58f,
                        cy + (float) Math.sin(angle) * size * 0.58f,
                        cx + (float) Math.cos(angle) * size * 0.92f,
                        cy + (float) Math.sin(angle) * size * 0.92f, paint);
            }
        } else {
            rect.set(cx - size, cy - size * 0.78f,
                    cx + size, cy + size * 0.48f);
            canvas.drawRoundRect(rect, size * 0.5f, size * 0.5f, paint);
            canvas.drawLine(cx - size * 0.3f, cy + size * 0.48f,
                    cx - size * 0.1f, cy + size, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private int indexAt(float x) {
        float left = AndroidUtilities.dp(14);
        float width = getWidth() - left * 2f;
        int index = (int) ((x - left) / (width / order.length));
        return index >= 0 && index < order.length ? index : -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int index = indexAt(event.getX());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                pressed = index;
                dragIndex = -1;
                dragging = false;
                tapCancelled = false;
                if (pressed >= 0) {
                    postDelayed(startDrag, 360);
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && !tapCancelled && (Math.abs(event.getX() - downX) > touchSlop
                        || Math.abs(event.getY() - downY) > touchSlop)) {
                    removeCallbacks(startDrag);
                    tapCancelled = true;
                    pressed = -1;
                }
                if (dragging && index >= 0 && index != dragIndex) {
                    NebulaBottomBar.moveTab(dragIndex, index);
                    order = NebulaBottomBar.tabOrder();
                    dragIndex = index;
                    pressed = index;
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    if (onChanged != null) onChanged.run();
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(startDrag);
                if (!dragging && !tapCancelled && index >= 0 && index == pressed) {
                    toggle(index);
                }
                finishGesture();
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(startDrag);
                finishGesture();
                return true;
            default:
                return true;
        }
    }

    private void finishGesture() {
        if (dragging && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(false);
        }
        pressed = -1;
        dragIndex = -1;
        dragging = false;
        tapCancelled = false;
        invalidate();
    }

    private void toggle(int index) {
        String tab = order[index];
        if (NebulaBottomBar.TAB_CHATS.equals(tab)) {
            AndroidUtilities.shakeView(this);
            return;
        }
        NebulaBottomBar.setTabEnabled(tab, !NebulaBottomBar.tabEnabled(tab));
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        if (onChanged != null) onChanged.run();
        invalidate();
    }
}
