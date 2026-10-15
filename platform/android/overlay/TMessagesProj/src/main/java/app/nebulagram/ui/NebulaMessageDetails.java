package app.nebulagram.ui;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** A snapshot of the selected, already loaded message. No file reads or requests. */
public final class NebulaMessageDetails {
    public static final class Field {
        public final String title, value;
        Field(String title, String value) { this.title = title; this.value = value; }
    }
    private NebulaMessageDetails() {}
    public static List<Field> snapshot(MessageObject message, boolean russian) {
        ArrayList<Field> fields = new ArrayList<>();
        if (message == null || message.messageOwner == null) return Collections.emptyList();
        fields.add(new Field(russian ? "ID сообщения" : "Message ID", Integer.toString(message.getId())));
        fields.add(new Field(russian ? "ID диалога" : "Dialog ID", Long.toString(message.getDialogId())));
        if (message.getFromChatId() != 0) fields.add(new Field(russian ? "ID отправителя" : "Sender ID", Long.toString(message.getFromChatId())));
        addDate(fields, russian ? "Отправлено" : "Sent", message.messageOwner.date);
        addDate(fields, russian ? "Изменено" : "Edited", message.messageOwner.edit_date);
        if (message.messageOwner.fwd_from != null) addDate(fields, russian ? "Оригинал пересылки" : "Forwarded original", message.messageOwner.fwd_from.date);
        TLRPC.Document document = message.getDocument();
        if (document != null) {
            String name = FileLoader.getDocumentFileName(document);
            if (name != null && !name.isEmpty()) fields.add(new Field(russian ? "Имя файла" : "File name", name));
            if (document.mime_type != null && !document.mime_type.isEmpty()) fields.add(new Field("MIME", document.mime_type));
            if (document.size > 0) fields.add(new Field(russian ? "Размер файла" : "File size", AndroidUtilities.formatFileSize(document.size) + " · " + document.size + (russian ? " байт" : " bytes")));
        }
        return Collections.unmodifiableList(fields);
    }
    private static void addDate(ArrayList<Field> fields, String title, int timestamp) {
        if (timestamp <= 0) return;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault());
        fields.add(new Field(title, format.format(new Date(timestamp * 1000L))));
    }
}
