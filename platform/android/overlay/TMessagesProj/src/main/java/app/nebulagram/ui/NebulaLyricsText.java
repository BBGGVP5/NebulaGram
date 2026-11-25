package app.nebulagram.ui;

/** Text decisions for lyrics that do not need Android. */
public final class NebulaLyricsText {
    private NebulaLyricsText() { }

    /** Consecutive line ranges [start, end) whose text joined with newlines fits {@code limit} characters. */
    public static java.util.List<int[]> chunks(java.util.List<String> lines, int limit) {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        int start = 0, length = 0;
        for (int i = 0; i < lines.size(); i++) {
            int add = lines.get(i).length() + (i > start ? 1 : 0);
            if (i > start && length + add > limit) { out.add(new int[]{start, i}); start = i; length = lines.get(i).length(); }
            else length += add;
        }
        if (start < lines.size()) out.add(new int[]{start, lines.size()});
        return out;
    }

    /** A translated chunk back into lines, or null when the service merged or split lines. */
    public static String[] split(String translated, int expected) {
        if (translated == null) return null;
        String[] parts = translated.replace("\r\n", "\n").split("\n", -1);
        if (parts.length == expected + 1 && parts[expected].trim().isEmpty()) parts = java.util.Arrays.copyOf(parts, expected);
        return parts.length == expected ? parts : null;
    }

    /** A translation worth showing: not empty and not just the same words. */
    public static boolean useful(String original, String translated) {
        if (translated == null) return false;
        String a = original == null ? "" : original.trim(), b = translated.trim();
        return !b.isEmpty() && !b.equalsIgnoreCase(a);
    }

    /** A line deserves a Latin reading when it has letters of another script (Cyrillic, kana, hangul…). */
    public static boolean needsReading(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); ) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            if (!Character.isLetter(c)) continue;
            if (Character.UnicodeScript.of(c) != Character.UnicodeScript.LATIN) return true;
        }
        return false;
    }
}
