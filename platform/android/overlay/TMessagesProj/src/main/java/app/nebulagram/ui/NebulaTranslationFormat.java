package app.nebulagram.ui;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Maps UTF-16 style boundaries through translation without guessing word offsets. */
public final class NebulaTranslationFormat {
    public interface Translator {
        String translate(String text) throws Exception;
        default String translateStructured(String text) throws Exception { return translate(text); }
    }
    public static final String BOUNDARY_INSTRUCTIONS = " Keep every numbered triple-bracket delimiter from the input exactly once, in its original order and at its text boundary. Rewrite only text between delimiters; do not add delimiters.";
    public static final class Range {
        public final int start, end;
        public final boolean protectedText;
        public Range(int start, int end, boolean protectedText) {
            this.start = start; this.end = end; this.protectedText = protectedText;
        }
    }
    public static final class Result {
        public final String text;
        private final Map<Integer, Integer> offsets;
        Result(String text, Map<Integer, Integer> offsets) { this.text = text; this.offsets = offsets; }
        public int offset(int sourceOffset) { return offsets.getOrDefault(sourceOffset, -1); }
    }
    // Keep emoji sequences and textual addresses intact, including plain drafts
    // whose Telegram entities have not been extracted yet.
    private static final Pattern PROTECTED = Pattern.compile(
        "\\r\\n|[\\r\\n]|(?:https?://|www\\.)[^\\s<>]+|[\\w.+-]+@[\\w.-]+\\.[\\p{L}]{2,}|(?<![\\p{L}\\p{N}_])[@#][\\p{L}\\p{N}_]+"
        + "|[0-9#*]\\uFE0F?\\u20E3"
        + "|(?:[\\x{1F1E6}-\\x{1F1FF}]{1,2})"
        + "|[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2300}-\\x{23FF}©®™]"
        + "(?:[\\uFE0E\\uFE0F\\x{1F3FB}-\\x{1F3FF}\\x{E0020}-\\x{E007F}]|\\u200D[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2300}-\\x{23FF}])*"
    );
    private NebulaTranslationFormat() { }
    public static boolean valid(String text, int start, int end) {
        return start >= 0 && end > start && end <= text.length()
            && boundary(text, start) && boundary(text, end);
    }
    private static boolean boundary(String text, int offset) {
        return offset <= 0 || offset >= text.length()
            || !Character.isHighSurrogate(text.charAt(offset - 1)) || !Character.isLowSurrogate(text.charAt(offset));
    }
    public static Result translate(String source, List<Range> styles, Translator engine,
                                   boolean structured, int budget) throws Exception {
        ArrayList<Range> ranges = new ArrayList<>();
        TreeSet<Integer> cuts = new TreeSet<>(Arrays.asList(0, source.length()));
        for (Range range : styles) if (valid(source, range.start, range.end)) ranges.add(range);
        Matcher matcher = PROTECTED.matcher(source);
        while (matcher.find()) ranges.add(new Range(matcher.start(), matcher.end(), true));
        for (Range range : ranges) { cuts.add(range.start); cuts.add(range.end); }
        ArrayList<Integer> points = new ArrayList<>(cuts);
        ArrayList<String> pieces = new ArrayList<>();
        ArrayList<Boolean> locked = new ArrayList<>();
        for (int i = 0; i + 1 < points.size(); i++) {
            int start = points.get(i), end = points.get(i + 1);
            pieces.add(source.substring(start, end));
            boolean protect = source.substring(start, end).trim().isEmpty();
            for (Range range : ranges) if (range.protectedText && range.start <= start && range.end >= end) protect = true;
            locked.add(protect);
        }
        String[] translated = null;
        // A validated single request keeps sentence context across style runs.
        // Ordinary on-device engines receive plain runs, never markup.
        if (structured && pieces.size() > 1 && locked.contains(false)) {
            String prefix = "[[[N";
            while (source.contains(prefix)) prefix += "N";
            StringBuilder payload = new StringBuilder();
            for (int i = 0; i < pieces.size(); i++) payload.append(prefix).append(i).append("]]]").append(pieces.get(i));
            payload.append(prefix).append(pieces.size()).append("]]]");
            if (payload.length() <= budget) translated = parse(engine.translateStructured(payload.toString()), prefix, pieces, locked);
        }
        if (translated == null) {
            translated = new String[pieces.size()];
            for (int i = 0; i < pieces.size(); i++) {
                String piece = pieces.get(i);
                translated[i] = locked.get(i) ? piece : NebulaTranslationText.surround(piece, checkedPlain(piece, engine.translate(piece)));
            }
        }
        HashMap<Integer, Integer> offsets = new HashMap<>();
        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < pieces.size(); i++) {
            offsets.put(points.get(i), answer.length()); answer.append(translated[i]);
        }
        offsets.put(source.length(), answer.length());
        return new Result(answer.toString(), offsets);
    }
    public static String checkedPlain(String source, String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalStateException("EMPTY_TRANSLATION");
        // Reject leaked protocol examples without stripping text authored by the user.
        Pattern marker = Pattern.compile("\\[\\[\\[N[^\\]]*\\]\\]\\]");
        HashMap<String, Integer> available = new HashMap<>();
        Matcher original = marker.matcher(source);
        while (original.find()) available.merge(original.group(), 1, Integer::sum);
        Matcher output = marker.matcher(value);
        while (output.find()) {
            String token = output.group();
            int count = available.getOrDefault(token, 0);
            if (count == 0) throw new IllegalStateException("INVALID_TRANSLATION_FORMAT");
            available.put(token, count - 1);
        }
        return value;
    }
    private static String[] parse(String answer, String prefix, List<String> pieces, List<Boolean> locked) {
        if (answer == null) return null;
        answer = answer.trim();
        String[] result = new String[pieces.size()];
        int offset = 0;
        for (int i = 0; i <= pieces.size(); i++) {
            String marker = prefix + i + "]]]";
            if (!answer.startsWith(marker, offset) || answer.indexOf(marker, offset + marker.length()) >= 0) return null;
            offset += marker.length();
            if (i == pieces.size()) return offset == answer.length() ? result : null;
            int next = answer.indexOf(prefix, offset);
            if (next < 0) return null;
            String part = answer.substring(offset, next);
            if (part.contains("[[[") || !locked.get(i) && part.trim().isEmpty()) return null;
            result[i] = locked.get(i) ? pieces.get(i) : NebulaTranslationText.surround(pieces.get(i), part);
            offset = next;
        }
        return null;
    }
}
