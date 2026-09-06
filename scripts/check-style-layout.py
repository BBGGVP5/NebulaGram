"""Exercise the real search-slot geometry and alternate switch drawing bounds."""
from pathlib import Path
import subprocess, sys

root = Path(__file__).resolve().parent.parent
tree = Path(sys.argv[1])
source = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java').read_text(encoding='utf-8')
switch = (root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaSwitch.java').read_text(encoding='utf-8')

def method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

methods = '\n'.join(method(source, signature) for signature in [
    'public int getActionBarFullHeight()', 'private int getMaxScrollYOffsetWithoutSearch()',
    'private int getMaxScrollYOffset()', 'private int nebulaSearchSlotHeight()'])
bar = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java').read_text(encoding='utf-8')
center = method(bar, 'private boolean nebulaCenterTitle()')
expanded = method(source, 'public boolean nebulaIsHeaderExpanded()')
draw = method(switch, 'private void drawAlternative(Canvas canvas, int style)')
java = '''class StyleLayoutCheck {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static float density=1;
 static int dp(float n){return (int)Math.ceil(n*density);}
 static class DialogStoriesCell {static final int HEIGHT_IN_DP=88;}
 static class Bar {int getHeight(){return dp(80);}}
 static class Sliding {float openedProgress;boolean hasFragment(){return true;}}
 static class Home {
  static final int SEARCH_FIELD_HEIGHT=48;
  Bar actionBar=new Bar();Sliding rightSlidingDialogContainer=new Sliding();
  boolean hasStories,searching;float scrollYOffset,progressToActionMode,searchAnimationProgress,storiesOverscroll;
''' + methods + expanded + '''
 }
 static class TitleBar {
  Object parentFragment;boolean isSearchFieldVisible;float mode;
  float getActionModeFactor(){return mode;}
''' + center + '''
 }
 static class AndroidUtilities {static float dpf2(float n){return n*density;}}
 static class NebulaTheme {int onSurfaceVariant(){return 0;}int primary(){return 0;}static int stateLayer(int c,float a){return 0;}}
 static class Paint {static class Style{static final int FILL=0;}void setStyle(int i){}void setColor(int c){}}
 static class RectF {float l,t,r,b;void set(float l,float t,float r,float b){this.l=l;this.t=t;this.r=r;this.b=b;}}
 static class Canvas {
  int shapes;
  void save(){}void restore(){}void scale(float x,float y){}
  void bounds(float l,float t,float r,float b){shapes++;check(l>=0&&t>=0&&r<=52&&b<=32,"Switch shape clipped: "+l+","+t+","+r+","+b);}
  void drawCircle(float x,float y,float r,Paint p){bounds(x-r,y-r,x+r,y+r);}
  void drawRoundRect(RectF r,float x,float y,Paint p){bounds(r.l,r.t,r.r,r.b);}
 }
 static class Toggle {
  static final int LAYOUT_DIRECTION_RTL=1;int direction;
  int getLayoutDirection(){return direction;}
  float progress;Paint paint=new Paint();RectF track=new RectF();NebulaTheme theme=new NebulaTheme();
  int blend(int a,int b,float p){return 0;}
''' + draw + '''
 }
 public static void main(String[] args){
  int cases=0;
  for(float d:new float[]{1,2,2.75f,3.5f})for(boolean stories:new boolean[]{false,true})
  for(float p:new float[]{0,.5f,1})for(float slide:new float[]{0,.5f,1}){
   density=d;Home h=new Home();h.hasStories=stories;h.searchAnimationProgress=p;h.rightSlidingDialogContainer.openedProgress=slide;
   app.nebulagram.ui.NebulaAppearance.hidden=false;int shown=h.getActionBarFullHeight();int maxShown=h.getMaxScrollYOffset();
   app.nebulagram.ui.NebulaAppearance.hidden=true;int hidden=h.getActionBarFullHeight();
   check(Math.abs((shown-hidden)-dp(48)*(1-p)*(1-slide))<=1,"Hidden search keeps header space");
   check(maxShown-h.getMaxScrollYOffset()==dp(48),"Search slot still scrolls when hidden");
   check(h.getMaxScrollYOffset()==h.getMaxScrollYOffsetWithoutSearch(),"Hidden search affects story scroll range");cases++;
  }
  Home home=new Home();
  home.scrollYOffset=0;check(home.nebulaIsHeaderExpanded(),"Expanded home not centered");
  home.scrollYOffset=-dp(20);check(!home.nebulaIsHeaderExpanded(),"Collapsed home centered");
  home.scrollYOffset=0;home.searching=true;check(!home.nebulaIsHeaderExpanded(),"Search home centered");
  TitleBar bar=new TitleBar();
  app.nebulagram.ui.NebulaAppearance.center=true;
  check(bar.nebulaCenterTitle(),"Settings title not centered");
  org.telegram.ui.DialogsActivity dialogs=new org.telegram.ui.DialogsActivity();bar.parentFragment=dialogs;
  check(!bar.nebulaCenterTitle(),"Collapsed home title centered");dialogs.expanded=true;check(bar.nebulaCenterTitle(),"Expanded home title not centered");
  bar.parentFragment=new org.telegram.ui.ChatActivity();check(!bar.nebulaCenterTitle(),"Custom chat header overridden");
  bar.parentFragment=null;bar.isSearchFieldVisible=true;check(!bar.nebulaCenterTitle(),"Search title overridden");
  bar.isSearchFieldVisible=false;bar.mode=1;check(!bar.nebulaCenterTitle(),"Selection title overridden");
  bar.mode=0;app.nebulagram.ui.NebulaAppearance.center=false;check(!bar.nebulaCenterTitle(),"Disabled centering still active");
  int shapes=0;
  for(int style=1;style<=3;style++)for(int direction=0;direction<=1;direction++)for(int step=0;step<=100;step++){
   Toggle t=new Toggle();t.direction=direction;t.progress=step/100f;Canvas c=new Canvas();t.drawAlternative(c,style);shapes+=c.shapes;
  }
  System.out.println(cases+" search/header cases, 10 title states and "+shapes+" switch drawing bounds passed (including RTL and intermediate frames)");
 }
}'''
work = root / 'build/style-layout-check'
work.mkdir(parents=True, exist_ok=True)
stub = work / 'app/nebulagram/ui/NebulaAppearance.java'
stub.parent.mkdir(parents=True, exist_ok=True)
stub.write_text('package app.nebulagram.ui; public class NebulaAppearance {public static boolean hidden,center;public static boolean centerHome(){return center;}public static boolean hideSearchField(){return hidden;}}', encoding='utf-8')
extra=[]
for name,body in [('DialogsActivity','public boolean expanded;public boolean nebulaIsHeaderExpanded(){return expanded;}'),('ChatActivity','')]:
    file=work / ('org/telegram/ui/'+name+'.java');file.parent.mkdir(parents=True,exist_ok=True)
    file.write_text('package org.telegram.ui; public class '+name+' {'+body+'}',encoding='utf-8');extra.append(str(file))
test = work / 'StyleLayoutCheck.java'
test.write_text(java, encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), str(stub), str(test), *extra], check=True)
subprocess.run(['java', '-cp', str(work), 'StyleLayoutCheck'], check=True)
