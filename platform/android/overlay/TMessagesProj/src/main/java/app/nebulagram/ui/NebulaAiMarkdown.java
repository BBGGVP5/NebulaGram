package app.nebulagram.ui;

import java.util.ArrayList;
import java.util.List;

/** Small, bounded Markdown subset. Code stays literal; no HTML, remote images or executable content. */
public final class NebulaAiMarkdown {
    public static final int BOLD = 1, ITALIC = 2, CODE = 3, HEADING = 4;
    public static final class Mark {
        public final int start, end, kind;
        Mark(int start, int end, int kind) { this.start = start; this.end = end; this.kind = kind; }
    }
    public static final class Result {
        public final String text;
        public final List<Mark> marks;
        Result(String text, List<Mark> marks) { this.text = text; this.marks = marks; }
    }
    public static Result parse(String source) {
        StringBuilder out = new StringBuilder();
        ArrayList<Mark> marks = new ArrayList<>();
        boolean fenced = false;
        int codeStart = 0;
        String bounded = source.length() > 60000 ? source.substring(0, 60000) + "\n…" : source;
        for (String line : bounded.split("\n", -1)) {
            if (line.trim().startsWith("```")) {
                if (fenced) marks.add(new Mark(codeStart, out.length(), CODE));
                else codeStart = out.length();
                fenced = !fenced;
                continue;
            }
            if (fenced) { out.append(line).append('\n'); continue; }
            int heading = 0;
            while (heading < line.length() && heading < 6 && line.charAt(heading) == '#') heading++;
            boolean isHeading = heading > 0 && line.length() > heading && line.charAt(heading) == ' ';
            if (isHeading) line = line.substring(heading + 1);
            if (line.startsWith("* ") || line.startsWith("- ")) line = "• " + line.substring(2);
            int lineStart = out.length();
            for (int i = 0; i < line.length();) {
                char c = line.charAt(i);
                if (c == '\\' && i + 1 < line.length()) { out.append(line.charAt(i + 1)); i += 2; continue; }
                String delimiter = line.startsWith("**", i) ? "**" : line.startsWith("__", i) ? "__"
                        : c == '`' ? "`" : c == '*' ? "*" : null;
                int end = delimiter == null ? -1 : line.indexOf(delimiter, i + delimiter.length());
                if (delimiter != null && end > i + delimiter.length()) {
                    int start = out.length();
                    out.append(line, i + delimiter.length(), end);
                    marks.add(new Mark(start, out.length(), delimiter.equals("`") ? CODE : delimiter.length() == 2 ? BOLD : ITALIC));
                    i = end + delimiter.length();
                } else { out.append(c); i++; }
            }
            if (isHeading) marks.add(new Mark(lineStart, out.length(), HEADING));
            out.append('\n');
        }
        if (fenced) marks.add(new Mark(codeStart, out.length(), CODE));
        return new Result(out.toString(), marks);
    }
}
