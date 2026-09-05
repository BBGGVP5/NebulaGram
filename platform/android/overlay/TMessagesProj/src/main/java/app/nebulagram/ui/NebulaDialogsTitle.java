package app.nebulagram.ui;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/** Supplies the branded home title and the optional live folder title. */
public final class NebulaDialogsTitle {
    private NebulaDialogsTitle() { }
    private static final WeakHashMap<ActionBar, WeakReference<NebulaFolderTitleView>> collapsedTitles = new WeakHashMap<>();

    public static void bind(ActionBar bar, NebulaFolderTitleView view) {
        if (bar == null) return;
        collapsedTitles.put(bar, new WeakReference<>(view));
        SimpleTextView title = bar.getTitleTextView();
        if (title != null) view.setText(title.getText());
    }

    public static void apply(ActionBar actionBar, MessagesController controller,
                             int selectedType, Drawable statusDrawable) {
        if (actionBar == null) {
            return;
        }
        CharSequence title = "NebulaGram";
        MessagesController.DialogFilter selected = null;
        if (NebulaAppearance.folderTitle() && controller != null) {
            ArrayList<MessagesController.DialogFilter> filters = controller.getDialogFilters();
            if (selectedType >= 0 && selectedType < filters.size()) {
                MessagesController.DialogFilter candidate = filters.get(selectedType);
                if (!candidate.isDefault() && !TextUtils.isEmpty(candidate.name)) {
                    selected = candidate;
                    SimpleTextView titleView = actionBar.getTitleTextView();
                    if (titleView != null) {
                        title = Emoji.replaceEmoji(candidate.name,
                                titleView.getPaint().getFontMetricsInt(), false);
                        title = MessageObject.replaceAnimatedEmoji(title, candidate.entities,
                                titleView.getPaint().getFontMetricsInt());
                    } else {
                        title = candidate.name;
                    }
                }
            }
        }
        SimpleTextView previous = actionBar.getTitleTextView();
        boolean changed = previous != null && !TextUtils.equals(previous.getText(), title);
        if (changed && previous.isAttachedToWindow() && previous.getWidth() > 0) {
            previous.animate().setListener(null).cancel();
            SimpleTextView outgoing = actionBar.getTitleTextView2();
            if (outgoing != null) outgoing.animate().setListener(null).cancel();
            actionBar.setTitleAnimated(title, false, 240, CubicBezierInterpolator.EASE_OUT_QUINT);
        }
        actionBar.setTitle(title, selected == null ? statusDrawable : null);
        int cacheType = selected != null && selected.title_noanimate
                ? AnimatedEmojiDrawable.CACHE_TYPE_NOANIMATE_FOLDER : AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES;
        WeakReference<NebulaFolderTitleView> reference = collapsedTitles.get(actionBar);
        NebulaFolderTitleView collapsed = reference == null ? null : reference.get();
        if (collapsed != null) {
            collapsed.setTitle(title, cacheType, selected == null, changed);
        }
        SimpleTextView titleView = actionBar.getTitleTextView();
        if (titleView != null) {
            titleView.setEmojiCacheType(cacheType);
        }
    }
}
