package app.nebulagram.ui;

import android.content.Context;
import android.view.Gravity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.SimpleTextView;

/** The collapsed stories header keeps native emoji spans and their lifecycle. */
public final class NebulaFolderTitleView extends SimpleTextView {
    public NebulaFolderTitleView(Context context) {
        super(context);
        setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        setTypeface(AndroidUtilities.bold());
        setTextSize(20);
        setWidthWrapContent(true);
        setEllipsizeByGradient(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = Math.min(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.displaySize.x / 2);
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), heightMeasureSpec);
    }
}
