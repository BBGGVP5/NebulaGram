"""Execute device-only pin storage and the actual view/reorder adapters."""
from pathlib import Path
import os, re, shutil, subprocess, sys, tempfile, urllib.request

root = Path(__file__).resolve().parents[1]
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
core = root / 'platform/ios/overlay/submodules/TelegramCore/Sources/State'
with tempfile.TemporaryDirectory(prefix='nebula-local-pins-') as temporary:
    work = Path(temporary)
    if '--swift' in sys.argv:
        model = (core / 'NebulaLocalPinStore.swift').read_text(encoding='utf-8').replace('public ', '')
        adapter = '\n'.join(line for line in (core / 'NebulaLocalPins.swift').read_text(encoding='utf-8').splitlines() if line not in ['import Postbox', 'import SwiftSignalKit']).replace('public ', '')
        stubs = r'''
import Foundation
enum NoError:Error{}
struct Signal<T,E:Error>{let value:T}
final class ValuePromise<T>{var value:T;init(_ v:T,ignoreRepeated:Bool){value=v};func set(_ v:T){value=v};func get()->Signal<T,NoError>{Signal(value:value)}}
struct PeerGroupId:Equatable{let rawValue:Int32;static let root=PeerGroupId(rawValue:0)}
struct PeerId:Hashable{let raw:Int64;init(_ n:Int64){raw=n};var namespace:Int32{Int32(truncatingIfNeeded:raw >> 32)};func toInt64()->Int64{raw}}
enum Namespaces{enum Peer{static let SecretChat:Int32=3}}
enum TogglePeerChatPinnedLocation{case group(PeerGroupId);case filter(Int32)}
enum PinnedItemId:Equatable{case peer(PeerId)}
final class Account{let peerId:PeerId;let postbox=Postbox();init(_ id:Int64){peerId=PeerId(id)}}
final class Transaction{var groups:[PeerId:PeerGroupId]=[:];var remote:[PinnedItemId]=[];func getPeerChatListIndex(_ peer:PeerId)->(PeerGroupId,Int)?{groups[peer].map{($0,1)}};func getPinnedItemIds(groupId:PeerGroupId)->[PinnedItemId]{remote}}
final class Postbox{let state=Transaction();func transaction<T>(_ body:(Transaction)->T)->Signal<T,NoError>{Signal(value:body(state))}}
func _internal_getPinnedItemIds(transaction:Transaction,location:TogglePeerChatPinnedLocation)->[PinnedItemId]{transaction.remote}
'''
        test = r'''
@main struct Check {static func main() {
 let suite="NebulaLocalPinsFixture."+UUID().uuidString;let defaults=UserDefaults(suiteName:suite)!;defer{defaults.removePersistentDomain(forName:suite)}
 let store=NebulaLocalPinStore(defaults:defaults);let owner:Int64=991100001,large:Int64=9223372036854775806
 precondition(store.read(owner:owner).count==0 && store.read(owner:owner).enabled)
 precondition(!store.pin(owner:0,scope:"root:0",peer:1,pinned:true));precondition(!store.pin(owner:owner,scope:"root:2",peer:1,pinned:true))
 precondition(store.pin(owner:owner,scope:"root:0",peer:large,pinned:true));let old=store.read(owner:owner)
 precondition(store.pin(owner:owner,scope:"root:0",peer:large,pinned:true));precondition(store.read(owner:owner).count==1)
 precondition(store.pin(owner:owner,scope:"root:0",peer:2,pinned:true));precondition(old.orders["root:0"]==[large])
 precondition(store.pin(owner:owner,scope:"filter:25",peer:3,pinned:true));precondition(store.read(owner:owner+1).count==0)
 precondition(NebulaLocalPinStore(defaults:defaults).read(owner:owner).orders["root:0"]==[2,large])
 precondition(store.reorder(owner:owner,scope:"root:0",requested:[large,large,999]));precondition(store.read(owner:owner).orders["root:0"]==[large,2])
 precondition(store.setEnabled(owner:owner,value:false));precondition(!store.read(owner:owner).enabled && store.read(owner:owner).count==3)
 precondition(store.clear(owner:owner));precondition(!store.read(owner:owner).enabled && store.read(owner:owner).count==0)
 precondition(store.setEnabled(owner:owner,value:true))
 for id in 1...1000 {precondition(store.pin(owner:owner,scope:"root:0",peer:Int64(id),pinned:true))}
 precondition(!store.pin(owner:owner,scope:"root:1",peer:1001,pinned:true));precondition(store.pin(owner:owner,scope:"root:0",peer:1,pinned:false));precondition(store.pin(owner:owner,scope:"root:1",peer:1001,pinned:true))
 precondition(NebulaLocalPinStore.merge(remote:[7,8,7],local:[8,large,9,0])==[7,8,large,9])
 let account=Account(991100002),transaction=Transaction(),location=TogglePeerChatPinnedLocation.group(.root)
 let key="nebula.localPins.\(account.peerId.toInt64())";defer{NebulaLocalPins.clear(account:account);UserDefaults.standard.removeObject(forKey:key)}
 NebulaLocalPins.clear(account:account);NebulaLocalPins.setEnabled(account:account,value:true)
 for id:Int64 in [10,11,12,13] {precondition(NebulaLocalPins.toggle(account:account,location:location,peer:PeerId(id)))}
 transaction.groups=[PeerId(10):.root,PeerId(11):PeerGroupId(rawValue:1),PeerId(12):.root]
 let native=[PeerId(8),PeerId(10)];transaction.remote=native.map(PinnedItemId.peer)
 let projected=NebulaLocalPins.projectedPins(transaction:transaction,accountPeerId:account.peerId,group:.root,filterId:nil,native:native,excluded:[PeerId(12)])
 precondition(projected==native,"Archived, removed or excluded local peer leaked into the root list")
 transaction.groups[PeerId(13)] = .root
 precondition(NebulaLocalPins.projectedPins(transaction:transaction,accountPeerId:account.peerId,group:.root,filterId:nil,native:native,excluded:[]).map{$0.raw}==[8,10,13,12])
 let reordered=NebulaLocalPins.remoteReorder(transaction:transaction,accountPeerId:account.peerId,location:location,requested:[.peer(PeerId(12)),.peer(PeerId(8)),.peer(PeerId(13)),.peer(PeerId(10))])
 precondition(reordered==[.peer(PeerId(8)),.peer(PeerId(10))],"Local pin entered a remote reorder payload")
 precondition(NebulaLocalPins.snapshot(account:account).orders["root:0"]==[12,13,10,11])
 NebulaLocalPins.setEnabled(account:account,value:false)
 precondition(NebulaLocalPins.projectedPins(transaction:transaction,accountPeerId:account.peerId,group:.root,filterId:nil,native:native,excluded:[])==native)
 precondition(NebulaLocalPins.scope(group:.root,filterId:0)=="root:0" && NebulaLocalPins.scope(group:.root,filterId:25)=="filter:25")
 WRAPPER_CHECKS
 print("Swift local pins: immutable/persistent 64-bit order, scopes/accounts, 1000 bound, disabled state, native-first merge, archive/removal/exclusion projection and remote reorder isolation passed")
}}
'''
        # Typecheck and execute the actual public view adapter, with native
        # predicate code and synchronous signal fixtures. Not a rendered UI test.
        upstream = root / 'build/ios-13-upstream-20261013'
        if not upstream.exists(): upstream = root / 'vendor/telegram-ios'
        native_dir = work / 'native'; native_dir.mkdir(); subprocess.run(['git', 'init', '--quiet', str(native_dir)], check=True)
        names = ['submodules/Postbox/Sources/ChatListView.swift', 'submodules/ChatListUI/Sources/Node/ChatListNodeLocation.swift']
        for name in names:
            target = native_dir / name; target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(subprocess.check_output(['git', '-C', str(upstream), 'show', 'HEAD:' + name]))
        for patch in sorted((root / 'patches/ios').glob('*.patch')):
            subprocess.run(['git', '-C', str(native_dir), 'apply', '--whitespace=nowarn', *['--include=' + p for p in names], '-'], input=patch.read_text(encoding='utf-8').encode(), check=True, capture_output=True)
        def unit(text, signature):
            start = text.index(signature); brace = text.index('{', start); depth = 1; end = brace + 1
            while depth: depth += (text[end] == '{') - (text[end] == '}'); end += 1
            return text[start:end].replace('public ', '')
        predicate = unit((native_dir / names[0]).read_text(encoding='utf-8'), 'public struct ChatListFilterPredicate')
        wrapper = unit((native_dir / names[1]).read_text(encoding='utf-8'), 'public func chatListViewForLocation(')
        wrapper_stubs = r'''
infix operator |> : AdditionPrecedence
func |> <A,B>(value:A,body:(A)->B)->B{body(value)}
func mapToSignal<A,B,E>(_ body:@escaping(A)->Signal<B,E>)->(Signal<A,E>)->Signal<B,E>{ { body($0.value) } }
func distinctUntilChanged<A:Equatable,E>(_ signal:Signal<A,E>)->Signal<A,E>{signal}
struct ChatListMessageTagSummaryResultCalculation{}
struct Peer{let id:PeerId;let associatedPeerId:PeerId?=nil;let associatedPeerOverridesIdentity=false}
extension PeerGroupId{func _asGroup()->PeerGroupId{self}}
enum ChatListControllerLocation{case chatList(PeerGroupId);case forum(PeerId)}
struct ChatListFilterData{let predicate:ChatListFilterPredicate}
enum ChatListFilter{case filter(Int32,String,String?,ChatListFilterData)}
struct ChatListNodeLocation{let filter:ChatListFilter?}
struct ChatListNodeViewUpdate{let predicate:ChatListFilterPredicate?}
func chatListFilterPredicate(filter:ChatListFilterData,accountPeerId:PeerId)->ChatListFilterPredicate{filter.predicate}
func nebulaNativeChatListViewForLocation(chatListLocation:ChatListControllerLocation,location:ChatListNodeLocation,account:Account,shouldLoadCanMessagePeer:Bool,predicateOverride:ChatListFilterPredicate?=nil)->Signal<ChatListNodeViewUpdate,NoError>{Signal(value:ChatListNodeViewUpdate(predicate:predicateOverride))}
'''
        wrapper_checks = r'''
 account.postbox.state.groups=transaction.groups;account.postbox.state.remote=transaction.remote
 let plain=ChatListNodeLocation(filter:nil)
 precondition(chatListViewForLocation(chatListLocation:.chatList(.root),location:plain,account:account,shouldLoadCanMessagePeer:false).value.predicate==nil)
 NebulaLocalPins.setEnabled(account:account,value:true)
 let projectedView=chatListViewForLocation(chatListLocation:.chatList(.root),location:plain,account:account,shouldLoadCanMessagePeer:false).value.predicate!
 precondition(projectedView.nebulaKeepsRootItems && projectedView.nebulaGroupOnly == .root)
 precondition(projectedView.pinnedPeerIds.map{$0.raw} == [8,10,12,13])
 let unpinned=Peer(id:PeerId(90))
 precondition(projectedView.includes(peer:unpinned,groupId:.root,isRemovedFromTotalUnreadCount:false,isUnread:false,isContact:false,messageTagSummaryResult:nil))
 precondition(!projectedView.includes(peer:unpinned,groupId:PeerGroupId(rawValue:1),isRemovedFromTotalUnreadCount:false,isUnread:false,isContact:false,messageTagSummaryResult:nil))
 precondition(!projectedView.includes(peer:Peer(id:PeerId(12)),groupId:.root,isRemovedFromTotalUnreadCount:false,isUnread:false,isContact:false,messageTagSummaryResult:nil))
 let folder=ChatListFilterPredicate(includePeerIds:[],excludePeerIds:[PeerId(12)],pinnedPeerIds:[PeerId(8)],messageTagSummary:nil,includeAdditionalPeerGroupIds:[],include:{_,_,_,_,_ in true})
 precondition(NebulaLocalPins.toggle(account:account,location:.filter(25),peer:PeerId(12)))
 precondition(NebulaLocalPins.toggle(account:account,location:.filter(25),peer:PeerId(11)))
 let filtered=chatListViewForLocation(chatListLocation:.chatList(.root),location:ChatListNodeLocation(filter:.filter(25,"folder",nil,ChatListFilterData(predicate:folder))),account:account,shouldLoadCanMessagePeer:false).value.predicate!
 precondition(!filtered.nebulaKeepsRootItems && filtered.nebulaGroupOnly == nil && filtered.pinnedPeerIds.map{$0.raw} == [8,11])
 print("Actual iOS view adapter and native predicate: disabled bypass, root/archive membership, native extras flag, duplicates, excluded peers and independent folders passed")
'''
        test = test.replace('WRAPPER_CHECKS', wrapper_checks)
        path = work / 'Check.swift'; path.write_text(stubs + '\n' + predicate + '\n' + wrapper_stubs + '\n' + model + '\n' + adapter + '\n' + wrapper + '\n' + test, encoding='utf-8')
        binary = work / 'check'; subprocess.run(['swiftc', '-parse-as-library', str(path), '-o', str(binary)], check=True)
        subprocess.run([str(binary)], check=True, timeout=60)
    else:
        jar = root / 'build/ai-protocol-check/json-20240303.jar'
        if not jar.exists():
            jar.parent.mkdir(parents=True, exist_ok=True)
            urllib.request.urlretrieve('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar', jar)
        path = work / 'Check.java'
        path.write_text(r'''import app.nebulagram.ui.NebulaLocalPinStore;import java.util.*;
public class Check {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[]args){
  Map<Long,String>disk=new HashMap<>();boolean[]fail={false};
  NebulaLocalPinStore.Storage storage=new NebulaLocalPinStore.Storage(){public String read(long n){return disk.get(n);}public boolean write(long n,String v){if(fail[0])return false;disk.put(n,v);return true;}};
  NebulaLocalPinStore store=new NebulaLocalPinStore(storage);long owner=991100001,large=9223372036854775806L;
  check(store.read(owner).enabled&&store.read(owner).count()==0,"initial");check(!store.pin(0,"root:0",1,true,"peer"),"zero owner");check(!store.pin(owner,"root:2",1,true,"peer"),"bad scope");
  check(store.pin(owner,"root:0",large,true,"peer"),"pin");NebulaLocalPinStore.Snapshot old=store.read(owner);check(store.pin(owner,"root:0",large,true,"peer")&&store.read(owner).count()==1,"duplicate");
  check(store.pin(owner,"root:0",2,true,"peer"),"second");check(old.orders.get("root:0").equals(Arrays.asList(large)),"snapshot mutated");
  check(store.pin(owner,"filter:25",3,true,"other")&&store.read(owner+1).count()==0,"account/scope leak");
  check(new NebulaLocalPinStore(storage).read(owner).orders.get("root:0").equals(Arrays.asList(2L,large)),"restart lost identity/order");
  fail[0]=true;check(!store.pin(owner,"root:0",9,true,"x")&&store.read(owner).count()==3,"failed commit published");fail[0]=false;
  check(store.setEnabled(owner,false)&&store.read(owner).rank("root:0",2)==-1&&store.read(owner).count()==3,"disabled lost order");
  check(store.clear(owner)&&!store.read(owner).enabled&&store.read(owner).count()==0,"clear changed mode");check(store.setEnabled(owner,true),"enable");
  for(int id=1;id<=1000;id++)check(store.pin(owner,"root:0",id,true,"peer"),"bounded pin");check(!store.pin(owner,"root:1",1001,true,"peer"),"bound bypassed with scope");
  check(store.pin(owner,"root:0",1,false,"")&&store.pin(owner,"root:1",1001,true,"peer"),"removal did not free capacity");
  check(NebulaLocalPinStore.merge(Arrays.asList(7L,8L,7L),Arrays.asList(8L,large,9L,0L)).equals(Arrays.asList(7L,8L,large,9L)),"server merge changed order/identity");
  disk.put(owner+1,"bad JSON");check(!new NebulaLocalPinStore(storage).read(owner+1).enabled,"corrupt storage not closed");
  try{old.orders.clear();throw new AssertionError("Mutable snapshot");}catch(UnsupportedOperationException expected){}
  System.out.println("Java local pins: immutable/persistent 64-bit order, account/folder isolation, failed commits, 1000 bound, disabled/clear state, native-first merge and malformed storage passed");
 }
}''', encoding='utf-8')
        home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
        subprocess.run([shutil.which('javac') or str(home / 'bin/javac.exe'), '--release', '8', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(work), str(ui / 'NebulaLocalPinStore.java'), str(path)], check=True)
        subprocess.run([shutil.which('java') or str(home / 'bin/java.exe'), '-cp', os.pathsep.join([str(work), str(jar)]), 'Check'], check=True, timeout=60)

        def unit(text, signature):
            start = text.index(signature); brace = text.index('{', start); depth = 1; end = brace + 1
            while depth: depth += (text[end] == '{') - (text[end] == '}'); end += 1
            if text[end:end + 1] == ';': end += 1
            return text[start:end]
        native_tree = root / 'build/android-1302-integrated-20261015'
        if not native_tree.exists(): native_tree = root / 'vendor/telegram-android'
        native = (native_tree / 'TMessagesProj/src/main/java/org/telegram/messenger/MessagesController.java').read_text(encoding='utf-8')
        adapter = (ui / 'NebulaLocalPins.java').read_text(encoding='utf-8')
        source = '''package app.nebulagram.ui;import java.util.*;
class TLRPC {static class Dialog{long id;int folder_id,pinnedNum,last_message_date;boolean pinned;Dialog(long id,int date){this.id=id;last_message_date=date;}}static class TL_dialogFolder extends Dialog{TL_dialogFolder(){super(-100,0);}}}
class DialogObject{static long getLastMessageOrDraftDate(TLRPC.Dialog d,Object draft){return d.last_message_date;}}
class MediaDataController{Object getDraft(long id,int topic){return null;}}
class NebulaLocalPins{
 static Map<Integer,NebulaLocalPinStore.Snapshot> states=new HashMap<>();static NebulaLocalPinStore.Snapshot empty=new NebulaLocalPinStore(new NebulaLocalPinStore.Storage(){public String read(long n){return "";}public boolean write(long n,String v){return true;}}).read(1);
 static NebulaLocalPinStore.Snapshot snapshot(int account){return states.getOrDefault(account,empty);}
 private static final Map<Integer,String>filterScopes=new HashMap<>();
 SCOPE COMPARE
}
class MessagesController {
 static class PinnedMap extends HashMap<Long,Integer>{int get(long key,int fallback){return getOrDefault(key,fallback);}}
 static class DialogFilter{int id;PinnedMap pinnedDialogs=new PinnedMap();DialogFilter(int id){this.id=id;}}
 int currentAccount;DialogFilter sortingDialogFilter;MediaDataController getMediaDataController(){return new MediaDataController();}
 DATE ROOT
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static List<Long>ids(List<TLRPC.Dialog>v){List<Long>r=new ArrayList<>();for(TLRPC.Dialog d:v)r.add(d.id);return r;}
 public static void main(String[]args){
 NebulaLocalPinStore store=new NebulaLocalPinStore(new NebulaLocalPinStore.Storage(){public String read(long n){return "";}public boolean write(long n,String v){return true;}});
 store.pin(1,"root:0",1,true,"");store.pin(1,"root:0",2,true,"");store.pin(1,"filter:25",3,true,"");NebulaLocalPins.states.put(0,store.read(1));
 MessagesController m=new MessagesController();List<TLRPC.Dialog>v=new ArrayList<>();v.add(new TLRPC.Dialog(1,40));v.add(new TLRPC.Dialog(2,50));v.add(new TLRPC.Dialog(3,70));v.add(new TLRPC.Dialog(6,100));
 TLRPC.Dialog a=new TLRPC.Dialog(7,1),b=new TLRPC.Dialog(8,1);a.pinned=b.pinned=true;a.pinnedNum=3;b.pinnedNum=2;v.add(a);v.add(b);v.add(new TLRPC.TL_dialogFolder());
 Collections.sort(v,m.dialogComparator);check(ids(v).equals(Arrays.asList(-100L,7L,8L,2L,1L,6L,3L)));
 m.sortingDialogFilter=new DialogFilter(25);m.sortingDialogFilter.pinnedDialogs.put(6L,0);Collections.sort(v,m.dialogDateComparator);check(ids(v).equals(Arrays.asList(-100L,6L,3L,2L,1L,7L,8L)));
 for(TLRPC.Dialog x:v)for(TLRPC.Dialog y:v)for(TLRPC.Dialog z:v){int xy=m.dialogComparator.compare(x,y),yz=m.dialogComparator.compare(y,z);if(xy<0&&yz<0)check(m.dialogComparator.compare(x,z)<0);}
 store.setEnabled(1,false);NebulaLocalPins.states.put(0,store.read(1));Collections.sort(v,m.dialogComparator);check(ids(v).equals(Arrays.asList(-100L,7L,8L,6L,3L,2L,1L)));
 m.currentAccount=1;Collections.sort(v,m.dialogComparator);check(ids(v).equals(Arrays.asList(-100L,7L,8L,6L,3L,2L,1L)));
 check(a.pinned&&b.pinned&&a.pinnedNum==3&&b.pinnedNum==2);for(TLRPC.Dialog d:v)if(d.id!=7&&d.id!=8)check(!d.pinned);
 System.out.println("Actual native comparators: native pins first, separate root/filter ranks, unchanged native flags, transitivity, disabled mode and account isolation passed");
 }}
'''
        source = source.replace('SCOPE', unit(adapter, 'public static String scope(')).replace('COMPARE', unit(adapter, 'public static int compare('))
        source = source.replace('DATE', unit(native, 'private final Comparator<TLRPC.Dialog> dialogDateComparator')).replace('ROOT', unit(native, 'private Comparator<TLRPC.Dialog> dialogComparator'))
        path = work / 'MessagesController.java'; path.write_text(source, encoding='utf-8')
        subprocess.run([shutil.which('javac') or str(home / 'bin/javac.exe'), '--release', '8', '-encoding', 'UTF-8', '-cp', os.pathsep.join([str(work), str(jar)]), '-d', str(work), str(path)], check=True)
        subprocess.run([shutil.which('java') or str(home / 'bin/java.exe'), '-cp', os.pathsep.join([str(work), str(jar)]), 'app.nebulagram.ui.MessagesController'], check=True)

