"""Run the actual native acknowledgement PTS/SEQ handler, without network or sessions."""
from pathlib import Path
import os, shutil, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'vendor/telegram-android'
native = (tree / 'TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java').read_text(encoding='utf-8')
start = native.index('    public void processNewDifferenceParams(')
end = native.index('    public void didAddedNewTask(', start)
method = native[start:end].replace('System.currentTimeMillis()', 'Clock.now')
source = r'''
import java.util.*;
public class ReadReceiptOrderCheck {
 static class Clock {static long now=5000;}
 static class BuildVars {static final boolean LOGS_ENABLED=false;}
 static class FileLog {static void d(String s){}}
 static class State {
  int pts=100,seq=10,date=77,saves;
  int getLastPtsValue(){return pts;}int getLastSeqValue(){return seq;}int getLastDateValue(){return date;}int getLastQtsValue(){return 0;}
  void setLastPtsValue(int p){pts=p;}void setLastSeqValue(int s){seq=s;}void setLastDateValue(int d){date=d;}
  void saveDiffParams(int s,int p,int d,int q){saves++;}
 }
 static class UserActionUpdatesPts {int pts,pts_count;}
 static class UserActionUpdatesSeq {int seq;}
 final State storage=new State();State getMessagesStorage(){return storage;}
 long updatesStartWaitTimePts=1,updatesStartWaitTimeSeq=1;boolean gettingDifference;int differences;
 final List<UserActionUpdatesPts> updatesQueuePts=new ArrayList<>();final List<UserActionUpdatesSeq> updatesQueueSeq=new ArrayList<>();
 void getDifference(){differences++;gettingDifference=true;}
 METHOD
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 public static void main(String[] args){
  ReadReceiptOrderCheck c=new ReadReceiptOrderCheck();
  // A burst of delayed read acknowledgements must not request getDifference.
  for(int i=0;i<1000;i++)c.processNewDifferenceParams(-1,99-i%50,-1,1);
  check(c.differences==0&&c.updatesQueuePts.isEmpty(),"stale read acknowledgements trigger sync");
  c.processNewDifferenceParams(-1,100,-1,1);c.processNewDifferenceParams(-1,102,-1,3);
  check(c.storage.pts==100&&c.differences==0&&c.updatesQueuePts.isEmpty(),"overlapping/already applied PTS must be ignored");
  c.processNewDifferenceParams(-1,103,-1,3);check(c.storage.pts==103&&c.storage.saves==1,"contiguous PTS not persisted");
  c.processNewDifferenceParams(-1,103,-1,0);check(c.differences==0,"duplicate zero-count PTS");
  c.processNewDifferenceParams(9,-1,999,0);c.processNewDifferenceParams(10,-1,999,0);
  check(c.storage.seq==10&&c.storage.date==77&&c.updatesQueueSeq.isEmpty()&&c.differences==0,"old SEQ causes sync/date rollback");
  c.processNewDifferenceParams(11,-1,88,0);check(c.storage.seq==11&&c.storage.date==88,"next SEQ not applied");
  ReadReceiptOrderCheck gap=new ReadReceiptOrderCheck();gap.updatesStartWaitTimePts=Clock.now;
  gap.processNewDifferenceParams(-1,102,-1,1);check(gap.updatesQueuePts.size()==1&&gap.differences==0,"real PTS gap must wait for reorder");
  Clock.now+=1600;gap.processNewDifferenceParams(-1,103,-1,1);check(gap.differences==1&&gap.gettingDifference,"real PTS gap must recover");
  ReadReceiptOrderCheck seqGap=new ReadReceiptOrderCheck();seqGap.processNewDifferenceParams(12,-1,99,0);check(seqGap.differences==1,"real SEQ gap must recover");
  ReadReceiptOrderCheck other=new ReadReceiptOrderCheck();check(other.storage.pts==100&&!other.gettingDifference,"account state leaked");
  ReadReceiptOrderCheck edge=new ReadReceiptOrderCheck();edge.storage.pts=Integer.MAX_VALUE-1;
  edge.processNewDifferenceParams(-1,Integer.MAX_VALUE,-1,2);check(edge.differences==0&&edge.updatesQueuePts.isEmpty(),"PTS addition overflow");
  System.out.println("Native receipt order: 1000 late callbacks, overlap/duplicate PTS, contiguous updates, SEQ/date isolation and real-gap recovery passed");
 }
}
'''.replace('METHOD', method)
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
dialogs = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java').read_text(encoding='utf-8')
start = dialogs.index('    private void markDialogsAsRead(')
bulk = dialogs[start:dialogs.index('    private void performDeleteOrClearDialogAction(',start)]
bulk_source = r'''
import java.util.*;
public class BulkReadScopeCheck {
 static class TLRPC {static class Dialog {long id;int unread_count,unread_mentions_count,top_message,last_message_date;boolean unread_mark;}}
 static class DialogObject {static boolean isFolderDialogId(long id){return id==Long.MIN_VALUE;}}
 static class Controller {
  Set<Long> read=new HashSet<>(),mentions=new HashSet<>(),topics=new HashSet<>();
  boolean isForum(long id){return id>0&&id%100==0;}boolean isMonoForumWithManageRights(long id){return id>0&&id%150==0;}
  void markAllTopicsAsRead(long id){topics.add(id);}void markMentionsAsRead(long id,int topic){mentions.add(id);}
  void markDialogAsRead(long id,int p,int n,int date,boolean pop,int t,int c,boolean now,int scheduled){read.add(id);}
 }
 static class Animator {void prepareForRemove(){}}static class Page {Animator dialogsItemAnimator=new Animator();void updateList(boolean b){}}
 int debugLastUpdateAction;Controller controller=new Controller();Controller getMessagesController(){return controller;}
 ArrayList<Object> frozenDialogsList=new ArrayList<>();Page[] viewPages={new Page()};boolean frozen,thawScheduled;
 void setDialogsListFrozen(boolean b){frozen=b;}void checkAnimationFinished(){thawScheduled=true;}
 BULK
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static TLRPC.Dialog item(long id,int unread,int mentions,boolean mark){TLRPC.Dialog d=new TLRPC.Dialog();d.id=id;d.unread_count=unread;d.unread_mentions_count=mentions;d.unread_mark=mark;d.top_message=77;return d;}
 public static void main(String[] args){
  ArrayList<TLRPC.Dialog> list=new ArrayList<>();for(int i=1;i<=1000;i++)list.add(item(i,i%20==0?1:0,i%50==0?1:0,false));
  list.add(item(1001,0,0,true));list.add(item(-5,1,0,false));list.add(item(Long.MIN_VALUE,99,99,true));list.add(null);
  BulkReadScopeCheck c=new BulkReadScopeCheck();c.markDialogsAsRead(list);
  check(c.controller.read.size()==52,"read RPCs include already-read/folder dialogs");
  check(c.controller.mentions.size()==20,"mention RPCs include dialogs without mentions");
  check(c.controller.topics.size()==10,"forum receipt semantics changed");
  check(c.controller.read.contains(-5L)&&c.controller.read.contains(1001L),"secret/manual-unread omitted");
  check(c.thawScheduled&&list.size()==1004,"list lifetime/source mutated");
  System.out.println("Native bulk read: 1000 mixed dialogs, read/mention filtering, forum/secret/manual marks, folder exclusion and thaw scheduling passed");
 }
}
'''.replace('BULK',bulk)
with tempfile.TemporaryDirectory(prefix='nebula-read-order-') as directory:
    work=Path(directory); harness=work/'ReadReceiptOrderCheck.java';harness.write_text(source,encoding='utf-8')
    bulk_harness=work/'BulkReadScopeCheck.java';bulk_harness.write_text(bulk_source,encoding='utf-8')
    subprocess.run([shutil.which('javac') or str(java_home/'bin/javac.exe'),'-encoding','UTF-8','-d',str(work),str(harness),str(bulk_harness)],check=True)
    subprocess.run([shutil.which('java') or str(java_home/'bin/java.exe'),'-cp',str(work),'ReadReceiptOrderCheck'],check=True)
    subprocess.run([shutil.which('java') or str(java_home/'bin/java.exe'),'-cp',str(work),'BulkReadScopeCheck'],check=True)
