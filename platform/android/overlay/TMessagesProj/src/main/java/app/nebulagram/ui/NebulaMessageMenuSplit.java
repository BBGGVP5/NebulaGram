package app.nebulagram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * Split message menu: frequent actions leave the list and become a separate
 * row of icons below it, the way Cherrygram lays the menu out. The list keeps
 * everything else, so no action disappears.
 */
public final class NebulaMessageMenuSplit {
    /** ChatActivity option ids, in the order the row shows them. */
    static final int REPLY = 8, COPY = 3, EDIT = 12, FORWARD = 2, COPY_LINK = 22;
    private static final int[] ORDER = {REPLY, COPY, EDIT, FORWARD, COPY_LINK};
    public static final int MAX = 5;

    private NebulaMessageMenuSplit() { }

    public static boolean enabled() {
        return NebulaMessageMenuSettings.enabled("split", true);
    }

    /**
     * Indices of {@code options} that move into the icon row, in row order.
     * Empty unless at least two actions move and at least one stays in the
     * list: a lone icon or an empty list reads worse than the plain menu.
     */
    public static int[] row(List<Integer> options, boolean enabled) {
        if (!enabled || options == null) return new int[0];
        List<Integer> picked = new ArrayList<>();
        for (int option : ORDER) {
            int index = options.indexOf(option);
            if (index >= 0 && picked.size() < MAX) picked.add(index);
        }
        if (picked.size() < 2 || options.size() - picked.size() < 1) return new int[0];
        int[] result = new int[picked.size()];
        for (int i = 0; i < result.length; i++) result[i] = picked.get(i);
        return result;
    }

    public static boolean contains(int[] row, int index) {
        for (int value : row) if (value == index) return true;
        return false;
    }

    public interface OnAction { void run(int index); }

    /** Equal-width icon buttons; a long press names the action. */
    public static View create(Context context, int[] row, List<Integer> icons, List<CharSequence> labels,
                              Theme.ResourcesProvider resources, OnAction action) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(2), AndroidUtilities.dp(6), AndroidUtilities.dp(2));
        int color = Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resources);
        int selector = Theme.getColor(Theme.key_dialogButtonSelector, resources);
        for (int index : row) {
            ImageView button = new ImageView(context);
            button.setScaleType(ImageView.ScaleType.CENTER);
            button.setImageResource(icons.get(index));
            button.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            button.setBackground(Theme.createRadSelectorDrawable(selector, 12, 12));
            CharSequence label = labels.get(index);
            button.setContentDescription(label);
            button.setOnClickListener(v -> action.run(index));
            button.setOnLongClickListener(v -> {
                NebulaHaptics.tick(v);
                Toast.makeText(v.getContext(), label, Toast.LENGTH_SHORT).show();
                return true;
            });
            layout.addView(button, new LinearLayout.LayoutParams(0, AndroidUtilities.dp(44), 1f));
        }
        return layout;
    }
}
