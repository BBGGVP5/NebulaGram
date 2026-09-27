"""Run the production retention loop with SQLite/message fixtures (no device needed)."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
archive = (UI / 'NebulaDeletedArchive.java').read_text(encoding='utf-8')

def block(signature):
    start = archive.index(signature)
    end = archive.index('{', start) + 1
    depth = 1
    while depth:
        depth += (archive[end] == '{') - (archive[end] == '}')
        end += 1
    return archive[start:end]

production = '\n'.join(block(s) for s in [
    'private static final class Snapshot', 'private static Snapshot snapshot(long owner)',
    'private static JSONArray copyEntries', 'public static synchronized ArrayList<Integer> retain',
    'private static void writeAsync(long owner, Snapshot snapshot)',
    'private static void writeAsync(long owner, Snapshot snapshot, Runnable done, Runnable error)'
])
for full, short in {
    'org.telegram.SQLite.SQLitePreparedStatement': 'Statement',
    'org.telegram.messenger.AndroidUtilities': 'AndroidUtilities',
    'org.telegram.messenger.MessageObject': 'MessageObject',
    'org.telegram.messenger.NotificationCenter': 'NotificationCenter',
    'NebulaDeletedArchive.class': 'ArchiveCheck.class',
}.items():
    production = production.replace(full, short)

harness = '''import java.util.*;
import app.nebulagram.ui.NebulaRetentionPolicy;
class ArchiveCheck {
 static long accountOwner=42,cachedOwner; static Snapshot cachedSnapshot;
 static boolean enabled=true; static int queries,writes,prepares,snapshots,failures,reads;
 static Map<Long,Snapshot> pendingWrites=new HashMap<>(),uncommittedSnapshots=new HashMap<>();
 static Set<Long> writingOwners=new HashSet<>();
 static Map<Long,ArrayList<Runnable>> writeDone=new HashMap<>(),writeError=new HashMap<>();
 static ArrayDeque<Runnable> worker=new ArrayDeque<>();
 static java.util.concurrent.Executor ARCHIVE_WRITER=worker::add;
 static JSONArray persisted;static Runnable duringWrite;static boolean failWrite;
 static class ApplicationLoader {
  static final ApplicationLoader applicationContext=new ApplicationLoader();
  ApplicationLoader getSharedPreferences(String n,int m){return this;}ApplicationLoader edit(){return this;}
  ApplicationLoader remove(String n){return this;}void apply(){}
 }
 static Map<Long,Set<String>> markers=new HashMap<>();
 static List<Row> rows=new ArrayList<>(); static Set<Long> disabledPeers=new HashSet<>();
 static class JSONObject extends HashMap<String,Object> {
  JSONObject put(String k,long v){super.put(k,v);return this;}
  JSONObject put(String k,int v){super.put(k,v);return this;}
  JSONObject put(String k,boolean v){super.put(k,v);return this;}
  JSONObject put(String k,String v){super.put(k,v);return this;}
  long optLong(String k){return ((Number)getOrDefault(k,0L)).longValue();}
  int optInt(String k){return (int)optLong(k);} boolean optBoolean(String k){return Boolean.TRUE.equals(get(k));}
 }
 static class JSONArray {
  List<JSONObject> values=new ArrayList<>(); int length(){return values.size();}
  JSONObject getJSONObject(int i){return values.get(i);} JSONArray put(JSONObject o){values.add(o);return this;}
 }
 static class DialogObject {static boolean isEncryptedDialog(long p){return p>1000000000L;}}
 static class TextUtils {static String join(String s,ArrayList<Integer> ids){return ids.toString();}}
 static class NativeByteBuffer {
  TLRPC.Message message;NativeByteBuffer(int size){} NativeByteBuffer(TLRPC.Message m){message=m;}
  int readInt32(boolean b){return 0;} void reuse(){}
 }
 static class TLRPC {
  static class Media {int ttl_seconds;}
  static class Message {
   int ttl,ttl_period,id,date;long dialog_id;boolean out,noforwards,unread,media_unread;String message="text";Media media;
   static Message TLdeserialize(NativeByteBuffer b,int c,boolean e){return b.message;}
   void readAttachPath(NativeByteBuffer b,long owner){} int getObjectSize(){return 100;}
   void serializeToStream(NativeByteBuffer b){}
  }
  static class TL_messageService extends Message {}
 }
 static class Row {long peer;int id,ttl;TLRPC.Message message;Row(long p,int i,boolean out){peer=p;id=i;message=new TLRPC.Message();message.out=out;}}
 static class SQLiteCursor {
  Iterator<Row> it=rows.iterator();Row row;
  boolean next(){if(!it.hasNext())return false;row=it.next();return true;}
  long longValue(int c){return row.peer;}
  int intValue(int c){return c==2?row.id:c==4?row.ttl:0;}
  NativeByteBuffer byteBufferValue(int c){return new NativeByteBuffer(row.message);} void dispose(){}
 }
 static class Statement {
  void bindByteBuffer(int i,NativeByteBuffer b){} void bindLong(int i,long p){} void bindInteger(int i,int id){}
  void step(){writes++;}void requery(){} void dispose(){}
 }
 static class Database {
  SQLiteCursor queryFinalized(String sql){queries++;return new SQLiteCursor();}
  Statement executeFast(String sql){prepares++;return new Statement();}
 }
 static class MessagesStorage {
  static MessagesStorage getInstance(int account){return new MessagesStorage();} Database getDatabase(){return new Database();}
 }
 static class NebulaDeletedStyle {static boolean retains(int a,long p){return !disabledPeers.contains(p);}}
 static class AndroidUtilities {static void runOnUIThread(Runnable r){r.run();}}
 static class MessageObject {MessageObject(int a,TLRPC.Message m,boolean x,boolean y){}}
 static class NotificationCenter {
  static int replaceMessagesObjects;static NotificationCenter getInstance(int a){return new NotificationCenter();}
  void postNotificationName(int event,long peer,ArrayList<MessageObject> objects){}
 }
 static long owner(int account){return accountOwner;}static boolean enabled(long o){return enabled;}
 static boolean present(long o){return cachedSnapshot!=null;}static boolean saveSecret(long o){return true;}
 static boolean saveExpiring(long o){return true;}static boolean protectedPeer(int a,long p){return false;}
 static void failed(long o){failures++;}static String marker(long p,int id){return p+":"+id;}
 static JSONArray read(long o){reads++;return new JSONArray();}
 static void publish(long o,Snapshot s){markers.put(o,s.keys);}
 static void writeFile(long o,JSONArray entries)throws Exception {
  snapshots++;if(duringWrite!=null){Runnable next=duringWrite;duringWrite=null;next.run();}
  if(failWrite)throw new Exception("Disk unavailable");persisted=entries;
 }
 PRODUCTION
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static ArrayList<Integer> ids(int... values){ArrayList<Integer> r=new ArrayList<>();for(int v:values)r.add(v);return r;}
 static void reset(){cachedSnapshot=null;cachedOwner=0;queries=writes=prepares=snapshots=failures=reads=0;rows.clear();markers.clear();disabledPeers.clear();enabled=true;uncommittedSnapshots.clear();pendingWrites.clear();writingOwners.clear();writeDone.clear();writeError.clear();worker.clear();duringWrite=null;failWrite=false;persisted=null;}
 public static void main(String[] args)throws Exception{
  reset();rows.add(new Row(42,10,true));rows.add(new Row(55,11,true));rows.add(new Row(55,12,false));
  check(retain(0,0,ids(10,11,12)).equals(ids(11)),"remote Saved Messages or ordinary outgoing policy failed");
  check(cachedSnapshot.entries.length()==2&&writes==0&&prepares==0,"ordinary cached messages rewritten");
  int count=queries;check(retain(0,0,ids(10,12)).isEmpty()&&queries==count,"duplicate remote event touches SQL");
  check(retain(0,42,ids(10)).isEmpty()&&queries==count,"explicit Saved Messages scope loses global entry");
  enabled=false;check(retain(0,0,ids(10,13)).equals(ids(13)),"disabled capture drops old copy");
  reset();disabledPeers.add(42L);rows.add(new Row(42,10,true));check(retain(0,0,ids(10)).equals(ids(10)),"Saved Messages opt-out ignored");
  reset();Row protectedRow=new Row(42,10,true);protectedRow.message.noforwards=true;rows.add(protectedRow);
  check(retain(0,0,ids(10)).equals(ids(10)),"copy protection bypassed");
  reset();Row timer=new Row(42,10,true);timer.ttl=5;rows.add(timer);Row timer2=new Row(42,11,true);timer2.message.media=new TLRPC.Media();timer2.message.media.ttl_seconds=7;rows.add(timer2);
  check(retain(0,0,ids(10,11)).isEmpty()&&writes==2&&prepares==1,"timers not cleared or statement not reused");
  check(timer.message.ttl==0&&timer2.message.media.ttl_seconds==0,"countdowns kept");
  reset();Snapshot newer=new Snapshot(new JSONArray().put(new JSONObject().put("peer",42L).put("id",20).put("scope",0L).put("inline",true)));
  uncommittedSnapshots.put(42L,newer);cachedOwner=99;cachedSnapshot=new Snapshot(new JSONArray());
  check(snapshot(42)==newer&&reads==0,"account switch reloaded stale on-disk snapshot");
  reset();Snapshot first=new Snapshot(new JSONArray());Snapshot latest=new Snapshot(new JSONArray());
  boolean[] completed={false};writeAsync(42,first,()->completed[0]=true,()->{throw new AssertionError("unexpected write failure");});
  duringWrite=()->{
   try{check(snapshot(42)==first,"in-flight snapshot unavailable");}catch(Exception e){throw new RuntimeException(e);}
   writeAsync(42,latest);
  };
  worker.remove().run();
  check(persisted==latest.entries&&snapshots==2&&completed[0],"newer write lost during commit");
  check(uncommittedSnapshots.isEmpty()&&writingOwners.isEmpty()&&worker.isEmpty(),"committed snapshot/worker leaked");
  reset();boolean[] errored={false};failWrite=true;writeAsync(42,latest,null,()->errored[0]=true);worker.remove().run();
  check(errored[0]&&snapshot(42)==latest,"failed write dropped the live archive");
  failWrite=false;writeAsync(42,latest);worker.remove().run();check(persisted==latest.entries&&uncommittedSnapshots.isEmpty(),"write retry lost entries");
  check(!NebulaRetentionPolicy.receivedOrSaved(false,0,0),"logged-out self match");
  check(failures==1,"write failure was not reported");
  System.out.println("Archive pipeline: remote Saved Messages, scope opt-out, duplicate/global/peer updates, timer writes and pending account switch passed");
 }
}'''.replace('PRODUCTION', production)
with tempfile.TemporaryDirectory(prefix='nebula-archive-pipeline-') as temp:
    p = Path(temp)
    (p/'ArchiveCheck.java').write_text(harness, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(p), str(UI/'NebulaRetentionPolicy.java'), str(p/'ArchiveCheck.java')], check=True)
    subprocess.run(['java', '-cp', str(p), 'ArchiveCheck'], check=True)
