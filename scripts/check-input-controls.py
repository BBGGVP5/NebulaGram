"""Run production home visibility, tab selection and title touch policies."""
from pathlib import Path
import subprocess, sys, re, xml.etree.ElementTree as ET
r=Path(__file__).resolve().parent.parent
n=Path(sys.argv[1])/'TMessagesProj/src/main/java/org/telegram/ui'
o=r/'platform/android/overlay/TMessagesProj'
w=r/'build/input-controls-check';w.mkdir(exist_ok=True)
def method(s, signature):
 a=s.index(signature);b=s.index('{',a)+1;depth=1
 while depth:depth+=(s[b]=='{')-(s[b]=='}');b+=1
 return s[a:b]
dialogs=(n/'DialogsActivity.java').read_text(encoding='utf-8')
tabs=(n/'Components/PagerSlidingTabStrip.java').read_text(encoding='utf-8')
header=(n/'Components/ChatAvatarContainer.java').read_text(encoding='utf-8')
visibility=method(dialogs,'private void updateFloatingButtonVisibility(boolean animated)')
offset=method(dialogs,'private void updateFloatingButtonOffset()')
selected=method(tabs,'public int selected()')
page_selected=method(tabs,'public void onPageSelected(int position)')
idle=method(tabs,'public void onPageScrollStateChanged(int state)')
touch=method(header,'public boolean onTouchEvent(MotionEvent ev)')
draw=method(header,'protected void dispatchDraw(Canvas canvas)')
prefs=w/'app/nebulagram/ui/NebulaAppearance.java';prefs.parent.mkdir(parents=True,exist_ok=True)
prefs.write_text('''package app.nebulagram.ui;public class NebulaAppearance {
 public static boolean camera,compose,floating;public static boolean hideHomeCamera(){return camera;}
 public static boolean hideHomeCompose(){return compose;}public static boolean chatHeader(){return floating;}
}''',encoding='utf-8')
source='''import app.nebulagram.ui.NebulaAppearance;
class InputControlsCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static class View {boolean visible;float y;void setButtonVisible(boolean v,boolean a){visible=v;}void setTranslationY(float f){y=f;}}
 static int dp(int x){return x;}
 static class Home {boolean onlySelect,inPreviewMode,searching,floatingButtonHidden;int initialDialogsType,folderId,communityId;
 int navigationBarHeight=20,additionFloatingButtonOffset=70,additionalFloatingTranslation=3,floatingButtonPanOffset=2;
 View floatingButton3=new View(),floatingButtonStories=new View(),storyHint=new View(); VIS OFFSET }
 static class ViewPager {static int SCROLL_STATE_IDLE=0;int current;int getCurrentItem(){return current;}}
 static class Tab {boolean selected;void setSelected(boolean b){selected=b;}}
 static class Container {Tab[] children={new Tab(),new Tab(),new Tab()};int getChildCount(){return children.length;}Tab getChildAt(int i){return children[i];}}
 static class Delegate {void onPageSelected(int p){}void onPageScrollStateChanged(int s){}}
 static class Strip {ViewPager pager=new ViewPager();int currentPosition;float currentPositionOffset=.4f;
 Delegate delegatePageListener;Container tabsContainer=new Container();void invalidate(){}void scrollToChild(int i,int o){}
 SELECTED PAGESELECTED IDLE }
 static class MotionEvent {static int ACTION_DOWN=0,ACTION_UP=1,ACTION_CANCEL=3;int action;MotionEvent(int a){action=a;}int getAction(){return action;}int getActionMasked(){return action;}}
 static class Bounce {boolean pressed;void setPressed(boolean b){pressed=b;}float getScale(float f){return .98f;}}
 static class AndroidUtilities {static void cancelRunOnUIThread(Runnable r){}static void runOnUIThread(Runnable r,int t){}}
 static class ViewConfiguration {static int getLongPressTimeout(){return 500;}}
 static class Canvas {int scales;void save(){}void restore(){}void scale(float a,float b,float x,float y){scales++;}}
 static class ActionBar {static int getCurrentActionBarHeight(){return 56;}}
 static class Base {boolean onTouchEvent(MotionEvent e){return false;}protected void dispatchDraw(Canvas c){}}
 static class Header extends Base {boolean pressed,nebulaCenteredTitle,clickable=true;int opens;Bounce bounce=new Bounce();Runnable onLongClick=()->{};
 boolean canSearch(){return true;}boolean isClickable(){return clickable;}void openProfile(boolean avatar){check(!avatar);opens++;}
 float getPivotX(){return 200;}int getHeight(){return 56;} TOUCH DRAW }
 public static void main(String[] args){
 int cases=0;
 for(boolean camera:new boolean[]{false,true})for(boolean compose:new boolean[]{false,true})for(boolean select:new boolean[]{false,true})for(boolean hidden:new boolean[]{false,true}){
 NebulaAppearance.camera=camera;NebulaAppearance.compose=compose;Home h=new Home();h.onlySelect=select;h.initialDialogsType=10;h.floatingButtonHidden=hidden;
 h.updateFloatingButtonVisibility(false);h.updateFloatingButtonOffset();
 check(h.floatingButton3.visible==(!hidden&&(select||!compose)));check(h.floatingButtonStories.visible==(!hidden&&!camera));
 check(h.floatingButton3.y==-95);check(h.floatingButtonStories.y==(-95-(!select&&compose?0:52)));check(h.storyHint.y==h.floatingButtonStories.y);cases++;
 }
 Home h=new Home();h.searching=true;h.updateFloatingButtonVisibility(false);check(!h.floatingButton3.visible&&!h.floatingButtonStories.visible);
 h.searching=false;h.folderId=1;h.updateFloatingButtonVisibility(false);check(!h.floatingButton3.visible);
 for(int page=0;page<3;page++){
 Strip s=new Strip();s.pager.current=page;s.currentPosition=(page+1)%3;
 check(s.selected()==page);s.onPageSelected(page);check(s.currentPosition==page&&s.currentPositionOffset==0);
 for(int i=0;i<3;i++)check(s.tabsContainer.children[i].selected==(i==page));
 s.currentPosition=0;s.currentPositionOffset=.5f;s.onPageScrollStateChanged(ViewPager.SCROLL_STATE_IDLE);check(s.currentPosition==page&&s.currentPositionOffset==0);
 }
 for(int action:new int[]{MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL}){
 Header t=new Header();check(t.onTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN)));check(t.pressed);
 check(t.onTouchEvent(new MotionEvent(action)));check(!t.pressed&&!t.bounce.pressed);check(t.opens==(action==MotionEvent.ACTION_UP?1:0));
 t.onTouchEvent(new MotionEvent(action));check(t.opens==(action==MotionEvent.ACTION_UP?1:0));
 }
 Header t=new Header();t.nebulaCenteredTitle=true;NebulaAppearance.floating=true;Canvas c=new Canvas();t.dispatchDraw(c);check(c.scales==0);
 t.nebulaCenteredTitle=false;t.dispatchDraw(c);check(c.scales==1);
 System.out.println(cases+" home visibility/offset cases; authoritative pager selection; cancelled and isolated header press passed");
 }
}'''
for k,v in [('PAGESELECTED',page_selected),('SELECTED',selected),('IDLE',idle),('VIS',visibility),('OFFSET',offset),('TOUCH',touch),('DRAW',draw)]:source=re.sub(r'\b'+k+r'\b',lambda m:v,source)
p=w/'InputControlsCheck.java';p.write_text(source,encoding='utf-8')
subprocess.run(['javac','-encoding','UTF-8','-d',str(w),str(prefs),str(p)],check=True)
subprocess.run(['java','-cp',str(w),'InputControlsCheck'],check=True)
appearance=(o/'src/main/java/app/nebulagram/ui/NebulaAppearance.java').read_text(encoding='utf-8')
schema=(o/'src/main/java/app/nebulagram/ui/NebulaSettingsSchema.java').read_text(encoding='utf-8')
settings=(o/'src/main/java/app/nebulagram/ui/NebulaSectionFragment.java').read_text(encoding='utf-8')
for key in ['hide_home_camera','hide_home_compose']:
 assert 'getBoolean("'+key+'", false)' in appearance
 assert 'map.put("'+key+'", Boolean.class)' in schema
