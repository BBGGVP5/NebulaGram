"""Execute production edit capture, bounds, storage ownership and clearing adapters."""
from pathlib import Path
import os, re, shutil, sqlite3, subprocess, sys, tempfile

root = Path(__file__).resolve().parents[1]
java_source = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaEditHistory.java'
swift_source = root / 'platform/ios/overlay/submodules/TelegramCore/Sources/State/NebulaEditHistory.swift'

# Validate the actual SQL statements with SQLite, including Android-6-compatible syntax.
source = java_source.read_text(encoding='utf-8')
# A reserved cache collection must never collide with the pinned Telegram namespace.
if '--swift' in sys.argv:
    upstream = root / 'build/ios-13-upstream-20261013'
    if not upstream.exists(): upstream = root / 'vendor/telegram-ios'
    namespace = subprocess.check_output(['git', '-C', str(upstream), 'show', 'HEAD:submodules/TelegramCore/Sources/SyncCore/SyncCore_Namespaces.swift'], text=True, encoding='utf-8')
    assert not re.search(r'public static let \w+: Int8 = 126\b', namespace), 'Reserved Nebula collection collides with upstream'
db = sqlite3.connect(':memory:')
for sql in re.findall(r'db.executeFast\("(CREATE [^"]+)"\)', source): db.execute(sql)
insert = re.search(r'db.executeFast\("(INSERT [^"]+)"\)', source)[1]
for i in range(40): db.execute(insert, (9223372036854775806, 1, bytes([i])))
prune = re.search(r'String.format\(Locale.US, "(DELETE [^"]+)"', source)[1]
db.execute(prune % (9223372036854775806, 1, 9223372036854775806, 1, 32))
assert db.execute('SELECT COUNT(*),MIN(seq),MAX(seq) FROM nebula_edit_history').fetchone() == (32, 9, 40)
db.execute(insert, (-9223372036854775806, 1, b'other'))
assert db.execute('SELECT COUNT(DISTINCT uid) FROM nebula_edit_history').fetchone()[0] == 2
global_prune = re.search(r'db.executeFast\("(DELETE [^"]+)" \+ MAX_MESSAGES', source)[1] + '1000)'
for i in range(1002): db.execute(insert, (i + 1, 7, b'version'))
db.execute(global_prune)
assert db.execute('SELECT COUNT(DISTINCT uid || ":" || mid) FROM nebula_edit_history').fetchone()[0] == 1000
assert db.execute('SELECT COUNT(*) FROM nebula_edit_history WHERE uid = 1 AND mid = 7').fetchone()[0] == 0

