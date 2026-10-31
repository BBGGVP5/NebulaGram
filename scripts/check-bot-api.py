"""Check bot TL field order and response parsing against public schema constants."""
from pathlib import Path
import subprocess
import tempfile
root=Path(__file__).resolve().parents[1]
ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
sources={
'org/telegram/tgnet/TLObject.java':'''package org.telegram.tgnet;public class TLObject {public void readParams(InputSerializedData in,boolean e){}public void serializeToStream(OutputSerializedData out){}public TLObject deserializeResponse(InputSerializedData in,int c,boolean e){return null;}}''',
'org/telegram/tgnet/InputSerializedData.java':'package org.telegram.tgnet;public interface InputSerializedData {int readInt32(boolean e);long readInt64(boolean e);String readString(boolean e);}',
'org/telegram/tgnet/OutputSerializedData.java':'package org.telegram.tgnet;public interface OutputSerializedData {void writeInt32(int x);void writeInt64(long x);void writeString(String x);}',
'org/telegram/tgnet/TLRPC.java':'''package org.telegram.tgnet;public class TLRPC {
 public static class InputUser extends TLObject {public void serializeToStream(OutputSerializedData o){o.writeInt32(0xf7c1b13f);}}
 public static class Bool extends TLObject {public static Bool TLdeserialize(InputSerializedData i,int c,boolean e){return new Bool();}}
 public static class TL_chatAdminRights extends TLObject {public void serializeToStream(OutputSerializedData o){o.writeInt32(0x5fb224d5);o.writeInt32(8);}}
 public static class Update extends TLObject {public static Update TLdeserialize(InputSerializedData i,int c,boolean e){return null;}}
 public static class User extends TLObject {public static User TLdeserialize(InputSerializedData i,int c,boolean e){throw new AssertionError();}}
 public static class Chat extends TLObject {public static Chat TLdeserialize(InputSerializedData i,int c,boolean e){throw new AssertionError();}}
}''',
'org/telegram/tgnet/tl/TL_account.java':'''package org.telegram.tgnet.tl;import org.telegram.tgnet.*;public class TL_account {
 public static class TL_businessBotRights extends TLObject {public int flags;public static TL_businessBotRights TLdeserialize(InputSerializedData in,int c,boolean e){if(c!=0xa0624cf7)throw new AssertionError();var r=new TL_businessBotRights();r.flags=in.readInt32(e);return r;}}
}''',
'BotApiCheck.java':r'''
import org.telegram.tgnet.*;import app.nebulagram.ui.*;import java.util.*;
class BotApiCheck {
 static class Data implements InputSerializedData,OutputSerializedData {
  ArrayList<Object> values=new ArrayList<>();int pos;
  Data(Object... v){values.addAll(Arrays.asList(v));}
  public int readInt32(boolean e){return (Integer)values.get(pos++);}public long readInt64(boolean e){return (Long)values.get(pos++);}public String readString(boolean e){return (String)values.get(pos++);}
  public void writeInt32(int x){values.add(x);}public void writeInt64(long x){values.add(x);}public void writeString(String x){values.add(x);}
 }
 static void ok(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[]args){
  var profile=new NebulaBotApi.GetProfile();profile.language="ru";var data=new Data();profile.serializeToStream(data);
  ok(data.values.equals(Arrays.asList(0xdcd914fd,0,"ru")));
  profile.bot=new TLRPC.InputUser();data=new Data();profile.serializeToStream(data);ok(data.values.equals(Arrays.asList(0xdcd914fd,1,0xf7c1b13f,"ru")));
  var p=(NebulaBotApi.Profile)profile.deserializeResponse(new Data("Bot","About","Description"),0xe8a775b0,true);ok(p.name.equals("Bot")&&p.about.equals("About")&&p.description.equals("Description"));
  for(boolean channel:new boolean[]{false,true}){var rights=new NebulaBotApi.SetRights();rights.channel=channel;rights.rights=new TLRPC.TL_chatAdminRights();data=new Data();rights.serializeToStream(data);ok(data.values.equals(Arrays.asList(channel?0x788464e1:0x925ec9ea,0x5fb224d5,8)));}
  var q=new NebulaBotApi.GetConnection();q.id="connection-1";data=new Data();q.serializeToStream(data);ok(data.values.equals(Arrays.asList(0x76a86270,"connection-1")));
  for(boolean combined:new boolean[]{false,true})for(int flags:new int[]{0,2,4,6}){
   data=new Data(0x1cb5c415,1,0x8ae5c97a,0x8f34b2f5,flags,"connection-1",123L,2,1700000000);
   if((flags&4)!=0){data.writeInt32(0xa0624cf7);data.writeInt32(3);}
   data.writeInt32(5);data.writeInt32(0x1cb5c415);data.writeInt32(0);data.writeInt32(0x1cb5c415);data.writeInt32(0);data.writeInt32(1700000001);if(combined)data.writeInt32(4);data.writeInt32(5);
   var result=(NebulaBotApi.Connection)q.deserializeResponse(data,combined?0x725b04c3:0x74ae4240,true);
   ok(result.id.equals(q.id)&&result.user==123L&&result.dc==2&&result.disabled==((flags&2)!=0));ok((result.rights!=null)==((flags&4)!=0));ok(data.pos==data.values.size());
  }
  for(int size:new int[]{-1,10001})try{q.deserializeResponse(new Data(0x1cb5c415,size),0x74ae4240,true);throw new AssertionError();}catch(RuntimeException expected){}
  try{profile.deserializeResponse(new Data(),0,true);throw new AssertionError();}catch(RuntimeException expected){}
  System.out.println("Bot profile flags, menu-independent admin rights, business connection variants and malformed responses passed (schema-level test)");
 }
}
'''}
with tempfile.TemporaryDirectory(prefix='nebula-bot-api-') as directory:
    work=Path(directory)
    for name,source in sources.items():
        p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(ui/'NebulaBotApi.java'),*[str(p) for p in work.rglob('*.java')]],check=True)
    subprocess.run(['java','-cp',str(work),'BotApiCheck'],check=True)
