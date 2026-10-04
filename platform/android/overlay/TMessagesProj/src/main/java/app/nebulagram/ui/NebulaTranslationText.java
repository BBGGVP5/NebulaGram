package app.nebulagram.ui;

import java.util.ArrayList;
import java.util.List;

/** Bounded prompts; whitespace and Unicode boundaries survive recombination. */
public final class NebulaTranslationText {
    private NebulaTranslationText() { }
    public static List<String> parts(String text, int limit) {
        if (limit < 4) throw new IllegalArgumentException("Chunk limit");
        ArrayList<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + limit);
            if (end < text.length()) {
                if (Character.isLowSurrogate(text.charAt(end))) end--;
                int boundary = end;
                while (boundary > start + limit / 2 && !Character.isWhitespace(text.charAt(boundary - 1))) boundary--;
                if (boundary > start + limit / 2) end = boundary;
            }
            result.add(text.substring(start, end)); start = end;
        }
        return result;
    }
    public static String surround(String source, String translation) {
        int start = 0, end = source.length();
        while (start < end && Character.isWhitespace(source.charAt(start))) start++;
        while (end > start && Character.isWhitespace(source.charAt(end - 1))) end--;
        return source.substring(0, start) + translation.trim() + source.substring(end);
    }
}
