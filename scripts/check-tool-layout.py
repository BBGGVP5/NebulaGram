"""Execute production tool-grid and sheet viewport geometry without Android."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
grid = (ui / 'NebulaToolGrid.java').read_text(encoding='utf-8')
dialog = (ui / 'NebulaDialog.java').read_text(encoding='utf-8')

def method(source, signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

java = r'''
import java.util.*;
class ToolLayoutCheck {
 static float density=1;
 static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
 static int dp(float v){return (int)Math.ceil(v*density);}
 static class Config {float fontScale;}
 static class Metrics {int heightPixels=800;}
 static class Resources {Config config=new Config();Metrics metrics=new Metrics();Config getConfiguration(){return config;}Metrics getDisplayMetrics(){return metrics;}}
 static class MeasureSpec {
  static int EXACTLY=0x40000000,AT_MOST=0x80000000,UNSPECIFIED=0;
  static int getSize(int s){return s&0x3fffffff;}static int getMode(int s){return s&0xc0000000;}
  static int makeMeasureSpec(int s,int mode){return s|mode;}
 }
 static class LinearLayout {static class LayoutParams {int topMargin,bottomMargin;}}
 static class View {
  int width,height,natural=88,l,t,r,b;String tag;LinearLayout.LayoutParams params=new LinearLayout.LayoutParams();
  void measure(int w,int h){width=MeasureSpec.getSize(w);height=natural;}
  int getMeasuredHeight(){return height;}Object getTag(){return tag;}Object getLayoutParams(){return params;}
  void layout(int a,int c,int d,int e){l=a;t=c;r=d;b=e;}
 }
 static class Parent {
  static int LAYOUT_DIRECTION_RTL=1;boolean rtl;int width,height;Resources resources=new Resources();ArrayList<View> children=new ArrayList<>();
  int dp(float n){return ToolLayoutCheck.dp(n);}int getWidth(){return width;}
  Resources getResources(){return resources;}int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}
  int getLayoutDirection(){return rtl?1:0;}void setMeasuredDimension(int w,int h){width=w;height=h;}
 }
 static class Grid extends Parent {
  int columns,cellWidth;int[] rowHeights;
  MEASURE
  LAYOUT
 }
 static class ScrollParent extends Parent {
  int cap;
  protected void onMeasure(int w,int h){cap=MeasureSpec.getSize(h);}
 }
 static class SheetScroll extends ScrollParent {
  Parent root=new Parent();
  SCROLL
 }
 public static void main(String[] args){
  int cases=0;
  for(float d:new float[]{1,2,2.75f})for(float font:new float[]{1,1.3f,2})for(int width:new int[]{240,328,440})for(boolean rtl:new boolean[]{false,true})for(int count:new int[]{3,5,6}){
   density=d;Grid g=new Grid();g.rtl=rtl;g.resources.config.fontScale=font;
   for(int i=0;i<count;i++){View v=new View();v.natural=dp((i%2==0?88:102)*font);g.children.add(v);}
   g.onMeasure(MeasureSpec.makeMeasureSpec(dp(width),MeasureSpec.EXACTLY),0);g.onLayout(true,0,0,g.width,g.height);
   for(int i=0;i<count;i++){
    View v=g.children.get(i);check(v.l>=0&&v.r<=g.width&&v.t>=0&&v.b<=g.height,"tile inside grid");
    check(v.b-v.t>=v.natural,"wrapping labels fit tallest-row layout");
    for(int j=0;j<i;j++){View p=g.children.get(j);check(v.r<=p.l||p.r<=v.l||v.b<=p.t||p.b<=v.t,"tiles do not overlap");}
   }
   if(font==1&&width==328)check(g.columns==3,"normal phone displays three tools per row");
   if(font==2&&width==240)check(g.columns==1,"large font on narrow phone collapses grid");cases++;
  }
  for(int screen:new int[]{400,800})for(int footerHeight:new int[]{48,96,144}){
   SheetScroll s=new SheetScroll();s.resources.metrics.heightPixels=screen;
   View unrelated=new View();unrelated.natural=200;s.root.children.add(unrelated);
   View footer=new View();footer.tag="nebula-dialog-actions";footer.natural=footerHeight;footer.params.topMargin=12;s.root.children.add(footer);
   s.onMeasure(328,MeasureSpec.makeMeasureSpec(screen,MeasureSpec.AT_MOST));
   check(s.cap==Math.min(Math.round(screen*.65f),Math.max(0,screen-footerHeight-12)),"scroll reserves actual wrapped footer height");
  }
  System.out.println(cases+" tool grid density/font/RTL cases and wrapped sheet footer heights passed");
 }
}
'''.replace('  MEASURE\n', method(grid, 'protected void onMeasure(int widthSpec, int heightSpec)')).replace(
    '  LAYOUT\n', method(grid, 'protected void onLayout(boolean changed, int l, int t, int r, int b)')).replace(
    '  SCROLL\n', method(dialog, 'protected void onMeasure(int widthSpec, int heightSpec)'))
with tempfile.TemporaryDirectory(prefix='nebula-tool-layout-') as temp:
    path = Path(temp) / 'ToolLayoutCheck.java'
    path.write_text(java, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp, str(path)], check=True)
    subprocess.run(['java', '-cp', temp, 'ToolLayoutCheck'], check=True)

tools = (ui / 'NebulaMessageToolsFragment.java').read_text(encoding='utf-8')
for callback in ('request(false)', 'request(true)', 'transcribe()', 'speechRequested = true; speak()',
                 'new NebulaTaskEditorFragment', 'AndroidUtilities.addToClipboard(lastResult)',
                 'id==generation', 'client.cancel()', 'speech.shutdown()'):
    assert callback in tools, callback
assert 'new NebulaToolGrid(c)' in tools and 'preferences.setVisibility(View.GONE)' in tools
assert 'negativeClick.onClick(sheet, DialogInterface.BUTTON_NEGATIVE)' in dialog
assert 'setSingleLine(false); button.setEllipsize(null)' in dialog
print('Message action/cancellation wiring, collapsed settings and sheet close callback retained')
