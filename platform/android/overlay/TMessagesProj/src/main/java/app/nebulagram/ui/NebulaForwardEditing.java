package app.nebulagram.ui;

import java.util.ArrayList;
import java.util.HashMap;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.messenger.MessageSuggestionParams;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.ChatActivityEnterView;

/** Opt-in editable copies; editing is separate from the existing explicit Send action. */
public final class NebulaForwardEditing {
    private NebulaForwardEditing() { }
    public static boolean enabled() {
        return ApplicationLoader.applicationContext.getSharedPreferences("nebula_privacy", 0)
                .getBoolean("edit_forward_draft", false);
    }
    public static void setEnabled(boolean value) {
        ApplicationLoader.applicationContext.getSharedPreferences("nebula_privacy", 0)
                .edit().putBoolean("edit_forward_draft", value).apply();
    }
    public static boolean supported(int account, MessageObject object) {
        if (object == null || object.messageOwner == null) return false;
        TLRPC.Message m = object.messageOwner;
        if (object.getId() <= 0 || DialogObject.isEncryptedDialog(object.getDialogId()) || m.rich_message != null || m.ttl_period != 0 || m.destroyTime != 0
                || object.isSecretMedia() || object.needDrawBluredPreview()
                || (NebulaProtectedCopies.isProtected(account, object) && !NebulaContentProtection.enabled())) return false;
        if (m.media == null || m.media instanceof TLRPC.TL_messageMediaEmpty || m.media instanceof TLRPC.TL_messageMediaWebPage)
            return object.type == MessageObject.TYPE_TEXT && m.message != null && !m.message.trim().isEmpty();
        if (m.media.ttl_seconds != 0) return false;
        if (m.media instanceof TLRPC.TL_messageMediaPhoto) return m.media.photo instanceof TLRPC.TL_photo && m.media.photo.id != 0;
        return m.media instanceof TLRPC.TL_messageMediaDocument && m.media.document instanceof TLRPC.TL_document
                && m.media.document.id != 0 && !object.isSticker() && !object.isAnimatedSticker() && !object.isVoice() && !object.isRoundVideo();
    }
    public static boolean canEdit(int account, ArrayList<MessageObject> messages) {
        if (!enabled() || messages == null || messages.isEmpty()) return false;
        for (MessageObject message : messages) if (!supported(account, message)) return false;
        return true;
    }
    public static final class EditedMessage extends MessageObject {
        public final MessageObject source;
        @Override public boolean needResendWhenEdit() { return false; }
        @Override public boolean canEditMedia() { return false; }
        EditedMessage(int account, TLRPC.Message copy, MessageObject source) {
            super(account, copy, true, true); this.source = source;
        }
    }
    public static boolean isEditedBatch(ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) return false;
        for (MessageObject message : messages) if (!(message instanceof EditedMessage)) return false;
        return true;
    }
    static TLRPC.Message copy(MessageObject source) throws Exception {
        NativeByteBuffer buffer = new NativeByteBuffer(source.messageOwner.getObjectSize());
        try {
            source.messageOwner.serializeToStream(buffer); buffer.rewind();
            TLRPC.Message copy = TLRPC.Message.TLdeserialize(buffer, buffer.readInt32(true), true);
            copy.attachPath = source.messageOwner.attachPath;
            copy.fwd_from = null; copy.flags &= ~TLRPC.MESSAGE_FLAG_FWD;
            copy.reply_markup = null;
            return copy;
        } finally { buffer.reuse(); }
    }
    public interface Apply { void apply(ArrayList<MessageObject> messages); }

    public static ArrayList<MessageObject> copies(int account, ArrayList<MessageObject> sources) throws Exception {
        ArrayList<MessageObject> result = new ArrayList<>();
        for (MessageObject source : sources) {
            result.add(new EditedMessage(account, copy(source), source instanceof EditedMessage ? ((EditedMessage) source).source : source));
        }
        return result;
    }

    public static EditedMessage withText(int account, EditedMessage source, CharSequence text,
            ArrayList<TLRPC.MessageEntity> entities) throws Exception {
        TLRPC.Message message = copy(source);
        message.message = text.toString();
        message.entities = entities == null ? new ArrayList<>() : entities;
        if (message.entities.isEmpty()) message.flags &= ~TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
        else message.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
        return new EditedMessage(account, message, source.source);
    }

    /** Native caption field, not a dialog. Nothing is sent by its checkmark. */
    public static void edit(BaseFragment fragment, ChatActivityEnterView composer, int account,
            ArrayList<MessageObject> sources, Apply apply) {
        if (!canEdit(account, sources) || fragment.getParentActivity() == null) return;
        try {
            editNext(fragment, composer, account, copies(account, sources), 0, apply);
        } catch (Exception error) {
            NebulaProtectedCopies.error(NebulaText.text("Не удалось открыть копию", "Could not open a copy"));
        }
    }

    private static void editNext(BaseFragment fragment, ChatActivityEnterView composer, int account,
            ArrayList<MessageObject> copies, int index, Apply apply) {
        if (fragment.getParentActivity() == null || !canEdit(account, copies)) return;
        if (index == copies.size()) { apply.apply(copies); return; }
        composer.editNebulaForwardCopy((EditedMessage) copies.get(index), edited -> {
            copies.set(index, edited);
            editNext(fragment, composer, account, copies, index + 1, apply);
        });
    }
    /** Called only by the existing Send button, after native paid-message confirmation. */
    public static int sendBatch(SendMessagesHelper helper, int account, ArrayList<MessageObject> messages, long peer,
            boolean hideCaption, boolean notify, int date, int repeat, MessageObject topic, long stars,
            long monoPeer, MessageSuggestionParams suggestion, boolean photo, boolean video, boolean document,
            boolean music, boolean stickers) {
        // Validate the entire batch before sending anything, so albums cannot get stranded on a banned last item.
        for (MessageObject message : messages) {
            if (!supported(account, message)) return 17;
            if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && !photo) return 10;
            if (message.isVideo() && !video) return 9;
            if (message.isMusic() && !music) return 19;
            if (message.isGif() && !stickers) return 4;
            if (message.messageOwner.media instanceof TLRPC.TL_messageMediaDocument && !message.isVideo()
                    && !message.isMusic() && !message.isGif() && !document) return 17;
        }
        return NebulaProtectedCopies.prepareUploads(account, messages, prepared ->
                sendPreparedBatch(helper, prepared, peer, hideCaption, notify, date, repeat, topic, stars, monoPeer, suggestion));
    }

    private static void sendPreparedBatch(SendMessagesHelper helper, ArrayList<MessageObject> messages, long peer,
            boolean hideCaption, boolean notify, int date, int repeat, MessageObject topic, long stars,
            long monoPeer, MessageSuggestionParams suggestion) {
        HashMap<Long, Long> groups = new HashMap<>();
        HashMap<Long, Integer> last = new HashMap<>();
        for (int i = 0; i < messages.size(); i++) {
            long group = messages.get(i).getGroupId();
            if (group != 0) { if (!groups.containsKey(group)) groups.put(group, Utilities.random.nextLong()); last.put(group, i); }
        }
        for (int i = 0; i < messages.size(); i++) {
            EditedMessage edited = (EditedMessage) messages.get(i); TLRPC.Message m = edited.messageOwner;
            String caption = hideCaption && edited.type != MessageObject.TYPE_TEXT ? "" : m.message;
            ArrayList<TLRPC.MessageEntity> entities = hideCaption && edited.type != MessageObject.TYPE_TEXT ? new ArrayList<>() : m.entities;
            HashMap<String, String> params = new HashMap<>();
            long group = edited.getGroupId();
            if (group != 0) { params.put("groupId", String.valueOf(groups.get(group))); if (last.get(group) == i) params.put("final", "1"); }
            SendMessagesHelper.SendMessageParams p;
            if (m.media != null && m.media.photo instanceof TLRPC.TL_photo) {
                p = SendMessagesHelper.SendMessageParams.of((TLRPC.TL_photo) m.media.photo, null, peer, null, topic, caption, entities, null, params, notify, date, repeat, 0, edited.source, false, edited.hasMediaSpoilers());
            } else if (m.media != null && m.media.document instanceof TLRPC.TL_document) {
                p = SendMessagesHelper.SendMessageParams.of((TLRPC.TL_document) m.media.document, null, m.attachPath, peer, null, topic, caption, entities, null, params, notify, date, repeat, 0, edited.source, null, false, edited.hasMediaSpoilers());
            } else {
                p = SendMessagesHelper.SendMessageParams.of(caption, peer, null, topic, null, true, entities, null, null, notify, date, repeat, null, false);
            }
            p.payStars = stars; p.monoForumPeer = monoPeer; p.suggestionParams = suggestion; p.invert_media = m.invert_media;
            helper.sendMessage(p);
        }
    }
    public static CharSequence draft(MessageObject object, android.graphics.Paint.FontMetricsInt metrics) throws Exception {
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        for (TLRPC.MessageEntity entity : object.messageOwner.entities == null ? new ArrayList<TLRPC.MessageEntity>() : object.messageOwner.entities) {
            // The upstream formatter sorts entities and can adjust mention lengths.
            // Deep-copy, not merely the list, so the source message stays untouched.
            NativeByteBuffer buffer = new NativeByteBuffer(entity.getObjectSize());
            try {
                entity.serializeToStream(buffer); buffer.rewind();
                entities.add(TLRPC.MessageEntity.TLdeserialize(buffer, buffer.readInt32(true), true));
            } finally { buffer.reuse(); }
        }
        return ChatActivityEnterView.applyMessageEntities(entities, object.messageOwner.message, metrics);
    }
}
