"""Keep quick-copy on the native styled-message path instead of raw server text."""
from pathlib import Path
import re
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
chat = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/ChatActivity.java').read_text(encoding='utf-8')
start = chat.index('public static CharSequence getMessageContent(')
end = chat.index('\n    private void unpinMessage(', start)
content = chat[start:end].strip()
action = re.search(r'else if \(action == 3\) (AndroidUtilities.addToClipboard\([^;]+\);)', chat).group(1)
code = '''
import java.util.*;
class PremiumCopyCheck {
 static class Marked implements CharSequence {
  String text;long emoji;Marked(String text,long emoji){this.text=text;this.emoji=emoji;}
  public int length(){return text.length();}public char charAt(int i){return text.charAt(i);}
  public CharSequence subSequence(int a,int b){return new Marked(text.substring(a,b),emoji);}
  public String toString(){return text;}
 }
 static class SpannableStringBuilder implements CharSequence {
  StringBuilder text=new StringBuilder();List<Long> ids=new ArrayList<>();
  SpannableStringBuilder append(CharSequence s){text.append(s);
   if(s instanceof Marked)ids.add(((Marked)s).emoji);return this;}
  public int length(){return text.length();}public char charAt(int i){return text.charAt(i);}
  public CharSequence subSequence(int a,int b){return text.subSequence(a,b);}
  public String toString(){return text.toString();}
 }
 static class MessageObject {int currentAccount;Owner messageOwner=new Owner();CharSequence caption,messageText;
  long getFromChatId(){return 1;}}
 static class Owner {String message="raw fallback",restriction_reason;}
 static class TLRPC {static class User {String first_name="Author",last_name="";}static class Chat {String title;}}
 static class MessagesController {
  static MessagesController getInstance(int account){return new MessagesController();}
  String getRestrictionReason(String reason){return reason;}
  TLRPC.User getUser(long id){return new TLRPC.User();}TLRPC.Chat getChat(long id){return null;}
 }
 static class ContactsController {static String formatName(String a,String b){return a;}}
 static class TextUtils {static boolean isEmpty(CharSequence text){return text==null||text.length()==0;}}
 static class AndroidUtilities {static CharSequence copied;static void addToClipboard(CharSequence text){copied=text;}}
 CONTENT
 static void quickCopy(MessageObject messageObject){ACTION}
 static void check(boolean b){if(!b)throw new AssertionError("Styled clipboard payload lost");}
 public static void main(String[] args) {
  for(boolean caption:new boolean[]{false,true})for(boolean name:new boolean[]{false,true}) {
   MessageObject m=new MessageObject();m.messageText=new Marked("Hello 😀",901);
   if(caption)m.caption=new Marked("Caption ❤️",902);
   quickCopy(m);SpannableStringBuilder copied=(SpannableStringBuilder)AndroidUtilities.copied;
   check(copied.ids.equals(Arrays.asList(caption?902L:901L)));
   SpannableStringBuilder named=(SpannableStringBuilder)getMessageContent(m,0,name);
   check(named.ids.equals(copied.ids)&&named.toString().startsWith(name?"Author:\\n":""));
   m.messageOwner.restriction_reason="Restricted";quickCopy(m);
   copied=(SpannableStringBuilder)AndroidUtilities.copied;
   check(copied.ids.isEmpty()&&copied.toString().equals("Restricted"));
  }
  System.out.println("Quick-copy retains styled text/captions and native restriction handling");
 }
}
'''.replace('CONTENT', content.replace('app.nebulagram.ui.NebulaClipboardText.restore(messageObject, messageObject.caption)', 'messageObject.caption')
    .replace('app.nebulagram.ui.NebulaClipboardText.restore(messageObject, messageObject.messageText)', 'messageObject.messageText')).replace('ACTION', action)
