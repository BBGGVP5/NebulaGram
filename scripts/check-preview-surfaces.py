from pathlib import Path
import sys, subprocess, xml.etree.ElementTree as ET
r=Path(__file__).resolve().parent.parent
native=Path(sys.argv[1])/'TMessagesProj/src/main/java/org/telegram/ui'
def method(s,signature):
 start=s.index(signature);end=s.index('{',start)+1;depth=1
 while depth:
  depth+=(s[end]=='{')-(s[end]=='}');end+=1
 return s[start:end]
cell=(native/'Cells/ChatMessageCell.java').read_text(encoding='utf-8')
swipe=(native/'Components/PopupSwipeBackLayout.java').read_text(encoding='utf-8')
chat=(native/'ChatActivity.java').read_text(encoding='utf-8')
assert 'private boolean drawingMenuPreview;' in cell, 'missing isolated media preview state'
scope=method(cell,'public void drawMenuPreview(Canvas canvas)')
policy=method(swipe,'private boolean usesNebulaGlass()').replace('app.nebulagram.ui.NebulaMenuStyle.enabled()', 'enabled')
src='''class SurfaceCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static class Canvas {}
 static class Cell {
  static class Image {boolean skip;void setSkipUpdateFrame(boolean x){skip=x;}} Image photoImage=new Image();
 boolean fullyDraw,needNewVisiblePart=true,drawingMenuPreview,skipFrameUpdate=true,fail;
 float alphaInternal=.4f;int firstVisibleBlockNum=8,lastVisibleBlockNum=9;
 void draw(Canvas c){check(fullyDraw && drawingMenuPreview && !skipFrameUpdate && alphaInternal==1f && !needNewVisiblePart);if(fail)throw new IllegalStateException();}
 SCOPE
 }
 static class Swipe {boolean enabled;int foregroundColor; POLICY}
 public static void main(String[] a){
 int count=0;
 for(boolean fail:new boolean[]{false,true})for(boolean drawing:new boolean[]{false,true})for(float alpha:new float[]{0,.4f,1}){
 Cell c=new Cell();c.fail=fail;c.drawingMenuPreview=drawing;c.alphaInternal=alpha;
 try{c.drawMenuPreview(new Canvas());}catch(IllegalStateException e){check(fail);}
 check(c.drawingMenuPreview==drawing && c.alphaInternal==alpha && c.skipFrameUpdate && c.photoImage.skip && c.needNewVisiblePart && !c.fullyDraw);count++;
 }
 for(boolean enabled:new boolean[]{false,true})for(int color:new int[]{0,0xff123456}){
 Swipe s=new Swipe();s.enabled=enabled;s.foregroundColor=color;check(s.usesNebulaGlass()==(enabled&&color==0));
 }
 System.out.println(count+" media preview restoration paths; glass/native page policy passed");
 }
}'''.replace('SCOPE',scope).replace('POLICY',policy)
w=r/'build/preview-surface-check';w.mkdir(exist_ok=True)
p=w/'SurfaceCheck.java';p.write_text(src,encoding='utf-8')
subprocess.run(['javac',str(p)],check=True);subprocess.run(['java','-cp',str(w),'SurfaceCheck'],check=True)
assert cell.count('if (!drawingMenuPreview && getY() < 0)') == 2
assert cell.count('if (!drawingMenuPreview && getY() + getMeasuredHeight() > parentHeight)') == 2
assert 'if (i != 0 && !usesNebulaGlass())' in swipe
assert 'if (i == 0 && !usesNebulaGlass())' in swipe
assert 'backgroundView.setAlpha(usesNebulaGlass() ? 1f - transitionProgress : 1f)' in swipe
assert 'minY = Math.max(minY, AndroidUtilities.statusBarHeight + dp(8))' in chat
ns='{http://schemas.android.com/apk/res/android}'
xml=ET.parse(r/'platform/android/overlay/TMessagesProj_AppStandalone/src/main/res/drawable/tg_splash_320.xml')
paths=xml.findall('.//path');plane=paths[-1]
assert ns+'strokeWidth' not in plane.attrib, 'thick vector stroke changes the splash silhouette'
assert 'A6.5,6.5' in plane.get(ns+'pathData')
assert len(paths)==3, 'keep both brand trails'
assert xml.find('group').get(ns+'translateX') is None
print('Media clipping, swipe surfaces, safe top inset and rounded splash geometry passed')
# Album regression: compile the actual asynchronous preparation/cleanup against
# minimal UI doubles, so cancellation and close cannot leave the recycler expanded.
for signature in ['private boolean prepareNebulaAlbumMenu(', 'private void clearNebulaAlbumPreview()']:
 assert signature in chat
