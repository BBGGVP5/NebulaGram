"""Execute folder geometry/inset and avatar fade regressions from production methods.

Usage: python scripts/check-settings-runtime.py PATH_TO_PATCHED_ANDROID_TREE
No emulator: native rendering and gestures still require device verification.
"""
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
overlay = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
native = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui'


def method(source, signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


dialogs = (native / 'DialogsActivity.java').read_text(encoding='utf-8')
bar = (native / 'ActionBar/ActionBar.java').read_text(encoding='utf-8')
tabs = (native / 'Components/FilterTabsView.java').read_text(encoding='utf-8')
folders = (overlay / 'NebulaFolderTabs.java').read_text(encoding='utf-8')
style = (overlay / 'NebulaChatStyle.java').read_text(encoding='utf-8')
java = '''
class SettingsRuntimeCheck {
 static void check(boolean b, String message) {if(!b) throw new AssertionError(message);}
 static class AndroidUtilities {static float density;static int dp(int x){return (int)Math.ceil(x*density);}}
 static class View {static final int VISIBLE=0;int visibility;int getVisibility(){return visibility;}}
 static class NebulaFolderTabs {static boolean bottom;static final int GAP_DP=4;
  static int reserved(){return bottom?AndroidUtilities.dp(54):0;}
  GEOMETRY
 }
 static class Host {
  View filterTabsView;float visibility;
  float getFilterTabsVisibilityFactor(boolean includeSearch){return visibility;}
  RESERVED
 }
 ALPHA
 public static void main(String[] args) {
  int cases=0;
  for(float density:new float[]{1f,1.5f,2.75f,3f}) {
   AndroidUtilities.density=density;
   for(int height:new int[]{320,640,896})for(int system:new int[]{0,24,48})for(int main:new int[]{0,72}) {
    int h=AndroidUtilities.dp(height),tab=AndroidUtilities.dp(50),nav=AndroidUtilities.dp(system),bar=AndroidUtilities.dp(main);
    int top=NebulaFolderTabs.bottomTop(h,tab,nav,bar);
    check(top>=0 && top+tab<=h-nav-bar,"tabs clipped or overlap navigation");
    check(h-nav-bar-top-tab==AndroidUtilities.dp(4),"bottom gap differs across densities");cases++;
   }
   for(boolean bottom:new boolean[]{false,true})for(boolean exists:new boolean[]{false,true})
    for(int visible:new int[]{0,4,8})for(float factor:new float[]{0f,.25f,.5f,1f}) {
     NebulaFolderTabs.bottom=bottom;Host host=new Host();host.visibility=factor;
     if(exists){host.filterTabsView=new View();host.filterTabsView.visibility=visible;}
     int expected=bottom&&exists&&visible==0?Math.round(AndroidUtilities.dp(54)*factor):0;
     check(host.nebulaFolderTabsReserved()==expected,"hidden/top folders reserve bottom space");cases++;
    }
  }
  int previous=0;
  for(int frame=0;frame<=1000;frame++) {
   float progress=1-frame/1000f;
   int alpha=avatarBackdropAlpha(progress,0,1);
   check(alpha>=previous && alpha-previous<=1,"avatar circle pops on search exit");
   check(avatarBackdropAlpha(0,progress,1)==alpha,"action-mode fade mismatch");
   check(avatarBackdropAlpha(progress,0,0)==0,"invisible avatar retains circle");
   check(avatarBackdropAlpha(progress,0,.5f)<=128,"avatar alpha ignored");previous=alpha;
  }
  check(previous==255 && avatarBackdropAlpha(1,0,1)==0,"fade endpoints");
  check(avatarBackdropAlpha(-1,-1,2)==255 && avatarBackdropAlpha(2,0,1)==0,"alpha clamp");
  System.out.println(cases+" folder geometry/inset cases; 1001 avatar transition frames passed");
 }
}
'''.replace('GEOMETRY', method(folders, 'public static int bottomTop(')).replace(
    'RESERVED', method(dialogs, 'private int nebulaFolderTabsReserved()').replace('app.nebulagram.ui.', '')
).replace('ALPHA', method(style, 'public static int avatarBackdropAlpha('))
work = root / 'build/settings-runtime-check'
work.mkdir(parents=True, exist_ok=True)
source = work / 'SettingsRuntimeCheck.java'
source.write_text(java, encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', str(source)], check=True)
subprocess.run(['java', '-cp', str(work), 'SettingsRuntimeCheck'], check=True)

# Record the actual renderer clip: it must contain the full padded child height,
# including the counter and selection stroke, at fractional display densities.
clip_java = '''
class FolderClipCheck {
 static float density;
 static int dp(int value) {return (int)Math.ceil(value*density);}
 static class Path {
  enum Direction {CW}
  float top,bottom;
  void rewind() {}
  void addRoundRect(float l,float t,float r,float b,float rx,float ry,Direction d) {top=t;bottom=b;}
 }
 static class Parent {protected void onSizeChanged(int w,int h,int oldw,int oldh) {}}
 static class Tabs extends Parent {
  Path clipPath=new Path();
  CLIP_METHOD
 }
 public static void main(String[] args) {
  int cases=0;
  for(float d:new float[]{1f,1.5f,2.625f,2.75f,3f,4f}) {
   density=d;
   for(int width:new int[]{240,320,400,600}) {
    Tabs tabs=new Tabs();int h=dp(50),padding=dp(7);
    tabs.onSizeChanged(dp(width),h,0,0);
    if(tabs.clipPath.top>padding || tabs.clipPath.bottom<h-padding)
     throw new AssertionError("Folder renderer clips padded tab content");
    cases++;
   }
  }
  System.out.println(cases+" native folder clip cases passed");
 }
}
'''.replace('CLIP_METHOD', method(tabs, 'protected void onSizeChanged('))
clip_source = work / 'FolderClipCheck.java'
clip_source.write_text(clip_java, encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', str(clip_source)], check=True)
subprocess.run(['java', '-cp', str(work), 'FolderClipCheck'], check=True)

# Guard the callers, since helper math alone cannot catch the original layout bug.
layout = method(dialogs, 'protected void onLayout(boolean changed, int l, int t, int r, int b)')
assert 'NebulaFolderTabs.bottomTop(H, height, navigationBarHeight,' in layout
assert 'child == topPanelLayout || child == topBubblesFadeView || child == filterTabsView' not in layout
assert 'NebulaFolderTabs.bottom() ? 0f : getFilterTabsVisibilityFactor(false)' in dialogs
assert 'NebulaChatStyle.avatarBackdropAlpha(' in bar
assert 'nebulaChatAvatarContainer != null && getActionModeFactor() == 0f && searchFactor == 0f' not in bar
preview = (overlay / 'NebulaFoldersPreview.java').read_text(encoding='utf-8')
assert 'onInterceptTouchEvent' not in preview
assert 'canPerformActions() { return true; }' in preview
assert 'NebulaFolderTabs.bottom()' in preview and 'invalidateViews()' in preview
section = (overlay / 'NebulaSectionFragment.java').read_text(encoding='utf-8')
assert 'Сервер значков' not in section and 'Админ-токен' not in section
badges = (overlay / 'NebulaBadges.java').read_text(encoding='utf-8')
assert 'Emoji.replaceEmoji' not in badges and 'new NebulaBadgeSpan(drawable)' in badges
for kind in ['supporter', 'dev', 'tester', 'star', 'heart']:
    path = root / f'platform/android/overlay/TMessagesProj/src/main/res/drawable/nebula_badge_{kind}.xml'
    vector = ET.parse(path).getroot()
    assert vector.tag == 'vector' and len(vector.findall('path')) >= 2
print('Native integration, interactive preview, hidden service controls and five vector badges passed')

# Compile the actual span against drawing recorders: font scaling and alpha must
# not alter the line height or leak canvas transforms into the surrounding name.
stubs = {
    'android/graphics/Paint.java': '''package android.graphics; public class Paint {
      public static class FontMetricsInt {public int top,ascent,descent,bottom;}
      public FontMetricsInt metrics=new FontMetricsInt();public int alpha;
      public FontMetricsInt getFontMetricsInt(){return metrics;}public int getAlpha(){return alpha;}}
    ''',
    'android/graphics/Canvas.java': '''package android.graphics; public class Canvas {
      public float x,y;public int restored;public int save(){return 7;}
      public void translate(float x,float y){this.x=x;this.y=y;}
      public void restoreToCount(int n){restored=n;}}
    ''',
    'android/graphics/drawable/Drawable.java': '''package android.graphics.drawable;
      public class Drawable {public int width,height,alpha,draws;
      public Drawable mutate(){return this;}public void setBounds(int l,int t,int r,int b){width=r-l;height=b-t;}
      public void setAlpha(int a){alpha=a;}public void draw(android.graphics.Canvas c){draws++;}}
    ''',
    'android/text/style/ReplacementSpan.java': '''package android.text.style;
      import android.graphics.*;public abstract class ReplacementSpan {
      public abstract int getSize(Paint p,CharSequence s,int start,int end,Paint.FontMetricsInt metrics);
      public abstract void draw(Canvas c,CharSequence s,int start,int end,float x,int top,int y,int bottom,Paint p);}
    ''',
    'BadgeSpanCheck.java': '''import android.graphics.*;import android.graphics.drawable.Drawable;
      import app.nebulagram.ui.NebulaBadgeSpan;
      class BadgeSpanCheck {
       static void check(boolean b){if(!b)throw new AssertionError("Badge alignment/alpha regression");}
       public static void main(String[] args){int cases=0;
        for(int size:new int[]{12,16,24,36,60})for(int alpha:new int[]{0,64,128,255}){
         Paint p=new Paint();p.metrics.ascent=-size;p.metrics.descent=size/4;
         p.metrics.top=-size-3;p.metrics.bottom=size/4+2;p.alpha=alpha;
         Drawable d=new Drawable();NebulaBadgeSpan span=new NebulaBadgeSpan(d);
         Paint.FontMetricsInt m=new Paint.FontMetricsInt();Canvas c=new Canvas();
         int advance=span.getSize(p,"x",0,1,m);
         check(m.top==p.metrics.top&&m.bottom==p.metrics.bottom&&m.ascent==p.metrics.ascent&&m.descent==p.metrics.descent);
         check(advance==size+size/4&&span.getSize(p,"x",0,1,null)==advance);
         span.draw(c,"x",0,1,43,0,100,200,p);
         check(d.width==advance&&d.height==advance&&d.alpha==alpha&&d.draws==1);
         check(c.x==43&&c.y==100-size&&c.restored==7);cases++;
        }System.out.println(cases+" badge font-size/opacity cases passed");
       }
      }
    ''',
}
sources = []
for name, body in stubs.items():
    path = work / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(body, encoding='utf-8')
    sources.append(str(path))
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), *sources,
                str(overlay / 'NebulaBadgeSpan.java')], check=True)
subprocess.run(['java', '-cp', str(work), 'BadgeSpanCheck'], check=True)
