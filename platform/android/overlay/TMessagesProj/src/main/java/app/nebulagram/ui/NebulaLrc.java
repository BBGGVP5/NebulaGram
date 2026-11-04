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
        Line(long time, String text) { this.time = time; this.text = text; }
    }

    public final List<Line> lines;
    public final boolean synced;

    private NebulaLrc(List<Line> lines, boolean synced) {
        this.lines = Collections.unmodifiableList(lines); this.synced = synced;
    }

    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?\\]");
    private static final Pattern TAG = Pattern.compile("^\\[([a-zA-Z#]+):(.*)\\]\\s*$");
    private static final Pattern WORD = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");

    public static NebulaLrc parse(String source) {
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
            for (long stamp : stamps) timed.add(new Line(stamp, text));
        }
        if (timed.isEmpty()) return new NebulaLrc(plain, false);
        // A positive [offset] shows lyrics earlier, as in the LRC convention.
        ArrayList<Line> shifted = new ArrayList<>(timed.size());
        for (Line line : timed) shifted.add(new Line(Math.max(0, line.time - offset), line.text));
        shifted.sort((a, b) -> Long.compare(a.time, b.time));
        return new NebulaLrc(shifted, true);
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