album=method(chat,'private boolean prepareNebulaAlbumMenu(')
clear=method(chat,'private void clearNebulaAlbumPreview()')
extra=method(chat,'protected void calculateExtraLayoutSpace(').replace('@Override','')
java='''import java.util.*;
class AlbumCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static class AndroidUtilities {static class Point {int y=1000;}static Point displaySize=new Point();}
 static class View {
 interface OnLayoutChangeListener {void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob);}
 OnLayoutChangeListener listener;ArrayList<Runnable> posts=new ArrayList<>();boolean attached=true;int layouts;
 void addOnLayoutChangeListener(OnLayoutChangeListener x){listener=x;}void removeOnLayoutChangeListener(OnLayoutChangeListener x){if(listener==x)listener=null;}
 int getWidth(){return 500;}int getHeight(){return 600;}void requestLayout(){layouts++;}void post(Runnable r){posts.add(r);}
 boolean isAttachedToWindow(){return attached;}
 void layout(){if(listener!=null)listener.onLayoutChange(this,0,0,500,600,0,0,500,600);}
 void flush(){for(Runnable r:new ArrayList<>(posts))r.run();posts.clear();}
 }
 static class MessageObject {
 static class GroupedMessagePosition {int minY,maxY;float ph;float[] siblingHeights;GroupedMessagePosition(int row){minY=maxY=row;ph=.5f;}}
 static class GroupedMessages {boolean isDocuments;ArrayList<Object> messages=new ArrayList<>(Arrays.asList(1,2));ArrayList<GroupedMessagePosition> posArray=new ArrayList<>(Arrays.asList(new GroupedMessagePosition(0),new GroupedMessagePosition(1)));}
 }
 static class ChatMessageCell extends View {
 MessageObject message=new MessageObject();MessageObject.GroupedMessages group=new MessageObject.GroupedMessages();
 MessageObject getMessageObject(){return message;}MessageObject.GroupedMessages getCurrentMessagesGroup(){return group;}int getMeasuredHeight(){return 200;}
 }
 static class Host {
 View chatListView=new View();boolean enabled=true,paused,nebulaAlbumMenuReady,opens=true;int nebulaAlbumExtraSpace,nebulaAlbumRequest,menus;
 Object scrimPopupWindow;View.OnLayoutChangeListener nebulaAlbumLayoutListener;
 int dp(int x){return x;}
 void createMenu(View v,boolean single,boolean list,float x,float y,boolean search,boolean press,boolean edit){check(nebulaAlbumMenuReady);check(nebulaAlbumExtraSpace==724);menus++;if(opens)scrimPopupWindow=new Object();}
 ALBUM
 CLEAR
 }
 static class RecyclerView {static class State{}}
 static class Base {protected void calculateExtraLayoutSpace(RecyclerView.State s,int[] e){e[0]=10;e[1]=20;}}
 static class Manager extends Base {int nebulaAlbumExtraSpace; EXTRA}
 public static void main(String[] args){
 for(int scenario=0;scenario<6;scenario++){
 Host h=new Host();ChatMessageCell c=new ChatMessageCell();
 check(h.prepareNebulaAlbumMenu(c,false,false,1,1,true,true,false));check(h.menus==0);check(h.nebulaAlbumExtraSpace==724);
 h.chatListView.layout();check(h.menus==0); // defer until layout transaction has finished
 if(scenario==1)c.attached=false;
 if(scenario==2)c.message=new MessageObject();
 if(scenario==3)h.paused=true;
 if(scenario==4)h.clearNebulaAlbumPreview();
 if(scenario==5)h.opens=false;
 h.chatListView.flush();
 check(h.menus==(scenario==0||scenario==5?1:0));
 if(scenario!=0)check(h.nebulaAlbumExtraSpace==0);
 h.clearNebulaAlbumPreview();check(h.nebulaAlbumExtraSpace==0 && h.nebulaAlbumLayoutListener==null);
 }
 Host h=new Host();ChatMessageCell c=new ChatMessageCell();
 check(!h.prepareNebulaAlbumMenu(c,true,false,0,0,true,true,false));
 h.enabled=false;check(!h.prepareNebulaAlbumMenu(c,false,false,0,0,true,true,false));
 h.enabled=true;c.group.isDocuments=true;check(!h.prepareNebulaAlbumMenu(c,false,false,0,0,true,true,false));
 c.group.isDocuments=false;check(h.prepareNebulaAlbumMenu(c,false,false,0,0,true,true,false));h.clearNebulaAlbumPreview();check(h.chatListView.listener==null);
 Manager m=new Manager();int[] e=new int[2];m.calculateExtraLayoutSpace(new RecyclerView.State(),e);check(e[0]==10&&e[1]==20);
 m.nebulaAlbumExtraSpace=724;m.calculateExtraLayoutSpace(new RecyclerView.State(),e);check(e[0]==724&&e[1]==724);
 System.out.println("Album pre-layout, both scroll directions, deferred open, cancellation, recycling, native bypass and cleanup passed");
 }
}'''.replace('ALBUM',album.replace('app.nebulagram.ui.NebulaAppearance.messageMenuBlur()','enabled')).replace('CLEAR',clear).replace('EXTRA',extra)
p=w/'AlbumCheck.java';p.write_text(java,encoding='utf-8')
subprocess.run(['javac',str(p)],check=True);subprocess.run(['java','-cp',str(w),'AlbumCheck'],check=True)
assert 'if (scrimView == null) clearNebulaAlbumPreview();' in chat
assert 'public void onFragmentDestroy() {\n        clearNebulaAlbumPreview();' in chat

assert "public void onPause() {\n        clearNebulaAlbumPreview();" in chat
