"""Execute production scrub/routing/viewport policies with small Java UI doubles."""
from pathlib import Path
import subprocess, sys

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
native = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram'
work = root / 'build/discussion-input-check'
work.mkdir(parents=True, exist_ok=True)

def write(name, text):
    p = work / name
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')
    return str(p)

def method(s, signature):
    start = s.index(signature); end = s.index('{', start) + 1; depth = 1
    while depth:
        depth += (s[end] == '{') - (s[end] == '}'); end += 1
    return s[start:end]

files = []
stubs = {
    'android/graphics/Canvas.java': 'package android.graphics; public class Canvas {}',
    'android/graphics/RectF.java': '''package android.graphics; public class RectF {
 public float left,top,right,bottom; public void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
 public boolean contains(float x,float y){return x>=left&&x<right&&y>=top&&y<bottom;}
 public float width(){return right-left;}public float centerX(){return (left+right)/2;}
 public void offset(float x,float y){left+=x;right+=x;top+=y;bottom+=y;}}''',
    'android/view/View.java': '''package android.view; public class View {
 public int invalidations;public View getParent(){return this;}public void requestDisallowInterceptTouchEvent(boolean b){}
 public void invalidate(){invalidations++;}public Object getContext(){return null;}public int getWidth(){return 300;}}''',
    'android/view/ViewConfiguration.java': '''package android.view; public class ViewConfiguration {
 public static ViewConfiguration get(Object c){return new ViewConfiguration();}public int getScaledTouchSlop(){return 8;}}''',
    'android/view/MotionEvent.java': '''package android.view; public class MotionEvent {
 public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5;
 int a,n;float x,y;public MotionEvent(int a,float x,float y,int n){this.a=a;this.x=x;this.y=y;this.n=n;}
 public int getActionMasked(){return a;}public float getX(){return x;}public float getY(){return y;}public int getPointerCount(){return n;}}''',
    'app/nebulagram/ui/NebulaTabLens.java': '''package app.nebulagram.ui;
 public class NebulaTabLens {
 public static float left,right;public NebulaTabLens(android.view.View v){}
 public void draw(android.graphics.Canvas c,float l,float t,float r,float b,int a){left=l;right=r;}
 public void drawDrag(android.graphics.Canvas c,float l,float t,float r,float b,int a){draw(c,l,t,r,b,a);}
 public void reset(){}public void pulse(){}public void setPressed(boolean v){}
 }''',
}
for name, text in stubs.items(): files.append(write(name, text))
files.append(str(ui/'NebulaTabGesture.java'))
files.append(write('GestureCheck.java', '''
import app.nebulagram.ui.*;import android.view.*;import android.graphics.*;
class GestureCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static class Tabs implements NebulaTabGesture.Tabs {
 int selected,commits;boolean disabled;
 public int count(){return 3;}public int selected(){return selected;}
 public boolean bounds(int i,RectF r){r.set(i*100,0,i*100+100,50);return !(disabled&&i==2);}
 public void select(int i){selected=i;commits++;}
 }
 static boolean event(NebulaTabGesture g,int a,float x,float y,int n){return g.onTouch(new MotionEvent(a,x,y,n));}
 public static void main(String[] args){
 for(int start=0;start<3;start++)for(int end=0;end<3;end++){
 Tabs t=new Tabs();t.selected=start;NebulaTabGesture g=new NebulaTabGesture(new View(),t);
 check(!event(g,0,start*100+20,20,1));
 check(!event(g,2,start*100+23,20,1));check(t.commits==0);
 check(event(g,2,end*100+50,20,1));check(g.takeChildCancel());check(!g.takeChildCancel());
 check(t.commits==0);g.draw(new Canvas(),0);check(Math.abs((NebulaTabLens.left+NebulaTabLens.right)/2-(end*100+50))<.1);
 check(event(g,1,end*100+50,20,1));check(t.commits==(start==end?0:1));check(t.selected==end);check(!g.isDragging());
 }
 for(int cancel=0;cancel<5;cancel++){
 Tabs t=new Tabs();NebulaTabGesture g=new NebulaTabGesture(new View(),t);
 event(g,0,20,20,1);event(g,2,140,20,1);check(g.takeChildCancel());
 if(cancel==0){check(event(g,3,140,20,1));event(g,1,240,20,1);}
 if(cancel==1){check(event(g,5,140,20,2));check(event(g,2,240,20,1));check(event(g,1,240,20,1));}
 if(cancel==2){t.disabled=true;check(event(g,1,240,20,1));}
 if(cancel==3){check(event(g,1,240,60,1));}
 if(cancel==4){g.reset();event(g,1,240,20,1);}
 check(t.commits==0&&!g.isDragging());
 }
 Tabs t=new Tabs();NebulaTabGesture g=new NebulaTabGesture(new View(),t);
 event(g,0,20,20,1);check(!event(g,1,20,20,1));check(!g.takeChildCancel()); // Native tap
 event(g,0,20,20,1);check(!event(g,2,20,40,1));check(!event(g,2,240,40,1));check(!event(g,1,240,40,1));
 event(g,0,20,60,1);check(!event(g,2,240,20,1));check(!event(g,1,240,20,1)); // Outside starts
 check(t.commits==0);
 RectF r=new RectF();r.set(50,0,150,50);g.drawInterpolated(new Canvas(),r,0);check(NebulaTabLens.left==50);
 System.out.println("Scrub release, tap, vertical, disabled, outside, multi-touch and cancellation passed");
 }
}'''))
profile = (native/'ui/ProfileActivity.java').read_text(encoding='utf-8')
bar = (native/'ui/ActionBar/ActionBar.java').read_text(encoding='utf-8')
rich = (native/'messenger/RichMessageLayout.java').read_text(encoding='utf-8')
route = method(profile,'private long nebulaProfileChannelId()')
clip = method(rich,'private void drawInternal(Canvas canvas, ChatMessageCell.TransitionParams tp)')
search = method(bar,'public void onSearchFieldVisibilityChanged(boolean visible)')
search = search[:search.index('        checkMenuItemsWidth();')] + '\n    }'
files.append(write('PolicyCheck.java', '''class PolicyCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static class Info {long personal_channel_id=11,linked_chat_id=22;}
 static class Chat {boolean megagroup;}
 static class Route {long userId;boolean isTopic;Info userInfo,chatInfo;Chat currentChat; ROUTE }
 static class Menu {int visibility;void setVisibility(int v){visibility=v;}}
 static class Bar {boolean isSearchFieldVisible,nebulaFloatingChatHeader,nebulaChatMenuHidden,actionMode;
 final int GONE=8,VISIBLE=0;Menu menu=new Menu();boolean isActionModeShowed(){return actionMode;} SEARCH }
 static class Canvas{}static class ChatMessageCell {static class TransitionParams{}
 int visibleHeight=400,childPosition=1200,textY=20;boolean preview;boolean isDrawingMenuPreview(){return preview;}}
 static class Rich {ChatMessageCell cell;boolean clipped;float top,bottom;
 void drawInternal(Canvas c,ChatMessageCell.TransitionParams t,boolean clip,float a,float b){clipped=clip;top=a;bottom=b;} CLIP }
 public static void main(String[] a){
 Route r=new Route();check(r.nebulaProfileChannelId()==0);r.chatInfo=new Info();r.currentChat=new Chat();
 check(r.nebulaProfileChannelId()==0);r.currentChat.megagroup=true;check(r.nebulaProfileChannelId()==22);
 r.isTopic=true;check(r.nebulaProfileChannelId()==0);r.userId=1;check(r.nebulaProfileChannelId()==0);
 r.userInfo=new Info();check(r.nebulaProfileChannelId()==11);
 for(boolean floating:new boolean[]{false,true})for(boolean hidden:new boolean[]{false,true})for(boolean mode:new boolean[]{false,true}){
 Bar b=new Bar();b.nebulaFloatingChatHeader=floating;b.nebulaChatMenuHidden=hidden;b.actionMode=mode;b.menu.visibility=8;
 b.onSearchFieldVisibilityChanged(true);check(b.isSearchFieldVisible);
 check(b.menu.visibility==(floating&&!mode?0:8));
 b.onSearchFieldVisibilityChanged(false);check(!b.isSearchFieldVisible);
 check(b.menu.visibility==(floating&&!mode?(hidden?8:0):8));
 }
 Rich q=new Rich();q.drawInternal(new Canvas(),null);check(!q.clipped);
 q.cell=new ChatMessageCell();q.drawInternal(new Canvas(),null);check(q.clipped&&q.top==1180&&q.bottom==1580);
 q.cell.preview=true;q.drawInternal(new Canvas(),null);check(!q.clipped&&q.cell.childPosition==1200&&q.cell.visibleHeight==400);
 q.cell.preview=false;q.drawInternal(new Canvas(),null);check(q.clipped); // Restore list culling
 System.out.println("Linked-channel routing, search visibility and Rich preview/list clipping passed");
 }
}'''.replace('ROUTE',route).replace('SEARCH',search).replace('CLIP',clip)))
subprocess.run(['javac','-encoding','UTF-8','-d',str(work),*files],check=True)
for cls in ['GestureCheck','PolicyCheck']:
    subprocess.run(['java','-cp',str(work),cls],check=True)

