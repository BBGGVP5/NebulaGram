package app.nebulagram.ui;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiSpan;

/** Clipboard text must retain server emoji identities even if the display omitted spans. */
public final class NebulaClipboardText {
    private NebulaClipboardText() { }

    public static CharSequence restore(MessageObject message, CharSequence displayed) {
        if (displayed == null || message == null || message.messageOwner == null
                || message.messageOwner.entities == null
                || !TextUtils.equals(displayed, message.messageOwner.message)) return displayed;
        SpannableStringBuilder result = null;
        for (TLRPC.MessageEntity item : message.messageOwner.entities) {
            if (!(item instanceof TLRPC.TL_messageEntityCustomEmoji)) continue;
            TLRPC.TL_messageEntityCustomEmoji emoji = (TLRPC.TL_messageEntityCustomEmoji) item;
            long id = emoji.document != null ? emoji.document.id : emoji.document_id;
            if (id == 0 || emoji.offset < 0 || emoji.length <= 0
                    || (long) emoji.offset + emoji.length > displayed.length()) continue;
            if (result == null) result = new SpannableStringBuilder(displayed);
            int end = emoji.offset + emoji.length;
            for (AnimatedEmojiSpan old : result.getSpans(emoji.offset, end, AnimatedEmojiSpan.class)) {
                result.removeSpan(old);
            }
            result.setSpan(new AnimatedEmojiSpan(id, null), emoji.offset, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return result == null ? displayed : result;
    }
}
