"""Execute production conversation bounds and safe Markdown parsing without Android/network."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
work = root / 'build/ai-chat-tests'
work.mkdir(parents=True, exist_ok=True)
source = r'''import app.nebulagram.ui.NebulaAiConversation;
import app.nebulagram.ui.NebulaAiMarkdown;
public class AiChatCheck {
    static void check(boolean value, String detail) { if (!value) throw new AssertionError(detail); }
    public static void main(String[] args) {
        NebulaAiConversation chat = new NebulaAiConversation(); chat.select("nano");
        check(chat.request("hello", 100).equals("hello"), "first request unchanged");
        chat.add("first question", "first answer");
        check(chat.request("next", 500).contains("first answer"), "context kept");
        check(chat.request("next", 20).equals("next"), "context discarded before new input");
        chat.select("remote"); check(chat.request("next", 100).equals("next"), "providers isolated");
        for (int i=0;i<20;i++) chat.add("q"+i, "a"+i);
        String request=chat.request("latest", 2000);
        check(!request.contains("q13\n") && request.contains("q14\n") && request.contains("q19\n"), "six complete pairs only");
        for(int n=20;n<500;n++) check(chat.request("new",n).length()<=n,"bounded request "+n);
        try { chat.request("long input",2);throw new AssertionError("length must reject"); }catch(IllegalArgumentException expected){}
        chat.clear();check(chat.request("fresh",100).equals("fresh"),"new chat clears context");
        NebulaAiMarkdown.Result result=NebulaAiMarkdown.parse("## Analysis\n**Bold** and *italic*\n- Item\n```java\n**literal**\n```\n`x < y`\nplain");
        check(result.text.equals("Analysis\nBold and italic\n• Item\n**literal**\nx < y\nplain\n"),"headers/lists/code/formatting");
        check(result.marks.size()==5,"semantic spans");
        for(NebulaAiMarkdown.Mark mark:result.marks)check(mark.start>=0&&mark.end<=result.text.length()&&mark.end>=mark.start,"valid span");
        check(NebulaAiMarkdown.parse("unclosed **marker").text.contains("**marker"),"unclosed marker literal");
        check(NebulaAiMarkdown.parse("\\* literal").text.equals("* literal\n"),"escaped marker");
        check(NebulaAiMarkdown.parse("<script>alert(1)</script>").text.contains("<script>"),"HTML stays inert text");
        check(NebulaAiMarkdown.parse("x".repeat(200000)).text.length()<60010,"render bound");
        System.out.println("AI chat: bounded context, provider isolation, Markdown/code spans and inert HTML passed");
    }
}'''
test = work / 'AiChatCheck.java'
test.write_text(source, encoding='utf-8')
java_home = os.environ.get('JAVA_HOME')
def tool(name):
    return str(Path(java_home) / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))) if java_home else name
subprocess.run([tool('javac'), '-J-Xmx256m', '-encoding', 'UTF-8', '-d', str(work), str(test),
                str(ui / 'NebulaAiConversation.java'), str(ui / 'NebulaAiMarkdown.java')], check=True)
subprocess.run([tool('java'), '-Xmx128m', '-cp', str(work), 'AiChatCheck'], check=True)
# Integration guards complement the executable pure-Java cases; they are not device tests.
chat = (ui / 'NebulaAiChatView.java').read_text(encoding='utf-8')
assert 'disposed || active != task' in chat and 'worker.interrupt()' in chat
assert 'NebulaAiMarkdown.parse(raw)' in chat and 'ACCESSIBILITY_LIVE_REGION_POLITE' in chat
nano = (ui / 'NebulaAiFragment.java').read_text(encoding='utf-8')
assert 'if (downloading && !nanoPaused) AndroidUtilities.runOnUIThread(nanoPoll, 3000)' in nano
assert 'nanoProgressBar.setIndeterminate(nanoTotal <= 0)' in nano
assert 'AndroidUtilities.cancelRunOnUIThread(nanoPoll)' in nano
print('Chat cancellation, lifecycle-safe Nano polling and unknown-size progress guards passed')
