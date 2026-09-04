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

/** Supplies the branded home title and the optional live folder title. */
public final class NebulaDialogsTitle {
    private NebulaDialogsTitle() { }

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
        // Свёрнутые истории показывают не заголовок, а логотип: передаём ему
        // ту же надпись, иначе название папки видно только в развёрнутом виде.
        NebulaWordmark.setText(title);
        actionBar.setTitle(title, statusDrawable);
        SimpleTextView titleView = actionBar.getTitleTextView();
        if (titleView != null) {
            titleView.setEmojiCacheType(selected != null && selected.title_noanimate
                    ? AnimatedEmojiDrawable.CACHE_TYPE_NOANIMATE_FOLDER
                    : AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES);
        }
    }
}
