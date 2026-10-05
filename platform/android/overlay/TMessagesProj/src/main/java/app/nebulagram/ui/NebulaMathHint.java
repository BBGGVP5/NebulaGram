package app.nebulagram.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.widget.EditText;

public final class NebulaMathHint {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String result="";
    public void update(CharSequence source) { result=NebulaChatPreferences.math()?NebulaMath.result(source==null?"":source.toString()):""; }
    public void draw(EditText input,Canvas canvas){
        if(result.isEmpty()||!NebulaChatPreferences.math())return;
        Layout layout=input.getLayout();if(layout==null)return;
        int last=layout.getLineCount()-1; if(last<0)return;
        paint.set(input.getPaint());paint.setColor(input.getCurrentHintTextColor());
        String value=" = "+result;float x=input.getCompoundPaddingLeft()+layout.getLineRight(last)-input.getScrollX();
        float y=input.getExtendedPaddingTop()+layout.getLineBaseline(last)-input.getScrollY();
        if(x+paint.measureText(value)>input.getWidth()-input.getCompoundPaddingRight()||y>input.getHeight()-input.getPaddingBottom())return;
        canvas.drawText(value,x,y,paint);
    }
}
