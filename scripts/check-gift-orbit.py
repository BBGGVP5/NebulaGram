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
assert 'postInvalidateOnAnimation();' in patch and 'if (moving)' in patch, 'animate only while visible'
assert 'giftsView.nebulaFrontLayer(context)' in patch, 'near half of the orbit passes in front of the avatar'
assert 'requestDisallowInterceptTouchEvent(true)' in patch and 'nebulaField.release(' in patch, 'drag owns the gesture and throws on release'
assert 'onGiftClick(pressedGift);' in patch, 'tap still opens the gift'
assert 'nebulaAlpha[hit] < .3f' in patch, 'faded bubbles do not catch touches'
assert patch.count('app.nebulagram.ui.NebulaProfileArt.actionsHeight()') == 8, 'every native 74 dp action row follows the iOS height'
assert 'drawableTop = actionIconTop(' in patch and 'protected float actionIconTop(float top, RectF rect) { return top; }' in patch
assert 'nebulaRing.drawHalo(' in patch and 'nebulaRing.drawRim(' in patch, 'avatar ring: halo behind, rim in front'
assert 'if (giftsView != null && giftsView.nebulaForwardTouch(this, event)) {' in patch, 'bubbles above the avatar are reachable through the glass header'
assert 'actionBar.setOnTouchListener' not in patch, 'the old action bar is invisible in the profile and gets no touches'
assert 'source.getParent().requestDisallowInterceptTouchEvent(true);' in patch, 'forwarded drag also blocks swipe-back'
assert 'musicView.setNebulaDialog(getDialogId());' in patch and 'MediaController.getInstance().pauseMessage(nebulaNow);' in patch, 'tap toggles the song inline'
assert 'showDialog(new AudioPlayerAlert(getContext(), getResourceProvider()));' in patch and 'nebulaMusicSheet = true;' in patch, 'long press keeps the full player'
assert 'if (bounce.isPressed() && !nebulaLongPressed)' in patch, 'long press never also toggles'
assert 'NebulaProfileArt.pillsHeight()' in patch
assert 'NebulaGiftOrbit.wander(i, count, nebulaSeconds' in patch and 'nebulaField.bounds(' in patch, 'bubbles wander inside the header'
assert 'visibility == VISIBLE && !nebulaOwnSong' in patch, 'the profile song has no separate player strip'
assert 'nebulaRing.setColor(' in patch
assert 'out.add(getOrCreate(KEY_MORE));' in patch and 'case ProfileActionsView.KEY_MORE:' in patch, 'iOS row ends with more'
assert 'insertIfAvailable(out, KEY_CALL);\n+                    insertIfAvailable(out, KEY_NOTIFICATION);' in patch, 'iOS order: chat, call, mute, more'
assert 'otherItem.addSubItem(video_call_item' in patch, 'video call stays reachable from the menu'
assert 'cs = actionLabel(cs);' in patch
assert 'nebulaRing.drawBackdrop(' in patch, 'header background pulses with the song'
assert 'nebulaIos ? dp(14) : dp(10)' in patch, 'song pill keeps a gap below the button labels'
assert 'private boolean nebulaMoreMenu()' in patch and '!nebulaMoreMenu()' in patch, 'more opens at the buttons'
assert 'NebulaAppearance.profileStyle() ? 19 : 24' in patch, 'status emoji is sized for the larger name'
art = open(ROOT / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui/NebulaProfileArt.java', encoding='utf-8').read()
assert 'profilePhotoBanner() && !iosProfile()' not in art, 'banner setting is honoured in the iOS profile'
art = (UI / 'NebulaProfileArt.java').read_text(encoding='utf-8')
assert 'if (iosProfile()) return; // круг' in art and 'return dp(iosProfile() ? 98 : 74);' in art

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
    'public static float target(int slot, long now)', 'public static float follow(', 'public static void orbit(', 'public static void wander(', 'static float clamp01(']]
