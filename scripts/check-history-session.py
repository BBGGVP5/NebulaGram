"""Execute the real history loader against a deferred fake Telegram transport."""
from pathlib import Path
import subprocess
import tempfile
root=Path(__file__).resolve().parents[1]
ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
stubs={
'org/telegram/messenger/AndroidUtilities.java':'package org.telegram.messenger;public class AndroidUtilities {public static void runOnUIThread(Runnable r){r.run();}}',
'org/telegram/messenger/UserConfig.java':'package org.telegram.messenger;public class UserConfig {public static long owner=42;public static UserConfig getInstance(int a){return new UserConfig();}public long getClientUserId(){return owner;}}',
'org/telegram/messenger/DialogObject.java':'package org.telegram.messenger;public class DialogObject {public static boolean isEncryptedDialog(long d){return d==999;}}',
'org/telegram/messenger/MessagesController.java':'''package org.telegram.messenger;import org.telegram.tgnet.TLRPC;public class MessagesController {
 public static MessagesController getInstance(int a){return new MessagesController();} public TLRPC.InputPeer getInputPeer(long d){return new TLRPC.InputPeer(d);}
 public void putUsers(Object o,boolean b){}public void putChats(Object o,boolean b){}
}''',
'org/telegram/tgnet/TLObject.java':'package org.telegram.tgnet;public class TLObject {}',
'org/telegram/tgnet/TLRPC.java':'''package org.telegram.tgnet;import java.util.*;public class TLRPC {
 public static class InputPeer{public long dialog;public InputPeer(long d){dialog=d;}}
 public static class Message{public int id;public Message(int id){this.id=id;}}
 public static class messages_Messages extends TLObject {public ArrayList<Message> messages=new ArrayList<>();public Object users,chats;}
 public static class TL_messages_getReplies extends TLObject {public InputPeer peer;public int msg_id,offset_id,limit;}
 public static class TL_messages_getHistory extends TLObject {public InputPeer peer;public int offset_id,limit;}
 public static class TL_error {public String text="FLOOD_WAIT_10";}
}''',
'org/telegram/tgnet/ConnectionsManager.java':'''package org.telegram.tgnet;public class ConnectionsManager {
 public interface Callback{void run(TLObject response,TLRPC.TL_error error);}
 public static final ConnectionsManager instance=new ConnectionsManager(); public TLObject last; public Callback callback;public int calls,cancels;
 public static ConnectionsManager getInstance(int a){return instance;}
 public int sendRequest(TLObject q,Callback c){last=q;callback=c;return ++calls;}
 public void cancelRequest(int id,boolean force){cancels++;}
 public void reply(int... ids){TLRPC.messages_Messages page=new TLRPC.messages_Messages();for(int id:ids)page.messages.add(new TLRPC.Message(id));callback.run(page,null);}
}''',
'HistorySessionCheck.java':r'''
import app.nebulagram.ui.*;import org.telegram.tgnet.*;import org.telegram.messenger.*;
class HistorySessionCheck{
 static void ok(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[]args){
  var net=ConnectionsManager.instance;int[] pages={0},done={0},errors={0};Runnable[] next={null};
  var session=new NebulaHistorySession(0,-12,17);
  session.start(3,(p,n)->{pages[0]+=p.size();next[0]=n;},()->done[0]++,e->errors[0]++);
  var q=(TLRPC.TL_messages_getReplies)net.last;ok(q.peer.dialog==-12&&q.msg_id==17&&q.limit==3);
  net.reply(50,50,49);ok(pages[0]==2&&net.calls==1);next[0].run();
  q=(TLRPC.TL_messages_getReplies)net.last;ok(q.offset_id==49&&q.limit==1);
  net.reply(49,48,47);ok(pages[0]==3);next[0].run();ok(done[0]==1&&errors[0]==0);
  session=new NebulaHistorySession(0,123,0);session.start(10,(p,n)->{next[0]=n;},()->done[0]++,e->errors[0]++);
  ok(net.last instanceof TLRPC.TL_messages_getHistory);net.reply(100);int calls=net.calls;
  UserConfig.owner=99;next[0].run();ok(net.calls==calls&&errors[0]==1);UserConfig.owner=42;
  session=new NebulaHistorySession(0,123,0);session.start(10,(p,n)->pages[0]++,()->done[0]++,e->errors[0]++);
  int before=pages[0];session.cancel();net.reply(10);ok(pages[0]==before&&net.cancels==1);
  session=new NebulaHistorySession(0,999,0);calls=net.calls;session.start(10,(p,n)->{},()->{},e->errors[0]++);ok(calls==net.calls&&errors[0]==2);
  session=new NebulaHistorySession(0,-12,-1);session.start(10,(p,n)->{},()->{},e->errors[0]++);ok(calls==net.calls&&errors[0]==3);
  session=new NebulaHistorySession(0,123,0);session.start(10,(p,n)->{},()->{},e->errors[0]++);net.callback.run(null,new TLRPC.TL_error());ok(errors[0]==4);
  System.out.println("History loader: topic isolation, duplicate pages, backpressure, count limit, cancellation, account changes and RPC failures passed");
 }
}
'''}
with tempfile.TemporaryDirectory(prefix='nebula-history-session-') as directory:
    work=Path(directory)
    for name,source in stubs.items():
        p=work/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(ui/'NebulaHistorySession.java'),str(ui/'NebulaHistoryPager.java'),*[str(p) for p in work.rglob('*.java')]],check=True)
    subprocess.run(['java','-cp',str(work),'HistorySessionCheck'],check=True)
