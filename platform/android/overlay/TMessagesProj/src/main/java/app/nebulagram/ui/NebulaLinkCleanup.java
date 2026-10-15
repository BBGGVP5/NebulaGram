package app.nebulagram.ui;

import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit, offline removal of known tracking fields; preserves all other bytes. */
public final class NebulaLinkCleanup {
    private static final Pattern URL = Pattern.compile("https?://[^\\s\\p{Z}<>\"']+", Pattern.CASE_INSENSITIVE);
    private static final Set<String> TRACKING = new HashSet<>(Arrays.asList(
            "fbclid", "gclid", "dclid", "msclkid", "yclid", "igshid", "_openstat", "mc_cid", "mc_eid"));
    private static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
            "sig", "signature", "token", "access_token", "auth", "authorization", "key", "api_key", "code", "state",
            "expires", "expiry", "policy", "hmac", "hash", "client_secret", "session_id", "payment_id", "invoice"));
    private static final Set<String> PROTECTED_PATHS = new HashSet<>(Arrays.asList(
            "sub", "subscription", "subscriptions", "subscribe", "auth", "oauth", "oauth2", "authorize", "login", "callback", "payment", "invoice"));
    private static final Pattern OPAQUE_PATH = Pattern.compile("[a-f0-9]{32}|[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}");
    private NebulaLinkCleanup() {}

    public static String clean(String text) {
        if (text == null || text.isEmpty() || text.length() > 50_000) return text == null ? "" : text;
        Matcher matches = URL.matcher(text);
        StringBuilder result = new StringBuilder();
        int offset = 0;
        while (matches.find()) {
            result.append(text, offset, matches.start());
            String candidate = matches.group();
            int end = trimEnd(candidate);
            result.append(cleanURL(candidate.substring(0, end))).append(candidate, end, candidate.length());
            offset = matches.end();
        }
        return result.append(text, offset, text.length()).toString();
    }

    private static int trimEnd(String value) {
        int[] balance = new int[3];
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '(') balance[0]++; else if (ch == ')') balance[0]--;
            else if (ch == '[') balance[1]++; else if (ch == ']') balance[1]--;
            else if (ch == '{') balance[2]++; else if (ch == '}') balance[2]--;
        }
        int end = value.length();
        while (end > 0) {
            char last = value.charAt(end - 1);
            if (last == '.' || last == ',' || last == '!' || last == ';') { end--; continue; }
            int bracket = last == ')' ? 0 : last == ']' ? 1 : last == '}' ? 2 : -1;
            if (bracket < 0 || balance[bracket] >= 0) break;
            balance[bracket]++; end--;
        }
        return end;
    }

    private static String key(String part) throws Exception {
        int equals = part.indexOf('=');
        String decoded = URLDecoder.decode(equals < 0 ? part : part.substring(0, equals), "UTF-8");
        if (decoded.indexOf('\ufffd') >= 0) throw new IllegalArgumentException("Invalid UTF-8 query key");
        return decoded.toLowerCase(Locale.ROOT);
    }
    private static boolean protectedParts(String value) throws Exception {
        for (String part : value.split("&", -1)) {
            String key = key(part);
            if (PROTECTED.contains(key) || key.startsWith("x-amz-") || key.startsWith("x-goog-")) return true;
        }
        return false;
    }
    private static String cleanURL(String value) {
        if (value.length() > 8192) return value;
        try {
            URI parsed = new URI(value);
            String authority = parsed.getRawAuthority();
            if (authority == null || authority.isEmpty() || authority.contains("@")) return value;
            String host = authority.toLowerCase(Locale.ROOT);
            if (host.startsWith("sub.") || host.startsWith("subs.") || host.startsWith("subscription.")) return value;
            String path = parsed.getPath();
            if (path != null) {
                if (path.indexOf('\ufffd') >= 0) return value;
                for (String part : path.toLowerCase(Locale.ROOT).split("/")) {
                    if (PROTECTED_PATHS.contains(part) || OPAQUE_PATH.matcher(part).matches()) return value;
                }
            }
            int hash = value.indexOf('#');
            int question = value.indexOf('?');
            if (question < 0 || hash >= 0 && question > hash) return value;
            int queryEnd = hash < 0 ? value.length() : hash;
            String query = value.substring(question + 1, queryEnd);
            if (protectedParts(query) || hash >= 0 && protectedParts(value.substring(hash + 1))) return value;
            ArrayList<String> kept = new ArrayList<>();
            boolean changed = false;
            for (String part : query.split("&", -1)) {
                String key = key(part);
                if (key.startsWith("utm_") || TRACKING.contains(key)) changed = true;
                else kept.add(part);
            }
            if (!changed) return value;
            String rest = String.join("&", kept);
            return value.substring(0, question) + (rest.isEmpty() ? "" : "?" + rest) + value.substring(queryEnd);
        } catch (Exception invalid) { return value; }
    }
}