work = root / 'build/premium-copy-check'
work.mkdir(parents=True, exist_ok=True)
path = work / 'PremiumCopyCheck.java'
path.write_text(code, encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', str(path)], check=True)
subprocess.run(['java', '-cp', str(work), 'PremiumCopyCheck'], check=True)

# Execute the production restoration helper with recorded span ranges. Telegram
# entity offsets are UTF-16; copied captions and translated display text differ.
stubs = {
 'android/text/Spanned.java': 'package android.text; public interface Spanned {int SPAN_EXCLUSIVE_EXCLUSIVE=33;}',
 'android/text/TextUtils.java': 'package android.text; public class TextUtils {public static boolean equals(CharSequence a,CharSequence b){return a==b || a!=null&&b!=null&&a.toString().equals(b.toString());}}',
 'android/text/SpannableStringBuilder.java': '''package android.text;
 import java.util.*;import java.lang.reflect.Array;
 public class SpannableStringBuilder implements CharSequence {
  public String text;public ArrayList<Object> spans=new ArrayList<>();public ArrayList<int[]> ranges=new ArrayList<>();
  public SpannableStringBuilder(CharSequence s){text=s.toString();if(s instanceof SpannableStringBuilder){spans.addAll(((SpannableStringBuilder)s).spans);ranges.addAll(((SpannableStringBuilder)s).ranges);}}
  public int length(){return text.length();}public char charAt(int i){return text.charAt(i);}
  public CharSequence subSequence(int a,int b){return text.subSequence(a,b);}public String toString(){return text;}
  public <T>T[] getSpans(int start,int end,Class<T> type){ArrayList<T> result=new ArrayList<>();for(int i=0;i<spans.size();i++)if(type.isInstance(spans.get(i))&&ranges.get(i)[0]<end&&ranges.get(i)[1]>start)result.add(type.cast(spans.get(i)));return result.toArray((T[])Array.newInstance(type,result.size()));}
  public void removeSpan(Object o){int i=spans.indexOf(o);if(i>=0){spans.remove(i);ranges.remove(i);}}
  public void setSpan(Object o,int a,int b,int flags){spans.add(o);ranges.add(new int[]{a,b});}
 }''',
 'org/telegram/tgnet/TLRPC.java': '''package org.telegram.tgnet;import java.util.*;
 public class TLRPC {public static class Document {public long id;}
 public static class MessageEntity {public int offset,length;}
 public static class TL_messageEntityCustomEmoji extends MessageEntity {public long document_id;public Document document;}
 public static class Message {public String message;public ArrayList<MessageEntity> entities=new ArrayList<>();}}''',
 'org/telegram/messenger/MessageObject.java': '''package org.telegram.messenger;import org.telegram.tgnet.TLRPC;
 public class MessageObject {public TLRPC.Message messageOwner=new TLRPC.Message();}''',
 'org/telegram/ui/Components/AnimatedEmojiSpan.java': '''package org.telegram.ui.Components;
 public class AnimatedEmojiSpan {public long id;public AnimatedEmojiSpan(long id,Object metrics){this.id=id;}}''',
 'RestoreCheck.java': '''import android.text.*;import org.telegram.tgnet.TLRPC;import org.telegram.messenger.MessageObject;
 import org.telegram.ui.Components.AnimatedEmojiSpan;import app.nebulagram.ui.NebulaClipboardText;
 class RestoreCheck {
 static void check(boolean b){if(!b)throw new AssertionError("Premium emoji entities lost or applied at wrong offsets");}
 static TLRPC.TL_messageEntityCustomEmoji entity(int a,int n,long id){TLRPC.TL_messageEntityCustomEmoji e=new TLRPC.TL_messageEntityCustomEmoji();e.offset=a;e.length=n;e.document_id=id;return e;}
 public static void main(String[] args){
  MessageObject m=new MessageObject();m.messageOwner.message="A😀 B❤️";
  m.messageOwner.entities.add(entity(1,2,9007199254740993L));
  TLRPC.TL_messageEntityCustomEmoji second=entity(5,2,0);second.document=new TLRPC.Document();second.document.id=902;m.messageOwner.entities.add(second);
  m.messageOwner.entities.add(entity(-1,2,3));m.messageOwner.entities.add(entity(1,Integer.MAX_VALUE,4));m.messageOwner.entities.add(entity(0,1,0));
  SpannableStringBuilder displayed=new SpannableStringBuilder(m.messageOwner.message);Object bold=new Object();displayed.setSpan(bold,0,7,33);displayed.setSpan(new AnimatedEmojiSpan(9,null),1,3,33);
  SpannableStringBuilder copied=(SpannableStringBuilder)NebulaClipboardText.restore(m,displayed);
  AnimatedEmojiSpan[] emoji=copied.getSpans(0,copied.length(),AnimatedEmojiSpan.class);
  check(emoji.length==2&&emoji[0].id==9007199254740993L&&emoji[1].id==902&&copied.spans.contains(bold));
  check(displayed.getSpans(0,7,AnimatedEmojiSpan.class)[0].id==9);
  check(copied.ranges.get(1)[0]==1&&copied.ranges.get(1)[1]==3&&copied.ranges.get(2)[0]==5);
  check(NebulaClipboardText.restore(m,"Translated text").toString().equals("Translated text"));
  check(NebulaClipboardText.restore(null,displayed)==displayed&&NebulaClipboardText.restore(m,null)==null);
  CharSequence plain=NebulaClipboardText.restore(m,m.messageOwner.message);check(plain instanceof SpannableStringBuilder);
  System.out.println("Emoji entity restoration passed: UTF-16 offsets, 64-bit IDs, captions, plain display, invalid ranges and formatting preservation");
 }}''',
}
sources = []
for name, body in stubs.items():
    p = work / name
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(body, encoding='utf-8')
    sources.append(str(p))
actual = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaClipboardText.java'
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), *sources, str(actual)], check=True)
subprocess.run(['java', '-cp', str(work), 'RestoreCheck'], check=True)
assert chat.count('NebulaClipboardText.restore(') >= 4