with tempfile.TemporaryDirectory(prefix='nebula-edit-history-') as temporary:
    work = Path(temporary)
    if '--swift' in sys.argv:
        production = '\n'.join(line for line in swift_source.read_text(encoding='utf-8').splitlines() if line not in ['import Postbox', 'import SwiftSignalKit']).replace('public ', '')
        stubs = r'''
import Foundation
typealias ItemCacheCollectionId = Int8
final class ValueBoxKey: Hashable {static func ==(a:ValueBoxKey,b:ValueBoxKey)->Bool{a.data==b.data};func hash(into h:inout Hasher){h.combine(data)};var data: Data;init(length: Int){data=Data(repeating:0,count:length)};init(_ value:String){data=Data(value.utf8)};func setInt64(_ offset:Int,value:Int64){var n=value;withUnsafeBytes(of:&n){data.replaceSubrange(offset..<offset+8,with:$0)}};func setInt32(_ offset:Int,value:Int32){var n=value;withUnsafeBytes(of:&n){data.replaceSubrange(offset..<offset+4,with:$0)}}}
struct ItemCacheEntryId: Hashable {let collectionId:Int8;let key:ValueBoxKey}
struct CodableEntry {let data:Data;init?<T:Encodable>(_ value:T){guard let data=try?JSONEncoder().encode(value)else{return nil};self.data=data};func get<T:Decodable>(_ type:T.Type)->T?{try?JSONDecoder().decode(type,from:data)}}
struct PeerId: Equatable {struct Id {let value:Int64;static func _internalFromInt64Value(_ n:Int64)->Id{Id(value:n)}};let namespace:Int32;let value:Int64;init(namespace:Int32,id:Id){self.namespace=namespace;value=id.value};func toInt64()->Int64{value}}
struct MessageId {let peerId:PeerId;let namespace:Int32;let id:Int32}
enum Namespaces {enum Message{static let Cloud:Int32=0};enum Peer{static let CloudUser:Int32=0;static let SecretChat:Int32=3}}
protocol MessageAttribute {}
struct MessageTextEntity:Codable,Equatable{let start:Int;let end:Int;let type:String}
class TextEntitiesMessageAttribute:MessageAttribute {let entities:[MessageTextEntity];init(_ e:[MessageTextEntity]){entities=e}}
class EditedMessageAttribute:MessageAttribute {let date:Int32;init(_ d:Int32){date=d}}
class AutoremoveTimeoutMessageAttribute:MessageAttribute{}
class AutoclearTimeoutMessageAttribute:MessageAttribute{}
class TelegramMediaAction{}
struct Flags:OptionSet{let rawValue:Int;static let CopyProtected=Flags(rawValue:1);static let copyProtectionEnabled=Flags(rawValue:1)}
struct Message {let id:MessageId;let text:String;let timestamp:Int32;let attributes:[MessageAttribute];var flags:Flags=[];var media:[Any]=[]}
struct StoreMessage {let text:String;let attributes:[MessageAttribute];var flags:Flags=[];var media:[Any]=[]}
struct Peer {let isCopyProtectionEnabled:Bool}
class CachedUserData {let flags:Flags;init(_ f:Flags){flags=f}}
enum NoError:Error{}
struct Signal<T,E:Error>{let value:T}
final class Transaction {
 var cache:[ItemCacheEntryId:CodableEntry]=[:];var previous:Message?;var protectedPeer=false;var protectedData=false
 func getMessage(_ id:MessageId)->Message?{previous}
 func getPeer(_ id:PeerId)->Peer?{Peer(isCopyProtectionEnabled:protectedPeer)}
 func getPeerCachedData(peerId:PeerId)->Any?{CachedUserData(protectedData ? .copyProtectionEnabled:[])}
 func retrieveItemCacheEntry(id:ItemCacheEntryId)->CodableEntry?{cache[id]}
 func putItemCacheEntry(id:ItemCacheEntryId,entry:CodableEntry){cache[id]=entry}
 func removeItemCacheEntry(id:ItemCacheEntryId){cache.removeValue(forKey:id)}
 func clearItemCacheCollection(collectionId:Int8){cache=cache.filter{$0.key.collectionId != collectionId}}
}
final class Postbox {let state=Transaction();func transaction<T>(_ body:(Transaction)->T)->Signal<T,NoError>{Signal(value:body(state))}}
final class Account {let postbox=Postbox()}
'''
        test = r'''
@main struct Check {static func main() throws {
 let account=Account(),other=Account();let owner=PeerId(namespace:0,id:.init(value:990001));defer{NebulaEditHistory.setEnabled(account:owner.toInt64(),value:false)}
 let id=MessageId(peerId:PeerId(namespace:0,id:.init(value:9223372036854775806)),namespace:0,id:1)
 let entities=[MessageTextEntity(start:0,end:2,type:"spoiler"),MessageTextEntity(start:3,end:5,type:"bold")]
 func previous(_ text:String,_ attrs:[MessageAttribute]=[])->Message{Message(id:id,text:text,timestamp:100,attributes:attrs)}
 func capture(_ old:Message,_ new:StoreMessage,_ dest:Account=account){dest.postbox.state.previous=old;NebulaEditHistory.capture(transaction:dest.postbox.state,accountPeerId:owner,id:old.id,incoming:new)}
 let incoming=StoreMessage(text:"new",attributes:[EditedMessageAttribute(101)])
 capture(previous("old"),incoming);precondition(NebulaEditHistory.read(account:account,id:id).value.isEmpty)
 NebulaEditHistory.setEnabled(account:owner.toInt64(),value:true)
 capture(previous("old"),incoming);precondition(NebulaEditHistory.read(account:account,id:id).value.first?.text == "old")
 capture(previous("new"),incoming);precondition(NebulaEditHistory.read(account:account,id:id).value.count==1)
 capture(previous("new",[EditedMessageAttribute(102)]),incoming);precondition(NebulaEditHistory.read(account:account,id:id).value.count==1)
 capture(previous("😀 hi",[TextEntitiesMessageAttribute(entities)]),incoming)
 precondition(NebulaEditHistory.read(account:account,id:id).value.first?.entities==entities)
 let same=StoreMessage(text:"😀 hi",attributes:[EditedMessageAttribute(102),TextEntitiesMessageAttribute(entities)])
 precondition(!NebulaEditHistory.changed(previous:previous("😀 hi",[TextEntitiesMessageAttribute(entities)]),incoming:same))
 precondition(NebulaEditHistory.changed(previous:previous("😀 hi"),incoming:same))
 for n in 0..<50 {capture(previous("v\(n)"),incoming)}
 let versions=NebulaEditHistory.read(account:account,id:id).value;precondition(versions.count==32 && versions.first?.text=="v49" && versions.last?.text=="v18")
 precondition(NebulaEditHistory.read(account:other,id:id).value.isEmpty)
 let restarted=Account();restarted.postbox.state.cache=try Dictionary(uniqueKeysWithValues:account.postbox.state.cache.map { key,value in (key,CodableEntry(try JSONDecoder().decode(Data.self,from:JSONEncoder().encode(value.data)))!) })
 // The actual Codable entry survives independent database/decoder access.
 for (key,value) in account.postbox.state.cache { restarted.postbox.state.cache[key]=value }
 precondition(NebulaEditHistory.read(account:restarted,id:id).value.count==32)
 for attr in [AutoremoveTimeoutMessageAttribute() as MessageAttribute, AutoclearTimeoutMessageAttribute()] {capture(previous("excluded",[attr]),incoming)}
 var protectedMessage=previous("excluded");protectedMessage.flags = .CopyProtected;capture(protectedMessage,incoming)
 protectedMessage=previous("excluded");protectedMessage.media=[TelegramMediaAction()];capture(protectedMessage,incoming)
 account.postbox.state.protectedPeer=true;capture(previous("excluded"),incoming);account.postbox.state.protectedPeer=false
 account.postbox.state.protectedData=true;capture(previous("excluded"),incoming);account.postbox.state.protectedData=false
 precondition(NebulaEditHistory.read(account:account,id:id).value.first?.text=="v49")
 for n in 2...1002 {let mid=MessageId(peerId:id.peerId,namespace:0,id:Int32(n));capture(Message(id:mid,text:"old",timestamp:100,attributes:[]),incoming)}
 precondition(NebulaEditHistory.read(account:account,id:id).value.isEmpty && account.postbox.state.cache.count==1001)
 let last=MessageId(peerId:id.peerId,namespace:0,id:1002);precondition(NebulaEditHistory.clear(account:account,id:last).value);precondition(NebulaEditHistory.read(account:account,id:last).value.isEmpty)
 precondition(NebulaEditHistory.clear(account:account).value && account.postbox.state.cache.isEmpty)
 print("Swift production edit history: opt-in, text/entities, stale and metadata-only updates, 32/1000 bounds, account isolation, Codable restart, exclusions and local clearing passed")
}}
'''
        # Remove a redundant wrapped-Data decoder experiment; cache entries already own encoded bytes.
        test = re.sub(r'let restarted=Account\(\);restarted.postbox.state.cache=.*?\n', 'let restarted=Account()\n', test)
        path = work / 'Check.swift'; path.write_text(stubs + '\n' + production + '\n' + test, encoding='utf-8')
        binary = work / 'check'; subprocess.run(['swiftc', '-parse-as-library', str(path), '-o', str(binary)], check=True)
        subprocess.run([str(binary)], check=True, timeout=60)
    else:
        files = {
            'android/content/SharedPreferences.java': '''package android.content;import java.util.*;public class SharedPreferences {Map<String,Boolean> values=new HashMap<>();public boolean getBoolean(String k,boolean d){return values.getOrDefault(k,d);}public Editor edit(){return new Editor();}public class Editor{public Editor putBoolean(String k,boolean v){values.put(k,v);return this;}public void apply(){}}}''',
            'org/telegram/messenger/ApplicationLoader.java': '''package org.telegram.messenger;public class ApplicationLoader {public static Context applicationContext=new Context();public static class Context{android.content.SharedPreferences p=new android.content.SharedPreferences();public android.content.SharedPreferences getSharedPreferences(String n,int m){return p;}}}''',
            'org/telegram/messenger/UserConfig.java': '''package org.telegram.messenger;public class UserConfig{int account;UserConfig(int a){account=a;}public static UserConfig getInstance(int a){return new UserConfig(a);}public long getClientUserId(){return account+1;}}''',
            'org/telegram/messenger/AndroidUtilities.java': '''package org.telegram.messenger;public class AndroidUtilities{public static void runOnUIThread(Runnable r){r.run();}}''',
            'org/telegram/messenger/DispatchQueue.java': '''package org.telegram.messenger;public class DispatchQueue{public void postRunnable(Runnable r){r.run();}}''',
            'org/telegram/messenger/FileLog.java': '''package org.telegram.messenger;public class FileLog{public static void e(Exception e){throw new AssertionError(e);}}''',
            'org/telegram/messenger/DialogObject.java': '''package org.telegram.messenger;public class DialogObject{public static boolean isEncryptedDialog(long p){return p==99;}}''',
            'org/telegram/messenger/MessageObject.java': '''package org.telegram.messenger;import org.telegram.tgnet.TLRPC;public class MessageObject{public static long getDialogId(TLRPC.Message m){return m.dialog_id;}}''',
            'org/telegram/messenger/MessagesController.java': '''package org.telegram.messenger;import org.telegram.tgnet.TLRPC;public class MessagesController{public static boolean protectedPeer,protectedData;public static MessagesController getInstance(int a){return new MessagesController();}public TLRPC.Chat getChat(long p){TLRPC.Chat c=new TLRPC.Chat();c.noforwards=protectedPeer;return c;}public TLRPC.UserFull getUserFull(long p){TLRPC.UserFull f=new TLRPC.UserFull();f.noforwards_peer_enabled=protectedData;return f;}}''',
            'org/telegram/messenger/MessagesStorage.java': '''package org.telegram.messenger;import java.util.*;import org.telegram.SQLite.SQLiteDatabase;public class MessagesStorage{static Map<Integer,MessagesStorage>all=new HashMap<>();public SQLiteDatabase db=new SQLiteDatabase();public static MessagesStorage getInstance(int a){return all.computeIfAbsent(a,k->new MessagesStorage());}public SQLiteDatabase getDatabase(){return db;}public DispatchQueue getStorageQueue(){return new DispatchQueue();}}''',
            'org/telegram/tgnet/TLRPC.java': '''package org.telegram.tgnet;import java.util.*;public class TLRPC{public static class Message{public int id=1,edit_date,date=100,ttl,ttl_period;public long dialog_id=123;public String message="old";public boolean noforwards;public Media media;public List<String>entities=new ArrayList<>();public static Message TLdeserialize(NativeByteBuffer b,int c,boolean e){return b.message;}}public static class TL_messageService extends Message{}public static class Media{public int ttl_seconds;}public static class Chat{public boolean noforwards;}public static class UserFull{public boolean noforwards_my_enabled,noforwards_peer_enabled;}}''',
            'org/telegram/tgnet/NativeByteBuffer.java': '''package org.telegram.tgnet;public class NativeByteBuffer{public final TLRPC.Message message;public boolean reused;public NativeByteBuffer(TLRPC.Message m){message=m;}public int readInt32(boolean e){return 0;}public void rewind(){}public void reuse(){reused=true;}}''',
            'app/nebulagram/ui/NebulaRichText.java': '''package app.nebulagram.ui;import java.util.*;public class NebulaRichText{public static String copy(String text,List<String> e){return text+"|"+e;}public static boolean same(String a,String b){return a.equals(b);}}''',
            'org/telegram/SQLite/SQLiteCursor.java': '''package org.telegram.SQLite;import java.util.*;import org.telegram.tgnet.*;public class SQLiteCursor{List<TLRPC.Message>items;int n=-1;public SQLiteCursor(List<TLRPC.Message>items){this.items=items;}public boolean next(){return ++n<items.size();}public NativeByteBuffer byteBufferValue(int c){return new NativeByteBuffer(items.get(n));}public void dispose(){}}''',
            'org/telegram/SQLite/SQLitePreparedStatement.java': '''package org.telegram.SQLite;import org.telegram.tgnet.*;public class SQLitePreparedStatement{SQLiteDatabase db;String sql;long peer;int mid;TLRPC.Message data;SQLitePreparedStatement(SQLiteDatabase d,String s){db=d;sql=s;}public SQLitePreparedStatement stepThis(){db.apply(sql);return this;}public void bindLong(int i,long v){peer=v;}public void bindInteger(int i,int v){mid=v;}public void bindByteBuffer(int i,NativeByteBuffer v){data=v.message;}public void step(){db.items.computeIfAbsent(peer+":"+mid,k->new java.util.ArrayList<>()).add(data);}public void dispose(){}}''',
            'org/telegram/SQLite/SQLiteDatabase.java': '''package org.telegram.SQLite;import java.util.*;import java.util.regex.*;import org.telegram.tgnet.*;public class SQLiteDatabase{public Map<String,List<TLRPC.Message>>items=new LinkedHashMap<>();public TLRPC.Message previous;public SQLitePreparedStatement executeFast(String s){return new SQLitePreparedStatement(this,s);}String key(String s){Matcher m=Pattern.compile("uid = (-?\\\\d+) AND mid = (-?\\\\d+)").matcher(s);if(!m.find())throw new AssertionError(s);return m.group(1)+":"+m.group(2);}public SQLiteCursor queryFinalized(String s){if(s.contains("FROM messages_v2"))return new SQLiteCursor(previous==null?Collections.emptyList():Arrays.asList(previous));List<TLRPC.Message>v=new ArrayList<>(items.getOrDefault(key(s),Collections.emptyList()));Collections.reverse(v);return new SQLiteCursor(v);}void apply(String s){if(s.startsWith("CREATE"))return;if(s.equals("DELETE FROM nebula_edit_history")){items.clear();return;}if(s.contains("seq NOT IN")){List<TLRPC.Message>v=items.get(key(s));while(v.size()>32)v.remove(0);return;}if(s.contains("GROUP BY")){while(items.size()>1000)items.remove(items.keySet().iterator().next());return;}if(s.startsWith("DELETE")){items.remove(key(s));return;}throw new AssertionError(s);}}''',
            'app/nebulagram/ui/Check.java': '''package app.nebulagram.ui;import org.telegram.messenger.*;import org.telegram.tgnet.*;import java.util.*;public class Check{static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("Check "+checks);}static TLRPC.Message message(String text,int edited){TLRPC.Message m=new TLRPC.Message();m.message=text;m.edit_date=edited;return m;}static void capture(TLRPC.Message old,TLRPC.Message next){MessagesStorage.getInstance(0).db.previous=old;NebulaEditHistory.capture(0,next);}static List<TLRPC.Message>read(){List<TLRPC.Message>[]r=new List[1];NebulaEditHistory.read(0,123,1,v->r[0]=v,()->{throw new AssertionError();});return r[0];}public static void main(String[]a){TLRPC.Message old=message("old",0),next=message("new",101);capture(old,next);check(read().isEmpty());NebulaEditHistory.setEnabled(0,true);capture(old,next);check(read().size()==1);capture(next,next);check(read().size()==1);capture(message("later",102),next);check(read().size()==1);old=message("😀 hi",0);old.entities.add("spoiler:0:2");old.entities.add("bold:3:5");capture(old,next);check(read().get(0).entities.equals(old.entities));check(NebulaEditHistory.changed(old,message("😀 hi",101)));TLRPC.Message same=message("😀 hi",101);same.entities.addAll(old.entities);check(!NebulaEditHistory.changed(old,same));for(int i=0;i<50;i++)capture(message("v"+i,0),next);check(read().size()==32&&read().get(0).message.equals("v49")&&read().get(31).message.equals("v18"));for(int kind=0;kind<7;kind++){TLRPC.Message p=message("excluded",0);if(kind==0)p.ttl=1;if(kind==1)p.ttl_period=1;if(kind==2){p.media=new TLRPC.Media();p.media.ttl_seconds=1;}if(kind==3)p.noforwards=true;if(kind==4)p.dialog_id=99;if(kind==5)p.dialog_id=777000;if(kind==6)p=new TLRPC.TL_messageService();check(!NebulaEditHistory.eligible(p,p.dialog_id));TLRPC.Message update=message("new",101);update.dialog_id=p.dialog_id;capture(p,update);}check(read().get(0).message.equals("v49"));check(MessagesStorage.getInstance(1).db.items.isEmpty());for(int i=2;i<=1002;i++){TLRPC.Message p=message("prior",0),n=message("new",101);p.id=n.id=i;capture(p,n);}check(read().isEmpty()&&MessagesStorage.getInstance(0).db.items.size()==1000);NebulaEditHistory.clear(0,123L,1002,()->{},()->{throw new AssertionError();});check(MessagesStorage.getInstance(0).db.items.size()==999);NebulaEditHistory.clear(0,null,null,()->{},()->{throw new AssertionError();});check(MessagesStorage.getInstance(0).db.items.isEmpty());System.out.println("Java production edit history: opt-in, text/entities, stale/metadata updates, native exclusions, 32/1000 bounds, account isolation and clearing passed");}}'''
        }
        sources = [str(java_source)]
        for name, content in files.items():
            path = work / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_text(content.replace(';', ';\n') if name.endswith('Check.java') else content, encoding='utf-8'); sources.append(str(path))
        home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
        subprocess.run([shutil.which('javac') or str(home / 'bin/javac.exe'), '--release', '8', '-encoding', 'UTF-8', '-d', str(work), *sources], check=True)
        subprocess.run([shutil.which('java') or str(home / 'bin/java.exe'), '-cp', str(work), 'app.nebulagram.ui.Check'], check=True)
print('Production schema and pruning statements execute with SQLite; source capture remains bounded and local')
