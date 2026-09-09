package app.nebulagram.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

public final class NebulaGlassSettings {
    private NebulaGlassSettings() { }
    public static void add(LinearLayout parent) {
        Context c=parent.getContext();
        parent.addView(NebulaCard.header(c, NebulaText.text("Жидкое стекло", "Liquid Glass")));
        NebulaCard card=new NebulaCard(c);
        NebulaExpand details=new NebulaExpand(c,NebulaGlass.custom());
        View preview=new Preview(c);
        details.addView(preview,new LinearLayout.LayoutParams(-1,AndroidUtilities.dp(132)));
        slider(details,NebulaText.text("Прозрачность", "Transparency"),100-NebulaGlass.value("opacity",63),n->{NebulaGlass.setValue("opacity",100-n);preview.invalidate();});
        slider(details,NebulaText.text("Размытие", "Blur"),NebulaGlass.value("blur",40),n->{NebulaGlass.setValue("blur",n);preview.invalidate();});
        slider(details,NebulaText.text("Преломление", "Refraction"),NebulaGlass.value("refraction",44),n->{NebulaGlass.setValue("refraction",n);preview.invalidate();});
        details.addView(NebulaExtras.toggle(c,R.drawable.msg_customize,NebulaText.text("Блики", "Highlights"),null,
            NebulaAppearance.glassHighlights(),v->{NebulaAppearance.setGlassHighlights(v);preview.invalidate();}));
        card.add(NebulaExtras.toggle(c,R.drawable.msg_customize,NebulaText.text("Настроить стекло", "Customize glass"),null,
            NebulaGlass.custom(),v->{NebulaGlass.custom(v);details.expand(v);preview.invalidate();}));
        NebulaRow quality = new NebulaRow(c).icon(R.drawable.msg_photo_settings);
        String[] modes = {NebulaText.text("Автоматически", "Automatic"), NebulaText.text("Полное", "Full"), NebulaText.text("Облегчённое", "Light")};
        quality.title(NebulaText.text("Качество стекла: ", "Glass quality: ") + modes[NebulaGlass.quality()]);
        quality.trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> new org.telegram.ui.ActionBar.AlertDialog.Builder(c)
            .setTitle(NebulaText.text("Адаптивное стекло", "Adaptive glass"))
            .setItems(modes, (dialog, which) -> {
                NebulaGlass.quality(which);
                quality.title(NebulaText.text("Качество стекла: ", "Glass quality: ") + modes[which]);
                preview.invalidate();
            }).show());
        card.add(quality);
        TextView hint = new TextView(c);
        hint.setText(NebulaText.text("Авто облегчает размытие и отключает преломление при энергосбережении, нагреве или малом объёме ОЗУ. Ваши настройки сохраняются.", "Auto reduces blur and disables refraction during power saving, thermal pressure or on low-RAM devices. Your settings are preserved."));
        hint.setTextColor(NebulaTheme.of(c).onSurfaceVariant()); hint.setTextSize(14); hint.setPadding(dp(18), dp(8), dp(18), dp(12));
        card.add(hint);
        card.add(details);parent.addView(card);
        NebulaCard haptics=new NebulaCard(c);
        NebulaExpand hapticDetails=new NebulaExpand(c,NebulaHaptics.enabled());
        slider(hapticDetails,NebulaText.text("Сила отклика", "Feedback strength"),NebulaHaptics.strength(),NebulaHaptics::strength);
        hapticDetails.addView(new NebulaRow(c).title(NebulaText.text("Попробовать отклик", "Try feedback")).withClick(NebulaHaptics::tick));
        haptics.add(NebulaExtras.toggle(c,R.drawable.msg_customize,NebulaText.text("Виброотклик стекла", "Glass haptics"),null,
            NebulaHaptics.enabled(),v->{NebulaHaptics.enabled(v);hapticDetails.expand(v);if(v)NebulaHaptics.tick(haptics);}));
        haptics.add(hapticDetails);parent.addView(NebulaCard.header(c,NebulaText.text("Отклик", "Feedback")));parent.addView(haptics);
    }
    public interface Change { void set(int value); }
    public static void slider(LinearLayout parent,String title,int value,Change change) {
        Context c=parent.getContext();TextView label=new TextView(c);
        label.setTextColor(NebulaTheme.of(c).onSurface());label.setTextSize(14);
        label.setPadding(dp(18),dp(12),dp(18),0);parent.addView(label);
        SeekBar bar=new SeekBar(c);bar.setMax(100);bar.setProgress(value);bar.setPadding(dp(18),dp(6),dp(18),dp(12));
        label.setText(title+" · "+value+"%");parent.addView(bar,new LinearLayout.LayoutParams(-1,dp(46)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int n,boolean user){label.setText(title+" · "+n+"%");if(user)change.set(n);}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){NebulaHaptics.tick(s);}
        });
    }
    private static int dp(float n){return AndroidUtilities.dp(n);}
    private static final class Preview extends View {
        final Paint paint=new Paint(3);final RectF rect=new RectF();
        final RectF wallpaperBounds = new RectF();
        final Path clip = new Path();
        Bitmap blurredWallpaper;
        Drawable cachedWallpaper;
        int cachedWidth, cachedHeight, cachedBlur = -1;
        Preview(Context c){super(c);setContentDescription(NebulaText.text("Предпросмотр стекла", "Glass preview"));}
        @Override protected void onDraw(Canvas canvas){
            NebulaTheme theme=NebulaTheme.of(getContext());
            rect.set(dp(16),dp(12),getWidth()-dp(16),getHeight()-dp(12));
            if (rect.width() <= 0 || rect.height() <= 0) return;
            wallpaperBounds.set(rect);
            clip.rewind();clip.addRoundRect(rect,dp(24),dp(24),Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(clip);canvas.translate(rect.left,rect.top);
            NebulaWallpaperPreview.drawWallpaper(canvas,(int)rect.width(),(int)rect.height());
            canvas.restoreToCount(save);
            updateBlur();
            rect.inset(dp(22),dp(20));
            if (blurredWallpaper != null) {
                clip.rewind();clip.addRoundRect(rect,dp(24),dp(24),Path.Direction.CW);
                save = canvas.save();canvas.clipPath(clip);
                paint.setColor(0xffffffff);
                canvas.drawBitmap(blurredWallpaper,null,wallpaperBounds,paint);
                canvas.restoreToCount(save);
            }
            paint.setColor(NebulaMenuStyle.surface(null));paint.setAlpha(Math.round(NebulaGlass.opacity()*255));canvas.drawRoundRect(rect,dp(24),dp(24),paint);paint.setAlpha(255);
            if(NebulaAppearance.glassHighlights()){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(.5f+NebulaGlass.refraction()*2));paint.setColor(0x77ffffff);canvas.drawRoundRect(rect,dp(24),dp(24),paint);paint.setStyle(Paint.Style.FILL);}
            paint.setColor(NebulaChatColors.foreground(theme.onSurface(),NebulaMenuStyle.surface(null)));paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(16));
            canvas.drawText(NebulaText.text("Жидкое стекло", "Liquid Glass"),rect.centerX(),rect.centerY()+dp(5),paint);
        }
        private void updateBlur() {
            Drawable wallpaper = Theme.getCachedWallpaperNonBlocking();
            int width = (int) wallpaperBounds.width(), height = (int) wallpaperBounds.height();
            int blur = Math.round(dp(NebulaGlass.blur()) / 4f);
            if (blurredWallpaper != null && cachedWallpaper == wallpaper && cachedWidth == width
                    && cachedHeight == height && cachedBlur == blur) return;
            releaseBlur();
            cachedWallpaper = wallpaper;cachedWidth = width;cachedHeight = height;cachedBlur = blur;
            if (blur == 0) return;
            blurredWallpaper = Bitmap.createBitmap(Math.max(1,width/4),Math.max(1,height/4),Bitmap.Config.ARGB_8888);
            Canvas capture = new Canvas(blurredWallpaper);
            capture.scale(blurredWallpaper.getWidth()/(float)width,blurredWallpaper.getHeight()/(float)height);
            NebulaWallpaperPreview.drawWallpaper(capture,width,height);
            Utilities.stackBlurBitmap(blurredWallpaper,blur);
        }
        private void releaseBlur() {
            if (blurredWallpaper != null) { blurredWallpaper.recycle();blurredWallpaper = null; }
            cachedWallpaper = null;
        }
        @Override protected void onDetachedFromWindow() {
            releaseBlur();
            super.onDetachedFromWindow();
        }
    }
}
