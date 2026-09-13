package app.nebulagram.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.function.Consumer;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;

/** Ordinary local copies, never messages.forwardMessages with a protected source. */
public final class NebulaProtectedCopies {
    private NebulaProtectedCopies() { }

    public static boolean isProtected(int account, MessageObject object) {
        if (object instanceof NebulaForwardEditing.EditedMessage)
            object = ((NebulaForwardEditing.EditedMessage) object).source;
        if (object.messageOwner.noforwards) return true;
        long peer = object.getDialogId();
        MessagesController controller = MessagesController.getInstance(account);
        if (peer < 0) {
            TLRPC.Chat chat = controller.getChat(-peer);
            return chat != null && chat.noforwards;
        }
        TLRPC.UserFull user = controller.getUserFull(peer);
        return user != null && (user.noforwards_my_enabled || user.noforwards_peer_enabled);
    }

    public static boolean requiresCopy(int account, ArrayList<MessageObject> messages) {
        if (!NebulaContentProtection.enabled()) return false;
        for (MessageObject message : messages) if (isProtected(account, message)) return true;
        return false;
    }

    public static ArrayList<MessageObject> create(int account, ArrayList<MessageObject> messages,
            boolean fromMyName, boolean hideCaption) throws Exception {
        for (MessageObject message : messages) {
            if (!NebulaForwardEditing.supported(account, message))
                throw new IllegalArgumentException(NebulaText.text("Этот тип сообщения нельзя отправить копией", "This message type cannot be sent as a copy"));
        }
        ArrayList<MessageObject> result = NebulaForwardEditing.copies(account, messages);
        for (int i = 0; i < result.size(); i++) {
            MessageObject object = result.get(i);
            TLRPC.Message m = object.messageOwner;
            if (hideCaption && object.type != MessageObject.TYPE_TEXT) {
                m.message = ""; m.entities.clear();
            }
            // Explicit source text, not a forged Telegram forwarding header.
            if (!fromMyName) {
                long peer = messages.get(i).getDialogId();
                MessagesController controller = MessagesController.getInstance(account);
                String name;
                if (peer < 0) {
                    TLRPC.Chat chat = controller.getChat(-peer);
                    name = chat == null ? null : chat.title;
                } else {
                    TLRPC.User user = controller.getUser(peer);
                    name = user == null ? null : UserObject.getUserName(user);
                }
                if (name != null && !name.isEmpty()) {
                    String prefix = NebulaText.text("Источник: ", "Source: ") + name + "\n\n";
                    for (TLRPC.MessageEntity entity : m.entities) entity.offset += prefix.length();
                    m.message = prefix + m.message;
                }
            }
            int limit = object.type == MessageObject.TYPE_TEXT ? MessagesController.getInstance(account).maxMessageLength
                    : MessagesController.getInstance(account).getCaptionMaxLengthLimit();
            if (m.message.codePointCount(0, m.message.length()) > limit)
                throw new IllegalArgumentException(NebulaText.text("Копия с источником слишком длинная. Скрыть автора или сократить текст.", "The copy with its source is too long. Hide the author or shorten the text."));
        }
        return result;
    }

    private static File cachedFile(int account, MessageObject message) {
        TLRPC.Message m = message.messageOwner;
        File file = FileLoader.getInstance(account).getPathToMessage(m);
        if (m.attachPath != null && !m.attachPath.isEmpty()) {
            File attached = new File(m.attachPath);
            if (attached.isFile()) file = attached;
        }
        long expected = m.media != null && m.media.document != null ? m.media.document.size : 0;
        if (m.media != null && m.media.photo != null) {
            TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(m.media.photo.sizes, AndroidUtilities.getPhotoSize(true), false, null, true);
            if (size != null) expected = size.size;
        }
        return file != null && file.isFile() && file.length() > 0 && (expected <= 0 || file.length() >= expected) ? file : null;
    }

    /** Preflight the whole album before accepting the send; decoding runs off the UI thread. */
    static int prepareUploads(int account, ArrayList<MessageObject> messages, Consumer<ArrayList<MessageObject>> send) {
        ArrayList<File> paths = new ArrayList<>();
        boolean upload = false;
        for (MessageObject message : messages) {
            File path = null;
            if (isProtected(account, message) && message.type != MessageObject.TYPE_TEXT) {
                path = cachedFile(account, message);
                if (path == null) {
                    error(NebulaText.text("Сначала полностью загрузите все вложения для отправки копии", "Download all attachments completely before sending a copy"));
                    return 17;
                }
                upload = true;
            }
            paths.add(path);
        }
        if (!upload) { send.accept(messages); return 0; }
        final long userId = UserConfig.getInstance(account).getClientUserId();
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<File> staging = new ArrayList<>();
            try {
                ArrayList<MessageObject> prepared = NebulaForwardEditing.copies(account, messages);
                for (int i = 0; i < prepared.size(); i++) {
                    File path = paths.get(i);
                    if (path == null) continue;
                    TLRPC.Message m = prepared.get(i).messageOwner;
                    if (m.media.photo != null) {
                        // New sizes/cache locations: never rename or mutate the original photo's cache.
                        TLRPC.TL_photo photo = SendMessagesHelper.getInstance(account).generatePhotoSizes(path.getAbsolutePath(), null);
                        if (photo == null) throw new IllegalStateException("photo preparation failed");
                        m.media.photo = photo;
                    } else if (m.media.document != null) {
                        // Native upload completion may rename attachPath. Do not give it
                        // the original message's cache file (or a user's external file).
                        File uploadFile = File.createTempFile("nebula-forward-", ".tmp", FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE));
                        staging.add(uploadFile);
                        if (!AndroidUtilities.copyFile(path, uploadFile) || uploadFile.length() != path.length())
                            throw new IllegalStateException("document preparation failed");
                        m.media.document.id = 0;
                        m.media.document.access_hash = 0;
                        m.media.document.file_reference = new byte[0];
                        m.attachPath = uploadFile.getAbsolutePath();
                    }
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (!UserConfig.getInstance(account).isClientActivated() || UserConfig.getInstance(account).getClientUserId() != userId || !NebulaContentProtection.enabled()) {
                        discardStaging(staging);
                        error(NebulaText.text("Отправка копии отменена", "Copy sending cancelled"));
                        return;
                    }
                    send.accept(prepared);
                });
            } catch (Exception error) {
                discardStaging(staging);
                error(NebulaText.text("Не удалось подготовить вложения. Сообщения не отправлены.", "Could not prepare attachments. No messages were sent."));
            }
        });
        return 0;
    }

    private static void discardStaging(ArrayList<File> staging) {
        // Exact files created by this operation only; native transport owns them after enqueue.
        for (File file : staging) file.delete();
    }

    public static void error(String text) {
        AndroidUtilities.runOnUIThread(() -> android.widget.Toast.makeText(ApplicationLoader.applicationContext, text, android.widget.Toast.LENGTH_LONG).show());
    }
}
