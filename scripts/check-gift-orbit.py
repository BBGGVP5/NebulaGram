"""Execute the gift orbit, music pulse and voice reveal logic; check the native hooks."""
from pathlib import Path
import subprocess, sys, tempfile

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'


def method(source, name):
    start = source.index(name)
    start = source.rfind('\n', 0, start) + 1
    opening = source.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


patch = (ROOT / 'patches/android/0371-profile-gift-orbit.patch').read_text(encoding='utf-8')
assert 'app.nebulagram.ui.NebulaGiftOrbit.onVisualizer(playing, values);' in patch
assert '|| app.nebulagram.ui.NebulaGiftOrbit.listening();' in patch
assert 'NebulaGiftOrbit.attach();' in patch and 'NebulaGiftOrbit.detach();' in patch
assert 'nebulaBubble = b.nebulaBubble;' in patch, 'bubble state survives gift list refresh'
assert 'postInvalidateOnAnimation();' in patch and 'if (visible)' in patch, 'animate only while visible'

orbit = (UI / 'NebulaGiftOrbit.java').read_text(encoding='utf-8')
reveal = (UI / 'NebulaVoiceReveal.java').read_text(encoding='utf-8')
host = (UI / 'NebulaDraftVoiceInlineHost.java').read_text(encoding='utf-8')
sheet = (UI / 'NebulaDraftVoiceTranscription.java').read_text(encoding='utf-8')
assert 'implements NebulaDraftVoiceTranscription.FieldHost' in host
deliver = method(sheet, 'private void deliver()')
assert deliver.index('source.equals(current.get())') < deliver.index('useText.accept(value)'), 'stale recording never replaced'
assert deliver.index('cancel();\n') < deliver.index('useText.accept(value)')
assert 'field.failed(message);cancel();' in sheet, 'failure keeps the voice and closes'

pieces = [method(orbit, n) for n in ['static void onVisualizer(boolean playing, float[] values, long now)',
    'public static float target(int slot, long now)', 'public static float follow(', 'public static void orbit(', 'static float clamp01(']]
pieces += [method(reveal, n) for n in ['static float wordProgress(', 'static long duration(', 'static List<int[]> words(']]
fixture = r'''import java.util.*;
public class GiftOrbitCheck {
 static final Object lock=new Object();static final float[] bands=new float[6];static float amplitude;static long updatedAt=Long.MIN_VALUE/2;
 static final double PERIOD=28.0;static final float FADE=3f;
 BODY
 static int checks;static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 public static void main(String[] a){
  check(target(0,1000)==0,"no audio: rest");
  onVisualizer(true,new float[]{0,0,0,0,0,0,.3f},1000);check(target(2,1100)==0,"quiet passage below threshold stays calm");
  onVisualizer(true,new float[]{1,.2f,.2f,.2f,.2f,.9f,.85f},2000);
  float beat=target(0,2050),other=target(1,2050);check(beat>.9f&&other<beat&&other>.5f,"loud beat drives every bubble, own band adds");
  check(target(6,2050)==beat,"slot wraps bands");
  check(target(0,2500)==0,"stale analyzer data decays to rest");
  onVisualizer(false,null,3000);check(target(0,3010)==0,"pause stops the pulse");
  float up=follow(0,1,16),down=follow(1,0,16);check(up>.25f&&down>.9f,"fast attack, slow release");
  check(follow(.5f,1,0)==.5f,"zero dt keeps value");
  float v=0;for(int i=0;i<30;i++)v=follow(v,1,16);check(v>.99f,"settles on target");
  float[] p=new float[3];
  for(int n=1;n<=6;n++)for(int i=0;i<n;i++)for(double t=0;t<60;t+=.37){
   orbit(i,n,t,180,200,130,80,5,p);
   double dx=(p[0]-180)/130.0,dy=(p[1]-5*Math.sin(t*1.25+i*1.1)-200)/80.0,r=Math.sqrt(dx*dx+dy*dy);
   check(r>.92&&r<1.08,"stays on the ellipse");check(p[2]>=0&&p[2]<=1,"depth in range");
  }
  orbit(0,3,10,0,0,1,1,0,p);float x0=p[0],y0=p[1];orbit(1,3,10,0,0,1,1,0,p);
  check(Math.hypot(p[0]-x0,p[1]-y0)>.8,"gifts spread around the avatar");
  orbit(0,1,0,0,0,1,1,0,p);float s0=p[0],t0=p[1];orbit(0,1,1,0,0,1,1,0,p);check(Math.hypot(p[0]-s0,p[1]-t0)>.05,"orbit moves");
  check(wordProgress(0,0,5)==0&&wordProgress(1,4,5)==1,"reveal starts hidden and ends complete");
  for(int i=0;i<4;i++)for(float t=0;t<=1;t+=.05f)check(wordProgress(t,i,5)>=wordProgress(t,i+1,5),"words appear in order");
  check(duration(1)==600&&duration(200)==2200&&duration(10)==900,"duration bounds");
  List<int[]> w=words("  Теперь можно\nзаписывать  голосовое ");check(w.size()==4&&w.get(0)[0]==2&&w.get(3)[1]==36,"word ranges");
  check(words("   ").isEmpty(),"blank text has no words");
  System.out.println("OK: "+checks+" gift orbit, music pulse and voice reveal checks");
 }
}'''.replace('BODY', '\n'.join(pieces))
with tempfile.TemporaryDirectory(prefix='nebula-gift-orbit-') as folder:
    java = Path(folder) / 'GiftOrbitCheck.java'
    java.write_text(fixture, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', folder, str(java)], check=True)
    subprocess.run(['java', '-cp', folder, 'GiftOrbitCheck'], check=True, timeout=20)
