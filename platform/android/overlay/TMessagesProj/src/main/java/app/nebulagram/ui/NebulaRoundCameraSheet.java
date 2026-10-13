package app.nebulagram.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.IntConsumer;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

/** The pre-recording choice has its own presentation; it never opens a camera itself. */
public final class NebulaRoundCameraSheet {
    private NebulaRoundCameraSheet() { }
    private static int dp(float value) { return AndroidUtilities.dp(value); }
    private static int alpha(int color, float value) {
        return (color & 0x00ffffff) | (Math.round(255 * value) << 24);
    }
    private static GradientDrawable shape(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color); drawable.setCornerRadius(dp(radius)); return drawable;
    }
    public static BottomSheet create(Context c, Theme.ResourcesProvider provider, boolean front,
                                     IntConsumer selected) {
        BottomSheet sheet = new BottomSheet.Builder(c, true, provider).create();
        sheet.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        sheet.setApplyTopPadding(false); sheet.setApplyBottomPadding(false);
        sheet.setCanDismissWithSwipe(true);
        sheet.setCustomView(content(c, provider, front, index -> {
            // The controller queues cancellation on dismissal. Complete the choice
            // in the same UI turn before that queued cancellation can run.
            sheet.dismiss(); selected.accept(index);
        }, sheet::dismiss));
        return sheet;
    }
    /** Also used by the credential-free UI fixture to render these exact views. */
    public static ScrollView content(Context c, Theme.ResourcesProvider provider, boolean front,
                                     IntConsumer selected, Runnable close) {
        int surface = Theme.getColor(Theme.key_dialogBackground, provider) | 0xff000000;
        int foreground = Theme.getColor(Theme.key_dialogTextBlack, provider);
        int muted = Theme.getColor(Theme.key_dialogTextGray, provider);
        int accent = Theme.getColor(Theme.key_dialogTextBlue, provider);
        ScrollView scroll = new ScrollView(c);
        scroll.setFillViewport(false); scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(shape(surface, 28)); scroll.setClipToOutline(true);
        LinearLayout body = new LinearLayout(c); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(10), dp(20), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        View handle = new View(c); handle.setBackground(shape(alpha(muted, .4f), 2));
        handle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams handleBounds = new LinearLayout.LayoutParams(dp(32), dp(4));
        handleBounds.gravity = Gravity.CENTER_HORIZONTAL; handleBounds.bottomMargin = dp(14);
        body.addView(handle, handleBounds);
        LinearLayout heading = new LinearLayout(c); heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(c, NebulaText.text("Камера кружка", "Round video camera"), 22, foreground);
        title.setTypeface(AndroidUtilities.bold()); title.setMaxLines(2);
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView dismiss = label(c, "×", 28, foreground); dismiss.setGravity(Gravity.CENTER);
        dismiss.setContentDescription(NebulaText.text("Закрыть", "Close")); dismiss.setFocusable(true);
        dismiss.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(accent, .12f)), null, shape(0xffffffff, 24)));
        dismiss.setOnClickListener(v -> close.run());
        heading.addView(dismiss, new LinearLayout.LayoutParams(dp(48), dp(48)));
        body.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        TextView subtitle = label(c, NebulaText.text("С какой стороны начать запись?", "Which camera should start recording?"), 14, muted);
        subtitle.setPadding(0, dp(4), dp(12), dp(18));
        body.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
        final boolean[] completed = {false};
        for (int index = 0; index < 2; index++) {
            final int facing = index;
            boolean checked = front == (index == 0);
            String name = NebulaText.text(index == 0 ? "Фронтальная" : "Основная", index == 0 ? "Front camera" : "Rear camera");
            String detail = NebulaText.text(index == 0 ? "Снимайте себя" : "Снимайте то, что перед вами", index == 0 ? "Record yourself" : "Record what's in front of you");
            LinearLayout row = new LinearLayout(c) {
                @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(info);
                    info.setClassName("android.widget.RadioButton"); info.setCheckable(true); info.setChecked(isSelected());
                }
            };
            row.setGravity(Gravity.CENTER_VERTICAL); row.setMinimumHeight(dp(88));
            row.setPaddingRelative(dp(14), dp(14), dp(14), dp(14));
            row.setSelected(checked); row.setFocusable(true);
            row.setContentDescription(name + ". " + detail);
            row.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(accent, .12f)),
                    shape(checked ? alpha(accent, .15f) : android.graphics.Color.TRANSPARENT, 20), shape(0xffffffff, 20)));
            row.addView(new CameraDrawing(c, index == 0, checked ? accent : foreground), new LinearLayout.LayoutParams(dp(48), dp(52)));
            LinearLayout labels = new LinearLayout(c); labels.setOrientation(LinearLayout.VERTICAL);
            labels.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            TextView nameLabel = label(c, name, 17, checked ? accent : foreground);
            nameLabel.setTypeface(AndroidUtilities.bold()); labels.addView(nameLabel);
            TextView detailLabel = label(c, detail, 13, muted); detailLabel.setPadding(0, dp(5), 0, 0); labels.addView(detailLabel);
            LinearLayout.LayoutParams labelBounds = new LinearLayout.LayoutParams(0, -2, 1); labelBounds.setMarginStart(dp(14));
            row.addView(labels, labelBounds);
            row.setOnClickListener(v -> {
                if (completed[0]) return;
                completed[0] = true; selected.accept(facing);
            });
            LinearLayout.LayoutParams rowBounds = new LinearLayout.LayoutParams(-1, -2);
            if (index > 0) rowBounds.topMargin = dp(8);
            body.addView(row, rowBounds);
        }
        return scroll;
    }
    private static TextView label(Context c, String value, int size, int color) {
        TextView label = new TextView(c); label.setText(value); label.setTextSize(size);
        label.setTextColor(color); label.setIncludeFontPadding(false); label.setGravity(Gravity.START);
        label.setLineSpacing(dp(2), 1f); return label;
    }
    private static final class CameraDrawing extends View {
        private final boolean front; private final int color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        CameraDrawing(Context c, boolean front, int color) {
            super(c); this.front = front; this.color = color;
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); canvas.save();
            float scale = Math.min(getWidth() / 48f, getHeight() / 52f);
            canvas.translate((getWidth() - 48 * scale) / 2, (getHeight() - 52 * scale) / 2); canvas.scale(scale, scale);
            paint.setColor(color); paint.setStrokeWidth(1.8f);
            rect.set(10, 3, 38, 49); canvas.drawRoundRect(rect, 7, 7, paint);
            if (front) {
                canvas.drawLine(20, 8, 28, 8, paint); canvas.drawCircle(24, 23, 4.5f, paint);
                rect.set(17, 30, 31, 40); canvas.drawArc(rect, 180, 180, false, paint);
            } else {
                rect.set(14, 7, 28, 27); canvas.drawRoundRect(rect, 5, 5, paint);
                canvas.drawCircle(21, 13, 2.8f, paint); canvas.drawCircle(21, 21, 2.8f, paint);
            }
            canvas.restore();
        }
    }
}
