"""Execute module routing, ruler geometry and the patched legacy camera ratio mapping."""
from pathlib import Path
import subprocess
import tempfile
import sys

root = Path(__file__).resolve().parent.parent
overlay = root / 'platform/android/overlay/TMessagesProj/src/main/java/app/nebulagram/ui'
tree = Path(sys.argv[1]) if len(sys.argv) > 1 else root / 'build/cherrygram-android'

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
    'public void setRange(float min, float max)', 'private float fullWidth()',
    'private float pixelsPerOctave()', 'private float xForZoom(float zoom)'])
legacy = (tree / 'TMessagesProj/src/main/java/org/telegram/messenger/camera/CameraSession.java').read_text(encoding='utf-8')
legacy_methods = '\n'.join(method(legacy, signature) for signature in [
    'private java.util.List<Integer> zoomRatios()', 'public float getMaxZoomFactor()',
    'public float getZoomFactor()', 'public void setZoomFactor(float factor)'])

java = r'''
import app.nebulagram.ui.NebulaZoomCapabilities;
import app.nebulagram.ui.NebulaZoomCapabilities.Module;
import java.util.*;
class CameraZoomCheck {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static void close(float a,float b){check(Math.abs(a-b)<.002f,a+" != "+b);}
 static class Ruler {
  float minimum=1,maximum=2,current=1,density=1;
  int getWidth(){return (int)(320*density);}int dp(float v){return (int)Math.ceil(v*density);}
  void setCurrent(float v){current=Math.max(minimum,Math.min(maximum,v));}
  GEOMETRY
 }
 static class Camera {
  static class Parameters {
   boolean isZoomSupported(){return true;}
   List<Integer> getZoomRatios(){return Arrays.asList(100,120,200,400,2000);}
  }
  Parameters getParameters(){return new Parameters();}
 }
 static class FileLog {static void e(Exception e){throw new AssertionError(e);}}
 static class Legacy {
  static class Info {Camera camera=new Camera();}
  Info cameraInfo=new Info();float currentZoom;int maxZoom=4;
  void setZoom(float v){currentZoom=v;}
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
  Module gap=new Module("tele",5,1,2,false);
  c=new NebulaZoomCapabilities(Arrays.asList(wide,gap),wide,Collections.emptyList());
  check(c.select(0.1f)==wide,"out of range uses usable endpoint");
  Module narrow=new Module("wide",1,1,2,false);
  c=new NebulaZoomCapabilities(Arrays.asList(narrow,gap),narrow,Collections.emptyList());
  check(c.select(3)==narrow,"range gap snaps to nearest logarithmic endpoint");
  for(float density:new float[]{1,2.75f,4})for(float[] range:new float[][]{{.3f,2},{1,2},{.5f,60},{1,100}}) {
   Ruler r=new Ruler();r.density=density;r.current=1.5f;r.setRange(range[0],range[1]);close(r.current,1.5f);
   for(float zoom:new float[]{range[0],(float)Math.sqrt(range[0]*range[1]),range[1]}) {
    r.setCurrent(zoom);
    float needle=r.xForZoom(zoom);check(needle>=20*density-2&&needle<=300*density+2,"needle visible at "+zoom);
    check(r.xForZoom(range[0])<=needle&&r.xForZoom(range[1])>=needle,"ruler order");
    check(r.pixelsPerOctave()>=95*density,"high zoom doesn't compress tick spacing");
   }
  }
  Ruler r=new Ruler();r.setRange(Float.NaN,100);close(r.maximum,2);
  Legacy l=new Legacy();close(l.getMaxZoomFactor(),20);
  for(float zoom:new float[]{1,1.2f,2,4,20}){l.setZoomFactor(zoom);close(l.getZoomFactor(),zoom);}
  l.setZoomFactor(1.9f);close(l.getZoomFactor(),2);l.setZoomFactor(99);close(l.getZoomFactor(),20);
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
