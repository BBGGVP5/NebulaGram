"""Check menu surface/content affine geometry and inverse touch dispatch."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
native = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarPopupWindow.java'
work = root / 'build/menu-drag-check'
work.mkdir(parents=True, exist_ok=True)

def method(text, signature):
    start = text.index(signature)
    end = text.index('{', start) + 1
    depth = 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]

reveal = (ui/'NebulaMenuReveal.java').read_text(encoding='utf-8')
source = r'''
import app.nebulagram.ui.NebulaMenuMotion;
import app.nebulagram.ui.NebulaMenuBubble;
public class CheckMenuDrag {
 static void near(float actual,float expected,String why) {
  if(Math.abs(actual-expected)>.001f) throw new AssertionError(why+": "+actual+" != "+expected);
 }
 static class Host {int w=240,h=320;int getWidth(){return w;}int getHeight(){return h;}
  int getMeasuredWidth(){return w;}int getMeasuredHeight(){return h;}}
 static class AndroidUtilities {static int dp(int n){return n;}}
 static class NebulaMenuStyle {static float radius(){return 24;}}
 static class Matrix {
  float sx=1,sy=1,tx,ty;
  void setScale(float x,float y){sx=x;sy=y;tx=ty=0;}
  void postScale(float x,float y){sx*=x;sy*=y;tx*=x;ty*=y;}
  void postTranslate(float x,float y){tx+=x;ty+=y;}
  boolean invert(Matrix out){if(sx==0||sy==0)return false;out.sx=1/sx;out.sy=1/sy;out.tx=-tx/sx;out.ty=-ty/sy;return true;}
  float x(float x){return x*sx+tx;}float y(float y){return y*sy+ty;}
 }
 static class MotionEvent {
  float x,y;MotionEvent(float x,float y){this.x=x;this.y=y;}
  static MotionEvent obtain(MotionEvent event){return new MotionEvent(event.x,event.y);}
  void transform(Matrix matrix){x=matrix.x(x);y=matrix.y(y);}
 }
 static class Reveal {
  Host host=new Host();float progress=1,pullX,pullY,originX=240,originY=0,seed=48,closeProgress;
  boolean closing;NebulaMenuBubble.Frame frame=new NebulaMenuBubble.Frame(),closeFrame=new NebulaMenuBubble.Frame();
  Matrix contentTransform=new Matrix(),inverseContentTransform=new Matrix();
  METHODS
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
  Reveal r=new Reveal();int moving=0;
  for(int corner=0;corner<4;corner++)for(int p=0;p<=100;p++)
  for(float pullX:new float[]{-9,0,9})for(float pullY:new float[]{-9,0,9}){
   r.originX=(corner&1)==0?0:r.host.w;r.originY=(corner&2)==0?0:r.host.h;
   r.progress=p/100f;r.pullX=pullX;r.pullY=pullY;r.updateContentTransform();
   float cx=r.progress==1?r.host.w/2f:r.frame.x,cy=r.progress==1?r.host.h/2f:r.frame.y;
   near(r.contentTransform.x(r.host.w/2f),cx+pullX/2,"content center follows glass during reveal/drag");
   near(r.contentTransform.y(r.host.h/2f),cy+pullY/2,"vertical content center follows glass");
   for(float x:new float[]{8,50,220})for(float y:new float[]{8,100,300}){
    MotionEvent original=new MotionEvent(r.contentTransform.x(x),r.contentTransform.y(y));
    MotionEvent mapped=r.contentTouchEvent(original);
    near(mapped.x,x,"actual inverse touch x");near(mapped.y,y,"actual inverse touch y");
    if(p==100&&pullX==0&&pullY==0&&mapped!=original)throw new AssertionError("idle event must not be copied");
   }
   moving++;
  }
  System.out.println(moving+" moving bubble/drag matrices and actual native pointer conversions passed");
 }
}
'''
source = source.replace('METHODS', '\n'.join(method(reveal, signature) for signature in
    ['private void updateFrame(', 'private void updateContentTransform(', 'public MotionEvent contentTouchEvent(']))
target = work / 'CheckMenuDrag.java'
target.write_text(source, encoding='utf-8')
subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(target),str(ui/'NebulaMenuMotion.java'),str(ui/'NebulaMenuBubble.java')],check=True)
subprocess.run(['java','-cp',str(work),'CheckMenuDrag'],check=True)
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
