package app.nebulagram.ui;

/** UTF-16 ranges for the native editor; never splits a surrogate pair. */
public final class NebulaFormattingSelection {
    private NebulaFormattingSelection() { }

    public static int[] range(CharSequence text, int anchor, int focus, boolean selectWord) {
        if (text == null || anchor < 0 || focus < 0 || anchor > text.length() || focus > text.length()) return null;
        int start = Math.min(anchor, focus), end = Math.max(anchor, focus);
        if (start == end && selectWord) {
            int cursor = start;
            if (cursor > 0 && cursor < text.length() && Character.isLowSurrogate(text.charAt(cursor))
                    && Character.isHighSurrogate(text.charAt(cursor - 1))) cursor--;
            if (cursor == text.length() || !wordAt(text, cursor)) {
                while (cursor > 0 && !wordAt(text, Character.offsetByCodePoints(text, cursor, -1)))
                    cursor -= Character.charCount(Character.codePointBefore(text, cursor));
            }
            start = end = cursor;
            while (start > 0 && wordAt(text, Character.offsetByCodePoints(text, start, -1)))
                start -= Character.charCount(Character.codePointBefore(text, start));
            while (end < text.length() && wordAt(text, end))
                end += Character.charCount(Character.codePointAt(text, end));
            if (start == end) return null;
        }
        if (start > 0 && start < text.length() && Character.isLowSurrogate(text.charAt(start))
                && Character.isHighSurrogate(text.charAt(start - 1))) start--;
        if (end > 0 && end < text.length() && Character.isLowSurrogate(text.charAt(end))
                && Character.isHighSurrogate(text.charAt(end - 1))) end++;
        return new int[]{start, end};
    }

    private static boolean wordAt(CharSequence text, int offset) {
        int point = Character.codePointAt(text, offset);
        while (Character.getType(point) == Character.NON_SPACING_MARK
                || Character.getType(point) == Character.COMBINING_SPACING_MARK) {
            if (offset == 0) return false;
            offset = Character.offsetByCodePoints(text, offset, -1);
            point = Character.codePointAt(text, offset);
        }
        return Character.isLetterOrDigit(point) || point == '_';
    }
}
