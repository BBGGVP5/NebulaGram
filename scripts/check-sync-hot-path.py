"""Execute archive membership helpers and command scheduling, not a device latency claim."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parent.parent
ui = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
link = ui.parent / 'nebulalink'

def method(source, signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

archive = (ui / 'NebulaDeletedArchive.java').read_text(encoding='utf-8')
helpers = '\n'.join(method(archive, s) for s in [
    'private static String marker(', 'private static java.util.Set<String> entryKeys(',
    'private static java.util.Map<Long,ArrayList<Integer>> removedEntries(',
    'private static JSONArray copyEntries(', 'private static final class Snapshot', 'private static Snapshot snapshot('])
harness = r'''
import java.util.*;
class ArchiveIndexCheck {
 static class JSONObject {
  long peer,date,scope; int id; boolean inline;
  JSONObject(long p,int i,boolean in){peer=p;id=i;inline=in;date=Long.MAX_VALUE;scope=p;}
  long getLong(String key){return optLong(key);} int getInt(String key){return id;}
  long optLong(String key){return key.equals("peer")?peer:key.equals("scope")?scope:date;}
  int optInt(String key){return id;} boolean optBoolean(String key){return inline;}
 }
 static class JSONArray {
  static long reads; ArrayList<JSONObject> rows=new ArrayList<>();
  int length(){return rows.size();} JSONObject getJSONObject(int i){reads++;return rows.get(i);}
  JSONObject optJSONObject(int i){return getJSONObject(i);}
  JSONArray put(JSONObject e){rows.add(e);return this;}
 }
 static long cachedOwner; static Snapshot cachedSnapshot;
 static int diskReads; static boolean readFails; static JSONArray disk;
 static JSONArray read(long owner)throws Exception{diskReads++;if(readFails)throw new Exception("Unreadable");return disk;}
 HELPERS
 public static void main(String[] args)throws Exception {
  JSONArray all=new JSONArray(),kept=new JSONArray();
  for(int i=0;i<50000;i++){JSONObject e=new JSONObject(-100-i%2,i,true);all.put(e);if(i%10!=0)kept.put(e);}
  // Same message ID in distinct peers must never collide; metadata-only rows aren't purged.
  JSONObject other=new JSONObject(999,0,true);all.put(other);kept.put(other);
  all.put(new JSONObject(888,1,false));
  JSONArray.reads=0;
  Map<Long,ArrayList<Integer>> removed=removedEntries(all,kept);
  if(removed.values().stream().mapToInt(List::size).sum()!=5000||removed.containsKey(999L)||removed.containsKey(888L))throw new AssertionError();
  if(JSONArray.reads>all.length()+kept.length())throw new AssertionError("Quadratic scan: "+JSONArray.reads);
  JSONArray copy=copyEntries(all);if(copy.length()!=all.length())throw new AssertionError("Clock change deleted history");
  copy.put(other);if(copy.length()!=all.length()+1)throw new AssertionError("Mutated original snapshot");
  if(!removedEntries(all,all).isEmpty())throw new AssertionError();
  if(removedEntries(new JSONArray(),new JSONArray()).size()!=0)throw new AssertionError();
  disk=all;Snapshot first=snapshot(1);long scans=JSONArray.reads;
  for(int i=0;i<10000;i++){
   Snapshot current=snapshot(1);
   ArrayList<Integer> ids=new ArrayList<>(Arrays.asList(2,3,100000));
   current.filter(-100,ids);
   if(!ids.equals(Arrays.asList(3,100000))||current!=first)throw new AssertionError("Scope mismatch");
  }
  if(diskReads!=1||JSONArray.reads!=scans)throw new AssertionError("Repeated archive scan/decryption");
  readFails=true;
  try{snapshot(2);throw new AssertionError("Read failure ignored");}catch(Exception expected){}
  if(snapshot(1)!=first||cachedOwner!=1)throw new AssertionError("Failed load poisoned cache");
  readFails=false;disk=new JSONArray();Snapshot second=snapshot(2);
  if(!second.keys.isEmpty()||cachedOwner!=2)throw new AssertionError("Cross-account data leak");
  Snapshot cleared=new Snapshot(new JSONArray());
  ArrayList<Integer> ids=new ArrayList<>(Arrays.asList(2));cleared.filter(-100,ids);
  if(ids.size()!=1)throw new AssertionError("Clear kept stale markers");
  System.out.println("PASS: 50,000-entry linear diff; 10,000 updates without rescanning/decrypting; account/scope isolation, failed loads, clear and snapshots");
 }
}'''.replace('HELPERS', helpers)
queue_test = r'''
package app.nebulagram.nebulalink;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class CommandQueueCheck {
 static void waitFor(CountDownLatch latch)throws Exception{if(!latch.await(5,TimeUnit.SECONDS))throw new AssertionError("Command blocked");}
 public static void main(String[] args)throws Exception{
  NebulaCommandQueue queue=new NebulaCommandQueue();
  CountDownLatch probeStarted=new CountDownLatch(1),releaseProbe=new CountDownLatch(1),done=new CountDownLatch(1),nextProbe=new CountDownLatch(1);
  AtomicBoolean ready=new AtomicBoolean(),invalid=new AtomicBoolean();
  List<String> order=Collections.synchronizedList(new ArrayList<>());
  try {
   queue.execute(()->ready.set(true));
   queue.execute("probe.servers",()->{if(!ready.get())invalid.set(true);probeStarted.countDown();try{releaseProbe.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
   waitFor(probeStarted);
   queue.execute("probe.url",nextProbe::countDown);
   queue.execute("tunnel.stop",()->order.add("stop"));
   queue.execute("settings.set",()->order.add("settings"));
   queue.execute("subscription.refresh",()->order.add("subscription"));
   queue.execute("settings.get",()->{order.add("read");done.countDown();});
   waitFor(done);
   if(!order.equals(Arrays.asList("stop","settings","subscription","read"))||invalid.get()||nextProbe.getCount()!=1)throw new AssertionError(order.toString());
   releaseProbe.countDown();waitFor(nextProbe);
   System.out.println("PASS: blocked probe cannot delay stop/settings, mutations FIFO, probes serialized, init first");
  }finally{releaseProbe.countDown();queue.shutdownForTests();}
 }
}'''

bridge = (link / 'NebulaLink.java').read_text(encoding='utf-8')
proxy_helpers = '\n'.join(method(bridge, s) for s in [
    'private static boolean matchesTunnelEndpoint(', 'private static boolean isRecordedTunnelEndpoint(String',
    'private static boolean isRecordedTunnelEndpoint(ProxySettings', 'public static boolean isTunnelProxy(',
    'private static void disableStaleProxy(',
    'private static void clearPreviousProxy(', 'public static void setCallsThroughTunnel(',
    'public static boolean callsThroughTunnel(', 'public static boolean isRoutingThroughTunnel('])
proxy_test = r'''
import java.util.*;
class ProxyCleanupCheck {
 static final String PROXY_ADDRESS="127.0.0.1",PREFS="nebulagram",KEY_PROXY_PORT="tunnel_proxy_port",KEY_ROUTE_CALLS="tunnel_route_calls";
 static class SharedPreferences {
  Map<String,Object> data=new HashMap<>();
  boolean getBoolean(String k,boolean d){return (boolean)data.getOrDefault(k,d);}
  int getInt(String k,int d){return (int)data.getOrDefault(k,d);}
  String getString(String k,String d){return (String)data.getOrDefault(k,d);}
  SharedPreferences edit(){return this;}
  SharedPreferences putBoolean(String k,boolean v){data.put(k,v);return this;}
  SharedPreferences putInt(String k,int v){data.put(k,v);return this;}
  SharedPreferences putString(String k,String v){data.put(k,v);return this;}
  SharedPreferences remove(String k){data.remove(k);return this;}
  void apply(){} boolean commit(){return true;}
 }
 static class ApplicationLoader {
  static ApplicationLoader applicationContext=new ApplicationLoader();
  SharedPreferences getSharedPreferences(String p,int m){return saved;}
 }
 static SharedPreferences settings=new SharedPreferences(),saved=new SharedPreferences();
 static class MessagesController {static SharedPreferences getGlobalMainSettings(){return settings;}}
 static class ProxySettings {
  static final ProxySettings EMPTY=new ProxySettings("",0);
  private final String address;private final int port;
  ProxySettings(String a,int p){address=a;port=p;}
  String getAddress(){return address;} int getPort(){return port;}
  String getUser(){return "";} String getPassword(){return "";} String getSecret(){return "";}
 }
 static class ConnectionsManager {
  static int disconnects;
  static void setProxySettings(boolean enabled,ProxySettings settings){if(enabled)throw new AssertionError();disconnects++;}
 }
 static class SharedConfig {
  static class ProxyInfo {
   final ProxySettings settings;
   ProxyInfo(String a,int p){settings=new ProxySettings(a,p);}
  }
  static ArrayList<ProxyInfo> proxyList=new ArrayList<>();
  static ProxyInfo currentProxy;
  static void loadProxyList(){}
  static void deleteProxy(ProxyInfo p){
   proxyList.remove(p);
   if(p==currentProxy){currentProxy=null;settings.putBoolean("proxy_enabled",false).putBoolean("proxy_enabled_calls",false);}
  }
 }
 static SharedConfig.ProxyInfo installedProxy;
 HELPERS
 static void reset(int port){
  settings=new SharedPreferences();saved=new SharedPreferences();
  settings.putBoolean("proxy_enabled",true).putBoolean("proxy_enabled_calls",true).putString("proxy_ip","127.0.0.1").putInt("proxy_port",port);
  saved.putInt(KEY_PROXY_PORT,19080);SharedConfig.proxyList.clear();SharedConfig.currentProxy=null;
  installedProxy=null;ConnectionsManager.disconnects=0;
 }
 public static void main(String[] args){
  if(!matchesTunnelEndpoint(19080,"127.0.0.1",19080,"","",""))throw new AssertionError();
  for(int known:new int[]{0,19081})if(matchesTunnelEndpoint(known,"127.0.0.1",19080,"","",""))throw new AssertionError();
  if(matchesTunnelEndpoint(19080,"localhost",19080,"","",""))throw new AssertionError();
  for(int field=0;field<3;field++){
   String[] auth={"","",""};auth[field]="secret";
   if(matchesTunnelEndpoint(19080,"127.0.0.1",19080,auth[0],auth[1],auth[2]))throw new AssertionError();
  }
  // Native prefs can survive while the proxy-list object has gone missing.
  reset(19080);setCallsThroughTunnel(true);clearPreviousProxy(19080);
  if(settings.getBoolean("proxy_enabled",true)||ConnectionsManager.disconnects!=1||saved.getInt(KEY_PROXY_PORT,0)!=0||!callsThroughTunnel())throw new AssertionError("Stale endpoint survived");
  // Выбор сохранён у нас, но Telegram больше не считает, что звонки идут через прокси.
  if(settings.getBoolean("proxy_enabled_calls",false))throw new AssertionError("Calls still routed through a proxy that is gone");
  // A different local app's proxy must stay active and in the user's proxy list.
  reset(19081);
  SharedConfig.ProxyInfo foreign=new SharedConfig.ProxyInfo("127.0.0.1",19081);
  SharedConfig.proxyList.add(foreign);SharedConfig.currentProxy=foreign;
  clearPreviousProxy(19080);
  if(!settings.getBoolean("proxy_enabled",false)||ConnectionsManager.disconnects!=0||SharedConfig.currentProxy!=foreign||!SharedConfig.proxyList.contains(foreign))throw new AssertionError("Foreign proxy removed");
  reset(19080);settings.putString("proxy_user","user");clearPreviousProxy(19080);
  if(!settings.getBoolean("proxy_enabled",false))throw new AssertionError("Authenticated user proxy removed");
  reset(19080);setCallsThroughTunnel(true);SharedConfig.ProxyInfo ours=new SharedConfig.ProxyInfo("127.0.0.1",19080);
  SharedConfig.proxyList.add(ours);SharedConfig.currentProxy=ours;clearPreviousProxy(19080);
  if(SharedConfig.currentProxy!=null||SharedConfig.proxyList.contains(ours)||!callsThroughTunnel())throw new AssertionError("Delete/call preference mismatch");
  // Наполовину снятая запись: адрес наш, флаг уже выключен. Ядру всё равно
  // надо сказать, что прокси больше нет, иначе клиент стучится в мёртвый порт.
  reset(19080);settings.putBoolean("proxy_enabled",false);clearPreviousProxy(19080);
  if(ConnectionsManager.disconnects!=1||!settings.getString("proxy_ip","x").isEmpty())throw new AssertionError("Half-removed endpoint left in place");
  System.out.println("PASS: recorded proxy cleanup, missing list entry, foreign proxy/credentials preserved, call preference kept");
 }
}'''.replace('HELPERS', proxy_helpers)
with tempfile.TemporaryDirectory(prefix='nebula-sync-') as tmp:
    work = Path(tmp)
    a, q, proxy = work / 'ArchiveIndexCheck.java', work / 'CommandQueueCheck.java', work / 'ProxyCleanupCheck.java'
    a.write_text(harness, encoding='utf-8')
    q.write_text(queue_test, encoding='utf-8')
    proxy.write_text(proxy_test, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(a), str(q), str(proxy), str(link / 'NebulaCommandQueue.java')], check=True)
    for name in ['ArchiveIndexCheck', 'app.nebulagram.nebulalink.CommandQueueCheck', 'ProxyCleanupCheck']:
        subprocess.run(['java', '-cp', str(work), name], check=True)

retain = method(archive, 'public static synchronized ArrayList<Integer> retain(')
assert 'purgeDifference(' not in retain
assert 'before.keys.contains(marker(peer,id))' in retain
assert 'if(entries==null)entries=copyEntries(before.entries)' in retain
assert retain.index('cachedSnapshot = next') < retain.index('writeAsync(owner, next)') < retain.index('committed.filter(')
assert 'private static void writeAsync(long owner, Snapshot snapshot)' in archive
assert 'writeFile(owner, next.entries)' in archive and 'ARCHIVE_WRITER.execute' in archive
write_file = method(archive, 'private static void writeFile(')
assert write_file.index('target.finishWrite(output)') > write_file.index('output.write(bytes)')
assert '.sort(' not in retain
bridge = (link / 'NebulaLink.java').read_text(encoding='utf-8')
init = method(bridge, 'public static void init(')
assert init.index('clearPreviousProxy(saved.getInt') < init.index('callBlocking("core.init"')
assert 'EXECUTOR.execute(method,' in bridge
assert 'disableStaleProxy(port);' in method(bridge, 'private static void clearPreviousProxy(')
# Снятие туннеля проходит по списку всегда, а не только когда ссылки нет:
# Telegram мог пересобрать список, и тогда наш объект в нём уже не тот.
stop = method(bridge, 'public static void stopUsingTunnel(')
assert stop.count('isTunnelProxy(proxy)') == 1 and 'else {' not in stop
search = (ui / 'NebulaSettingsSearch.java').read_text(encoding='utf-8')
assert search.index('R.string.NebulaHideHomeCamera') > search.index('Истории в списке чатов')
assert 'new Entry(2, R.string.NebulaHideHomeCompose' in search
section = (ui / 'NebulaSectionFragment.java').read_text(encoding='utf-8')
assert 'Кнопки на экране чатов' in section
print('PASS: persistence ordering, early stale-proxy cleanup, home settings discovery')
