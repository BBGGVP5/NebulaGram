package app.nebulagram.ui;

import java.util.ArrayList;
import java.util.HashMap;
import android.content.Context;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.*;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.AlertDialog;
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
        if (object.getId() <= 0 || DialogObject.isEncryptedDialog(object.getDialogId()) || m.rich_message != null || m.noforwards || m.ttl_period != 0 || m.destroyTime != 0
                || object.isSecretMedia() || object.needDrawBluredPreview()
                || MessagesController.getInstance(account).isPeerNoForwards(object.getDialogId())) return false;
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
        EditedMessage(int account, TLRPC.Message copy, MessageObject source) {
            super(account, copy, true, true); this.source = source;
        }
    }
    public static boolean isEditedBatch(ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) return false;
        for (MessageObject message : messages) if (!(message instanceof EditedMessage)) return false;
        return true;
    }
    private static TLRPC.Message copy(MessageObject source) throws Exception {
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
    public static void edit(BaseFragment fragment, int account, ArrayList<MessageObject> sources, Apply apply) {
        if (!canEdit(account, sources) || fragment.getParentActivity() == null) return;
        Context context = fragment.getParentActivity();
        LinearLayout fields = new LinearLayout(context); fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), AndroidUtilities.dp(12));
        ArrayList<EditText> editors = new ArrayList<>();
        try {
            for (int i = 0; i < sources.size(); i++) {
                MessageObject source = sources.get(i);
                TextView label = new TextView(context);
                label.setText((i + 1) + " · " + (source.type == MessageObject.TYPE_TEXT ? NebulaText.text("Текст", "Text")
                        : source.isVideo() ? NebulaText.text("Видео · подпись", "Video · caption")
                        : source.messageOwner.media.photo != null ? NebulaText.text("Фото · подпись", "Photo · caption")
                        : NebulaText.text("Файл · подпись", "File · caption")));
                label.setTextColor(NebulaTheme.of(context).primary()); label.setTextSize(14);
                label.setPadding(0, AndroidUtilities.dp(16), 0, AndroidUtilities.dp(6)); fields.addView(label);
                EditText editor = new EditText(context); editor.setTextColor(NebulaTheme.of(context).onSurface());
                editor.setTextSize(16); editor.setMinLines(2); editor.setMaxLines(8);
                editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
                editor.setText(draft(source, editor.getPaint().getFontMetricsInt()));
                int limit = source.type == MessageObject.TYPE_TEXT ? MessagesController.getInstance(account).maxMessageLength
                        : MessagesController.getInstance(account).getCaptionMaxLengthLimit();
                editor.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(Math.max(editor.length(), limit))});
                fields.addView(editor, new LinearLayout.LayoutParams(-1, -2)); editors.add(editor);
            }
        } catch (Exception error) { android.widget.Toast.makeText(context, NebulaText.text("Не удалось открыть копию", "Could not open a copy"), 0).show(); return; }
        ScrollView scroll = new ScrollView(context); scroll.addView(fields);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(NebulaText.text("Изменить и отправить", "Edit and send"))
                .setMessage(NebulaText.text("Копия без автора. Вложения и альбомы сохранятся. «Применить» вернёт к предпросмотру — отправьте после проверки.",
                        "An anonymous copy. Attachments and albums are kept. Apply returns to the preview — send after reviewing."))
                .setView(scroll).setNegativeButton(NebulaText.text("Отмена", "Cancel"), null)
                .setPositiveButton(NebulaText.text("Применить", "Apply"), (d, w) -> { }).create();
        fragment.showDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!canEdit(account, sources)) { dialog.dismiss(); return; }
            try {
                ArrayList<MessageObject> copies = new ArrayList<>();
                for (int i = 0; i < sources.size(); i++) {
                    MessageObject source = sources.get(i);
                    CharSequence[] value = {editors.get(i).getText()};
                    ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(account).getEntities(value, true);
                    if (source.type == MessageObject.TYPE_TEXT && value[0].toString().trim().isEmpty()) {
                        editors.get(i).setError(NebulaText.text("Введите текст", "Enter text")); return;
                    }
                    int max = source.type == MessageObject.TYPE_TEXT ? MessagesController.getInstance(account).maxMessageLength
                            : MessagesController.getInstance(account).getCaptionMaxLengthLimit();
                    if (value[0].length() > max) {
                        editors.get(i).setError(NebulaText.text("Текст слишком длинный", "Text is too long")); return;
                    }
                    TLRPC.Message copy = copy(source); copy.message = value[0].toString();
                    copy.entities = entities == null ? new ArrayList<>() : entities;
                    if (copy.entities.isEmpty()) copy.flags &= ~TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
                    else copy.flags |= TLRPC.MESSAGE_FLAG_HAS_ENTITIES;
                    copies.add(new EditedMessage(account, copy, source instanceof EditedMessage ? ((EditedMessage) source).source : source));
                }
                apply.apply(copies); dialog.dismiss();
            } catch (Exception error) { android.widget.Toast.makeText(context, NebulaText.text("Не удалось подготовить копию", "Could not prepare a copy"), 0).show(); }
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
        return 0;
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
