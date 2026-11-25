"""Share as image: run the colour and message choice of NebulaMessageImage, then check the sheet, the setting and the 0378 hooks."""
from pathlib import Path
import os, re, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
javac = shutil.which('javac') or str(java_home / 'bin/javac.exe')
java = shutil.which('java') or str(java_home / 'bin/java.exe')
source = (ui / 'NebulaMessageImage.java').read_text(encoding='utf-8')

def block(signature):
    start = source.index(signature)
    depth, i = 0, source.index('{', start)
    while True:
        if source[i] == '{': depth += 1
        elif source[i] == '}':
            depth -= 1
            if depth == 0: return source[start:i + 1]
        i += 1

day = source[source.index('    static final int[][] DAY'):source.index('};', source.index('static final int[][] DAY')) + 2]
pure = '\n'.join([day, 'static final int LIMIT = 10;', block('static int night('), block('static int[] colors('), block('static List<MessageObject> pick('), block('static List<Integer> kept('), block('static int[] stickerSize(')])
stubs = {
'android/graphics/Color.java': 'package android.graphics; public class Color {public static int red(int c){return (c>>16)&255;}public static int green(int c){return (c>>8)&255;}public static int blue(int c){return c&255;}public static int argb(int a,int r,int g,int b){return (a<<24)|(r<<16)|(g<<8)|b;}}',
'org/telegram/messenger/MessageObject.java': 'package org.telegram.messenger; public class MessageObject {public int id;public MessageObject(int i){id=i;} public static class GroupedMessages {public java.util.ArrayList<MessageObject> messages=new java.util.ArrayList<>();}}',
'Pure.java': 'import java.util.*; import android.graphics.Color; import org.telegram.messenger.MessageObject;\npublic class Pure {\n' + pure + '\n}',
'Check.java': r'''import java.util.*; import org.telegram.messenger.MessageObject;
public class Check {
 static int cases;
 static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);cases++;}
 public static void main(String[] a){
  check(Pure.DAY.length==8,"eight backgrounds");
  int[] day=Pure.colors(0,false),night=Pure.colors(0,true);
  check(day[0]==Pure.DAY[0][0]&&night[0]!=day[0]&&(night[0]>>>24)==255,"night is an opaque darker copy");
  check(((night[0]>>16)&255)<((day[0]>>16)&255),"darker");
  check(Arrays.equals(Pure.colors(9,false),Pure.DAY[1])&&Arrays.equals(Pure.colors(-1,false),Pure.DAY[7]),"index wraps");
  int[] copy=Pure.colors(2,false);copy[0]=0;check(Pure.DAY[2][0]!=0,"callers cannot change the palette");
  MessageObject one=new MessageObject(1);
  check(Pure.pick(one,null).size()==1&&Pure.pick(null,null).isEmpty(),"single message");
  MessageObject.GroupedMessages album=new MessageObject.GroupedMessages();for(int i=0;i<14;i++)album.messages.add(new MessageObject(i));album.messages.add(2,null);
  List<MessageObject> picked=Pure.pick(one,album);
  check(picked.size()==10&&picked.get(0).id==0&&!picked.contains(null),"album, at most ten, no gaps");
  check(Pure.pick(one,new MessageObject.GroupedMessages()).get(0)==one,"empty album falls back to the message");
  Set<Integer> none=new HashSet<>();check(Pure.kept(3,none).equals(Arrays.asList(0,1,2)),"all kept");
  Set<Integer> skip=new HashSet<>(Arrays.asList(1));check(Pure.kept(3,skip).equals(Arrays.asList(0,2)),"one excluded");
  Set<Integer> all=new HashSet<>(Arrays.asList(0,1,2));check(Pure.kept(3,all).equals(Arrays.asList(0)),"never an empty picture");
  check(Arrays.equals(Pure.stickerSize(1000,400),new int[]{512,205})&&Arrays.equals(Pure.stickerSize(300,600),new int[]{256,512})&&Arrays.equals(Pure.stickerSize(0,5),new int[]{0,0}),"sticker 512 px on the long side");
  System.out.println("Message image: "+cases+" cases passed");
 }
}
'''}

with tempfile.TemporaryDirectory(prefix='nebula-message-image-') as work:
    work = Path(work)
    for name, text in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(text.encode('utf-8'))
    subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(work), *map(str, work.rglob('*.java'))], check=True)
    subprocess.run([java, '-cp', str(work), 'Check'], check=True)

assert 'KEY = "share_beautifully"' in source and 'NebulaMessagePreferences.enabled(KEY, false)' in source
assert 'OPTION = 7931' in source
# The chat's own MessageObject is never handed to the preview cell.
assert 'TLRPC.Message owner = original.messageOwner;' in source and 'MessageObject copy = new MessageObject(account, owner, true, true);' in source and 'cell.setMessageObject(copy,' in source
for part in ['TLRPC.Message.TLdeserialize(in, in.readInt32(false), false)', 'new TLRPC.TL_messageEntitySpoiler()', 'cell.nebulaHideTime = hideTime;',
             'if (hideText) {', 'TLRPC.Message detached = detached(owner);', '"image/webp"', 'Bitmap.CompressFormat.WEBP_LOSSY', 'SendMessagesHelper.prepareSendingDocument(',
             'NebulaText.text("Скрыть текст", "Hide text")', 'NebulaText.text("Скрыть время", "Hide time")', 'NebulaText.text("Отправить стикером", "Send as sticker")', 'NebulaText.text("Отправить файлом", "Send as file")',
             'new DarkThemeResourceProvider()', 'SendMessagesHelper.prepareSendingPhoto(', 'MediaController.saveFile(', 'FileProvider.getUriForFile(', 'bitmap.recycle()',
             'NebulaText.text("Поделиться красиво", "Share it beautifully")', 'NebulaText.text("Отправить в этот чат", "Send to this chat")']:
    assert part in source, part
section = (ui / 'NebulaSectionFragment.java').read_text(encoding='utf-8')
assert 'preference(context, R.drawable.msg_photos, NebulaMessageImage.KEY, false,' in section and '"Поделиться картинкой", "Share as image"' in section
assert '0380-share-image-hide-time.patch' in (root / 'patches/android/HOOKS.md').read_text(encoding='utf-8')
assert '0378-share-message-as-image.patch' in (root / 'patches/android/HOOKS.md').read_text(encoding='utf-8')
if len(sys.argv) > 1:
    chat = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text(encoding='utf-8')
    assert 'if (!noforwards && !isEphemeral && message.type != MessageObject.TYPE_PAID_MEDIA && app.nebulagram.ui.NebulaMessageImage.enabled()) {' in chat
    assert 'options.add(app.nebulagram.ui.NebulaMessageImage.OPTION);' in chat
    assert 'case app.nebulagram.ui.NebulaMessageImage.OPTION:\n                app.nebulagram.ui.NebulaMessageImage.show(this, selectedObject, selectedObjectGroup);' in chat
    assert not re.search(r'case 7931\b|options\.add\(7931\)', chat), 'the option id is used once'
    cell = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/Cells/ChatMessageCell.java').read_text(encoding='utf-8')
    assert 'public boolean nebulaHideTime;' in cell
    draw = cell[cell.index('public void drawTime(Canvas canvas, float alpha, boolean fromParent) {'):]
    assert draw.index('if (nebulaHideTime) return;') < draw.index('isAnyKindOfSticker()')
    assert 'public ChatMessageCell(Context context, int currentAccount, boolean canDrawBackgroundInParent, ChatMessageSharedResources sharedResources, Theme.ResourcesProvider resourcesProvider)' in cell
print('Message image: sheet, setting and 0378 hooks verified')
