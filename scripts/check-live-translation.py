"""Exercise the production draft request gate across edits and lifecycle changes."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
test = r'''
import app.nebulagram.ui.NebulaDraftRequestGate;
public class LiveTranslationCheck {
    static void check(boolean value, String context) { if (!value) throw new AssertionError(context); }
    public static void main(String[] args) {
        NebulaDraftRequestGate gate = new NebulaDraftRequestGate();
        long a = gate.begin("account1:chat1:en:provider1:A");
        check(gate.begin("account1:chat1:en:provider1:A") == 0, "layout refresh must not issue duplicate requests");
        long b = gate.begin("account1:chat1:en:provider1:B");
        long aAgain = gate.begin("account1:chat1:en:provider1:A");
        check(!gate.accepts(a) && !gate.accepts(b) && gate.accepts(aAgain), "A-B-A must reject both old responses");
        for (String identity : new String[]{"account1:chat1:de:provider1:A", "account1:chat1:de:provider2:A", "account1:chat2:de:provider2:A", "account2:chat2:de:provider2:A"}) {
            long next = gate.begin(identity);
            check(!gate.accepts(aAgain) && gate.accepts(next), "language/provider/chat/account changes invalidate responses");
            aAgain = next;
        }
        gate.cancel(); check(!gate.accepts(aAgain) && !gate.accepts(0), "pause/disable/send invalidates in-flight work");
        check(gate.begin("account2:chat2:de:provider2:A") != 0, "returning to chat permits a fresh request");
        gate.suppress("sameDraft:en");
        check(gate.begin("sameDraft:en") == 0, "dismissed or applied preview must not request itself again");
        gate.cancel(); check(gate.begin("sameDraft:en") == 0, "dismissal survives pause/resume");
        check(gate.begin("sameDraft:de") != 0, "changing target language permits the same draft again");
        for (int i = 0; i < 1000; i++) {
            long old = gate.begin("old" + i); gate.cancel();
            long current = gate.begin("new" + i);
            check(!gate.accepts(old) && gate.accepts(current), "rapid cancellation " + i);
        }
        System.out.println("Live translation: deduplication, A-B-A edits, account/language/provider changes and 1000 cancellations passed");
    }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-live-translation-') as temporary:
    path = Path(temporary) / 'LiveTranslationCheck.java'
    path.write_text(test, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temporary, str(ui / 'NebulaDraftRequestGate.java'), str(path)], check=True)
    subprocess.run(['java', '-cp', temporary, 'LiveTranslationCheck'], check=True)

# Guards complement executable state tests; they do not claim on-device UI acceptance.
draft = (ui / 'NebulaDraftTranslation.java').read_text(encoding='utf-8')
assert 'gate.accepts(request)' in draft and 'source.equals(editor.getText().toString())' in draft
assert 'ArrayBlockingQueue<>(1)' in draft and 'client.cancel()' in draft
incoming = (ui / 'NebulaAutoTranslate.java').read_text(encoding='utf-8')
assert 'message.isOutOwner()' in incoming and 'message.messageOwner.noforwards' in incoming
assert 'message.isSecretMedia()' in incoming and 'chat.noforwards' in incoming
settings = (ui / 'NebulaTranslationSettings.java').read_text(encoding='utf-8')
assert 'NebulaTasks.user(account)' in settings and '"composer_shortcut", false' in settings
print('Bounded requests, explicit draft application, protected text and opt-in account settings wired')
