"""Exercise source-shaped seeds and continuity at clipped native popup endpoints."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1];ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
source='''import app.nebulagram.ui.NebulaMenuBubble;
public class CheckAnchor {
 static void near(float a,float b,String why){if(Math.abs(a-b)>.003f)throw new AssertionError(why+": "+a+" != "+b);}
 public static void main(String[]args){
  var f=new NebulaMenuBubble.Frame();var end=new NebulaMenuBubble.Frame();int cases=0;
  if(NebulaMenuBubble.outset(180,400,180,0,500,60,32)<282)throw new AssertionError("wide source capsule must fit within the reserved drawing surface");
  for(float w:new float[]{180,240,400})for(float h:new float[]{160,400,850})
  for(float sw:new float[]{48,160,300})for(float sh:new float[]{40,60})for(float radius:new float[]{0,12,20}) {
   NebulaMenuBubble.opening(f,0,w,h,w-20,20,sw,sh,radius,24);
   near(f.width,sw,"source width");near(f.height,sh,"source height");near(f.radius,radius,"source corner radius");
   // Native shadow padding can extend beyond the visible display frame.
   NebulaMenuBubble.openingWithin(end,1,w,h,w-20,20,sw,sh,radius,24,0,0,w-20,h-12);
   near(end.x,w/2,"final X must match the native popup");near(end.y,h/2,"final Y");
   NebulaMenuBubble.openingWithin(f,.999999f,w,h,w-20,20,sw,sh,radius,24,0,0,w-20,h-12);
   near(f.x,end.x,"no completion jump X");near(f.y,end.y,"no completion jump Y");
   NebulaMenuBubble.closing(f,end,1,w,h,w-20,20,sw,sh,radius);
   near(f.width,sw,"closing source width");near(f.height,sh,"closing source height");near(f.radius,radius,"closing source radius");cases++;
  }
  System.out.println(cases+" source rectangle/capsule seeds and clipped-popup completion endpoints passed");
 }
}'''
with tempfile.TemporaryDirectory(prefix='nebula-anchor-') as folder:
 target=Path(folder)/'CheckAnchor.java';target.write_text(source,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',folder,str(target),str(ui/'NebulaMenuBubble.java')],check=True)
 subprocess.run(['java','-cp',folder,'CheckAnchor'],check=True)
reveal=(ui/'NebulaMenuReveal.java').read_text(encoding='utf-8')
assert 'outline.getRect(outlineRect)' in reveal and 'outline.getRadius()' in reveal
assert 'seedWidth,seedHeight,seedRadius' in reveal
