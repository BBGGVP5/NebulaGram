"""Exercise actual community-card request/lifecycle methods without the Android UI."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
source = (root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaCommunityCard.java').read_text(encoding='utf-8')

def method(signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

methods = '\n'.join(method(s) for s in [
    'protected void onAttachedToWindow()', 'private void refresh()', 'private void selectCommunity(',
    'public void didReceivedNotification(', 'protected void onDetachedFromWindow()'])
harness = r'''
import java.util.*;
class CommunityCardCheck {
 static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
 static class TLRPC {
  static class Peer {long dialog;Peer(long dialog){this.dialog=dialog;}}
  static class ChatPhoto {long photo_id;int dc_id;ChatPhoto(long id,int dc){photo_id=id;dc_id=dc;}}
  static class Chat {long id,linked_community_id;boolean community;ChatPhoto photo;String title="Hub";Chat(long id,boolean c){this.id=id;community=c;}}
  static class ChatFull {long id;List<Object> linked_peers=new ArrayList<>();ChatFull(long id,int count){this.id=id;for(int i=0;i<count;i++)linked_peers.add(new Object());}}
  static class TL_contacts_resolveUsername {String username;}
  static class TL_contacts_resolvedPeer {
   Peer peer;List<Chat> chats;List<Object> users=new ArrayList<>();
   TL_contacts_resolvedPeer(Chat chat){peer=new Peer(-chat.id);chats=Arrays.asList(chat);}
  }
 }
 static class ChatObject {static boolean isCommunity(TLRPC.Chat c){return c.community;}}
 static class DialogObject {static long getPeerDialogId(TLRPC.Peer p){return p==null?0:p.dialog;}static String getShortName(TLRPC.Chat c){return c.title;}}
 static class LocaleController {static String formatPluralString(String key,int count){return "Community with "+count+" chats";}}
 static class NebulaText {static String text(String ru,String en){return en;}}
 static class AndroidUtilities {
  static List<Runnable> queue=new ArrayList<>();
  static void runOnUIThread(Runnable r){queue.add(r);}
  static void drain(){while(!queue.isEmpty())queue.remove(0).run();}
 }
 static class NotificationCenter {
  interface NotificationCenterDelegate {void didReceivedNotification(int id,int account,Object... args);}
  static int chatInfoDidLoad=7;static NotificationCenter[] instances={new NotificationCenter(),new NotificationCenter()};
  Set<Object> observers=new HashSet<>();static NotificationCenter getInstance(int a){return instances[a];}
  void addObserver(Object o,int id){observers.add(o);}void removeObserver(Object o,int id){observers.remove(o);}
 }
 static class MessagesController {
  static MessagesController[] instances={new MessagesController(),new MessagesController()};
  Map<Long,TLRPC.Chat> chats=new HashMap<>();Map<Long,TLRPC.ChatFull> full=new HashMap<>();int writes,fullLoads;
  Object cachedSource;
  Object getUserOrChat(String username){return cachedSource;}
  static MessagesController getInstance(int account){return instances[account];}
  void putUsers(Object users,boolean cache){}
  void putChats(List<TLRPC.Chat> values,boolean cache){writes++;for(TLRPC.Chat c:values)chats.put(c.id,c);}
  TLRPC.Chat getChat(long id){return chats.get(id);}TLRPC.ChatFull getChatFull(long id){return full.get(id);}
  void loadFullChat(long id,int guid,boolean force){fullLoads++;}
 }
 static class MessagesStorage {
  static MessagesStorage getInstance(int account){return new MessagesStorage();}
  void putUsersAndChats(Object users,Object chats,boolean replace,boolean useQueue){}
 }
 static class ConnectionsManager {
  interface Callback {void accept(Object response,Object error);}
  static ConnectionsManager[] instances={new ConnectionsManager(),new ConnectionsManager()};
  Map<Integer,Callback> pending=new HashMap<>();Set<Integer> canceled=new HashSet<>();int next;
  static ConnectionsManager getInstance(int a){return instances[a];}
  int sendRequest(TLRPC.TL_contacts_resolveUsername query,Callback callback){int id=++next;pending.put(id,callback);return id;}
  void cancelRequest(int id,boolean notify){canceled.add(id);}
  void deliver(int id,Object response){pending.get(id).accept(response,null);AndroidUtilities.drain();}
 }
 static class BaseFragment {int getClassGuid(){return 123;}}
 static class NativeCard {
  int sets;long peer;String subtitle,title;Avatar avatarView=new Avatar();
  static class Avatar {void setImageDrawable(Object x){}}
  void updateColors(){}void setTitle(String s){title=s;}void setSubtitle(String s){subtitle=s;}
  void setChat(int account,TLRPC.Chat chat){sets++;peer=chat.id;TLRPC.ChatFull data=MessagesController.getInstance(account).getChatFull(chat.id);subtitle="Community with "+(data==null?0:data.linked_peers.size())+" chats";}
 }
 static class Parent {protected void onAttachedToWindow(){}protected void onDetachedFromWindow(){}}
 static class Card extends Parent implements NotificationCenter.NotificationCenterDelegate {
  final BaseFragment fragment=new BaseFragment();final int account;String username="supplied_hub";
  NativeCard card=new NativeCard();TLRPC.Chat community;long sourceChatId;boolean attached;int generation,request;
  long boundChatId,boundPhotoId,requestedCommunityId;int boundPhotoDc;
  Card(int account){this.account=account;}
  METHODS
 }
 public static void main(String[] args){
  Card c=new Card(1);ConnectionsManager network=ConnectionsManager.getInstance(1);MessagesController controller=MessagesController.getInstance(1);
  c.onAttachedToWindow();int oldRequest=c.request;c.onDetachedFromWindow();
  check(network.canceled.contains(oldRequest)&&NotificationCenter.getInstance(1).observers.isEmpty(),"detach cancels owned request and removes observer");
  c.onAttachedToWindow();int newRequest=c.request;
  network.deliver(oldRequest,new TLRPC.TL_contacts_resolvedPeer(new TLRPC.Chat(41,true)));
  check(c.community==null&&controller.writes==0,"late old response must not change reattached card or cache");
  network.deliver(newRequest,new TLRPC.TL_contacts_resolvedPeer(new TLRPC.Chat(42,true)));
  check(c.card.peer==42&&controller.fullLoads==1,"resolved real community is displayed and full metadata requested");
  check(c.card.subtitle.equals("Telegram community"),"unknown chat count is not fabricated as zero");
  check(MessagesController.getInstance(0).writes==0,"resolution and metadata use the host fragment account");
  TLRPC.ChatFull full=new TLRPC.ChatFull(42,7);controller.full.put(42L,full);int before=c.card.sets;
  c.didReceivedNotification(7,0,full);c.didReceivedNotification(7,1,new TLRPC.ChatFull(99,99));
  check(c.card.sets==before,"notifications from another account or community are ignored");
  c.didReceivedNotification(7,1,full);check(c.card.subtitle.equals("Community with 7 chats"),"actual server-linked chat count replaces generic subtitle");
  check(c.card.sets==before,"metadata refresh must not restart avatar binding");
  controller.chats.get(42L).photo=new TLRPC.ChatPhoto(100,2);
  c.didReceivedNotification(7,1,full);check(c.card.sets==before+1,"new photo binds once");
  controller.chats.get(42L).photo.photo_id=101;
  c.didReceivedNotification(7,1,full);check(c.card.sets==before+2,"in-place photo update is observed");
  c.didReceivedNotification(7,1,full);check(c.card.sets==before+2,"same photo never rebinds");
  controller.chats.get(42L).title="Renamed hub";
  c.didReceivedNotification(7,1,full);check(c.card.title.equals("Renamed hub")&&c.card.sets==before+2,"title changes preserve avatar");
  c.onDetachedFromWindow();before=c.card.sets;c.didReceivedNotification(7,1,full);check(c.card.sets==before,"detached card cannot update its UI");
  c=new Card(1);c.onAttachedToWindow();network.deliver(c.request,new TLRPC.TL_contacts_resolvedPeer(new TLRPC.Chat(43,false)));
  check(c.community==null,"a regular channel is not silently presented as a community");c.onDetachedFromWindow();
  c=new Card(1);c.onAttachedToWindow();TLRPC.Chat channel=new TLRPC.Chat(44,false);
  network.deliver(c.request,new TLRPC.TL_contacts_resolvedPeer(channel));
  check(c.community==null,"channel must wait for linked community metadata");
  channel.linked_community_id=45;controller.chats.put(45L,new TLRPC.Chat(45,true));
  c.didReceivedNotification(7,1,new TLRPC.ChatFull(44,0));
  check(c.community.id==45&&c.card.peer==45,"channel full info must select its actual linked community");
  controller.full.put(45L,new TLRPC.ChatFull(45,5));c.didReceivedNotification(7,1,controller.full.get(45L));
  check(c.card.subtitle.equals("Community with 5 chats"),"linked community count must come from server metadata");
  before=controller.fullLoads;
  c.didReceivedNotification(7,1,new TLRPC.ChatFull(44,0));
  check(controller.fullLoads==before,"source refresh cannot request community metadata repeatedly");
  controller.cachedSource=channel;
  Card cached=new Card(1);cached.onAttachedToWindow();
  check(cached.card.peer==45&&cached.card.subtitle.equals("Community with 5 chats"),"recreated card displays cache before network reply");
  before=cached.card.sets;network.deliver(cached.request,null);
  check(cached.card.sets==before&&cached.card.subtitle.equals("Community with 5 chats"),"network error retains cached avatar/count");
  cached.onDetachedFromWindow();cached.onAttachedToWindow();
  check(cached.card.sets==before,"reattach preserves loaded avatar");
  cached.onDetachedFromWindow();controller.cachedSource=null;
  channel.linked_community_id=0;c.didReceivedNotification(7,1,new TLRPC.ChatFull(44,0));
  check(c.community==null,"unlinking must clear old community navigation");c.onDetachedFromWindow();
  c=new Card(1);c.onAttachedToWindow();network.deliver(c.request,null);check(c.card.subtitle.equals("Tap to open in Telegram"),"network failure preserves direct Telegram route");c.onDetachedFromWindow();
  System.out.println("Community card: cached first display, stable avatar, photo updates, real peer/count, account isolation and lifecycle guards passed");
 }
}
'''.replace('METHODS', methods)
assert 'getImageReceiver().setCurrentAccount(account)' in source, 'avatar loading must use the host account'
with tempfile.TemporaryDirectory(prefix='nebula-community-card-') as temp:
    file = Path(temp) / 'CommunityCardCheck.java'
    file.write_text(harness, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp, str(file)], check=True)
    subprocess.run(['java', '-cp', temp, 'CommunityCardCheck'], check=True)
