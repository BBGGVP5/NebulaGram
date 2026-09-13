"""Run the production eligibility/batch builder with inert transport models (no messages sent)."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent.parent
UI = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
source = (UI / 'NebulaForwardEditing.java').read_text(encoding='utf-8')
supported = source[source.index('    public static boolean supported('):source.index('    public static boolean canEdit(')]
protection = (UI / 'NebulaProtectedCopies.java').read_text(encoding='utf-8')
raw_detection = protection[protection.index('    public static boolean isProtected('):protection.index('    public static ArrayList<MessageObject> create(')]
raw_detection = raw_detection.replace('NebulaForwardEditing.EditedMessage', 'EditedMessage')
batch = source[source.index('    public static int sendBatch('):source.index('    public static CharSequence draft(')]
work = ROOT / 'build/forward-editing-check'
work.mkdir(parents=True, exist_ok=True)
java = r'''
import java.util.*;
public class Check {
 static class TLRPC {
  static class Photo {long id=1;} static class Document {long id=1;}
  static class TL_photo extends Photo {} static class TL_document extends Document {}
  static class MessageMedia {Photo photo;Document document;int ttl_seconds;}
  static class TL_messageMediaEmpty extends MessageMedia {} static class TL_messageMediaWebPage extends MessageMedia {}
  static class TL_messageMediaPhoto extends MessageMedia {} static class TL_messageMediaDocument extends MessageMedia {}
  static class Message {Object rich_message; boolean noforwards,invert_media;int ttl_period,destroyTime;
   String message="original",attachPath="cached";MessageMedia media;ArrayList<Object> entities=new ArrayList<>();}
  static class Chat {boolean noforwards;} static class UserFull {boolean noforwards_my_enabled,noforwards_peer_enabled;}
  static class MessageEntity {}
 }
 static class MessageObject {
  static final int TYPE_TEXT=0; int type,id=1; long group,dialog=123; int kind; boolean secret,blur,spoiler;
  TLRPC.Message messageOwner=new TLRPC.Message();
  int getId(){return id;}long getDialogId(){return dialog;}long getGroupId(){return group;}
  boolean isSecretMedia(){return secret;}boolean needDrawBluredPreview(){return blur;}
  boolean isSticker(){return kind==5;}boolean isAnimatedSticker(){return kind==6;}
  boolean isVoice(){return kind==7;}boolean isRoundVideo(){return kind==8;}
  boolean isVideo(){return kind==2;}boolean isMusic(){return kind==3;}boolean isGif(){return kind==4;}
  boolean hasMediaSpoilers(){return spoiler;}
 }
 static class EditedMessage extends MessageObject {MessageObject source=new MessageObject();}
 static class DialogObject {static boolean isEncryptedDialog(long x){return false;}}
 static class MessagesController {
  static TLRPC.Chat chat = new TLRPC.Chat(); static TLRPC.UserFull user = new TLRPC.UserFull();
  static MessagesController getInstance(int x){return new MessagesController();}
  TLRPC.Chat getChat(long x){return chat;}TLRPC.UserFull getUserFull(long x){return user;}
 }
 static class NebulaContentProtection {static boolean active;static boolean enabled(){return active;}}
 static class NebulaProtectedCopies {
 RAW_DETECTION
 static boolean missingFile;
 static int prepareUploads(int a,ArrayList<MessageObject> m,java.util.function.Consumer<ArrayList<MessageObject>> send){if(missingFile)return 17;send.accept(m);return 0;}
 }

 static class MessageSuggestionParams {}
 static class Utilities {static Random random=new Random(12);}
 static class SendMessagesHelper {
  ArrayList<SendMessageParams> sent=new ArrayList<>();void sendMessage(SendMessageParams p){sent.add(p);}
  static class SendMessageParams {
   long payStars,monoForumPeer;MessageSuggestionParams suggestionParams;boolean invert_media;
   String text,kind;Object attachment,topic,parent;HashMap<String,String> params;boolean notify,spoiler;int date,repeat;
   static SendMessageParams of(Object... a){
    SendMessageParams p=new SendMessageParams(); int shift;
    if(a[0] instanceof TLRPC.Photo){p.kind="photo";p.attachment=a[0];shift=0;}
    else if(a[0] instanceof TLRPC.Document){p.kind="document";p.attachment=a[0];shift=1;}
    else {p.kind="text";p.text=(String)a[0];p.topic=a[3];p.notify=(Boolean)a[9];p.date=(Integer)a[10];p.repeat=(Integer)a[11];return p;}
    p.text=(String)a[5+shift];p.topic=a[4+shift];p.params=(HashMap<String,String>)a[8+shift];
    p.notify=(Boolean)a[9+shift];p.date=(Integer)a[10+shift];p.repeat=(Integer)a[11+shift];p.parent=a[13+shift];p.spoiler=(Boolean)a[a.length-1];return p;
   }
  }
 }
''' + supported + batch + r'''
 static EditedMessage item(int kind,long group,String text){
  EditedMessage m=new EditedMessage();m.kind=kind;m.group=group;m.messageOwner.message=text;m.type=kind==0?0:1;
  if(kind==1){m.messageOwner.media=new TLRPC.TL_messageMediaPhoto();m.messageOwner.media.photo=new TLRPC.TL_photo();}
  else if(kind!=0){m.messageOwner.media=new TLRPC.TL_messageMediaDocument();m.messageOwner.media.document=new TLRPC.TL_document();}
  return m;
 }
 static void require(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  for(int kind=0;kind<=8;kind++)require(supported(0,item(kind,0,"text"))==(kind<5),"supported type "+kind);
  EditedMessage p=item(1,0,"");p.messageOwner.media.ttl_seconds=1;require(!supported(0,p),"TTL media");
  p=item(1,0,"");p.messageOwner.media.photo.id=0;require(!supported(0,p),"unsent media");
  p=item(0,0,"");require(!supported(0,p),"empty text");
  p=item(1,0,"");p.source.messageOwner.noforwards=true;require(!supported(0,p),"protected");
  MessagesController.user.noforwards_peer_enabled=true;require(!supported(0,item(1,0,"")),"protected chat");
  NebulaContentProtection.active=true;require(supported(0,item(1,0,"")),"opt-in protected copy");
  require(NebulaProtectedCopies.requiresCopy(0,new ArrayList<>(Arrays.asList(item(0,0,"text")))),"protected routing");
  MessagesController.user.noforwards_peer_enabled=false;
  p=item(0,0,"text");p.source.dialog=-55;MessagesController.chat.noforwards=true;
  require(NebulaProtectedCopies.isProtected(0,p),"raw chat restriction on original source");
  MessagesController.chat.noforwards=false;p.source.messageOwner.noforwards=true;
  require(NebulaProtectedCopies.isProtected(0,p),"original message restriction");
  NebulaContentProtection.active=false;
  require(!NebulaProtectedCopies.requiresCopy(0,new ArrayList<>(Arrays.asList(p))),"opt-out routing");
  p=item(1,0,"");NebulaContentProtection.active=true;p.secret=true;require(!supported(0,p),"secret media with opt-in");
  p.secret=false;p.messageOwner.destroyTime=1;require(!supported(0,p),"disappearing media with opt-in");NebulaContentProtection.active=false;
  for(boolean hide:new boolean[]{false,true})for(boolean notify:new boolean[]{false,true}) {
   ArrayList<MessageObject> messages=new ArrayList<>(Arrays.asList(item(1,7,"photo caption"),item(2,7,"video caption"),item(0,0,"text"),item(9,8,"file caption")));
   MessageObject topic=new MessageObject();MessageSuggestionParams suggestion=new MessageSuggestionParams();
   messages.get(0).spoiler=true;messages.get(0).messageOwner.invert_media=true;
   SendMessagesHelper h=new SendMessagesHelper();
   require(sendBatch(h,0,messages,456,hide,notify,1234,44,topic,15,999,suggestion,true,true,true,true,true)==0,"send result");
   require(h.sent.size()==4,"batch size");
   for(int i=0;i<4;i++){
    SendMessagesHelper.SendMessageParams s=h.sent.get(i);MessageObject m=messages.get(i);
    require(s.text.equals(hide&&i!=2?"":m.messageOwner.message),"caption or text");
    require(s.notify==notify&&s.date==1234&&s.repeat==44&&s.payStars==15&&s.monoForumPeer==999&&s.topic==topic&&s.suggestionParams==suggestion,"send metadata");
    if(i!=2)require(s.parent==((EditedMessage)m).source,"file-reference retry parent");
   }
   require(h.sent.get(0).params.get("groupId").equals(h.sent.get(1).params.get("groupId")),"album split");
   require(!h.sent.get(0).params.containsKey("final")&&h.sent.get(1).params.containsKey("final")&&h.sent.get(3).params.containsKey("final"),"group completion");
   require(!h.sent.get(1).params.get("groupId").equals(h.sent.get(3).params.get("groupId")),"albums merged");
   require(h.sent.get(0).spoiler&&h.sent.get(0).invert_media,"media presentation");
   h=new SendMessagesHelper();NebulaProtectedCopies.missingFile=true;
   require(sendBatch(h,0,messages,456,false,true,0,0,topic,0,0,null,true,true,true,true,true)!=0&&h.sent.isEmpty(),"partial missing-cache batch sent");
   NebulaProtectedCopies.missingFile=false;
   h=new SendMessagesHelper();require(sendBatch(h,0,messages,456,false,true,0,0,topic,0,0,null,true,false,true,true,true)!=0&&h.sent.isEmpty(),"partial banned album sent");
  }
  System.out.println("Forward editing passed: eligibility, albums, captions, source references, metadata, no partial send on restrictions");
 }
}
'''
java = java.replace('RAW_DETECTION', raw_detection)
# The inert TL model uses Object entities; production compilation is checked separately against Telegram.
java = java.replace('ArrayList<TLRPC.MessageEntity> entities = hideCaption', 'ArrayList<Object> entities = hideCaption')
(work / 'Check.java').write_text(java, encoding='utf-8', newline='\n')
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(work / 'Check.java')], check=True)
subprocess.run(['java', '-cp', str(work), 'Check'], check=True)
assert 'entity.serializeToStream(buffer)' in source and 'buffer.reuse()' in source
assert 'getSelectedMessages(selected)' in (ROOT / 'patches/android/0089-edit-forward-draft.patch').read_text(encoding='utf-8')
assert 'candidate' not in source
print('Forward preview/source isolation guards passed')

patch = (ROOT / 'patches/android/0091-inline-forward-copies.patch').read_text(encoding='utf-8')
assert 'new AlertDialog' not in source and 'new EditText' not in source
assert 'composer.editNebulaForwardCopy' in source
assert 'setEditingMessageObject(copy, null,' in patch
assert 'return isNebulaForwardEditing() ? null : editingMessageObject' in patch
assert 'nebulaForwardEditApply = null;' in patch
assert patch.index('messagePreviewParams.updateForward(null, dialog_id);') < patch.index('hideFieldPanel(true);')
assert 'hasAudioToSend()' in patch and 'doneButton.setOnLongClickListener(v -> false)' in patch
# Native editor completion branches to a local callback before the original remote edit body.
local = patch[patch.index('+        if (isNebulaForwardEditing()) {'):]
assert local.index('apply.accept(copy)') < local.index('return;', local.index('apply.accept(copy)')) < local.index('editingMessageObject.editingMessage =')
assert 'setEditingMessageObject(null, null, false);' in local
print('Inline editor guards passed: no modal, no remote edit, preserved original group, cancel and explicit send')
