package app.nebulagram.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** LRC lyrics: timed lines sorted by time, or untimed lines when only plain text exists. */
public final class NebulaLrc {
    public static final class Line {
        /** Start in milliseconds, or -1 for plain lyrics. */
        public final long time;
        public final String text;
        /** Word starts from enhanced LRC ("<00:12.34>word"): times in ms and offsets in text; empty without them. */
        public final long[] wordTimes;
        public final int[] wordOffsets;
        /** 0 the main voice, 1 the second duet voice (drawn on the opposite side), 2 both together. */
        public final int voice;
        Line(long time, String text) { this(time, text, new long[0], new int[0], 0); }
        Line(long time, String text, long[] wordTimes, int[] wordOffsets, int voice) {
            this.time = time; this.text = text; this.wordTimes = wordTimes; this.wordOffsets = wordOffsets; this.voice = voice;
        }
        Line shifted(long offset) {
            long[] times = wordTimes.clone();
            for (int i = 0; i < times.length; i++) times[i] = Math.max(0, times[i] - offset);
            return new Line(Math.max(0, time - offset), text, times, wordOffsets, voice);
        }
        public boolean hasWords() { return wordTimes.length > 0; }
    }

    public final List<Line> lines;
    public final boolean synced;

    private NebulaLrc(List<Line> lines, boolean synced) {
        this.lines = Collections.unmodifiableList(lines); this.synced = synced;
    }

    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern TAG = Pattern.compile("^\\[([a-zA-Z#]+):(.*)\\]\\s*$");
    private static final Pattern WORD = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");
    private static final Pattern WORD_TIME = Pattern.compile("<(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?>");
    /** Duet markers some LRC files put before a line: "v1:", "v2:", "v3:" or "M:", "F:", "D:". */
    private static final Pattern VOICE = Pattern.compile("^(v[123]|[FMD]):\\s*");

    public static NebulaLrc parse(String source) { return parse(source, false); }

    /**
     * {@code rich} also keeps enhanced-LRC word times and duet markers ("Word sync" in the player).
     * Without it the result is exactly the classic line-by-line parse.
     */
    public static NebulaLrc parse(String source, boolean rich) {
        ArrayList<Line> timed = new ArrayList<>();
        ArrayList<Line> plain = new ArrayList<>();
        long offset = 0;
        if (source == null) source = "";
        for (String raw : source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            String line = raw.startsWith("﻿") ? raw.substring(1) : raw;
            Matcher tag = TAG.matcher(line.trim());
            if (tag.matches() && !TIME.matcher(line.trim()).lookingAt()) {
                if ("offset".equalsIgnoreCase(tag.group(1))) {
                    try { offset = Long.parseLong(tag.group(2).trim().replace("+", "")); } catch (NumberFormatException ignore) { }
                }
                continue;
            }
            Matcher time = TIME.matcher(line);
            ArrayList<Long> stamps = new ArrayList<>();
            int end = 0;
            while (time.find() && time.start() == end) {
                long minutes = Long.parseLong(time.group(1)), seconds = Long.parseLong(time.group(2));
                String fraction = time.group(3);
                long millis = 0;
                if (fraction != null) {
                    millis = Long.parseLong(fraction);
                    if (fraction.length() == 1) millis *= 100; else if (fraction.length() == 2) millis *= 10;
                }
                if (seconds < 60) stamps.add(minutes * 60000 + seconds * 1000 + millis);
                end = time.end();
            }
            String text = WORD.matcher(line.substring(end)).replaceAll("").trim();
            if (stamps.isEmpty()) {
                if (end == 0 && !line.trim().isEmpty()) plain.add(new Line(-1, line.trim()));
                continue;
            }
            if (rich) {
                for (long stamp : stamps) timed.add(richLine(stamp, line.substring(end)));
                continue;
            }
            for (long stamp : stamps) timed.add(new Line(stamp, text));
        }
        if (timed.isEmpty()) return new NebulaLrc(plain, false);
        // A positive [offset] shows lyrics earlier, as in the LRC convention.
        ArrayList<Line> shifted = new ArrayList<>(timed.size());
        for (Line line : timed) shifted.add(line.shifted(offset));
        shifted.sort((a, b) -> Long.compare(a.time, b.time));
        return new NebulaLrc(shifted, true);
    }

