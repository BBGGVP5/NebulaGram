package app.nebulagram.ui;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import java.util.function.IntConsumer;
import org.telegram.ui.ActionBar.Theme;

/** Compact camera content matching the inline voice-to-text panel. */
public final class NebulaRoundCameraSheet {
    private NebulaRoundCameraSheet() { }
    public static ScrollView content(Context c, Theme.ResourcesProvider provider, boolean front,
                                     IntConsumer selected, Runnable close) {
        ScrollView scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false); scroll.setClipToOutline(true);
        // Поверхность и краски — диалоговые, как у самого Telegram. Карточка
        // при этом остаётся привязанной к полю ввода: выбор камеры делают в тот
        // момент, когда смотрят на кадр, и центр экрана закрыл бы именно его.
        scroll.setBackground(NebulaAiPanelUi.shape(
                Theme.getColor(Theme.key_dialogBackground, provider), 24));
        LinearLayout body = NebulaAiPanelUi.column(c, true);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        body.addView(NebulaAiPanelUi.header(c, NebulaAiPanelUi.CAMERA,
                NebulaText.text("Камера кружка", "Round video camera"),
                NebulaText.text("С какой стороны начать запись?", "Which camera should start recording?"), close),
                new LinearLayout.LayoutParams(-1, -2));
        // Это выбор, а не два действия: Telegram показывает такое кружками,
        // и вместе с его ячейкой достаются цвета по теме и роль для озвучки.
        LinearLayout choices = new LinearLayout(c);
        choices.setOrientation(LinearLayout.VERTICAL);
        final boolean[] completed = {false};
        for (int index = 0; index < 2; index++) {
            final int facing = index;
            boolean checked = front == (index == 0);
            org.telegram.ui.Cells.RadioColorCell cell =
                    new org.telegram.ui.Cells.RadioColorCell(c, provider);
            cell.setPadding(NebulaAiPanelUi.dp(4), 0, NebulaAiPanelUi.dp(4), 0);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground, provider),
                    Theme.getColor(Theme.key_dialogRadioBackgroundChecked, provider));
            cell.setTextAndValue(NebulaText.text(index == 0 ? "Фронтальная" : "Основная",
                    index == 0 ? "Front camera" : "Rear camera"), checked);
            cell.setBackground(Theme.createSelectorDrawable(
                    Theme.getColor(Theme.key_listSelector, provider), Theme.RIPPLE_MASK_ALL));
            cell.setOnClickListener(v -> {
                if (completed[0]) return;
                completed[0] = true;
                selected.accept(facing);
            });
            choices.addView(cell, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout.LayoutParams bounds = new LinearLayout.LayoutParams(-1, -2); bounds.topMargin = NebulaAiPanelUi.dp(12);
        body.addView(choices, bounds);
        return scroll;
    }
}
