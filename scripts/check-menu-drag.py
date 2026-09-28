"""Check menu surface/content affine geometry and inverse touch dispatch."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
native = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarPopupWindow.java'
work = root / 'build/menu-drag-check'
work.mkdir(parents=True, exist_ok=True)
source = r'''
import app.nebulagram.ui.NebulaMenuMotion;
public class CheckMenuDrag {
 static void near(float actual,float expected,String why) {
  if(Math.abs(actual-expected)>.001f) throw new AssertionError(why+": "+actual+" != "+expected);
 }
 public static void main(String[] args) {
  int cases=0;
  for(float extent:new float[]{0,8,16,48,240,800})
  for(float padding:new float[]{0,8,22})
  for(float pull:new float[]{-24,-9,-1,0,1,9,24}) {
   float scale=NebulaMenuMotion.stretchScale(pull,extent,padding);
   float offset=NebulaMenuMotion.stretchOffset(pull,extent,padding);
   if(extent<=padding*2 || pull==0) {
    near(scale,1,"idle/empty scale"); near(offset,0,"idle/empty offset");
   } else {
    near(padding*scale+offset,padding+Math.min(0,pull),"leading glass edge");
    near((extent-padding)*scale+offset,extent-padding+Math.max(0,pull),"trailing glass edge");
   }
   for(float x:new float[]{0,padding,extent/2,extent})
    near((x*scale+offset-offset)/scale,x,"pointer inverse");
   cases++;
  }
  System.out.println(cases+" menu drag geometries and inverse pointer transforms passed");
 }
}
'''
target = work / 'CheckMenuDrag.java'
target.write_text(source, encoding='utf-8')
subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(target),str(ui/'NebulaMenuMotion.java')],check=True)
subprocess.run(['java','-cp',str(work),'CheckMenuDrag'],check=True)
reveal = (ui/'NebulaMenuReveal.java').read_text(encoding='utf-8')
assert 'canvas.concat(contentTransform)' in reveal
assert 'contentTransform.invert(inverseContentTransform)' in reveal
assert 'copy.transform(inverseContentTransform)' in reveal
assert 'return event;' in reveal, 'identity path must not allocate an event'
popup = native.read_text(encoding='utf-8')
assert 'nebulaReveal.contentTouchEvent(event)' in popup
assert 'super.dispatchTouchEvent(contentEvent)' in popup
assert 'finally {' in popup and 'if (contentEvent != event) contentEvent.recycle();' in popup
divider = (ui/'NebulaMenuDivider.java').read_text(encoding='utf-8')
assert 'extends View' in divider and 'extends ActionBarPopupWindow.GapView' not in divider
assert 'canvas.drawRect(edge, top, getWidth() - edge, top + thickness, paint)' in divider
tree = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui'
menu_item = (tree/'ActionBar/ActionBarMenuItem.java').read_text(encoding='utf-8')
chat = (tree/'ChatActivity.java').read_text(encoding='utf-8')
tabs = (tree/'MainTabsActivity.java').read_text(encoding='utf-8')
assert 'lazilyAddNebulaDivider()' in menu_item and 'new app.nebulagram.ui.NebulaMenuDivider' in menu_item
assert 'headerItem.lazilyAddNebulaDivider()' in chat
assert tabs.count('NebulaMenuDivider.add(o, getContext(), getResourceProvider())') == 6
assert 'if (child instanceof GapView) {' in popup, 'The upstream animation must skip only native gaps'
print('Shared content geometry and copied/recycled touch dispatch are wired')
