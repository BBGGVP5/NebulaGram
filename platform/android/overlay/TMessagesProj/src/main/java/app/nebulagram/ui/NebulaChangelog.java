package app.nebulagram.ui;

import org.telegram.tgnet.TLRPC;
import java.util.List;

/** Explicit caption association, independent of rendering and network requests. */
public final class NebulaChangelog {
    public static boolean validRange(int offset, int length, int textLength) {
        return offset >= 0 && length > 0 && offset <= textLength && length <= textLength - offset;
    }

    public static int reference(TLRPC.Message caption) {
        if (caption == null || caption.message == null) return 0;
        if (caption.entities != null) for (TLRPC.MessageEntity entity : caption.entities) {
            if (entity instanceof TLRPC.TL_messageEntityTextUrl && validRange(entity.offset, entity.length, caption.message.length())
                    && NebulaRelease.isChangelogLabel(caption.message.substring(entity.offset, entity.offset + entity.length))) {
                int id = NebulaRelease.linkedPost(entity.url);
                if (id != 0) return id;
            }
        }
        return NebulaRelease.plainChangelogPost(caption.message);
    }

    public static TLRPC.Message albumCaption(TLRPC.Message apk, List<TLRPC.Message> posts) {
        TLRPC.Message caption = apk;
        if (apk == null || apk.grouped_id == 0) return caption;
        for (TLRPC.Message post : posts) {
            if (post.peer_id == null || post.peer_id.channel_id != NebulaRelease.CHANNEL_ID
                    || post.grouped_id != apk.grouped_id || post.message == null) continue;
            boolean linked = reference(post) != 0, chosenLinked = reference(caption) != 0;
            if (linked && !chosenLinked || linked == chosenLinked
                    && post.message.length() > (caption.message == null ? 0 : caption.message.length())) caption = post;
        }
        return caption;
    }
}