cell = (native/'ui/Cells/ProfileChannelCell.java').read_text(encoding='utf-8')
assert 'true, fragment.getCurrentAccount(), resourcesProvider' in cell
assert 'TL_messages_getHistory' in cell and 'history.limit = 10' in cell and 'specific.id.add(id)' in cell
assert 'Collections.sort(res.messages,' in cell and 'loading = messageObjects == null;' in cell
assert 'fetcher.fetch(linkedId, 0);' in profile and 'profileChannelMessageFetcher == fetcher && !isFinished' in profile
assert 'getChat(nebulaProfileChannelId())' in profile and 'args.putLong("chat_id", linkedChannelId)' in profile
chat = (native/'ui/ChatActivity.java').read_text(encoding='utf-8')
assert 'return navbarContentDrawableFactory.create(view)' in chat
assert 'chatInputViewsContainer.setNebulaSearchBackground(searchContainer,' in chat
assert 'hideMenu && !isSearchFieldVisible' in bar
assert 'layout.draw(canvas, padLeft, padRight, transitionParams);' in (native/'ui/Cells/ChatMessageCell.java').read_text(encoding='utf-8')
attach = (native/'ui/Components/ChatAttachAlert.java').read_text(encoding='utf-8')
assert 'getOnItemClickListener().onItemClick(child, position)' in attach
assert '!canScrollHorizontally(-1) && !canScrollHorizontally(1)' in attach, 'keep scrolling for long attachment strips'
assert 'currentAttachLayout.hasCustomBackground()' in method(attach,'public boolean hasNebulaSheetGlass()')
assert 'nextAttachLayout.hasCustomBackground()' in method(attach,'public boolean hasNebulaSheetGlass()')
sheet = (ui/'NebulaSheetSurface.java').read_text(encoding='utf-8')
assert 'host.getRootView() == root' in sheet and 'root.draw(capture)' in sheet and 'finally { source.endRecording(); }' in sheet
assert 'removeOnPreDrawListener(this)' in sheet and 'materials[slot]' in sheet
assert '!parentAlert.hasNebulaSheetGlass()' in (native/'ui/Components/ChatAttachAlertLocationLayout.java').read_text(encoding='utf-8')
print('Native callbacks, scoped material, account routing and unmodified Rich rendering are wired')
