package app.nebulagram.ui;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/** Supplies the branded home title and the optional live folder title. */
public final class NebulaDialogsTitle {
    private NebulaDialogsTitle() { }
    private static final WeakHashMap<ActionBar, WeakReference<SimpleTextView>> collapsedTitles = new WeakHashMap<>();

    public static void bind(ActionBar bar, SimpleTextView view) {
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
        actionBar.setTitle(title, statusDrawable);
        int cacheType = selected != null && selected.title_noanimate
                ? AnimatedEmojiDrawable.CACHE_TYPE_NOANIMATE_FOLDER : AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES;
        WeakReference<SimpleTextView> reference = collapsedTitles.get(actionBar);
        SimpleTextView collapsed = reference == null ? null : reference.get();
        if (collapsed != null) {
            collapsed.setEmojiCacheType(cacheType);
            collapsed.setText(title);
            collapsed.requestLayout();
        }
        SimpleTextView titleView = actionBar.getTitleTextView();
        if (titleView != null) {
            titleView.setEmojiCacheType(cacheType);
        }
    }
}