for name in ['NebulaHideHomeCamera','NebulaHideHomeCameraInfo','NebulaHideHomeCompose','NebulaHideHomeComposeInfo']:
 assert 'R.string.'+name in settings
 for language in ['values','values-ru']:
  doc=ET.parse(o/'src/main/res'/language/'strings_nebula_controls.xml')
  assert len(doc.findall("string[@name='"+name+"']"))==1
assert 'updateFloatingButtonVisibility(false);' in method(dialogs,'public void onResume()')
emoji=(n/'Components/EmojiView.java').read_text(encoding='utf-8')
assert 'if (glassDesign && app.nebulagram.ui.NebulaMenuStyle.enabled()) hideBottomTabContainerBackground();' in emoji
assert 'if (glassDesign && !app.nebulagram.ui.NebulaMenuStyle.enabled())' in emoji
child=method(header,'protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime)')
assert 'child == avatarImageView' not in child[:child.index('boolean drawn = super.drawChild')]
section=method((n/'Components/RecyclerListView.java').read_text(encoding='utf-8'),'public void drawBackgroundRect(Canvas canvas, RectF rect, float topRadius, float bottomRadius, float alpha)')
assert 'parent instanceof ChatAttachAlert.AttachAlertLayout' in section
assert 'alert.drawNebulaSection(this, canvas, rect, topRadius, bottomRadius, alpha)' in section
assert 'drawBackgroundRect(canvas, rect, topRadius, bottomRadius, alpha, resourcesProvider)' in section
surface=(o/'src/main/java/app/nebulagram/ui/NebulaSheetSurface.java').read_text(encoding='utf-8')
assert 'bucket.used = 0' in method(surface,'@Override public boolean onPreDraw()')
assert 'bucket.drawables.get(bucket.used++)' in surface
assert 'setSourceOffset(sectionPosition[0] - origin[0], sectionPosition[1] - origin[1])' in surface
assert 'sections.clear()' in surface
print('Settings, localized strings, native fallback, per-frame section slots and glass-strip wiring passed')

