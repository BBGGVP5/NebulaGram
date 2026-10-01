"""Execute module routing, ruler geometry and the patched legacy camera ratio mapping."""
from pathlib import Path
import subprocess
import tempfile
import sys
import re

root = Path(__file__).resolve().parent.parent
overlay = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'build/android-validation'

def method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

slider = (overlay / 'NebulaZoomSlider.java').read_text(encoding='utf-8')
geometry = '\n'.join(method(slider, signature) for signature in [
    'public void setRange(float min, float max)', 'public void setCameraStops(float[] values)',
    'private void addQuickStop(java.util.TreeSet<Float> stops, float value)',
    'private void animateZoom(float factor)', 'private void selectPreset(float factor)', 'private float fullWidth()',
    'private float compactWidth()', 'private float widthForProgress()', 'private RectF capsuleBounds()',
    'private float pixelsPerOctave()', 'private float xForZoom(float zoom)',
    'private float presetAt(float x)', 'public boolean onTouchEvent(MotionEvent event)'])
legacy = (tree / 'TMessagesProj/src/main/java/org/telegram/messenger/camera/CameraSession.java').read_text(encoding='utf-8')
instant = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/Components/InstantCameraView.java').read_text(encoding='utf-8')
composer = (tree / 'TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java').read_text(encoding='utf-8')
zoom_margin = int(re.search(r'zoomParams.bottomMargin = dp\((\d+)\)', instant).group(1))
buttons_height, buttons_margin = map(int, re.search(r'addView\(buttonsLayout, LayoutHelper.createFrame\(LayoutHelper.WRAP_CONTENT, (\d+), Gravity.CENTER_HORIZONTAL \| Gravity.BOTTOM, 0, 0, 0, (\d+)\)', instant).groups())
timer_margin = int(re.search(r'int margin = dp\(nebulaRecordingIslands \? (\d+) : 6\)', composer).group(1))
for density in (1, 1.5, 3):
    dp = lambda value: int(value * density + .5)
    assert dp(zoom_margin) + dp(24) - dp(buttons_height) - dp(buttons_margin) >= dp(36), 'ruler/camera island gap'
    assert dp(13) + dp(28) + dp(timer_margin) - dp(55) >= dp(16), 'record timer left inset'

legacy_methods = '\n'.join(method(legacy, signature) for signature in [
    'private java.util.List<Integer> zoomRatios()', 'public float getMaxZoomFactor()',
    'public float getZoomFactor()', 'public void setZoomFactor(float factor)',
    'public void setZoom(float value)', 'private void scheduleRoundCameraUpdate()', 'public void setTorchEnabled(boolean enabled)'])

