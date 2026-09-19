"""Run actual style/header policy methods without replacing native folder routing."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
native = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui'
folders = (ui / 'NebulaFolderTabs.java').read_text(encoding='utf-8')
tabs = (native / 'Components/FilterTabsView.java').read_text(encoding='utf-8')
dialogs = (native / 'DialogsActivity.java').read_text(encoding='utf-8')

def method(source, signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

source = '''
class FolderStyleCheck {
 static void check(boolean ok) {if(!ok) throw new AssertionError();}
 static class Prefs {
  int value; int getInt(String key,int fallback){return value;}
  Prefs edit(){return this;} Prefs putInt(String key,int value){this.value=value;return this;} void apply(){}
 }
 static class NebulaFolderTabs {
  static final int GLASS=0,ORDINARY=1,MINIMAL=2;
  static Prefs prefs=new Prefs(); static Prefs preferences(){return prefs;}
  static int changes; static void notifyChanged(){changes++;}
  GET_STYLE
  SET_STYLE
  FLAT_HEADER
 }
 static class NebulaAppearance {static boolean liquid;static boolean liquidAnimations(){return liquid;}}
 static class Host {
  boolean nebulaBottomPanel;int nebulaPanelStyle,applied;
  Object background=new Object();Object getBackground(){return background;}
  static class Lens {void reset(){}} Lens nebulaLens=new Lens();
  void applyNebulaPanelStyle(){applied++;background=nebulaPanelStyle==2?null:new Object();}
  APPLY
  LIQUID
 }
 public static void main(String[] args) {
  int cases=0;
  Host host=new Host();
  for(int pass=0;pass<3;pass++)for(int style:new int[]{0,1,2,1,0,-1,99}) {
   int before=NebulaFolderTabs.changes;NebulaFolderTabs.setPanelStyle(style);
   check(NebulaFolderTabs.changes==before+1);
   int expected=Math.max(0,Math.min(2,style));check(NebulaFolderTabs.panelStyle()==expected);
   for(boolean bottom:new boolean[]{true,false,true})for(boolean motion:new boolean[]{true,false}) {
    NebulaAppearance.liquid=motion;host.setNebulaBottomPanel(bottom);
    check(host.nebulaPanelStyle==(bottom?expected:0));
    check(host.nebulaLiquidSelector()==(motion&&(!bottom||expected==0)));cases++;
   }
  }
  for(boolean top:new boolean[]{false,true})for(boolean stories:new boolean[]{false,true})
   for(boolean slot:new boolean[]{false,true})for(float search:new float[]{0,.5f,1})
    for(float action:new float[]{0,.5f,1})for(float sliding:new float[]{0,.5f,1}) {
     check(NebulaFolderTabs.flatHeader(top,stories,slot,search,action,sliding)
      ==(!top&&!stories&&!slot&&search==0&&action==0&&sliding==0));cases++;
    }
  System.out.println(cases+" style changes/header visibility cases passed");
 }
}
'''
for key, body in {
    'GET_STYLE': method(folders, 'public static int panelStyle('),
    'SET_STYLE': method(folders, 'public static void setPanelStyle('),
    'FLAT_HEADER': method(folders, 'public static boolean flatHeader('),
    'APPLY': method(tabs, 'public void setNebulaBottomPanel('),
    'LIQUID': method(tabs, 'private boolean nebulaLiquidSelector('),
}.items():
    source = source.replace(key, body.replace('app.nebulagram.ui.', ''))
work = root / 'build/folder-panel-check'
work.mkdir(parents=True, exist_ok=True)
path = work / 'FolderStyleCheck.java'
path.write_text(source, encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', str(path)], check=True)
subprocess.run(['java', '-cp', str(work), 'FolderStyleCheck'], check=True)

render = method(tabs, 'private void drawSelector(')
assert 'NebulaFolderTabs.MINIMAL' in render and 'canvas.drawRoundRect' in render
assert render.count('nebulaLiquidSelector()') == 2
assert 'if (!nebulaLiquidSelector() || isEditing' in tabs
assert 'applyNebulaPanelStyle();' in method(tabs, 'public void updateColors(')
assert 'setNebulaBottomPanel' in method(dialogs, 'private void nebulaApplyBottomReserve(')
assert 'nebulaFlatHomeHeader()' in method(dialogs, 'public void drawBlurRect(')
assert 'nebulaFlatHomeHeader()' in method(dialogs, 'private void drawHeaderShadow(')
assert 'setNebulaBottomPanel(bottom)' in (ui / 'NebulaFoldersPreview.java').read_text(encoding='utf-8')
print('Native preview, live style updates, minimal selector and flat-header wiring passed')
