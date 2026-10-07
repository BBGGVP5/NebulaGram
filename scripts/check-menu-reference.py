"""Check menu timing, focus, travel continuity and interruption behavior."""
from pathlib import Path
import subprocess,sys,tempfile
root=Path(__file__).resolve().parents[1]
ui=Path(sys.argv[1]) if len(sys.argv)>1 else root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
source='''import app.nebulagram.ui.NebulaMenuBubble;
public class CheckMotion {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static void near(float a,float b,String why){check(Math.abs(a-b)<.003f,why+": "+a+" != "+b);}
 public static void main(String[]args){
  check(NebulaMenuBubble.OPEN_DURATION_MS>=350&&NebulaMenuBubble.OPEN_DURATION_MS<=450,"responsive open");
  var f=new NebulaMenuBubble.Frame();var previous=new NebulaMenuBubble.Frame();var capture=new NebulaMenuBubble.Frame();int cases=0;
  for(float w:new float[]{80,180,240,600})for(float h:new float[]{80,400,900})for(int corner=0;corner<4;corner++) {
   float x=(corner&1)==0?0:w,y=(corner&2)==0?0:h;
   NebulaMenuBubble.opening(previous,0,w,h,x,y,44,24);
   near(previous.width,44,"initial circle width");near(previous.radius,22,"initial circle radius");
   for(int i=1;i<=1000;i++){
    NebulaMenuBubble.opening(f,i/1000f,w,h,x,y,44,24);
    check(f.width>=previous.width-.001f&&f.height>=previous.height-.001f,"no size ringing");
    check(f.width<=w+.001f&&f.height<=h+.001f,"no overshoot plateau");
    check(Math.abs(f.x-w/2)<=Math.abs(previous.x-w/2)+.001f&&Math.abs(f.y-h/2)<=Math.abs(previous.y-h/2)+.001f,"travel approaches endpoint");
    check(f.content>=previous.content-.001f&&f.content>=0&&f.content<=1,"monotonic focus");
    if(i<=200)check(f.content==0,"circle must not expose miniature icons or labels");
    check(NebulaMenuBubble.contentScale(f,w,h,8,false)>=.95f,"content emerges in place");
    if(i==1||i==1000)check(Math.abs(f.width-previous.width)<.01f,"soft start/stop velocity");
    previous.copy(f);cases++;
   }
   near(f.x,w/2,"final x");near(f.y,h/2,"final y");near(f.radius,24,"final radius");near(f.content,1,"final focus");
   for(float interrupt:new float[]{.03f,.2f,.45f,.8f,1}) {
    NebulaMenuBubble.opening(capture,interrupt,w,h,x,y,44,24);
    int duration=NebulaMenuBubble.closeDuration(capture,w,h,44);check(duration>=160&&duration<=240,"smooth close timing");
    NebulaMenuBubble.closing(f,capture,0,w,h,x,y,44);near(f.width,capture.width,"interrupted size");near(f.x,capture.x,"interrupted position");near(f.radius,capture.radius,"interrupted radius");near(f.content,capture.content,"interrupted focus");
    previous.copy(f);
    for(int i=1;i<=1000;i++) {
     NebulaMenuBubble.closing(f,capture,i/1000f,w,h,x,y,44);
     check(f.width<=previous.width+.001f&&f.height<=previous.height+.001f,"close shrinks continuously");
     check(f.content<=previous.content+.001f&&f.alpha<=previous.alpha+.001f,"close opacity/focus cannot return");
     previous.copy(f);cases++;
    }
    near(f.width,44,"closing circle");near(f.radius,22,"closing radius");near(f.x,x,"closing anchor");near(f.alpha,0,"closing opacity");
   }
  }
  System.out.println(cases+" circular menu motion/focus/interruption frames passed");
 }
}'''
with tempfile.TemporaryDirectory(prefix='nebula-menu-motion-') as folder:
 target=Path(folder)/'CheckMotion.java';target.write_text(source,encoding='utf-8')
 subprocess.run(['javac','-encoding','UTF-8','-d',folder,str(target),str(ui/'NebulaMenuBubble.java')],check=True)
 subprocess.run(['java','-cp',folder,'CheckMotion'],check=True)