# Integration boundaries: no native pin status or server folder mutation in the adapters.
android = (ui / 'NebulaLocalPins.java').read_text(encoding='utf-8')
assert '.pinned =' not in android and 'toggleDialogPin' not in android and 'saveFilterToServer' not in android
assert 'TL_messages_getPeerDialogs' in android and 'offset + 50' in android and 'expectedOwner' in android
android_patch = (root / 'patches/android/0225-local-chat-pins.patch').read_text(encoding='utf-8')
assert 'DIALOGS_LOAD_TYPE_NEBULA_LOCAL_PINS && currentDialog != null) continue' in android_patch
assert 'loadType == DIALOGS_LOAD_TYPE_NEBULA_LOCAL_PINS ? 1 :' in android_patch
assert 'SELECT pinned FROM dialogs WHERE did = ' in android_patch
ios_patch = (root / 'patches/ios/0113-local-chat-pins.patch').read_text(encoding='utf-8')
assert 'nebulaKeepsRootItems' in ios_patch and 'nebulaGroupOnly' in ios_patch and '|> distinctUntilChanged' in ios_patch
assert 'itemIds: remoteItems)' in ios_patch and 'guard state.enabled' in ios_patch
assert 'addSynchronizePinnedChatsOperation' not in (core / 'NebulaLocalPins.swift').read_text(encoding='utf-8')
print('Native integration retains remote pin storage; cold hydration is bounded/read-only; iOS keeps root extras and filters reorder payloads')
