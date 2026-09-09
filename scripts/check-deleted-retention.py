from pathlib import Path
import subprocess,sys,tempfile
root=Path.cwd();tree=Path(sys.argv[1]) if len(sys.argv)>1 else root/'build/final-verify-0904/tree'
policy=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaRetentionPolicy.java'
with tempfile.TemporaryDirectory(prefix='nebula-retention-policy-') as temp:
 p=Path(temp);(p/'Check.java').write_text('''import app.nebulagram.ui.NebulaRetentionPolicy; class Check {public static void main(String[] args){for(int mask=0;mask<512;mask++){boolean[] b=new boolean[9];for(int i=0;i<9;i++)b[i]=(mask&(1<<i))!=0;boolean expected=b[0]&&b[6]&&!b[7]&&b[8]&&!b[5]&&(!b[1]||b[3])&&(!b[2]||b[4]);if(NebulaRetentionPolicy.allowed(b[0],b[1],b[2],b[3],b[4],b[5],b[6],b[7],b[8])!=expected)throw new AssertionError(mask);}System.out.println("512 native retention policy combinations passed");}}''',encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',str(p),str(policy),str(p/'Check.java')],check=True)
 subprocess.run(['java','-cp',str(p),'Check'],check=True)
ui=tree/'TMessagesProj/src/main/java/org/telegram'
controller=(ui/'messenger/MessagesController.java').read_text(encoding='utf-8')
start=controller.index('LongSparseArray<ArrayList<Integer>> nebulaNotificationDeletes')
end=controller.index('        if (savedReadInbox != null)',start)
section=controller[start:end]
assert section.index('NebulaDeletedArchive.retain(')<section.index('AndroidUtilities.runOnUIThread(')
assert 'original.clear(); original.addAll(remove)' in section
assert 'getNewDeleteTask(nebulaAllTask, nebulaAllMedia)' in controller
assert 'nebulaCopyDeleteTasks(taskMedia)' in controller
storage=(ui/'messenger/MessagesStorage.java').read_text(encoding='utf-8')
assert storage.index('databaseCreated = true')<storage.index('NebulaDeletedArchive.initialize(currentAccount)')
start=storage.index('public void markMessagesAsDeletedByRandoms(');end=storage.index('protected void deletePushMessages',start)
section=storage[start:end];assert section.index('NebulaDeletedArchive.retain')<section.index('NotificationCenter.messagesDeleted')
archive=(root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaDeletedArchive.java').read_text(encoding='utf-8')
assert 'message.serializeToStream(serialized)' in archive and 'message.media.ttl_seconds=0' in archive
assert 'storage.markMessagesAsDeleted(peer,ids,false,false,0,0)' in archive
assert 'if(owner(account)!=expected)' in archive
assert 'peer!=0&&entry.optLong("peer")!=peer' in archive
assert 'if(e.optBoolean("inline")&&!contains(after,peer,id))' in archive
assert 'TextUtils.isEmpty(message.message)' not in archive # Photo-only messages must survive.
assert 'sendRequest(' not in archive
cell=(ui/'ui/Cells/ChatMessageCell.java').read_text(encoding='utf-8')
assert 'matrix.setSaturation(.18f)' in cell and 'NebulaDeletedArchive.icon(), " ", currentTimeString' in cell
print('Android native deletion ordering, media/timer retention, account/chat clear guards and marker wiring passed')

# Execute the production setter with preference/Telegram emoji-range fixtures.
# This exercises marker boundaries and persistence, not Android font rendering.
setter = archive[archive.index("    public static void setIcon("):archive.index("    public static boolean saveSecret")]
setter = setter.replace("org.telegram.messenger.Emoji", "Emoji")
harness = r'''import java.util.ArrayList;
class MarkerCheck {
 static String saved = "🗑";
 static class ApplicationLoader {
  static final ApplicationLoader applicationContext = new ApplicationLoader();
  ApplicationLoader getSharedPreferences(String key, int mode) { return this; }
  ApplicationLoader edit() { return this; }
  ApplicationLoader putString(String key, String value) { saved = value; return this; }
  void apply() { }
 }
 static class Emoji {
  static class EmojiSpanRange { int start,end; EmojiSpanRange(int a,int b){start=a;end=b;} }
  static ArrayList<EmojiSpanRange> parseEmojis(String text) {
   ArrayList<EmojiSpanRange> result = new ArrayList<>();
   for(String value:new String[]{"👨‍👩‍👧‍👦","👍🏽","🇷🇺"}) {
    for(int at=text.indexOf(value);at>=0;at=text.indexOf(value,at+value.length())) result.add(new EmojiSpanRange(at,at+value.length()));
   }
   return result;
  }
 }
 SETTER
 static void check(String input,String expected){setIcon(input);if(!expected.equals(saved))throw new AssertionError(input+" -> "+saved);}
 public static void main(String[] args){
  check(" ✕ ","✕");check("", "✕");check(null,"✕");check("abcdef","abcd");
  check("👨‍👩‍👧‍👦abcde","👨‍👩‍👧‍👦abc");check("👍🏽🇷🇺xyZ","👍🏽🇷🇺xy");
  check("e\u0301abcd","e\u0301abc");check("👨‍👩‍👧‍👦👨‍👩‍👧‍👦","👨‍👩‍👧‍👦👨‍👩‍👧‍👦");
  System.out.println("8 custom marker setter cases passed (emoji range fixtures)");
 }
}'''.replace('SETTER', setter)
with tempfile.TemporaryDirectory(prefix='nebula-marker-') as temp:
 p=Path(temp);(p/'MarkerCheck.java').write_bytes(harness.encode('utf-8'))
 subprocess.run(['javac','-encoding','UTF-8','-d',str(p),str(p/'MarkerCheck.java')],check=True)
 subprocess.run(['java','-cp',str(p),'MarkerCheck'],check=True)
