"""Guard circular seeds independent of the source outline and stable native endpoints."""
from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1];ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
source='''import app.nebulagram.ui.NebulaMenuBubble;
public class CheckAnchor {
 static void near(float a,float b,String why){if(Math.abs(a-b)>.003f)throw new AssertionError(why+": "+a+" != "+b);}
 public static void main(String[]args){
  var f=new NebulaMenuBubble.Frame();var end=new NebulaMenuBubble.Frame();int cases=0;
  for(float w:new float[]{180,240,400})for(float h:new float[]{160,400,850})for(int edge=0;edge<4;edge++) {
   float seed=44,x=(edge&1)==0?20:w-20,y=(edge&2)==0?20:h-20;
   NebulaMenuBubble.opening(f,0,w,h,x,y,seed,24);
   near(f.width,seed,"circle width");near(f.height,seed,"circle height");near(f.radius,seed/2,"circle radius");
   NebulaMenuBubble.openingWithin(end,1,w,h,x,y,seed,24,0,0,w-20,h-12);
   near(end.x,w/2,"final X matches native popup");near(end.y,h/2,"final Y");
   NebulaMenuBubble.openingWithin(f,.999999f,w,h,x,y,seed,24,0,0,w-20,h-12);
   near(f.x,end.x,"no completion jump X");near(f.y,end.y,"no completion jump Y");
   NebulaMenuBubble.closing(f,end,1,w,h,x,y,seed);
   near(f.width,seed,"closing circle width");near(f.height,seed,"closing circle height");near(f.radius,seed/2,"closing circle radius");cases++;
  }
  System.out.println(cases+" circular seeds and clipped-popup completion endpoints passed");
 }
}'''
with tempfile.TemporaryDirectory(prefix='nebula-anchor-') as folder:
 target=Path(folder)/'CheckAnchor.java';target.write_text(source,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',folder,str(target),str(ui/'NebulaMenuBubble.java')],check=True)
 subprocess.run(['java','-cp',folder,'CheckAnchor'],check=True)
reveal=(ui/'NebulaMenuReveal.java').read_text(encoding='utf-8')
assert 'getOutlineProvider' not in reveal and 'sourceRect' not in reveal
assert 'AndroidUtilities.dp(NebulaMenuBubble.SEED_DP)' in reveal
viewport=(ui/'NebulaMenuViewport.java').read_text(encoding='utf-8')
assert 'source.getWidth()*width' not in viewport and 'source.getHeight()*height' not in viewport
