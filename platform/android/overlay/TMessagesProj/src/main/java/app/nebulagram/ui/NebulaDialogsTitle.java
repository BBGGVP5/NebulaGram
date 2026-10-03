package app.nebulagram.ui;

import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.text.Spanned;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/** Supplies the branded home title and the optional live folder title. */
public final class NebulaDialogsTitle {
    private NebulaDialogsTitle() { }
    private static final WeakHashMap<ActionBar, WeakReference<NebulaFolderTitleView>> collapsedTitles = new WeakHashMap<>();

    public static boolean sameTitle(CharSequence first, CharSequence second) {
        if (!TextUtils.equals(first, second)) return false;
        AnimatedEmojiSpan[] a = first instanceof Spanned
                ? ((Spanned) first).getSpans(0, first.length(), AnimatedEmojiSpan.class) : new AnimatedEmojiSpan[0];
        AnimatedEmojiSpan[] b = second instanceof Spanned
                ? ((Spanned) second).getSpans(0, second.length(), AnimatedEmojiSpan.class) : new AnimatedEmojiSpan[0];
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i].getDocumentId() != b[i].getDocumentId()
                    || ((Spanned) first).getSpanStart(a[i]) != ((Spanned) second).getSpanStart(b[i])
                    || ((Spanned) first).getSpanEnd(a[i]) != ((Spanned) second).getSpanEnd(b[i])) return false;
        }
        return true;
    }

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
        CharSequence title = NebulaAppearance.homeChatsTitle()
                ? NebulaText.text("Чаты", "Chats") : "NebulaGram";
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
        boolean changed = previous != null && !sameTitle(previous.getText(), title);
        Drawable targetStatus = selected == null ? statusDrawable : null;
        // setTitle reassigns text and the status drawable and requests layout.
        // Avoid restarting that work for duplicate folder notifications.
        if (previous == null || changed || previous.getRightDrawable() != targetStatus) {
            actionBar.setNebulaTitle(title, targetStatus, changed);
        }
        if (changed) actionBar.requestLayout();
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
