"""Chat search media filters, reverse image search, emoji packs and the voice/round "sent from" rules."""
from pathlib import Path
import re, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'vendor/telegram-android'
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
java = tree / 'TMessagesProj/src/main/java/org/telegram'

def read(path):
    return path.read_text(encoding='utf-8')

media = read(java / 'messenger/MediaDataController.java')
start = media.index('public void searchMessagesInChat(String query, long dialogId, long mergeDialogId, int guid, int direction, long replyMessageId, boolean internal')
body = media[start:media.index('\n    public ', start + 10)]
assert body.count('NebulaSearchMediaFilters.filter(currentAccount, guid)') == 2, 'both search requests must take the chosen filter'
assert 'TL_inputMessagesFilterEmpty' not in body, 'a search request still ignores the filter'

filters = read(ui / 'NebulaSearchMediaFilters.java')
for name in ['PhotoVideo', 'Document', 'Url', 'Music', 'RoundVoice', 'Gif']:
    assert 'TL_inputMessagesFilter' + name + '()' in filters, name + ' filter missing'
assert re.search(r'ORDER = \{MEDIA, FILES, LINKS, MUSIC, VOICE, GIFS\}', filters)

chat = read(java / 'ui/ChatActivity.java')
assert 'NebulaSearchMediaFilters.attach(chatInputViewsContainer, searchContainer, currentAccount, classGuid' in chat
assert 'nebulaSearchFilters.reset();' in chat, 'closing search must clear the filter'
assert 'searchingUserMessages, searchingChatMessages, true, searchingReaction);' in chat[chat.index('private void nebulaSearchByMedia()'):], 'filter must combine with "from user"'
assert 'NebulaEmojiPacks.addExtra(currentAccount, message, stickerSets)' in chat
assert 'NebulaEmojiPacks.showRefresh(ChatActivity.this, stickerSets' in chat

photo = read(java / 'ui/PhotoViewer.java')
assert 'NebulaImageSearch.choose(parentActivity, centerImage.getBitmap()' in photo
assert 'menuItem.hideSubItem(gallery_menu_nebula_image_search)' in photo
search = read(ui / 'NebulaImageSearch.java')
assert 'Browser.openInTelegramBrowser' in search, 'results must open in the in-app browser'

harness = r'''package app.nebulagram.ui;
public class ExeraCheck {
 static void eq(Object a, Object b, String m){ if(!java.util.Objects.equals(a,b)) throw new AssertionError(m+": "+a+" != "+b); }
 public static void main(String[] args){
  eq(NebulaSentFromRules.round(null, true), NebulaSentFromRules.ANDROID, "android round");
  eq(NebulaSentFromRules.round("video.mp4", false), NebulaSentFromRules.IOS, "ios round");
  eq(NebulaSentFromRules.round("clip.mp4", true), NebulaSentFromRules.UNKNOWN, "renamed round");
  eq(NebulaSentFromRules.round(null, false), NebulaSentFromRules.UNKNOWN, "bare round");
  byte[] wave = new byte[63];
  eq(NebulaSentFromRules.voice("audio/ogg", null, wave), NebulaSentFromRules.OFFICIAL_APP, "app voice");
  eq(NebulaSentFromRules.voice("audio/ogg", null, null), NebulaSentFromRules.NON_STANDARD, "no waveform");
  eq(NebulaSentFromRules.voice("audio/ogg", "voice.ogg", wave), NebulaSentFromRules.NON_STANDARD, "named voice");
  eq(NebulaSentFromRules.voice("audio/mpeg", null, wave), NebulaSentFromRules.NON_STANDARD, "mp3 voice");
  for (int v = 0; v <= 4; v++) { if (NebulaSentFromRules.label(v, true).isEmpty() || NebulaSentFromRules.label(v, false).isEmpty()) throw new AssertionError("label"); }
  eq(NebulaImageSearchLinks.lensResult("/search?p=abc"), "https://lens.google.com/search?p=abc", "relative lens");
  eq(NebulaImageSearchLinks.lensResult("https://lens.google.com/search?p=1"), "https://lens.google.com/search?p=1", "lens");
  eq(NebulaImageSearchLinks.lensResult("https://evil.example/google.com"), null, "foreign host");
  eq(NebulaImageSearchLinks.lensResult("https://google.com.evil.example/"), null, "suffix host");
  eq(NebulaImageSearchLinks.lensResult("http://lens.google.com/"), null, "plain http");
  eq(NebulaImageSearchLinks.yandexResult("{\"cbir_id\":\"4/a b\",\"url\":\"https:\\/\\/avatars.mds.yandex.net\\/get-images-cbir\\/1\\/orig\"}"),
     "https://yandex.ru/images/search?rpt=imageview&url=https%3A%2F%2Favatars.mds.yandex.net%2Fget-images-cbir%2F1%2Forig&cbir_id=4%2Fa+b", "yandex");
  eq(NebulaImageSearchLinks.yandexResult("{\"error\":1}"), null, "yandex error");
  eq(NebulaImageSearchLinks.home(NebulaImageSearchLinks.YANDEX), NebulaImageSearchLinks.YANDEX_HOME, "fallback");
  System.out.println("Search filters, image search links, emoji packs and sent-from rules hold");
 }
}'''
with tempfile.TemporaryDirectory(prefix='nebula-exera-') as folder:
    out = Path(folder)
    (out / 'ExeraCheck.java').write_text(harness, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', folder, str(ui / 'NebulaSentFromRules.java'),
                    str(ui / 'NebulaImageSearchLinks.java'), str(out / 'ExeraCheck.java')], check=True)
    subprocess.run(['java', '-cp', folder, 'app.nebulagram.ui.ExeraCheck'], check=True)