# Execute the actual per-card allocation/coordinate code, not just its call sites.
section_draw=method(surface,'public boolean drawSection(')
pre_draw=method(surface,'@Override public boolean onPreDraw()').replace('@Override ','')
detach=method(surface,'@Override public void onViewDetachedFromWindow(View view)').replace('@Override ','')
pool='''import java.util.*;
class SectionPoolCheck {
 static void check(boolean b){if(!b)throw new AssertionError();}
 static class RectF {float left,top,right,bottom;RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}boolean isEmpty(){return right<=left||bottom<=top;}}
 static class Canvas {boolean hardware=true;boolean isHardwareAccelerated(){return hardware;}}
 static class Observer {boolean isAlive(){return true;}void removeOnPreDrawListener(Object o){}}
 static class View {int x,y,draws;View root=this;boolean attached=true;
 boolean isAttachedToWindow(){return attached;}View getRootView(){return root;}int getWidth(){return 400;}int getHeight(){return 800;}
 void draw(Canvas c){draws++;}void getLocationOnScreen(int[] p){p[0]=x;p[1]=y;}Observer getViewTreeObserver(){return new Observer();}}
 static class AndroidUtilities {static int dp(int x){return x;}}
 static class NebulaGlass {static float refraction(){return .2f;}}
 static class NebulaMenuStyle {static Object provider(Object p){return p;}}
 static class BlurredBackgroundDrawable {int x,y,left,top,right,bottom,alpha;float r1,r2;
 BlurredBackgroundDrawable setColorProvider(Object p){return this;}void setThickness(int n){}void setIntensity(float n){}
 void setSourceOffset(int x,int y){this.x=x;this.y=y;}void setRadius(float a,float b,float c,float d){r1=a;r2=c;}
 void setBounds(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}void setAlpha(int a){alpha=a;}void draw(Canvas c){}}
 static class Factory {BlurredBackgroundDrawable create(){return new BlurredBackgroundDrawable();}}
 static class Source {boolean recording;boolean inRecording(){return recording;}Canvas beginRecording(int w,int h){recording=true;return new Canvas();}void endRecording(){recording=false;}}
 static class Host {View root=new View(),host=new View();Source source=new Source();boolean ready;Object provider;
 static class Sections {ArrayList<BlurredBackgroundDrawable> drawables=new ArrayList<>();int used;}
 WeakHashMap<View,Sections> sections=new WeakHashMap<>();Factory factory=new Factory();int[] origin=new int[2],position=new int[2],sectionPosition=new int[2];
 BlurredBackgroundDrawable[] materials={new BlurredBackgroundDrawable(),new BlurredBackgroundDrawable(),new BlurredBackgroundDrawable()};
 boolean isReady(){return ready;}
 SECTION_DRAW
 PRE_DRAW
 DETACH
 }
 public static void main(String[] args){
 Host h=new Host();h.root.x=10;h.root.y=20;h.host.x=30;h.host.y=40;
 View list=new View();list.x=50;list.y=90;Canvas c=new Canvas();RectF r=new RectF(12,18,300,180);
 check(!h.drawSection(list,c,r,16,8,1));check(h.onPreDraw());check(h.ready&&h.root.draws==1&&!h.source.recording);
 check(h.drawSection(list,c,r,16,8,.5f));Host.Sections b=h.sections.get(list);BlurredBackgroundDrawable first=b.drawables.get(0);
 check(first.x==40&&first.y==70&&first.alpha==128&&first.r1==16&&first.r2==8);
 r.top=250;r.bottom=400;check(h.drawSection(list,c,r,8,16,1));check(b.used==2&&b.drawables.get(1)!=first&&first.top==18);
 h.onPreDraw();check(b.used==0);h.drawSection(list,c,r,8,16,1);check(b.drawables.size()==2&&b.drawables.get(0)==first);
 c.hardware=false;check(!h.drawSection(list,c,r,8,8,1));c.hardware=true;
 h.host.root=h.root;h.onPreDraw();check(!h.ready&&h.root.draws==2); // No recursive same-window capture
 h.onViewDetachedFromWindow(h.host);check(h.sections.isEmpty()&&!h.ready);
 System.out.println("Section slots, frame reuse, original-window coordinates, fallback and detach passed");
 }
}'''
for k,v in [('SECTION_DRAW',section_draw),('PRE_DRAW',pre_draw),('DETACH',detach)]:pool=pool.replace(k,v)
p=w/'SectionPoolCheck.java';p.write_text(pool,encoding='utf-8')
subprocess.run(['javac','-encoding','UTF-8','-d',str(w),str(p)],check=True)
subprocess.run(['java','-cp',str(w),'SectionPoolCheck'],check=True)