    private static long millis(String minutes, String seconds, String fraction) {
        long value = Long.parseLong(minutes) * 60000 + Long.parseLong(seconds) * 1000;
        if (fraction != null) {
            long part = Long.parseLong(fraction);
            value += fraction.length() == 1 ? part * 100 : fraction.length() == 2 ? part * 10 : part;
        }
        return value;
    }

    /** Text without word tags; each tag's time is attached to the offset where its word starts. */
    static Line richLine(long stamp, String body) {
        String rest = body.trim();
        int voice = 0;
        Matcher marker = VOICE.matcher(rest);
        if (marker.find()) {
            String tag = marker.group(1);
            voice = tag.equals("v2") || tag.equals("F") ? 1 : tag.equals("v3") || tag.equals("D") ? 2 : 0;
            rest = rest.substring(marker.end());
        }
        StringBuilder text = new StringBuilder();
        java.util.ArrayList<Long> times = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> offsets = new java.util.ArrayList<>();
        Matcher word = WORD_TIME.matcher(rest);
        int from = 0;
        while (word.find()) {
            text.append(rest, from, word.start());
            from = word.end();
            if (Long.parseLong(word.group(2)) >= 60) continue;
            // A tag with nothing after it only marks when the last word stops.
            if (rest.substring(word.end()).trim().isEmpty()) continue;
            times.add(millis(word.group(1), word.group(2), word.group(3)));
            offsets.add(text.length());
        }
        text.append(rest, from, rest.length());
        String raw = text.toString();
        int lead = 0;
        while (lead < raw.length() && Character.isWhitespace(raw.charAt(lead))) lead++;
        String trimmed = raw.trim();
        long[] t = new long[times.size()];
        int[] o = new int[offsets.size()];
        for (int i = 0; i < t.length; i++) {
            t[i] = times.get(i);
            int at = offsets.get(i) - lead;
            // The word starts after any spaces that followed the tag.
            while (at >= 0 && at < trimmed.length() && Character.isWhitespace(trimmed.charAt(at))) at++;
            o[i] = Math.max(0, Math.min(trimmed.length(), at));
        }
        return new Line(stamp, trimmed, t, o, voice);
    }

    /**
     * Characters sung by {@code position} ms on a line with word times: whole words up to the current
     * one, plus the share of the current word that its duration has covered. -1 before the first word.
     */
    public static float sung(Line line, long position, long lineEnd) {
        if (line == null || !line.hasWords() || position < line.wordTimes[0]) return -1f;
        int n = line.wordTimes.length, i = n - 1;
        while (i > 0 && line.wordTimes[i] > position) i--;
        int start = line.wordOffsets[i];
        int end = i + 1 < n ? line.wordOffsets[i + 1] : line.text.length();
        long from = line.wordTimes[i];
        long to = i + 1 < n ? line.wordTimes[i + 1] : Math.max(from + 1, lineEnd > from ? Math.min(lineEnd, from + 1500) : from + 600);
        float share = to <= from ? 1f : Math.max(0f, Math.min(1f, (position - from) / (float) (to - from)));
        return start + (end - start) * share;
    }

    /** Ranges of background vocals: text in parentheses, as most synced lyrics write them. */
    public static int[] backgroundRanges(String text) {
        java.util.ArrayList<Integer> out = new java.util.ArrayList<>();
        int depth = 0, start = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') { if (depth++ == 0) start = i; }
            else if (c == ')' && depth > 0 && --depth == 0) { out.add(start); out.add(i + 1); }
        }
        int[] ranges = new int[out.size()];
        for (int i = 0; i < ranges.length; i++) ranges[i] = out.get(i);
        return ranges;
    }

    public static NebulaLrc plain(String text) {
        ArrayList<Line> lines = new ArrayList<>();
        if (text != null) for (String line : text.replace("\r\n", "\n").split("\n")) lines.add(new Line(-1, line.trim()));
        while (!lines.isEmpty() && lines.get(lines.size() - 1).text.isEmpty()) lines.remove(lines.size() - 1);
        return new NebulaLrc(lines, false);
    }

    public boolean isEmpty() {
        for (Line line : lines) if (!line.text.isEmpty()) return false;
        return true;
    }

    /** Index of the line playing at {@code position} ms, or -1 before the first line or for plain lyrics. */
    public int index(long position) {
        if (!synced || lines.isEmpty() || position < lines.get(0).time) return -1;
        int low = 0, high = lines.size() - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (lines.get(middle).time <= position) low = middle; else high = middle - 1;
        }
        return low;
    }
}
