package app.nebulagram.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/** A 48dp touch target, positioned independently from zoom and capture buttons. */
public final class NebulaExposureControl extends FrameLayout {
    public interface Listener { void changed(float value); }
    private final SeekBar slider;
    public NebulaExposureControl(Context context, Listener listener) {
        super(context);
        int position=NebulaCameraSettings.exposure();
        slider=new SeekBar(context); slider.setMax(200); slider.setProgress(100);
        slider.setContentDescription(NebulaText.text("Экспозиция камеры","Camera exposure"));
        int color=Theme.getColor(Theme.key_switchTrackChecked);
        slider.setProgressTintList(ColorStateList.valueOf(color));slider.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        addView(slider,new LayoutParams(dp(180),dp(48),Gravity.CENTER));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar view,int value,boolean user){if(user)listener.changed((value-100)/100f);}
            public void onStartTrackingTouch(SeekBar view){getParent().requestDisallowInterceptTouchEvent(true);}
            public void onStopTrackingTouch(SeekBar view){getParent().requestDisallowInterceptTouchEvent(false);}
        });
        // Rotate the complete touch surface, not just its painted track.
        if(position>1)setRotation(-90);
        setVisibility(position==0?GONE:VISIBLE);
    }
    private static int dp(float value){return AndroidUtilities.dp(value);}
    public static void attach(FrameLayout parent,Listener listener){
        int position=NebulaCameraSettings.exposure(); if(position==0)return;
        NebulaExposureControl view=new NebulaExposureControl(parent.getContext(),listener);
        LayoutParams params=new LayoutParams(dp(180),dp(48));
        if(position==1){params.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;params.bottomMargin=dp(122);}
        else {params.gravity=Gravity.CENTER_VERTICAL|(position==2?Gravity.LEFT:Gravity.RIGHT);
            // Compensate rotated bounds to keep the 48dp touch strip inside the screen.
            if(position==2)params.leftMargin=dp(-60);else params.rightMargin=dp(-60);}
        parent.addView(view,params);
    }
}
