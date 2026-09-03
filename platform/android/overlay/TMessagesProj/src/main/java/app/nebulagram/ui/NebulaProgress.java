package app.nebulagram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;

/**
 * Шаги первого запуска — полоски под кнопками.
 *
 * <p>Именно под, а не над заголовком: в Material 3 это строка состояния, а не
 * заголовок, и место ей рядом с действиями.
 */
public final class NebulaProgress {

    private NebulaProgress() {
    }

    public static View build(Context context, int steps, int current) {
        NebulaTheme theme = NebulaTheme.of(context);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        for (int i = 0; i < steps; i++) {
            boolean active = i == current;
            View dash = new View(context);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(AndroidUtilities.dp(2));
            shape.setColor(active ? theme.primary() : theme.outline());
            dash.setBackground(shape);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    AndroidUtilities.dp(active ? 30 : 20), AndroidUtilities.dp(4));
            params.leftMargin = i == 0 ? 0 : AndroidUtilities.dp(6);
            row.addView(dash, params);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = AndroidUtilities.dp(12);
        params.bottomMargin = AndroidUtilities.dp(4);
        row.setLayoutParams(params);
        return row;
    }
}