field = orbit[orbit.index('    public static final class Field {'):]
field = field[:method(field, 'public static final class Field {').__len__()]
pieces.append(field.replace('public static final class Field', 'static final class Field'))
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
  Field f=new Field();f.ensure(3);float[] tx={0,200,400},ty={0,0,0};float r=20;
  for(int i=0;i<3;i++)f.bind(i,10+i,tx[i],ty[i]);
  f.drag(1,100,300);for(int i=0;i<120;i++){f.step(1/60f,tx,ty,r);}
  check(Math.hypot(f.x[1]-100,f.y[1]-300)<2,"held bubble follows the finger");check(f.grab[1]>.99f,"held bubble inflates");
  f.move(0,0);for(int i=0;i<120;i++)f.step(1/60f,tx,ty,r);
  check(Math.hypot(f.x[0]-f.x[1],f.y[0]-f.y[1])>=r*2.3f,"held bubble pushes a neighbour aside");check(Math.hypot(f.x[1],f.y[1])<2,"held bubble keeps its place");
  f.release(9000,0,3000);check(f.vx[1]==3000&&f.dragged==-1,"throw speed is capped");
  check(f.stretch(1,r)>1.2f&&f.stretch(1,r)<=1.28f,"fast bubble stretches along motion");
  for(int i=0;i<600;i++)f.step(1/60f,tx,ty,r);
  for(int i=0;i<3;i++){check(!Float.isNaN(f.x[i])&&Math.hypot(f.x[i]-tx[i],f.y[i]-ty[i])<3,"released bubbles settle back on the orbit "+i);check(f.grab[i]<.01f,"deflates");}
  check(Math.abs(f.stretch(0,r)-1)<.01f,"resting bubble is round");
  f.bind(2,99,50,50);check(f.x[2]==50&&f.vx[2]==0,"new gift in a slot starts at its point");
  Field g=new Field();g.ensure(2);g.bind(0,1,0,0);g.bind(1,2,0,0);float[] z={0,0};
  for(int i=0;i<240;i++)g.step(1/60f,z,z,r);check(!Float.isNaN(g.x[0])&&Math.hypot(g.x[0]-g.x[1],g.y[0]-g.y[1])>r,"coincident bubbles separate without NaN");
  g.step(0,z,z,r);g.step(5,z,z,r);check(!Float.isNaN(g.x[0]),"zero and huge dt are safe");
  g.drag(5,0,0);check(g.dragged==-1,"out of range drag ignored");g.ensure(1);
  // Хаотичное плавание: зона у каждого своя, точка не стоит и не повторяет соседа.
  float[] wv=new float[3],w2=new float[3];
  for(int n=1;n<=6;n++)for(int i=0;i<n;i++){
   double minR=1e9,maxR=0;float px=Float.NaN,py=0;double path=0;
   for(double t=0;t<120;t+=.25){wander(i,n,t,180,200,130,90,28,wv);
    double rr=Math.hypot((wv[0]-180)/130.0,(wv[1]-200)/90.0);minR=Math.min(minR,rr);maxR=Math.max(maxR,rr);
    check(wv[2]>=0&&wv[2]<=1,"wander depth in range");
    if(!Float.isNaN(px))path+=Math.hypot(wv[0]-px,wv[1]-py);px=wv[0];py=wv[1];}
   check(minR>.45&&maxR<1.6,"wander stays around the avatar "+n+"/"+i+" "+minR+".."+maxR);
   check(path>400,"wander keeps moving "+path);
  }
  for(int i=0;i<4;i++){wander(i,5,33,0,0,100,100,28,wv);for(int j=i+1;j<5;j++){wander(j,5,33,0,0,100,100,28,w2);check(Math.hypot(wv[0]-w2[0],wv[1]-w2[1])>15,"zones do not stack "+i+","+j);}}
  Field b=new Field();b.ensure(1);b.bounds(0,0,100,100);float[] bx={50},by={50};b.bind(0,1,50,50);
  b.drag(0,50,50);b.release(4000,0,3000);float peak=0;boolean bounced=false;
  for(int i=0;i<120;i++){b.step(1/60f,bx,by,10);peak=Math.max(peak,b.x[0]);if(b.vx[0]<0)bounced=true;check(b.x[0]>=0&&b.x[0]<=100&&b.y[0]>=0&&b.y[0]<=100,"thrown bubble stays inside the header");}
  check(bounced&&peak>=99.9f,"thrown bubble flies to the wall and bounces");
  for(int i=0;i<900;i++)b.step(1/60f,bx,by,10);check(Math.hypot(b.x[0]-50,b.y[0]-50)<3&&b.calm[0]>.99f,"after a while it floats back into its zone");
  System.out.println("OK: "+checks+" gift orbit, music pulse and voice reveal checks");
 }
}'''.replace('BODY', '\n'.join(pieces))
with tempfile.TemporaryDirectory(prefix='nebula-gift-orbit-') as folder:
    java = Path(folder) / 'GiftOrbitCheck.java'
    java.write_text(fixture, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', folder, str(java)], check=True)
    subprocess.run(['java', '-cp', folder, 'GiftOrbitCheck'], check=True, timeout=20)
