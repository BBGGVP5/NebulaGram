package app.nebulagram.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.NumberPicker;
import java.util.function.IntConsumer;

/** Illustrated camera selector. The phone is a settings sample, not a live camera feed. */
public final class NebulaCameraPreview extends LinearLayout {
    private final NumberPicker picker;
    private final Phone phone;
    private final IntConsumer onSelected;
    private static int dp(float value) { return AndroidUtilities.dp(value); }

    public NebulaCameraPreview(Context context, IntConsumer onSelected) {
        super(context);
        this.onSelected = onSelected;
        NebulaTheme theme = NebulaTheme.of(context);
        setOrientation(VERTICAL);
        setPadding(dp(16), dp(16), dp(16), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(theme.surfaceContainer()); background.setCornerRadius(dp(24));
        setBackground(background); setClipToOutline(true);
        TextView heading = new TextView(context);
        heading.setText(NebulaText.text("Тип камеры", "Camera type"));
        heading.setTextSize(14); heading.setTextColor(theme.primary());
        heading.setTypeface(AndroidUtilities.bold());
        addView(heading, new LayoutParams(-1, -2));
        LinearLayout sample = new LinearLayout(context);
        sample.setGravity(Gravity.CENTER_VERTICAL);
        LayoutParams sampleParams = new LayoutParams(-1, dp(182)); sampleParams.topMargin = dp(8);
        addView(sample, sampleParams);
        phone = new Phone(context);
        sample.addView(phone, new LayoutParams(0, -1, .44f));
        picker = new NumberPicker(context, 14) {
            private final Paint highlight = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                highlight.setColor(ColorUtils.setAlphaComponent(NebulaTheme.of(getContext()).primary(), 30));
                canvas.drawRoundRect(dp(2), getHeight()/2f-dp(24), getWidth()-dp(2), getHeight()/2f+dp(24), dp(12), dp(12), highlight);
                super.onDraw(canvas);
            }
        };
        picker.setItemCount(3); picker.setMinValue(0); picker.setMaxValue(4);
        picker.setDisplayedValues(new String[]{NebulaText.text("Авто · Telegram", "Auto · Telegram"),
                NebulaText.text("Совместимый", "Compatibility"), "Camera2", "CameraX", NebulaText.text("Системная", "System")});
        picker.setWrapSelectorWheel(false); picker.setDrawDividers(false);
        picker.setTextColor(theme.onSurface()); picker.setValue(NebulaCameraSettings.backend());
        picker.setOnValueChangedListener((view, previous, next) -> {
            if (next != NebulaCameraSettings.backend()) this.onSelected.accept(next);
            phone.select(next);
        });
        sample.addView(picker, new LayoutParams(0, dp(156), .56f));
    }

    public void refresh() {
        int selected = NebulaCameraSettings.backend();
        if (picker.getValue() != selected) picker.setValue(selected);
        phone.select(selected);
    }

