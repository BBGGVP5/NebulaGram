package app.nebulagram.ui;

import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import java.util.function.Supplier;
import org.telegram.messenger.AndroidUtilities;

/** Places the editable transcript above the real recorder, following keyboard/inset changes. */
public final class NebulaDraftVoiceInlineHost implements NebulaDraftVoiceTranscription.InlineHost {
    private final View anchor;
    private final FrameLayout parent;
    private final Supplier<Integer> controlsTop;
    private final Runnable closed;
    private final int[] parentPosition = new int[2], anchorPosition = new int[2];
    private FrameLayout panel;
    private ViewTreeObserver.OnPreDrawListener listener;
    private int availableHeight;
    private Runnable dismiss;
    public NebulaDraftVoiceInlineHost(View anchor, FrameLayout parent, Supplier<Integer> controlsTop, Runnable closed) {
        this.anchor=anchor;this.parent=parent;this.controlsTop=controlsTop;this.closed=closed;
    }
    public void onDismiss(Runnable dismiss) { this.dismiss=dismiss; }
    @Override public void show(View content) {
        if(panel!=null)hide();
        panel=new FrameLayout(anchor.getContext()) {
            @Override protected void onMeasure(int width,int height) {
                int max=availableHeight>0?availableHeight:AndroidUtilities.dp(320);
                super.onMeasure(width,MeasureSpec.makeMeasureSpec(max,MeasureSpec.AT_MOST));
            }
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                if(event.getKeyCode()==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP&&dismiss!=null) {
                    dismiss.run();return true;
                }
                return super.dispatchKeyEvent(event);
            }
        };
        panel.setFocusableInTouchMode(true);
        panel.setElevation(AndroidUtilities.dp(4));
        panel.addView(content,new FrameLayout.LayoutParams(-1,-2));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        lp.leftMargin=lp.rightMargin=AndroidUtilities.dp(12);
        parent.addView(panel,lp);
        listener=()->{
            if(panel==null)return true;
            parent.getLocationOnScreen(parentPosition);anchor.getLocationOnScreen(anchorPosition);
            Integer control=controlsTop.get();
            int top=Math.min(anchorPosition[1],control==null?anchorPosition[1]:control)-parentPosition[1];
            int height=Math.max(AndroidUtilities.dp(64),Math.min(AndroidUtilities.dp(320),top-AndroidUtilities.dp(72)));
            FrameLayout.LayoutParams layout=(FrameLayout.LayoutParams)panel.getLayoutParams();
            int bottom=Math.max(0,parent.getHeight()-top+AndroidUtilities.dp(8));
            if(layout.bottomMargin!=bottom||availableHeight!=height) {
                availableHeight=height;layout.bottomMargin=bottom;panel.setLayoutParams(layout);
            }
            return true;
        };
        parent.getViewTreeObserver().addOnPreDrawListener(listener);
        listener.onPreDraw();panel.requestFocus();
        if(!NebulaGlass.reduced()) {
            panel.setAlpha(0);panel.setTranslationY(AndroidUtilities.dp(8));
            panel.animate().alpha(1).translationY(0).setDuration(180).start();
        }
    }
    @Override public void hide() {
        if(listener!=null&&parent.getViewTreeObserver().isAlive())parent.getViewTreeObserver().removeOnPreDrawListener(listener);
        listener=null;
        if(panel!=null) {
            View focused=panel.findFocus();if(focused!=null)AndroidUtilities.hideKeyboard(focused);
            panel.animate().cancel();parent.removeView(panel);panel=null;
        }
        closed.run();
    }
}
