"""Forward recipient change, forward targets, chat search and deleted-archive sync hooks (0340, 0341)."""
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'vendor/telegram-android'
src = tree / 'TMessagesProj/src/main/java/org/telegram'
ui = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'


def read(path):
    return path.read_text(encoding='utf-8')


def method(source, signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


# 1. «Изменить получателя»: черновик уходит из исходного чата.
chat = read(src / 'ui/ChatActivity.java')
moved = chat.index('chatActivity.nebulaShowForwardDraft(fmessages, withoutAuthor);')
assert chat.index('nebulaDropMovedForwardDraft();', moved) - moved < 120
drop = method(chat, 'private void nebulaDropMovedForwardDraft(')
assert 'messagePreviewParams.updateForward(null, dialog_id)' in drop and 'fallbackFieldPanel()' in drop
assert 'forbidForwardingWithDismiss = false' in drop

# 2. Список пересылки: архив в конце, «Все чаты» не прячутся в окне выбора.
controller = read(src / 'messenger/MessagesController.java')
assert 'nebulaArchivedForward.add(d);' in controller
assert controller.index('dialogsForward.addAll(nebulaArchivedForward);') < controller.index('        if (!selfAdded) {')
assert 'd.folder_id == 1 && d.id != selfId && canAddToForward(d)' in controller
dialogs = read(src / 'ui/DialogsActivity.java')
assert 'NebulaAppearance.hideAllChats() && !onlySelect && filters.size() > 1' in dialogs

# 3. Инструменты NebulaGram — только в поиске настроек.
search = read(src / 'ui/Adapters/DialogsSearchAdapter.java')
assert 'NebulaSettingsSearch.match(' not in search
assert 'NebulaSettingsSearch.append(' in read(src / 'ui/ProfileActivity.java')

# 4–5. Очередь базы только при удалениях с архивом; массовые стирания щадят архив.
assert 'NebulaDeletedArchive.beforeDeletes(currentAccount, deletedMessagesFinal != null, () -> {' in controller
storage = read(src / 'messenger/MessagesStorage.java')
assert 'NebulaDeletedArchive.keepClause(currentAccount, did)' in method(storage, 'public void overwriteChannel(')
reset = method(storage, 'public void resetDialogs(')
assert 'NebulaDeletedArchive.keepClause(currentAccount, nebulaDid)' in reset and 'NebulaDeletedArchive.active(currentAccount)' in reset
style = read(ui / 'NebulaDeletedStyle.java')
assert 'MessagesStorage.getInstance(account).getChat(-peer)' in style and 'onStorage' in style

archive = read(ui / 'NebulaDeletedArchive.java')
assert 'cachedOwner' not in archive and 'cachedSnapshots' in archive
retain = method(archive, 'public static ArrayList<Integer> retain(')
assert retain.index('if(!enabled(owner)&&!present(owner))return result;') < retain.index('synchronized (NebulaDeletedArchive.class)')
assert 'diagnose(account,dialog,asked,found,kept,skipped)' in retain

# Выполняем keepClause и beforeDeletes на заглушках.
production = '\n'.join(method(archive, s) for s in [
    'public static boolean active(', 'public static void beforeDeletes(', 'public static String keepClause('])
harness = '''import java.util.*;
class KeepCheck {
 static boolean on=true,fail;static int queued,failures;
 static long owner(int a){return 42;} static boolean enabled(long o){return on;} static boolean present(long o){return false;}
 static void failed(long o){failures++;}
 static class Snapshot {Map<Long,Set<Integer>> scopes=new HashMap<>();}
 static Snapshot snap=new Snapshot();
 static Snapshot snapshot(long o)throws Exception{if(fail)throw new Exception("unreadable");return snap;}
 static class TextUtils {static String join(String s,Iterable<Integer> ids){StringBuilder b=new StringBuilder();for(Integer i:ids){if(b.length()>0)b.append(s);b.append(i);}return b.toString();}}
 static class Queue {void postRunnable(Runnable r){queued++;}}
 static class MessagesStorage {static MessagesStorage getInstance(int a){return new MessagesStorage();} Queue getStorageQueue(){return new Queue();}}
 static class NebulaDeletedArchive {}
 PRODUCTION
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] a){
  check(keepClause(0,-100).isEmpty(),"empty archive produced a clause");
  snap.scopes.put(-100L,new HashSet<>(Arrays.asList(30,10,20)));
  check(keepClause(0,-100).equals(" AND mid NOT IN(10,20,30)"),keepClause(0,-100));
  check(keepClause(0,-200).isEmpty(),"other chat kept");
  fail=true;check(keepClause(0,-100).isEmpty()&&failures==1,"unreadable archive must fall back to Telegram's delete");fail=false;
  on=false;check(keepClause(0,-100).isEmpty(),"inactive archive kept rows");
  int[] ran={0};beforeDeletes(0,true,()->ran[0]++);check(ran[0]==1&&queued==0,"inactive archive delayed the UI batch");
  on=true;beforeDeletes(0,false,()->ran[0]++);check(ran[0]==2&&queued==0,"batch without deletions went through the storage queue");
  beforeDeletes(0,true,()->ran[0]++);check(ran[0]==2&&queued==1,"deletions skipped the archive queue");
  System.out.println("keepClause and beforeDeletes fixtures passed");
 }
}'''.replace('PRODUCTION', production)
with tempfile.TemporaryDirectory(prefix='nebula-keep-') as temp:
    p = Path(temp)
    (p / 'KeepCheck.java').write_text(harness, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(p), str(p / 'KeepCheck.java')], check=True)
    subprocess.run(['java', '-cp', str(p), 'KeepCheck'], check=True)
print('Forward recipient/targets, chat search tools and deleted-archive sync hooks passed')
