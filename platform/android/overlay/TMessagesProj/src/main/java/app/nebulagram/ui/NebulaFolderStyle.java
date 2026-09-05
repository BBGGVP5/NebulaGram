package app.nebulagram.ui;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import org.telegram.messenger.Emoji;
import org.telegram.ui.Components.AnimatedEmojiSpan;

public final class NebulaFolderStyle {
    private NebulaFolderStyle() { }
    public static CharSequence title(CharSequence original, TextPaint paint) {
        int style = NebulaAppearance.folderStyle();
        if (style == 0) return original;
        CharSequence icon = null;
        if (original instanceof Spanned) {
            Spanned spans = (Spanned) original;
            Object[] emoji = spans.getSpans(0, original.length(), AnimatedEmojiSpan.class);
            if (emoji.length == 0) emoji = spans.getSpans(0, original.length(), Emoji.EmojiSpan.class);
            if (emoji.length > 0) icon = original.subSequence(spans.getSpanStart(emoji[0]), spans.getSpanEnd(emoji[0]));
        }
        if (icon == null) icon = Emoji.replaceEmoji("📁", paint.getFontMetricsInt(), false);
        return style == 1 ? icon : new SpannableStringBuilder(icon).append(" ").append(original);
    }
}
