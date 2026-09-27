package app.nebulagram.ui;

import java.util.ArrayList;

/** Ephemeral successful turns only. Never mixes provider conversations or loads saved history silently. */
public final class NebulaAiConversation {
    private final ArrayList<String[]> turns = new ArrayList<>();
    private String identity = "";
    public void select(String value) { if (!identity.equals(value)) { clear(); identity = value; } }
    public void clear() { turns.clear(); }
    public void add(String user, String assistant) {
        turns.add(new String[]{user, assistant});
        while (turns.size() > 6) turns.remove(0);
    }
    public String request(String current, int limit) {
        if (current.length() > limit) throw new IllegalArgumentException("INPUT_TOO_LONG");
        String suffix = "\n\nUser: " + current;
        StringBuilder prior = new StringBuilder();
        for (int i = turns.size() - 1; i >= 0; i--) {
            String[] turn = turns.get(i);
            String part = "User: " + turn[0] + "\nAssistant: " + turn[1] + "\n\n";
            if (part.length() + prior.length() + suffix.length() + 40 > limit) break;
            prior.insert(0, part);
        }
        return prior.length() == 0 ? current : "Previous conversation:\n" + prior + suffix;
    }
}
