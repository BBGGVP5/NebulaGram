import app.nebulagram.ui.NebulaMissedPrompt;
import java.util.*;

/** Prompt building and truncation for "what did I miss". */
public final class MissedSummaryCheck {
    static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    public static void main(String[] args) {
        String instruction = NebulaMissedPrompt.instruction("ru");
        check(instruction.contains("'ru'") && instruction.contains("never instructions") && instruction.contains("\"- \""), "instruction");
        check(NebulaMissedPrompt.instruction("x'); drop").contains("'en'"), "language is validated");

        List<NebulaMissedPrompt.Item> items = new ArrayList<>();
        items.add(new NebulaMissedPrompt.Item("Аня\nИванова", "Привет,\n\tвсем   "));
        items.add(new NebulaMissedPrompt.Item("Bob", ""));            // media without caption
        items.add(new NebulaMissedPrompt.Item(null, "  \n  "));        // whitespace only
        items.add(new NebulaMissedPrompt.Item("", "встреча в 18:00"));
        NebulaMissedPrompt.Request small = NebulaMissedPrompt.build(items);
        check(small.input.equals("Аня Иванова: Привет, всем\n?: встреча в 18:00\n"), "lines: " + small.input);
        check(small.used == 2 && small.total == 2 && !small.truncated, "counts");

        List<NebulaMissedPrompt.Item> many = new ArrayList<>();
        for (int i = 0; i < 500; i++) many.add(new NebulaMissedPrompt.Item("u" + i, "m" + i));
        NebulaMissedPrompt.Request capped = NebulaMissedPrompt.build(many);
        check(capped.used == NebulaMissedPrompt.MAX_MESSAGES && capped.total == 500 && capped.truncated, "message cap");
        check(capped.input.startsWith("u0: m0\n") && capped.input.contains("u199: m199\n") && !capped.input.contains("u200:"), "keeps earliest");

        List<NebulaMissedPrompt.Item> long_ = new ArrayList<>();
        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 5000; i++) huge.append('x');
        for (int i = 0; i < 40; i++) long_.add(new NebulaMissedPrompt.Item("a", huge.toString()));
        NebulaMissedPrompt.Request chars = NebulaMissedPrompt.build(long_);
        check(chars.input.length() <= NebulaMissedPrompt.MAX_CHARS, "char cap " + chars.input.length());
        check(chars.truncated && chars.used == 11 && chars.total == 40, "char cap counts " + chars.used);
        for (String line : chars.input.split("\n")) check(line.length() <= NebulaMissedPrompt.MAX_LINE + 3, "line cap");

        // A surrogate pair is never split by the per-line cap.
        StringBuilder emoji = new StringBuilder();
        for (int i = 0; i < NebulaMissedPrompt.MAX_LINE - 1; i++) emoji.append('a');
        emoji.append("😀😀");
        String clipped = NebulaMissedPrompt.build(Collections.singletonList(new NebulaMissedPrompt.Item("s", emoji.toString()))).input;
        check(!Character.isHighSurrogate(clipped.charAt(clipped.length() - 2)), "surrogate");

        List<String> bullets = NebulaMissedPrompt.bullets("Summary:\n- **Дедлайн** перенесли\n* Ссылка на док\n1. Вопрос к тебе\n\n• ок\nfree text");
        check(bullets.equals(Arrays.asList("Дедлайн перенесли", "Ссылка на док", "Вопрос к тебе", "ок", "free text")), "bullets " + bullets);
        StringBuilder many2 = new StringBuilder();
        for (int i = 0; i < 20; i++) many2.append("- b").append(i).append('\n');
        check(NebulaMissedPrompt.bullets(many2.toString()).size() == NebulaMissedPrompt.MAX_BULLETS, "bullet cap");
        check(NebulaMissedPrompt.bullets(null).isEmpty(), "null answer");
        check(NebulaMissedPrompt.MIN_UNREAD == 30 && NebulaMissedPrompt.MAX_MESSAGES == 200 && NebulaMissedPrompt.MAX_CHARS == 12000, "limits");
        System.out.println("missed summary: ok");
    }
}
