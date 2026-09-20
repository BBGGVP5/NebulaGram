"""Exercise the actual bottom-folder capture block, including a hidden main bar."""
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
tree = Path(sys.argv[1])
dialogs = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/DialogsActivity.java').read_text(encoding='utf-8')
start = dialogs.index('        // Reuse the second capture region for folders')
end = dialogs.index('        scrollableViewNoiseSuppressor.setupRenderNodes', start)
block = dialogs[start:end].replace('app.nebulagram.ui.', '')
work = ROOT / 'build/folder-glass-check'
work.mkdir(parents=True, exist_ok=True)
source = work / 'FolderGlassCheck.java'
source.write_text('''class FolderGlassCheck {
 static class View {
  static final int VISIBLE=0; int visibility=0,width=360,height=50;float y=660,alpha=1;
  int getVisibility(){return visibility;}int getWidth(){return width;}int getHeight(){return height;}
  int getMeasuredWidth(){return width;}int getMeasuredHeight(){return height;}
  float getY(){return y;}float getAlpha(){return alpha;}
 }
 static class Rect {float top,bottom;void set(float l,float t,float r,float b){top=t;bottom=b;}
  void union(float l,float t,float r,float b){top=Math.min(top,t);bottom=Math.max(bottom,b);}}
 static class NebulaFolderTabs {static boolean enabled;static boolean bottom(){return enabled;}}
 View fragmentView=new View(),filterTabsView; Rect iBlur3PositionMainTabs=new Rect(); int additionalList;
 boolean capture(boolean hasBottomBlur) {
''' + block + '''
  return hasBottomBlur;
 }
 static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
 public static void main(String[] args) {
  int cases=0;
  for(float density:new float[]{1,1.5f,2.75f,3})for(boolean main:new boolean[]{false,true})
  for(boolean bottom:new boolean[]{false,true})for(int visibility:new int[]{0,4,8})
  for(float alpha:new float[]{0,.5f,1})for(float translation:new float[]{-70,0,25}) {
   FolderGlassCheck c=new FolderGlassCheck(); c.fragmentView.width=(int)(360*density);c.fragmentView.height=(int)(800*density);
   c.additionalList=(int)(48*density);c.filterTabsView=new View();c.filterTabsView.width=c.fragmentView.width;
   c.filterTabsView.height=(int)(50*density);c.filterTabsView.y=(main?660:734)*density+translation*density;
   c.filterTabsView.visibility=visibility;c.filterTabsView.alpha=alpha;NebulaFolderTabs.enabled=bottom;
   float oldTop=730*density,oldBottom=780*density;c.iBlur3PositionMainTabs.set(0,oldTop,c.fragmentView.width,oldBottom);
   boolean expected=bottom&&visibility==0&&alpha>0;boolean actual=c.capture(main);
   check(actual==(main||expected),"missing bottom capture without main navigation");
   if(expected){
    float top=Math.max(0,c.filterTabsView.y-c.additionalList);
    float end=Math.min(c.fragmentView.height,c.filterTabsView.y+c.filterTabsView.height+c.additionalList);
    check(c.iBlur3PositionMainTabs.top==(main?Math.min(oldTop,top):top),"translated top/blur margin not captured");
    check(c.iBlur3PositionMainTabs.bottom==(main?Math.max(oldBottom,end):end),"bottom/main bar lost");
   }else{check(c.iBlur3PositionMainTabs.top==oldTop&&c.iBlur3PositionMainTabs.bottom==oldBottom,"hidden/top folders changed capture");}
   cases++;
  }
  FolderGlassCheck c=new FolderGlassCheck();NebulaFolderTabs.enabled=true;
  check(!c.capture(false),"null folder view reserves a blur region");
  c.filterTabsView=new View();c.filterTabsView.width=0;
  check(!c.capture(false),"unmeasured folder view reserves a blur region");
  System.out.println(cases+" bottom-folder blur capture cases passed; null/unmeasured views skipped");
 }
}
''', encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', str(source)], check=True)
subprocess.run(['java', '-cp', str(work), 'FolderGlassCheck'], check=True)

tabs = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/Components/FilterTabsView.java').read_text(encoding='utf-8')
assert 'NebulaFolderGlass.provider(resourcesProvider)' in tabs
assert 'BlurredBackgroundProviderImpl.topPanel(resourcesProvider)' in tabs
assert '!app.nebulagram.ui.NebulaFolderTabs.bottom() && filterTabsView != null' in dialogs
preview = (ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaFoldersPreview.java').read_text(encoding='utf-8')
assert 'sampleChats.draw(capture)' in preview and 'finally {' in preview
assert 'glass.setSourceOffset(tabs.getX(), tabs.getY())' in preview
print('Live sample capture and native top/bottom material integration passed (device visual QA still required)')