java = r'''
import app.nebulagram.ui.NebulaZoomCapabilities;
import app.nebulagram.ui.NebulaZoomCapabilities.Module;
import java.util.*;
class CameraZoomCheck {
 static class CubicBezierInterpolator {static Object EASE_OUT=new Object();}
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static void close(float a,float b){check(Math.abs(a-b)<.002f,a+" != "+b);}
 static class ValueAnimator {
  interface Update {void accept(ValueAnimator animator);}
  float start,end,value;boolean canceled;Update update;
  static ValueAnimator ofFloat(float a,float b){ValueAnimator v=new ValueAnimator();v.start=a;v.end=b;return v;}
  void setDuration(int ms){check(ms>0&&ms<=270,"responsive preset animation");}
  void setInterpolator(Object i){}
  void addUpdateListener(Update listener){update=listener;}
  void start(){tick(0);}
  void cancel(){canceled=true;}
  Object getAnimatedValue(){return value;}
  void tick(float fraction){if(canceled)return;value=start+(end-start)*fraction;update.accept(this);}
 }
 interface OnZoomChanged {void onZoomChanged(float factor);}
 static class MotionEvent {
  static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;int action;float x;
  MotionEvent(int action,float x){this.action=action;this.x=x;}int getActionMasked(){return action;}float getX(){return x;}float getY(){return 48;}
 }
 static class Parent {boolean onTouchEvent(MotionEvent e){return false;}}
 static class UiParent {void requestDisallowInterceptTouchEvent(boolean value){}}
 static class Ruler extends Parent {
  float minimum=1,maximum=2,current=1,density=1;
  float expansion;float[] cameraStops={1};ValueAnimator zoomAnimator;float applied;boolean frontFacing;
  List<Float> requests=new ArrayList<>();
  OnZoomChanged callback=factor -> {applied=factor;requests.add(factor);};void invalidate(){}
  float dragStartX,dragStartZoom;boolean dragged,held,dragOnRuler,expanded;Runnable pending;
  Runnable longPress=()->{held=true;setExpanded(true);},collapse=()->setExpanded(false);
  UiParent getParent(){return new UiParent();}void removeCallbacks(Runnable r){if(pending==r)pending=null;}
  void postDelayed(Runnable r,int delay){pending=r;}void setExpanded(boolean value){expanded=value;}
  void scheduleCollapse(){}boolean performClick(){return true;}
  float presetX(int index){return (getWidth()-compactWidth())/2+compactWidth()/cameraStops.length*(index+.5f);}
  void tap(int index){float x=presetX(index);onTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,x));onTouchEvent(new MotionEvent(MotionEvent.ACTION_UP,x));}
  static class RectF {float left,top,right,bottom;RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}}
  int getWidth(){return (int)(320*density);}int dp(float v){return (int)Math.ceil(v*density);}
  int getHeight(){return (int)(96*density);}
  void setCurrent(float v){current=Math.max(minimum,Math.min(maximum,v));}
  void updateRulerMarks(){}
  GEOMETRY
 }
 static class Camera {
  static class Parameters {
   static String FLASH_MODE_ON="on",FLASH_MODE_TORCH="torch",FLASH_MODE_OFF="off";
   boolean isZoomSupported(){return true;}
   List<Integer> getZoomRatios(){return Arrays.asList(100,120,200,400,2000);}
  }
  Parameters getParameters(){return new Parameters();}
 }
 static class FileLog {static void e(Exception e){throw new AssertionError(e);}}
 static class TextUtils {static boolean equals(String a,String b){return Objects.equals(a,b);}}
 static class Worker {
  boolean running;List<Runnable> queue=new ArrayList<>();
  void execute(Runnable r){queue.add(r);}
  void drain(){running=true;while(!queue.isEmpty())queue.remove(0).run();running=false;}
 }
 static class CameraController {
  static CameraController instance=new CameraController();Worker threadPool=new Worker();
  static CameraController getInstance(){return instance;}
 }
 static class Legacy {
  static class Info {Camera camera=new Camera();}
  Info cameraInfo=new Info();float currentZoom;int maxZoom=4;java.util.List<Integer> cachedZoomRatios;
  boolean destroyed,isVideo=true,isRound=true,useTorch;String currentFlashMode="off",appliedFlash;
  int configurations,photoConfigurations;float appliedZoom;
  Object roundUpdateLock=new Object();boolean roundUpdateQueued;
  void configureRoundCamera(boolean initial){check(CameraController.getInstance().threadPool.running,"Camera1 IPC ran on UI thread");configurations++;appliedZoom=getZoomFactor();appliedFlash=currentFlashMode;}
  void configurePhotoCamera(){photoConfigurations++;}
  LEGACY
 }
 public static void main(String[] args) {
  Module ultra=new Module("ultra",.5f,1,8,false),wide=new Module("wide",1,1,10,false),tele=new Module("tele",3,1,20,false);
  NebulaZoomCapabilities c=new NebulaZoomCapabilities(Arrays.asList(ultra,wide,tele),wide,Collections.emptyList());
  close(c.minimum,.5f);close(c.maximum,60);check(c.needsReopen(),"independent modules need a preview transition");
  for(float zoom:new float[]{.5f,.8f,1,1.5f,2,3,5,10,30,60}) {
   Module m=c.select(zoom);close(m.base*m.local(zoom),zoom);
   check(m.id.equals(zoom<1?"ultra":zoom<3?"wide":"tele"),"optical module at "+zoom);
  }
  Module logical=new Module("logical",1,.4f,100,true);
  c=new NebulaZoomCapabilities(Arrays.asList(logical),logical,Arrays.asList(.4f,1f,3f,5f));
  close(c.maximum,100);check(c.stops.length==4,"physical lens stops, no invented digital buttons");
  check(!c.needsReopen(),"logical HAL handles physical transitions");
  for(float zoom:new float[]{.4f,1,3,5,30,100})check(c.select(zoom)==logical,"logical path remains uninterrupted");
  Module cropped=new Module("logical",1,.6f,30,true);
  c=new NebulaZoomCapabilities(Arrays.asList(cropped),cropped,Arrays.asList(.5f,1f,3f));
  close(c.stops[0],.6f);check(c.stops.length==3,"cropped ultra-wide remains selectable at supported floor");
  Module gap=new Module("tele",5,1,2,false);
  c=new NebulaZoomCapabilities(Arrays.asList(wide,gap),wide,Collections.emptyList());
  check(c.select(0.1f)==wide,"out of range uses usable endpoint");
  Module narrow=new Module("wide",1,1,2,false);
  c=new NebulaZoomCapabilities(Arrays.asList(narrow,gap),narrow,Collections.emptyList());
  check(c.select(3)==narrow,"range gap snaps to nearest logarithmic endpoint");
  for(float density:new float[]{1,2.75f,4})for(float[] range:new float[][]{{.3f,2},{1,2},{.5f,60},{1,100}}) {
   Ruler r=new Ruler();r.density=density;r.current=1.5f;r.setRange(range[0],range[1]);close(r.current,1.5f);
   for(int lenses:new int[]{1,4,6})for(float expansion:new float[]{0,.5f,1}) {
    r.cameraStops=new float[lenses];r.expansion=expansion;Ruler.RectF b=r.capsuleBounds();
    check(b.left-r.dp(6)>=0&&b.right+r.dp(6)<=r.getWidth(),"native glass padding not clipped");
    check(b.bottom-b.top==2*r.dp(24),"compact and ruler share a readable 48dp height");
    check(b.top-r.dp(14)>=0&&b.bottom+r.dp(14+5)<=r.getHeight(),"glass shadow not clipped");
   }
   for(float zoom:new float[]{range[0],(float)Math.sqrt(range[0]*range[1]),range[1]}) {
    r.setCurrent(zoom);
    float needle=r.xForZoom(zoom);close(needle,r.getWidth()/2f);
    check(r.xForZoom(range[0])<=needle&&r.xForZoom(range[1])>=needle,"ruler order");
    check(r.pixelsPerOctave()>=95*density,"high zoom doesn't compress tick spacing");
   }
  }
  Ruler r=new Ruler();r.setRange(1,60);r.setCameraStops(new float[]{1});
  check(Arrays.equals(r.cameraStops,new float[]{1,2,5,60}),"rear quick 2x, intermediate zoom and true endpoint");
  r.setRange(.6f,60);r.setCameraStops(new float[]{.6f,1,3,5});
  check(Arrays.equals(r.cameraStops,new float[]{.6f,1,2,3,5,60}),"digital 2x complements optical stops");
  r.setCameraStops(new float[]{.6f,1,2.001f,5.001f});
  check(Arrays.equals(r.cameraStops,new float[]{.6f,1,2.001f,5.001f,60}),"fractional focal metadata must not duplicate 2x/5x buttons");
  r.frontFacing=true;r.setCameraStops(new float[]{.6f,1,3,5});
  check(Arrays.equals(r.cameraStops,new float[]{1,2}),"front shortcuts never inherit rear lenses");
  r.setCurrent(9);close(r.current,9);close(r.xForZoom(9),160);
  r.setRange(1,1.8f);r.setCameraStops(new float[]{1});check(r.cameraStops.length==1,"unsupported 2x hidden");
  r.setRange(1,4);r.setCurrent(1);r.animateZoom(4);r.zoomAnimator.tick(.5f);close(r.applied,2);
  ValueAnimator interrupted=r.zoomAnimator;r.animateZoom(1);check(interrupted.canceled,"new tap replaces animation");
  r.zoomAnimator.tick(1);close(r.applied,1);r.animateZoom(999);r.zoomAnimator.tick(1);close(r.applied,4);
  r=new Ruler();r.setRange(1,30);r.cameraStops=new float[]{1,2,30};
  r.tap(1);close(r.current,2);close(r.applied,2);
  check(r.requests.equals(Arrays.asList(2f)),"compact 2x tap submits the target exactly once, no intermediate fractions");
  check(r.zoomAnimator==null,"quick presets do not wait for animated numeric interpolation");
  r.animateZoom(5);ValueAnimator old=r.zoomAnimator;r.requests.clear();r.tap(0);r.tap(2);old.tick(1);
  check(old.canceled&&r.requests.equals(Arrays.asList(1f,30f)),"rapid taps replace prior animated work without stale frames");
  close(r.current,30);
  r.setCurrent(1);r.requests.clear();float x=r.presetX(0);
  r.onTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,x));r.pending.run();r.onTouchEvent(new MotionEvent(MotionEvent.ACTION_UP,x));
  check(r.expanded&&r.requests.isEmpty(),"holding opens ruler without selecting a quick preset");
  r.expanded=false;r.onTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,x));r.onTouchEvent(new MotionEvent(MotionEvent.ACTION_MOVE,x-20));
  int requests=r.requests.size();r.onTouchEvent(new MotionEvent(MotionEvent.ACTION_UP,x-20));
  check(requests==1&&r.requests.size()==requests&&r.current>1,"drag stays continuous and does not submit an extra preset on release");
  r.expanded=false;r.requests.clear();r.onTouchEvent(new MotionEvent(MotionEvent.ACTION_DOWN,x));r.onTouchEvent(new MotionEvent(MotionEvent.ACTION_CANCEL,x));
  check(r.requests.isEmpty()&&r.pending==null,"cancelled tap never changes zoom");
  r=new Ruler();r.setRange(Float.NaN,100);close(r.maximum,2);
  Legacy l=new Legacy();close(l.getMaxZoomFactor(),20);
  for(float zoom:new float[]{1,1.2f,2,4,20}){l.setZoomFactor(zoom);close(l.getZoomFactor(),zoom);}
  Worker worker=CameraController.getInstance().threadPool;
  check(l.configurations==0&&worker.queue.size()==1,"Camera1 input coalesces off UI");
  l.setTorchEnabled(true);check(worker.queue.size()==1,"torch shares the camera queue");worker.drain();
  check(l.configurations==1&&l.appliedFlash.equals("torch"),"latest zoom and flash applied together");close(l.appliedZoom,20);
  l.setZoomFactor(1.9f);close(l.getZoomFactor(),2);l.setZoomFactor(99);close(l.getZoomFactor(),20);
  worker.drain();int configurations=l.configurations;l.setZoomFactor(99);check(worker.queue.isEmpty(),"same quantized zoom ignored");
  l.setZoomFactor(4);l.destroyed=true;worker.drain();check(l.configurations==configurations,"Camera1 ignores work after destroy");
  l.setZoomFactor(8);l.setTorchEnabled(false);check(worker.queue.isEmpty(),"destroyed Camera1 queues no hardware requests");
  l=new Legacy();l.isRound=false;l.setZoom(.5f);check(l.photoConfigurations==1&&worker.queue.isEmpty(),"photo camera retains native synchronous path");
  System.out.println("Camera zoom: optical routing, 100x logical range, ratio table, pinch/ruler geometry passed");
 }
}
'''.replace('GEOMETRY', geometry).replace('LEGACY', legacy_methods)
with tempfile.TemporaryDirectory(prefix='nebula-camera-zoom-') as temp:
    source = Path(temp) / 'CameraZoomCheck.java'
    source.write_text(java, encoding='utf-8')
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', temp,
                    str(overlay / 'NebulaZoomCapabilities.java'), str(source)], check=True)
    subprocess.run(['java', '-cp', temp, 'CameraZoomCheck'], check=True)

subprocess.run([sys.executable, str(root / 'scripts/check-camera-zoom-discovery.py')], check=True)
subprocess.run([sys.executable, str(root / 'scripts/check-camera-zoom-recorder.py'), str(tree)], check=True)
subprocess.run([sys.executable, str(root / 'scripts/check-camera-zoom-requests.py'), str(tree)], check=True)
subprocess.run([sys.executable, str(root / 'scripts/check-camera-zoom-colors.py')], check=True)
