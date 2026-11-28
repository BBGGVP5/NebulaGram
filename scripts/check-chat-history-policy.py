"""Exercise pagination and slow-mode spacing without network or user messages."""
from pathlib import Path
import subprocess
import tempfile
root=Path(__file__).resolve().parents[1]
ui=root/'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
source=r'''
import app.nebulagram.ui.*;
class ChatHistoryPolicyCheck {
 static void ok(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  var p=new NebulaHistoryPager(250);ok(p.remaining()==100&&p.accepts(1000)&&!p.accepts(0));
  ok(p.advance(901,100));ok(p.offset()==901&&!p.accepts(901)&&!p.accepts(1000)&&p.accepts(900));
  ok(p.advance(801,100));ok(p.remaining()==50);ok(!p.advance(751,50));ok(p.remaining()==0&&p.received()==250);
  p=new NebulaHistoryPager(0);for(int n=100000;n>0;n-=100){ok(p.remaining()==100);ok(p.advance(n,100));}
  ok(p.received()==100000);ok(!p.advance(0,0));ok(!p.advance(100,100));
  for(int step:new int[]{1,10,30,60,3600,86400}) {
   int last=0;for(int i=0;i<1000;i++){
    int next=NebulaSlowModePolicy.next(1700000000,1700000030,last,step);
    ok(next>1700000030);if(last>0)ok(next-last>=step);last=next;
   }
  }
  ok(NebulaSlowModePolicy.next(1700000000,0,1699999999,60)==1700000010);
  for(int bad:new int[]{0,-1,86401})try{NebulaSlowModePolicy.next(1700000000,0,0,bad);throw new AssertionError();}catch(IllegalArgumentException expected){}
  try{NebulaSlowModePolicy.next(Integer.MAX_VALUE-5,0,0,60);throw new AssertionError();}catch(IllegalArgumentException expected){}
  var fade=new NebulaTextFade();ok(fade.alpha(0,10,1,0,1000,true)==0);
  int previous=0;for(int t=1000;t<=1160;t++) {int alpha=fade.alpha(0,10,1,0,t,true);ok(alpha>=previous&&alpha<=255);previous=alpha;}
  ok(previous==255&&fade.alpha(0,10,1,0,1200,true)==255);
  ok(fade.alpha(0,10,1,1,1300,true)==0);ok(fade.alpha(0,10,1,1,1301,false)==255);
  ok(fade.alpha(0,10,1,1,1302,true)==255);ok(fade.alpha(1,10,1,1,1400,true)==0);
  ok(fade.alpha(1,20,1,1,1500,true)==0);ok(fade.alpha(1,20,2,1,1600,true)==0);
  System.out.println("History pagination/dedup boundary, 100000-message stream and 6000 queue slots passed");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-history-policy-') as directory:
    work=Path(directory);test=work/'ChatHistoryPolicyCheck.java';test.write_text(source,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8','-d',str(work),str(test),str(ui/'NebulaHistoryPager.java'),str(ui/'NebulaSlowModePolicy.java'),str(ui/'NebulaTextFade.java')],check=True)
    subprocess.run(['java','-cp',str(work),'ChatHistoryPolicyCheck'],check=True)
