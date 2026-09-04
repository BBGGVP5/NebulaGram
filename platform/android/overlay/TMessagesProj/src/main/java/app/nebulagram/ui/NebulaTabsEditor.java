package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;

/**
 * Полоса вкладок, которой управляют прямо в ней.
 *
 * <p>Три переключателя в списке говорили, что вкладка включена, но не
 * показывали, как от этого изменится панель. Здесь то же действие делается по
 * самой вкладке: нажатие гасит её и панель тут же перерисовывается — видно и
 * что выключил, и что осталось.
 *
 * <p>Вкладка чатов не выключается: это единственный экран, с которого
 * начинается приложение, и панель без неё вела бы в никуда.
 */
public class NebulaTabsEditor extends View {

    /** Порядок соответствует позициям страниц: чаты, контакты, настройки, профиль. */
    private static final String[] TABS = {
            null, NebulaBottomBar.TAB_CONTACTS,
            NebulaBottomBar.TAB_SETTINGS, NebulaBottomBar.TAB_PROFILE,
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final org.telegram.messenger.ImageReceiver avatar =
            new org.telegram.messenger.ImageReceiver(this);
    private NebulaTheme theme;
    private Runnable onChanged;
    private int pressed = -1;

    public NebulaTabsEditor(@NonNull Context context) {
        super(context);
        theme = NebulaTheme.of(context);
        loadAvatar();
    }

    /** Вызывается после переключения: экран обновляет свои строки. */
    public void setOnChanged(Runnable listener) {
        onChanged = listener;
    }

    public void refresh() {
        theme = NebulaTheme.of(getContext());
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
        } catch (Throwable e) {
            // Без аватарки вкладка профиля просто нарисуется значком.
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        avatar.onAttachedToWindow();
    }

    @Override
    protected void onDetachedFromWindow() {
        avatar.onDetachedFromWindow();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), AndroidUtilities.dp(74));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean labels = NebulaBottomBar.tabLabels();
        float height = AndroidUtilities.dp(labels ? 58 : 48);
        float top = (getHeight() - height) / 2f;
        rect.set(0, top, getWidth(), top + height);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(theme.surfaceContainer());
        canvas.drawRoundRect(rect, height / 2f, height / 2f, paint);

        float step = getWidth() / (float) TABS.length;
        float iconY = labels ? top + AndroidUtilities.dp(20) : top + height / 2f;

        for (int i = 0; i < TABS.length; i++) {
            boolean on = TABS[i] == null || NebulaBottomBar.tabEnabled(TABS[i]);
            float centerX = step * i + step / 2f;

            if (i == pressed) {
                paint.setColor(NebulaTheme.stateLayer(theme.onSurface(), 0.10f));
                canvas.drawCircle(centerX, top + height / 2f, height / 2f - AndroidUtilities.dp(3), paint);
            }

            // Выключенная вкладка не исчезает, а гаснет: иначе непонятно, что
            // её можно вернуть тем же нажатием.
            int color = on ? theme.primary()
                    : NebulaTheme.stateLayer(theme.onSurfaceVariant(), 0.45f);
            paint.setColor(color);
            if (i == 3) {
                drawProfile(canvas, centerX, iconY, on);
            } else {
                glyph(canvas, i, centerX, iconY);
            }

            if (labels) {
                paint.setTextSize(AndroidUtilities.dp(9));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(on ? AndroidUtilities.bold() : null);
                paint.setColor(color);
                canvas.drawText(label(i), centerX, top + height - AndroidUtilities.dp(9), paint);
                paint.setTypeface(null);
                paint.setTextAlign(Paint.Align.LEFT);
            }
        }
    }

    private String label(int index) {
        switch (index) {
            case 1:
                return LocaleController.getString(R.string.NebulaTabContacts);
            case 2:
                return LocaleController.getString(R.string.NebulaTabSettings);
            case 3:
                return LocaleController.getString(R.string.NebulaTabProfile);
            default:
                return LocaleController.getString(R.string.NebulaTabChats);
        }
    }

    private void drawProfile(Canvas canvas, float cx, float cy, boolean on) {
        float r = AndroidUtilities.dp(11);
        avatar.setRoundRadius((int) r);
        avatar.setImageCoords(cx - r, cy - r, r * 2, r * 2);
        avatar.setAlpha(on ? 1f : 0.45f);
        avatar.draw(canvas);
    }

    /** Значки те же, что рисует превью: узнаваемая форма важнее точной копии. */
    private void glyph(Canvas canvas, int index, float cx, float cy) {
        float size = AndroidUtilities.dp(10);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(AndroidUtilities.dp(1.7f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        switch (index) {
            case 1: // контакты
                canvas.drawCircle(cx, cy - size * 0.35f, size * 0.4f, paint);
                rect.set(cx - size * 0.72f, cy + size * 0.05f, cx + size * 0.72f, cy + size * 1.15f);
                canvas.drawArc(rect, 200, 140, false, paint);
                break;
            case 2: // настройки
                canvas.drawCircle(cx, cy, size * 0.42f, paint);
                for (int i = 0; i < 6; i++) {
                    double angle = Math.PI * i / 3;
                    canvas.drawLine(
                            cx + (float) Math.cos(angle) * size * 0.58f,
                            cy + (float) Math.sin(angle) * size * 0.58f,
                            cx + (float) Math.cos(angle) * size * 0.92f,
                            cy + (float) Math.sin(angle) * size * 0.92f, paint);
                }
                break;
            default: // чаты
                rect.set(cx - size, cy - size * 0.78f, cx + size, cy + size * 0.48f);
                canvas.drawRoundRect(rect, size * 0.5f, size * 0.5f, paint);
                canvas.drawLine(cx - size * 0.3f, cy + size * 0.48f, cx - size * 0.1f, cy + size, paint);
                break;
        }
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int index = (int) (event.getX() / (getWidth() / (float) TABS.length));
        if (index < 0 || index >= TABS.length) {
            index = -1;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                pressed = index;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (index >= 0 && index == pressed) {
                    toggle(index);
                }
                pressed = -1;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = -1;
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private void toggle(int index) {
        String tab = TABS[index];
        if (tab == null) {
            // Чаты выключить нельзя — говорим об этом отдачей, а не молчанием.
            AndroidUtilities.shakeView(this);
            return;
        }
        NebulaBottomBar.setTabEnabled(tab, !NebulaBottomBar.tabEnabled(tab));
        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        if (onChanged != null) {
            onChanged.run();
        }
        invalidate();
    }
}
