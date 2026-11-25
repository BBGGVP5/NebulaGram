"""Hold to quick-send: run the selection helpers of NebulaQuickSend, then check the setting and the 0375 hooks."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
javac = shutil.which('javac') or str(java_home / 'bin/javac.exe')
java = shutil.which('java') or str(java_home / 'bin/java.exe')
source = (ui / 'NebulaQuickSend.java').read_text(encoding='utf-8')

# The pure helpers are lifted out of the class and compiled against minimal Telegram types.
def method(name):
    start = source.index('    static ' + name) if ('    static ' + name) in source else source.index(name)
    depth, i = 0, source.index('{', start)
    while True:
        if source[i] == '{': depth += 1
        elif source[i] == '}':
            depth -= 1
            if depth == 0: return source[start:i + 1]
        i += 1

helpers = '\n'.join(method(n) for n in ['List<MediaController.PhotoEntry> recentPhotos(', 'List<TLRPC.Document> recentStickers(', 'int position('])
stubs = {
'org/telegram/messenger/MediaController.java': 'package org.telegram.messenger; public class MediaController {public static class PhotoEntry {public boolean isVideo;public String path;public int imageId;public PhotoEntry(int id,String p,boolean v){imageId=id;path=p;isVideo=v;}}}',
'org/telegram/tgnet/TLRPC.java': 'package org.telegram.tgnet; public class TLRPC {public static class Document {public long id;public Document(long i){id=i;}}}',
'Helpers.java': 'import java.util.*; import org.telegram.messenger.MediaController; import org.telegram.tgnet.TLRPC;\npublic class Helpers {static final int LIMIT = 8;\n' + helpers + '\n}',
'Check.java': r'''import java.util.*; import org.telegram.messenger.MediaController; import org.telegram.tgnet.TLRPC;
public class Check {
 static int cases;
 static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);cases++;}
 public static void main(String[] a){
  List<MediaController.PhotoEntry> all=new ArrayList<>();
  all.add(new MediaController.PhotoEntry(1,"/a.jpg",false));all.add(new MediaController.PhotoEntry(2,"/b.mp4",true));all.add(null);all.add(new MediaController.PhotoEntry(3,null,false));
  for(int i=4;i<20;i++)all.add(new MediaController.PhotoEntry(i,"/"+i+".jpg",false));
  List<MediaController.PhotoEntry> photos=Helpers.recentPhotos(all);
  check(photos.size()==8&&photos.get(0).imageId==1&&photos.get(1).imageId==4&&photos.get(7).imageId==10,"newest still photos, no videos or missing files");
  check(Helpers.recentPhotos(null).isEmpty()&&Helpers.recentPhotos(new ArrayList<>()).isEmpty(),"empty gallery");
  List<TLRPC.Document> docs=new ArrayList<>();docs.add(null);for(int i=0;i<12;i++)docs.add(new TLRPC.Document(i));
  List<TLRPC.Document> stickers=Helpers.recentStickers(docs);
  check(stickers.size()==8&&stickers.get(0).id==0&&stickers.get(7).id==7,"recent stickers in order");
  check(Helpers.recentStickers(null).isEmpty(),"no recents");
  check(Helpers.position(500,300,1080,24)==500&&Helpers.position(-50,300,1080,24)==24&&Helpers.position(900,300,1080,24)==756,"row stays on screen");
  System.out.println("Quick send: "+cases+" cases passed");
 }
}
'''}

with tempfile.TemporaryDirectory(prefix='nebula-quick-send-') as work:
    work = Path(work)
    for name, text in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(text.encode('utf-8'))
    subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(work), *map(str, work.rglob('*.java'))], check=True)
    subprocess.run([java, '-cp', str(work), 'Check'], check=True)

# Off by default; the row never steals focus from the keyboard; the release reaches the button.
assert 'NebulaMessagePreferences.enabled(KEY, false)' in source and 'KEY = "quick_send_hold"' in source
assert 'ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);' in source
assert 'MediaController.loadGalleryPhotosAlbums(0);' in source and 'Manifest.permission.READ_MEDIA_IMAGES' in source
up = source[source.index('if (action == MotionEvent.ACTION_UP) {'):]
assert up.index('return false;') < up.index('if (action == MotionEvent.ACTION_CANCEL)')
section = (ui / 'NebulaSectionFragment.java').read_text(encoding='utf-8')
assert 'preference(context, R.drawable.msg_sticker, NebulaQuickSend.KEY, false,' in section and '"Быстрая отправка удержанием", "Hold to quick-send"' in section
assert '0375-hold-to-quick-send.patch' in (root / 'patches/android/HOOKS.md').read_text(encoding='utf-8')
if len(sys.argv) > 1:
    enter = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java').read_text(encoding='utf-8')
    for hook in ['attachButton.setOnLongClickListener(v -> app.nebulagram.ui.NebulaQuickSend.photos(v,',
                 'parentFragment.sendMedia(entry, null, true, 0, 0, false, 0)',
                 'attachButton.setOnTouchListener(app.nebulagram.ui.NebulaQuickSend::touch);',
                 'emojiButton.setOnLongClickListener(v -> !emojiButton.isFormatMode() && app.nebulagram.ui.NebulaQuickSend.stickers(v, currentAccount,',
                 'sticker -> onStickerSelected(sticker, null, "recent", null, false, true, 0, 0)',
                 'emojiButton.setOnTouchListener(app.nebulagram.ui.NebulaQuickSend::touch);',
                 'ChatObject.canSendPhoto(parentFragment.getCurrentChat())', 'ChatObject.canSendStickers(parentFragment.getCurrentChat())']:
        assert hook in enter, hook
    assert enter.count('!isInScheduleMode() && slowModeTimer <= 0 && editingMessageObject == null') == 2
    chat = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text(encoding='utf-8')
    assert 'public void sendMedia(MediaController.PhotoEntry photoEntry, VideoEditedInfo videoEditedInfo, boolean notify, int scheduleDate, int scheduleRepeatPeriod, boolean forceDocument, long stars)' in chat
    assert 'public void onStickerSelected(TLRPC.Document sticker, String query, Object parent, MessageObject.SendAnimationData sendAnimationData, boolean clearsInputField, boolean notify, int scheduleDate, int scheduleRepeatPeriod)' in enter
print('Quick send: setting, gestures and 0375 hooks verified')
