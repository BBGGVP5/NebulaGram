package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

public final class NebulaGlassSettings {
    private NebulaGlassSettings() { }
    public static void add(LinearLayout parent) {
        Context c=parent.getContext();
        parent.addView(NebulaCard.header(c, NebulaText.text("Жидкое стекло", "Liquid Glass")));
        NebulaCard card=new NebulaCard(c);
        NebulaExpand details=new NebulaExpand(c,NebulaGlass.custom());
        NebulaGlassPreview preview=new NebulaGlassPreview(c);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(200));
        previewParams.setMargins(dp(12), dp(12), dp(12), dp(4));
        parent.addView(preview, previewParams);
        TextView previewHint = new TextView(c);
        previewHint.setText(NebulaText.text("Проведи по превью — пилюля движется поверх текста. Настройки применяются сразу.", "Drag the preview: the capsule moves over text. Changes apply immediately."));
        previewHint.setTextSize(13); previewHint.setTextColor(NebulaTheme.of(c).onSurfaceVariant());
        previewHint.setPadding(dp(18),dp(8),dp(18),dp(12));parent.addView(previewHint);
        SeekBar transparency = slider(details,NebulaText.text("Прозрачность", "Transparency"),Math.round(75-NebulaGlass.value("opacity",63)*.75f),75,n->{NebulaGlass.setValue("opacity",Math.round((75-n)/.75f));preview.invalidate();});
        SeekBar blurAmount = slider(details,NebulaText.text("Размытие", "Blur"),NebulaGlass.value("blur",40),n->{NebulaGlass.setValue("blur",n);preview.invalidate();});
        SeekBar refraction = slider(details,NebulaText.text("Преломление", "Refraction"),NebulaGlass.value("refraction",0),n->{NebulaGlass.setValue("refraction",n);preview.invalidate();});
        TextView refractionHint = new TextView(c);
        refractionHint.setText(NebulaText.text("Преломление сдвигает фон у края стекла. Поставь 0%, чтобы текст под пилюлей не искажался.", "Refraction shifts the backdrop near the glass edge. Set it to 0% to keep the text underneath undistorted."));
        refractionHint.setTextSize(13); refractionHint.setTextColor(NebulaTheme.of(c).onSurfaceVariant());
        refractionHint.setPadding(dp(18),0,dp(18),dp(12));details.addView(refractionHint);
        NebulaRow highlights = NebulaExtras.toggle(c,R.drawable.msg_customize,NebulaText.text("Блики", "Highlights"),null,
            NebulaAppearance.glassHighlights(),v->{NebulaAppearance.setGlassHighlights(v);preview.invalidate();});
        details.addView(highlights);
        NebulaRow customize = NebulaExtras.toggle(c,R.drawable.msg_customize,NebulaText.text("Настроить стекло", "Customize glass"),null,
            NebulaGlass.custom(),v->{NebulaGlass.custom(v);details.expand(v);preview.invalidate();});
        card.add(customize);
        card.add(new NebulaRow(c).icon(R.drawable.msg_customize)
            .title(NebulaText.text("Применить жидкое стекло", "Apply liquid glass"))
            .subtitle(NebulaText.text("Мягкое преломление, лёгкое размытие и блики", "Soft refraction, light blur and highlights"), true)
            .withClick(v -> {
                NebulaGlass.custom(true);
                NebulaGlass.setValue("opacity",47); NebulaGlass.setValue("blur",20); NebulaGlass.setValue("refraction",60);
                NebulaAppearance.setGlassHighlights(true);
                customize.checked(true); highlights.checked(true); details.expand(true);
                transparency.setProgress(40); blurAmount.setProgress(20); refraction.setProgress(60);
                preview.invalidate();
            }));
        NebulaRow quality = new NebulaRow(c).icon(R.drawable.msg_photo_settings);
        String[] modes = {NebulaText.text("Автоматически", "Automatic"), NebulaText.text("Полное", "Full"), NebulaText.text("Облегчённое", "Light")};
        quality.title(NebulaText.text("Качество стекла: ", "Glass quality: ") + modes[NebulaGlass.quality()]);
        quality.trailing(NebulaRow.TRAIL_CHEVRON).withClick(v -> new NebulaDialog.Builder(c)
            .setTitle(NebulaText.text("Адаптивное стекло", "Adaptive glass"))
            .setSelectedIndex(NebulaGlass.quality()).setItems(modes, (dialog, which) -> {
                NebulaGlass.quality(which);
                quality.title(NebulaText.text("Качество стекла: ", "Glass quality: ") + modes[which]);
                preview.invalidate();
            }).show());
        card.add(quality);
        TextView hint = new TextView(c);
        Runnable updateStatus = () -> {
            boolean blur = org.telegram.messenger.LiteMode.isEnabled(org.telegram.messenger.LiteMode.FLAG_CHAT_BLUR);
            boolean liquid = android.os.Build.VERSION.SDK_INT >= 33 && org.telegram.messenger.LiteMode.isEnabled(org.telegram.messenger.LiteMode.FLAG_LIQUID_GLASS);
            refraction.setEnabled(blur && liquid && !NebulaGlass.reduced());
            String status = !blur ? NebulaText.text("Размытие отключено в энергосбережении Telegram: сейчас используется сплошная заливка.", "Blur is disabled in Telegram power saving: an opaque background is used.")
                    : NebulaGlass.reduced() ? NebulaText.text("Сейчас облегчённый режим: прозрачность до 15%, размытие до 20%, преломление выключено. Авто включает его при энергосбережении, нагреве или нехватке ОЗУ.", "Light mode is active: transparency up to 15%, blur up to 20%, refraction off. Auto enables it during power saving, heat or low RAM.")
                    : !liquid ? NebulaText.text("Размытие активно. Для преломления нужны Android 13+ и включённые эффекты Liquid Glass.", "Blur is active. Refraction needs Android 13+ and enabled Liquid Glass effects.")
                    : NebulaText.text("Полный эффект активен. Преломление: ", "Full effect active. Refraction: ") + Math.round(NebulaGlass.refraction()*200) + "%";
            hint.setText(status + "\n" + NebulaText.text("В меню прозрачность ограничена ради читаемости текста.", "Menu transparency is limited to keep labels readable."));
        };
        preview.setStatusChanged(updateStatus); updateStatus.run();
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
    public static SeekBar slider(LinearLayout parent,String title,int value,Change change) {
        return slider(parent, title, value, 100, change);
    }
    public static SeekBar slider(LinearLayout parent,String title,int value,int maximum,Change change) {
        Context c=parent.getContext();TextView label=new TextView(c);
        label.setTextColor(NebulaTheme.of(c).onSurface());label.setTextSize(14);
        label.setPadding(dp(18),dp(12),dp(18),0);parent.addView(label);
        SeekBar bar=new SeekBar(c);bar.setMax(maximum);bar.setProgress(value);bar.setPadding(dp(18),dp(6),dp(18),dp(12));
        label.setText(title+" · "+value+"%");parent.addView(bar,new LinearLayout.LayoutParams(-1,dp(46)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int n,boolean user){label.setText(title+" · "+n+"%");if(user)change.set(n);}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){NebulaHaptics.tick(s);}
        });
        return bar;
    }
    private static int dp(float n){return AndroidUtilities.dp(n);}
}
