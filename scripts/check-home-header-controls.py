"""Exercise the production divider measurement and native leading-control layout."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
bar = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBar.java').read_text(encoding='utf-8')

def method(source, signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

stubs = {
 'android/content/Context.java': 'package android.content; public class Context {}',
 'android/graphics/Paint.java': 'package android.graphics; public class Paint {public static final int ANTI_ALIAS_FLAG=1; public Paint(int n){} public void setColor(int c){} public void setAlpha(int a){}}',
 'android/graphics/Canvas.java': 'package android.graphics; public class Canvas {public void drawRect(float a,float b,float c,float d,Paint p){}}',
 'android/view/View.java': '''package android.view; import android.content.Context; import android.graphics.Canvas;
 public class View {
 public static final int IMPORTANT_FOR_ACCESSIBILITY_NO=2, GONE=8; int width,height; public int left,top,right,bottom,visibility;
 public View(Context c){} public void setImportantForAccessibility(int v){} public void setTag(int k,Object v){}
 public int getWidth(){return width;} public int getHeight(){return height;} public int getMeasuredWidth(){return width;} public int getMeasuredHeight(){return height;}
 public int getVisibility(){return visibility;} public void layout(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 protected void onDraw(Canvas c){} protected void setMeasuredDimension(int w,int h){width=w;height=h;}
 public void measure(int w,int h){onMeasure(w,h);} protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),MeasureSpec.getSize(h));}
 public static class MeasureSpec {public static final int EXACTLY=0x40000000, AT_MOST=0x80000000; public static int getMode(int s){return s&0xc0000000;} public static int getSize(int s){return s&0x3fffffff;} public static int makeMeasureSpec(int s,int m){return s|m;}}
 }''',
 'org/telegram/messenger/AndroidUtilities.java': 'package org.telegram.messenger; public class AndroidUtilities {public static float dpf2(float v){return v;}}',
 'org/telegram/messenger/R.java': 'package org.telegram.messenger; public class R {public static class id {public static int fit_width_tag=1;}}',
 'org/telegram/ui/ActionBar/Theme.java': 'package org.telegram.ui.ActionBar; public class Theme {public static class ResourcesProvider{} public static int key_actionBarDefaultSubmenuItem=1;public static int getColor(int key,ResourcesProvider r){return 0;}}',
 'org/telegram/ui/Components/ItemOptions.java': 'package org.telegram.ui.Components; public class ItemOptions {public void addView(android.view.View v,Object lp){}}',
 'org/telegram/ui/Components/LayoutHelper.java': 'package org.telegram.ui.Components; public class LayoutHelper {public static int MATCH_PARENT=-1; public static Object createLinear(int w,int h){return null;}}',
 'app/nebulagram/ui/NebulaMenuStyle.java': 'package app.nebulagram.ui; public class NebulaMenuStyle {public static int foreground(android.view.View v,int c,org.telegram.ui.ActionBar.Theme.ResourcesProvider r){return c;}}',
}
layout_method = method(bar, 'private void layoutNebulaHomeLeadingView(') if 'private void layoutNebulaHomeLeadingView(' in bar else ''
stubs['Check.java'] = '''import android.view.View; import app.nebulagram.ui.NebulaMenuDivider;
class Check {
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static int spec(int size,int mode){return View.MeasureSpec.makeMeasureSpec(size,mode);}
 static class Bar {static final int GONE=View.GONE; float density; int toolbar; View nebulaHomeLeadingView; int dp(float n){return Math.round(n*density);} int getCurrentActionBarHeight(){return toolbar;}
''' + layout_method + '''
 }
 public static void main(String[] args){
  NebulaMenuDivider divider=new NebulaMenuDivider(new android.content.Context(),null);
  for(int screen:new int[]{320,393,1008,1440})for(int row:new int[]{180,240,280}) {
   divider.measure(spec(screen,View.MeasureSpec.AT_MOST),spec(9,View.MeasureSpec.EXACTLY));
   check(divider.getMeasuredWidth()==0,"Divider expands initial popup width to screen: "+divider.getMeasuredWidth());
   int naturalWidth=Math.max(row,divider.getMeasuredWidth());
   divider.measure(spec(naturalWidth,View.MeasureSpec.EXACTLY),spec(9,View.MeasureSpec.EXACTLY));
   check(divider.getMeasuredWidth()==row,"Divider does not fill action width on second pass");
   check(screen-naturalWidth==screen-row,"Divider moved the popup away from its right anchor");
  }
  System.out.println("12 popup first/second measurement passes preserve natural width and right anchor");
''' + ('''
  int cases=0;
  for(float density:new float[]{1,2,2.625f,3.5f})for(int toolbar:new int[]{48,56,64}) {
   Bar bar=new Bar();bar.density=density;bar.toolbar=bar.dp(toolbar);
   View edit=bar.nebulaHomeLeadingView=new View(new android.content.Context());
   edit.measure(spec(bar.dp(68),View.MeasureSpec.EXACTLY),spec(bar.dp(44),View.MeasureSpec.EXACTLY));
   // The same control is created before insets arrive and then relaid out.
   for(int status:new int[]{0,24,48,0,36}){
    int inset=bar.dp(status);bar.layoutNebulaHomeLeadingView(inset);
    check(edit.top>=inset && edit.bottom<=inset+bar.toolbar,"Leading action overlaps status bar or leaves toolbar");
    check(Math.abs((edit.top+edit.bottom)/2f-(inset+bar.toolbar/2f))<=.5f,"Leading action not aligned with native menu");
    check(edit.left==bar.dp(8),"Leading action lost horizontal inset");cases++;
   }
  }
  System.out.println(cases+" live status-inset, density and toolbar-height leading-control layouts passed");
''' if layout_method else '') + '}}'

with tempfile.TemporaryDirectory(prefix='nebula-header-controls-') as temp:
    work = Path(temp)
    for name, source in stubs.items():
        path = work / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), *[str(work / name) for name in stubs], str(ui / 'NebulaMenuDivider.java')], check=True)
    subprocess.run(['java', '-cp', str(work), 'Check'], check=True)

assert layout_method, 'Home action still uses a creation-time status inset'
layout = method(bar, 'protected void onLayout(')
assert 'layoutNebulaHomeLeadingView(additionalTop)' in layout
assert 'child == nebulaHomeLeadingView' in layout, 'Generic child layout overwrites native leading placement'
dialogs = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java').read_text(encoding='utf-8')
assert 'actionBar.setNebulaHomeLeadingView(nebulaHomeEditButton)' in dialogs
assert 'editLayout.topMargin' not in dialogs, 'Creation-time status-bar policy must not position the action'
home_menu = method(dialogs, 'private void showItemOptions()')
assert 'io.addGap()' not in home_menu, 'Main menu still mixes thick and hairline separators'
assert 'if (nebulaHomeEditButton == null) io.setTranslationY(-dp(64));' in home_menu
appearance = (ui / 'NebulaAppearance.java').read_text(encoding='utf-8')
assert 'getBoolean("home_chats_title", false)' in appearance
assert 'putBoolean("home_chats_title", value)' in appearance
assert 'NebulaAppearance.homeChatsTitle(), NebulaAppearance::setHomeChatsTitle' in (ui / 'NebulaExtras.java').read_text(encoding='utf-8')
assert 'isNebulaHomeTabsGlass()' in (ui / 'NebulaLinkShortcut.java').read_text(encoding='utf-8')
print('Native onLayout owns and preserves leading-action geometry')
