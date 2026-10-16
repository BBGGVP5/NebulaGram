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
            int run = -1;
            int previousStart = -1, previousEnd = -1;
            // One pass also bounds work for pasted words with thousands of combining
            // marks. Rechecking each mark's base character makes that case quadratic.
            for (int index = 0; index <= text.length(); ) {
                int point = index < text.length() ? Character.codePointAt(text, index) : -1;
                boolean base = Character.isLetterOrDigit(point) || point == '_';
                int type = point < 0 ? -1 : Character.getType(point);
                boolean mark = type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK;
                if (base && run < 0) run = index;
                if (!base && !(mark && run >= 0) && run >= 0) {
                    if (run <= cursor && cursor <= index) return new int[]{run, index};
                    if (run > cursor) break;
                    previousStart = run; previousEnd = index;
                    run = -1;
                }
                if (index == text.length()) break;
                index += Character.charCount(point);
            }
            return previousStart < 0 ? null : new int[]{previousStart, previousEnd};
        }
        if (start > 0 && start < text.length() && Character.isLowSurrogate(text.charAt(start))
                && Character.isHighSurrogate(text.charAt(start - 1))) start--;
        if (end > 0 && end < text.length() && Character.isLowSurrogate(text.charAt(end))
                && Character.isHighSurrogate(text.charAt(end - 1))) end++;
        return new int[]{start, end};
    }
}
