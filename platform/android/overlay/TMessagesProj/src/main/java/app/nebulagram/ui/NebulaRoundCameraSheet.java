package app.nebulagram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.IntConsumer;
import org.telegram.ui.ActionBar.Theme;

/** Compact camera content matching the inline voice-to-text panel. */
public final class NebulaRoundCameraSheet {
    private NebulaRoundCameraSheet() { }
    public static ScrollView content(Context c, Theme.ResourcesProvider provider, boolean front,
                                     IntConsumer selected, Runnable close) {
        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false); scroll.setClipToOutline(true);
        scroll.setBackground(NebulaAiPanelUi.shape(NebulaTheme.of(c).modalSurface(), 24));
        LinearLayout body = NebulaAiPanelUi.column(c, true);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        body.addView(NebulaAiPanelUi.header(c, NebulaAiPanelUi.CAMERA,
                NebulaText.text("Камера кружка", "Round video camera"),
                NebulaText.text("С какой стороны начать запись?", "Which camera should start recording?"), close),
                new LinearLayout.LayoutParams(-1, -2));
        LinearLayout choices = new LinearLayout(c);
        final boolean[] completed = {false};
        for (int index = 0; index < 2; index++) {
            final int facing = index;
            boolean checked = front == (index == 0);
            TextView button = NebulaAiPanelUi.button(c,
                    NebulaText.text(index == 0 ? "Фронтальная" : "Основная", index == 0 ? "Front camera" : "Rear camera"),
                    checked, v -> { if (!completed[0]) { completed[0] = true; selected.accept(facing); } });
            button.setSelected(checked); button.setMinHeight(NebulaAiPanelUi.dp(56)); button.setMaxLines(2);
            button.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName("android.widget.Button"); info.setCheckable(true); info.setChecked(host.isSelected());
                }
            });
            LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(0, -1, 1);
            if (index > 0) bounds.leftMargin = NebulaAiPanelUi.dp(8);
            choices.addView(button, bounds);
        }
        LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(-1, -2); bounds.topMargin = NebulaAiPanelUi.dp(12);
        body.addView(choices, bounds);
        return scroll;
    }
}
