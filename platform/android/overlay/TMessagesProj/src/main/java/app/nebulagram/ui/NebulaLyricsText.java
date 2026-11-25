package app.nebulagram.ui;

/** Text decisions for lyrics that do not need Android. */
public final class NebulaLyricsText {
    private NebulaLyricsText() { }

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
