"""Execute the folder preview's actual geometry and blur capture at device densities."""
from pathlib import Path
import re
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
source = (ui / 'NebulaFoldersPreview.java').read_text(encoding='utf-8')

def method(signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end].replace('@Override ', '').replace(
        'android.os.Build.VERSION.SDK_INT', '31')

constants = re.search(r'private static final int SAMPLE_HEIGHT_DP.*?;', source).group()
java = r'''
import java.util.*;
class PreviewCheck {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static class AndroidUtilities {
  static float density;
  static int dp(float v){return (int)Math.ceil(v*density);}
  static float dpf2(float v){return v*density;}
 }
 static class Gravity {static int TOP=1,BOTTOM=2;}
 static class MeasureSpec {static int EXACTLY=1;static int makeMeasureSpec(int size,int mode){return size;}}
 static class FrameLayout {
  static class LayoutParams {int gravity=Gravity.TOP,topMargin,bottomMargin,width,height;}
 }
 static class Paint {
  void setColor(int x){}void setAlpha(int x){}
 }
 static class Canvas {
  float x,y;ArrayList<float[]> bounds=new ArrayList<>();ArrayList<float[]> stack=new ArrayList<>();
  void save(){stack.add(new float[]{x,y});}
  void restore(){float[] p=stack.remove(stack.size()-1);x=p[0];y=p[1];}
  void translate(float dx,float dy){x+=dx;y+=dy;}
  void drawColor(int c){}
  void drawCircle(float cx,float cy,float radius,Paint p){bounds.add(new float[]{x+cx-radius,y+cy-radius,x+cx+radius,y+cy+radius});}
  void drawRoundRect(float l,float t,float r,float b,float rx,float ry,Paint p){bounds.add(new float[]{x+l,y+t,x+r,y+b});}
 }
 static class NebulaTheme {
  static NebulaTheme of(Object c){return new NebulaTheme();}
  int onSurfaceVariant(){return 0;}int surfaceContainer(){return 0;}
 }
 static class NebulaText {static String text(String ru,String en){return en;}}
 static class LocaleController {static String getString(int id){return "All";}}
 static class R {static class string {static int FilterAllChats=1;}}
 static class NebulaAppearance {
  static int folderStyle(){return 0;}static boolean hideAllChats(){return false;}
  static boolean hideTabCounters(){return false;}
 }
 static class NebulaFolderTabs {static int HEIGHT_DP=50;static boolean bottom;static boolean bottom(){return bottom;}}
 static class NebulaGlass {static int blur(){return 12;}}
 static class Child {
  FrameLayout.LayoutParams params=new FrameLayout.LayoutParams();float x,y;int width;
  Object getLayoutParams(){return params;}void setLayoutParams(FrameLayout.LayoutParams p){params=p;}
  float getX(){return x;}float getY(){return y;}int getWidth(){return width;}Object getContext(){return null;}
  void invalidate(){}void invalidateViews(){}
 }
 static class Sample extends Child {
  Paint paint=new Paint();
  DRAW
  void draw(Canvas c){onDraw(c);}
 }
 static class Tabs extends Child {
  void updateColors(){}void removeTabs(){}void addTab(int a,int b,String s,boolean c,boolean d,boolean e){}
  void finishAddingTabs(boolean b){}void setNebulaBottomPanel(boolean b){}
  Child getTabsContainer(){return this;}
 }
 static class Color {void setColor(int c){}}
 static class Captured {
  Canvas canvas;int captures;
  void setBlur(float b){}Canvas beginRecording(int w,int h){captures++;return canvas=new Canvas();}
  void endRecording(){}void invalidateDisplayListForDrawables(){}
 }
 static class Glass {float x,y;void setSourceOffset(float a,float b){x=a;y=b;}}
 static class Parent {
  int width,height;Sample sampleChats=new Sample();Tabs tabs=new Tabs();
  Object getContext(){return null;}int getWidth(){return width;}int getHeight(){return height;}
  void invalidate(){}protected void onMeasure(int w,int h){width=w;height=h;sampleChats.width=w;tabs.width=w;}
  protected void onLayout(boolean changed,int l,int t,int r,int b){
   sampleChats.y=sampleChats.params.topMargin;
   tabs.y=tabs.params.gravity==Gravity.BOTTOM?height-tabs.params.bottomMargin-tabs.params.height:tabs.params.topMargin;
  }
  protected void dispatchDraw(Canvas c){}
 }
 static class Preview extends Parent {
  CONSTANTS
  Color glassSource=new Color();Captured capturedChats=new Captured();Glass glass=new Glass();
  int style=-1;boolean hidden,counters,captureDirty=true;
  Preview(){
   sampleChats.params.height=AndroidUtilities.dp(SAMPLE_HEIGHT_DP);
   tabs.params.height=AndroidUtilities.dp(NebulaFolderTabs.HEIGHT_DP);
   tabs.params.topMargin=tabs.params.bottomMargin=AndroidUtilities.dp(8);
  }
  REFRESH
  MEASURE
  LAYOUT
  DISPATCH
 }
 public static void main(String[] args){
  int cases=0;
  for(float density:new float[]{1,1.5f,2,2.75f,3.5f})for(int width:new int[]{280,360,480}){
   AndroidUtilities.density=density;Preview p=new Preview();
   for(boolean bottom:new boolean[]{false,true,false,true}){
    NebulaFolderTabs.bottom=bottom;p.refresh();p.onMeasure(AndroidUtilities.dp(width),0);p.onLayout(true,0,0,p.width,p.height);
    float sampleTop=p.sampleChats.y,sampleBottom=sampleTop+p.sampleChats.params.height;
    float tabsTop=p.tabs.y,tabsBottom=tabsTop+p.tabs.params.height;
    check(sampleTop>=0&&sampleBottom<=p.height&&tabsTop>=0&&tabsBottom<=p.height,"children remain inside preview");
    check(bottom?tabsTop-sampleBottom>=AndroidUtilities.dp(8)-1:sampleTop-tabsBottom>=AndroidUtilities.dp(8)-1,"tabs and sample have a separate gap");
    int before=p.capturedChats.captures;p.dispatchDraw(new Canvas());
    check(p.capturedChats.captures==before+1&&!p.captureDirty,"one new capture after layout/toggle");
    Canvas capture=p.capturedChats.canvas;check(capture.bounds.size()==9,"all three sample rows captured");
    for(float[] rect:capture.bounds)check(rect[1]>=sampleTop&&rect[3]<=sampleBottom,"actual sample drawing fits its own rectangle");
    check(Math.abs(capture.bounds.get(0)[1]-(sampleTop+AndroidUtilities.dp(24)-AndroidUtilities.dp(16)))<.01f,"capture uses actual sample offset");
    check(p.glass.x==p.tabs.x&&p.glass.y==p.tabs.y,"glass uses actual tabs offset");
    p.dispatchDraw(new Canvas());check(p.capturedChats.captures==before+1,"unchanged draw reuses capture");
    cases++;
   }
  }
  System.out.println(cases+" navigation preview bounds, placement toggles and blur capture paths passed");
 }
}
'''
for key, signature in {
    'DRAW': 'protected void onDraw(Canvas canvas)',
    'REFRESH': 'public void refresh()',
    'MEASURE': 'protected void onMeasure(int w, int h)',
    'LAYOUT': 'protected void onLayout(boolean changed, int l, int t, int r, int b)',
    'DISPATCH': 'protected void dispatchDraw(Canvas canvas)',
}.items():
    java = java.replace(key, method(signature))
java = java.replace('CONSTANTS', constants)
assert 'previewParams.topMargin = AndroidUtilities.dp(10)' in (ui / 'NebulaSectionFragment.java').read_text(encoding='utf-8')
with tempfile.TemporaryDirectory(prefix='nebula-navigation-preview-') as temp:
    path = Path(temp) / 'PreviewCheck.java'
    path.write_text(java, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp, str(path)], check=True)
    subprocess.run(['java', '-cp', temp, 'PreviewCheck'], check=True)