    private static final class Phone extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        // Module bounds followed by three lens centres/visibility. Coordinates use a 112 × 174 phone.
        private static final float[][] MODELS = {
            {10,12,48,101, 29,29,1, 29,57,1, 29,85,1},
            {10,12,48,75, 29,29,1, 29,57,1, 29,57,0},
            {5,18,107,65, 28,41,1, 56,41,1, 84,41,1},
            {10,12,79,84, 29,30,1, 59,48,1, 29,65,1},
            {10,12,50,53, 30,32,1, 30,32,0, 30,32,0}
        };
        private float[] geometry = MODELS[NebulaCameraSettings.backend()].clone();
        private int selected = NebulaCameraSettings.backend();
        private float turn;
        private ValueAnimator animation;
        Phone(Context context) { super(context); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void select(int value) {
            if (selected == value) return;
            selected = value;
            if (animation != null) animation.cancel();
            if (!isAttachedToWindow() || NebulaGlass.reduced() || Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) {
                geometry = MODELS[value].clone(); turn = 0; invalidate(); return;
            }
            final float[] start = geometry.clone(), end = MODELS[value];
            animation = ValueAnimator.ofFloat(0, 1);
            animation.setDuration(280); animation.setInterpolator(new DecelerateInterpolator(1.3f));
            animation.addUpdateListener(a -> {
                float progress = (float)a.getAnimatedValue();
                for (int i=0; i<geometry.length; i++) geometry[i] = start[i] + (end[i]-start[i])*progress;
                turn = (float)Math.sin(progress*Math.PI)*-3; invalidate();
            });
            animation.start();
        }
        @Override protected void onDetachedFromWindow() {
            if (animation != null) { animation.cancel(); animation = null; }
            geometry = MODELS[selected].clone(); turn = 0; super.onDetachedFromWindow();
        }
        private void fill(Canvas canvas, float l, float t, float r, float b, float radius, int color) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(color); rect.set(l,t,r,b);
            canvas.drawRoundRect(rect,radius,radius,paint);
        }
        @Override protected void onDraw(Canvas canvas) {
            NebulaTheme theme = NebulaTheme.of(getContext());
            float scale = Math.min(getWidth()/130f, getHeight()/188f);
            int saved = canvas.save(); canvas.translate((getWidth()-112*scale)/2, (getHeight()-174*scale)/2);
            canvas.scale(scale,scale); canvas.rotate(turn,56,87);
            int body = ColorUtils.blendARGB(theme.surfaceContainer(), theme.primary(), .09f);
            fill(canvas,0,0,112,174,20,body);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.4f);
            paint.setColor(ColorUtils.blendARGB(body,theme.onSurface(),.26f));
            canvas.drawRoundRect(1,1,111,173,19,19,paint);
            fill(canvas,111,45,114,72,1.5f,ColorUtils.blendARGB(body,theme.onSurface(),.35f));
            fill(canvas,geometry[0],geometry[1],geometry[2],geometry[3],15,ColorUtils.blendARGB(body,theme.onSurface(),.08f));
            for (int i=4;i<geometry.length;i+=3) {
                float x=geometry[i],y=geometry[i+1],visibility=geometry[i+2];
                if (visibility < .01f) continue;
                paint.setStyle(Paint.Style.FILL); paint.setColor(ColorUtils.setAlphaComponent(theme.onSurface(), Math.round(75*visibility)));
                canvas.drawCircle(x,y,12,paint);
                paint.setColor(ColorUtils.setAlphaComponent(body, Math.round(255*visibility))); canvas.drawCircle(x,y,10.5f,paint);
                paint.setColor(ColorUtils.setAlphaComponent(theme.primary(), Math.round(95*visibility))); canvas.drawCircle(x,y,6.5f,paint);
                paint.setColor(ColorUtils.setAlphaComponent(theme.onSurface(), Math.round(125*visibility))); canvas.drawCircle(x-2,y-2,2.3f,paint);
            }
            paint.setStyle(Paint.Style.FILL); paint.setColor(ColorUtils.blendARGB(body,theme.onSurface(),.55f));
            canvas.drawCircle(91,86,3,paint);
            // Nebula orbit mark; no third-party manufacturer logo or extra control icon.
            paint.setColor(ColorUtils.blendARGB(body,theme.primary(),.55f)); paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);
            canvas.drawCircle(56,122,10,paint);rect.set(41,117,71,127);
            canvas.save();canvas.rotate(-28,56,122);canvas.drawOval(rect,paint);canvas.restore();
            paint.setStyle(Paint.Style.FILL);paint.setTextSize(9);paint.setTypeface(AndroidUtilities.bold());paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(new String[]{"AUTO","TG","2","X","SYS"}[selected],56,152,paint);
            paint.setTextAlign(Paint.Align.LEFT);canvas.restoreToCount(saved);
        }
    }
}
