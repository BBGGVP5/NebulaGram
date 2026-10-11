package app.nebulagram.ui;

/** Chat-only presentation boundary. Quoted/code/inner labels remain content. */
public final class NebulaAiChatReply {
    private static final String[] MARKERS = {"Assistant:", "**Assistant:**", "**Assistant**:"};
    private NebulaAiChatReply() {}
    public static String clean(String value, boolean partial) {
        if (value == null || value.isEmpty()) return "";
        int start = 0;
        while (start < value.length() && Character.isWhitespace(value.charAt(start))) start++;
        String leading = value.substring(start);
        for (String marker : MARKERS) {
            if (leading.length() >= marker.length() && leading.regionMatches(true, 0, marker, 0, marker.length())) {
                return value.substring(contentStart(value, start + marker.length()));
            }
            if (partial && !leading.isEmpty() && leading.length() < marker.length()
                    && marker.regionMatches(true, 0, leading, 0, leading.length())) return "";
        }
        return value;
    }
    private static int contentStart(String value, int index) {
        while (index < value.length() && (value.charAt(index) == ' ' || value.charAt(index) == '\t')) index++;
        if (index < value.length() && (value.charAt(index) == '\n' || value.charAt(index) == '\r')) {
            if (value.charAt(index++) == '\r' && index < value.length() && value.charAt(index) == '\n') index++;
            // Remove blank role-padding lines, preserve first content indentation.
            while (index < value.length()) {
                int end = index;
                while (end < value.length() && (value.charAt(end) == ' ' || value.charAt(end) == '\t')) end++;
                if (end == value.length()) return end;
                if (value.charAt(end) != '\n' && value.charAt(end) != '\r') return index;
                index = end + 1;
                if (value.charAt(end) == '\r' && index < value.length() && value.charAt(index) == '\n') index++;
            }
        }
        return index;
    }
}
