"""Execute protected-copy policy/preparation with deterministic cache and queue fakes.

No account or network is used; Telegram/Android compilation is a separate check.
"""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parent.parent
source = (ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaProtectedCopies.java').read_text(encoding='utf-8')
body = source[source.index('    public static boolean isProtected('):source.index('    public static void error(')]
work = ROOT / 'build/protected-copies-check'
work.mkdir(parents=True, exist_ok=True)
java = r'''
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
public class Check {
 static class TLRPC {
  static class MessageEntity {int offset,length;}
  static class PhotoSize {int size=10;}
  static class TL_photo {long id=90,access_hash=91;ArrayList<PhotoSize> sizes=new ArrayList<>(Arrays.asList(new PhotoSize()));}
  static class TL_document {long id=80,access_hash=81,size=10;byte[] file_reference={1};}
  static class Media {TL_photo photo;TL_document document;}
  static class Message {boolean noforwards;String message="hello",attachPath="";Media media;ArrayList<MessageEntity> entities=new ArrayList<>();}
  static class Chat {boolean noforwards;String title="Channel";}
  static class User {} static class UserFull {boolean noforwards_my_enabled,noforwards_peer_enabled;}
 }
 static class MessageObject {static final int TYPE_TEXT=0;int type;long peer=1;TLRPC.Message messageOwner=new TLRPC.Message();long getDialogId(){return peer;}}
 static class MessagesController {
  static MessagesController instance=new MessagesController();static MessagesController getInstance(int a){return instance;}
  TLRPC.Chat chat=new TLRPC.Chat();TLRPC.UserFull full=new TLRPC.UserFull();int maxMessageLength=4096;
  TLRPC.Chat getChat(long p){return chat;}TLRPC.UserFull getUserFull(long p){return full;}
  TLRPC.User getUser(long p){return new TLRPC.User();}int getCaptionMaxLengthLimit(){return 1024;}
 }
 static class UserObject {static String getUserName(TLRPC.User u){return "Author";}}
 static class NebulaContentProtection {static boolean on=true;static boolean enabled(){return on;}}
 static class NebulaText {static String text(String ru,String en){return en;}}
 static class UserConfig {static long id=7;static boolean active=true;static UserConfig getInstance(int a){return new UserConfig();}long getClientUserId(){return id;}boolean isClientActivated(){return active;}}
 static class AndroidUtilities {static boolean copyFile(File a,File b)throws IOException{Files.copy(a.toPath(),b.toPath(),StandardCopyOption.REPLACE_EXISTING);return true;}static int getPhotoSize(boolean b){return 1280;}static void runOnUIThread(Runnable r){ui.add(r);}}
 static class Utilities {static Queue globalQueue=new Queue();}
 static class Queue {void postRunnable(Runnable r){background.add(r);}}
 static final ArrayList<Runnable> background=new ArrayList<>(),ui=new ArrayList<>();
 static void drain(){while(!background.isEmpty())background.remove(0).run();while(!ui.isEmpty())ui.remove(0).run();}
 static class FileLoader {
  static final int MEDIA_DIR_CACHE=4;static File getDirectory(int kind){return file.getParentFile();}
  static File file; static FileLoader getInstance(int a){return new FileLoader();}
  File getPathToMessage(TLRPC.Message m){return file;}
  static TLRPC.PhotoSize getClosestPhotoSizeWithSize(ArrayList<TLRPC.PhotoSize> s,int n,boolean x,Object y,boolean z){return s.get(0);}
 }
 static class SendMessagesHelper {
  static boolean failPhoto;
  static SendMessagesHelper getInstance(int a){return new SendMessagesHelper();}
  TLRPC.TL_photo generatePhotoSizes(String p,Object uri){if(failPhoto)return null;TLRPC.TL_photo photo=new TLRPC.TL_photo();photo.id=0;photo.access_hash=0;return photo;}
 }
 static class NebulaForwardEditing {
  static class EditedMessage extends MessageObject {MessageObject source;}
  static boolean supported(int account,MessageObject m){return m.type!=99;}
  // Fake only TL deserialization; assertions below test the real transport preparation.
  static ArrayList<MessageObject> copies(int account,ArrayList<MessageObject> input){
   ArrayList<MessageObject> result=new ArrayList<>();
   for(MessageObject original:input){
    EditedMessage m=new EditedMessage();m.source=original instanceof EditedMessage?((EditedMessage)original).source:original;
    m.type=original.type;m.peer=original.peer;m.messageOwner.message=original.messageOwner.message;m.messageOwner.attachPath=original.messageOwner.attachPath;
    m.messageOwner.noforwards=original.messageOwner.noforwards;
    for(TLRPC.MessageEntity entity:original.messageOwner.entities){TLRPC.MessageEntity e=new TLRPC.MessageEntity();e.offset=entity.offset;e.length=entity.length;m.messageOwner.entities.add(e);}
    if(original.messageOwner.media!=null){m.messageOwner.media=new TLRPC.Media();
     if(original.messageOwner.media.photo!=null)m.messageOwner.media.photo=new TLRPC.TL_photo();
     if(original.messageOwner.media.document!=null)m.messageOwner.media.document=new TLRPC.TL_document();}
    result.add(m);
   }
   return result;
  }
 }
 static class NebulaProtectedCopies {
 BODY
 static ArrayList<String> errors=new ArrayList<>();static void error(String s){errors.add(s);}
 }
 static void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
 static MessageObject item(int kind){MessageObject m=new MessageObject();m.type=kind;m.messageOwner.noforwards=true;
  if(kind!=0){m.messageOwner.media=new TLRPC.Media();if(kind==1)m.messageOwner.media.photo=new TLRPC.TL_photo();else m.messageOwner.media.document=new TLRPC.TL_document();}return m;}
 public static void main(String[] args)throws Exception{
  FileLoader.file=Files.createTempFile("nebula-copy-", ".bin").toFile();
  try {
   Files.write(FileLoader.file.toPath(),new byte[10]);
   MessageObject text=item(0);TLRPC.MessageEntity entity=new TLRPC.MessageEntity();entity.offset=1;text.messageOwner.entities.add(entity);
   ArrayList<MessageObject> originals=new ArrayList<>(Arrays.asList(text,item(1),item(2)));
   ArrayList<MessageObject> copies=NebulaProtectedCopies.create(0,originals,false,false);
   require(copies.get(0).messageOwner.message.equals("Source: Author\n\nhello"),"explicit attribution");
   require(text.messageOwner.message.equals("hello")&&entity.offset==1,"source text/entities mutated");
   require(copies.get(0).messageOwner.entities.get(0).offset==17,"copy entities not shifted");
   require(NebulaProtectedCopies.create(0,originals,true,false).get(0).messageOwner.message.equals("hello"),"anonymous copy");
   copies=NebulaProtectedCopies.create(0,originals,false,true);
   require(copies.get(1).messageOwner.message.equals("Source: Author\n\n"),"hide caption lost attribution");
   final ArrayList<ArrayList<MessageObject>> sent=new ArrayList<>();
   require(NebulaProtectedCopies.prepareUploads(0,copies,sent::add)==0&&sent.isEmpty()&&background.size()==1,"upload preparation not deferred");drain();
   require(sent.size()==1&&sent.get(0).size()==3,"batch not delivered together");
   require(sent.get(0).get(1).messageOwner.media.photo.id==0,"photo reference reused");
   require(sent.get(0).get(2).messageOwner.media.document.id==0&&sent.get(0).get(2).messageOwner.media.document.access_hash==0,"document reference reused");
   require(originals.get(1).messageOwner.media.photo.id==90&&originals.get(2).messageOwner.media.document.access_hash==81,"original attachment mutated");
   File staged=new File(sent.get(0).get(2).messageOwner.attachPath);
   require(!staged.equals(FileLoader.file)&&staged.length()==10&&FileLoader.file.length()==10,"source cache not isolated");Files.delete(staged.toPath());
   sent.clear();Files.write(FileLoader.file.toPath(),new byte[3]);
   require(NebulaProtectedCopies.prepareUploads(0,copies,sent::add)!=0&&background.isEmpty()&&sent.isEmpty(),"partial cache accepted");
   Files.write(FileLoader.file.toPath(),new byte[10]);
   NebulaProtectedCopies.prepareUploads(0,copies,sent::add);NebulaContentProtection.on=false;drain();
   require(sent.isEmpty(),"opt-out while preparing ignored");NebulaContentProtection.on=true;
   NebulaProtectedCopies.prepareUploads(0,copies,sent::add);UserConfig.id++;drain();require(sent.isEmpty(),"sent from different login");
   SendMessagesHelper.failPhoto=true;NebulaProtectedCopies.prepareUploads(0,copies,sent::add);drain();require(sent.isEmpty(),"partial send after preparation failure");SendMessagesHelper.failPhoto=false;
   MessageObject longText=item(0);longText.messageOwner.message="x".repeat(4090);
   boolean failed=false;try{NebulaProtectedCopies.create(0,new ArrayList<>(Arrays.asList(longText)),false,false);}catch(IllegalArgumentException expected){failed=true;}
   require(failed,"attribution overflow not rejected");
   System.out.println("Protected copies passed: source labels/entities, cache completeness, fresh media uploads, deferred preparation, no partial send, opt-out/account guards");
  }finally{Files.deleteIfExists(FileLoader.file.toPath());}
 }
}
'''.replace('BODY', body)
(work / 'Check.java').write_text(java, encoding='utf-8', newline='\n')
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(work / 'Check.java')], check=True)
subprocess.run(['java', '-cp', str(work), 'Check'], check=True)
