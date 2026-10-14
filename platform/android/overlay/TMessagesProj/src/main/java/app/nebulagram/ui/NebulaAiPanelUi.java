package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;

/** Scoped presentation for AI and audio tools; native requests remain in their controllers. */
public final class NebulaAiPanelUi {
    public static final int VOICE = 0, CHAT = 1, SPEECH = 2, CAMERA = 3;
    private NebulaAiPanelUi() { }
    public static int dp(float value) { return AndroidUtilities.dp(value); }
    private static int tint(int color, float alpha) {
        return (color & 0x00ffffff) | (Math.round(255 * alpha) << 24);
    }
    public static GradientDrawable shape(int color, float radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    public static LinearLayout column(Context context, boolean compact) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(compact ? 14 : 20), dp(compact ? 12 : 18), dp(compact ? 14 : 20), dp(compact ? 12 : 20));
        column.setBackground(shape(NebulaTheme.of(context).modalSurface(), compact ? 24 : 28));
        return column;
    }
    public static LinearLayout header(Context c, int kind, String title, String subtitle, Runnable close) {
        NebulaTheme theme = NebulaTheme.of(c);
        LinearLayout row = new LinearLayout(c); row.setGravity(Gravity.CENTER_VERTICAL);
        Glyph glyph = new Glyph(c, kind);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout labels = new LinearLayout(c); labels.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(c); heading.setText(title); heading.setTextSize(20);
        heading.setTextColor(theme.onSurface()); heading.setTypeface(AndroidUtilities.bold());
        heading.setIncludeFontPadding(false); heading.setMaxLines(2);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        labels.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView detail = caption(c, subtitle); detail.setTextSize(12); detail.setPadding(0, dp(5), 0, 0);
            labels.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1); lp.leftMargin = dp(12);
        row.addView(labels, lp);
        if (close != null) {
            TextView dismiss = button(c, "×", false, v -> close.run());
            dismiss.setTextSize(26); dismiss.setPadding(0, 0, 0, 0);
            dismiss.setContentDescription(NebulaText.text("Закрыть", "Close"));
            row.addView(dismiss, new LinearLayout.LayoutParams(dp(44), dp(44)));
        }
        return row;
    }
    public static TextView caption(Context c, String value) {
        TextView text = new TextView(c); text.setText(value); text.setTextSize(13);
        text.setTextColor(NebulaTheme.of(c).onSurfaceVariant()); text.setIncludeFontPadding(false);
        text.setLineSpacing(dp(2), 1f); return text;
    }
    public static TextView provider(Context c, String value, Runnable click) {
        TextView text = button(c, value, false, v -> click.run());
        text.setTextSize(13); text.setTypeface(AndroidUtilities.bold());
        text.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        text.setMinHeight(dp(38)); text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setContentDescription(NebulaText.text("Сервис: ", "Service: ") + value);
        return text;
    }
    public static TextView button(Context c, String value, boolean primary, View.OnClickListener click) {
        TextView text = new TextView(c); styleButton(text, primary);
        text.setText(value); text.setOnClickListener(click); return text;
    }
    public static void styleButton(TextView button, boolean primary) {
        NebulaTheme theme = NebulaTheme.of(button.getContext());
        int fill = primary ? theme.primary() : tint(theme.primary(), theme.isDark() ? .13f : .08f);
        button.setTextSize(15); button.setTypeface(AndroidUtilities.bold());
        button.setIncludeFontPadding(false); button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), dp(10), dp(14), dp(10)); button.setMinHeight(dp(primary ? 50 : 44));
        button.setTextColor(primary ? theme.onPrimary() : theme.primary());
        button.setFocusable(true);
        button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info); info.setClassName("android.widget.Button");
            }
        });
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(tint(primary ? theme.onPrimary() : theme.primary(), .15f)),
                shape(fill, primary ? 25 : 18), shape(0xffffffff, primary ? 25 : 18)));
    }
    public static void enabled(View view, boolean enabled) {
        view.setEnabled(enabled); view.setAlpha(enabled ? 1f : .42f);
    }
    public static EditText editor(Context c, String hint, int minimumLines) {
        EditText editor = NebulaFormUi.field(c, hint, minimumLines, 50000);
        editor.setSingleLine(false); editor.setMinLines(minimumLines); editor.setMaxLines(6);
        editor.setIncludeFontPadding(false); editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setTextSize(17); editor.setLineSpacing(dp(3), 1f);
        editor.setPadding(dp(16), dp(16), dp(16), dp(16));
        editor.setBackground(shape(NebulaTheme.of(c).surfaceContainer(), 20)); return editor;
    }
    public static FrameLayout card(Context c, View child) {
        FrameLayout card = new FrameLayout(c); card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(shape(NebulaTheme.of(c).surfaceContainer(), 20));
        card.addView(child, new FrameLayout.LayoutParams(-1, -2)); return card;
    }
    public static ScrollView scroll(Context c, View content) {
        ScrollView scroll = new ScrollView(c); scroll.setFillViewport(false);
        scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2)); return scroll;
    }
    public static void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(top); parent.addView(child, lp);
    }
    public static LinearLayout actions(Context c, View... actions) {
        LinearLayout row = new LinearLayout(c); row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < actions.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) lp.leftMargin = dp(8); row.addView(actions[i], lp);
        }
        return row;
    }
    /** Small deliberate line artwork: speech, Nebula sparkle and voice waves. */
    public static final class Glyph extends View {
        private final int kind; private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path(); private final RectF rect = new RectF();
        private boolean busy; private ValueAnimator animator; private float phase;
        public Glyph(Context c, int kind) {
            super(c); this.kind = kind; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
        }
        public void setBusy(boolean value) { busy = value; updateAnimation(); invalidate(); }
        private void updateAnimation() {
            boolean run = busy && isAttachedToWindow() && isShown() && getWindowVisibility() == VISIBLE
                    && !NebulaGlass.reduced() && (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled());
            if (run && animator == null) {
                animator = ValueAnimator.ofFloat(0, 1); animator.setDuration(1500);
                animator.setRepeatCount(ValueAnimator.INFINITE); animator.setInterpolator(new LinearInterpolator());
                animator.addUpdateListener(a -> { phase = (float) a.getAnimatedValue(); invalidate(); }); animator.start();
            } else if (!run && animator != null) { animator.cancel(); animator = null; phase = 0; }
        }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation(); }
        @Override protected void onDetachedFromWindow() { if (animator != null) { animator.cancel(); animator = null; } super.onDetachedFromWindow(); }
        @Override protected void onWindowVisibilityChanged(int visibility) { super.onWindowVisibilityChanged(visibility); updateAnimation(); }
        @Override protected void onVisibilityChanged(View changed, int visibility) { super.onVisibilityChanged(changed, visibility); updateAnimation(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); NebulaTheme theme = NebulaTheme.of(getContext());
            float size = Math.min(getWidth(), getHeight()); if (size <= 0) return;
            canvas.save(); canvas.translate(getWidth()/2f, getHeight()/2f); canvas.scale(size/64f, size/64f);
            paint.setStyle(Paint.Style.FILL); paint.setColor(tint(theme.primary(), theme.isDark() ? .13f : .09f));
            rect.set(-31, -31, 31, 31); canvas.drawRoundRect(rect, 20, 20, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.1f); paint.setColor(theme.primary());
            if (kind == CAMERA) {
                rect.set(-18, -11, 18, 15); canvas.drawRoundRect(rect, 7, 7, paint);
                path.reset(); path.moveTo(-10, -11); path.lineTo(-6, -17); path.lineTo(6, -17); path.lineTo(10, -11); canvas.drawPath(path, paint);
                canvas.drawCircle(0, 2, 8, paint); canvas.drawCircle(12, -5, 1, paint);
            } else if (kind == CHAT) {
                path.reset(); path.moveTo(-17,-13); path.cubicTo(-17,-18,16,-18,17,-13);
                path.lineTo(17,7); path.quadTo(17,12,12,12); path.lineTo(-6,12);
                path.lineTo(-16,19); path.lineTo(-13,11); path.quadTo(-18,10,-17,5); path.close(); canvas.drawPath(path,paint);
                canvas.drawLine(-9,-4,8,-4,paint); canvas.drawLine(-9,3,3,3,paint);
            } else if (kind == SPEECH) {
                path.reset(); path.moveTo(-16,-5); path.lineTo(-9,-5); path.lineTo(0,-13); path.lineTo(0,13);
                path.lineTo(-9,5); path.lineTo(-16,5); path.close(); canvas.drawPath(path,paint);
                rect.set(-2,-11,18,11); canvas.drawArc(rect,-65,130,false,paint);
                rect.set(-3,-17,28,17); canvas.drawArc(rect,-55,110,false,paint);
            } else {
                rect.set(-6,-17,6,4); canvas.drawRoundRect(rect,6,6,paint);
                path.reset(); path.moveTo(-12,-3); path.cubicTo(-13,15,13,15,12,-3); canvas.drawPath(path,paint);
                canvas.drawLine(0,12,0,20,paint); canvas.drawLine(-6,20,6,20,paint);
                for (int i=0;i<3;i++) {
                    float height = 3+i*2 + (busy ? 2*(float)Math.sin(phase*Math.PI*2+i) : 0);
                    canvas.drawLine(-23+i*3,-height,-23+i*3,height,paint);
                }
            }
            canvas.save(); canvas.translate(19,-18); if (busy) canvas.rotate(phase*360);
            path.reset(); path.moveTo(0,-5); path.lineTo(1.5f,-1.5f); path.lineTo(5,0); path.lineTo(1.5f,1.5f);
            path.lineTo(0,5); path.lineTo(-1.5f,1.5f); path.lineTo(-5,0); path.lineTo(-1.5f,-1.5f); path.close();
            paint.setStyle(Paint.Style.FILL); canvas.drawPath(path,paint); canvas.restore(); canvas.restore();
        }
    }
}
