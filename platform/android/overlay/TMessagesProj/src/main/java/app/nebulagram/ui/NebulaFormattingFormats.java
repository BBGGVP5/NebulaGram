package app.nebulagram.ui;

import android.text.Spanned;
import android.text.style.URLSpan;

import org.telegram.ui.Components.QuoteSpan;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.utils.code.highlight.CodeHighlighting;

/** Читает форматы выделения из спанов поля ввода Telegram для подсветки панели. */
public final class NebulaFormattingFormats implements NebulaFormattingToolbar.Formats {
    @Override public int present(Spanned text, int start, int end) {
        int flags = 0;
        for (TextStyleSpan span : text.getSpans(start, end, TextStyleSpan.class)) {
            if (text.getSpanStart(span) <= start && text.getSpanEnd(span) >= end) flags |= span.getStyleFlags();
        }
        if (covered(text, start, end, URLSpan.class)) flags |= NebulaFormattingToolbar.FLAG_LINK;
        if (covered(text, start, end, QuoteSpan.class)) flags |= NebulaFormattingToolbar.FLAG_QUOTE;
        if (covered(text, start, end, CodeHighlighting.Span.class)) flags |= NebulaFormattingToolbar.FLAG_CODE;
        return flags;
    }

    private static boolean covered(Spanned text, int start, int end, Class<?> type) {
        for (Object span : text.getSpans(start, end, type)) {
            if (text.getSpanStart(span) <= start && text.getSpanEnd(span) >= end) return true;
        }
        return false;
    }
}
