"""Check frame-rate-independent folder settling and continuity after a drag."""
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parent.parent
ui = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
native = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui/Components'
work = root / 'build/folder-swipe-check'
work.mkdir(parents=True, exist_ok=True)
source = r'''
import app.nebulagram.ui.NebulaFolderMotion;
public class CheckFolderSwipe {
 static void check(boolean ok,String why) { if(!ok) throw new AssertionError(why); }
 static void near(float a,float b) { check(Math.abs(a-b)<.001f,"continuity: "+a+" != "+b); }
 public static void main(String[] args) {
  for(int hz:new int[]{60,90,120}) {
   float p=0; long previous=0;
   for(int frame=1;frame<=hz;frame++) {
    long now=Math.round(frame*1000.0/hz);
    p=NebulaFolderMotion.advance(p,now-previous); previous=now;
    near(p,Math.min(1,now/320f));
   }
   near(p,1);
  }
  near(NebulaFolderMotion.advance(.25f,0),.25f);
  near(NebulaFolderMotion.advance(.25f,-20),.25f);
  near(NebulaFolderMotion.advance(.25f,1000),1);
  for(float origin:new float[]{-50,0,120,900}) for(float target:new float[]{0,90,400}) {
   near(NebulaFolderMotion.release(origin,target,0),origin);
   near(NebulaFolderMotion.release(origin,target,1),target);
   float last=origin;
   for(int i=1;i<=100;i++) {
    float next=NebulaFolderMotion.release(origin,target,i/100f);
    check(target>=origin ? next>=last : next<=last,"release reversed"); last=next;
   }
  }
  System.out.println("Folder timing at 60/90/120 Hz, delayed frames and 12 continuous releases passed");
 }
}
'''
target = work / 'CheckFolderSwipe.java'
target.write_text(source, encoding='utf-8')
subprocess.run(['javac','-d',str(work),str(target),str(ui/'NebulaFolderMotion.java')],check=True)
subprocess.run(['java','-cp',str(work),'CheckFolderSwipe'],check=True)
folders = (native/'FilterTabsView.java').read_text(encoding='utf-8')
assert 'lastAnimationTime = newTime;' in folders
assert 'postOnAnimation(animationRunnable)' in folders
assert 'postOnAnimation(this)' in folders
assert 'runOnUIThread(animationRunnable)' not in folders
assert 'nebulaReleaseCenter - nebulaReleaseWidth / 2f' in folders
assert 'x + nebulaDragOffsetX' in folders
assert 'nebulaLens.pulse()' not in folders
lens = (ui/'NebulaTabLens.java').read_text(encoding='utf-8')
assert 'dragSpeed' not in lens, 'per-draw speed estimate changes shape at unrelated redraws'
scrim = (native/'ScrimOptions.java').read_text(encoding='utf-8')
assert scrim.count('if (!app.nebulagram.ui.NebulaMenuStyle.enabled()) {') >= 2
print('Vsync scheduling, drag handoff, stable lens and untinted menu-source wiring passed')
