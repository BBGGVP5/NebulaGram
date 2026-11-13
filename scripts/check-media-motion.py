"""Expressive loading indicator and folder switch styles.

Compiles the Android-free geometry (NebulaMotionShapes) and checks the
properties the drawing relies on, then — when a prepared tree is given —
that MediaActionDrawable, RadialProgress2 and DialogsActivity call the hooks.
"""
from pathlib import Path
import subprocess
import sys

r = Path(__file__).resolve().parent.parent
work = r / 'build/media-motion'
work.mkdir(parents=True, exist_ok=True)
overlay = r / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'

harness = r'''
import app.nebulagram.ui.NebulaMotionShapes;
public class CheckMediaMotion {
static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
static boolean near(float a,float b){return Math.abs(a-b)<1e-3f;}
public static void main(String[] a){
 int shapes=7;
 for(int s=0;s<shapes;s++){
  float max=0,min=2;
  for(int i=0;i<720;i++){double t=Math.PI*2*i/720;float v=NebulaMotionShapes.radius(s,0,t,0.3,0);max=Math.max(max,v);min=Math.min(min,v);}
  check(max<=1.0001f&&max>.995f,"shape "+s+" must touch the ring, max="+max);
  check(min>.7f,"shape "+s+" too deep for a 3dp stroke, min="+min);
  for(int i=0;i<64;i++){double t=i*.1;
   check(near(NebulaMotionShapes.radius(s,1,t,0,0),NebulaMotionShapes.radius((s+1)%shapes,0,t,0,0)),"morph end must equal next shape");
   check(near(NebulaMotionShapes.radius(s,.4f,t,0,1),1f),"settle must end on a circle");}
 }
 long step=800;
 for(long t=0;t<step*shapes*2;t+=7){float m=NebulaMotionShapes.morphAt(t);check(m>=0&&m<=1,"morph out of range");}
 check(NebulaMotionShapes.morphAt(0)==0&&NebulaMotionShapes.morphAt(step-1)>.99f,"morph must start still and finish at next shape");
 check(NebulaMotionShapes.shapeAt(step*shapes)==0&&NebulaMotionShapes.shapeAt(step)==1,"shape cycle");
 check(NebulaMotionShapes.shapeAt(-5)>=0,"negative clock");
 check(near(NebulaMotionShapes.pulse(0),1)&&near(NebulaMotionShapes.pulse(1),1)&&NebulaMotionShapes.pulse(.5f)>1.1f,"finish pulse");
 check(NebulaMotionShapes.checkAlpha(.3f)==1&&NebulaMotionShapes.checkAlpha(1)==0&&NebulaMotionShapes.checkTrim(.45f)==1,"check timing");
 check(NebulaMotionShapes.points(true)<NebulaMotionShapes.points(false),"mini icon must be cheaper");

 // Кнопка 44dp при 3x: фигура обязана быть видимого размера, а светящаяся часть —
 // не точкой, в любом состоянии: ожидание сети, начало, середина, финал, «меньше движения».
 float outer=66, sw[]=new float[2];
 float[][] states={{0,0},{.005f,0},{.019f,0},{.3f,0},{1,0},{.7f,.4f}};
 for(float[] st:states)for(int calm=0;calm<2;calm++)for(long t=0;t<6000;t+=97){
  NebulaMotionShapes.sweep(st[0],st[1],t,calm==1,sw);
  check(sw[1]>0&&sw[1]<=1&&sw[0]>=0&&sw[0]<1,"sweep out of range");
  float minX=1e9f,maxX=-1e9f,minY=1e9f,maxY=-1e9f,lit=0,px=0,py=0;
  int shape=NebulaMotionShapes.shapeAt(t);float morph=NebulaMotionShapes.morphAt(t);
  for(int i=0;i<=96;i++){double ang=Math.PI*2*(sw[0]+sw[1]*i/96.0);
   float rr=outer*NebulaMotionShapes.radius(shape,morph,ang,.4,0);float x=(float)Math.cos(ang)*rr,y=(float)Math.sin(ang)*rr;
   if(i>0)lit+=Math.hypot(x-px,y-py);px=x;py=y;}
  for(int i=0;i<96;i++){double ang=Math.PI*2*i/96.0;float rr=outer*NebulaMotionShapes.radius(shape,morph,ang,.4,0);
   minX=Math.min(minX,(float)Math.cos(ang)*rr);maxX=Math.max(maxX,(float)Math.cos(ang)*rr);minY=Math.min(minY,(float)Math.sin(ang)*rr);maxY=Math.max(maxY,(float)Math.sin(ang)*rr);}
  check(maxX-minX>outer*1.4f&&maxY-minY>outer*1.4f,"shape collapsed for a non-zero button");
  check(lit>outer*.5f,"lit part shrank to a dot: progress="+st[0]+" lit="+lit);
  if(st[0]<.02f&&st[1]==0)check(NebulaMotionShapes.trackAlpha(st[0],0)>=.5f,"waiting shape must be readable");
  if(st[0]>=.3f&&st[1]==0)check(near(sw[1],st[0]),"known progress must be exact");
 }
 float w=1080,o[]=new float[5];
 for(int st=0;st<NebulaMotionShapes.STYLES;st++){
  NebulaMotionShapes.page(st,0,w,o);
  check(o[0]==0&&o[1]==1&&o[2]==1&&o[3]==w&&o[4]==0,"style "+st+" must rest untouched");
  for(float f=-1;f<=1;f+=.05f){NebulaMotionShapes.page(st,f,w,o);check(o[2]>=0&&o[2]<=1&&o[1]>.85f&&o[1]<=1&&o[3]>=0&&o[3]<=w,"style "+st+" out of range at "+f);}
 }
 for(float f=-1;f<=1;f+=.1f){NebulaMotionShapes.page(NebulaMotionShapes.SLIDE,f,w,o);check(o[0]==0&&o[1]==1&&o[2]==1&&o[3]==w,"slide must stay Telegram's");}
 NebulaMotionShapes.page(NebulaMotionShapes.CARDS,-.5f,w,o);check(o[1]<.95f&&o[4]>.99f,"cards shrink and round mid-way");
 NebulaMotionShapes.page(NebulaMotionShapes.FADE,1,w,o);check(o[2]==0,"fade: incoming starts invisible");
 NebulaMotionShapes.page(NebulaMotionShapes.FADE,-.5f,w,o);check(Math.abs(-.5f*w+o[0])<.07f*w,"fade must barely move");
 NebulaMotionShapes.page(NebulaMotionShapes.INSTANT,.4f,w,o);check(o[2]==1&&near(.4f*w+o[0],0),"instant: near page in place");
 NebulaMotionShapes.page(NebulaMotionShapes.INSTANT,.6f,w,o);check(o[2]==0,"instant: far page hidden");
 for(float f=-.95f;f<0;f+=.05f){
  NebulaMotionShapes.page(NebulaMotionShapes.DEPTH_STYLE,f,w,o);
  float visualLeft=f*w+o[0];
  float clipRight=visualLeft+w/2+(o[3]-w/2)*o[1];
  check(Math.abs(clipRight-(1+f)*w)<1f||o[3]==w,"depth: under page must end at the top page edge, f="+f);
  check(Math.abs(visualLeft-f*w*.3f)<1f,"depth: parallax");
  NebulaMotionShapes.page(NebulaMotionShapes.DEPTH_STYLE,f+1,w,o);
  check(o[0]==0&&o[1]==1&&o[3]==w,"depth: top page slides untouched");
 }
 System.out.println("Media motion passed: shapes touch the ring, morph is continuous, finish settles, folder styles rest untouched and keep geometry");
}}
'''
classes = work / 'classes'
(work / 'CheckMediaMotion.java').write_text(harness, encoding='utf-8')
subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(classes), str(work / 'CheckMediaMotion.java'),
                str(overlay / 'NebulaMotionShapes.java')], check=True)
