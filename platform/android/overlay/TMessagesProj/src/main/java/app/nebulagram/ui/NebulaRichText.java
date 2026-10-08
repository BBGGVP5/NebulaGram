package app.nebulagram.ui;

import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import org.telegram.messenger.*;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Telegram entities remain attached to text through tools, caches and translation. */
public final class NebulaRichText {
    private NebulaRichText() { }
    public static CharSequence snapshot(CharSequence text) {
        return new SpannableStringBuilder(text == null ? "" : text);
    }
    public static TLRPC.TL_textWithEntities capture(int account, CharSequence value) {
        CharSequence[] text = {snapshot(value)};
        ArrayList<TLRPC.MessageEntity> entities = MediaDataController.getInstance(account).getEntities(text, true);
        return copy(text[0].toString(), entities);
    }
    public static TLRPC.TL_textWithEntities copy(String text, ArrayList<TLRPC.MessageEntity> entities) {
        TLRPC.TL_textWithEntities result = new TLRPC.TL_textWithEntities();
        result.text = text == null ? "" : text;
        if (entities != null) for (TLRPC.MessageEntity entity : entities) {
            if (NebulaTranslationFormat.valid(result.text, entity.offset, entity.offset + entity.length)) result.entities.add(clone(entity));
        }
        return result;
    }
    private static TLRPC.MessageEntity clone(TLRPC.MessageEntity entity) {
        SerializedData writer = new SerializedData(entity.getObjectSize());
        entity.serializeToStream(writer);
        SerializedData reader = new SerializedData(writer.toByteArray());
        try { return TLRPC.MessageEntity.TLdeserialize(reader, reader.readInt32(true), true); }
        finally { writer.cleanup(); reader.cleanup(); }
    }
    public static String key(TLRPC.TL_textWithEntities text) {
        SerializedData writer = new SerializedData(text.getObjectSize());
        try {
            text.serializeToStream(writer);
            return android.util.Base64.encodeToString(writer.toByteArray(), android.util.Base64.NO_WRAP);
        } finally { writer.cleanup(); }
    }
    public static String key(int account, CharSequence text) { return key(capture(account, text)); }
    public static boolean same(TLRPC.TL_textWithEntities first, TLRPC.TL_textWithEntities second) {
        return first != null && second != null && key(first).equals(key(second));
    }
    public static CharSequence render(TLRPC.TL_textWithEntities value, Paint.FontMetricsInt metrics) {
        CharSequence text = new SpannableStringBuilder(value.text);
        MessageObject.addEntitiesToText(text, value.entities, true, true, false, false);
        text = Emoji.replaceEmoji(text, metrics, false, null);
        return MessageObject.replaceAnimatedEmoji(text, value.entities, metrics);
    }
    public static TLRPC.TL_textWithEntities translate(NebulaTranslationClient client, TLRPC.TL_textWithEntities source,
                                                   String language, boolean interactive, Consumer<String> progress) throws Exception {
        ArrayList<NebulaTranslationFormat.Range> ranges = new ArrayList<>();
        for (TLRPC.MessageEntity entity : source.entities) ranges.add(new NebulaTranslationFormat.Range(
            entity.offset, entity.offset + entity.length, protectedEntity(entity)));
        boolean local = NebulaTranslationSettings.local();
        int budget = NebulaTranslationSettings.global().getInt("provider", 0) == NebulaAiClient.NANO ? 900 : 3500;
        NebulaTranslationFormat.Result result = NebulaTranslationFormat.translate(source.text, ranges,
            text -> client.translate(text, language, interactive, progress), !local, budget);
        return remap(source, result);
    }
    public static TLRPC.TL_textWithEntities transform(TLRPC.TL_textWithEntities source,
                                                    NebulaTranslationFormat.Translator operation, boolean structured) throws Exception {
        ArrayList<NebulaTranslationFormat.Range> ranges = new ArrayList<>();
        for (TLRPC.MessageEntity entity : source.entities) ranges.add(new NebulaTranslationFormat.Range(
            entity.offset, entity.offset + entity.length, protectedEntity(entity)));
        return remap(source, NebulaTranslationFormat.translate(source.text, ranges, operation, structured, 3500));
    }
    private static TLRPC.TL_textWithEntities remap(TLRPC.TL_textWithEntities source, NebulaTranslationFormat.Result result) {
        TLRPC.TL_textWithEntities answer = new TLRPC.TL_textWithEntities(); answer.text = result.text;
        for (TLRPC.MessageEntity entity : source.entities) {
            int start = result.offset(entity.offset), end = result.offset(entity.offset + entity.length);
            if (start < 0 || end <= start) continue;
            TLRPC.MessageEntity translated = clone(entity);
            translated.offset = start; translated.length = end - start; answer.entities.add(translated);
        }
        return answer;
    }
    private static boolean protectedEntity(TLRPC.MessageEntity entity) {
        return entity instanceof TLRPC.TL_messageEntityCustomEmoji || entity instanceof TLRPC.TL_messageEntityCode
            || entity instanceof TLRPC.TL_messageEntityPre || entity instanceof TLRPC.TL_messageEntityUrl
            || entity instanceof TLRPC.TL_messageEntityMention || entity instanceof TLRPC.TL_messageEntityEmail
            || entity instanceof TLRPC.TL_messageEntityPhone || entity instanceof TLRPC.TL_messageEntityBotCommand
            || entity instanceof TLRPC.TL_messageEntityHashtag || entity instanceof TLRPC.TL_messageEntityCashtag
            || entity instanceof TLRPC.TL_messageEntityBankCard || entity instanceof TLRPC.TL_messageEntityMentionName;
    }
}
