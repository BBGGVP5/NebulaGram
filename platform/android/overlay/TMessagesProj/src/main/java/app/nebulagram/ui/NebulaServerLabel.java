package app.nebulagram.ui;

/** Splits a country prefix for display without changing the saved server name. */
public final class NebulaServerLabel {
    public final String title;
    public final String flag;

    public NebulaServerLabel(String name, String address, String countryFlag) {
        String cleanName = strip(name);
        String prefix = leadingFlag(cleanName);
        String metadataFlag = strip(countryFlag);
        flag = !prefix.isEmpty() ? prefix
                : metadataFlag.equals(leadingFlag(metadataFlag)) ? metadataFlag : "";
        String remainder = strip(cleanName.substring(prefix.length()));
        title = remainder.isEmpty() ? strip(address) : remainder;
    }

    private static String leadingFlag(String value) {
        if (value.isEmpty()) {
            return "";
        }
        int first = value.codePointAt(0);
        int next = Character.charCount(first);
        if (!regionalIndicator(first) || next >= value.length()) {
            return "";
        }
        int second = value.codePointAt(next);
        return regionalIndicator(second)
                ? value.substring(0, next + Character.charCount(second)) : "";
    }

    private static boolean regionalIndicator(int point) {
        return point >= 0x1F1E6 && point <= 0x1F1FF;
    }

    private static String strip(String value) {
        if (value == null) {
            return "";
        }
        int start = 0;
        int end = value.length();
        while (start < end && space(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (end > start && space(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    private static boolean space(int point) {
        return Character.isWhitespace(point) || Character.isSpaceChar(point);
    }
}