subprocess.run(['java', '-cp', str(classes), 'CheckMediaMotion'], check=True)

progress = (overlay / 'NebulaShapeProgress.java').read_text(encoding='utf-8')
switch = (overlay / 'NebulaFolderSwitch.java').read_text(encoding='utf-8')
assert 'NebulaGlass.reduced()' in progress and 'NebulaGlass.reduced()' in switch, 'reduced motion must be honoured'
assert '.015f' not in progress, 'a fixed tiny sweep leaves a spinning dot'
assert 'new Path' not in progress.split('public static boolean draw')[1], 'draw must not allocate per frame'
assert 'setAnimationMatrix' in switch and 'SDK_INT < 29' in switch, 'folder styles use the animation matrix, guarded'

if len(sys.argv) > 1:
    tree = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/ui'
    media = (tree / 'Components/MediaActionDrawable.java').read_text(encoding='utf-8')
    assert media.count('NebulaShapeProgress.draw(') == 2, 'both progress arcs go through the shape hook'
    assert 'NebulaShapeProgress.drawPercent(' in media and 'NebulaShapeProgress.finishDuration(' in media
    radial = (tree / 'Components/RadialProgress2.java').read_text(encoding='utf-8')
    assert 'nebulaOnMedia = circleColorKey == Theme.key_chat_mediaLoaderPhoto' in radial
    dialogs = (tree / 'DialogsActivity.java').read_text(encoding='utf-8')
    hook = dialogs.index('NebulaFolderSwitch.apply(this, translationX)')
    assert dialogs.rindex('super.setTranslationX(translationX)', 0, hook) > dialogs.rindex('public class ViewPage', 0, hook)
    print('Media motion hooks present in', sys.argv[1])
